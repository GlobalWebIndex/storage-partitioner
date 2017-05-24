package gwi.partitioner

import com.typesafe.scalalogging.LazyLogging

case class Expression(exp: String, variable: String, value: Option[String])
object Expression {
  def apply(expression: String): Expression = {
    expression.stripPrefix("${").stripSuffix("}").split(":-").filter(_.nonEmpty) match {
      case Array(variable) =>
        Expression(expression, variable, sys.env.get(variable))
      case Array(variable,default) =>
        Expression(expression, variable, Some(sys.env.getOrElse(variable, default)))
    }
  }
}

object Config extends StorageCodec with LazyLogging {
  import spray.json._

  private def storagePath(name: String) = s"storages/$name.json"
  private def storageLines(path: String) = {
    require(getClass.getClassLoader.getResourceAsStream(path) != null, s"Resource $path doesn't exist on classpath !!!")
    IO.streamToSeq(getClass.getClassLoader.getResourceAsStream(path), 8192)
  }
  private def extrapolate(content: String): String = {
    val expressions = "\\$\\{.+?\\}".r.findAllIn(content).map(Expression(_)).toList
    expressions.foldLeft(content) {
      case (acc, Expression(exp,_,Some(value))) =>
        acc.replace(exp, value)
      case (acc, Expression(exp,_,None)) =>
        logger.warn(s"Please export variable $exp !!!")
        acc
    }
  }

  def load[S <: TimeStorage.* : JsonReader](storageName: String): S =
    extrapolate(
      storageLines(
        storagePath(storageName)
      ).mkString("\n")
    ).parseJson.convertTo[S]

  def load(storageNames: List[String]): List[TimeStorage.*] =
    storageNames
      .map(storagePath)
      .map(storageLines(_).mkString("\n"))
      .map(extrapolate)
      .map(_.parseJson.convertTo[TimeStorage.*])

}
