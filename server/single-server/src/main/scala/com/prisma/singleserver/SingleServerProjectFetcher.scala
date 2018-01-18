package com.prisma.singleserver

import com.prisma.api.project.RefreshableProjectFetcher
import com.prisma.deploy.database.persistence.ProjectPersistence
import com.prisma.shared.models.ProjectWithClientId

import scala.concurrent.{ExecutionContext, Future}

case class SingleServerProjectFetcher(projectPersistence: ProjectPersistence)(implicit ec: ExecutionContext) extends RefreshableProjectFetcher {
  override def fetch(projectIdOrAlias: String): Future[Option[ProjectWithClientId]] = {
    fetchRefreshed(projectIdOrAlias)
  }

  override def fetchRefreshed(projectIdOrAlias: String) = {
    projectPersistence
      .load(projectIdOrAlias)
      .map(_.map { project =>
        ProjectWithClientId(project)
      })
  }
}
