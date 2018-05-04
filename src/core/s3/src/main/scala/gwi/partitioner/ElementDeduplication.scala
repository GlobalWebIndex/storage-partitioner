package gwi.partitioner

import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}

import scala.collection.mutable

class ElementDeduplication[A, B](extract: A => B)
  extends GraphStage[FlowShape[A, A]] {

  private[this] val in = Inlet[A]("Deduplicator.in")
  private[this] val out = Outlet[A]("Deduplicator.out")

  val shape: FlowShape[A, A] = FlowShape.of(in, out)

  private[this] val cache = mutable.Set.empty[B]

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler {
      setHandlers(in, out, this)

      override def onPush(): Unit = {
        val element = grab(in)
        val dedupValue = extract(element)
        if (!cache.contains(dedupValue)) {
          cache.add(dedupValue)
          push(out, element)
        } else {
          pull(in)
        }
      }

      override def onPull(): Unit = pull(in)

    }
}