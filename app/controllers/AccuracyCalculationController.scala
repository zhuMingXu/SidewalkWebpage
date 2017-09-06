package controllers

import javax.inject.Inject

import com.mohiva.play.silhouette.api.{Environment, Silhouette}
import com.mohiva.play.silhouette.impl.authenticators.SessionAuthenticator
import controllers.headers.ProvidesHeader
import models.amt.AMTAssignmentTable
import models.clustering_session.LabelToCluster
import models.user.User
import play.api.libs.json.{JsError, JsObject, Json}
import play.api.mvc.BodyParsers

import scala.concurrent.Future

class AccuracyCalculationController @Inject()(implicit val env: Environment[User, SessionAuthenticator])
  extends Silhouette[User, SessionAuthenticator] with ProvidesHeader {


  // Pages

  // Gets
  def getTurkerLabelsByCondition(conditionId: Int) = UserAwareAction.async { implicit request =>

    val turkerLabels: List[LabelToCluster] = AMTAssignmentTable.getTurkerLabelsByCondition(conditionId)
    val json = Json.arr(turkerLabels.map(x => Json.obj(
      "label_id" -> x.labelId, "label_type" -> x.labelType, "lat" -> x.lat, "lng" -> x.lng, "severity" -> x.severity,
      "temporary" -> x.temp, "turker_id" -> x.turkerId
    )))
    Future.successful(Ok(json))
  }

  // Posts

}
