package io.altoo.serialization.kryo.pekko

import io.altoo.serialization.kryo.scala
import io.altoo.serialization.kryo.scala.KryoSerializer
import org.apache.pekko.actor.ExtendedActorSystem
import org.apache.pekko.serialization.{ByteBufferSerializer, Serializer}

import java.nio.ByteBuffer

class PekkoKryoSerializer(val system: ExtendedActorSystem) extends Serializer with ByteBufferSerializer {
  private val kryoSerializer = new KryoSerializer(system.settings.config, system.dynamicAccess.classLoader) {
    override protected[kryo] def configKey: String = "pekko-kryo-serialization"
    override protected[kryo] val useManifest: Boolean = system.settings.config.getBoolean(s"$configKey.use-manifests")
    override protected[kryo] def prepareKryoInitializer(initializer: scala.DefaultKryoInitializer): Unit = initializer match {
      case init: DefaultKryoInitializer => init.system_=(system)
      case _ => // nothing to do
    }
  }

  // Serializer API
  override def identifier: Int = 123454323
  override def includeManifest: Boolean = kryoSerializer.useManifest

  override def toBinary(obj: Any): Array[Byte] = kryoSerializer.toBinary(obj)
  override def fromBinary(bytes: Array[Byte], clazz: Option[Class[?]]): AnyRef = kryoSerializer.fromBinary(bytes, clazz)

  // ByteBufferSerializer API
  override def toBinary(obj: Any, buf: ByteBuffer): Unit = kryoSerializer.toBinary(obj, buf)
  override def fromBinary(buf: ByteBuffer, manifest: String): AnyRef = kryoSerializer.fromBinary(buf, Some(manifest))
}
