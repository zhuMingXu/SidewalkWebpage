package controllers

import javax.inject.Inject

import com.mohiva.play.silhouette.api.{Environment, Silhouette}
import com.mohiva.play.silhouette.impl.authenticators.SessionAuthenticator
import controllers.headers.ProvidesHeader
import models.amt.AMTAssignmentTable
import models.clustering_session.ClusteringSessionTable
import models.user.User
import play.api.libs.json.{JsError, JsObject, Json}
import play.api.mvc.BodyParsers

import scala.concurrent.Future

class IRRController @Inject()(implicit val env: Environment[User, SessionAuthenticator])
  extends Silhouette[User, SessionAuthenticator] with ProvidesHeader {


  // Pages
  def index = UserAwareAction.async { implicit request =>
    Future.successful(Ok(views.html.irr("Project Sidewalk", request.identity)))
  }

  def getDataForIRR(hitId: String, routeId: Int) = UserAwareAction.async { implicit request =>
    val streets: JsObject = Json.obj("type" -> "FeatureCollection",
      "features" -> ClusteringSessionTable.getStreetGeomForIRR(routeId).map(_.toJSON))
    val labels: JsObject = Json.obj("type" -> "FeatureCollection",
      "features" -> ClusteringSessionTable.getLabelsForIRR(hitId, routeId).map(_.toJSON))

    val json = Json.obj("labels" -> labels, "streets" -> streets)
    Future.successful(Ok(json))
  }

  def getDataForIRRForHits = UserAwareAction.async(BodyParsers.parse.json) { implicit request =>
    var submission = request.body//request.body.validate[List[String]]
    println(submission)

    val hitIdList = List("hit72", "hit128")

    var finalJson = Json.obj()
    for ( hitId <- hitIdList ) {
      val routeIds = AMTAssignmentTable.getHITRouteIds(hitId)
      println(routeIds)
      finalJson.as[JsObject] + (hitId.toString -> Json.obj())

      var routeListJson = Json.obj()
      for (routeId <- routeIds) {
        val streets: JsObject = Json.obj("type" -> "FeatureCollection",
          "features" -> ClusteringSessionTable.getStreetGeomForIRR(routeId).map(_.toJSON))
        val labels: JsObject = Json.obj("type" -> "FeatureCollection",
          "features" -> ClusteringSessionTable.getLabelsForIRR(hitId, routeId).map(_.toJSON))

        val routeMetadataJson = Json.obj("labels" -> labels, "streets" -> streets)

        routeListJson = routeListJson.as[JsObject] + (routeId.toString -> routeMetadataJson)
      }
      finalJson = finalJson.as[JsObject] + (hitId.toString -> routeListJson)
    }

    Future.successful(Ok(finalJson))

//    submission.fold(
//      errors => {
//        Future.successful(BadRequest(Json.obj("status" -> "Error", "message" -> JsError.toFlatJson(errors))))
//      },
//      submission => {
//
//        //    val streets = ClusteringSessionTable.getStreetGeomForIRR(routeId).map(_.toJSON)
//        //    val labels = ClusteringSessionTable.getLabelsForIRR(hitId, routeId).map(_.toJSON)
//        //    val json = Json.obj("labels" -> labels.foldLeft(Json.obj())(_ deepMerge _), "streets" -> streets.foldLeft(Json.obj())(_ deepMerge _))
//
//        Future.successful(Ok(Json.obj()))
//      }
//    )
  }

}
