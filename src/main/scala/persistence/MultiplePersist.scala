package persistence

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.persistence.PersistentActor

import java.util.{Date, UUID}
import scala.concurrent.duration.DurationInt

object MultiplePersist extends App {

  /**
   * scenario: Accountan will persist two events
   * - tax record
   * - invoice record
   */

  trait AccountantCmd

  case class Invoice(recipient: String, date: Date, amount: Int) extends AccountantCmd

  trait AccountantEvent

  case class TaxRecord(taxId: String, recordId: UUID, date: Date, amount: Float) extends AccountantEvent
  case class InvoiceRecord(daId:String, invoiceId: UUID, recipient: String, date: Date, amount: Int) extends AccountantEvent

  object DiligentAccountant {
    def props(daId: String, taxId: String, taxAuth: ActorRef) =
      Props(new DiligentAccountant(daId, taxId, taxAuth))
  }

  class DiligentAccountant(daId: String, taxId: String, taxAuth: ActorRef) extends PersistentActor with
    ActorLogging {
    private var latestRecordId = UUID.randomUUID()
    private var latestInvoiceId = UUID.randomUUID()
    override def receiveRecover: Receive = {
      case event => log.info(s"[recover-event] $event")
    }

    override def receiveCommand: Receive = {
      case Invoice(recipient, date, amount) =>

        /**
         * the order on which the persist are send to journal are kept
         * you can think of persist as
         * journal ! persist(taxrecord)
         * journal ! persist(invoicerecord)
         * and for nested persist will also keep consistency
         */
        persist(TaxRecord(taxId, latestRecordId, date, amount * 0.3f)) {
          e =>
            taxAuth ! e
            latestRecordId = UUID.randomUUID()
            persist("tax record correct"){
              e => taxAuth ! e
            }
        }
        persist(InvoiceRecord(daId, latestInvoiceId, recipient, date, amount )){
          e => taxAuth ! e
            latestInvoiceId = UUID.randomUUID()
            persist("invoice record correct") {
              e => taxAuth ! e
            }
        }

    }

    override def persistenceId: String = daId
  }

  class TaxAuthority extends Actor with ActorLogging {
    override def receive: Receive = {
      case msg => log.info(s"[tax-authority] got $msg")
    }
  }

  val systemGuardian = ActorSystem("sysguardian")
  val taxAuthority = systemGuardian.actorOf(Props[TaxAuthority])
  val diligentAccountant = systemGuardian
    .actorOf(DiligentAccountant.props("da-01", "da-taid-01", taxAuthority))

  val scheduler = systemGuardian.scheduler
  implicit val execCtx = systemGuardian.dispatcher


  diligentAccountant ! Invoice("Bleak Inc.", new Date(), 1000)
  diligentAccountant ! Invoice("Apple Inc.", new Date(), 900)

  scheduler.scheduleOnce(6 seconds){
    systemGuardian.terminate()
  }

}
