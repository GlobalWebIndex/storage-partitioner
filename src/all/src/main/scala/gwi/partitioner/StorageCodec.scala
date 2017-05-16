package gwi.partitioner

import spray.json._

trait StorageCodec extends DefaultJsonProtocol {

  private[this] implicit object GranularityFormat extends RootJsonFormat[Granularity] {
    def write(g: Granularity) = JsString(g.toString)
    def read(value: JsValue) = Granularity(value.asInstanceOf[JsString].value)
  }

  private[this] implicit val s3Source                 = jsonFormat5(S3Source.apply)
  private[this] implicit val druidSource              = jsonFormat7(DruidSource.apply)
  private[this] implicit val timePathPartitioner      = jsonFormat(S3TimePartitioner.apply, "granularity", "pathFormat", "pathPattern")
  private[this] implicit val plainTimePartitioner     = jsonFormat1(PlainTimePartitioner)

  private[this] implicit object StorageSourceFormat extends RootJsonFormat[StorageSource] {
    def write(a: StorageSource): JsValue = a match {
      case p: S3Source => p.toJson
      case p: DruidSource => p.toJson
    }
    def read(value: JsValue): StorageSource =
      value.asJsObject.fields match {
        case f if f.contains("bucket") => value.convertTo[S3Source]
        case f if f.contains("dataSource") => value.convertTo[DruidSource]
      }
  }

  private[this] implicit object TimePartitionerFormat extends RootJsonFormat[TimePartitioner] {
    def write(a: TimePartitioner): JsValue = a match {
      case p: PlainTimePartitioner => p.toJson
      case p: S3TimePartitioner => p.toJson
    }
    def read(value: JsValue): TimePartitioner =
      value.asJsObject.fields match {
        case f if f.contains("granularity") => value.convertTo[TimePartitioner]
      }
  }

  implicit val s3TimeStorage          = jsonFormat3(S3TimeStorage.apply)
  implicit val druidTimeStorage       = jsonFormat3(DruidTimeStorage.apply)

  implicit object timeStorage extends RootJsonFormat[TimeStorage.*] {
    def write(a: TimeStorage.*): JsValue = a match {
      case p: S3TimeStorage => p.toJson
      case p: DruidTimeStorage => p.toJson
    }
    def read(value: JsValue) =
      value.asJsObject.fields("source").convertTo[StorageSource] match {
        case _: S3Source => value.convertTo[S3TimeStorage]
        case _: DruidSource => value.convertTo[DruidTimeStorage]
      }
  }

}
