package com.prisma.deploy.connector.mongo.impl

import com.prisma.deploy.connector.ClientDbQueries
import com.prisma.deploy.connector.mongo.database.MongoDeployDatabaseQueryBuilder
import com.prisma.shared.models.RelationSide.RelationSide
import com.prisma.shared.models._
import org.mongodb.scala.MongoClient

import scala.concurrent.{ExecutionContext, Future}

case class MongoClientDbQueries(project: Project, clientDatabase: MongoClient)(implicit ec: ExecutionContext) extends ClientDbQueries {

  def existsByModel(modelName: String): Future[Boolean] = {
    clientDatabase.getDatabase(project.id).getCollection(modelName).countDocuments().toFuture().map(count => if (count > 0) true else false)
  }

  def existsByRelation(relationId: String): Future[Boolean] = {
//    val query = MongoDeployDatabaseQueryBuilder.existsByRelation(project.id, relationId)
    Future.successful(false)
  }

  def existsDuplicateByRelationAndSide(relationId: String, relationSide: RelationSide): Future[Boolean] = {
//    val query = MongoDeployDatabaseQueryBuilder.existsDuplicateByRelationAndSide(project.id, relationId, relationSide)
    Future.successful(false)
  }

  def existsNullByModelAndField(model: Model, field: Field): Future[Boolean] = {
//    val query = field match {
//      case f: ScalarField   => MongoDeployDatabaseQueryBuilder.existsNullByModelAndScalarField(project.id, model.name, f.name)
//      case f: RelationField => MongoDeployDatabaseQueryBuilder.existsNullByModelAndRelationField(project.id, model.name, f)
//    }
    Future.successful(false)
  }

  def existsDuplicateValueByModelAndField(model: Model, field: ScalarField): Future[Boolean] = {
//    val query = MongoDeployDatabaseQueryBuilder.existsDuplicateValueByModelAndField(project.id, model.name, field.name)
    Future.successful(false)
  }

  override def enumValueIsInUse(models: Vector[Model], enumName: String, value: String): Future[Boolean] = {
//    val query = MongoDeployDatabaseQueryBuilder.enumValueIsInUse(project.id, models, enumName, value)
    Future.successful(false)
  }

}
