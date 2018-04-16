package models.clustering_session

/**
  * Created by misaugstad on 12/10/17.
  */

import models.utils.MyPostgresDriver.simple._
import play.api.Play.current

import scala.slick.lifted.{ForeignKeyQuery, ProvenShape}
import scala.language.postfixOps

case class IssueClusteringSession(issueClusteringSessionId: Int, clusteringSessionId: Int)


class IssueClusteringSessionTable(tag: Tag) extends Table[IssueClusteringSession](tag, Some("sidewalk"), "issue_clustering_session") {
  def issueClusteringSessionLabelId: Column[Int] = column[Int]("issue_clustering_session_id", O.NotNull, O.PrimaryKey, O.AutoInc)
  def clusteringSessionId: Column[Int] = column[Int]("clustering_session_id", O.NotNull)

  def * : ProvenShape[IssueClusteringSession] = (clusteringSessionLabelTypeThresholdId, clusteringSessionId) <>
    ((IssueClusteringSession.apply _).tupled, IssueClusteringSession.unapply)

  def clusteringSession: ForeignKeyQuery[ClusteringSessionTable, ClusteringSession] =
    foreignKey("issue_clustering_session_clustering_session_id_fkey", clusteringSessionId, TableQuery[ClusteringSessionTable])(_.clusteringSessionId)
}

/**
  * Data access object for the ClusteringSessionLabelTypeThreshold table
  */
object IssueClusteringSessionTable {
  val db = play.api.db.slick.DB
  val issueClusteringSessions = TableQuery[IssueClusteringSessionTable]
  val clusteringSessions = TableQuery[ClusteringSessionTable]

  def getAllSessions: List[IssueClusteringSession] = db.withTransaction { implicit session =>
    issueClusteringSessions.list
  }

  def save(newSess: IssueClusteringSession): Int = db.withTransaction { implicit session =>
    val newId: Int = (issueClusteringSessions returning issueClusteringSessions.map(_.issueClusteringSessionId)) += newSess
    newId
  }
}
