package com.prisma.deploy.migration.validation

import com.prisma.deploy.connector.InferredTables
import com.prisma.shared.models.Manifestations.{InlineRelationManifestation, RelationTableManifestation}
import com.prisma.shared.models.Schema

object InferredTablesValidator {

  def checkRelationsAgainstInferredTables(schema: Schema, inferredTables: InferredTables): Seq[SchemaError] = {
    schema.relations.flatMap { relation =>
      relation.manifestation match {
        case None =>
          val modelA = relation.getModelA_!(schema)
          val modelB = relation.getModelB_!(schema)
          Some(SchemaError.global(s"Could not find the relation between the models ${modelA.name} and ${modelB.name} in the database"))

        case Some(m: InlineRelationManifestation) =>
          val model = schema.getModelById_!(m.inTableOfModelId)
          inferredTables.modelTables.find(_.name == model.dbName) match {
            case None =>
              Some(SchemaError.global(s"Could not find the model table ${model.dbName} in the database"))

            case Some(modelTable) =>
              modelTable.foreignKeys.find(_.name == m.referencingColumn) match {
                case None    => Some(SchemaError.global(s"Could not find the foreign key column ${m.referencingColumn} in the model table ${model.dbName}"))
                case Some(_) => None
              }
          }

        case Some(m: RelationTableManifestation) =>
          inferredTables.relationTables.find(_.name == m.table) match {
            case None =>
              Some(SchemaError.global(s"Could not find the relation table ${m.table}"))

            case Some(relationTable) =>
              val modelA = relation.getModelA_!(schema)
              val modelB = relation.getModelB_!(schema)
              if (!relationTable.referencesTheTables(modelA.dbName, modelB.dbName)) {
                Some(SchemaError.global(s"The specified relation table ${m.table} does not reference the tables for model ${modelA.name} and ${modelB.name}"))
              } else if (!relationTable.doesColumnReferenceTable(m.modelAColumn, modelA.dbName)) {
                Some(SchemaError.global(
                  s"The specified relation table ${m.table} does not have a column ${m.modelAColumn} or does not the reference the right table ${modelA.dbName}"))
              } else if (!relationTable.doesColumnReferenceTable(m.modelBColumn, modelB.dbName)) {
                Some(SchemaError.global(
                  s"The specified relation table ${m.table} does not have a column ${m.modelBColumn} or does not the reference the right table ${modelB.dbName}"))
              } else {
                None
              }
          }
      }
    }
  }
}
