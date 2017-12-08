package cool.graph.deploy.migration

import akka.actor.Actor
import cool.graph.deploy.database.persistence.MigrationPersistence
import cool.graph.deploy.migration.MigrationApplierJob.ScanForUnappliedMigrations
import cool.graph.deploy.migration.mutactions._
import cool.graph.shared.models._
import slick.jdbc.MySQLProfile.backend.DatabaseDef

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

trait MigrationApplier {
  def applyMigration(previousProject: Project, nextProject: Project, migration: Migration): Future[MigrationApplierResult]
}

case class MigrationApplierResult(succeeded: Boolean)

case class MigrationApplierImpl(clientDatabase: DatabaseDef)(implicit ec: ExecutionContext) extends MigrationApplier {
  override def applyMigration(previousProject: Project, nextProject: Project, migration: Migration): Future[MigrationApplierResult] = {
    val initialProgress = MigrationProgress(pendingSteps = migration.steps, appliedSteps = Vector.empty, isRollingback = false)
    recurse(previousProject, nextProject, initialProgress)
  }

  def recurse(previousProject: Project, nextProject: Project, progress: MigrationProgress): Future[MigrationApplierResult] = {
    if (!progress.isRollingback) {
      recurseForward(previousProject, nextProject, progress)
    } else {
      recurseForRollback(previousProject, nextProject, progress)
    }
  }

  def recurseForward(previousProject: Project, nextProject: Project, progress: MigrationProgress): Future[MigrationApplierResult] = {
    if (progress.pendingSteps.nonEmpty) {
      val (step, newProgress) = progress.popPending

      val result = for {
        _ <- applyStep(previousProject, nextProject, step)
        x <- recurse(previousProject, nextProject, newProgress)
      } yield x

      result.recoverWith {
        case exception =>
          println("encountered exception while applying migration. will roll back.")
          exception.printStackTrace()
          recurseForRollback(previousProject, nextProject, newProgress.markForRollback)
      }
    } else {
      Future.successful(MigrationApplierResult(succeeded = true))
    }
  }

  def recurseForRollback(previousProject: Project, nextProject: Project, progress: MigrationProgress): Future[MigrationApplierResult] = {
    if (progress.appliedSteps.nonEmpty) {
      val (step, newProgress) = progress.popApplied

      for {
        _ <- unapplyStep(previousProject, nextProject, step).recover { case _ => () }
        x <- recurse(previousProject, nextProject, newProgress)
      } yield x
    } else {
      Future.successful(MigrationApplierResult(succeeded = false))
    }
  }

  def applyStep(previousProject: Project, nextProject: Project, step: MigrationStep): Future[Unit] = {
    migrationStepToMutaction(previousProject, nextProject, step).map(executeClientMutaction).getOrElse(Future.successful(()))
  }

  def unapplyStep(previousProject: Project, nextProject: Project, step: MigrationStep): Future[Unit] = {
    migrationStepToMutaction(previousProject, nextProject, step).map(executeClientMutactionRollback).getOrElse(Future.successful(()))
  }

  // todo: I think this knows too much about previous and next. It should just know how to apply steps to previous.
  // Ideally, the interface would just have a (previous)project and a step.
  def migrationStepToMutaction(previousProject: Project, nextProject: Project, step: MigrationStep): Option[ClientSqlMutaction] = step match {
    case x: CreateModel =>
      Some(CreateModelTable(previousProject.id, x.name))

    case x: DeleteModel =>
      Some(DeleteModelTable(previousProject.id, x.name))

    case x: UpdateModel =>
      Some(RenameModelTable(projectId = previousProject.id, previousName = x.name, nextName = x.newName))

    case x: CreateField =>
      // todo I think those validations should be somewhere else, preferably preventing a step being created
      val model = nextProject.getModelByName_!(x.model)
      val field = model.getFieldByName_!(x.name)
      if (field.isSystemField || !field.isScalar) {
        None
      } else {
        Some(CreateColumn(nextProject.id, model, field))
      }

    case x: DeleteField =>
      val model = previousProject.getModelByName_!(x.model)
      val field = model.getFieldByName_!(x.name)
      Some(DeleteColumn(nextProject.id, model, field))

    case x: UpdateField =>
      val model         = nextProject.getModelByName_!(x.model)
      val nextField     = nextProject.getFieldByName_!(x.model, x.finalName)
      val previousField = previousProject.getFieldByName_!(x.model, x.name)
      Some(UpdateColumn(nextProject.id, model, previousField, nextField))

    case x: EnumMigrationStep =>
      None

    case x: CreateRelation =>
      val relation = nextProject.getRelationByName_!(x.name)
      Some(CreateRelationTable(nextProject, relation))

    case x: DeleteRelation =>
      val relation = previousProject.getRelationByName_!(x.name)
      Some(DeleteRelationTable(nextProject, relation))
  }

  def executeClientMutaction(mutaction: ClientSqlMutaction): Future[Unit] = {
    for {
      statements <- mutaction.execute
      _          <- clientDatabase.run(statements.sqlAction)
    } yield ()
  }

  def executeClientMutactionRollback(mutaction: ClientSqlMutaction): Future[Unit] = {
    for {
      statements <- mutaction.rollback.get
      _          <- clientDatabase.run(statements.sqlAction)
    } yield ()
  }

//  private val emptyMutaction = new ClientSqlMutaction {
//    val emptyResult = Future(ClientSqlStatementResult[Any](DBIOAction.successful(())))
//
//    override def execute: Future[ClientSqlStatementResult[Any]]          = emptyResult
//    override def rollback: Option[Future[ClientSqlStatementResult[Any]]] = Some(emptyResult)
//  }
}

case class MigrationProgress(
    appliedSteps: Vector[MigrationStep],
    pendingSteps: Vector[MigrationStep],
    isRollingback: Boolean
) {
  def addAppliedStep(step: MigrationStep) = copy(appliedSteps = appliedSteps :+ step)

  def popPending: (MigrationStep, MigrationProgress) = {
    val step = pendingSteps.head
    step -> copy(appliedSteps = appliedSteps :+ step, pendingSteps = pendingSteps.tail)
  }

  def popApplied: (MigrationStep, MigrationProgress) = {
    val step = appliedSteps.last
    step -> copy(appliedSteps = appliedSteps.dropRight(1))
  }

  def markForRollback = copy(isRollingback = true)
}

object MigrationApplierJob {
  object ScanForUnappliedMigrations
}

case class MigrationApplierJob(
    clientDatabase: DatabaseDef,
    migrationPersistence: MigrationPersistence
) extends Actor {
  import akka.pattern.pipe
  import context.dispatcher
  import scala.concurrent.duration._

  val applier = MigrationApplierImpl(clientDatabase)

  scheduleScanMessage

  override def receive: Receive = {
    case ScanForUnappliedMigrations =>
      println("scanning for migrations")
      pipe(migrationPersistence.getUnappliedMigration()) to self

    case Some(UnappliedMigration(prevProject, nextProject, migration)) =>
      println(s"found the unapplied migration in project ${prevProject.id}: $migration")
      val doit = for {
        result <- applier.applyMigration(prevProject, nextProject, migration)
        _ <- if (result.succeeded) {
              migrationPersistence.markMigrationAsApplied(migration)
            } else {
              Future.successful(())
            }
      } yield ()
      doit.onComplete {
        case Success(_) =>
          println("applying migration succeeded")
          scheduleScanMessage

        case Failure(e) =>
          println("applying migration failed with:")
          e.printStackTrace()
          scheduleScanMessage
      }

    case None =>
      println("found no unapplied migration")
      scheduleScanMessage

    case akka.actor.Status.Failure(throwable) =>
      println("piping failed with:")
      throwable.printStackTrace()
      scheduleScanMessage
  }

  def scheduleScanMessage = context.system.scheduler.scheduleOnce(10.seconds, self, ScanForUnappliedMigrations)
}
