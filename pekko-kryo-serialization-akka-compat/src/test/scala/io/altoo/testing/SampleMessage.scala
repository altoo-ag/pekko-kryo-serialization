package io.altoo.testing

import org.apache.pekko.actor.ActorRef

// Mirror class using Pekko ActorRef instead of Akka ActorRef
case class SampleMessage(actorRef: ActorRef) extends Serializable
