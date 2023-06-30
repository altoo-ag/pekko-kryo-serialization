package io.altoo.serialization.kryo.pekko

import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.serializers.FieldSerializer
import io.altoo.serialization.kryo.pekko.serializer.*
import io.altoo.serialization.kryo.scala.serializer.ScalaKryo
import org.apache.pekko.actor.{ActorRef, ExtendedActorSystem}

import scala.util.{Failure, Success}

/**
 * Extensible strategy to configure and customize kryo instance.
 */
class DefaultKryoInitializer extends io.altoo.serialization.kryo.scala.DefaultKryoInitializer {

  private var _system: ExtendedActorSystem = _
  private[kryo] def system_=(system: ExtendedActorSystem): Unit = _system = system

  /**
   * Provides access to the actor system and will be set during initialization.
   */
  protected final def system: ExtendedActorSystem = _system

  /**
   * Can be overridden to set a different field serializer before other serializer are initialized.
   * Note: register custom classes/serializer in `postInit`, otherwise default order might break.
   */
  override def preInit(kryo: ScalaKryo): Unit = {
    super.preInit(kryo)
  }

  /**
   * Registers serializer for standard/often used scala classes - override only if you know what you are doing!
   */
  override def init(kryo: ScalaKryo): Unit = {
    super.init(kryo)
    initPekkoSerializer(kryo)
  }

  /**
   * Can be overridden to register additional serializer and classes explicitly or reconfigure kryo.
   */
  override def postInit(kryo: ScalaKryo): Unit = {
    super.postInit(kryo)
  }

  protected def initPekkoSerializer(kryo: ScalaKryo): Unit = {
    kryo.addDefaultSerializer(classOf[org.apache.pekko.util.ByteString], classOf[ByteStringSerializer])
    kryo.addDefaultSerializer(classOf[ActorRef], new ActorRefSerializer(system))
  }
}
