import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods.DELETE
import akka.http.scaladsl.model.{HttpRequest, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.stream.{ActorMaterializer, Materializer}
import com.typesafe.config.{Config, ConfigFactory}
import net.liftweb.json._
import spray.json.DefaultJsonProtocol
import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.concurrent.ExecutionContextExecutor

case class Ant_DTO(id: String, x_current: Int, y_current: Int, x_new: Int, y_new: Int)
case class Position(x: Int, y: Int)

trait Service extends DefaultJsonProtocol {
  implicit val system: ActorSystem
  implicit val materializer: Materializer
  implicit val formats = DefaultFormats
  implicit def executor: ExecutionContextExecutor

  val logger: LoggingAdapter
  val positionSet: mutable.HashSet[Position] = mutable.HashSet[Position]()
  val routes = {
    logRequestResult("akka-http-microservice") {
      pathPrefix("ant") {
        pathEnd {
          put {
            decodeRequest {
              entity(as[String]) { content: String =>
                val json = parse(content)
                val ant = json.extract[Ant_DTO]
                println(ant.toString)
                var statusCode = 0
                val position: Position = Position(ant.x_new, ant.y_new)
                if (positionSet.add(position)) {
                  val responsibleServerNumberDelete = ant.x_current % numberOfServer
                  val serverUriDelete = ipAddressMap(responsibleServerNumberDelete)
                  statusCode = StatusCodes.Created.intValue
                  Http().singleRequest(HttpRequest(DELETE, uri = "http://" + serverUriDelete + "/ant", entity = compact(render(json))))
                } else {
                  statusCode = StatusCodes.Forbidden.intValue
                }
                complete(statusCode, "")
              }
            }
          } ~
            delete {
              decodeRequest {
                entity(as[String]) { content: String =>
                  val jValue = parse(content)
                  val ant = jValue.extract[Ant_DTO]
                  var statusCode = 0
                  val position: Position = Position(ant.x_current, ant.y_current)
                  if (positionSet.remove(position)) {
                    statusCode = StatusCodes.Accepted.intValue
                  } else {
                    statusCode = StatusCodes.NotFound.intValue
                  }
                  complete(statusCode, "")
                }
              }
            }
        }
      }
    }
  }
  var numberOfServer: Int = 0
  var ipAddressMap: mutable.HashMap[Int, String] = mutable.HashMap[Int, String]()
  def config: Config
}

object AkkaHttpMicroservice extends App with Service {
  override implicit val system = ActorSystem()
  override implicit val executor = system.dispatcher
  override implicit val materializer = ActorMaterializer()
  override val config = ConfigFactory.load()
  override val logger = Logging(system, getClass)

  numberOfServer = config.getStringList("servers").size()

  for ((ipAddress, id) <- config.getStringList("servers").zipWithIndex) {
    ipAddressMap.put(id, ipAddress)
  }
  println("Worker on Port " + config.getInt("http.port"))
  Http().bindAndHandle(routes, config.getString("http.interface"), config.getInt("http.port"))
}
