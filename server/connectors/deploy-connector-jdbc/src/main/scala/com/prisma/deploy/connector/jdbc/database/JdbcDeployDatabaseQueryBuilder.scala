package com.prisma.deploy.connector.jdbc.database

import java.sql.ResultSet

import com.prisma.connector.shared.jdbc.SlickDatabase
import com.prisma.deploy.connector.jdbc.JdbcBase
import com.prisma.shared.models.RelationSide.RelationSide
import com.prisma.shared.models.{Model, RelationField}
import org.jooq.impl.DSL._

case class JdbcDeployDatabaseQueryBuilder(slickDatabase: SlickDatabase) extends JdbcBase {
  import slickDatabase.profile.api._

  def existsByModel(projectId: String, model: Model): DBIOAction[Boolean, NoStream, Effect.All] = {
    val query = sql.select(
      field(
        exists(sql.select(field(name(model.dbNameOfIdField_!))).from(table(name(projectId, model.dbName))))
      )
    )

    queryToDBIO(query)(readResult = readExists)
  }

  def existsByRelation(projectId: String, relationId: String): DBIOAction[Boolean, NoStream, Effect.All] = {
    val query = sql.select(
      field(
        exists(sql.select(field(name("id"))).from(table(name(projectId, relationId))))
      )
    )

    queryToDBIO(query)(readResult = readExists)
  }

  def existsDuplicateByRelationAndSide(projectId: String, relationTableName: String, relationSide: RelationSide): DBIOAction[Boolean, NoStream, Effect.All] = {
    val query = sql.select(
      field(
        exists(
          sql.select(count()).from(table(name(projectId, relationTableName))).groupBy(field(name(relationSide.toString))).having(count().gt(inline(1)))
        ))
    )

    queryToDBIO(query)(readResult = readExists)
  }

  def existsNullByModelAndScalarField(projectId: String, model: Model, fieldName: String): DBIOAction[Boolean, NoStream, Effect.All] = {
    val query = sql.select(
      field(
        exists(
          sql
            .select(field(name(model.dbNameOfIdField_!)))
            .from(table(name(projectId, model.dbName)))
            .where(field(name(fieldName)).isNull))
      )
    )

    queryToDBIO(query)(readResult = readExists)
  }

  def existsDuplicateValueByModelAndField(projectId: String, model: Model, fieldName: String): DBIOAction[Boolean, NoStream, Effect.All] = {
    val query = sql.select(
      field(
        exists(
          sql
            .select(count())
            .from(
              sql
                .select(field(name(fieldName)))
                .from(table(name(projectId, model.dbName)))
                .where(field(name(fieldName)).isNotNull)
                .asTable("temp"))
            .groupBy(field(name("temp", fieldName)))
            .having(count().gt(inline(1)))
        )
      )
    )

    queryToDBIO(query)(readResult = readExists)
  }

  def existsNullByModelAndRelationField(projectId: String, model: Model, f: RelationField): DBIOAction[Boolean, NoStream, Effect.All] = {
    val query = sql.select(
      field(
        exists(
          sql
            .select(field(name(model.dbNameOfIdField_!)))
            .from(table(name(projectId, model.dbName)))
            .where(
              field(name(model.dbNameOfIdField_!)).notIn(
                sql
                  .select(field(name(f.relationSide.toString)))
                  .from(table(name(projectId, f.relation.relationTableName)))
              ))
        )
      )
    )

    queryToDBIO(query)(readResult = readExists)
  }

  def enumValueIsInUse(projectId: String, models: Vector[Model], enumName: String, value: String): DBIOAction[Boolean, NoStream, Effect.All] = {
    val nameTuples = for {
      model <- models
      field <- model.fields
      if field.enum.isDefined && field.enum.get.name == enumName
    } yield {
      if (field.isList) ("nodeId", s"${model.dbName}_${field.dbName}", "value", value) else (model.dbNameOfIdField_!, model.dbName, field.dbName, value)
    }

    val checks = nameTuples
      .map { t =>
        sql
          .select(
            field(
              exists(
                sql
                  .select(inline(0).as("check"))
                  .from(table(name(projectId, t._2)))
                  .where(field(name(t._3)).equal(inline(t._4)))
              )))
          .orderBy(field(""))
      }

    // JOOQ switches types in between .union calls, preventing a more elegant solution (e.g. just a .reduce over the col)
    val inner = checks.length match {
      case x if x == 1 =>
        checks.head

      case x if x == 2 =>
        checks.head.union(checks.last)

      case x if x > 2 =>
        val initial = checks.take(2)
        val rest    = checks.drop(2)
        rest.foldLeft(initial.head.union(initial.last)) { (prev, next) =>
          prev.union(next)
        }

      case _ =>
        sql.select(inline(false).as("check"))
    }

    val outerQuery = sql.select(
      field(
        exists(
          sql
            .selectZero()
            .from(inner.asTable("inner"))
            .where(field(name("inner", "check")).isTrue)
        ))
    )

    queryToDBIO(outerQuery)(readResult = readExists)
  }

  val readExists: Function[ResultSet, Boolean] = { rs =>
    if (rs.next) {
      rs.getBoolean(1)
    } else {
      false
    }
  }
}
