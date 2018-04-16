package models.clustering_session

/**
  * Created by misaugstad on 12/10/17.
  */

import models.daos.slick.DBTableDefinitions.{DBUser, UserTable}
import models.utils.MyPostgresDriver.simple._
import play.api.Play.current

import scala.slick.lifted.{ForeignKeyQuery, ProvenShape}
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

  def save(newSess: UserClusteringSession): Int = db.withTransaction { implicit session =>
    val newId: Int = (userClusteringSessions returning userClusteringSessions.map(_.userClusteringSessionId)) += newSess
    newId
  }
}
