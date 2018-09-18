package models.clustering_session

/**
  * Created by hmaddali on 7/26/17.
  */

import models.utils.MyPostgresDriver.simple._
import play.api.Play.current

import scala.slick.lifted.ForeignKeyQuery

case class ClusteringSessionCluster(clusteringSessionClusterId: Int, clusteringSessionId: Int,
                                    labelTypeId: Option[Int], lat: Option[Float], lng: Option[Float],
                                    severity: Option[Int], temporary: Option[Boolean])

/**
  *
  */
class ClusteringSessionClusterTable(tag: Tag) extends Table[ClusteringSessionCluster](tag, Some("sidewalk"), "clustering_session_cluster") {
  def clusteringSessionClusterId = column[Int]("clustering_session_cluster_id", O.NotNull, O.PrimaryKey, O.AutoInc)
  def clusteringSessionId = column[Int]("clustering_session_id", O.NotNull)
  def labelTypeId = column[Option[Int]]("label_type_id", O.Nullable)
  def lat = column[Option[Float]]("lat", O.Nullable)
  def lng = column[Option[Float]]("lng", O.Nullable)
  def severity = column[Option[Int]]("severity", O.Nullable)
  def temporary = column[Option[Boolean]]("temporary", O.Nullable)

  def * = (clusteringSessionClusterId, clusteringSessionId, labelTypeId, lat, lng, severity, temporary) <> ((ClusteringSessionCluster.apply _).tupled, ClusteringSessionCluster.unapply)

  def clusteringSession: ForeignKeyQuery[ClusteringSessionTable, ClusteringSession] =
    foreignKey("clustering_session_cluster_cluster_session_id_fkey", clusteringSessionId, TableQuery[ClusteringSessionTable])(_.clusteringSessionId)

}

/**
  * Data access object for the Clustering Session Cluster table
  */
object ClusteringSessionClusterTable{
  val db = play.api.db.slick.DB
  val clusteringSessionClusters = TableQuery[ClusteringSessionClusterTable]

  def getClusteringSessionCluster(clusteringSessionClusterId: Int): Option[ClusteringSessionCluster] = db.withSession { implicit session =>
    val clusteringSessionCluster = clusteringSessionClusters.filter(_.clusteringSessionClusterId === clusteringSessionClusterId).list
    clusteringSessionCluster.headOption
  }

  def all: List[ClusteringSessionCluster] = db.withSession { implicit session =>
    clusteringSessionClusters.list
  }

  def getSpecificClusteringSessionClusters(clusteringSessionId: Int): List[ClusteringSessionCluster] = db.withSession { implicit session =>
    clusteringSessionClusters.filter(_.clusteringSessionId === clusteringSessionId).list
  }

  def save(sessionId: Int): Int = db.withTransaction { implicit session =>
    save(ClusteringSessionCluster(0, sessionId, labelTypeId = None, lat = None, lng = None, severity = None, temporary = None))
  }

  def save(clusteringSessionCluster: ClusteringSessionCluster): Int = db.withTransaction { implicit session =>
    val scId: Int =
      (clusteringSessionClusters returning clusteringSessionClusters.map(_.clusteringSessionClusterId)) += clusteringSessionCluster
    scId
  }

}