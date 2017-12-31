package cool.graph.websocket.services

import akka.actor.ActorSystem
import cool.graph.bugsnag.BugSnagger
import cool.graph.messagebus.pubsub.rabbit.RabbitAkkaPubSub
import cool.graph.messagebus._
import cool.graph.messagebus.queue.rabbit.RabbitQueue
import cool.graph.websocket.protocol.Request

trait WebsocketServices {
  val requestsQueuePublisher: QueuePublisher[Request]
  val responsePubSubSubscriber: PubSubSubscriber[String]
}

case class WebsocketCloudServives()(implicit val bugsnagger: BugSnagger, system: ActorSystem) extends WebsocketServices {
  import Request._

  val clusterLocalRabbitUri = sys.env("RABBITMQ_URI")

  val requestsQueuePublisher: QueuePublisher[Request] =
    RabbitQueue.publisher[Request](clusterLocalRabbitUri, "subscription-requests", durable = true)

  val responsePubSubSubscriber: PubSubSubscriber[String] =
    RabbitAkkaPubSub
      .subscriber[String](clusterLocalRabbitUri, "subscription-responses", durable = true)(bugsnagger, system, Conversions.Unmarshallers.ToString)
}

case class WebsocketDevDependencies(
    requestsQueuePublisher: QueuePublisher[Request],
    responsePubSubSubscriber: PubSubSubscriber[String]
) extends WebsocketServices
