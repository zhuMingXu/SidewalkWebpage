package models.daos.slick

import models.audit.{AuditTaskEnvironmentTable, AuditTaskTable}
import models.label.LabelTable
import models.utils.MyPostgresDriver.simple._

object DBTableDefinitions {

  case class DBUser (userId: String, username: String, email: String )

  class UserTable(tag: Tag) extends Table[DBUser](tag, Some("sidewalk"), "user") {
    def userId = column[String]("user_id", O.PrimaryKey)
    def username = column[String]("username")
    def email = column[String]("email")
    def * = (userId, username, email) <> (DBUser.tupled, DBUser.unapply)
  }

  case class DBLoginInfo (id: Option[Long], providerID: String, providerKey: String )

  class LoginInfos(tag: Tag) extends Table[DBLoginInfo](tag, Some("sidewalk"), "login_info") {
    def loginInfoId = column[Long]("login_info_id", O.PrimaryKey, O.AutoInc)
    def providerID = column[String]("provider_id")
    def providerKey = column[String]("provider_key")
    def * = (loginInfoId.?, providerID, providerKey) <> (DBLoginInfo.tupled, DBLoginInfo.unapply)
  }

  case class DBUserLoginInfo (userID: String, loginInfoId: Long)

  class UserLoginInfoTable(tag: Tag) extends Table[DBUserLoginInfo](tag, Some("sidewalk"), "user_login_info") {
    def userID = column[String]("user_id", O.NotNull)
    def loginInfoId = column[Long]("login_info_id", O.NotNull)
    def * = (userID, loginInfoId) <> (DBUserLoginInfo.tupled, DBUserLoginInfo.unapply)
  }

  case class DBPasswordInfo (hasher: String, password: String, salt: Option[String], loginInfoId: Long)

  class PasswordInfoTable(tag: Tag) extends Table[DBPasswordInfo](tag, Some("sidewalk"), "user_password_info") {
    def hasher = column[String]("hasher")
    def password = column[String]("password")
    def salt = column[Option[String]]("salt")
    def loginInfoId = column[Long]("login_info_id")
    def * = (hasher, password, salt, loginInfoId) <> (DBPasswordInfo.tupled, DBPasswordInfo.unapply)
  }


  val slickUsers = TableQuery[UserTable]
  val slickLoginInfos = TableQuery[LoginInfos]
  val slickUserLoginInfos = TableQuery[UserLoginInfoTable]
  val slickPasswordInfos = TableQuery[PasswordInfoTable]

  object UserTable {
    import play.api.Play.current

    val db = play.api.db.slick.DB
    def find(username: String): Option[DBUser] = db.withTransaction { implicit session =>
      slickUsers.filter(_.username === username).firstOption match {
        case Some(user) => Some(user)
        case None => None
      }
    }
    def findEmail(email: String): Option[DBUser] = db.withTransaction { implicit session =>
      slickUsers.filter(_.email === email).firstOption match {
        case Some(user) => Some(user)
        case None => None
      }
    }

    def getAllUserIds: List[String] = db.withTransaction { implicit session =>
      slickUsers.map(_.userId).list
    }

    def getHighLabelingFrequencyRegisteredUserIds: List[String] = db.withTransaction { implicit session =>

      val onboardingPanos = List("bdmGHJkiSgmO7_80SnbzXw", "OgLbmLAuC4urfE5o7GP_JQ", "stxXyCKAbd73DmkM2vsIHA")
      val nonOnboardingLabs = LabelTable.labelsWithoutDeleted.filterNot(_.gsvPanoramaId inSet onboardingPanos)

      slickUsers
        .filterNot(_.username === "anonymous")  // Take only registered users
        .innerJoin(AuditTaskTable.auditTasks).on(_.userId === _.userId)
        .innerJoin(nonOnboardingLabs).on(_._2.auditTaskId === _.auditTaskId)
        .groupBy(_._1._2.userId)
        .map { case (userId, group) => (userId, group.length) }
        .filter(_._2 < 50)
        .map(_._1)
        .list.take(10)
    }

    def getHighLabelingFrequencyAnonUserIps: List[String] = db.withTransaction { implicit session =>

      val onboardingPanos = List("bdmGHJkiSgmO7_80SnbzXw", "OgLbmLAuC4urfE5o7GP_JQ", "stxXyCKAbd73DmkM2vsIHA")
      val nonOnboardingLabs = LabelTable.labelsWithoutDeleted.filterNot(_.gsvPanoramaId inSet onboardingPanos)

      slickUsers
        .filter(_.username === "anonymous")  // Take only anonymous users
        .innerJoin(AuditTaskTable.auditTasks).on(_.userId === _.userId)
        .innerJoin(AuditTaskEnvironmentTable.auditTaskEnvironments).on(_._2.auditTaskId === _.auditTaskId)
        .innerJoin(nonOnboardingLabs).on(_._2.auditTaskId === _.auditTaskId)
        .groupBy(_._1._2.ipAddress)
        .map { case (ip, group) => (ip, group.length) }
        .filter(_._2 < 50)
        .map(_._1)
        .list.flatten.take(10)
    }

    def count: Int = db.withTransaction { implicit session =>
      val users = slickUsers.list
      users.length
    }
  }
}