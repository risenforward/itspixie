package com.prisma.deploy.connector

import com.prisma.shared.models._

sealed trait DeployMutaction {
  def projectId: String
}

case class CreateProject(projectId: String)  extends DeployMutaction
case class TruncateProject(project: Project) extends DeployMutaction { val projectId = project.id }
case class DeleteProject(projectId: String)  extends DeployMutaction

case class CreateColumn(projectId: String, model: Model, field: ScalarField)                           extends DeployMutaction
case class DeleteColumn(projectId: String, model: Model, field: ScalarField)                           extends DeployMutaction
case class UpdateColumn(projectId: String, model: Model, oldField: ScalarField, newField: ScalarField) extends DeployMutaction

case class CreateScalarListTable(projectId: String, model: Model, field: ScalarField)                                               extends DeployMutaction
case class DeleteScalarListTable(projectId: String, model: Model, field: ScalarField)                                               extends DeployMutaction
case class UpdateScalarListTable(projectId: String, oldModel: Model, newModel: Model, oldField: ScalarField, newField: ScalarField) extends DeployMutaction

case class CreateModelTable(projectId: String, model: Model)                                                             extends DeployMutaction
case class DeleteModelTable(projectId: String, model: Model, nameOfIdField: String, scalarListFields: Vector[String])    extends DeployMutaction // delete/truncate collection based on migrations setting in server config
case class RenameTable(projectId: String, previousName: String, nextName: String, scalarListFieldsNames: Vector[String]) extends DeployMutaction // rename based on migration setting

case class CreateRelationTable(projectId: String, schema: Schema, relation: Relation) extends DeployMutaction
case class DeleteRelationTable(projectId: String, schema: Schema, relation: Relation) extends DeployMutaction // based on migration settings;  set relation fields to null in document

case class CreateInlineRelation(projectId: String, model: Model, field: RelationField, references: Model, column: String) extends DeployMutaction
