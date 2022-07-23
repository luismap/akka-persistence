package serialization.serializers

import akka.serialization.Serializer
import org.apache.log4j.Logger
import patternsAndBestPractices.EventAdapters.GuitarAdded

class GuitarAddedSerializer extends Serializer {
  val canlog = false

  val log = Logger.getLogger(this.getClass.getName)
  import patternsAndBestPractices.EventAdapters.GuitarType.GuitarType

  val SEPARATOR = ","

  override def identifier: Int = 999 //any number over 40, check doc

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case e@GuitarAdded(id, model, make, qty) =>
      if(canlog)log.info(s"[serializing] message $e")
      val target = s"$id$SEPARATOR$model$SEPARATOR$make$SEPARATOR$qty"
      target.getBytes()
    case _ => throw new IllegalArgumentException("Only accepting GuitarAdded Events")
  }

  override def includeManifest: Boolean = false //if you wan to use a manifest in fromBinary

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    val result = new String(bytes)
    val content = result.split(SEPARATOR)
    val ans = GuitarAdded(content(0), content(1), content(2), content(3).toInt)
    if(canlog)log.info(s"[deserialized-message-v1] $ans")
    ans
  }
}
