package patternsAndBestPractices

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.persistence.journal.{EventAdapters, EventSeq, ReadEventAdapter}
import com.typesafe.config.{Config, ConfigFactory}

import scala.collection.mutable
import scala.concurrent.duration.DurationInt

object EventAdapters extends App {

  /**
   * exmple:
   * a music store
   *
   * first- we start with V1 of guitar, after a while
   * we need to update to V2
   *
   * - but we need to recover events that were created with older version
   * - use EventAdapters
   */

  object GuitarType extends Enumeration {
    type GuitarType = Value
    val ACOUSTIC, ELECTRIC = Value
  }

  import GuitarType._

  trait InventoryItem

  case class Guitar(id: String, model: String, make: String) extends InventoryItem

  case class GuitarV2(id: String, model: String, make: String, guitarType: GuitarType = ACOUSTIC) extends InventoryItem

  trait InventoryManagerCmd

  case class AddGuitar(guitar: GuitarV2, qty: Int) extends InventoryManagerCmd

  case object Print extends InventoryManagerCmd

  trait InventoryManagerEvent

  case class GuitarAdded(id: String, model: String, make: String, qty: Int) extends InventoryManagerEvent
  case class GuitarAddedV2(id: String, model: String, make: String,qty: Int, guitarType: GuitarType = ACOUSTIC) extends InventoryManagerEvent

  object InventoryManager {
    def props(imId: String): Props = Props(new InventoryManager(imId))
  }

  class InventoryManager(imId: String) extends PersistentActor with ActorLogging {
    val inventory = mutable.HashMap.empty[InventoryItem, Int]

    override def receiveRecover: Receive = {
      case e@GuitarAddedV2(id, model, make, qty,guitarType) => {
        log.info(s"[recovered-event] $e")
        updateInventory(GuitarV2(id, model, make,guitarType), qty)
      }
      case e: AnyRef => log.info(s"[other-event] $e")
    }

    override def receiveCommand: Receive = {
      case AddGuitar(item@GuitarV2(id, model, make,guitarType), qty) =>
        persist(GuitarAddedV2(id, model, make, qty, guitarType)) {
          e =>
            log.info(s"[added-guitar] $e")
            updateInventory(item, qty)
        }
      case Print =>
        log.info(s"[printing-status] ${inventory.foreach(println)}")
    }

    def updateInventory[A <: InventoryItem](item: A, qty: Int) = {
      inventory(item) = inventory.getOrElse(item, 0) + qty
    }

    override def persistenceId: String = imId
  }

  /**
   * creating the Read event adapter
   * normal workflow of persisted events
   *
   * journal -> serializer -> read event adapter -> actor receive event
   * has some bytes -> has V1  of guitar -> convert V1 to V2 -> received V2
   *
   * there is also writeEventAdapter and EventAdapter, which
   * actor receive -> write-event-adapter -> serialize -> toJournal
   *
   * and envent adapter, which implements boths.
   *
   * WriteEventAdapter, useful for backwards compatibility
   */
  class GuitarReadEventAdapter extends ReadEventAdapter {
    override def fromJournal(event: Any, manifest: String): EventSeq = event match {
      case GuitarAdded(id, model, make, qty) =>
        //convert to current Version
        EventSeq.single(GuitarAddedV2(id, model, make, qty))
      case otherEvents => EventSeq.single(otherEvents)
    }
  }

  val guardian = ActorSystem("guardian", ConfigFactory.load().getConfig("eventAdapters"))
  val im = guardian.actorOf(InventoryManager.props("Ganstar.AB.02"))
  val scheduler = guardian.scheduler
  implicit val execCtx = guardian.dispatcher

  for (i <- 0 to 10) im ! AddGuitar(GuitarV2(i.toString, "Stravious", "WoodIncs", ELECTRIC), 5)


  scheduler.scheduleOnce(3 seconds) { im ! Print }

  scheduler.scheduleOnce(6 seconds) {
    guardian.terminate()
  }
}
