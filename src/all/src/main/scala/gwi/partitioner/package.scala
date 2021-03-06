package gwi

import akka.stream.ActorMaterializer
import com.datastax.driver.core.Session
import gwi.druid.client.DruidClient

package object partitioner {

  implicit class TimeStoragePimp(underlying: TimeStorage.*) {
    import DruidTimeStorage.DruidTimeStoragePimp
    import MemoryTimeStorage.MemoryTimeStoragePimp
    import S3TimeStorage.S3TimeStoragePimp
    def getClient(implicit s3client: BlobStorageClient, druidDriver: DruidClient, session: Session, mat: ActorMaterializer): TimeClient = underlying match {
      case s: S3TimeStorage => s.client(s3client, mat)
      case s: DruidTimeStorage => s.client(druidDriver)
      case s: MemoryTimeStorage => s.client
      case s: CqlTimeStorage => s.client(session, mat)
    }
  }
}
