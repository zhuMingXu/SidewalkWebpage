package models.clustering_session

/**
  * Created by hmaddali on 7/26/17.
  */
import java.util.UUID

import com.vividsolutions.jts.geom.{Coordinate, LineString}
import models.amt._
import models.audit.{AuditTaskEnvironmentTable, AuditTaskTable}
import models.daos.slick.DBTableDefinitions.UserTable
import models.label.{LabelLocation, LabelTable, ProblemDescriptionTable, ProblemTemporarinessTable}
import models.route.{Route, RouteTable}
import models.user.UserRoleTable
import models.utils.MyPostgresDriver.simple._
import play.api.Logger
import play.api.Play.current
import play.api.libs.json.{JsObject, Json}
import play.extras.geojson

import scala.slick.jdbc.{GetResult, StaticQuery => Q}
import scala.slick.lifted.ForeignKeyQuery

case class ClusteringSession(clusteringSessionId: Int,
                             routeId: Option[Int],
                             clusteringThreshold: Double,
                             timeCreated: java.sql.Timestamp,
                             deleted: Boolean,
                             userId: Option[String],
                             turkerId: Option[String])

// TODO combine these into one type
case class LabelToCluster(labelId: Int, labelType: String, lat: Option[Float], lng: Option[Float],
                          severity: Option[Int], temp: Boolean, turkerId: String)
case class UserLabelToCluster(labelId: Int, labelType: String, lat: Option[Float], lng: Option[Float],
                          severity: Option[Int], temp: Boolean)

case class LabelsForResolution(labelId: Int, clusterId: Int, routeId: Int, turkerId: String, gsvPanoramaId: String,
                               labelType: String, svImageX: Int, svImageY: Int, canvasX: Int, canvasY: Int,
                               heading: Float, pitch: Float, zoom: Int, canvasHeight: Int, canvasWidth: Int,
                               alphaX: Float, alphaY: Float, lat: Option[Float], lng: Option[Float],
                               description: Option[String], severity: Option[Int], temporaryProblem: Boolean)
case class ClusteredLabel(sessionId: Int, clusterId: Int, labelType: String, workerId: String, role: String,
                          lat: Float, lng: Float, threshold: Double)
case class Issue(sessionId: Int, clusterId: Int, labelType: String, lat: Float, lng: Float, threshold: Double)

case class LineStringCaseClass(streetEdgeId: Int, routeId: Int, geom: LineString) {
  /**
    * This method converts the data into the GeoJSON format
    * @return
    */
  def toJSON: JsObject = {

    val coordinates: Array[Coordinate] = geom.getCoordinates
    val latlngs: List[geojson.LatLng] = coordinates.map(coord => geojson.LatLng(coord.y, coord.x)).toList
    val linestring: geojson.LineString[geojson.LatLng] = geojson.LineString(latlngs)

    val properties = Json.obj(
      "street_edge_id" -> streetEdgeId,
      "route_id" -> routeId,
      "condition_id" -> AMTConditionTable.getConditionIdForRoute(routeId)
    )
    Json.obj("type" -> "Feature", "geometry" -> linestring, "properties" -> properties)
  }
}

case class LabelCaseClass(hitId: String, routeId: Int, turkerId: String, labelId: Int, labelType: String, severity: Int, temporary: Option[Boolean], lat: Float, lng: Float, clusterId: Int)  {
  /**
    * This method converts the data into the GeoJSON format
    * @return
    */
  def toJSON: JsObject = {
    val latlngs = geojson.Point(geojson.LatLng(lat.toDouble, lng.toDouble))
    val properties = Json.obj(
      "label_id" -> labelId,
      "hit_id" -> hitId,
      "route_id" -> routeId,
      "turker_id" -> turkerId,
      "label_type" -> labelType,
      "severity" -> severity,
      "temporary" -> temporary,
      "cluster_id" -> clusterId
    )
    Json.obj("type" -> "Feature", "geometry" -> latlngs, "properties" -> properties)
  }
}

case class ClusteredTurkerLabel(routeId: Int, turkerId: String, clusterId: Int, labelId: Int, labelType: String,
                                lat: Option[Float], lng: Option[Float], severity: Option[Int], temporary: Boolean) {
  /**
    * This method converts the data into the GeoJSON format
    * @return
    */
  def toJSON: JsObject = {
    val latlngs = geojson.Point(geojson.LatLng(lat.get.toDouble, lng.get.toDouble))
    val properties = Json.obj(
      "condition_id" -> AMTConditionTable.getConditionIdForRoute(routeId),
      "route_id" -> routeId,
      "worker_id" -> turkerId,
      "label_id" -> labelId,
      "label_type" -> labelType,
      "severity" -> severity,
      "temporary" -> temporary,
      "cluster_id" -> clusterId
    )
    Json.obj("type" -> "Feature", "geometry" -> latlngs, "properties" -> properties)
  }
}

/**
  *
  */
class ClusteringSessionTable(tag: Tag) extends Table[ClusteringSession](tag, Some("sidewalk"), "clustering_session") {
  def clusteringSessionId = column[Int]("clustering_session_id", O.NotNull, O.PrimaryKey, O.AutoInc)
  def routeId = column[Option[Int]]("route_id", O.Nullable)
  def clusteringThreshold = column[Double]("clustering_threshold", O.NotNull)
  def timeCreated = column[java.sql.Timestamp]("time_created",O.NotNull)
  def deleted = column[Boolean]("deleted", O.NotNull)
  def userId = column[Option[String]]("user_id",O.Nullable)
  def turkerId = column[Option[String]]("turker_id",O.Nullable)
  def * = (clusteringSessionId, routeId, clusteringThreshold, timeCreated, deleted, userId, turkerId) <> ((ClusteringSession.apply _).tupled, ClusteringSession.unapply)

  def route: ForeignKeyQuery[RouteTable, Route] =
    foreignKey("clustering_session_route_id_fkey", routeId, TableQuery[RouteTable])(_.routeId)

}

/**
  * Data access object for the Clustering Session table
  */
object ClusteringSessionTable{
  val db = play.api.db.slick.DB
  val clusteringSessions = TableQuery[ClusteringSessionTable]

  val amtAssignments = AMTAssignmentTable.amtAssignments

  import models.utils.MyPostgresDriver.plainImplicits._

  implicit val streetsGeomConverter = GetResult[LineStringCaseClass](r => {
    LineStringCaseClass(r.nextInt, r.nextInt, r.nextGeometry[LineString])
  })

  implicit val labelConverter = GetResult[LabelCaseClass](r => {
    LabelCaseClass(r.nextString, r.nextInt, r.nextString, r.nextInt, r.nextString, r.nextInt, r.nextBooleanOption, r.nextFloat, r.nextFloat, r.nextInt)
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
    routeIds.headOption match {
      case Some(r) => r
      case None => None
    }
  }

  def getNewestClusteringSessionId: Int = db.withTransaction { implicit session =>
    clusteringSessions.map(_.clusteringSessionId).list.max
  }

  /**
    * Returns labels that were placed during the specified HIT on the specified route, in the form needed for clustering
    *
    * @param routeId
    * @param hitId
    * @return
    */
  def getLabelsToCluster(routeId: Int, hitId: String): List[LabelToCluster] = db.withSession { implicit session =>
    val asmts = amtAssignments.filter(asmt => asmt.routeId === routeId && asmt.hitId === hitId && asmt.completed)
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
    } yield (_labs._1, _labs._2, _labs._3, _labs._4, _labs._5, _severity.severity.?)

    // left joins to get temporariness for any labels that have them (those that don't are marked as temporary=false)
    val labelsWithTemporariness = for {
      (_labs, _temporariness) <- labelsWithSeverity.leftJoin(ProblemTemporarinessTable.problemTemporarinesses).on(_._2 === _.labelId)
    } yield (_labs._2, _labs._3, _labs._4, _labs._5, _labs._6, _temporariness.temporaryProblem.?, _labs._1)

    labelsWithTemporariness.list.map(x => LabelToCluster.tupled((x._1, x._2, x._3, x._4, x._5, x._6.getOrElse(false), x._7)))
  }

  /**
    * Returns clusters from specified clustering session ids, in the format needed for another round of clustering.
    *
    * @param clusteringSessionIds
    * @return
    */
  def getClusteredTurkerLabelsToCluster(clusteringSessionIds: List[Int], routeId: Option[Int]): List[LabelToCluster] = db.withSession { implicit session =>

    val possibleSessions = routeId match {
      case Some(rId) => clusteringSessions.filter(_.routeId === rId)
      case None => clusteringSessions
    }

    val labels = for {
      _session <- possibleSessions if _session.clusteringSessionId inSet clusteringSessionIds
      if !_session.turkerId.isEmpty
      _clusters <- ClusteringSessionClusterTable.clusteringSessionClusters if _session.clusteringSessionId === _clusters.clusteringSessionId
      _types <- LabelTable.labelTypes if _clusters.labelTypeId === _types.labelTypeId
      if !(_types.labelType === "Problem")
    } yield (
      _clusters.clusteringSessionClusterId,
      _types.labelType,
      _clusters.lat,
      _clusters.lng,
      _clusters.severity,
      _clusters.temporary.getOrElse(false),
      _session.turkerId.get
    )

    labels.list.map(x => LabelToCluster.tupled(x))
  }

  /**
    * Returns clusters from specified clustering session ids, in the format needed for another round of clustering.
    *
    * @param clusteringSessionIds
    * @return
    */
  def getClusteredVolunteerLabelsToCluster(clusteringSessionIds: List[Int]): List[LabelToCluster] = db.withSession { implicit session =>

    val labels = for {
      _session <- clusteringSessions if !_session.userId.isEmpty
      _clusters <- ClusteringSessionClusterTable.clusteringSessionClusters if _session.clusteringSessionId === _clusters.clusteringSessionId
      _types <- LabelTable.labelTypes if _clusters.labelTypeId === _types.labelTypeId
      if !(_types.labelType === "Problem")
    } yield (
      _clusters.clusteringSessionClusterId,
      _types.labelType,
      _clusters.lat,
      _clusters.lng,
      _clusters.severity,
      _clusters.temporary.getOrElse(false),
      _session.userId.get
    )

    labels.list.map(x => LabelToCluster.tupled(x))
  }

  /**
    * Returns labels that were placed by the specified user, in the form needed for clustering.
    *
    * @param userId
    * @return
    */
  def getRegisteredUserLabelsToCluster(userId: String): List[UserLabelToCluster] = db.withSession { implicit session =>

    val nonOnboardingLabs = LabelTable.labelsWithoutDeleted.filterNot(_.gsvPanoramaId === "stxXyCKAbd73DmkM2vsIHA")

    val labels = for {
      _tasks <- AuditTaskTable.auditTasks if _tasks.userId === userId
      _labs <- nonOnboardingLabs if _labs.auditTaskId === _tasks.auditTaskId
      _latlngs <- LabelTable.labelPoints if _labs.labelId === _latlngs.labelId
      _types <- LabelTable.labelTypes if _labs.labelTypeId === _types.labelTypeId
    } yield (_labs.labelId, _types.labelType, _latlngs.lat, _latlngs.lng)

    // left joins to get severity for any labels that have them
    val labelsWithSeverity = for {
      (_labs, _severity) <- labels.leftJoin(LabelTable.severities).on(_._1 === _.labelId)
    } yield (_labs._1, _labs._2, _labs._3, _labs._4, _severity.severity.?)

    // left joins to get temporariness for any labels that have them (those that don't are marked as temporary=false)
    val labelsWithTemporariness = for {
      (_labs, _temporariness) <- labelsWithSeverity.leftJoin(ProblemTemporarinessTable.problemTemporarinesses).on(_._1 === _.labelId)
    } yield (_labs._1, _labs._2, _labs._3, _labs._4, _labs._5, _temporariness.temporaryProblem.?)

    labelsWithTemporariness.list.map(x => UserLabelToCluster.tupled((x._1, x._2, x._3, x._4, x._5, x._6.getOrElse(false))))
  }

  /**
    * Returns labels that were placed by the specified user, in the form needed for clustering.
    *
    * @param ipAddress
    * @return
    */
  def getAnonymousUserLabelsToCluster(ipAddress: String): List[UserLabelToCluster] = db.withSession { implicit session =>

    val nonOnboardingLabs = LabelTable.labelsWithoutDeleted.filterNot(_.gsvPanoramaId === "stxXyCKAbd73DmkM2vsIHA")

    val userAudits: List[Int] =
      AuditTaskEnvironmentTable.auditTaskEnvironments
      .filter(_.ipAddress === ipAddress)
      .map(_.auditTask)
      .list.toSet

    val labels = for {
      _tasks <- AuditTaskTable.auditTasks if _tasks.auditTaskId inSet userAudits
      _labs <- nonOnboardingLabs if _labs.auditTaskId === _tasks.auditTaskId
      _latlngs <- LabelTable.labelPoints if _labs.labelId === _latlngs.labelId
      _types <- LabelTable.labelTypes if _labs.labelTypeId === _types.labelTypeId
    } yield (_labs.labelId, _types.labelType, _latlngs.lat, _latlngs.lng)

    // left joins to get severity for any labels that have them
    val labelsWithSeverity = for {
      (_labs, _severity) <- labels.leftJoin(LabelTable.severities).on(_._1 === _.labelId)
    } yield (_labs._1, _labs._2, _labs._3, _labs._4, _severity.severity.?)

    // left joins to get temporariness for any labels that have them (those that don't are marked as temporary=false)
    val labelsWithTemporariness = for {
      (_labs, _temporariness) <- labelsWithSeverity.leftJoin(ProblemTemporarinessTable.problemTemporarinesses).on(_._1 === _.labelId)
    } yield (_labs._1, _labs._2, _labs._3, _labs._4, _labs._5, _temporariness.temporaryProblem.?)

    labelsWithTemporariness.list.map(x => UserLabelToCluster.tupled((x._1, x._2, x._3, x._4, x._5, x._6.getOrElse(false))))
  }

  /**
    * Returns the labels that were placed by the first n turkers to finish the condition, in format for clustering.
    *
    * @param conditionId Condition that should be associated with all labels returned.
    * @param routeId Route that should be associated with all labels returned.
    * @param n Number of turkers who's labels to return
    * @return
    */
  def getNonGTLabelsToCluster(conditionId: Int, routeId: Int, n: Int): List[LabelToCluster] = db.withSession { implicit session =>

    // Get list of turkers who have completed this condition
    val turkers: List[String] = AMTAssignmentTable.getTurkersWithAcceptedHITForCondition(conditionId).take(n)
    if (turkers.length != n) {
      Logger.warn(s"Trying to cluster $n turkers for condition $conditionId, but only ${turkers.length} have finished it.")
    }

    // filter for only the specified route, and the turkers that we just picked, also remove onboarding labels
    val routeAsmts = amtAssignments.filter(asmt => asmt.conditionId === conditionId && asmt.routeId === routeId)
    val asmts = routeAsmts.filter(_.turkerId inSet turkers)
    val nonOnboardingLabs = LabelTable.labelsWithoutDeleted.filterNot(_.gsvPanoramaId === "stxXyCKAbd73DmkM2vsIHA")

    // does a bunch of inner joins to go from amt_assignment table to label, label_point, label_type tables
    val labels = for {
      _asmts <- asmts
      _tasks <- AuditTaskTable.auditTasks if _asmts.amtAssignmentId === _tasks.amtAssignmentId
      _labs <- nonOnboardingLabs if _tasks.auditTaskId === _labs.auditTaskId
      _latlngs <- LabelTable.labelPoints if _labs.labelId === _latlngs.labelId
      _types <- LabelTable.labelTypes if _labs.labelTypeId === _types.labelTypeId
    } yield (_asmts.turkerId, _labs.labelId, _types.labelType, _latlngs.lat, _latlngs.lng)

    // left joins to get severity for any labels that have them
    val labelsWithSev = for {
      (_labs, _severity) <- labels.leftJoin(LabelTable.severities).on(_._2 === _.labelId)
    } yield (_labs._1, _labs._2, _labs._3, _labs._4, _labs._5,  _severity.severity.?)

    // left joins to get temporariness for any labels that have them (those that don't are marked as temporary=false)
    val labelsWithTemporariness = for {
      (_labs, _temp) <- labelsWithSev.leftJoin(ProblemTemporarinessTable.problemTemporarinesses).on(_._2 === _.labelId)
    } yield (_labs._2, _labs._3, _labs._4, _labs._5, _labs._6, _temp.temporaryProblem.?, _labs._1)

    labelsWithTemporariness.list.map(x => LabelToCluster.tupled((x._1, x._2, x._3, x._4, x._5, x._6.getOrElse(false), x._7)))
  }


  /**
    * Returns the labels that were used in the specified clustering sessions, in the format needed to calculate accuracy.
    *
    * @param clusteringSessionIds
    * @return
    */
  def getLabelsForAccuracy(clusteringSessionIds: List[Int]): List[ClusteredTurkerLabel] = db.withTransaction { implicit session =>
    // Does a bunch of inner joins to go from clustering session to label, label_point, label_type tables.
    val labels = for {
      _session <- clusteringSessions if _session.clusteringSessionId inSet clusteringSessionIds
      if !_session.routeId.isEmpty
      _clusters <- ClusteringSessionClusterTable.clusteringSessionClusters if _session.clusteringSessionId === _clusters.clusteringSessionId
      _clustLabs <- ClusteringSessionLabelTable.clusteringSessionLabels if _clusters.clusteringSessionClusterId === _clustLabs.clusteringSessionClusterId
      _labs <- LabelTable.labels if _clustLabs.labelId === _labs.labelId
      _tasks <- AuditTaskTable.auditTasks if _labs.auditTaskId === _tasks.auditTaskId
      _amtAsmt <- amtAssignments if _tasks.amtAssignmentId === _amtAsmt.amtAssignmentId
      _labPoints <- LabelTable.labelPoints if _labs.labelId === _labPoints.labelId
      _types <- LabelTable.labelTypes if _labs.labelTypeId === _types.labelTypeId
    } yield (_session.routeId, _amtAsmt.turkerId, _clusters.clusteringSessionClusterId, _labs.labelId, _types.labelType,
      _labPoints.lat, _labPoints.lng)

    // left joins to get severity for any labels that have them
    val labelsWithSev = for {
      (_labs, _severity) <- labels.leftJoin(LabelTable.severities).on(_._4 === _.labelId)
    } yield (_labs._1, _labs._2, _labs._3, _labs._4, _labs._5, _labs._6, _labs._7, _severity.severity.?)

    // left joins to get temporariness for any labels that have them (those that don't are marked as temporary=false)
    val labelsWithTemporariness = for {
      (_labs, _temp) <- labelsWithSev.leftJoin(ProblemTemporarinessTable.problemTemporarinesses).on(_._4 === _.labelId)
    } yield (_labs._1, _labs._2, _labs._3, _labs._4, _labs._5, _labs._6, _labs._7, _labs._8, _temp.temporaryProblem.?)

    labelsWithTemporariness.list.map(x =>
      ClusteredTurkerLabel.tupled((x._1.get, x._2, x._3, x._4, x._5, x._6, x._7, x._8, x._9.getOrElse(false))))
  }


  /**
    * Returns the labels that were used in the specified clustering sessions, in the format needed to calculate accuracy.
    *
    * @param clusteringSessionIds
    * @return
    */
  def getClusteredLabelsForAccuracy(clusteringSessionIds: List[Int]): List[ClusteredTurkerLabel] = db.withTransaction { implicit session =>

    val labels = for {
      _session <- clusteringSessions if _session.clusteringSessionId inSet clusteringSessionIds
      if !_session.routeId.isEmpty
      _clusters <- ClusteringSessionClusterTable.clusteringSessionClusters if _session.clusteringSessionId === _clusters.clusteringSessionId
      _clustLabs <- ClusteringSessionLabelTable.clusteringSessionLabels if _clusters.clusteringSessionClusterId === _clustLabs.clusteringSessionClusterId
      _labs <- ClusteringSessionClusterTable.clusteringSessionClusters if _clustLabs.originatingClusterId === _labs.clusteringSessionClusterId
      _types <- LabelTable.labelTypes if _clusters.labelTypeId === _types.labelTypeId
      _origSess <- clusteringSessions if _origSess.clusteringSessionId === _labs.clusteringSessionId
    } yield (
      _session.routeId.get,
      _origSess.turkerId.get,
      _clusters.clusteringSessionClusterId,
      _clustLabs.originatingClusterId.get,
      _types.labelType,
      _labs.lat,
      _labs.lng,
      _labs.severity,
      _labs.temporary
    )

    labels.list.map(x =>
      ClusteredTurkerLabel.tupled((x._1, x._2, x._3, x._4, x._5, x._6, x._7, x._8, x._9.getOrElse(false))))
  }


  /**
    * Returns the labels that were used in the specified clustering sessions, in the format needed to calculate accuracy.
    *
    * @param clusteringSessionIds
    * @return
    */
  def getSingleClusteredTurkerLabelsForAccuracy(clusteringSessionIds: List[Int]): List[TurkerLabel] = db.withTransaction { implicit session =>
    val labels = for {
      _sessions <- clusteringSessions if _sessions.clusteringSessionId inSet clusteringSessionIds
      _clust <- ClusteringSessionClusterTable.clusteringSessionClusters if _clust.clusteringSessionId === _sessions.clusteringSessionId
      _labType <- LabelTable.labelTypes if _labType.labelTypeId === _clust.labelTypeId
      // Get condition id
      _volunteerRoute <- AMTVolunteerRouteTable.amtVolunteerRoutes if _volunteerRoute.routeId === _sessions.routeId
      _condition <- AMTConditionTable.amtConditions if _condition.volunteerId === _volunteerRoute.volunteerId
    } yield (
      _condition.amtConditionId,
      _sessions.routeId.get,
      _sessions.turkerId.get,
      _clust.clusteringSessionClusterId,
      _labType.labelType,
      _clust.lat,
      _clust.lng,
      _clust.severity,
      _clust.temporary.getOrElse(false)
    )
    labels.list.map(x => TurkerLabel.tupled(x))
  }


  /**
    * Returns the labels that were used in the specified clustering sessions, in the format needed to calculate accuracy.
    *
    * @param clusteringSessionIds
    * @return
    */
  def getSingleClusteredVolunteerLabelsForAccuracy(clusteringSessionIds: List[Int]): List[VolunteerLabel] = db.withTransaction { implicit session =>
    val labels = for {
      _sessions <- clusteringSessions if _sessions.clusteringSessionId inSet clusteringSessionIds
      _clust <- ClusteringSessionClusterTable.clusteringSessionClusters if _clust.clusteringSessionId === _sessions.clusteringSessionId
      _labType <- LabelTable.labelTypes if _labType.labelTypeId === _clust.labelTypeId
      // Get condition id
      _volunteerRoute <- AMTVolunteerRouteTable.amtVolunteerRoutes if _volunteerRoute.routeId === _sessions.routeId
      _condition <- AMTConditionTable.amtConditions if _condition.volunteerId === _volunteerRoute.volunteerId
    } yield (
      _condition.amtConditionId,
      _sessions.routeId.get,
      _sessions.userId.get,
      _clust.clusteringSessionClusterId,
      _labType.labelType,
      _clust.lat,
      _clust.lng,
      _clust.severity,
      _clust.temporary.getOrElse(false)
    )
    labels.list.map(x => VolunteerLabel.tupled(x))
  }

  /**
    * Gets list of all clusters from non-deleted clustering sessions for this user.
    *
    * @param userId
    * @return
    */
  def getSingleClusteredVolunteerLabels(userId: UUID): List[ClusteredLabel] = db.withSession { implicit session =>
    val labels = for {
      _sessions <- clusteringSessions if _sessions.userId === userId.toString && _sessions.deleted === false
      _clust <- ClusteringSessionClusterTable.clusteringSessionClusters if _clust.clusteringSessionId === _sessions.clusteringSessionId
      _labType <- LabelTable.labelTypes if _labType.labelTypeId === _clust.labelTypeId
      // Get role
      _userRole <- UserRoleTable.userRoles if _userRole.userId === _sessions.userId
      _role <- UserRoleTable.roles if _role.roleId === _userRole.roleId
      if !_clust.lat.isEmpty && !_clust.lng.isEmpty
    } yield (
      _sessions.clusteringSessionId,
      _clust.clusteringSessionClusterId,
      _labType.labelType,
      _sessions.userId.get,
      _role.role,
      _clust.lat.get,
      _clust.lng.get,
      _sessions.clusteringThreshold
    )
    labels.list.map(x => ClusteredLabel.tupled(x))
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
      if !_session.routeId.isEmpty
      _clusters <- ClusteringSessionClusterTable.clusteringSessionClusters if _session.clusteringSessionId === _clusters.clusteringSessionId
      _clustLabs <- ClusteringSessionLabelTable.clusteringSessionLabels if _clusters.clusteringSessionClusterId === _clustLabs.clusteringSessionClusterId
      _labs <- LabelTable.labels if _clustLabs.labelId === _labs.labelId
      _tasks <- AuditTaskTable.auditTasks if _labs.auditTaskId === _tasks.auditTaskId
      _amtAsmt <- amtAssignments if _tasks.amtAssignmentId === _amtAsmt.amtAssignmentId
      _labPoints <- LabelTable.labelPoints if _labs.labelId === _labPoints.labelId
      _types <- LabelTable.labelTypes if _labs.labelTypeId === _types.labelTypeId
    } yield (_labs.labelId, _clusters.clusteringSessionClusterId, _session.routeId.get, _amtAsmt.turkerId,
             _labs.gsvPanoramaId, _types.labelType, _labPoints.svImageX, _labPoints.svImageY, _labPoints.canvasX,
             _labPoints.canvasY, _labPoints.heading, _labPoints.pitch, _labPoints.zoom, _labPoints.canvasHeight,
             _labPoints.canvasWidth, _labPoints.alphaX, _labPoints.alphaY, _labPoints.lat, _labPoints.lng)

    // left joins to get descriptions for any labels that have them
    val labelsWithDescription = for {
      (_labs, _descriptions) <- labels.leftJoin(ProblemDescriptionTable.problemDescriptions).on(_._1 === _.labelId)
    } yield (_labs._1, _labs._2, _labs._3, _labs._4, _labs._5, _labs._6, _labs._7, _labs._8, _labs._9, _labs._10,
             _labs._11, _labs._12, _labs._13, _labs._14, _labs._15, _labs._16, _labs._17, _labs._18, _labs._19,
             _descriptions.description.?)

    // left joins to get severity for any labels that have them
    val labelsWithSeverity = for {
      (_labs, _severity) <- labelsWithDescription.leftJoin(LabelTable.severities).on(_._1 === _.labelId)
    } yield (_labs._1, _labs._2, _labs._3, _labs._4, _labs._5, _labs._6, _labs._7, _labs._8, _labs._9, _labs._10,
             _labs._11, _labs._12, _labs._13, _labs._14, _labs._15, _labs._16, _labs._17, _labs._18, _labs._19,
             _labs._20, _severity.severity.?)

    // left joins to get temporariness for any labels that have them (those that don't are marked as temporary=false)
    val labelsWithTemporariness = for {
      (_labs, _temporariness) <- labelsWithSeverity.leftJoin(ProblemTemporarinessTable.problemTemporarinesses).on(_._1 === _.labelId)
    } yield (_labs._1, _labs._2, _labs._3, _labs._4, _labs._5, _labs._6, _labs._7, _labs._8, _labs._9, _labs._10,
             _labs._11, _labs._12, _labs._13, _labs._14, _labs._15, _labs._16, _labs._17, _labs._18, _labs._19,
             _labs._20, _labs._21, _temporariness.temporaryProblem.?)

    labelsWithTemporariness.list.map(x =>
      LabelsForResolution.tupled((x._1, x._2, x._3, x._4, x._5, x._6, x._7, x._8, x._9, x._10, x._11, x._12, x._13,
                                  x._14, x._15, x._16, x._17, x._18, x._19, x._20, x._21, x._22.getOrElse(false))))
  }

  def getLabelsForIRR(hitId: String, routeId: Int): List[LabelCaseClass] = db.withSession { implicit session =>
    val labelsQuery = Q.query[(String, Int), LabelCaseClass](
      """SELECT abc.hit_id,
        |   		abc.route_id,
        |		    abc.turker_id,
        |		    abc.label_id,
        |		    abc.label_type,
        |		    abc.severity,
        |		    abc.temporary_problem,
        |		    abc.lat,
        |		    abc.lng,
        |		    clust.cluster_id
        |FROM (SELECT amt_assignment.hit_id,
        |   		amt_assignment.route_id,
        |		    amt_assignment.turker_id,
        |		    label.label_id,
        |		    label_type.label_type,
        |		    problem_severity.severity,
        |		    problem_temporariness.temporary_problem,
        |		    label_point.lat,
        |		    label_point.lng
        |        FROM  audit_task
        |              INNER JOIN amt_assignment ON audit_task.amt_assignment_id = amt_assignment.amt_assignment_id
        |              INNER JOIN "label" ON label.audit_task_id = audit_task.audit_task_id
        |              INNER JOIN label_type ON label.label_type_id = label_type.label_type_id
        |              LEFT OUTER JOIN problem_severity ON problem_severity.label_id = label.label_id
        |              LEFT OUTER JOIN problem_temporariness ON problem_temporariness.label_id = label.label_id
        |              INNER JOIN label_point ON label.label_id = label_point.label_id
        |        WHERE (amt_assignment.hit_id = ? )
        |                AND ( amt_assignment.route_id = ? )
        |                AND ( label.deleted = false )
        |                AND ( label.gsv_panorama_id <> 'stxXyCKAbd73DmkM2vsIHA')
        |                AND ( amt_assignment.completed = true )
        |      ) AS abc,
        |      (SELECT MAX(clustering_session_cluster_id) AS "cluster_id", label_id FROM clustering_session_label GROUP BY label_id) AS clust
        |WHERE abc.label_id = clust.label_id
      """.stripMargin
    )
    val labelsResult = labelsQuery((hitId, routeId)).list

    val labels: List[LabelCaseClass] = labelsResult.map(l => LabelCaseClass(l.hitId, l.routeId, l.turkerId, l.labelId,
      l.labelType, l.severity, l.temporary, l.lat, l.lng, l.clusterId))
    labels
  }

  def getStreetGeomForIRR(routeId: Int): List[LineStringCaseClass] = db.withSession { implicit session =>
    val lineStringQuery = Q.query[Int, LineStringCaseClass](
      """SELECT route_street.current_street_edge_id, route_street.route_id, street_edge.geom
        |FROM route_street
        |	INNER JOIN street_edge  ON route_street.current_street_edge_id = street_edge.street_edge_id
        |	INNER JOIN amt_volunteer_route  ON amt_volunteer_route.route_id = route_street.route_id
        |WHERE ( amt_volunteer_route.route_id = ? )
        |ORDER BY route_street.route_street_id
      """.stripMargin
    )

    val linestringList = lineStringQuery(routeId).list

    val streets: List[LineStringCaseClass] = linestringList.map(street => LineStringCaseClass(street.streetEdgeId,
                                                                              street.routeId, street.geom))
    streets
  }


  /**
    * Given clustering sesion id, returns the clusters and the label that made up the clusters
    *
    * @param sessionId
    * @return
    */
  def getLabelsForValidation(sessionId: Int): (List[Issue], List[ClusteredLabel]) = db.withSession { implicit session =>

    val clustersQuery = for {
      _sess <- clusteringSessions if _sess.clusteringSessionId === sessionId
      _clust <- ClusteringSessionClusterTable.clusteringSessionClusters if _clust.clusteringSessionId === _sess.clusteringSessionId
      _labType <- LabelTable.labelTypes if _labType.labelTypeId === _clust.labelTypeId
      if !_clust.lat.isEmpty && !_clust.lng.isEmpty
    } yield (
      _sess.clusteringSessionId,
      _clust.clusteringSessionClusterId,
      _labType.labelType,
      _clust.lat.get,
      _clust.lng.get,
      _sess.clusteringThreshold
    )
    val clusters: List[Issue] = clustersQuery.list.map(x => Issue.tupled(x))

    val labelsQuery = for {
      _sess <- clusteringSessions if _sess.clusteringSessionId === sessionId
      _clust <- ClusteringSessionClusterTable.clusteringSessionClusters if _clust.clusteringSessionId === _sess.clusteringSessionId
      _clustLab <- ClusteringSessionLabelTable.clusteringSessionLabels if _clustLab.clusteringSessionClusterId === _clust.clusteringSessionClusterId
      _origClust <- ClusteringSessionClusterTable.clusteringSessionClusters if _origClust.clusteringSessionClusterId === _clustLab.originatingClusterId
      _origSess <- ClusteringSessionTable.clusteringSessions if _origSess.clusteringSessionId === _origClust.clusteringSessionId
      _labType <- LabelTable.labelTypes if _labType.labelTypeId === _origClust.labelTypeId
      if !_origClust.lat.isEmpty && !_origClust.lng.isEmpty
    } yield (
      _origClust.clusteringSessionId,
      _clust.clusteringSessionClusterId,
      _labType.labelType,
      _origSess.turkerId.get,
//      "turker",
      _origSess.turkerId.get,
      _origClust.lat.get,
      _origClust.lng.get,
      _origSess.clusteringThreshold
    )
    val labels: List[ClusteredLabel] = labelsQuery.list.map(x => ClusteredLabel.tupled(x))

    (clusters, labels)
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