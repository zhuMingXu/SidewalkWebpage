package models.mission

import java.sql.Timestamp
import java.util.UUID

import models.daos.slick.DBTableDefinitions.UserTable
import models.utils.MyPostgresDriver.simple._
import models.region._
import models.audit._
import models.daos.UserDAOImpl.{anonIps, auditTaskEnvironmentTable, auditTaskTable}
import models.label.LabelTable
import models.label.LabelTable.labels
import models.street.StreetEdgeTable

import scala.slick.lifted.ForeignKeyQuery
import scala.slick.jdbc.{GetResult, StaticQuery => Q}
import play.api.Play.current
import play.api.libs.json.{JsObject, Json}



case class RegionalMission(missionId: Int, regionId: Option[Int], regionName: Option[String], label: String, level: Int, distance: Option[Double], distance_ft: Option[Double], distance_mi: Option[Double], coverage: Option[Double])
case class Mission(missionId: Int, regionId: Option[Int], label: String, level: Int, distance: Option[Double], distance_ft: Option[Double], distance_mi: Option[Double], coverage: Option[Double], deleted: Boolean) {
  def completed(status: MissionStatus): Boolean = label match {
    case "initial-mission" =>
      if (this.distance.getOrElse(Double.PositiveInfinity) < status.currentRegionDistance) true else false
    case "distance-mission" =>
      if (this.distance.getOrElse(Double.PositiveInfinity) < status.currentRegionDistance) true else false
    case "area-coverage-mission" =>
      if (this.distance.getOrElse(Double.PositiveInfinity) < status.currentRegionDistance) true else false
    case "neighborhood-coverage-mission" =>
      if (this.level <= status.totalNumberOfRegionsCompleted) true else false
    case _ => false
  }

  def toJSON: JsObject = {
    Json.obj("mission_id" -> missionId, "region_id" -> regionId, "label" -> label, "level" -> level, "distance" -> distance, "coverage" -> coverage)
  }
}
case class MissionStatus(currentRegionDistance: Double, currentRegionCoverage: Double, totalNumberOfRegionsCompleted: Int)

class MissionTable(tag: Tag) extends Table[Mission](tag, Some("sidewalk"), "mission") {
  def missionId = column[Int]("mission_id", O.PrimaryKey, O.AutoInc)
  def regionId = column[Option[Int]]("region_id", O.Nullable)
  def label = column[String]("label", O.NotNull)
  def level = column[Int]("level", O.NotNull)
  def distance = column[Option[Double]]("distance", O.Nullable)
  def distance_ft = column[Option[Double]]("distance_ft", O.Nullable)
  def distance_mi = column[Option[Double]]("distance_mi", O.Nullable)
  def coverage = column[Option[Double]]("coverage", O.Nullable)
  def deleted = column[Boolean]("deleted", O.NotNull)

  def * = (missionId, regionId, label, level, distance, distance_ft, distance_mi, coverage, deleted) <> ((Mission.apply _).tupled, Mission.unapply)

  def region: ForeignKeyQuery[RegionTable, Region] =
    foreignKey("mission_region_id_fkey", regionId, TableQuery[RegionTable])(_.regionId)
}

object MissionTable {
  val db = play.api.db.slick.DB
  val missions = TableQuery[MissionTable]
  val missionUsers = TableQuery[MissionUserTable]
  val users = TableQuery[UserTable]
  val regionProperties = TableQuery[RegionPropertyTable]
  val regions = TableQuery[RegionTable]
  val neighborhoods = regions.filter(_.deleted === false).filter(_.regionTypeId === 2)

  val missionsWithoutDeleted = missions.filter(_.deleted === false)

  // Jon, Kotaro, Mikey, Soheil, Manaswi, Teja, Aditya, Chirag, Sage, Anthony, Ryan H, Ladan, Ji Hyuk Bae, Maria Furman,
  // Zadorozhnyy, Alexander Zhang, Zachary Lawrence, test5, Manaswi again, test4, test6, test7, test8, test_0830
  val researcherIds: List[String] = List("49787727-e427-4835-a153-9af6a83d1ed1", "25b85b51-574b-436e-a9c4-339eef879e78",
    "9efaca05-53bb-492e-83ab-2b47219ee863", "5473abc6-38fc-4807-a515-e44cdfb92ca2", "0c6cb637-05b7-4759-afb2-b0a25b615597",
    "9c828571-eb9d-4723-9e8d-2c00289a6f6a", "6acde11f-d9a2-4415-b73e-137f28eaa4ab", "0082be2e-c664-4c05-9881-447924880e2e",
    "ae8fc440-b465-4a45-ab49-1964a7f1dcee", "c4ba8834-4722-4ee1-8f71-4e3fe9af38eb", "41804389-8f0e-46b1-882c-477e060dbe95",
    "d8862038-e4dd-48a4-a6d0-69042d9e247a", "43bd82ab-bc7d-4be7-a637-99c92f566ba5", "0bfed786-ce24-43f9-9c58-084ae82ad175",
    "b65c0864-7c3a-4ba7-953b-50743a2634f6", "b6049113-7e7a-4421-a966-887266200d72", "395abc5a-14ea-443c-92f8-85e87fa002be",
    "a6611125-51d0-41d1-9868-befcf523e131", "1dc2f78e-f722-4450-b14e-b21b232ecdef", "ee570f03-7bca-471e-a0dc-e7924dac95a4",
    "1dc2f78e-f722-4450-b14e-b21b232ecdef", "23fce322-9f64-4e95-90fc-7141f755b2a1", "c846ef76-39c1-4a53-841c-6588edaac09b",
    "74b56671-c9b0-4052-956e-02083cbb5091", "fe724938-797a-48af-84e9-66b6b86b6245")

  val auditTaskTable = TableQuery[AuditTaskTable]
  val completedAudits = auditTaskTable.filter(_.completed === true)
  val nonResearcherCompletedAudits = completedAudits.filterNot(_.userId inSet researcherIds)
  val usersWithAnAudit = nonResearcherCompletedAudits.groupBy(x => x.userId).map {
    case (userId, group) => userId
  }
  val allUsersWithAnAudit = completedAudits.groupBy(x => x.userId).map {
    case (userId, group) => userId
  }

  val auditTaskEnvironmentTable = TableQuery[AuditTaskEnvironmentTable]

  val labels = TableQuery[LabelTable]
  val labelsWithoutDeleted = labels.filter(_.deleted === false)

  val streetEdges = TableQuery[StreetEdgeTable].filter(_.deleted === false)

  val anonId = "97760883-8ef0-4309-9a5e-0c086ef27573"
  val anonUsers = for {
    (_ate, _at) <- auditTaskEnvironmentTable.innerJoin(auditTaskTable).on(_.auditTaskId === _.auditTaskId)
    if _at.userId === anonId && _at.completed === true
  } yield (_ate.ipAddress, _ate.auditTaskId, _at.taskStart, _at.taskEnd)

val anonIps = anonUsers.groupBy(_._1).map{case(ip,group)=>ip}

  implicit val missionConverter = GetResult[Mission](r => {
    // missionId: Int, regionId: Option[Int], label: String, level: Int, distance: Option[Double], distance_ft: Option[Double], distance_mi: Option[Double], coverage: Option[Double], deleted: Boolean
    // Int, Option[Int], String, Int, Option[Double], Option[Double], Option[Double], Option[Double], Boolean
    val missionId: Int = r.nextInt
    val regionId: Option[Int] = r.nextIntOption
    val label: String = r.nextString
    val level: Int = r.nextInt
    val distance: Option[Double] = r.nextDoubleOption
    val distance_ft: Option[Double] = r.nextDoubleOption
    val distance_mi: Option[Double] = r.nextDoubleOption
    val coverage: Option[Double] = r.nextDoubleOption
    val deleted: Boolean = r.nextBoolean
    Mission(missionId, regionId, label, level, distance, distance_ft, distance_mi, coverage, deleted)
  })

  case class MissionCompletedByAUser(username: String, label: String, level: Int, distance_m: Option[Double], distance_ft: Option[Double], distance_mi: Option[Double])

  /**
    * Count the number of missions completed by a user
    *
    * @param userId
    * @return
    */
  def countCompletedMissionsByUserId(userId: UUID): Int = db.withTransaction { implicit session =>
    val completedMissions = selectCompletedMissionsByAUser(userId)
    val missionsWithoutOnboarding = completedMissions.filter(_.label != "onboarding")
    missionsWithoutOnboarding.size
  }

  /**
    * This method checks if there are missions remaining for the given user
    *
    * @param userId User id
    * @param regionId Region id
    * @return
    */
  def isMissionAvailable(userId: UUID, regionId: Int): Boolean = db.withSession { implicit session =>
    val incompleteMissions = selectIncompleteMissionsByAUser(userId, regionId)
    incompleteMissions.nonEmpty
  }


  /**
    * Get a list of all the completed tasks
    *
    * @param userId User's UUID
    * @return
    */
  def selectCompletedMissionsByAUser(userId: UUID): List[Mission] = db.withSession { implicit session =>
    val _missions = for {
      (_missions, _missionUsers) <- missionsWithoutDeleted.innerJoin(missionUsers).on(_.missionId === _.missionId)
      if _missionUsers.userId === userId.toString
    } yield _missions

    _missions.list.groupBy(_.missionId).map(_._2.head).toList
  }

  /**
    * Get the list of the completed tasks in the given region for the given user
    *
    * @param userId User's UUID
    * @param regionId region Id
    * @return
    */
  def selectCompletedMissionsByAUser(userId: UUID, regionId: Int): List[Mission] = db.withSession { implicit session =>
    val completedMissions: List[Mission] = selectCompletedMissionsByAUser(userId)
    completedMissions.filter(_.regionId.getOrElse(-1) == regionId)
  }

  /**
    * Select missions with neighborhood names
    * @param userId
    * @return
    */
  def selectCompletedRegionalMission(userId: UUID): List[RegionalMission] = db.withSession { implicit session =>
    val missions = for {
      (_missions, _missionUsers) <- missionsWithoutDeleted.innerJoin(missionUsers).on(_.missionId === _.missionId)
      if _missionUsers.userId === userId.toString
    } yield _missions


    val regionalMissions = for {
      (_m, _r) <- missions.innerJoin(regionProperties).on(_.regionId === _.regionId)
      if _r.key === "Neighborhood Name"
    } yield (_m.missionId, _m.regionId, _r.value.?, _m.label, _m.level, _m.distance, _m.distance_ft, _m.distance_mi, _m.coverage)

    regionalMissions.sortBy(rm => (rm._2.getOrElse(1).asc, rm._6.getOrElse(0.0))).list.map(rm => RegionalMission.tupled(rm))
  }

  /**
    * Get a list of the incomplete missions for the given user
    *
    * @param userId User's UUID
    * @return
    */
  def selectIncompleteMissionsByAUser(userId: UUID): List[Mission] = db.withSession { implicit session =>
    val selectIncompleteMissionQuery = Q.query[String, Mission](
      """SELECT mission.mission_id, mission.region_id, mission.label, mission.level, mission.distance, mission.distance_ft, mission.distance_mi, mission.coverage, mission.deleted
        |  FROM sidewalk.mission
        |LEFT JOIN (
        |    SELECT mission.mission_id
        |      FROM sidewalk.mission
        |    LEFT JOIN sidewalk.mission_user
        |      ON mission.mission_id = mission_user.mission_id
        |    WHERE mission.deleted = false
        |    AND mission_user.user_id = ?
        |) AS completed_mission
        |  ON mission.mission_id = completed_mission.mission_id
        |WHERE deleted = false AND completed_mission.mission_id IS NULL""".stripMargin
    )
    val incompleteMissions: List[Mission] = selectIncompleteMissionQuery(userId.toString).list
    incompleteMissions
  }

  /**
    * Get a list of incomplete missions in the give region for the given user
    *
    * @param userId User's UUID
    * @param regionId Region Id
    * @return
    */
  def selectIncompleteMissionsByAUser(userId: UUID, regionId: Int): List[Mission] = db.withSession { implicit session =>
    val incompleteMissions: List[Mission] = selectIncompleteMissionsByAUser(userId)
    incompleteMissions.filter(_.regionId.getOrElse(-1) == regionId)
  }

  /**
    * Get a set of regions where the user has not completed all the missions.
    *
    * @param userId UUID for the user
    * @return
    */
  def selectIncompleteRegions(userId: UUID): Set[Int] = db.withSession { implicit session =>
    val incompleteMissions: List[Mission] = selectIncompleteMissionsByAUser(userId)
    val incompleteRegions: Set[Int] = incompleteMissions.map(_.regionId).flatten.toSet
    incompleteRegions
  }

  /**
    * Returns all the missions
    *
    * @return A list of SidewalkEdge objects.
    */
  def selectMissions: List[Mission] = db.withSession { implicit session =>
    missionsWithoutDeleted.list
  }

  /**
    * Select a list of missions completed by users
    * @return
    */
  def selectMissionsCompletedByUsers: List[MissionCompletedByAUser] = db.withSession { implicit session =>
    val _missions = for {
      (_missions, _missionUsers) <- missionsWithoutDeleted.innerJoin(missionUsers).on(_.missionId === _.missionId)
    } yield (_missions.label, _missions.level, _missionUsers.userId, _missions.distance, _missions.distance_ft, _missions.distance_mi)

    val _missionsCompleted = for {
      (_users, _missions) <- users.innerJoin(_missions).on(_.userId === _._3)
    } yield (_users.username, _missions._1, _missions._2, _missions._4, _missions._5, _missions._6)

    _missionsCompleted.list.map(x => MissionCompletedByAUser.tupled(x))
  }

  /**
    * Select mission counts by user
    *
    * @ List[(user_id,count)]
    */
  def selectMissionCountsPerUser: List[(String, Int)] = db.withSession { implicit session =>
    val _missions = for {
      (_missions, _missionUsers) <- missionsWithoutDeleted.innerJoin(missionUsers).on(_.missionId === _.missionId)
    } yield _missionUsers.userId

//    val nonResearcherMissions2 = _missions.filterNot(_ inSet researcherIds)
//val nonResearcherMissions = nonResearcherMissions2.filterNot(_ === "97760883-8ef0-4309-9a5e-0c086ef27573")
    val nonResearcherMissions = _missions.filterNot(_ === "97760883-8ef0-4309-9a5e-0c086ef27573")

    val missionCounts = nonResearcherMissions.groupBy(m => m).map{ case(id, group) => (id, group.length)}

    val fullMissionCounts: List[(String, Option[Int])] = missionCounts.rightJoin(allUsersWithAnAudit).on(_._1 === _).map {case (mc, uwaa) => (uwaa, mc._2.?)}.list

    fullMissionCounts.map{pair => (pair._1, pair._2.getOrElse(0))}
  }

  /**
    * Select label counts per registered user
    */
  def getLabelCountsPerRegisteredUser: List[(String, Int)] = db.withSession { implicit session =>

//    val regUserAudits = nonResearcherCompletedAudits.filterNot(_.userId === "97760883-8ef0-4309-9a5e-0c086ef27573")
    val regUserAudits = completedAudits.filterNot(_.userId === "97760883-8ef0-4309-9a5e-0c086ef27573")

    val _labels = for {
      (_tasks, _labels) <- regUserAudits.innerJoin(labelsWithoutDeleted).on(_.auditTaskId === _.auditTaskId)
    } yield _tasks.userId

    _labels.groupBy(l => l).map{ case (uid, group) => (uid, group.length)}.list
  }

  /**
    * Select label counts per anonymous user
    */
  def getLabelCountsPerAnonUser: List[(String, Int)] = db.withSession { implicit session =>

    val _anonAudits = for {
      (_ate, _at) <- auditTaskEnvironmentTable.innerJoin(auditTaskTable).on(_.auditTaskId === _.auditTaskId)
      if _at.userId === "97760883-8ef0-4309-9a5e-0c086ef27573"
    } yield (_ate.ipAddress, _ate.auditTaskId)

    val _labels = for {
      (_tasks, _labels) <- _anonAudits.innerJoin(labelsWithoutDeleted).on(_._2 === _.auditTaskId)
    } yield _tasks._1

    val labelCounts = _labels.groupBy(l => l).map{ case (uid, group) => (uid, group.length)}.rightJoin(anonIps).on(_._1 === _).map{
    case (cm, ai) => (ai, cm._2.?)
  }.list

    // right now the count is an option; replace the None with a 0 -- it was none b/c only users who had completed
    // missions ended up in the completedMissions query.
    labelCounts.map{pair => (pair._1.get, pair._2.getOrElse(0))}
  }

  /**
    * Select distance audited per registered user as a query
    */
  def getAuditDistancePerRegisteredUserQuery = db.withSession { implicit session =>

    val regUserAudits = nonResearcherCompletedAudits.filterNot(_.userId === "97760883-8ef0-4309-9a5e-0c086ef27573")

    val _dists = for {
      (_tasks, _streets) <- regUserAudits.innerJoin(streetEdges).on(_.streetEdgeId === _.streetEdgeId)
    } yield (_tasks.userId, _streets.geom.transform(26918).length)

    _dists.groupBy(d => d).map{ case (self, group) => (self._1, group.map(_._2).max)}
  }

  /**
    * Select average speed (in minutes per 1000ft) per registered user
    */
  def selectAveSpeedPerUser: List[(String, Float)] = db.withSession { implicit session =>
    val tasks = nonResearcherCompletedAudits.filterNot(audit => (audit.taskEnd.isEmpty || audit.userId === anonId))
    val tasksWithDistQuery = for {
      (_tasks, _edges) <- tasks.innerJoin(streetEdges).on(_.streetEdgeId === _.streetEdgeId)
    } yield (_tasks.userId, _tasks.taskStart, _tasks.taskEnd, _edges.geom.transform(26918).length)// * 0.032467)
    val tasksWithDist: List[(String, Timestamp, Option[Timestamp], Float)] = tasksWithDistQuery.list
    val tasksWithDuration: List[(String, Float, Float)] = tasksWithDist.map(x => (x._1, (x._3.get.getTime - x._2.getTime) * 0.000016667.toFloat, x._4))
    val filteredTasks = tasksWithDuration.filter(x => x._2 < 20.0) // filter out audit tasks that took over 20 minutes
    (for ((userId, taskList) <- filteredTasks.groupBy(_._1)) yield {
      // only include if the user audited at least 1000ft
      val totalDist = taskList.map(_._3).sum * 0.00328084
      if (totalDist >= 1) {
        Some((userId, (taskList.map(_._2).sum / totalDist).toFloat))
      } else {
        None
      }
    }).toList.flatten
  }

  /**
    * Gets distance audited by each user that is not a researcher (in miles)
    */
  def selectAuditDistancePerUser: List[(String, Float)] = db.withSession {implicit session =>
    val tasks = completedAudits.filterNot(audit => (audit.taskEnd.isEmpty || audit.userId === anonId))
    val _tasksWithDists = for {
      (_tasks, _edges) <- tasks.innerJoin(streetEdges).on(_.streetEdgeId === _.streetEdgeId)
    } yield (_tasks.userId, _edges.geom.transform(26918).length)
    _tasksWithDists.groupBy(_._1).map{ case (uid,group) => (uid, group.map(_._2).sum.getOrElse(0.0.toFloat) * 0.000621371.toFloat)}.list
  }

  /**
    * Select average label counts per 1000ft, per registered user
    */
//  def getRegUserLabelCounts: List[(String, Float)] = db.withSession { implicit session =>
//
//    val distPerUser = getAuditDistancePerRegisteredUserQuery
//
//    val countPerUser = getLabelCountsPerRegisteredUserQuery
//
//    val aveCountPerUser = countPerUser.rightJoin(distPerUser).on(_._1 === _._1).map {case (c, d) => (d._1, d._2, c._2.?)}.list
//
//    val usersWithFullMission = aveCountPerUser.filter(_._2.get * 3.28084 > 1000.0)
//
//    usersWithFullMission.map{triple => (triple._1, (triple._3.getOrElse(0).toFloat / (triple._2.get * 0.00328084)).toFloat)}
//  }
}
