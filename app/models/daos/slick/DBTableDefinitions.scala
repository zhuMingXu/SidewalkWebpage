package models.daos.slick

import models.audit.{AuditTaskEnvironmentTable, AuditTaskTable}
import models.label.LabelTable
import models.street.StreetEdgeTable
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

    /**
      * Gets the list of all registered user_ids from "good" users, i.e., they have labeling freq. above our threshold.
      *
      * @return
      */
    def getHighLabelingFrequencyRegisteredUserIds: List[String] = db.withTransaction { implicit session =>
      val LABEL_PER_METER_THRESHOLD: Float = 0.0375.toFloat

      val onboardingPanos = List("bdmGHJkiSgmO7_80SnbzXw", "OgLbmLAuC4urfE5o7GP_JQ", "stxXyCKAbd73DmkM2vsIHA")
      val nonOnboardingLabels = LabelTable.labelsWithoutDeleted.filterNot(_.gsvPanoramaId inSet onboardingPanos)

      val streetDist = StreetEdgeTable.streetEdges.map(edge => (edge.streetEdgeId, edge.geom.transform(26918).length))

      // Gets all tasks completed by registered users, groups by user_id, & sums over the distances of the street edges.
      val regAuditedDists = (for {
        _user <- slickUsers if _user.username =!= "anonymous"
        _task <- AuditTaskTable.completedTasks if _task.userId === _user.userId
        _dist <- streetDist if _task.streetEdgeId === _dist._1
      } yield (_user.userId, _dist._2)).groupBy(_._1).map(x => (x._1, x._2.map(_._2).sum))

      // Gets all registered user tasks, groups by user_id, and counts number of labels places (incl. incomplete tasks).
      val regLabelCounts = (for {
        _user <- slickUsers if _user.username =!= "anonymous"
        _task <- AuditTaskTable.auditTasks if _task.userId === _user.userId
        _lab  <- nonOnboardingLabels if _task.auditTaskId === _lab.auditTaskId
      } yield (_user.userId, _lab.labelId)).groupBy(_._1).map(x => (x._1, x._2.length)) // SELECT user_id, COUNT(*)

      // Finally, compute labeling frequency and take only the users who are above label per meter threshold.
      // SELECT user_id WHERE is_good_user == TRUE (where is_good_user = label_count/distance_audited > threshold)
      regAuditedDists
        .leftJoin(regLabelCounts).on(_._1 === _._1)
        .filter { case (d, c) => c._2.ifNull(0.asColumnOf[Int]).asColumnOf[Float] / d._2 > LABEL_PER_METER_THRESHOLD }
        .map(_._1._1)
        .list
    }

    /**
      * Gets the list of all anon ip_adresses from "good" users, i.e., they have a labeling freq. above our threshold.
      *
      * @return
      */
    def getHighLabelingFrequencyAnonUserIps: List[String] = db.withTransaction { implicit session =>
      val LABEL_PER_METER_THRESHOLD: Float = 0.0375.toFloat

      val onboardingPanos = List("bdmGHJkiSgmO7_80SnbzXw", "OgLbmLAuC4urfE5o7GP_JQ", "stxXyCKAbd73DmkM2vsIHA")
      val nonOnboardingLabels = LabelTable.labelsWithoutDeleted.filterNot(_.gsvPanoramaId inSet onboardingPanos)

      val streetDist = StreetEdgeTable.streetEdges.map(edge => (edge.streetEdgeId, edge.geom.transform(26918).length))

      // Gets the tasks performed by anonymous users, along with ip address; need to select distinct b/c there can be
      // multiple audit_task_environment entries for a single task
      val anonTasks = (for {
        _user <- slickUsers if _user.username === "anonymous"
        _task <- AuditTaskTable.completedTasks if _user.userId === _task.userId
        _env <- AuditTaskEnvironmentTable.auditTaskEnvironments if _task.auditTaskId === _env.auditTaskId
        if !_env.ipAddress.isEmpty
      } yield (_env.ipAddress, _task.auditTaskId, _task.streetEdgeId)).groupBy(x => x).map(_._1) // SELECT DISTINCT

      // Takes all tasks completed by anon users, groups by ip_address, and sums over the distances of the street edges.
      val anonAuditedDists = anonTasks.innerJoin(streetDist).on(_._3 === _._1)
        .map { case (_task, _dist) => (_task._1, _dist._2) }   // SELECT ip_address, street_distance
        .groupBy(_._1)                                         // GROUP BY ip_address
        .map { case (ip, group) => (ip, group.map(_._2).sum) } // SELECT ip_address, SUM(street_distance)

      // Gets all anon user tasks, groups by ip_address, and counts number of labels places (incl. incomplete tasks).
      val anonLabelCounts = (for {
        _user <- slickUsers if _user.username === "anonymous"
        _task <- AuditTaskTable.auditTasks if _user.userId === _task.userId
        _env <- AuditTaskEnvironmentTable.auditTaskEnvironments if _task.auditTaskId === _env.auditTaskId
        if !_env.ipAddress.isEmpty
        _lab <- nonOnboardingLabels if _task.auditTaskId === _lab.auditTaskId
      } yield (_env.ipAddress, _lab.labelId))
        .groupBy(x => x).map(_._1) // select distinct
        .groupBy(_._1).map(x => (x._1, x._2.length)) // SELECT ip_address, COUNT(*)

      // Finally, compute labeling frequency and take only the users who are above label per meter threshold.
      // SELECT ip_address WHERE is_good_user == TRUE (where is_good_user = label_count/distance_audited > threshold)
      anonAuditedDists
        .leftJoin(anonLabelCounts).on(_._1 === _._1)
        .filter { case (d, c) => c._2.ifNull(0.asColumnOf[Int]).asColumnOf[Float] / d._2 > LABEL_PER_METER_THRESHOLD }
        .map(_._1._1)
        .list.flatten
    }

    def count: Int = db.withTransaction { implicit session =>
      val users = slickUsers.list
      users.length
    }
  }
}