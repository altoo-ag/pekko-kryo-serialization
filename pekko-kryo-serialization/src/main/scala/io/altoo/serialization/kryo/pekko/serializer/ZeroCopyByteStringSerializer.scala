/**
 * *****************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ****************************************************************************
 */

package io.altoo.serialization.kryo.pekko.serializer

import com.esotericsoftware.kryo.kryo5.io.{ByteBufferOutput, Input, Output}
import com.esotericsoftware.kryo.kryo5.{Kryo, Serializer}
import org.apache.pekko.util.ByteString

/**
 * Serializer for pekko [[ByteString]]
 */
class ZeroCopyByteStringSerializer() extends ByteStringSerializer {

  override def read(kryo: Kryo, input: Input, typ: Class[? <: ByteString]): ByteString = {
    val len = input.readInt(true)
    // access Kryo's internal buffer directly
    val buf = input.getBuffer
    val pos = input.position

    // wrap the internal buffer without copying
    val byteString = ByteString.fromArrayUnsafe(buf, pos, len)

    // advance Kryo's position manually
    input.setPosition(pos + len)
    byteString
  }

  override def write(kryo: Kryo, output: Output, obj: ByteString): Unit = {
    val len = obj.size
    output.writeInt(len, true)
    output match {
      case outputBB: ByteBufferOutput =>
        val posBefore = outputBB.position()
        // write each ByteBuffer in the ByteString directly
        val byteBuffer = outputBB.getByteBuffer
        val posBeforeByteBuffer = byteBuffer.position()
        obj.asByteBuffers.foreach { bb =>
          val remaining = bb.remaining()
          // copy directly into ByteBufferOutput
          byteBuffer.put(bb.duplicate())
        }
        // fix the position since ByteBufferOutput has internal position and buffer position and setPosition writes both - but put advanced
        outputBB.setPosition(posBefore + obj.length)
        byteBuffer.position(posBeforeByteBuffer + obj.length)
        assert(posBefore == posBeforeByteBuffer)
      case _ =>
        output.writeBytes(obj.toArrayUnsafe()) //writeBytes anyway copies byte[]
    }
  }
}
