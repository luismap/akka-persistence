package patternsAndBestPractices

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.persistence.journal.{EventAdapter, EventSeq}
import com.typesafe.config.ConfigFactory
import org.apache.log4j.Logger
import patternsAndBestPractices.DataModel.WrittenCouponApplied

import scala.collection.mutable
import scala.concurrent.duration.DurationInt
import scala.util.Random

object DetachingModels extends App {
  /**
   * domain model: events our actor thinks it persist
   * data model: objects that get persisted
   *
   * made this two model independent of each other, and
   * as a side effect
   * - easier schema evolution
   *
   * example. a store that tracks coupons
   * - start with user schema 1
   * - then add a new field
   */

  import DomainModel._

  object CouponManager {
    def props(id: String): Props = Props(new CouponManager(id))
  }

  class CouponManager(id: String) extends PersistentActor with ActorLogging {
    val coupons: mutable.Map[String, User] = mutable.HashMap.empty

    override def receiveRecover: Receive = {
      case e@CouponApplied(code, user) =>
        log.info(s"[recovered] $e")
        coupons.put(code, user)
      case other => log.info(other.toString)
    }

    override def receiveCommand: Receive = {
      case e@ApplyCoupon(coupon, user) =>
        if (!coupons.contains(coupon.code)) {
          persist(CouponApplied(coupon.code, user)) {
            event =>
              log.info(s"[persisted] $event")
              coupons(coupon.code) = user
          }
        }
        else log.info(s"[coupon] aldready applied to $e")
      case Print => log.info(coupons.toString())

    }

    override def persistenceId: String = id
  }

  val guardian = ActorSystem("couponManagerGuardian", ConfigFactory.load().getConfig("detachingModel"))
  val cm = guardian.actorOf(CouponManager.props("cm-01"))
  val scheduler = guardian.scheduler
  implicit val exeCtx = guardian.dispatcher

  val rnd = Random

  def exec = {
    for (i <- 18 to 22) {
      val coupon = Coupon(s"coupon-$i", 100)
      val user = User(s"user-$i", s"user-$i@gmail.com", rnd.nextInt(2))
      cm ! ApplyCoupon(coupon, user)
    }
  }
  exec
  cm ! Print

  scheduler.scheduleOnce(3 seconds) {
    guardian.terminate()
  }


}

object DomainModel {
  case class User(id: String, email: String, gender: Int)

  case class Coupon(code: String, promotionAmount: Int)

  trait CouponMCmd

  case class ApplyCoupon(coupon: Coupon, user: User) extends CouponMCmd

  case object Print extends CouponMCmd

  trait CouponMEvent

  case class CouponApplied(code: String, user: User) extends CouponMEvent
}

object DataModel {
  case class WrittenCouponApplied(code: String, userId: String, email: String)

  //if you wan then to persist the new schema
  case class WrittenCouponAppliedV2(code: String, userId: String, email: String, gender: Int)
}

class ModelAdapter extends EventAdapter {

  import DomainModel._
  import DataModel._

  val log = Logger.getLogger(this.getClass.getName)

  override def fromJournal(event: Any, manifest: String): EventSeq = event match {
    case e@WrittenCouponApplied(code, userId, email) =>
      val ca = CouponApplied(code, User(userId, email, 0))
      log.info(s"[reading-from-datamodel] $e into $ca")
      EventSeq.single(ca)
    case e@WrittenCouponAppliedV2(code, userId, email, gender) =>
      val ca = CouponApplied(code, User(userId, email, gender))
      log.info(s"[reading-from-datamodel] $e into $ca")
      EventSeq.single(ca)
    case other => EventSeq.single(other)
  }

  override def manifest(event: Any): String = "CMA" //just any string, we don't use it now

  override def toJournal(event: Any): Any = event match {
    case e@CouponApplied(code, user) =>
      val nd = WrittenCouponAppliedV2(code, user.id, user.email, user.gender)
      log.info(s"[converting-to-datamodel] $e int $nd")
      nd
  }
}
