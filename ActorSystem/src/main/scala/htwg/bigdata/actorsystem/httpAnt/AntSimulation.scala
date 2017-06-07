package htwg.bigdata.actorsystem.httpAnt

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.ExecutionContextExecutor
import akka.actor.ActorSystem
import akka.actor.Props
import akka.stream.{ActorMaterializer, Materializer}
import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory

/**
  * Created by tim on 06.04.17.
  */
object AntSimulation {
  private val counter = new AtomicInteger()
  private val random = scala.util.Random
  implicit val system: ActorSystem= ActorSystem()
  implicit val materializer: Materializer= ActorMaterializer()
  implicit def executor: ExecutionContextExecutor = system.dispatcher
  val config = ConfigFactory.load()
  val mainServerAdresse: String = config.getString("server")
  val antNumber:Int =config.getInt("antNumber")
  val targetPosition: Position = Position(config.getInt("destination.x"),config.getInt("destination.y"))
  def getCounter = counter.incrementAndGet

  def main(args: Array[String]) {
    val antSystem = ActorSystem("antsystem")
    for (it <- 1 to antNumber) {
      val myActor = antSystem.actorOf(Props(new Ant()))
      val waitDuration = random.nextDouble()
      system.scheduler.scheduleOnce(waitDuration seconds,myActor,"start")
    }
  }
}
