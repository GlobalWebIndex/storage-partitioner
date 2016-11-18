package gwi.partitioner

import spray.json._

trait StorageCodec extends DefaultJsonProtocol {

  implicit object GranularityFormat extends RootJsonFormat[Granularity] {
    def write(g: Granularity) = JsString(g.toString)
    def read(value: JsValue) = Granularity(value.asInstanceOf[JsString].value)
  }

  implicit val s3Source = jsonFormat4(S3Source.apply)
  implicit val druidSource = jsonFormat6(DruidSource.apply)
  implicit val timePathPartitioner = jsonFormat(S3TimePartitioner.apply, "granularity", "pathFormat", "pathPattern")
  implicit val identityTimePartitioner = jsonFormat1(IdentityTimePartitioner)
  implicit val s3TimeStorage = jsonFormat3(S3TimeStorage.apply)
  implicit val druidTimeStorage = jsonFormat3(DruidTimeStorage.apply)

  implicit object StorageSourceFormat extends RootJsonFormat[StorageSource] {
    def write(a: StorageSource) = a match {
      case p: S3Source => p.toJson
      case p: DruidSource => p.toJson
    }
    def read(value: JsValue) =
      value.asJsObject.fields match {
        case f if f.contains("bucket") => value.convertTo[S3Source]
        case f if f.contains("dataSource") => value.convertTo[DruidSource]
      }
  }

  implicit object TimePartitionerFormat extends RootJsonFormat[TimePartitioner] {
    def write(a: TimePartitioner) = a match {
      case p: IdentityTimePartitioner => p.toJson
      case p: S3TimePartitioner => p.toJson
    }
    def read(value: JsValue) =
      value.asJsObject.fields match {
        case f if f.contains("granularity") => value.convertTo[TimePartitioner]
      }
  }

  implicit object timeStorage extends RootJsonFormat[TimeStorage.*] {
    def write(a: TimeStorage.*) = a match {
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
