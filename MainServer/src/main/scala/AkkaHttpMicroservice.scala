import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.HttpMethods.{DELETE, _}
import akka.http.scaladsl.model.{HttpRequest, StatusCodes, _}
import akka.http.scaladsl.server.Directives._
import akka.stream.{ActorMaterializer, Materializer}
import com.typesafe.config.{Config, ConfigFactory}
import net.liftweb.json._
import spray.json.{DefaultJsonProtocol, _}
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContextExecutor, _}
import scala.concurrent.duration.Duration
import scala.collection.mutable

trait Service extends DefaultJsonProtocol {
  implicit def executor: ExecutionContextExecutor
  val logger: LoggingAdapter
  implicit val system: ActorSystem
  implicit val materializer: Materializer
  implicit val formats = DefaultFormats
  implicit val ant_dtoFormat = jsonFormat5(Ant_DTO)
  implicit val antFormat = jsonFormat3(Ant)

  val antService: AntService = new AntService()
  val routes = {
    logRequestResult("akka-http-microservice") {
      pathPrefix("ants") {
        pathEnd {
          get {
            val positions = antService.getAntsPosition()
            val strBuilder = new StringBuilder
            for (row <- 0 to rows) {
              for (col <- 0 to columns) {
                if (positions.exists(p => p.x == col && p.y == row)) {
                  strBuilder ++= " "
                  strBuilder ++= "@"
                } else {
                  strBuilder ++= "  "
                }
              }
              strBuilder ++= "\n"
            }
            complete(strBuilder.toString)
          }
        }
      }~
        pathPrefix("ant") {
          path(Segment) { id =>
            get {
              /* einzelne Ameise bekommen*/
              val ant = antService.getAnt(id)
              complete(ant.toJson.toString())
            } ~
              put {
                decodeRequest {
                  entity(as[String]) { content: String =>
                    var statusCode = 0
                    val blocker = Promise[Unit]()
                    val futureBlocker = blocker.future
                    val json = parse(content)
                    val ant = json.extract[Ant_DTO]
                    println(ant.toString)
                    /* Ameise will aus dem Feld laufen */
                    if(ant.x_new > columns || ant.y_new > rows){
                      statusCode = StatusCodes.Forbidden.intValue
                      complete(statusCode,"")
                    }
                    val responsibleServerNumberMove = ant.x_new % numberOfServer
                    val serverUriMove = ipAddressMap(responsibleServerNumberMove)
                    /* Kollisionsüberprüfung über HTTP auf Worker Server */
                    val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(PUT, uri = "http://" + serverUriMove + "/ant", entity = ant.toJson.toString()))
                    for(response <- responseFuture) {
                      response.status match{

                        /* Ameise hat sich bewegt */
                        case StatusCodes.Created => {
                          /* Ameise bewegen auf Main Server */
                          antService.updateAnt(ant.id, ant.x_new, ant.y_new)}
                          statusCode = StatusCodes.OK.intValue

                        /* Ameise hat sich nicht bewegt */
                        case StatusCodes.Forbidden => {
                          statusCode = StatusCodes.Forbidden.intValue
                        }
                      }
                      /* Response zur Ameise wird freigegeben */
                      blocker.success()
                    }
                    /* Falls die Ameise auf dem Zielfeld ankommt gibt sie dieses wieder frei */
                    if(ant.x_new == destination_x && ant.y_new == destination_y) {
                      val ant_for_delete :Ant_DTO = Ant_DTO(ant.id,ant x_new, ant.y_new,ant.x_new,ant.y_new)
                      Await.result(responseFuture, Duration.Inf)
                      Http().singleRequest(HttpRequest(DELETE, uri = "http://" + serverUriMove + "/ant", entity = ant_for_delete.toJson.toString()))
                    }

                    /* warten ob die Ameise gelaufen ist */
                    Await.result(futureBlocker, Duration.Inf)
                    complete(statusCode,"")
                  }
                }
              } ~
              delete {
                val ant = antService.getAnt(id)
                antService.deleteAnt(id)
                complete(ant.toJson.toString())
              }
          }~
            pathEnd {
              /* Ameise anlegen */
              post {
                val r = scala.util.Random
                val x_current : Int = r.nextInt(2)
                val y_current : Int = r.nextInt(2)
                val ant = Ant(getCounter.toString ,x_current,y_current)
                antService.createAnt(ant)
                val antDTO = Ant_DTO(ant.id,ant.x,ant.y, -1,-1)
                complete(antDTO)
              }
            }
        }
    }
  }
  private val counter = new AtomicInteger()
  var numberOfServer: Int = 0
  var ipAddressMap: mutable.HashMap[Int,String] = mutable.HashMap[Int, String]()
  var rows: Int = 0
  var columns: Int = 0
  var destination_x = 0
  var destination_y = 0

  def config: Config

  def getCounter = counter.incrementAndGet
}

object AkkaHttpMicroservice extends App with Service {
  override implicit val system = ActorSystem()
  override implicit val executor = system.dispatcher
  override implicit val materializer = ActorMaterializer()
  override val config = ConfigFactory.load()
  override val logger = Logging(system, getClass)
  rows = config.getInt("fieldWith.rows")
  columns = config.getInt("fieldWith.columns")
  destination_x = config.getInt("destination.x")
  destination_y = config.getInt("destination.y")

  numberOfServer = config.getStringList("servers").size()

  for ((ipAddress, iterator) <- config.getStringList("servers").zipWithIndex) {
    ipAddressMap.put(iterator,ipAddress)
  }

  println("MainServer on Port "+config.getInt("http.port"))
  Http().bindAndHandle(routes, config.getString("http.interface"), config.getInt("http.port"))
}
