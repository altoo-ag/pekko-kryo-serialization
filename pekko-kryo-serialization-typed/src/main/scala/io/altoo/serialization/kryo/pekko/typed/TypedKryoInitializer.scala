package io.altoo.serialization.kryo.pekko.typed

import org.apache.pekko.actor.ExtendedActorSystem
import org.apache.pekko.actor.typed
import io.altoo.serialization.kryo.pekko.DefaultKryoInitializer
import io.altoo.serialization.kryo.pekko.typed.serializer.TypedActorRefSerializer
import io.altoo.serialization.kryo.scala.serializer.ScalaKryo

/**
 * Extensible strategy to configure and customize kryo instance.
 */
class TypedKryoInitializer extends DefaultKryoInitializer {

  protected final def typedSystem: typed.ActorSystem[Nothing] = typed.ActorSystem.wrap(system)

  override def init(kryo: ScalaKryo): Unit = {
    super.init(kryo)
    initPekkoTypedSerializer(kryo)
  }

  /**
   * Registers serializer for standard akka classes - override only if you know what you are doing!
   */
  protected def initPekkoTypedSerializer(kryo: ScalaKryo): Unit = {
    kryo.addDefaultSerializer(classOf[typed.ActorRef[Nothing]], new TypedActorRefSerializer(typedSystem))
  }
}
