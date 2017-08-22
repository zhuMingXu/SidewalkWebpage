package controllers

import java.sql.Timestamp
import javax.inject.Inject

import com.mohiva.play.silhouette.api.{Environment, Silhouette}
import com.mohiva.play.silhouette.impl.authenticators.SessionAuthenticator
import controllers.headers.ProvidesHeader
import models.clustering_session.ClusteringSessionTable
import models.user.User
import play.api.libs.json.Json

import scala.concurrent.Future

class IRRController @Inject()(implicit val env: Environment[User, SessionAuthenticator])
  extends Silhouette[User, SessionAuthenticator] with ProvidesHeader {


  // Pages
  def index = UserAwareAction.async { implicit request =>
    Future.successful(Ok(views.html.irr("Project Sidewalk", request.identity)))
  }

  def getDataForIRR(hitId: String, routeId: Int) = UserAwareAction.async { implicit request =>
    val streets = ClusteringSessionTable.getStreetGeomForIRR(routeId).map(_.toJSON)
    val labels = ClusteringSessionTable.getLabelsForIRR(hitId, routeId).map(_.toJSON)

    val json = Json.obj("labels" -> labels.foldLeft(Json.obj())(_ deepMerge _), "streets" -> streets.foldLeft(Json.obj())(_ deepMerge _))
    Future.successful(Ok(json))
  }

}
