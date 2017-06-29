package models.street

import java.sql.Timestamp
import java.util.UUID
import java.util.Calendar
import java.text.SimpleDateFormat

import com.vividsolutions.jts.geom.LineString
import models.audit.AuditTaskTable
import models.region.RegionTable
import models.utils.MyPostgresDriver
import models.utils.MyPostgresDriver.simple._
import org.postgresql.util.PSQLException
import play.api.Play.current

import scala.slick.jdbc.{GetResult, StaticQuery => Q}

case class StreetEdge(streetEdgeId: Int, geom: LineString, source: Int, target: Int, x1: Float, y1: Float, x2: Float, y2: Float, wayType: String, deleted: Boolean, timestamp: Option[Timestamp])
case class StreetEdgePlus(userId: String, streetEdgeId: Int, geom: LineString, source: Int, target: Int, x1: Float, y1: Float, x2: Float, y2: Float, wayType: String, deleted: Boolean, timestamp: Option[Timestamp])

/**
 *
 */
class StreetEdgeTable(tag: Tag) extends Table[StreetEdge](tag, Some("sidewalk"), "street_edge") {
  def streetEdgeId = column[Int]("street_edge_id", O.PrimaryKey)
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

  def * = (streetEdgeId, geom, source, target, x1, y1, x2, y2, wayType, deleted, timestamp) <> ((StreetEdge.apply _).tupled, StreetEdge.unapply)
}


/**
 * Data access object for the street_edge table
 */
object StreetEdgeTable {
  // For plain query
  // https://github.com/tminglei/slick-pg/blob/slick2/src/test/scala/com/github/tminglei/slickpg/addon/PgPostGISSupportTest.scala
  import MyPostgresDriver.plainImplicits._

  implicit val streetEdgeConverter = GetResult[StreetEdge](r => {
    val streetEdgeId = r.nextInt
    val geometry = r.nextGeometry[LineString]
    val source = r.nextInt
    val target = r.nextInt
    val x1 = r.nextFloat
    val y1 = r.nextFloat
    val x2 = r.nextFloat
    val y2 = r.nextFloat
    val wayType = r.nextString
    val deleted = r.nextBoolean
    val timestamp = r.nextTimestampOption
    StreetEdge(streetEdgeId, geometry, source, target, x1, y1, x2, y2, wayType, deleted, timestamp)
  })
  implicit val streetEdgePlusConverter = GetResult[StreetEdgePlus](r => {
    val userId = r.nextString
    val streetEdgeId = r.nextInt
    val geometry = r.nextGeometry[LineString]
    val source = r.nextInt
    val target = r.nextInt
    val x1 = r.nextFloat
    val y1 = r.nextFloat
    val x2 = r.nextFloat
    val y2 = r.nextFloat
    val wayType = r.nextString
    val deleted = r.nextBoolean
    val timestamp = r.nextTimestampOption
    StreetEdgePlus(userId, streetEdgeId, geometry, source, target, x1, y1, x2, y2, wayType, deleted, timestamp)
  })

  val db = play.api.db.slick.DB
  val auditTasks = TableQuery[AuditTaskTable]
  val regions = TableQuery[RegionTable]
  val streetEdges = TableQuery[StreetEdgeTable]
  val streetEdgeAssignmentCounts = TableQuery[StreetEdgeAssignmentCountTable]
  val streetEdgeRegion = TableQuery[StreetEdgeRegionTable]

  val neighborhoods = regions.filter(_.deleted === false).filter(_.regionTypeId === 2)

  val completedAuditTasks = auditTasks.filter(_.completed === true)
  val streetEdgesWithoutDeleted = streetEdges.filter(_.deleted === false)
  val streetEdgeNeighborhood = for { (se, n) <- streetEdgeRegion.innerJoin(neighborhoods).on(_.regionId === _.regionId) } yield se


  // Jon, Kotaro, Mikey, Soheil, Manaswi, Teja, Aditya, Chirag, Sage, Anthony, Ryan H, Ladan, Ji Hyuk Bae, Maria Furman,
  // Zadorozhnyy, Alexander Zhang, Zachary Lawrence, test5, Manaswi again, test4, test6, test7, test8, test_0830, ANONYMOUS
  val researcherIds: List[String] = List("49787727-e427-4835-a153-9af6a83d1ed1", "25b85b51-574b-436e-a9c4-339eef879e78",
    "9efaca05-53bb-492e-83ab-2b47219ee863", "5473abc6-38fc-4807-a515-e44cdfb92ca2", "0c6cb637-05b7-4759-afb2-b0a25b615597",
    "9c828571-eb9d-4723-9e8d-2c00289a6f6a", "6acde11f-d9a2-4415-b73e-137f28eaa4ab", "0082be2e-c664-4c05-9881-447924880e2e",
    "ae8fc440-b465-4a45-ab49-1964a7f1dcee", "c4ba8834-4722-4ee1-8f71-4e3fe9af38eb", "41804389-8f0e-46b1-882c-477e060dbe95",
    "d8862038-e4dd-48a4-a6d0-69042d9e247a", "43bd82ab-bc7d-4be7-a637-99c92f566ba5", "0bfed786-ce24-43f9-9c58-084ae82ad175",
    "b65c0864-7c3a-4ba7-953b-50743a2634f6", "b6049113-7e7a-4421-a966-887266200d72", "395abc5a-14ea-443c-92f8-85e87fa002be",
    "a6611125-51d0-41d1-9868-befcf523e131", "1dc2f78e-f722-4450-b14e-b21b232ecdef", "ee570f03-7bca-471e-a0dc-e7924dac95a4",
    "1dc2f78e-f722-4450-b14e-b21b232ecdef", "23fce322-9f64-4e95-90fc-7141f755b2a1", "c846ef76-39c1-4a53-841c-6588edaac09b",
    "74b56671-c9b0-4052-956e-02083cbb5091", "fe724938-797a-48af-84e9-66b6b86b6245", "97760883-8ef0-4309-9a5e-0c086ef27573")
  val researcherIdsSansAnon: List[String] = researcherIds.dropRight(1)


  /**
   * Returns a list of all the street edges
    *
    * @return A list of StreetEdge objects.
   */
  def all: List[StreetEdge] = db.withSession { implicit session =>
    streetEdgesWithoutDeleted.list
  }

  /**
    * Count the number of streets that have been audited at least a given number of times
    *
    * @return
    */
  def countTotalStreets(): Int = db.withSession { implicit session =>
    all.size
  }
  
  /**
    * This method returns the audit completion rate
    *
    * @param auditCount
    * @return
    */
  def auditCompletionRate(auditCount: Int): Float = db.withSession { implicit session =>
    val allEdges = streetEdgesWithoutDeleted.list
    countAuditedStreets(auditCount).toFloat / allEdges.length
  }

  /**
    * Calculate the proportion of the total miles of DC that have been audited at least auditCount times.
    *
    * @param auditCount
    * @return Float between 0 and 1
    */
  def streetDistanceCompletionRate(auditCount: Int): Float = db.withSession { implicit session =>
    val auditedDistance = auditedStreetDistance(auditCount)
    val totalDistance = totalStreetDistance()
    auditedDistance / totalDistance
  }

  /**
    * Calculate the proportion of the total miles of DC that have been audited at least auditCount times by non researchers.
    *
    * @param auditCount
    * @return Float between 0 and 1
    */
  def streetDistanceCompletionRateByNonResearchers(auditCount: Int): Float = db.withSession { implicit session =>
    val auditedDistance = auditedStreetDistanceByNonResearchers(auditCount)
    val totalDistance = totalStreetDistance()
    auditedDistance / totalDistance
  }

  /**
    * Get the total distance in miles
    * Reference: http://gis.stackexchange.com/questions/143436/how-do-i-calculate-st-length-in-miles
    *
    * @return
    */
  def totalStreetDistance(): Float = db.withSession { implicit session =>
    // DISTINCT query: http://stackoverflow.com/questions/18256768/select-distinct-in-scala-slick

    val distances: List[Float] = streetEdgesWithoutDeleted.groupBy(x => x).map(_._1.geom.transform(26918).length).list
    (distances.sum * 0.000621371).toFloat
  }

  /**
    * Get the audited distance in miles
    * Reference: http://gis.stackexchange.com/questions/143436/how-do-i-calculate-st-length-in-miles
    *
    * @param auditCount
    * @return
    */
  def auditedStreetDistance(auditCount: Int): Float = db.withSession { implicit session =>
    // DISTINCT query: http://stackoverflow.com/questions/18256768/select-distinct-in-scala-slick
    val edges = for {
      (_streetEdges, _auditTasks) <- streetEdgesWithoutDeleted.innerJoin(completedAuditTasks).on(_.streetEdgeId === _.streetEdgeId)
    } yield _streetEdges
    val distances: List[Float] = edges.groupBy(x => x).map(_._1.geom.transform(26918).length).list
    (distances.sum * 0.000621371).toFloat
  }

  /**
    * Get the audited distance in miles of non researchers
    * Reference: http://gis.stackexchange.com/questions/143436/how-do-i-calculate-st-length-in-miles
    *
    * @param auditCount
    * @return
    */
  def auditedStreetDistanceByNonResearchers(auditCount: Int): Float = db.withSession { implicit session =>
    // DISTINCT query: http://stackoverflow.com/questions/18256768/select-distinct-in-scala-slick
    val nonResearcherAudits = completedAuditTasks.filterNot(_.userId inSet researcherIdsSansAnon)
    val edges = for {
      (_streetEdges, _auditTasks) <- streetEdgesWithoutDeleted.innerJoin(nonResearcherAudits).on(_.streetEdgeId === _.streetEdgeId)
    } yield _streetEdges
    val distances: List[Float] = edges.groupBy(x => x).map(_._1.geom.transform(26918).length).list
    (distances.sum * 0.000621371).toFloat
  }


  /**
    * Computes percentage of DC audited over time.
    *
    * author: Mikey Saugstad
    * date: 06/16/2017
    *
    * @param auditCount
    * @return List[(String,Float)] representing dates and percentages
    */
  def streetDistanceCompletionRateByDate(auditCount: Int): Seq[(String, Float)] = db.withSession { implicit session =>
    // join the street edges and audit tasks
    // TODO figure out how to do this w/out doing the join twice
    val edges = for {
      (_streetEdges, _auditTasks) <- streetEdgesWithoutDeleted.innerJoin(completedAuditTasks).on(_.streetEdgeId === _.streetEdgeId)
    } yield _streetEdges
    val audits = for {
      (_streetEdges, _auditTasks) <- streetEdgesWithoutDeleted.innerJoin(completedAuditTasks).on(_.streetEdgeId === _.streetEdgeId)
    } yield _auditTasks

    // get distances of street edges associated with their edgeId
    val edgeDists: Map[Int, Float] = edges.groupBy(x => x).map(g => (g._1.streetEdgeId, g._1.geom.transform(26918).length)).list.toMap

    // Filter out group of edges with the size less than the passed `auditCount`, picking 1 rep from each group
    // TODO pick audit with earliest timestamp
    val uniqueEdgeDists: List[(Option[Timestamp], Option[Float])] = (for ((eid, groupedAudits) <- audits.list.groupBy(_.streetEdgeId)) yield {
      if (auditCount > 0 && groupedAudits.size >= auditCount) {
        Some((groupedAudits.head.taskEnd, edgeDists.get(eid)))
      } else {
        None
      }
    }).toList.flatten

    // round the timestamps down to just the date (year-month-day)
    val dateRoundedDists: List[(Calendar, Double)] = uniqueEdgeDists.map({
      pair => {
        var c : Calendar = Calendar.getInstance()
        c.setTimeInMillis(pair._1.get.getTime)
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        (c, pair._2.get * 0.000621371)
      }})

    // sum the distances by date
    val distsPerDay: List[(Calendar, Double)] = dateRoundedDists.groupBy(_._1).mapValues(_.map(_._2).sum).view.force.toList

    // sort the list by date
    val sortedEdges: Seq[(Calendar, Double)] =
      scala.util.Sorting.stableSort(distsPerDay, (e1: (Calendar,Double), e2: (Calendar, Double)) => e1._1.getTimeInMillis < e2._1.getTimeInMillis).toSeq

    // get the cumulative distance over time
    val cumDistsPerDay: Seq[(Calendar, Double)] = sortedEdges.map({var dist = 0.0; pair => {dist += pair._2; (pair._1, dist)}})

    // calculate the completion percentage for each day
    val totalDist = totalStreetDistance()
    val ratePerDay: Seq[(Calendar, Float)] = cumDistsPerDay.map(pair => (pair._1, (100.0 * pair._2 / totalDist).toFloat))

    // format the calendar date in the correct format and return the (date,completionPercentage) pair
    val format1 = new SimpleDateFormat("yyyy-MM-dd")
    ratePerDay.map(pair => (format1.format(pair._1.getTime), pair._2))
  }

  /**
    * Count the number of streets that have been audited at least a given number of times
    *
    * @param auditCount
    * @return
    */
  def countAuditedStreets(auditCount: Int = 1): Int = db.withSession { implicit session =>
    selectAuditedStreets(auditCount).size
  }

  /**
    * Returns a list of street edges that are audited at least auditCount times
    *
    * @return
    */
  def selectAuditedStreets(auditCount: Int = 1): List[StreetEdge] = db.withSession { implicit session =>
    val edges = for {
      (_streetEdges, _auditTasks) <- streetEdgesWithoutDeleted.innerJoin(completedAuditTasks).on(_.streetEdgeId === _.streetEdgeId)
    } yield _streetEdges

    val uniqueStreetEdges: List[StreetEdge] = (for ((eid, groupedEdges) <- edges.list.groupBy(_.streetEdgeId)) yield {
      // Filter out group of edges with the size less than the passed `auditCount`
      if (auditCount > 0 && groupedEdges.size >= auditCount) {
        Some(groupedEdges.head)
      } else {
        None
      }
    }).toList.flatten

    uniqueStreetEdges
  }

  /**
    * Returns all the streets in the given region that has been audited
    * @param regionId
    * @param auditCount
    * @return
    */
  def selectAuditedStreetsByARegionId(regionId: Int, auditCount: Int = 1): List[StreetEdge] = db.withSession { implicit session =>
    val selectAuditedStreetsQuery = Q.query[Int, StreetEdge](
      """SELECT street_edge.street_edge_id, street_edge.geom, source, target, x1, y1, x2, y2, way_type, street_edge.deleted, street_edge.timestamp
        |  FROM sidewalk.street_edge
        |INNER JOIN sidewalk.region
        |  ON ST_Intersects(street_edge.geom, region.geom)
        |INNER JOIN sidewalk.audit_task
        |  ON street_edge.street_edge_id = audit_task.street_edge_id
        |  AND audit_task.completed = TRUE
        |WHERE region.region_id=?
        |  AND street_edge.deleted=FALSE
      """.stripMargin
    )
    selectAuditedStreetsQuery(regionId).list.groupBy(_.streetEdgeId).map(_._2.head).toList
  }

  def selectNonResearcherAuditedStreetsByARegionId(regionId: Int, auditCount: Int = 1): List[StreetEdgePlus] = db.withSession { implicit session =>
    val selectAuditedStreetsQuery = Q.query[Int, StreetEdgePlus](
      """SELECT audit_task.user_id, street_edge.street_edge_id, street_edge.geom, source, target, x1, y1, x2, y2, way_type, street_edge.deleted, street_edge.timestamp
        |  FROM sidewalk.street_edge
        |INNER JOIN sidewalk.region
        |  ON ST_Intersects(street_edge.geom, region.geom)
        |INNER JOIN sidewalk.audit_task
        |  ON street_edge.street_edge_id = audit_task.street_edge_id
        |  AND audit_task.completed = TRUE
        |WHERE region.region_id=?
        |  AND street_edge.deleted=FALSE
      """.stripMargin
    )
    //).filterNot(_.userId inSet researcherIdsSansAnon).groupBy(x => x).map((_._1.streetEdgeId, _._1.geom.transform(26918).length)).list.groupBy(_._1).map(_._2.head._2).toList.sum
//    val researcherIdsString = researcherIdsSansAnon.map("'" + _ + "'").mkString(",")
    val nonResearcherStreets = selectAuditedStreetsQuery(regionId).list.filterNot(edgePlus => researcherIdsSansAnon.contains(edgePlus.userId))
    nonResearcherStreets.groupBy(_.streetEdgeId).map(_._2.head).toList

//    val nonResearcherAudits = completedAuditTasks.filterNot(_.userId inSet researcherIdsSansAnon)
    //val something = selectAuditedStreetsQuery(regionId).filterNot(_.userId inSet researcherIdsSansAnon).groupBy(x => x).map((_._1.streetEdgeId, _._1.geom.transform(26918).length))
    //something.list.groupBy(_._1).map(_._2.head._2).toList.sum

  }

  def selectStreetsAuditedByAUser(userId: UUID, regionId: Int): List[StreetEdge] = db.withSession { implicit session =>
    val selectAuditedStreetsQuery = Q.query[(String, Int), StreetEdge](
      """SELECT street_edge.street_edge_id, street_edge.geom, source, target, x1, y1, x2, y2, way_type, street_edge.deleted, street_edge.timestamp
        |  FROM sidewalk.street_edge
        |INNER JOIN sidewalk.street_edge_region
        |  ON street_edge_region.street_edge_id = street_edge.street_edge_id
        |INNER JOIN sidewalk.audit_task
        |  ON street_edge.street_edge_id = audit_task.street_edge_id
        |  AND audit_task.completed = TRUE
        |  AND audit_task.user_id = ?
        |WHERE street_edge_region.region_id=?
        |  AND street_edge.deleted=FALSE
      """.stripMargin
    )
    selectAuditedStreetsQuery((userId.toString, regionId)).list.groupBy(_.streetEdgeId).map(_._2.head).toList
  }

  /**
    * Returns all the streets intersecting the neighborhood
    * @param regionId
    * @param auditCount
    * @return
    */
  def selectStreetsByARegionId(regionId: Int, auditCount: Int = 1): List[StreetEdge] = db.withSession { implicit session =>
    val selectAuditedStreetsQuery = Q.query[Int, StreetEdge](
      """SELECT street_edge.street_edge_id, street_edge.geom, source, target, x1, y1, x2, y2, way_type, street_edge.deleted, street_edge.timestamp
        |  FROM sidewalk.street_edge
        |INNER JOIN sidewalk.region
        |  ON ST_Intersects(street_edge.geom, region.geom)
        |WHERE region.region_id=?
        |  AND street_edge.deleted=FALSE
      """.stripMargin
    )

    try {
      selectAuditedStreetsQuery(regionId).list
    } catch {
      case e: PSQLException => List()
    }
  }

  def selectStreetsIntersecting(minLat: Double, minLng: Double, maxLat: Double, maxLng: Double): List[StreetEdge] = db.withSession { implicit session =>
    // http://gis.stackexchange.com/questions/60700/postgis-select-by-lat-long-bounding-box
    // http://postgis.net/docs/ST_MakeEnvelope.html
    val selectEdgeQuery = Q.query[(Double, Double, Double, Double), StreetEdge](
      """SELECT st_e.street_edge_id, st_e.geom, st_e.source, st_e.target, st_e.x1, st_e.y1, st_e.x2, st_e.y2, st_e.way_type, st_e.deleted, st_e.timestamp
       |FROM sidewalk.street_edge AS st_e
       |WHERE st_e.deleted = FALSE AND ST_Intersects(st_e.geom, ST_MakeEnvelope(?, ?, ?, ?, 4326))""".stripMargin
    )

    val edges: List[StreetEdge] = selectEdgeQuery((minLng, minLat, maxLng, maxLat)).list
    edges
  }

  def selectAuditedStreetsIntersecting(minLat: Double, minLng: Double, maxLat: Double, maxLng: Double): List[StreetEdge] = db.withSession { implicit session =>
    // http://gis.stackexchange.com/questions/60700/postgis-select-by-lat-long-bounding-box
    // http://postgis.net/docs/ST_MakeEnvelope.html
    val selectEdgeQuery = Q.query[(Double, Double, Double, Double), StreetEdge](
      """SELECT DISTINCT(street_edge.street_edge_id), street_edge.geom, street_edge.source, street_edge.target, street_edge.x1, street_edge.y1, street_edge.x2, street_edge.y2, street_edge.way_type, street_edge.deleted, street_edge.timestamp
        |  FROM sidewalk.street_edge
        |  INNER JOIN sidewalk.audit_task
        |  ON street_edge.street_edge_id = audit_task.street_edge_id
        |  WHERE street_edge.deleted = FALSE
        |  AND ST_Intersects(street_edge.geom, ST_MakeEnvelope(?, ?, ?, ?, 4326))
        |  AND audit_task.completed = TRUE""".stripMargin
    )

    val edges: List[StreetEdge] = selectEdgeQuery((minLng, minLat, maxLng, maxLat)).list
    edges
  }

  def selectStreetsWithin(minLat: Double, minLng: Double, maxLat: Double, maxLng: Double): List[StreetEdge] = db.withSession { implicit session =>
    val selectEdgeQuery = Q.query[(Double, Double, Double, Double), StreetEdge](
      """SELECT DISTINCT(st_e.street_edge_id), st_e.geom, st_e.source, st_e.target, st_e.x1, st_e.y1, st_e.x2, st_e.y2, st_e.way_type, st_e.deleted, st_e.timestamp
        |FROM sidewalk.street_edge AS st_e
        |WHERE st_e.deleted = FALSE
        |AND ST_Within(st_e.geom, ST_MakeEnvelope(?, ?, ?, ?, 4326))""".stripMargin
    )

    val edges: List[StreetEdge] = selectEdgeQuery((minLng, minLat, maxLng, maxLat)).list
    edges
  }

  def selectAuditedStreetsWithin(minLat: Double, minLng: Double, maxLat: Double, maxLng: Double): List[StreetEdge] = db.withSession { implicit session =>
    val selectEdgeQuery = Q.query[(Double, Double, Double, Double), StreetEdge](
      """SELECT DISTINCT(street_edge.street_edge_id), street_edge.geom, street_edge.source, street_edge.target, street_edge.x1, street_edge.y1, street_edge.x2, street_edge.y2, street_edge.way_type, street_edge.deleted, street_edge.timestamp
        |  FROM sidewalk.street_edge
        |  INNER JOIN sidewalk.audit_task
        |  ON street_edge.street_edge_id = audit_task.street_edge_id
        |  WHERE street_edge.deleted = FALSE
        |  AND ST_Within(street_edge.geom, ST_MakeEnvelope(?, ?, ?, ?, 4326))
        |  AND audit_task.completed = TRUE""".stripMargin
    )

    val edges: List[StreetEdge] = selectEdgeQuery((minLng, minLat, maxLng, maxLat)).list
    edges
  }

  /**
   * Set a record's deleted column to true
   */
  def delete(id: Int) = db.withSession { implicit session =>
    streetEdges.filter(edge => edge.streetEdgeId === id).map(_.deleted).update(true)
  }

  /**
   * Save a StreetEdge into the street_edge table
    *
    * @param edge A StreetEdge object
   * @return
   */
  def save(edge: StreetEdge): Int = db.withTransaction { implicit session =>
    streetEdges += edge
    edge.streetEdgeId // return the edge id.
  }
}

