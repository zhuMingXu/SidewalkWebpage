package formats.json

import play.api.libs.json.{JsPath, Reads}
import play.api.libs.functional.syntax._

//object IssueFormats {
//  case class NoStreetView(streetEdgeId: Int, space: String)
//
//  implicit val noStreetViewReads: Reads[NoStreetView] = (
//    (JsPath \ "street_edge_id").read[Int] and
//      (JsPath \ "issue").read[String]
//    )(NoStreetView.apply _)
//}
object ClusteringFormats {

  case class Lab(labelId: Int, labelType: String, clusterNum: Int)


  implicit val labReads: Reads[Lab] = (
    (JsPath \ "label_id").read[Int] and
      (JsPath \ "label_type").read[String] and
      (JsPath \ "cluster").read[Int]
    )(Lab.apply _)
}