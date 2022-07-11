package persistence

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.persistence.PersistentActor

import scala.concurrent.duration.DurationInt

object AboutPersistAsync extends App {
  //usefull fo hightroughput scenarios
  //relaxes event ordering guarantees


  object CriticalStream {
    trait CmdCriticalStream

    case class Command(content: String) extends CmdCriticalStream

    trait EventCriticalStream

    case class EventRecorded(content: String) extends EventCriticalStream

    def props(id: String, agg: ActorRef): Props = Props(new CriticalStream(id, agg))
  }

  class CriticalStream(id: String, aggregator: ActorRef) extends PersistentActor with ActorLogging {

    import CriticalStream._

    override def receiveRecover: Receive = {
      case EventRecorded(content) => {
        log.info(s"[recovering-event] $content")
      }
    }

    override def receiveCommand: Receive = {
      case Command(content) =>
        log.info(s"[critical-stream] received $content")

        /**
         *by using persistAsync, we will continue processing messages
         * instead of stashing them
         * persistAsync
         * - good for high throughput(no waiting) when processing events
         * - bad if you want to keep order of events
         * - bad if you mutated state
         */
        persistAsync(EventRecorded(content)) {
          e =>
            log.info(s"[recorded] $e")
            aggregator ! e
        }
        //simulating som process
        val newContent = content + "_processed"
        log.info(s"[critical-stream-processing] processing $content")
        persistAsync(EventRecorded(newContent)) {
          e =>
            log.info(s"[recorde-processed] $e")
            aggregator ! e
        }
    }

    override def persistenceId: String = id
  }

  class EventAggregator extends Actor with ActorLogging {
    import CriticalStream._
    override def receive: Receive = {
      case EventRecorded(content) =>
        log.info(s"[aggregator] aggregating content $content")
    }
  }

  import CriticalStream._

  val caStreamGuardian = ActorSystem("caStreamGuardian")
  val aggregator = caStreamGuardian.actorOf(Props[EventAggregator])
  val caStreamActor = caStreamGuardian.actorOf(CriticalStream.props("ca-stream-01",aggregator))
  val scheduler = caStreamGuardian.scheduler
  implicit val exCtx = caStreamGuardian.dispatcher

  caStreamActor ! Command("compute01")
  caStreamActor ! Command("compute02")

  scheduler.scheduleOnce(6 seconds) {
    caStreamGuardian.terminate()
  }


}
