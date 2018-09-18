package controllers

import javax.inject.Inject

import com.mohiva.play.silhouette.api.{Environment, Silhouette}
import com.mohiva.play.silhouette.impl.authenticators.SessionAuthenticator
import controllers.headers.ProvidesHeader
import formats.json.IRRDataRequestFormat.IRRDataRequest
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
    var submission = request.body.validate[IRRDataRequest]

    submission.fold(
      errors => {
        Future.successful(BadRequest(Json.obj("status" -> "Error", "message" -> JsError.toFlatJson(errors))))
      },
      submission => {

        val hitIdList: List[String] = submission.hitIds

        var streets = List[JsObject]()
        var labels = List[JsObject]()
        for ( hitId <- hitIdList ) {
          val routeIds = AMTAssignmentTable.getHITRouteIds(hitId)

          for (routeId <- routeIds) {
            val streets_i =  ClusteringSessionTable.getStreetGeomForIRR(routeId).map(_.toJSON).toList
            val labels_i = ClusteringSessionTable.getLabelsForIRR(hitId, routeId).map(_.toJSON).toList

            labels =  List.concat(labels, labels_i)
            streets =  List.concat(streets, streets_i)
          }
        }
        var finalJson = Json.obj(
          "labels" -> Json.obj("type" -> "FeatureCollection", "features" -> labels),
          "streets" -> Json.obj("type" -> "FeatureCollection", "features" -> streets)
        )

        Future.successful(Ok(finalJson))

      }
    )
  }

}
