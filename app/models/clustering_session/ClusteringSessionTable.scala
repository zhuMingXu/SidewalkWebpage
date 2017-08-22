package models.clustering_session

/**
  * Created by hmaddali on 7/26/17.
  */
import com.vividsolutions.jts.geom.{Coordinate, LineString}
import models.amt.AMTAssignmentTable
import models.audit.AuditTaskTable
import models.label.{LabelTable, ProblemDescriptionTable, ProblemTemporarinessTable}
import models.route.{Route, RouteTable}
import models.utils.MyPostgresDriver.simple._
import play.api.Play.current

import play.api.libs.json.{JsObject, Json}
import play.extras.geojson

import scala.slick.jdbc.{GetResult, StaticQuery => Q}
import scala.slick.lifted.ForeignKeyQuery

case class ClusteringSession(clusteringSessionId: Int, routeId: Int, clusteringThreshold: Double,
                             timeCreated: java.sql.Timestamp, deleted: Boolean)

case class LabelToCluster(labelId: Int, labelType: String, lat: Option[Float], lng: Option[Float],
                          severity: Option[Int], temp: Boolean, turkerId: String)

case class LabelsForResolution(labelId: Int, clusterId: Int, turkerId: String, gsvPanoramaId: String, labelType: String,
                               svImageX: Int, svImageY: Int, canvasX: Int, canvasY: Int, heading: Float, pitch: Float,
                               zoom: Int, canvasHeight: Int, canvasWidth: Int, alphaX: Float, alphaY: Float,
                               lat: Option[Float], lng: Option[Float], description: Option[String],
                               severity: Option[Int], temporaryProblem: Boolean)

case class LineStringCaseClass(streetEdgeId: Int, geom: LineString) {
  /**
    * This method converts the data into the GeoJSON format
    * @return
    */
  def toJSON: JsObject = {

    val coordinates: Array[Coordinate] = geom.getCoordinates
    val latlngs: List[geojson.LatLng] = coordinates.map(coord => geojson.LatLng(coord.y, coord.x)).toList
    val linestring: geojson.LineString[geojson.LatLng] = geojson.LineString(latlngs)

    Json.obj(streetEdgeId.toString -> linestring)
  }
}

case class LabelCaseClass(hitId: String, routeId: Int, turkerId: String, labelId: Int, labelType: String, severity: Int, temporary: Option[Boolean], lat: Float, lng: Float)  {
  /**
    * This method converts the data into the GeoJSON format
    * @return
    */
  def toJSON: JsObject = {
    val labelMetadataJSON = Json.obj(
      "hit_id" -> hitId,
      "route_id" -> routeId,
      "turker_id" -> turkerId,
      "label_type" -> labelType,
      "severity" -> severity,
      "temporary" -> temporary,
      "lat" -> lat,
      "lng" -> lng
    )
    Json.obj(labelId.toString -> labelMetadataJSON)
  }
}

/**
  *
  */
class ClusteringSessionTable(tag: Tag) extends Table[ClusteringSession](tag, Some("sidewalk"), "clustering_session") {
  def clusteringSessionId = column[Int]("clustering_session_id", O.NotNull, O.PrimaryKey, O.AutoInc)
  def routeId = column[Int]("route_id", O.NotNull)
  def clusteringThreshold = column[Double]("clustering_threshold", O.NotNull)
  def deleted = column[Boolean]("deleted", O.NotNull)
  def timeCreated = column[java.sql.Timestamp]("time_created",O.NotNull)
  def * = (clusteringSessionId, routeId, clusteringThreshold, timeCreated, deleted) <> ((ClusteringSession.apply _).tupled, ClusteringSession.unapply)

  def route: ForeignKeyQuery[RouteTable, Route] =
    foreignKey("clustering_session_route_id_fkey", routeId, TableQuery[RouteTable])(_.routeId)

}

/**
  * Data access object for the Clustering Session table
  */
object ClusteringSessionTable{
  val db = play.api.db.slick.DB
  val clusteringSessions = TableQuery[ClusteringSessionTable]

  import models.utils.MyPostgresDriver.plainImplicits._

  implicit val streetsGeomConverter = GetResult[LineStringCaseClass](r => {
    LineStringCaseClass(r.nextInt, r.nextGeometry[LineString])
  })

  implicit val labelConverter = GetResult[LabelCaseClass](r => {
    LabelCaseClass(r.nextString, r.nextInt, r.nextString, r.nextInt, r.nextString, r.nextInt, r.nextBooleanOption, r.nextFloat, r.nextFloat)
  })

  def getClusteringSession(clusteringSessionId: Int): Option[ClusteringSession] = db.withSession { implicit session =>
    val clusteringSession = clusteringSessions.filter(_.clusteringSessionId === clusteringSessionId).list
    clusteringSession.headOption
  }

  def all: List[ClusteringSession] = db.withSession { implicit session =>
    clusteringSessions.list
  }

  def selectSessionsWithoutDeleted: List[ClusteringSession] = db.withSession { implicit session =>
    clusteringSessions.filter(_.deleted === false).list
  }

  def getRouteIdOfClusteringSession(clusteringSessionId: Int): Option[Int] = db.withSession { implicit session =>
    val routeIds = clusteringSessions.filter(_.clusteringSessionId === clusteringSessionId).map(_.routeId).list
    routeIds.headOption
  }

  /**
    * Returns labels that were placed during the specified HIT on the specified route, in the form needed for clustering
    *
    * @param routeId
    * @param hitId
    * @return
    */
  def getLabelsToCluster(routeId: Int, hitId: String): List[LabelToCluster] = db.withSession { implicit session =>
    val asmts = AMTAssignmentTable.amtAssignments.filter(asmt => asmt.routeId === routeId && asmt.hitId === hitId && asmt.completed)
    val nonOnboardingLabs = LabelTable.labelsWithoutDeleted.filterNot(_.gsvPanoramaId === "stxXyCKAbd73DmkM2vsIHA")

    // does a bunch of inner joins
    val labels = for {
      _asmts <- asmts
      _tasks <- AuditTaskTable.auditTasks if _asmts.amtAssignmentId === _tasks.amtAssignmentId
      _labs <- nonOnboardingLabs if _tasks.auditTaskId === _labs.auditTaskId
      _latlngs <- LabelTable.labelPoints if _labs.labelId === _latlngs.labelId
      _types <- LabelTable.labelTypes if _labs.labelTypeId === _types.labelTypeId
    } yield (_asmts.turkerId, _labs.labelId, _types.labelType, _latlngs.lat, _latlngs.lng)

    // left joins to get severity for any labels that have them
    val labelsWithSeverity = for {
      (_labs, _severity) <- labels.leftJoin(LabelTable.severities).on(_._2 === _.labelId)
    } yield (_labs._1, _labs._2, _labs._3, _labs._4, _labs._5,  _severity.severity.?)

    // left joins to get temporariness for any labels that have them (those that don't are marked as temporary=false)
    val labelsWithTemporariness = for {
      (_labs, _temporariness) <- labelsWithSeverity.leftJoin(ProblemTemporarinessTable.problemTemporarinesses).on(_._2 === _.labelId)
    } yield (_labs._2, _labs._3, _labs._4, _labs._5, _labs._6, _temporariness.temporaryProblem.?, _labs._1)

    labelsWithTemporariness.list.map(x => LabelToCluster.tupled((x._1, x._2, x._3, x._4, x._5, x._6.getOrElse(false), x._7)))
  }

  /**
    * Returns labels that were used in the specified clustering session, includes all data needed for gt_label table.
    *
    * @param clusteringSessionId
    * @return
    */
  def getLabelsForGtResolution(clusteringSessionId: Int): List[LabelsForResolution] = db.withTransaction { implicit session =>
    // does a bunch of inner joins to get most of the label data
    val labels = for {
      _session <- clusteringSessions if _session.clusteringSessionId === clusteringSessionId
      _clusters <- ClusteringSessionClusterTable.clusteringSessionClusters if _session.clusteringSessionId === _clusters.clusteringSessionId
      _clustLabs <- ClusteringSessionLabelTable.clusteringSessionLabels if _clusters.clusteringSessionClusterId === _clustLabs.clusteringSessionClusterId
      _labs <- LabelTable.labels if _clustLabs.labelId === _labs.labelId
      _tasks <- AuditTaskTable.auditTasks if _labs.auditTaskId === _tasks.auditTaskId
      _amtAsmt <- AMTAssignmentTable.amtAssignments if _tasks.amtAssignmentId === _amtAsmt.amtAssignmentId
      _labPoints <- LabelTable.labelPoints if _labs.labelId === _labPoints.labelId
      _types <- LabelTable.labelTypes if _labs.labelTypeId === _types.labelTypeId
    } yield (_labs.labelId, _clusters.clusteringSessionClusterId, _amtAsmt.turkerId, _labs.gsvPanoramaId, _types.labelType,
             _labPoints.svImageX, _labPoints.svImageY, _labPoints.canvasX, _labPoints.canvasY, _labPoints.heading,
             _labPoints.pitch, _labPoints.zoom, _labPoints.canvasHeight, _labPoints.canvasWidth, _labPoints.alphaX,
             _labPoints.alphaY, _labPoints.lat, _labPoints.lng)

    // left joins to get descriptions for any labels that have them
    val labelsWithDescription = for {
      (_labs, _descriptions) <- labels.leftJoin(ProblemDescriptionTable.problemDescriptions).on(_._1 === _.labelId)
    } yield (_labs._1, _labs._2, _labs._3, _labs._4, _labs._5, _labs._6, _labs._7, _labs._8, _labs._9, _labs._10,
             _labs._11, _labs._12, _labs._13, _labs._14, _labs._15, _labs._16, _labs._17, _labs._18, _descriptions.description.?)

    // left joins to get severity for any labels that have them
    val labelsWithSeverity = for {
      (_labs, _severity) <- labelsWithDescription.leftJoin(LabelTable.severities).on(_._1 === _.labelId)
    } yield (_labs._1, _labs._2, _labs._3, _labs._4, _labs._5, _labs._6, _labs._7, _labs._8, _labs._9, _labs._10,
             _labs._11, _labs._12, _labs._13, _labs._14, _labs._15, _labs._16, _labs._17, _labs._18, _labs._19, _severity.severity.?)

    // left joins to get temporariness for any labels that have them (those that don't are marked as temporary=false)
    val labelsWithTemporariness = for {
      (_labs, _temporariness) <- labelsWithSeverity.leftJoin(ProblemTemporarinessTable.problemTemporarinesses).on(_._1 === _.labelId)
    } yield (_labs._1, _labs._2, _labs._3, _labs._4, _labs._5, _labs._6, _labs._7, _labs._8, _labs._9, _labs._10,
             _labs._11, _labs._12, _labs._13, _labs._14, _labs._15, _labs._16, _labs._17, _labs._18, _labs._19, _labs._20,
             _temporariness.temporaryProblem.?)

    labelsWithTemporariness.list.map(x =>
      LabelsForResolution.tupled((x._1, x._2, x._3, x._4, x._5, x._6, x._7, x._8, x._9, x._10, x._11, x._12, x._13,
                                  x._14, x._15, x._16, x._17, x._18, x._19, x._20, x._21.getOrElse(false))))
  }

  def getLabelsForIRR(hitId: String, routeId: Int): List[LabelCaseClass] = db.withSession { implicit session =>
    val labelsQuery = Q.query[(String, Int), LabelCaseClass](
      """SELECT amt_assignment.hit_id,
        |   		amt_assignment.route_id,
        |		    amt_assignment.turker_id,
        |		    label.label_id,
        |		    label_type.label_type,
        |		    problem_severity.severity,
        |		    problem_temporariness.temporary_problem,
        |		    label_point.lat,
        |		    label_point.lng
        |FROM  audit_task
        |	  INNER JOIN amt_assignment ON audit_task.amt_assignment_id = amt_assignment.amt_assignment_id
        |	  INNER JOIN label ON label.audit_task_id = audit_task.audit_task_id
        |	  INNER JOIN label_type ON label.label_type_id = label_type.label_type_id
        |	  LEFT OUTER JOIN problem_severity ON problem_severity.label_id = label.label_id
        |	  LEFT OUTER JOIN problem_temporariness ON problem_temporariness.label_id = label.label_id
        |	  INNER JOIN label_point ON label.label_id = label_point.label_id
        |WHERE (amt_assignment.hit_id = ? )
        |		AND ( amt_assignment.route_id = ? )
        |		AND ( label.deleted = false )
        |		AND ( label.gsv_panorama_id <> 'stxXyCKAbd73DmkM2vsIHA')
        |		AND ( amt_assignment.completed = true )
      """.stripMargin
    )
    val labelsResult = labelsQuery((hitId, routeId)).list

    val labels: List[LabelCaseClass] = labelsResult.map(l => LabelCaseClass(l.hitId, l.routeId, l.turkerId, l.labelId,
      l.labelType, l.severity, l.temporary, l.lat, l.lng))
    labels
  }

  def getStreetGeomForIRR(routeId: Int): List[LineStringCaseClass] = db.withSession { implicit session =>
    val lineStringQuery = Q.query[Int, LineStringCaseClass](
      """SELECT route_street.current_street_edge_id, street_edge.geom
        |FROM route_street
        |	INNER JOIN street_edge  ON route_street.current_street_edge_id = street_edge.street_edge_id
        |	INNER JOIN amt_volunteer_route  ON amt_volunteer_route.route_id = route_street.route_id
        |WHERE ( amt_volunteer_route.route_id = ? )
        |ORDER BY route_street.route_street_id
      """.stripMargin
    )

    val linestringList = lineStringQuery(routeId).list

    val streets: List[LineStringCaseClass] = linestringList.map(street => LineStringCaseClass(street.streetEdgeId, street.geom))
    streets
  }

  def save(clusteringSession: ClusteringSession): Int = db.withTransaction { implicit session =>
    val sId: Int =
      (clusteringSessions returning clusteringSessions.map(_.clusteringSessionId)) += clusteringSession
    sId
  }

  def updateDeleted(clusteringSessionId: Int, deleted: Boolean)= db.withTransaction { implicit session =>
    val q = for {
      clusteringSession <- clusteringSessions
      if clusteringSession.clusteringSessionId === clusteringSessionId
    } yield clusteringSession.deleted
    q.update(deleted)
  }

}