package serialization.serializers.detachingModels

import akka.serialization.Serializer
import org.apache.log4j.Logger
import patternsAndBestPractices.DataModel.WrittenCouponAppliedV2

class WrittenCouponAppliedSerializerV2 extends Serializer {
  val SEPARATOR = ","
  val log = Logger.getLogger(this.getClass.getName)
  val canlog = true

  override def identifier: Int = 999

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case e@WrittenCouponAppliedV2(code, userId, email, gender) =>
      val ser = s"$code$SEPARATOR$userId$SEPARATOR$email$SEPARATOR$gender".getBytes()
      if (canlog) log.info(s"[serializing] $e")
      ser

    case _ => throw new IllegalArgumentException("Serialize only WrittenCouponApplied")
  }

  override def includeManifest: Boolean = false

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    val str = new String(bytes)
    val parsed = str.split(SEPARATOR)
    val ans = WrittenCouponAppliedV2(parsed(0), parsed(1), parsed(2), parsed(3).toInt)
    if (canlog) log.info(s"[deserialized] $ans")
    ans
  }
}
