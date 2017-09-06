package models.amt

import java.sql.Timestamp

import models.audit.AuditTaskTable
import models.clustering_session.ClusteringSessionTable.db
import models.clustering_session.LabelToCluster
import models.label.{LabelTable, ProblemTemporarinessTable}
import models.route.{Route, RouteTable}
import models.turker.{Turker, TurkerTable}
import models.utils.MyPostgresDriver.simple._
import play.api.Play.current

import scala.slick.lifted.ForeignKeyQuery

case class AMTAssignment(amtAssignmentId: Int, hitId: String, assignmentId: String,
                         assignmentStart: Timestamp, assignmentEnd: Option[Timestamp],
                         turkerId: String, conditionId: Int, routeId: Option[Int], completed: Boolean)

/**
 *
 */
class AMTAssignmentTable(tag: Tag) extends Table[AMTAssignment](tag, Some("sidewalk"), "amt_assignment") {
  def amtAssignmentId = column[Int]("amt_assignment_id", O.PrimaryKey, O.AutoInc)
  def hitId = column[String]("hit_id", O.NotNull)
  def assignmentId = column[String]("assignment_id", O.NotNull)
  def assignmentStart = column[Timestamp]("assignment_start", O.NotNull)
  def assignmentEnd = column[Option[Timestamp]]("assignment_end", O.Nullable)
  def turkerId = column[String]("turker_id", O.NotNull)
  def conditionId = column[Int]("condition_id", O.NotNull)
  def routeId = column[Option[Int]]("route_id", O.NotNull)
  def completed = column[Boolean]("completed", O.NotNull)

  def * = (amtAssignmentId, hitId, assignmentId, assignmentStart, assignmentEnd, turkerId, conditionId, routeId,
    completed) <> ((AMTAssignment.apply _).tupled, AMTAssignment.unapply)

  def route: ForeignKeyQuery[RouteTable, Route] =
    foreignKey("amt_assignment_route_id_fkey", routeId, TableQuery[RouteTable])(_.routeId)

  def condition: ForeignKeyQuery[AMTConditionTable, AMTCondition] =
    foreignKey("amt_assignment_condition_id_fkey", conditionId, TableQuery[AMTConditionTable])(_.amtConditionId)

//  def turker: ForeignKeyQuery[TurkerTable, Turker] =
//    foreignKey("amt_assignment_turker_id_fkey", turkerId, TableQuery[TurkerTable])(_.turkerId)
}

/**
 * Data access object for the AMTAssignment table
 */
object AMTAssignmentTable {
  val db = play.api.db.slick.DB
  val amtAssignments = TableQuery[AMTAssignmentTable]

  def save(asg: AMTAssignment): Int = db.withTransaction { implicit session =>
    val asgId: Int =
      (amtAssignments returning amtAssignments.map(_.amtAssignmentId)) += asg
    asgId
  }

  /**
    * Update the `completed` column of the specified assignment row.
    * Reference: http://slick.lightbend.com/doc/2.0.0/queries.html#updating
    *
    * @param amtAssignmentId AMT Assignment id
    * @param completed A completed flag
    * @return
    */
  def updateCompleted(amtAssignmentId: Int, completed: Boolean) = db.withTransaction { implicit session =>
    val q = for { asg <- amtAssignments if asg.amtAssignmentId === amtAssignmentId } yield asg.completed
    q.update(completed)
  }

  def getCountOfCompletedByTurkerId(turkerId: String): Int = db.withTransaction { implicit session =>
    val conditionId = TurkerTable.getConditionIdByTurkerId(turkerId).get
    amtAssignments.filter(x => x.turkerId === turkerId && x.completed === true && x.conditionId === conditionId).length.run
  }

  def getHITRouteIds(hitId: String): List[Int] = db.withTransaction { implicit session =>
    amtAssignments.filter(_.hitId === hitId).map(_.routeId.getOrElse(-1)).run.distinct.toList
  }

  /**
    * Returns labels that were placed by turkers in the specified condition.
    *
    * This only labels from turkers who have completed all of the routes in that condition. It also excludes turkerIds
    * associated with researchers.
    *
    * @param conditionId
    * @return
    */
  def getTurkerLabelsByCondition(conditionId: Int): List[LabelToCluster] = db.withSession { implicit session =>
    // figure out number of routes in the condition
    val nRoutes: Int = (for {
      _condition <- AMTConditionTable.amtConditions if _condition.amtConditionId === conditionId
      _routes <- AMTVolunteerRouteTable.amtVolunteerRoutes if _routes.volunteerId === _condition.volunteerId
    } yield _routes).list.length

    // find all (non-researcher) turkers who have completed all of the routes
    // TODO create list of researcher turker ids to exclude
    val turkersToExclude: List[String] = List("a", "Abc")
    val completedAsmts = AMTAssignmentTable.amtAssignments.filter(asmt => asmt.completed && asmt.conditionId === conditionId)
    val routeCounts = completedAsmts.groupBy(_.turkerId).map { case (id, group) => (id, group.length) }
    val turkers: List[String] = routeCounts.filter(_._2 === nRoutes).filterNot(_._1 inSet turkersToExclude).map(_._1).list

    val asmts = completedAsmts.filter(_.turkerId inSet turkers)
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
    * Update the `assignment_end` timestamp column of the specified amt_assignment row
    *
    * @param amtAssignmentId
    * @param timestamp
    * @return
    */
  def updateAssignmentEnd(amtAssignmentId: Int, timestamp: Timestamp) = db.withTransaction { implicit session =>
    val q = for { asg <- amtAssignments if asg.amtAssignmentId === amtAssignmentId } yield asg.assignmentEnd
    q.update(Some(timestamp))
  }
}

