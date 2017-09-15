package controllers

import javax.inject.Inject

import com.mohiva.play.silhouette.api.{Environment, Silhouette}
import com.mohiva.play.silhouette.impl.authenticators.SessionAuthenticator
import controllers.headers.ProvidesHeader
import models.amt.{AMTAssignmentTable, AMTConditionTable}
import models.clustering_session.ClusteringSessionTable
import models.gt.GTLabelTable
import models.user.User
import play.api.libs.json.{JsObject, Json}
import scala.sys.process._

import scala.concurrent.Future

class AccuracyCalculationController @Inject()(implicit val env: Environment[User, SessionAuthenticator])
  extends Silhouette[User, SessionAuthenticator] with ProvidesHeader {


  /** Pages */

  def index = UserAwareAction.async { implicit request =>
    Future.successful(Ok(views.html.accuracy("Project Sidewalk", request.identity)))
  }


  /** Gets */

  /**
    * Returns the set street edges associated with every MTurk condition, and the labels from turkers who completed them
    *
    * @return
    */
  def getAccuracyData(workerType: String, clusterNum: String) = UserAwareAction.async { implicit request =>
    val clustNum: Int = clusterNum.toInt

    val gtLabels: List[JsObject] = GTLabelTable.all.map(_.toGeoJSON).toList
    var streets = List[JsObject]()
    var labels = List[JsObject]()

    // get street data
    val routeIds: List[Int] = AMTConditionTable.getRouteIdsForAllConditions
    for (routeId <- routeIds) {
      streets = List.concat(streets, ClusteringSessionTable.getStreetGeomForIRR(routeId).map(_.toJSON).toList)
    }

    // get labels from both GT and turkers/volunteers
//    val conditionIds: List[Int] = AMTConditionTable.getAllConditionIds
    val conditionIds: List[Int] = List(72, 74, 98, 100, 122, 128) // a few conditions for testing
    for (conditionId <- conditionIds) {
      labels = workerType match {
        case "turker" =>
          clustNum match {
            case 1 =>
              List.concat(labels, AMTAssignmentTable.getTurkerLabelsByCondition(conditionId).map(_.toJSON).toList)
            case _ =>
              val clustSessionIds: List[Int] = runNonGTClusteringForRoutesInCondition(conditionId, clustNum)
              List.concat(labels, ClusteringSessionTable.getLabelsForAccuracy(clustSessionIds).map(_.toJSON).toList)
          }
        case "volunteer" => List.concat(labels, AMTConditionTable.getVolunteerLabelsByCondition(conditionId).map(_.toJSON).toList)
        case _ => labels
      }
    }

    // output as geoJSON feature collections
    var finalJson = Json.obj(
      "gt_labels" -> Json.obj("type" -> "FeatureCollection", "features" -> gtLabels),
      "worker_labels" -> Json.obj("type" -> "FeatureCollection", "features" -> labels),
      "streets" -> Json.obj("type" -> "FeatureCollection", "features" -> streets)
    )

    Future.successful(Ok(finalJson))
  }

  /**
    * Runs clustering on the specified route, using nTurker many turkers. Returns the new clustering_session_id used.
    *
    * @param routeId
    * @param nTurkers
    * @return
    */
  def runNonGTClustering(routeId: Int, nTurkers: Int): Int = {
    val clusteringOutput = ("python label_clustering.py " + routeId + " --n_labelers " + nTurkers).!!
    ClusteringSessionTable.getNewestClusteringSessionId
  }

  /**
    * Runs clustering on each route of the specified condition, for nTurkers. Returns new clustering_session_ids used.
    *
    * @param conditionId
    * @param nTurkers
    * @return
    */
  def runNonGTClusteringForRoutesInCondition(conditionId: Int, nTurkers: Int): List[Int] = {
    var sessionIds = List[Int]()

    AMTAssignmentTable.getNonResearcherTurkersWhoCompletedCondition(conditionId) match {
      case Nil => None
      case _ =>
        val routesToCluster: List[Int] = AMTConditionTable.getRouteIdsForACondition(conditionId)
        for (routeId <- routesToCluster) {
          sessionIds = sessionIds :+ runNonGTClustering(routeId, nTurkers)
        }
    }
    sessionIds
  }


  /** Posts */

}