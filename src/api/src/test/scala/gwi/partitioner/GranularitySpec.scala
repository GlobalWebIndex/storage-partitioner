package gwi.partitioner

import org.joda.time._
import org.scalatest.{FreeSpec, Matchers}

class GranularitySpec extends FreeSpec with Matchers {
  import Granularity._

  "testGetUnits" in {
    assertResult(Seconds.seconds(1))(SECOND.getUnits(1))
    assertResult(Minutes.minutes(1))(MINUTE.getUnits(1))
    assertResult(Hours.hours(1))(HOUR.getUnits(1))
    assertResult(Days.days(1))(DAY.getUnits(1))
    assertResult(Weeks.weeks(1))(WEEK.getUnits(1))
    assertResult(Months.months(1))(MONTH.getUnits(1))
  }

  "testNumIn" in {
    val intervals = List(new TestInterval(2, 0, 0, 0, 0, 0, 0), new TestInterval(1, 2, 3, 4, 5, 6, 7), new TestInterval(4, 0, 0, 0, 0, 0, 0))
    intervals.foreach { testInterval =>
      val interval = testInterval.interval
      assertResult(testInterval.getMonths)(MONTH.numIn(interval))
      assertResult(testInterval.getWeeks)(WEEK.numIn(interval))
      assertResult(testInterval.getDays)(DAY.numIn(interval))
      assertResult(testInterval.getHours)(HOUR.numIn(interval))
      assertResult(testInterval.getMinutes)(MINUTE.numIn(interval))
      assertResult(testInterval.getSeconds)(SECOND.numIn(interval))
    }
    assertResult(27)(MONTH.numIn(new Interval("P2y3m4d/2011-04-01")))
    assertResult(824)(DAY.numIn(new Interval("P2y3m4d/2011-04-01")))
  }

  "testTruncate" in {
    val date = new DateTime("2011-03-15T22:42:23.898")
    assertResult(new DateTime("2011-03-15T00:00:00.000"))(HOUR.truncate(new DateTime("2011-03-15T00:00:00.000")))
    assertResult(new DateTime("2011-03-01T00:00:00.000"))(MONTH.truncate(date))
    assertResult(new DateTime("2011-03-14T00:00:00.000"))(WEEK.truncate(date))
    assertResult(new DateTime("2011-03-15T00:00:00.000"))(DAY.truncate(date))
    assertResult(new DateTime("2011-03-15T22:00:00.000"))(HOUR.truncate(date))
    assertResult(new DateTime("2011-03-15T22:42:00.000"))(MINUTE.truncate(date))
    assertResult(new DateTime("2011-03-15T22:42:23.000"))(SECOND.truncate(date))
  }


  "testGetIterable" in {
    val intervals = DAY.getIterable(new DateTime("2011-01-01T00:00:00"), new DateTime("2011-01-14T00:00:00")).toList
    val expected = List(
      "2011-01-01/P1d",
      "2011-01-02/P1d",
      "2011-01-03/P1d",
      "2011-01-04/P1d",
      "2011-01-05/P1d",
      "2011-01-06/P1d",
      "2011-01-07/P1d",
      "2011-01-08/P1d",
      "2011-01-09/P1d",
      "2011-01-10/P1d",
      "2011-01-11/P1d",
      "2011-01-12/P1d",
      "2011-01-13/P1d"
    ).map(new Interval(_))
    assertResult(expected)(intervals)
  }


  "testGetReverseIterable" in {
    val intervals = DAY.getReverseIterable(new DateTime("2011-01-01T00:00:00"), new DateTime("2011-01-14T00:00:00")).toList
    val expected = List(
      "2011-01-13/P1d",
      "2011-01-12/P1d",
      "2011-01-11/P1d",
      "2011-01-10/P1d",
      "2011-01-09/P1d",
      "2011-01-08/P1d",
      "2011-01-07/P1d",
      "2011-01-06/P1d",
      "2011-01-05/P1d",
      "2011-01-04/P1d",
      "2011-01-03/P1d",
      "2011-01-02/P1d",
      "2011-01-01/P1d"
    ).map(new Interval(_))
    assertResult(expected)(intervals)
  }

  "testBucket" in {
    val dt = new DateTime("2011-02-03T04:05:06.100")
    assertResult(new Interval("2011-02-01/2011-03-01"))(MONTH.bucket(dt))
    assertResult(new Interval("2011-01-31/2011-02-07"))(WEEK.bucket(dt))
    assertResult(new Interval("2011-02-03/2011-02-04"))(DAY.bucket(dt))
    assertResult(new Interval("2011-02-03T04/2011-02-03T05"))(HOUR.bucket(dt))
    assertResult(new Interval("2011-02-03T04:05:00/2011-02-03T04:06:00"))(MINUTE.bucket(dt))
    assertResult(new Interval("2011-02-03T04:05:06/2011-02-03T04:05:07"))(SECOND.bucket(dt))
    assertResult(new Interval("2011-01-01/2011-01-02"))(DAY.bucket(new DateTime("2011-01-01")))
  }

  "testWiden" in {
    assertResult(new Interval("0/0T01"))(HOUR.widen(new Interval("0/0")))
    assertResult(new Interval("T03/T04"))(HOUR.widen(new Interval("T03:00/T03:00")))
    assertResult(new Interval("T03/T04"))(HOUR.widen(new Interval("T03:00/T03:05")))
    assertResult(new Interval("T03/T04"))(HOUR.widen(new Interval("T03:05/T04:00")))
    assertResult(new Interval("T03/T04"))(HOUR.widen(new Interval("T03:00/T04:00")))
    assertResult(new Interval("T03/T04"))(HOUR.widen(new Interval("T03:00/T03:59:59.999")))
    assertResult(new Interval("T03/T05"))(HOUR.widen(new Interval("T03:00/T04:00:00.001")))
    assertResult(new Interval("T03/T06"))(HOUR.widen(new Interval("T03:05/T05:30")))
    assertResult(new Interval("T03/T04"))(HOUR.widen(new Interval("T03:05/T03:05")))
  }

  private case class TestInterval(years: Int, months: Int, days: Int, hours: Int, minutes: Int, seconds: Int, millis: Int) {
    private val start = new DateTime(2001, 1, 1, 0, 0, 0, 0)
    private val end = start.plusYears(years).plusMonths(months).plusDays(days).plusHours(hours).plusMinutes(minutes).plusSeconds(seconds).plusMillis(millis)
    val interval = new Interval(start, end)
    def getYears = Years.yearsIn(interval).getYears
    def getMonths = Months.monthsIn(interval).getMonths
    def getWeeks = Weeks.weeksIn(interval).getWeeks
    def getDays = Days.daysIn(interval).getDays
    def getHours = Hours.hoursIn(interval).getHours
    def getMinutes = Minutes.minutesIn(interval).getMinutes
    def getSeconds = Seconds.secondsIn(interval).getSeconds
  }

}
