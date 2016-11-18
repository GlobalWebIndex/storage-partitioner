package gwi

import gwi.druid.client.DruidClient

package object partitioner {

  implicit class TimeStoragePimp(underlying: TimeStorage.*) {
    import DruidTimeStorage.DruidTimeStoragePimp
    import S3TimeStorage.S3TimeStoragePimp
    import MemoryTimeStorage.MemoryTimeStoragePimp
    def client(implicit s3driver: S3Driver, druidDriver: DruidClient): TimeClient = underlying match {
      case s: S3TimeStorage => s.client(s3driver)
      case s: DruidTimeStorage => s.client(druidDriver)
      case s: MemoryTimeStorage => s.client
    }
  }

}
