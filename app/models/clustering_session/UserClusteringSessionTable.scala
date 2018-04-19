package models.clustering_session

/**
  * Created by misaugstad on 12/10/17.
  */

import models.daos.slick.DBTableDefinitions.{DBUser, UserTable}
import models.utils.MyPostgresDriver.simple._
import play.api.Play.current

import scala.slick.lifted.{ForeignKeyQuery, ProvenShape}
import scala.slick.jdbc.{StaticQuery => Q}
import scala.language.postfixOps

case class UserClusteringSession(userClusteringSessionId: Int,
                                 isRegistered: Boolean,
                                 userId: Option[String],
                                 ipAddress: Option[String],
                                 clusteringSessionId: Int)


class UserClusteringSessionTable(tag: Tag) extends Table[UserClusteringSession](tag, Some("sidewalk"), "user_clustering_session") {
  def userClusteringSessionId: Column[Int] = column[Int]("user_clustering_session_id", O.NotNull, O.PrimaryKey, O.AutoInc)
  def isRegistered: Column[Boolean] = column[Boolean]("is_registered", O.NotNull)
  def userId: Column[Option[String]] = column[Option[String]]("user_id")
  def ipAddress: Column[Option[String]] = column[Option[String]]("ip_address")
  def clusteringSessionId: Column[Int] = column[Int]("clustering_session_id", O.NotNull)

  def * : ProvenShape[UserClusteringSession] = (userClusteringSessionId, isRegistered, userId, ipAddress, clusteringSessionId) <>
    ((UserClusteringSession.apply _).tupled, UserClusteringSession.unapply)

  def clusteringSession: ForeignKeyQuery[ClusteringSessionTable, ClusteringSession] =
    foreignKey("user_clustering_session_clustering_session_id_fkey", clusteringSessionId, TableQuery[ClusteringSessionTable])(_.clusteringSessionId)

  def user: ForeignKeyQuery[UserTable, DBUser] =
    foreignKey("user_clustering_session_user_id_fkey", userId, TableQuery[UserTable])(_.userId)
}

/**
  * Data access object for the UserClusteringSessionTable table
  */
object UserClusteringSessionTable {
  val db = play.api.db.slick.DB
  val userClusteringSessions = TableQuery[UserClusteringSessionTable]
  val users = TableQuery[UserTable]
  val clusteringSessions = TableQuery[ClusteringSessionTable]


  def getSessionIdsForUser(userId: String, isRegistered: Boolean): List[Int] = db.withTransaction { implicit session =>
    if (isRegistered) userClusteringSessions.filter(_.userId === userId).map(_.clusteringSessionId).list
    else              userClusteringSessions.filter(_.ipAddress === userId).map(_.clusteringSessionId).list
  }

  def getAllSessions: List[UserClusteringSession] = db.withTransaction { implicit session =>
    userClusteringSessions.list
  }
  
  /**
    * Gets all clusters from single-user clustering that are in this region, outputs in format needed for clustering.
    *
    * @param regionId
    * @return
    */
  def getClusteredLabelsInRegion(regionId: Int): List[LabelToCluster] = db.withTransaction { implicit session =>
    import models.clustering_session.ClusteringSessionTable.labelToClusterConverter
    val clustersInRegionQuery = Q.query[Int, LabelToCluster](
      """SELECT clustering_session_cluster.clustering_session_cluster_id,
        |        label_type.label_type,
        |        clustering_session_cluster.lat, clustering_session_cluster.lng,
        |        clustering_session_cluster.severity,
        |        clustering_session_cluster.temporary,
        |        clustering_session.user_id
        |FROM user_clustering_session
        |INNER JOIN clustering_session
        |    ON user_clustering_session.clustering_session_id = clustering_session.clustering_session_id
        |INNER JOIN clustering_session_cluster
        |    ON clustering_session.clustering_session_id = clustering_session_cluster.clustering_session_id
        |INNER JOIN label_type ON clustering_session_cluster.label_type_id = label_type.label_type_id
        |INNER JOIN region
        |    ON st_intersects
        |    (
        |        st_setsrid(st_makepoint(clustering_session_cluster.lng, clustering_session_cluster.lat), 4326),
        |        region.geom
        |    )
        |WHERE region.region_id = ?;
      """.stripMargin
    )
    clustersInRegionQuery(regionId).list
  }

  def truncateTable() = db.withTransaction { implicit session =>
    Q.updateNA("TRUNCATE TABLE user_clustering_session").execute
  }

  def save(newSess: UserClusteringSession): Int = db.withTransaction { implicit session =>
    val newId: Int = (userClusteringSessions returning userClusteringSessions.map(_.userClusteringSessionId)) += newSess
    newId
  }
}
