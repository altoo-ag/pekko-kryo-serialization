package io.altoo.pekko.serialization.kryo.compat

import io.altoo.pekko.serialization.kryo.compat.serializer.{CompatActorRefSerializer, CompatTypedActorRefSerializer}
import io.altoo.serialization.kryo.pekko.serializer.ByteStringSerializer
import io.altoo.serialization.kryo.pekko.typed.TypedKryoInitializer
import io.altoo.serialization.kryo.scala.serializer.ScalaKryo

class TypedAkkaCompatKryoInitializer extends TypedKryoInitializer {

  override protected def initPekkoSerializer(kryo: ScalaKryo): Unit = {
    super.initPekkoSerializer(kryo)

    // registering dummy Akka ActorRef to provide wire compatibility
    kryo.addDefaultSerializer(classOf[akka.actor.ActorRef], new CompatActorRefSerializer(system))
    kryo.addDefaultSerializer(classOf[akka.actor.RepointableActorRef], new CompatActorRefSerializer(system))
    // registering dummy Akka ByteString to provide wire compatibility
    kryo.addDefaultSerializer(classOf[akka.util.ByteString], classOf[ByteStringSerializer])
  }

  override protected def initPekkoTypedSerializer(kryo: ScalaKryo): Unit = {
    super.initPekkoTypedSerializer(kryo)

    // registering dummy Akka ActorRef to provide wire compatibility
    kryo.addDefaultSerializer(classOf[akka.actor.typed.ActorRef[Nothing]], new CompatTypedActorRefSerializer(typedSystem))
  }
}
