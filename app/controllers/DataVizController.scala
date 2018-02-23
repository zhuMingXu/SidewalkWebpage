package controllers

import java.net.URLDecoder
import java.util.UUID
import javax.inject.Inject

import com.mohiva.play.silhouette.api.{Environment, LogoutEvent, Silhouette}
import com.mohiva.play.silhouette.impl.authenticators.SessionAuthenticator

import controllers.headers.ProvidesHeader
import models.label.LabelTable
import models.user.User

import play.api.libs.json.{JsArray, JsError, JsObject, JsValue, Json}
import play.extras.geojson
import play.api.mvc.BodyParsers


import scala.concurrent.Future


class DataVizController @Inject()(implicit val env: Environment[User, SessionAuthenticator])
  extends Silhouette[User, SessionAuthenticator] with ProvidesHeader {

  // JSON APIs

  /**
    * Get a list of all labels
    *
    * @return
    */
  def getAllLabels = UserAwareAction.async { implicit request =>
    val labels = LabelTable.selectLocationsAndSeveritiesOfLabels
    val features: List[JsObject] = labels.map { label =>
      val point = geojson.Point(geojson.LatLng(label.lat.toDouble, label.lng.toDouble))
      val properties = Json.obj(
        "audit_task_id" -> label.auditTaskId,
        "label_id" -> label.labelId,
        "gsv_panorama_id" -> label.gsvPanoramaId,
        "label_type" -> label.labelType,
        "severity" -> label.severity
      )
      Json.obj("type" -> "Feature", "geometry" -> point, "properties" -> properties)
    }
    val featureCollection = Json.obj("type" -> "FeatureCollection", "features" -> features)
    Future.successful(Ok(featureCollection))
  }


  def getLabelsAtZoomLevel(zoomLevel: String) = UserAwareAction.async { implicit request =>

    // TODO: Update it by getting labels from presampled labels from certain zoom levels
    val labels = LabelTable.selectLocationsAndSeveritiesOfLabels
    val features: List[JsObject] = labels.map { label =>
      val point = geojson.Point(geojson.LatLng(label.lat.toDouble, label.lng.toDouble))
      val properties = Json.obj(
        "audit_task_id" -> label.auditTaskId,
        "label_id" -> label.labelId,
        "gsv_panorama_id" -> label.gsvPanoramaId,
        "label_type" -> label.labelType,
        "severity" -> label.severity
      )
      Json.obj("type" -> "Feature", "geometry" -> point, "properties" -> properties)
    }
    val featureCollection = Json.obj("type" -> "FeatureCollection", "features" -> features)
    Future.successful(Ok(featureCollection))
  }
}
