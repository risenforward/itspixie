package com.prisma.subscriptions
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.prisma.api.{ApiDependencies, TestApiDependencies}
import com.prisma.api.connector.postgresql.PostgresApiConnector
import com.prisma.api.mutactions.{DatabaseMutactionVerifierImpl, SideEffectMutactionExecutorImpl}
import com.prisma.api.project.{ProjectFetcher, ProjectFetcherImpl}
import com.prisma.api.schema.SchemaBuilder
import com.prisma.config.ConfigLoader
import com.prisma.deploy.connector.postgresql.PostgresDeployConnector
import com.prisma.messagebus.testkits.{InMemoryPubSubTestKit, InMemoryQueueTestKit}
import com.prisma.messagebus.{PubSubPublisher, PubSubSubscriber, QueueConsumer, QueuePublisher}
import com.prisma.subscriptions.protocol.SubscriptionProtocolV05.Responses.SubscriptionSessionResponseV05
import com.prisma.subscriptions.protocol.SubscriptionProtocolV07.Responses.SubscriptionSessionResponse
import com.prisma.subscriptions.protocol.{Converters, SubscriptionRequest}
import com.prisma.subscriptions.resolving.SubscriptionsManagerForProject.{SchemaInvalidated, SchemaInvalidatedMessage}
import com.prisma.websocket.protocol.Request

import scala.util.{Failure, Success}

class SubscriptionDependenciesForTest()(implicit val system: ActorSystem, val materializer: ActorMaterializer)
    extends SubscriptionDependencies
    with TestApiDependencies {
  override implicit def self: ApiDependencies = this

  val config = ConfigLoader.load() match {
    case Success(c)   => c
    case Failure(err) => sys.error(s"Unable to load Prisma config: $err")
  }

  lazy val deployConnector = PostgresDeployConnector(config.databases.head)

  lazy val invalidationTestKit   = InMemoryPubSubTestKit[String]()
  lazy val sssEventsTestKit      = InMemoryPubSubTestKit[String]()
  lazy val responsePubSubTestKit = InMemoryPubSubTestKit[String]()
  lazy val requestsQueueTestKit  = InMemoryQueueTestKit[SubscriptionRequest]()

  override val invalidationSubscriber: PubSubSubscriber[SchemaInvalidatedMessage] = {
    invalidationTestKit.map[SchemaInvalidatedMessage]((_: String) => SchemaInvalidated)
  }

  override lazy val sssEventsPublisher: PubSubPublisher[String] = sssEventsTestKit
  override val sssEventsSubscriber: PubSubSubscriber[String]    = sssEventsTestKit

  override val responsePubSubPublisherV05: PubSubPublisher[SubscriptionSessionResponseV05] = {
    responsePubSubTestKit.map[SubscriptionSessionResponseV05](Converters.converterResponse05ToString)
  }
  override val responsePubSubPublisherV07: PubSubPublisher[SubscriptionSessionResponse] = {
    responsePubSubTestKit.map[SubscriptionSessionResponse](Converters.converterResponse07ToString)
  }
  override def responsePubSubSubscriber: PubSubSubscriber[String] = responsePubSubTestKit

  override def requestsQueuePublisher: QueuePublisher[Request] = requestsQueueTestKit.map[Request] { req: Request =>
    SubscriptionRequest(req.sessionId, req.projectId, req.body)
  }
  override val requestsQueueConsumer: QueueConsumer[SubscriptionRequest] = requestsQueueTestKit

  val projectFetcherPort                = 12345
  override val keepAliveIntervalSeconds = 1000
  val projectFetcherPath                = "project-fetcher"
  override val projectFetcher: ProjectFetcher = {
    ProjectFetcherImpl(Vector.empty, schemaManagerEndpoint = s"http://localhost:$projectFetcherPort/$projectFetcherPath", schemaManagerSecret = "empty")
  }
  override lazy val apiSchemaBuilder: SchemaBuilder = ???
  override lazy val sssEventsPubSub                 = ???
  override lazy val webhookPublisher                = ???

  override lazy val apiConnector                = PostgresApiConnector(config.databases.head)
  override lazy val sideEffectMutactionExecutor = SideEffectMutactionExecutorImpl()
  override lazy val mutactionVerifier           = DatabaseMutactionVerifierImpl
}
