import scala.collection.mutable
import scala.concurrent.ExecutionContext

case class Ant(id: String, x: Int, y: Int)
case class Ant_DTO(id: String, x_current: Int, y_current: Int, x_new: Int, y_new: Int)
case class Position(x:Int,y:Int)

class AntService(implicit val executionContext: ExecutionContext){

  val ants: mutable.HashMap[String, Ant] = mutable.HashMap[String, Ant]()
  def createAnt(ant: Ant) {
    ants.put(ant.id, ant)
  }

  def getAnt(id: String): Option[Ant] = {
    ants.get(id)
  }

  def deleteAnt(id: String) = {
    ants.remove(id)
  }

  def updateAnt(id: String, x: Int, y: Int) {
    val ant = Ant(id,x,y)
    ants.update(id,ant)
  }
  def getAntsPosition(): mutable.HashSet[Position] = {
    val positions : mutable.HashSet[Position] = mutable.HashSet()
    for ((key,ant) <- ants) {  positions.add(Position(ant.x,ant.y)) }
    positions
  }

}