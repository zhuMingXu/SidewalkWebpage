package formats.json

import play.api.libs.json.{JsPath, Reads}

object IRRDataRequestFormat {
  case class IRRDataRequest(hitIds: List[String])

  implicit val hitIdListReads: Reads[IRRDataRequest] = (JsPath \ "hitIds").read[List[String]].map(IRRDataRequest.apply)

}