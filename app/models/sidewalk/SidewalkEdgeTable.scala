package models.sidewalk

/**
 * References:
 * Slick-pg https://github.com/tminglei/slick-pg
 *
 * To use models when using REPL, type:
 * scala> new play.core.StaticApplication(new java.io.File("."))
 * https://yobriefca.se/blog/2014/07/11/working-with-play-apps-in-the-console/
   */

//import scala.slick.driver.PostgresDriver.simple._

import java.sql.Timestamp

import com.vividsolutions.jts.geom.LineString
import models.utils.MyPostgresDriver.simple._
import play.api.Play.current
import scala.slick.jdbc.{GetResult, StaticQuery => Q}
import scala.slick.lifted.ForeignKeyQuery
import play.api.libs.json._
import scala.util.control.Breaks._
import org.geotools.geometry.jts.JTS
import com.vividsolutions.jts.geom.Coordinate
import models.street.StreetEdgeTable
import models.label.LabelTable
import play.api.libs.json._
import play.extras.geojson
import com.vividsolutions.jts.geom.Coordinate
import models.utils.MyPostgresDriver.simple._
import play.api.mvc._
import models.street._
import play.api.libs.json.Json
import play.api.libs.json.Json._
import scala.util.Random

case class SidewalkEdge(sidewalkEdgeId: Option[Int], geom: LineString, source: Int, target: Int,
                        x1: Float, y1: Float, x2: Float, y2: Float, wayType: String, deleted: Boolean, timestamp: Option[Timestamp])


/**
 *
 */
class SidewalkEdgeTable(tag: Tag) extends Table[SidewalkEdge](tag, Some("sidewalk"), "sidewalk_edge") {
  def sidewalkEdgeId = column[Option[Int]]("sidewalk_edge_id", O.PrimaryKey, O.Default(Some(0)))
  def geom = column[LineString]("geom")
  def source = column[Int]("source")
  def target = column[Int]("target")
  def x1 = column[Float]("x1")
  def y1 = column[Float]("y1")
  def x2 = column[Float]("x2")
  def y2 = column[Float]("y2")
  def wayType = column[String]("way_type")
  def deleted = column[Boolean]("deleted", O.Default(false))
  def timestamp = column[Option[Timestamp]]("timestamp")

  def * = (sidewalkEdgeId, geom, source, target, x1, y1, x2, y2, wayType, deleted, timestamp) <> ((SidewalkEdge.apply _).tupled, SidewalkEdge.unapply)
}


/**
 * Data access object for the sidewalk_edge table
 */
object SidewalkEdgeTable {
  val db = play.api.db.slick.DB
  val sidewalkEdges = TableQuery[SidewalkEdgeTable]

  /**
   * Returns a list of all the sidewalk edges
   * @return A list of SidewalkEdge objects.
   */
  def all: List[SidewalkEdge] = db.withSession { implicit session =>
    sidewalkEdges.filter(edge => edge.deleted === false).list
  }

  /**
   * Set a record's deleted column to true
   */
  def delete(id: Int) = db.withSession { implicit session =>
    // sidewalkEdges.filter(_.sidewalkEdgeId == id)
    sidewalkEdges.filter(edge => edge.sidewalkEdgeId === id).map(_.deleted).update(true)
  }

  /**
   * Save a SidewalkEdge into the sidewalk_edge table
   * @param edge A SidewalkEdge object
   * @return
   */
  def save(edge: SidewalkEdge): Int = db.withTransaction { implicit session =>
    sidewalkEdges += edge
    edge.sidewalkEdgeId.get // return the edge id.
  }

  /**
   * http://stackoverflow.com/questions/19891881/scala-slick-plain-sql-retrieve-result-as-a-map
   * http://stackoverflow.com/questions/25578793/how-to-return-a-listuser-when-using-sql-with-slick
   * https://websketchbook.wordpress.com/2015/03/23/make-plain-sql-queries-work-with-slick-play-framework/
   *
   * @param id
   * @return
   */
//  def randomQuery(id: Int) = db.withSession { implicit session =>
//    import scala.slick.jdbc.meta._
//    import scala.slick.jdbc.{StaticQuery => Q}
//    import Q.interpolation
//
//    val columns = MTable.getTables(None, None, None, None).list.filter(_.name.name == "USER")
//    val user = sql"""SELECT * FROM "user" WHERE "id" = $id""".as[List[String]].firstOption.map(columns zip _ toMap)
//    user
//  }
}



//TODO: What we need instead is acute/obtuse correlation by bearing, not loc.
//So we do like this:
//1) get street segment
//2) get labels on the street segment, and where the pictures were taken.
//3) get the direction of the road at that point (hmm... trickier) HAH! NAH! I DID IT
//4) draw associations.

//NOTE: this process is very slow - it would be much faster if we could simply link labels to the placeId of the street they are on.
object Populator {

  val db = play.api.db.slick.DB
  var logStr = "";

  var pairs : List[(Int, Int)] = List[(Int, Int)]()

  val numSamples = 1000
  val seed = 0
  //ideally, we call this once; then never again.
  var numLabelStreetEdgeEntries = 0
  var max = 15262

  def populateLabelStreetTable(): Unit ={

    var allLabels: Array[(Int, Int, Int, Int)] = Array[(Int, Int, Int, Int)]()

    max = LabelTable.all.length
    val labels = LabelTable.all.toArray
    val streetEdges = StreetEdgeTable.all.toArray
    val sampleIndexes = getSampleEdges(seed, max)


    logprint("the max index: " + max)



    for(sampleIndex: Int <- sampleIndexes){
        logprint("random sampled index " + sampleIndex)
        val label = labels(sampleIndex)
        val labelLat = label.panoramaLat
        val labelLong = label.panoramaLng

        var closestStreetId = -1
        var closestStreetIndex = -1
        var closestStreetPathIndex = -1
        var closestDist = Float.MaxValue

        //loop through all street edges to find the closest point to the label, as well as the closest index.
        for(currStreetEdge <- 0 until streetEdges.length){
          val streetEdge = streetEdges(currStreetEdge)
          //get the path of lat longs
          val coordinates: Array[Coordinate] = streetEdge.geom.getCoordinates
          val path: Array[geojson.LatLng] = coordinates.map(coord => geojson.LatLng(coord.y, coord.x)).toArray

          //go through our path
          for(i <- 0 until path.length){
            //get the current pos in the path
            val currPos = path(i)
            //get lat and long
            val currPosLat = currPos.lat.toFloat
            val currPosLong =  currPos.lng.toFloat
            val distToLabel = getDist(labelLat, labelLong, currPosLat, currPosLong)
            if(distToLabel < closestDist){
              closestStreetId = streetEdges(currStreetEdge).streetEdgeId
              closestStreetPathIndex = i
              closestStreetIndex = currStreetEdge
              closestDist = distToLabel
            }
          }
        }
        var labelMetaData = (closestStreetId, closestStreetPathIndex, label.labelId, closestStreetIndex)
        allLabels = allLabels :+ labelMetaData
    }
    clearLabelStreetTable()
    for(labelData : (Int,Int,Int,Int) <- allLabels) {
      insertEntryStreetEdgeTable(labelData._1, labelData._2, labelData._3, labelData._4)
    }
  }


  def log(): String = {
    logStr
  }

  def logprint(msg : String)= {
    logStr += msg + " \n"
  }

  def clearLog(): Unit = {
    logStr = ""
  }

  def getSampleEdges(seed: Int, max: Int): Array[Int] ={
    var r = new Random(seed)
    var edges: Array[Int] = Array[Int]()
    for(i <- 0 until numSamples){
      var randSample = r.nextInt(max)
      while(edges contains randSample){
        randSample = r.nextInt(max)
      }
      edges = edges :+ randSample
    }
    edges
  }

  def getStartAndEndIndexes(index: Int, range: Int, max: Int): (Int, Int) ={
    if(index + range < max && index - range >= 0){
       return (index - range, index + range)
    }else if(index + range >= max){
       var start = max -  range * 2 - 1;
       if(start < 0){
         start = 0
       }
       return (start, max - 1)
    }else{
        var end = range * 2;
        if(end >= max){
          end = max - 1
        }
       return (0, end)
    }
  }

  //pupulate the 'left/right' part of the table, as well as bearing statistics.
  def populateAlgorithmTable(): Unit ={
    val streetEdges = StreetEdgeTable.all.toArray
    clearAlgorithmTable()
    val allLabels = getAllLabelsFromLabelStreetTable()

    logprint("length of street edge array: " + streetEdges.length)
    for(label: (Int, Int, Int, Int) <- allLabels) {
      breakable{

        if (label._4 >= streetEdges.length) {
          logprint("for some reason, we got an out of bounds here... index: " + label._4)
          break
        }

        val streetEdge = streetEdges(label._4)
        val coordinates: Array[Coordinate] = streetEdge.geom.getCoordinates
        val path: Array[geojson.LatLng] = coordinates.map(coord => geojson.LatLng(coord.y, coord.x)).toArray

        var start: play.extras.geojson.LatLng = null;
        var end: play.extras.geojson.LatLng = null;


        var startAndEndIndexes = getStartAndEndIndexes(label._2, 1, path.length)

        start = path(startAndEndIndexes._1)
        end = path(startAndEndIndexes._2)

        var roadBearing = getBearing(start.lat.toFloat, start.lng.toFloat, end.lat.toFloat, end.lng.toFloat)
        if(roadBearing < 0){
          roadBearing += 360
        }
        val labelBearing = getHeadingFromId(label._3)

        insertEntryAlgorithm(label._3, getClockwiseAngle(roadBearing, labelBearing) < 180,roadBearing, labelBearing)
      }
    }
  }

  def getClockwiseAngle(angleA: Float, angleB: Float): Float ={
    if(angleB > angleA){
      return angleB - angleA;
    }else{
      return (360 - angleA) + angleB;
    }
  }

  def getAllStreetPointsAsStr(): Array[String] ={
    val streetEdges = StreetEdgeTable.all.toArray
    var streetPoints: Array[String] = Array[String]()
    val allLabels = getAllLabelsFromLabelStreetTable()
    for(label: (Int, Int, Int, Int) <- allLabels) {
      val streetEdge = streetEdges(label._4)
      val coordinates: Array[Coordinate] = streetEdge.geom.getCoordinates
      val path: Array[geojson.LatLng] = coordinates.map(coord => geojson.LatLng(coord.y, coord.x)).toArray
      var startAndEndIndexes = getStartAndEndIndexes(label._2, 3, path.length)
      var streetPtsStr = "";
      for(i <- startAndEndIndexes._1 to startAndEndIndexes._2){
         streetPtsStr += path(i).lat.toFloat + "," + path(i).lng.toFloat + ";"
      }
      streetPoints = streetPoints :+ streetPtsStr
    }
    return streetPoints
  }

  //gets distance between two lat long coordinates "as the crow flies"
  def getDist(startLat: Float, startLng: Float, endLat: Float, endLng: Float): Float ={
    val R = 6371e3 // meters
    val rad_lat_start = startLat.toRadians
    val rad_lat_end = endLat.toRadians
    val delta_rad_lat = ((endLat - startLat).toDouble).toRadians
    val delta_rad_lng = ((endLng - startLng).toDouble).toRadians
    val a = Math.sin(delta_rad_lat.toDouble / 2F) * Math.sin(delta_rad_lat.toDouble / 2F) + Math.cos(rad_lat_start.toDouble) * Math.cos(rad_lat_end.toDouble) * Math.sin(delta_rad_lng.toDouble / 2F) * Math.sin(delta_rad_lng.toDouble / 2F)
    val c = 2 * Math.atan2(Math.sqrt(a.toDouble), Math.sqrt((rad_lat_start - a).toDouble))
    (R * c).toFloat
  }

  def getBearing(startLat: Float, startLng: Float, endLat: Float, endLng: Float): Float ={
      val y = Math.sin((endLng - startLng).toDouble) * Math.cos(endLat.toDouble)
      val x = Math.cos(startLat.toDouble) * Math.sin(endLat.toDouble) - Math.sin(startLat.toDouble) * Math.cos(endLat.toDouble) * Math.cos((endLng - startLng).toDouble)
      Math.atan2(y, x).toDegrees.toFloat
  }

  def getRectBounds(startLat: Float, startLng: Float, maxRad: Float): (Float, Float, Float, Float) = {
    (startLat - maxRad, startLat + maxRad, startLng - maxRad, startLng + maxRad)
  }


  def getPointsOfPath(geom: String): Array[(Float, Float)] = {
      var fullPolyline = decode(geom)
      var polyLineArr : Array[(Float, Float)] = new Array[(Float, Float)](fullPolyline.length)
      var i = 0
      for(point: (Float, Float) <- fullPolyline){
          polyLineArr(i) = point
          i = i + 1
      }
      polyLineArr
  }

  /**
    * Decodes an encoded path string into a sequence of LatLngs.
    */

  def decode(encoded: String): List[(Float, Float)] = {
    var poly = List[(Float, Float)]()
    var index = 0
    val len = encoded.length
    var lat = 0
    var lng = 0
    while ( {
      index < len
    }) {
      var b = 0
      var shift = 0
      var result = 0
      do {
        b = encoded.charAt({
          index += 1; index - 1
        }) - 63
        result |= (b & 0x1f) << shift
        shift += 5
      } while ( {
        b >= 0x20
      })
      val dlat = if ((result & 1) != 0) ~((result >> 1))
      else (result >> 1)
      lat += dlat
      shift = 0
      result = 0
      do {
        b = encoded.charAt({
          index += 1; index - 1
        }) - 63
        result |= (b & 0x1f) << shift
        shift += 5
      } while ( {
        b >= 0x20
      })
      val dlng = if ((result & 1) != 0) ~((result >> 1))
      else (result >> 1)
      lng += dlng
      val p = ((lat * 10e-6).toFloat, (lng * 10e-6).toFloat)
      poly = p :: poly
    }
    poly
  }

  def clearLabelStreetTable(): Unit = db.withSession { implicit session =>
    Q.updateNA("TRUNCATE sidewalk.label_street_edge").execute
  }

  def clearGroundTruthTable(): Unit = db.withSession { implicit session =>
    Q.updateNA("TRUNCATE sidewalk.label_street_side_ground_truth").execute
  }

  def clearAlgorithmTable(): Unit = db.withSession { implicit session =>
    Q.updateNA("TRUNCATE sidewalk.label_street_side_algorithm").execute
  }

  //inserts an entry into the label_street_edge table.
  def insertEntryStreetEdgeTable(streetEdgeId: Int, index: Int, labelId: Int, streetEdgeIndex: Int): Unit = db.withSession { implicit session =>
    Q.updateNA("INSERT INTO sidewalk.label_street_edge VALUES("+ streetEdgeId + "," + index + "," + labelId + "," + streetEdgeIndex +  ")").execute
  }

  def insertEntryGroundTruth(label_id: Int, isRight: Boolean, isIncorrectStreet: Boolean): Unit = db.withSession { implicit session =>
    Q.updateNA("INSERT INTO sidewalk.label_street_side_ground_truth VALUES("+ label_id + "," + isRight + "," + isIncorrectStreet + ")").execute
  }

  //returns number of rows deleted
  def deleteEntryGroundTruth(label_id: Int): Int = db.withSession { implicit session =>
    val deleteQuery = Q.query[Int, Int](
      """DELETE FROM sidewalk.label_street_side_ground_truth AS lgt
        |WHERE lgt.label_id = ?""".stripMargin
    )
    deleteQuery(label_id).list.head
  }

  def insertEntryAlgorithm(label_id: Int, isRight: Boolean, roadBearing: Float, labelBearing: Float): Unit = db.withSession { implicit session =>
    Q.updateNA("INSERT INTO sidewalk.label_street_side_algorithm VALUES("+ label_id + "," + isRight + "," + roadBearing + "," + labelBearing  + ")").execute
  }

  def getLabelTypeFromId(id: Int): Int = db.withSession { implicit session =>
    val selectQuery = Q.query[Int, Int](
      """SELECT lb.label_type_id
        |FROM sidewalk.label AS lb
        |WHERE lb.label_id = ?""".stripMargin
    )
    selectQuery(id).list.head
  }

  def getHeadingFromId(id: Int): Float = db.withSession { implicit session =>
    val selectQuery = Q.query[Int, Float](
      """SELECT lp.heading
        |FROM sidewalk.label_point AS lp
        |WHERE lp.label_id = ?""".stripMargin
    )
    selectQuery(id).list.head
  }

  def getPanoBearingFromId(id: Int): Float = db.withSession { implicit session =>
    val selectQuery = Q.query[Int, Float](
      """SELECT lb.photographer_heading
        |FROM sidewalk.label AS lb
        |WHERE lb.label_id = ?""".stripMargin
    )
    selectQuery(id).list.head
  }

  def getPanoPitchFromId(id: Int): Float = db.withSession { implicit session =>
    val selectQuery = Q.query[Int, Float](
      """SELECT lb.photographer_pitch
        |FROM sidewalk.label AS lb
        |WHERE lb.label_id = ?""".stripMargin
    )
    selectQuery(id).list.head
  }

  def getPositionFromId(id: Int): (Float,Float) = db.withSession { implicit session =>
    val selectQuery = Q.query[Int, (Float,Float)](
      """SELECT lp.lat, lp.lng
        |FROM sidewalk.label_point AS lp
        |WHERE lp.label_id = ?""".stripMargin
    )
    selectQuery(id).list.head
  }

  def getPanoPositionFromId(id: Int): (Float,Float) = db.withSession { implicit session =>
    val selectQuery = Q.query[Int, (Float,Float)](
      """SELECT lb.panorama_lat, lb.panorama_lng
        |FROM sidewalk.label AS lb
        |WHERE lb.label_id = ?""".stripMargin
    )
    selectQuery(id).list.head
  }

  def getLabelsNear(Lat: Float, Lng: Float): List[(Int)] = db.withSession { implicit session =>
    val epsilon : Float = 0.00001f;
    val selectQuery = Q.query[(Float, Float, Float, Float), (Int)](
      """SELECT lb.label_id
        |FROM sidewalk.label AS lb
        |WHERE lb.panorama_lat NOTNULL AND lb.panorama_lng NOTNULL AND
        |lb.panorama_lat > ? AND lb.panorama_lat < ? AND
        |lb.panorama_lng > ? AND lb.panorama_lng < ?""".stripMargin
    )
    //minLat, maxLat, minLng, maxLng
    selectQuery((Lat - epsilon, Lat + epsilon, Lng - epsilon, Lng + epsilon)).list
  }

  def getAllLabelsFromLabelStreetTable(): List[(Int, Int, Int, Int)] = db.withSession { implicit session =>
    val selectQuery = Q.queryNA[(Int, Int, Int, Int)](
      """SELECT * FROM sidewalk.label_street_edge""".stripMargin
    )
    selectQuery.list
  }

  def getAllLabelsFromAlgorithmTable(): List[(Int, Boolean, Float, Float)] = db.withSession { implicit session =>
    val selectQuery = Q.queryNA[(Int, Boolean, Float, Float)](
      """SELECT * FROM sidewalk.label_street_side_algorithm AS ls""".stripMargin
    )
    selectQuery.list
  }

  def getAllLabelsFromGroundTruthTable(): List[(Int, Boolean)] = db.withSession { implicit session =>
    val selectQuery = Q.queryNA[(Int, Boolean)](
      """SELECT * FROM sidewalk.label_street_side_ground_truth AS ls""".stripMargin
    )
    selectQuery.list
  }

  def getIsRightByLabelIDAlgorithm(label_id: Int): Boolean = db.withSession { implicit session =>
    val selectQuery = Q.query[Int, Boolean](
      """SELECT ls.isRight
         |FROM sidewalk.label_street_side_algorithm AS ls
         |WHERE ls.label_id = ?""".stripMargin
    )
    selectQuery(label_id).list.head
  }

  def getIsRightByLabelIDGroundTruth(label_id: Int): Boolean = db.withSession { implicit session =>
    val selectQuery = Q.query[Int, Boolean](
      """SELECT ls.isRight
         |FROM sidewalk.label_street_side_ground_truth AS ls 
         |WHERE ls.label_id = ?""".stripMargin
    )
    selectQuery(label_id).list.head
  }
}