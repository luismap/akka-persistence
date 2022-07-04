package persistence

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.persistence.cassandra.query.UUIDPersistentRepr

import java.util.{Date, UUID}
import scala.concurrent.duration.DurationInt
import scala.util.Random

object PersistenctActors extends App {

  /**
   * scenario
   * Account keeps track of invoice.
   * - sum total per invoice
   */
  sealed trait InvoiceCommands
  case class Invoice(recipient: String, date: Date, amount: Int) extends InvoiceCommands

  sealed trait InvoiceEvents
  case class InvoiceRecorded(id: UUID, recipient: String, date: Date, amount: Int) extends InvoiceEvents

  class Accountant extends PersistentActor with ActorLogging {
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
        persist(InvoiceRecorded(latestInvoiceId,recipient, date, amount))
        /**
         * during the time gap of persist and callback function
         * persistentActor will stash all received messages to keep consistency
         */
        {
          e =>

            /**
             * safe to access mutable state here,
             * persist guarantees that not other thread access the actor
             * during the call back
             */
            latestInvoiceId = UUID.randomUUID()
            totalAmount += amount
            //correctly identify the sender of the message
            //sender() ! "PersistenceAck"
            log.info(s"[persisted] event with amount $amount for a total $totalAmount")
        }
    }


    //the id should be unique, and is the one use for recovery
    override def persistenceId: String = "accountant-01"
  }


  val guardian = ActorSystem("guardian")
  val accountant = guardian.actorOf(Props[Accountant],"accountant")
  val scheduler = guardian.scheduler
  implicit val execCtx = guardian.dispatcher
  val rnd = Random

 for ( e <- 0 to 10 ) {
   accountant ! Invoice("Bleak Inc.", new Date(), rnd.nextInt(1000))
   if (e % 2 == 0) accountant ! "print"
 }


  scheduler.scheduleOnce(6 seconds){
    guardian.terminate()
  }


}
