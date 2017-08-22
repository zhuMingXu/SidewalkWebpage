package controllers

import java.sql.Timestamp
import javax.inject.Inject

import com.mohiva.play.silhouette.api.{Environment, Silhouette}
import com.mohiva.play.silhouette.impl.authenticators.SessionAuthenticator
import com.vividsolutions.jts.geom.{Coordinate, LineString}
import controllers.headers.ProvidesHeader
import models.user.User
import play.api.libs.json.{JsObject, Json}
import play.extras.geojson
import scala.slick.jdbc.{GetResult, StaticQuery => Q}

import scala.concurrent.Future

case class LineStringCaseClass(streetEdgeId: Int, geom: LineString) {
  /**
    * This method converts the data into the GeoJSON format
    * @return
    */
  def toJSON: JsObject = {

    val coordinates: Array[Coordinate] = geom.getCoordinates
    val latlngs: List[geojson.LatLng] = coordinates.map(coord => geojson.LatLng(coord.y, coord.x)).toList
    val linestring: geojson.LineString[geojson.LatLng] = geojson.LineString(latlngs)

    Json.obj(streetEdgeId.toString -> linestring)
  }
}
case class Labels(edgeId: Int, amtAssignmentId: Option[Int], geom: LineString, x1: Float, y1: Float, x2: Float, y2: Float, taskStart: Timestamp, completed: Boolean)  {
  /**
    * This method converts the data into the GeoJSON format
    * @return
    */
  def toJSON: JsObject = {
    val coordinates: Array[Coordinate] = geom.getCoordinates
    val latlngs: List[geojson.LatLng] = coordinates.map(coord => geojson.LatLng(coord.y, coord.x)).toList
    val linestring: geojson.LineString[geojson.LatLng] = geojson.LineString(latlngs)
    val properties = Json.obj(
      "street_edge_id" -> edgeId,
      "assignment_id" -> amtAssignmentId,
      "x1" -> x1,
      "y1" -> y1,
      "x2" -> x2,
      "y2" -> y2,
      "task_start" -> taskStart.toString,
      "completed" -> completed
    )
    val feature = Json.obj("geometry" -> linestring, "properties" -> properties)
    Json.obj("features" -> List(feature))
  }
}


class IRRController @Inject()(implicit val env: Environment[User, SessionAuthenticator])
  extends Silhouette[User, SessionAuthenticator] with ProvidesHeader {

  // Pages
  def index = UserAwareAction.async { implicit request =>
    Future.successful(Ok(views.html.irr("Project Sidewalk", request.identity)))
  }

  def getDataForIRR(hitId: String, routeId: Int): Boolean = UserAwareAction.async { implicit request =>
    val lineStringQuery = Q.query[Int, LineStringCaseClass](
      """SELECT route_street.current_street_edge_id, street_edge.geom
        |FROM route_street
        |	INNER JOIN street_edge  ON route_street.current_street_edge_id = street_edge.street_edge_id
        |	INNER JOIN amt_volunteer_route  ON amt_volunteer_route.route_id = route_street.route_id
        |WHERE ( amt_volunteer_route.route_id = ? )
        |ORDER BY route_street.route_street_id
      """.stripMargin
    )

    val linestringList = lineStringQuery(routeId).list
    println(linestringList)

    //val streets: List[LineStringCaseClass] = linestringList.map(street => LineStringCaseClass(street.streetEdgeId, street.geom))
    //println(streets)

//    val labelsQuery = Q.query[(String, Int), AuditTask](
//      """SELECT amt_assignment.hit_id,
//        |   		amt_assignment.route_id,
//        |		    amt_assignment.turker_id,
//        |		    label.label_id,
//        |		    label_type.label_type,
//        |		    problem_severity.severity,
//        |		    problem_temporariness.temporary_problem,
//        |		    label_point.lat,
//        |		    label_point.lng
//        |FROM  audit_task
//        |	  INNER JOIN amt_assignment ON audit_task.amt_assignment_id = amt_assignment.amt_assignment_id
//        |	  INNER JOIN label ON label.audit_task_id = audit_task.audit_task_id
//        |	  INNER JOIN label_type ON label.label_type_id = label_type.label_type_id
//        |	  LEFT OUTER JOIN problem_severity ON problem_severity.label_id = label.label_id
//        |	  LEFT OUTER JOIN problem_temporariness ON problem_temporariness.label_id = label.label_id
//        |	  INNER JOIN label_point ON label.label_id = label_point.label_id
//        |WHERE (amt_assignment.hit_id = ? )
//        |		AND ( amt_assignment.route_id = ? )
//        |		AND ( label.deleted = false )
//        |		AND ( label.gsv_panorama_id <> 'stxXyCKAbd73DmkM2vsIHA')
//        |		AND ( amt_assignment.completed = true )
//      """.stripMargin
//    )
//    val labelsResult = labelsQuery(hitId, routeId).list

//    val json = Json.obj("labels" -> Json.obj(), "streets" -> streets.map(_.toJSON))
    val json = Json.obj("labels" -> Json.obj(), "streets" -> Json.obj())
    Future.successful(Ok(json))
//    Future.successful(Ok(task.toJSON))
  }

  /**
    *
    SELECT sidewalk.amt_assignment.hit_id,
        sidewalk.amt_assignment.turker_id,
        sidewalk.amt_assignment.route_id,
        sidewalk.label.label_id,
        sidewalk.label_type.label_type,
        sidewalk.problem_severity.severity,
        sidewalk.problem_temporariness.temporary_problem,
        sidewalk.label_point.lat,
        sidewalk.label_point.lng
    FROM  sidewalk.audit_task
    INNER JOIN sidewalk.amt_assignment ON sidewalk.audit_task.amt_assignment_id = sidewalk.amt_assignment.amt_assignment_id
    INNER JOIN sidewalk.label  ON sidewalk.label.audit_task_id = sidewalk.audit_task.audit_task_id
    INNER JOIN sidewalk.label_type  ON sidewalk.label.label_type_id = sidewalk.label_type.label_type_id
    LEFT OUTER JOIN sidewalk.problem_severity  ON sidewalk.problem_severity.label_id = sidewalk.label.label_id
    LEFT OUTER JOIN sidewalk.problem_temporariness  ON sidewalk.problem_temporariness.label_id = sidewalk.label.label_id
    INNER JOIN sidewalk.label_point  ON sidewalk.label.label_id = sidewalk.label_point.label_id
    WHERE (sidewalk.amt_assignment.hit_id = 'hit72' )
        AND ( sidewalk.amt_assignment.route_id = 145 )
        AND ( sidewalk.label.deleted = false )
        AND ( sidewalk.label.gsv_panorama_id <> 'stxXyCKAbd73DmkM2vsIHA')
        AND ( sidewalk.amt_assignment.completed = true
    */

}
