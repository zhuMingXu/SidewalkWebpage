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
    val queryResult = Q.query[Int, (Int, Int)](
      """SELECT l.label_1_id, l.label_2_id FROM sidewalk.label_connections AS l
        |   WHERE l.label_1_id = ?
      """.stripMargin
    )
    queryResult(id).list
  }

  def getPositionFromId(id: Int): (Double, Double) = db.withSession { implicit session =>
    val selectQuery = Q.query[Int, (Double, Double)](
      """SELECT lp.lat,
        |       lp.lng,
        |FROM sidewalk.label_point AS lp,
        |WHERE lp.label_id = ?""".stripMargin
    )
    selectQuery(id).list.headOption
  }

}

object Populator {

  val db = play.api.db.slick.DB

  def populate(): Unit ={
      for(streetEdge: (Double, Double, Double, Double) <- getStreetStartsAndEnds(10)){
          //get the path of lat longs
          val path: Array[(Double, Double)] = getPointsOfPath(getJsonString(streetEdge._1,streetEdge._2,streetEdge._3,streetEdge._4))

          //get the bounds around the start, get all the labels that are in those bounds


          obtuseBearing: List[(Int, Double, Double, Int)] = new List[(Int, Double, Double, Int)]
          acuteBearing: List[(Int, Double, Double, Int)] = new List[(Int, Double, Double, Int)]


          //go through our path
          for(i <- 0 until path.length - 1){
              //get the start and end points
              val start = path(i)
              val end = path(i + 1)

              //get our bearing from the start to the end
              val bearing = getBearing(start._1, start._2, end._1, end._2)
              val bounds = getRectBounds(start._1, start._2, 10)
              val labelsInBounds : List[(Int, Double, Double, Int)] = getLabelsIn(bounds._1, bounds._2, bounds._3, bounds._4)


              for(label <- labelsInBounds){
                 val labelBearing = getBearing(start._1, start._2, label._2, label._3)
                 //if curb ramp or missing curb ramp, add label to respective side of street
                 if(label._4 == 1 /*curb ramp*/ || label._4 == 2 /*missing curb ramp*/){
                    if(labelBearing - bearing < 0){
                        acuteBearing += label
                    }else{
                        obtuseBearing += label
                    }
                 }
                 //if no sidewalk, clear the list and connect what we have
                 else if(label._4 == 7/*no sidewalk*/){
                   if(labelBearing - bearing < 0){
                     makeConnections(acuteBearing)
                     acuteBearing = new List[(Int, Double, Double, Int)]
                   }else{
                     makeConnections(obtuseBearing)
                     obtuseBearing = new List[(Int, Double, Double, Int)]
                   }
                 }
              }

              //make connections between all the labels that we have remaining.
              makeConnections(acuteBearing)
              makeConnections(obtuseBearing)
          }
      }
  }


  def makeConnections(labels: List[(Int, Double, Double, Int)]): Unit ={
      for(label1 <- labels){
          for(label2 <- labels){
              if(label1._1 !== label2._1){
                insertEntry(label1._1, label2._1)
              }
          }
      }
  }


  def getRectBounds(startLat: Double, startLng: Double, maxRad: Double): (Double, Double, Double, Double) = {
      val upperLeft = flyCrowFromStart(startLat, startLng, 135, maxRad);
      val lowerRight = flyCrowFromStart(startLat, startLng, -45, maxRad);
      (upperLeft._1, lowerRight._1, lowerRight._2, upperLeft._2)
  }


  def flyCrowFromStart(startLat: Double, startLng: Double, bearing: Double, distance: Double): (Double,Double) ={
    val lat = Math.asin(Math.sin(startLat) * Math.cos(d / R) + Math.cos(startLat) * Math.sin(d / R) * Math.cos(bearing))
    val lng = startLng + Math.atan2(Math.sin(bearing) * Math.sin(d / R) * Math.cos(startLat), Math.cos(d / R) - Math.sin(startLat) * Math.sin(lat))
    (lat, lng)
  }

  //gets distance between two lat long coordinates "as the crow flies"
  def getDist(startLat: Double, startLng: Double, endLat: Double, endLng: Double): Double ={
    val R = 6371e3 // meters
    val rad_lat_start = startLat.toRadians
    val rad_lat_end = endLat.toRadians
    val delta_rad_lat = (endLat - startLat).toRadians
    val delta_rad_lng = (endLng - startLng).toRadians
    val a = Math.sin(delta_rad_lat / 2) * Math.sin(delta_rad_lat / 2) + Math.cos(rad_lat_start) * Math.cos(rad_lat_end) * Math.sin(delta_rad_lng / 2) * Math.sin(delta_rad_lng / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(_start - a))
    R * c
  }

  def getBearing(startLat: Double, startLng: Double, endLat: Double, endLng: Double): Double ={
      val y = Math.sin(endLng - startLng) * Math.cos(endLat)
      val x = Math.cos(startLat) * Math.sin(endLat) - Math.sin(startLat) * Math.cos(endLat) * Math.cos(endLng - startLng)
      val bearing = Math.atan2(y, x).toDegrees
  }


  def getLabelsIn(minLat: Double, maxLat: Double, minLng: Double, maxLng: Double): List[(Int, Double, Double, Int)] = db.withSession { implicit session =>
    val selectQuery = Q.query[Double, Double, Double, Double, (Int, Double, Double, Int)](
                      """SELECT lb.label_id,
                        |       lp.lat,
                        |       lp.lng,
                        |       lb.label_type_id
                        |FROM sidewalk.label AS lb,
                        |     sidewalk.label_point AS lp
                        |WHERE lp.label_id = lb.label_id AND
                        |lp.lat > ? AND lp.lat < ? AND
                        |lp.lng > ? AND lp.lng < ? AND""".stripMargin
                      )
    selectQuery(minLat,maxLat,minLng,maxLng)
  }


  def getPointsOfPath(jsonString: String): Array[(Double, Double)] = {
      val polylineList = decode(Json.parse(jsonString)['overview_polyline'])
      polylineList.asScala.toArray
  }

  import java.util

  /**
    * Decodes an encoded path string into a sequence of LatLngs.
    */
  def decode(encodedPath: String): util.List[(Double, Double)] = {
    val len = encodedPath.length
    // For speed we preallocate to an upper bound on the final length, then
    // truncate the array before returning.
    val path = new util.ArrayList[Nothing]
    var index = 0
    var lat = 0
    var lng = 0
    while ( {
      index < len
    }) {
      var result = 1
      var shift = 0
      var b = 0
      do {
        b = encodedPath.charAt({
          index += 1; index - 1
        }) - 63 - 1
        result += b << shift
        shift += 5
      } while ( {
        b >= 0x1f
      })
      lat +=
      if ((result & 1) != 0) ~result >> 1
      else result >> 1
      result = 1
      shift = 0
      do {
        b = encodedPath.charAt({
          index += 1; index - 1
        }) - 63 - 1
        result += b << shift
        shift += 5
      } while ( {
        b >= 0x1f
      })
      lng +=
      if ((result & 1) != 0) ~result >> 1
      else result >> 1
      path.add((lat * 1e-5, lng * 1e-5))
    }
    path
  }

  //get the json from the points
  def getJsonString(x1: Double, y1: Double, x2: Double, y2: Double): String={
      //uses google to get the route as a json string
      val url = "https://maps.googleapis.com/maps/api/directions/json?origin="+ x1 + "," + y1 +"&destination="+ x2 + "," + y2 +"&key="
      scala.io.Source.fromURL(url).mkString
  }

  //get the starts and ends of the streets as a List of Double arrays.
  //in format x1,y1,x2,y2
  def getStreetStartsAndEnds(limit) = db.withSession { implicit session =>
    val queryResult = Q.queryNA[Int, (Double, Double, Double, Double)](
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
  def insertEntry(label1Id: Int, label2Id: Int): Unit = db.withSession { implicit session =>
    Q.updateNA("INSERT INTO sidewalk.label_connections VALUES("+ label1Id + "," + label2Id + ")").execute
  }
}