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

  val numSamples = 10
  val seed = 0
  //ideally, we call this once; then never again.
  var numLabelStreetEdgeEntries = 0
  var max = 15262

  def populateLabelStreetTable(): Unit ={
    max = StreetEdgeTable.all.length
    val streetEdges = StreetEdgeTable.all.toArray
    val sampleIndexes = getSampleEdges(seed, max)

    logprint("the max index: " + max)

    var allLabels: Array[(Int, Int, Int)] = Array[(Int, Int, Int)]()
    for(sampleIndex: Int <- sampleIndexes){
        logprint("random sampled index " + sampleIndex)
        val streetEdge = streetEdges(sampleIndex)

        //get the path of lat longs (YAY! no longer need slow google API for paths!)
        val coordinates: Array[Coordinate] = streetEdge.geom.getCoordinates
        val path: Array[geojson.LatLng] = coordinates.map(coord => geojson.LatLng(coord.y, coord.x)).toArray

        //go through our path
        for(i <- 0 until path.length){
            //get the start and end points
            val start = path(i)
            val newLabels : List[(Int)] = getLabelsNear(start.lat.toFloat, start.lng.toFloat)
            logprint("     number of labels found at " + i + ": " + newLabels.length)
            for(labelId : (Int) <- newLabels){
              var labelMetaData = (streetEdge.streetEdgeId, i, labelId)
              allLabels = allLabels :+ labelMetaData
            }
        }
    }
    clearLabelStreetTable()
    for(labelData : (Int,Int,Int) <- allLabels) {
      insertEntryStreetEdgeTable(labelData._1, labelData._2, labelData._3)
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

  //pupulate the 'left/right' part of the table, as well as bearing statistics.
  def populateAlgorithmTable(): Unit ={
    max = StreetEdgeTable.all.length
    val streetEdges = StreetEdgeTable.all.toArray
    val sampleIndexes = getSampleEdges(seed, max)
    clearAlgorithmTable()
    for(sampleIndex: Int <- sampleIndexes){

        val streetEdge = streetEdges(sampleIndex)
        val coordinates: Array[Coordinate] = streetEdge.geom.getCoordinates
        val path: Array[geojson.LatLng] = coordinates.map(coord => geojson.LatLng(coord.y, coord.x)).toArray
        val labels: List[(Int,Int)] = getAllLabelStreetEntriesWithStreetIndex(streetEdge.streetEdgeId)

        for(label: (Int, Int) <- labels) {
            var start: play.extras.geojson.LatLng = null;
            var end: play.extras.geojson.LatLng = null;
            //get the start and end points
            if(label._1 + 1 < path.length){
              start = path(label._1)
              end = path(label._1 + 1)
            }else{
              start = path(label._1 - 1)
              end = path(label._1)
            }

            val roadBearing = getBearing(start.lat.toFloat, start.lng.toFloat, end.lat.toFloat, end.lng.toFloat)
            val labelBearing = getHeadingFromId(label._2)
            insertEntryAlgorithm(label._2, !(labelBearing - roadBearing > 0 && labelBearing - roadBearing < 180),roadBearing, labelBearing)
        }
    }
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
  def insertEntryStreetEdgeTable(streetEdgeId: Int, index: Int, labelId: Int): Unit = db.withSession { implicit session =>
    Q.updateNA("INSERT INTO sidewalk.label_street_edge VALUES("+ streetEdgeId + "," + index + "," + labelId + ")").execute
  }

  def insertEntryGroundTruth(label_id: Int, isRight: Boolean): Unit = db.withSession { implicit session =>
    Q.updateNA("INSERT INTO sidewalk.label_street_side_ground_truth VALUES("+ label_id + "," + isRight + ")").execute
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

  def getAllLabelStreetEntriesWithStreetIndex(streetEdgeIndex: Int): List[(Int, Int)] = db.withSession { implicit session =>
    val selectQuery = Q.query[Int, (Int, Int)](
      """SELECT ls.index, ls.label_id
        |FROM sidewalk.label_street_edge AS ls
        |WHERE ls.street_edge_id = ?""".stripMargin
    )
    selectQuery(streetEdgeIndex).list
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