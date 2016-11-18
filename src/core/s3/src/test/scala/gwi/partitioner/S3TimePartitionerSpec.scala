package gwi.partitioner

import org.joda.time.{DateTime, IllegalFieldValueException}
import org.scalatest.{FreeSpec, Matchers}

import scala.util.{Failure, Success, Try}

class S3TimePartitionerSpec extends FreeSpec with Matchers {
  import Granularity._
  val exception = new IllegalFieldValueException("","")

  val bucket = "foo"
  val path = "bar/"

  val source = S3Source(bucket, path, "r", Map.empty)

  "toPath" in {
    val plainPartitioner = S3TimePartitioner.plain(HOUR)
    val qualifiedPartitioner = S3TimePartitioner.qualified(HOUR)
    assertResult(S3Pointer(bucket, path, "2011/02/03/04/"))(plainPartitioner.construct(plainPartitioner.buildPartition("2011-02-03T04:00:00.000/2011-02-03T05:00:00.000"), source))
    assertResult(S3Pointer(bucket, path, "y=2011/m=02/d=03/H=04/"))(qualifiedPartitioner.construct(qualifiedPartitioner.buildPartition("2011-02-03T04:00:00.000/2011-02-03T05:00:00.000"), source))
  }

  "test hour to date" in {
    assertPathToDate(HOUR, "2016/01/01/00/",                    "y=2016/m=01/d=01/H=00/",                        Some(Success(new DateTime(2016, 1, 1,  0, 0, 0, 0))))
    assertPathToDate(HOUR, "2011/03/15/20/50/43/Test0",         "y=2011/m=03/d=15/H=20/M=50/S=43/Test0",         Some(Success(new DateTime(2011, 3, 15, 20, 0, 0, 0))))
    assertPathToDate(HOUR, "/2011/03/15/20/50/43/Test0",        "/y=2011/m=03/d=15/H=20/M=50/S=43/Test0",        Some(Success(new DateTime(2011, 3, 15, 20, 0, 0, 0))))
    assertPathToDate(HOUR, "valid/2011/03/15/20/50/43/Test1",   "valid/y=2011/m=03/d=15/H=20/M=50/S=43/Test1",   Some(Success(new DateTime(2011, 3, 15, 20, 0, 0, 0))))
    assertPathToDate(HOUR, "valid/2011/03/15/20/50/Test2",      "valid/y=2011/m=03/d=15/H=20/M=50/Test2",        Some(Success(new DateTime(2011, 3, 15, 20, 0, 0, 0))))
    assertPathToDate(HOUR, "valid/2011/03/15/20/Test3",         "valid/y=2011/m=03/d=15/H=20/Test3",             Some(Success(new DateTime(2011, 3, 15, 20, 0, 0, 0))))
    assertPathToDate(HOUR, "valid/2011/03/15/Test4",            "valid/y=2011/m=03/d=15/Test4")
    assertPathToDate(HOUR, "valid/2011/03/Test5",               "valid/y=2011/m=03/Test5")
    assertPathToDate(HOUR, "valid/2011/Test6",                  "valid/y=2011/Test6")
    assertPathToDate(HOUR, "null////Test7",                     "null/y=/m=/d=/Test7")
    assertPathToDate(HOUR, "null/10/2011/23/Test8",             "null/m=10/y=2011/d=23/Test8")
    assertPathToDate(HOUR, "null/Test9",                        "null/Test9")
    assertPathToDate(HOUR, "",                                  "")
    assertPathToDate(HOUR, "error/2011/10/20/20/42/72/Test11",  "error/y=2011/m=10/d=20/H=20/M=42/S=72/Test11",  Some(Success(new DateTime(2011, 10, 20, 20, 0, 0, 0))))
    assertPathToDate(HOUR, "error/2011/10/20/20/90/24/Test12",  "error/y=2011/m=10/d=20/H=20/M=90/S=24/Test12",  Some(Success(new DateTime(2011, 10, 20, 20, 0, 0, 0))))
    assertPathToDate(HOUR, "error/2011/10/20/42/42/24/Test13",  "error/y=2011/m=10/d=20/H=42/M=42/S=24/Test13",  Some(Failure(exception)))
    assertPathToDate(HOUR, "error/2011/10/33/20/42/24/Test14",  "error/y=2011/m=10/d=33/H=20/M=42/S=24/Test14",  Some(Failure(exception)))
    assertPathToDate(HOUR, "error/2011/13/20/20/42/24/Test15",  "error/y=2011/m=13/d=20/H=20/M=42/S=24/Test15",  Some(Failure(exception)))
  }

  "test day to date" in {
    assertPathToDate(DAY, "2011/03/15/20/50/43/Test0",          "y=2011/m=03/d=15/H=20/M=50/S=43/Test0",          Some(Success(new DateTime(2011, 3, 15, 0, 0, 0, 0))))
    assertPathToDate(DAY, "/2011/03/15/20/50/43/Test0",         "/y=2011/m=03/d=15/H=20/M=50/S=43/Test0",         Some(Success(new DateTime(2011, 3, 15, 0, 0, 0, 0))))
    assertPathToDate(DAY, "valid/2011/03/15/20/50/43/Test1",    "valid/y=2011/m=03/d=15/H=20/M=50/S=43/Test1",    Some(Success(new DateTime(2011, 3, 15, 0, 0, 0, 0))))
    assertPathToDate(DAY, "valid/2011/03/15/20/50/Test2",       "valid/y=2011/m=03/d=15/H=20/M=50/Test2",         Some(Success(new DateTime(2011, 3, 15, 0, 0, 0, 0))))
    assertPathToDate(DAY, "valid/2011/03/15/20/Test3",          "valid/y=2011/m=03/d=15/H=20/Test3",              Some(Success(new DateTime(2011, 3, 15, 0, 0, 0, 0))))
    assertPathToDate(DAY, "valid/2011/03/15/Test4",             "valid/y=2011/m=03/d=15/Test4",                   Some(Success(new DateTime(2011, 3, 15, 0, 0, 0, 0))))
    assertPathToDate(DAY, "valid/2011/03/Test5",                "valid/y=2011/m=03/Test5")
    assertPathToDate(DAY, "valid/2011/Test6",                   "valid/y=2011/Test6")
    assertPathToDate(DAY, "null////Test7",                      "null/y=/m=/d=/Test7")
    assertPathToDate(DAY, "null/10/2011/23/Test8",              "null/m=10/y=2011/d=23/Test8")
    assertPathToDate(DAY, "null/Test9",                         "null/Test9")
    assertPathToDate(DAY, "",                                   "")
    assertPathToDate(DAY, "error/2011/10/20/20/42/72/Test11",   "error/y=2011/m=10/d=20/H=20/M=42/S=72/Test11",   Some(Success(new DateTime(2011, 10, 20, 0, 0, 0, 0))))
    assertPathToDate(DAY, "error/2011/10/20/20/90/24/Test12",   "error/y=2011/m=10/d=20/H=20/M=90/S=24/Test12",   Some(Success(new DateTime(2011, 10, 20, 0, 0, 0, 0))))
    assertPathToDate(DAY, "error/2011/10/20/42/42/24/Test13",   "error/y=2011/m=10/d=20/H=42/M=42/S=24/Test13",   Some(Success(new DateTime(2011, 10, 20, 0, 0, 0, 0))))
    assertPathToDate(DAY, "error/2011/10/33/20/42/24/Test14",   "error/y=2011/m=10/d=33/H=20/M=42/S=24/Test14",   Some(Failure(exception)))
    assertPathToDate(DAY, "error/2011/13/20/20/42/24/Test15",   "error/y=2011/m=13/d=20/H=20/M=42/S=24/Test15",   Some(Failure(exception)))
  }

  private def assertPathToDate(granularity: Granularity, plainPath: String, qualifiedPath: String, dateTime: Option[Try[DateTime]] = None) = {
    val plainP = S3TimePartitioner.plain(granularity)
    val qualifiedP = S3TimePartitioner.qualified(granularity)
    dateTime match {
      case None =>
        assertResult(None)(plainP.deconstruct(S3Pointer(bucket, path, plainPath)))
        assertResult(None)(qualifiedP.deconstruct(S3Pointer(bucket, path, qualifiedPath)))
      case Some(Success(date)) =>
        assertResult(granularity.bucket(date))(plainP.deconstruct(S3Pointer(bucket, path, plainPath)).get.value)
        assertResult(granularity.bucket(date))(qualifiedP.deconstruct(S3Pointer(bucket, path, qualifiedPath)).get.value)
      case Some(Failure(ex)) =>
        assertThrows[IllegalFieldValueException](plainP.deconstruct(S3Pointer(bucket, path, plainPath)))
        assertThrows[IllegalFieldValueException](qualifiedP.deconstruct(S3Pointer(bucket, path, qualifiedPath)))
    }
  }

}
