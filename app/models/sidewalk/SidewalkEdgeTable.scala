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
import scala.collection.JavaConverters._
import scala.util.control.Breaks._

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

object LabelConnTable{
  val db = play.api.db.slick.DB

  def getConnectedLabelsToId(id: Int): List[(Int,Int)]= db.withSession { implicit session =>
    val queryResult = Q.query[(Int), (Int, Int)](
      """SELECT l.label_1_id, l.label_2_id FROM sidewalk.label_connections AS l
        |   WHERE l.label_1_id = ?
      """.stripMargin
    )
    queryResult((id)).list
  }

  def getConnectedLabels(): List[(Int,Int)]= db.withSession { implicit session =>
    val queryResult = Q.queryNA[(Int, Int)](
      """SELECT l.label_1_id, l.label_2_id FROM sidewalk.label_connections AS l
      """.stripMargin
    )
    queryResult.list
  }

  def getPositionFromId(id: Int): (Float, Float) = db.withSession { implicit session =>
    val selectQuery = Q.query[(Int), (Float, Float)](
      """SELECT lp.lat,
        |       lp.lng
        |FROM sidewalk.label_point AS lp
        |WHERE lp.label_id = ?""".stripMargin
    )
    selectQuery((id)).list.head
  }

  def getHeadingFromId(id: Int): (Float) = db.withSession { implicit session =>
    val selectQuery = Q.query[(Int), (Float)](
      """SELECT lb.photographer_heading
        |FROM sidewalk.label AS lb
        |WHERE lb.label_id = ?""".stripMargin
    )
    selectQuery((id)).list.head
  }
}

object Populator {

  val db = play.api.db.slick.DB
  var logStr = "";

  var pairs : List[(Int, Int)] = List[(Int, Int)]()
  def log(): String = {
     logStr
  }



  def logprint(msg : String)= {
     logStr += msg + " | "
  }

  def populate(): Unit ={
      val streetEdges = getStreetStartsAndEnds(10);
      for(streetEdge: (Float, Float, Float, Float) <- streetEdges){
          //get the path of lat longs
          val path: Array[(Float, Float)] = getPointsOfPath(getJsonString(streetEdge._1,streetEdge._2,streetEdge._3,streetEdge._4))


          //get the bounds around the start, get all the labels that are in those bounds


          var obtuseBearing: List[(Int, Float, Float, Int, Float, Float)] = List[(Int, Float, Float, Int, Float, Float)]()
          var acuteBearing: List[(Int, Float, Float, Int, Float, Float)] = List[(Int, Float, Float, Int, Float, Float)]()

          var numLbl = 0
          var recentLabels: List[(Int, Float, Float, Int)] = List[(Int, Float, Float, Int)]()
          //go through our path
          for(i <- 0 until path.length - 1){
              //get the start and end points
              val start = path(i)
              val end = path(i + 1)

              //get our bearing from the start to the end
              val bearing = getBearing(start._1, start._2, end._1, end._2)

              val bounds = getRectBounds(start._1, start._2, 0.00025F)

              val labelsInBounds : List[(Int, Float, Float, Int)] = getLabelsIn(bounds._1, bounds._2, bounds._3, bounds._4)

              var newRecentLabels: List[(Int, Float, Float, Int)] = List[(Int, Float, Float, Int)]()


              for(label <- labelsInBounds) {
                breakable{
                  newRecentLabels = label :: newRecentLabels
                  if(hasLabelCopy(recentLabels, label) || !placeIdsMatch((label._3, label._2),start)) {
                    break
                  }
                  numLbl = numLbl + 1
                  val labelBearingLatLng = getBearing(start._1, start._2, label._3, label._2)
                  val photographerBearing = LabelConnTable.getHeadingFromId(label._1)
                  val labelBearing = labelBearingLatLng

                  logprint((labelBearing - bearing) + "")
                  val labelMetaData: (Int, Float, Float, Int, Float, Float) = (label._1, label._2, label._3, label._4, (labelBearing-bearing), bearing)

                  //if curb ramp or missing curb ramp, add label to respective side of street
                  if (label._4 == 1 /*curb ramp*/ || label._4 == 2 /*missing curb ramp*/ ) {
                    if (labelBearing - bearing < 0 && labelBearing - bearing > -180) {
                      if(!hasLabelCopyMeta(obtuseBearing, label)) {
                        acuteBearing = labelMetaData :: acuteBearing
                      }
                    } else {
                      if(!hasLabelCopyMeta(acuteBearing, label)){
                        obtuseBearing = labelMetaData :: obtuseBearing
                      }
                    }
                  }
                  //if no sidewalk, clear the list and connect what we have
                  else if (label._4 == 7 /*no sidewalk*/ ) {
                    if ((labelBearing - bearing < 0 && labelBearing - bearing > -180) || (labelBearing - bearing > 180 && labelBearing - bearing < 360)){
                      makeConnections(acuteBearing)
                      acuteBearing = List[(Int, Float, Float, Int, Float, Float)]()
                    } else {
                      makeConnections(obtuseBearing)
                      obtuseBearing = List[(Int, Float, Float, Int, Float, Float)]()
                    }
                  }
                }
              }
              recentLabels = newRecentLabels
          }

          //make connections between all the labels that we have remaining.
          makeConnections(acuteBearing)
          makeConnections(obtuseBearing)
      }
  }

  def hasLabelCopy(list: List[(Int, Float, Float, Int)], labelToAdd : (Int, Float, Float, Int)): Boolean = {
      for(label : (Int, Float, Float, Int) <- list){
          if(label._1 == labelToAdd._1){
            return true
          }
      }
      return false
  }

  def hasLabelCopyMeta(list: List[(Int, Float, Float, Int,Float,Float)], labelToAdd : (Int, Float, Float, Int)): Boolean = {
    for(label : (Int, Float, Float, Int,Float,Float) <- list){
      if(label._1 == labelToAdd._1){
        return true
      }
    }
    return false
  }

  def placeIdsMatch(p1: (Float, Float), p2: (Float, Float)): Boolean = {
      val url1 = "https://roads.googleapis.com/v1/nearestRoads?&points=" + p1._2 + "," + p1._1 + "&key=AIzaSyDCBNAIxzx31wIKhxMv91i3cM5yK8gDxCk"
      val url2 = "https://roads.googleapis.com/v1/nearestRoads?&points=" + p2._2 + "," + p2._1 + "&key=AIzaSyDCBNAIxzx31wIKhxMv91i3cM5yK8gDxCk"
      val jsonStr1 = scala.io.Source.fromURL(url1).mkString.toString
      val jsonStr2 = scala.io.Source.fromURL(url2).mkString.toString
      val jsonObj1: JsValue = Json.parse(jsonStr1)
      val jsonObj2: JsValue = Json.parse(jsonStr2)
      return ((jsonObj1 \ "snappedPoints")(0) \ "placeId").toString == ((jsonObj2 \ "snappedPoints")(0) \ "placeId").toString
  }

  def makeConnections(labels: List[(Int, Float, Float, Int, Float, Float)]): Unit ={
      for(label1 <- labels){
          for(label2 <- labels){
              if(label1._1 != label2._1 && Math.abs(label1._6 - label2._6) < 20){

                insertEntry(label1._1, label2._1, label1._5, label2._5, label1._6, label2._6)
              }
          }
      }
  }

  def hasPair(pairs: List[(Int, Int)], pair: (Int, Int)): Boolean = {
      for(p <- pairs){
        if(p._1 == pair._1 && p._2 == pair._2){
          return true
        }
      }
      return false
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

  def getLabelsIn(minLat: Float, maxLat: Float, minLng: Float, maxLng: Float): List[(Int, Float, Float, Int)] = db.withSession { implicit session =>
    val selectQuery = Q.query[(Float, Float, Float, Float), (Int, Float, Float, Int)](
                      """SELECT lb.label_id,
                        |       lp.lat,
                        |       lp.lng,
                        |       lb.label_type_id
                        |FROM sidewalk.label AS lb,
                        |     sidewalk.label_point AS lp
                        |WHERE lp.label_id = lb.label_id AND
                        |lp.lat NOTNULL AND lp.lng NOTNULL AND
                        |lp.lat > ? AND lp.lat < ? AND
                        |lp.lng > ? AND lp.lng < ?""".stripMargin
                      )
    selectQuery((minLng,maxLng,minLat,maxLat)).list
  }


  def getPointsOfPath(jsonString: String): Array[(Float, Float)] = {
      var fullPolyline = List[(Float, Float)]()
      val jsonObj: JsValue = Json.parse(jsonString)
      val pointsList = jsonObj \\ "points"
      for (points <- pointsList){
          if(points.toString.length != 0){
            val polylineList = decode(points.toString)
            for (point <- polylineList){
              fullPolyline = point :: fullPolyline
            }
          }
      }
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

  //get the json from the points
  def getJsonString(x1: Float, y1: Float, x2: Float, y2: Float): String={
      //uses google to get the route as a json string
      //need that API key!!!!!!!!!!!!!!!!!
      val url = "https://maps.googleapis.com/maps/api/directions/json?origin="+ y1 + "," + x1 +"&destination="+ y2 + "," + x2 +"&key=AIzaSyDCBNAIxzx31wIKhxMv91i3cM5yK8gDxCk"
      scala.io.Source.fromURL(url).mkString.toString
  }

  //get the starts and ends of the streets as a List of Float arrays.
  //in format x1,y1,x2,y2
  def getStreetStartsAndEnds(limit: Int): List[(Float, Float, Float, Float)] = db.withSession { implicit session =>
    val queryResult = Q.query[(Int), (Float, Float, Float, Float)](
        """SELECT s.x1,
          |       s.y1,
          |       s.x2,
          |       s.y2
          |FROM sidewalk.street_edge AS s
          |LIMIT ?""".stripMargin
    )
    queryResult(limit).list
  }

  //inserts an entry into the label_connections table.
  def insertEntry(label1Id: Int, label2Id: Int, relBearing1: Float, relBearing2: Float, turtleBearing1: Float, turtleBearing2: Float): Unit = db.withSession { implicit session =>
    Q.updateNA("INSERT INTO sidewalk.label_connections VALUES("+ label1Id + "," + label2Id + "," + relBearing1 + "," + relBearing2 + "," + turtleBearing1 + "," + turtleBearing2 + ")").execute
  }
}