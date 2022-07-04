package persistence

import akka.actor.{ActorLogging, ActorSystem, Kill, PoisonPill, Props, actorRef2Scala}
import akka.persistence.PersistentActor
import akka.persistence.cassandra.query.UUIDPersistentRepr
import akka.stream.Supervision.Stop

import java.util.{Date, UUID}
import scala.concurrent.duration.DurationInt
import scala.util.Random

object PersistenctActors extends App {

  /**
   * scenario
   * Account keeps track of invoice.
   * - sum total per invoice
   * about persitence Actor
   * - handle shutdown logic yourself
   *    because now the message will be handle by the persistence actor logic in the same mailbox
   *
   */
  sealed trait InvoiceCommands

  case class Invoice(recipient: String, date: Date, amount: Int) extends InvoiceCommands

  case class InvoiceBulk(invoices: List[Invoice]) extends InvoiceCommands

  sealed trait InvoiceEvents

  case class InvoiceRecorded(id: UUID, recipient: String, date: Date, amount: Int) extends InvoiceEvents

  sealed trait SpecialCommands
  object Shutdown extends SpecialCommands

  class Accountant(id: String) extends PersistentActor with ActorLogging {
    var latestInvoiceId = UUID.randomUUID()
    var totalAmount = 0

    /**
     * best practice:
     * - follow same logic of receiveCommand
     */
    override def receiveRecover: Receive = {
      case InvoiceRecorded(id, _, _, amount) =>
        latestInvoiceId = id
        totalAmount += amount
        log.info(s"[recovering] updating state with $amount for a total of $totalAmount")
    }

    /**
     * when we receive a command
     * - create event to persist to store
     * - persist event and pass a callback function
     * - update actor state
     */
    override def receiveCommand: Receive = {
      case "print" =>
        log.info(s"[printing] current invoice: $latestInvoiceId with total: $totalAmount")
      case Invoice(recipient, date, amount) =>
        log.info(s"[invoice] $recipient with total $amount")
        persist(InvoiceRecorded(latestInvoiceId, recipient, date, amount)) {
          /**
           * during the time gap of persist and callback function
           * persistentActor will stash all received messages to keep consistency
           */
          e =>

            /**
             * safe to access mutable state here,
             * persist guarantees that not other thread access the actor
             * during the call back
             */
            //NEVER CALL PERSIST OR PERSISTALL from a future
            latestInvoiceId = UUID.randomUUID()
            totalAmount += amount
            //correctly identify the sender of the message
            //sender() ! "PersistenceAck"
            log.info(s"[persisted] event with amount $amount for a total $totalAmount")
        }
      case InvoiceBulk(invoices) =>
        //create events, persist all events, update actor state for each event persisted
        val events = invoices.map(i => InvoiceRecorded(UUID.randomUUID(), i.recipient, i.date, i.amount))
        persistAll(events) {
          e =>
            latestInvoiceId = e.id
            totalAmount += e.amount
            log.info(s"[persisted] event wit amount ${e.amount} total: $totalAmount ")
        }
      case Shutdown => context.stop(self)
    }


    //the id should be unique, and is the one use for recovery
    override def persistenceId: String = id

    override protected def onPersistRejected(cause: Throwable, event: Any, seqNr: Long): Unit = {
      log.error(s"[persist-rejected] fail to persist $event cause: $cause")
      super.onPersistRejected(cause, event, seqNr)
    }

    /**
     * method call if persisted failed
     * - actor will be stopped
     * Best practice: use the backoff pattern
     *
     * @param cause
     * @param event
     * @param seqNr
     */
    override protected def onPersistFailure(cause: Throwable, event: Any, seqNr: Long): Unit = {
      log.error(s"[persist-failure] failed to persist $event cause: $cause")
      super.onPersistFailure(cause, event, seqNr)
    }

  }

  val guardian = ActorSystem("guardian")
  val accountant = guardian.actorOf(Props(new Accountant("accountant-01")), "accountant01")
  val accountantShutdown = guardian.actorOf(Props(new Accountant("accountant-02")), "accountant02")
  val scheduler = guardian.scheduler
  implicit val execCtx = guardian.dispatcher
  val rnd = Random

  for (e <- 0 to 10) accountant ! Invoice("Bleak Inc.", new Date(), rnd.nextInt(1000))
  for (e <- 0 to 10) accountantShutdown ! Invoice("Bleak Inc.", new Date(), rnd.nextInt(1000))

  accountant ! "print"

  val invoices = for( _ <- 0 to 10 ) yield Invoice("Apple Inc.", new Date(), rnd.nextInt(100))

  accountant ! InvoiceBulk(invoices.toList)


  //testing shutdown behaviour
  accountant ! Shutdown //correct way, will handle message in stashing
  accountantShutdown ! Kill //wrong, will avoid stashing

  scheduler.scheduleOnce(6 seconds) {
    guardian.terminate()
  }


}
