package com.prisma.deploy.connector.mongo.impl

import com.prisma.deploy.connector.mongo.database.ProjectDocument
import com.prisma.deploy.connector.{MigrationPersistence, ProjectPersistence}
import com.prisma.shared.models.{Migration, MigrationStatus, Project}
import com.prisma.utils.mongo.MongoExtensions
import org.mongodb.scala.model.Filters
import org.mongodb.scala.{Document, MongoCollection, MongoDatabase}

import scala.concurrent.{ExecutionContext, Future}

case class ProjectPersistenceImpl(
    internalDatabase: MongoDatabase,
    migrationPersistence: MigrationPersistence
)(implicit ec: ExecutionContext)
    extends ProjectPersistence
    with MongoExtensions {

  import DbMapper._

  val projects: MongoCollection[Document] = internalDatabase.getCollection("Project")

  override def load(id: String): Future[Option[Project]] = {
    for {
      theProjects <- projects.find(projectIdFilter(id)).collect.toFuture()
      migration   <- lastSuccessfulMigrationForProjectId(id)
    } yield {
      migration.map { migration =>
        DbMapper.convertToProjectModel(theProjects.head, migration)
      }
    }
  }

  override def create(project: Project): Future[Unit] = {
    projects.insertOne(DbMapper.convertToDocument(project)).toFuture().map(_ => ())
  }

  override def delete(projectId: String): Future[Unit] = {
    projects.deleteOne(projectIdFilter(projectId)).toFuture().map(_ => ())
  }

  override def update(project: Project): Future[_] = {
    val mongoDocument = DbMapper.convertToDocument(project)
    projects.replaceOne(projectIdFilter(project.id), mongoDocument).toFuture
  }

  override def loadAll(): Future[Seq[Project]] = {
    for {
      projects         <- projects.find().collect.toFuture()
      projectDocuments = projects.map(_.as[ProjectDocument])
      migrations       <- Future.sequence(projectDocuments.map(p => lastSuccessfulMigrationForProjectId(p.id)))
    } yield {
      projectDocuments.map { pd =>
        val migration = migrations.flatten.find(_.projectId == pd.id).get
        DbMapper.convertToProjectModel(pd, migration)
      }
    }
  }

  private def lastSuccessfulMigrationForProjectId(id: String): Future[Option[Migration]] = {
    migrationPersistence.loadAll(id).map(_.find(_.status == MigrationStatus.Success))
  }

  private def projectIdFilter(projectId: String) = Filters.eq("id", projectId)
}
