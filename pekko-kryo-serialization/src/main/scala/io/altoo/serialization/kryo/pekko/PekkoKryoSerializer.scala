package io.altoo.serialization.kryo.pekko

import io.altoo.serialization.kryo.scala
import io.altoo.serialization.kryo.scala.KryoSerializer
import org.apache.pekko.actor.ExtendedActorSystem
import org.apache.pekko.serialization.{ByteBufferSerializer, Serializer}

import java.nio.ByteBuffer

class PekkoKryoSerializer(val system: ExtendedActorSystem) extends KryoSerializer(system.settings.config, system.dynamicAccess.classLoader) with Serializer with ByteBufferSerializer {
  override protected def configKey: String = "pekko-kryo-serialization"
  override protected[kryo] val useManifest: Boolean = system.settings.config.getBoolean(s"$configKey.use-manifests")
  override protected[kryo] def prepareKryoInitializer(initializer: scala.DefaultKryoInitializer): Unit = initializer match {
    case init: DefaultKryoInitializer => init.system_=(system)
    case _                            => // nothing to do
  }

  // Serializer API
  override def identifier: Int = 123454323
  override def includeManifest: Boolean = useManifest

  def toBinary(obj: AnyRef): Array[Byte] = toBinaryInternal(obj)
  def fromBinary(bytes: Array[Byte], clazz: Option[Class[?]]): AnyRef = fromBinaryInternal(bytes, clazz)

  // ByteBufferSerializer API
  def toBinary(obj: AnyRef, buf: ByteBuffer): Unit = toBinaryInternal(obj.asInstanceOf[Any], buf)
  def fromBinary(buf: ByteBuffer, manifest: String): AnyRef = fromBinaryInternal(buf, Some(manifest))
}
