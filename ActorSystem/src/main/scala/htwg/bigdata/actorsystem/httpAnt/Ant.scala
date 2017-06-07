package htwg.bigdata.actorsystem.httpAnt

import akka.actor.{Actor, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ActorMaterializer, Materializer}
import net.liftweb.json._
import spray.json.{DefaultJsonProtocol, _}
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}

/**
  * Created by tim on 06.04.17.
  */
case class Ant_DTO(id: String, x_current: Int, y_current: Int, x_new: Int, y_new: Int)

class Ant(var id: String = "-1", var position: Position = Position(-1, -1), var finalPosition: Position = Position(-1, -1)) extends DefaultJsonProtocol with Actor {

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: Materializer = ActorMaterializer()
  implicit def executor: ExecutionContextExecutor = system.dispatcher
  implicit val formats = DefaultFormats
  implicit val ant_dtoFormat = jsonFormat5(Ant_DTO)
  private val random = scala.util.Random

  private def calculateNewPosition(): Position = {
    // init result position with current position
    var result: Position = position

    // increase x OR y randomly OR (increase x and y)
    var randomInt = random.nextInt(3)

    // x already on border
    if (position.x >= finalPosition.x) {
      randomInt = 2
    }

    // y already on border
    if (position.y >= finalPosition.y) {
      randomInt = 1
    }

    if (randomInt == 0) {
      if (position.x < finalPosition.x && position.y < finalPosition.y) {
        result = Position(position.x + 1, position.y + 1)
      }
    } else if (randomInt == 1) {
      if (position.x < finalPosition.x) {
        result = Position(position.x + 1, position.y)
      }
    } else if (randomInt == 2) {
      if (position.y < finalPosition.y) {
        result = Position(position.x, position.y + 1)
      }
    }
    result
  }

  def startAnt() = {
    val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(POST, uri = "http://" + AntSimulation.mainServerAdresse + "/ant", entity = ""))
    for (response <- responseFuture) {
      val antDtoFuture: Future[Ant_DTO] = Unmarshal(response.entity.withContentType(ContentTypes.`application/json`)).to[Ant_DTO]
      for (antDto <- antDtoFuture) {
        this.id = antDto.id
        this.position = Position(antDto.x_current, antDto.y_current)
        val waitDuration = random.nextDouble()
        system.scheduler.scheduleOnce(waitDuration seconds, self, "move")
      }
    }
  }

  def moveAnt() = {
    val newPosition = calculateNewPosition()
    val antDto = Ant_DTO(id, position.x, position.y, newPosition.x, newPosition.y)
    val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(PUT, uri = "http://" + AntSimulation.mainServerAdresse + "/ant/" + id, entity = antDto.toJson.toString()))
    for (response <- responseFuture) {
      response.status match {
        case StatusCodes.OK => {
          position = newPosition
          if (position.x == finalPosition.x && position.y == finalPosition.y) {
            println(id + " ist am Ziel - Zähler: " + AntSimulation.getCounter)
            context.stop(self)
            println("--------------------")
          } else {
            val waitDuration = random.nextDouble()
            system.scheduler.scheduleOnce(waitDuration seconds, self, "move")
          }
        }
        case StatusCodes.Forbidden =>
          val waitDuration = random.nextDouble()
          system.scheduler.scheduleOnce(waitDuration seconds, self, "move")
      }
    }
  }


  def receive = {
    case "start" ⇒
      this.finalPosition = AntSimulation.targetPosition
      startAnt()
    case "move" ⇒
      moveAnt()
    case _ ⇒ println("received unknow message")
  }
}
