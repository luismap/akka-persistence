package serialization.serializers
import akka.serialization.Serializer
import org.apache.log4j.Logger
import patternsAndBestPractices.EventAdapters.GuitarAddedV2

class GuitarAddedV2Serializer extends Serializer {

    val canlog = false

    val log = Logger.getLogger(this.getClass.getName)
    import patternsAndBestPractices.EventAdapters.GuitarType

    val SEPARATOR = ","

    override def identifier: Int = 1000 //any number over 40, check doc

    override def toBinary(o: AnyRef): Array[Byte] = o match {
      case e@GuitarAddedV2(id, model, make, qty, guitarType) =>
        if(canlog)log.info(s"[serializing v2] message $e")
        val target = s"$id$SEPARATOR$model$SEPARATOR$make$SEPARATOR$qty$SEPARATOR$guitarType"
        target.getBytes()
      case _ => throw new IllegalArgumentException("Only accepting GuitarAdded Events")
    }

    override def includeManifest: Boolean = false //if you wan to use a manifest in fromBinary

    override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
      val result = new String(bytes)
      val content = result.split(SEPARATOR)
      val ans = GuitarAddedV2(content(0), content(1), content(2), content(3).toInt, GuitarType.withName(content(4)))
      if(canlog)log.info(s"[deserialized-message-v2] $ans")
      ans
    }

}
