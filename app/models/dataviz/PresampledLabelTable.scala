package models.dataviz

import models.utils.MyPostgresDriver.simple._
import models.label.{Label, LabelTable}
import play.api.Play.current

import scala.slick.lifted.ForeignKeyQuery

case class PresampledLabel(labelId: Int, zoomLevel: Int)

class PresampledLabelTable(tag: Tag) extends Table[PresampledLabel](tag, Some("sidewalk"),  "label_presampled") {
  def labelId = column[Int]("label_id", O.PrimaryKey)
  def zoomLevel = column[Int]("zoom_level", O.PrimaryKey)


  def * = (labelId, zoomLevel) <> ((PresampledLabel.apply _).tupled, PresampledLabel.unapply)

  def label: ForeignKeyQuery[LabelTable, Label] =
    foreignKey("label_presampled_label_id_fkey", labelId, TableQuery[LabelTable])(_.labelId)

}

object PresampledLabelsTable {
  val db = play.api.db.slick.DB
  val presampledLabels = TableQuery[PresampledLabelTable]
}
