package controllers

import javax.inject.Inject

import com.mohiva.play.silhouette.api.{Environment, Silhouette}
import com.mohiva.play.silhouette.impl.authenticators.SessionAuthenticator
import controllers.headers.ProvidesHeader
import models.amt.{AMTAssignmentTable, AMTConditionTable}
import models.clustering_session.ClusteringSessionTable
import models.user.User
import play.api.libs.json.{JsObject, Json}

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
  def getTurkerLabelsByCondition = UserAwareAction.async { implicit request =>
    var streets = List[JsObject]()
    var labels = List[JsObject]()

    val routeIds: List[Int] = AMTConditionTable.getRouteIdsForAllConditions
    for (routeId <- routeIds) {
      streets = List.concat(streets, ClusteringSessionTable.getStreetGeomForIRR(routeId).map(_.toJSON).toList)
    }

    val conditionIds: List[Int] = AMTConditionTable.getAllConditionIds
    for (conditionId <- conditionIds) {
      labels = List.concat(labels, AMTAssignmentTable.getTurkerLabelsByCondition(conditionId.toInt).map(_.toJSON).toList)
    }
    var finalJson = Json.obj(
      "labels" -> Json.obj("type" -> "FeatureCollection", "features" -> labels),
      "streets" -> Json.obj("type" -> "FeatureCollection", "features" -> streets)
    )

    Future.successful(Ok(finalJson))
  }


  /** Posts */

}
