package controllers

import javax.inject.Inject

import com.mohiva.play.silhouette.api.{Environment, Silhouette}
import com.mohiva.play.silhouette.impl.authenticators.SessionAuthenticator
import controllers.headers.ProvidesHeader
import models.user.User

import scala.concurrent.Future

class IRRController @Inject()(implicit val env: Environment[User, SessionAuthenticator])
  extends Silhouette[User, SessionAuthenticator] with ProvidesHeader {

  // Pages
  def index = UserAwareAction.async { implicit request =>
    Future.successful(Ok(views.html.irr("Project Sidewalk", request.identity)))
  }

}
