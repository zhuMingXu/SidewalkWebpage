package models.clustering_session

/**
  * Created by misaugstad on 12/10/17.
  */

import models.label.{LabelType, LabelTypeTable}
import models.utils.MyPostgresDriver.simple._
import play.api.Play.current

import scala.slick.lifted.ForeignKeyQuery
import scala.language.postfixOps

case class ClusteringSessionLabelTypeThreshold(clusteringSessionLabelTypeThresholdId: Int,
                                               clusteringSessionId: Int,
                                               labelTypeId: Int,
                                               clusteringThreshold: Float)

/**
  *
  */
class ClusteringSessionLabelTypeThresholdTable(tag: Tag) extends Table[ClusteringSessionLabelTypeThreshold](tag, Some("sidewalk"), "amt_volunteer_route") {
  def clusteringSessionLabelTypeThresholdId = column[Int]("clustering_session_label_type_threshold_id", O.NotNull, O.PrimaryKey)
  def clusteringSessionId = column[Int]("clustering_session_id", O.NotNull)
  def labelTypeId = column[Int]("label_type_id", O.NotNull)
  def clusteringThreshold = column[Float]("clustering_threshold", O.NotNull)

  def * = (clusteringSessionLabelTypeThresholdId, clusteringSessionId, labelTypeId, clusteringThreshold) <>
    ((ClusteringSessionLabelTypeThreshold.apply _).tupled, ClusteringSessionLabelTypeThreshold.unapply)

  def clusteringSession: ForeignKeyQuery[ClusteringSessionTable, ClusteringSession] =
    foreignKey("clustering_session_label_type_threshold_clustering_session_id_fkey", clusteringSessionId, TableQuery[ClusteringSessionTable])(_.clusteringSessionId)
  def labelType: ForeignKeyQuery[LabelTypeTable, LabelType] =
    foreignKey("clustering_session_label_type_threshold_clustering_session_id_fkey", labelTypeId, TableQuery[LabelTypeTable])(_.labelTypeId)

}

/**
  * Data access object for the AMTVolunteerRoute table
  */
object AMTVolunteerRouteTable {
  val db = play.api.db.slick.DB
  val clusteringSessionLabelTypeThresholds = TableQuery[ClusteringSessionLabelTypeThresholdTable]
  val clusteringSessions = TableQuery[ClusteringSessionTable]
  val labelTypes = TableQuery[LabelTypeTable]


  def getThresholdsByClusteringSessionId(sessionId: Int): List[ClusteringSessionLabelTypeThreshold] = db.withTransaction { implicit session =>
    clusteringSessionLabelTypeThresholds.filter(_.clusteringSessionId === sessionId).list
  }

  def getThresholdsByClusteringSessionIds(sessionIds: List[Int]): List[ClusteringSessionLabelTypeThreshold] = db.withTransaction { implicit session =>
    clusteringSessionLabelTypeThresholds.filter(_.clusteringSessionId inSet sessionIds).list
  }

  def save(newThresh: ClusteringSessionLabelTypeThreshold): Int = db.withTransaction { implicit session =>
    val newId: Int =
      (clusteringSessionLabelTypeThresholds returning clusteringSessionLabelTypeThresholds.map(_.clusteringSessionLabelTypeThresholdId)) += newThresh
    newId
  }
}

