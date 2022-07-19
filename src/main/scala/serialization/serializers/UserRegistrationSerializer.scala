package serialization.serializers

import akka.serialization.Serializer
import org.apache.log4j.Logger
import serialization.UserRegistrationActor.UserRegistered

class UserRegistrationSerializer extends Serializer {
  val log = Logger.getLogger(this.getClass.getName)

  val SEPARATOR = ","
  override def identifier: Int = 999 //any number over 40, check doc

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case e @ UserRegistered(id, name, email) =>
      log.info(s"[serializing] message $e")
      val target = s"$id$SEPARATOR$name$SEPARATOR$email"
      target.getBytes()
    case _ => throw new IllegalArgumentException("Only accepting UserRegistered Events")
  }

  override def includeManifest: Boolean = false //if you wan to use a manifest in fromBinary

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    val result = new String(bytes)
    val content = result.split(SEPARATOR)
    val ans = UserRegistered(content(0).toInt, content(1), content(2))
    log.info(s"[deserialized-message] $ans")
    ans
  }
}