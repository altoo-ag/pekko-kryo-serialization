package io.altoo.pekko.serialization.kryo

import com.esotericsoftware.kryo.Kryo
import io.altoo.pekko.serialization.kryo.serializer.scala.{ScalaCollectionSerializer, ScalaEnumNameSerializer, ScalaImmutableMapSerializer}

private[kryo] object ScalaVersionSerializers {
  def mapAndSet(kryo: Kryo): Unit = {
    kryo.addDefaultSerializer(classOf[scala.collection.MapFactory[_root_.scala.collection.Map]], classOf[ScalaImmutableMapSerializer])
  }

  def iterable(kryo: Kryo): Unit = {
    kryo.addDefaultSerializer(classOf[scala.collection.Iterable[_]], classOf[ScalaCollectionSerializer])
  }

  def enums(kryo: Kryo): Unit = {
    kryo.addDefaultSerializer(classOf[scala.runtime.EnumValue], classOf[ScalaEnumNameSerializer[scala.runtime.EnumValue]])
  }
}