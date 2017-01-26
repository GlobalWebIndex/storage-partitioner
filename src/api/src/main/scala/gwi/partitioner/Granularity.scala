package gwi.partitioner

import org.joda.time.chrono.ISOChronology
import org.joda.time.{Days, _}

/**
  * @note this code is based on Druid's https://github.com/metamx/java-util/blob/master/src/main/java/com/metamx/common/Granularity.java
  *       I wanted to use it directly but I had to :
  *        - make several modifications because this library is not about Druid, but it is more general
  *        - transitive dependencies on classpath would make troubles to users
  */
trait Granularity {

  override def toString: String = getClass.getSimpleName.stripSuffix("$")
  def value: String
  def getUnits(n: Int): ReadablePeriod
  def truncate(time: DateTime): DateTime
  def numIn(interval: ReadableInterval): Int
  def arity: Int

  def increment(time: DateTime): DateTime = time.plus(getUnits(1))
  def increment(time: DateTime, count: Int): DateTime = time.plus(getUnits(count))
  def decrement(time: DateTime): DateTime = time.minus(getUnits(1))
  def decrement(time: DateTime, count: Int): DateTime = time.minus(getUnits(count))
  def getIterable(start: DateTime, end: DateTime): Iterable[Interval] = getIterable(new Interval(start, end))
  def getIterable(input: Interval): Iterable[Interval] = new IntervalIterable(input)
  def getReverseIterable(start: DateTime, end: DateTime): Iterable[Interval] = getReverseIterable(new Interval(start, end))
  def getReverseIterable(input: Interval): Iterable[Interval] = new ReverseIntervalIterable(input)

  def bucket(t: DateTime): Interval = {
    val start = truncate(t)
    new Interval(start, increment(start))
  }

  def widen(interval: Interval): Interval = {
    val intervalStart = truncate(interval.getStart)
    val intervalEnd =
      interval.getEnd match {
        case end if end == intervalStart => increment(intervalStart)
        case end if truncate(end) == end => end
        case end => increment(truncate(end))
      }
    new Interval(intervalStart, intervalEnd)
  }

  class IntervalIterable(inputInterval: Interval) extends Iterable[Interval] {
    def iterator = new Iterator[Interval] {
      private var currStart = truncate(inputInterval.getStart)
      private var currEnd = increment(currStart)

      def hasNext = currStart.isBefore(inputInterval.getEnd)

      def next: Interval = {
        if (!hasNext) {
          throw new NoSuchElementException("There are no more intervals")
        }
        val retVal = new Interval(currStart, currEnd)
        currStart = currEnd
        currEnd = increment(currStart)
        retVal
      }
    }
  }

  class ReverseIntervalIterable(inputInterval: Interval) extends Iterable[Interval] {
    def iterator = new Iterator[Interval] {
      private var currEnd = inputInterval.getEnd
      private var currStart = decrement(currEnd)

      def hasNext: Boolean = currEnd.isAfter(inputInterval.getStart)

      def next = {
        if (!hasNext) {
          throw new NoSuchElementException("There are no more intervals")
        }
        val retVal = new Interval(currStart, currEnd)
        currEnd = currStart
        currStart = decrement(currEnd)
        retVal
      }
    }
  }

}

object Granularity {

  val MONTH   = Month("MONTH")
  val WEEK    = Week("WEEK")
  val DAY     = Day("DAY")
  val HOUR    = Hour("HOUR")
  val MINUTE  = Minute("MINUTE")
  val SECOND  = Second("SECOND")

  def apply(value: String): Granularity = value match {
    case g if g == MONTH.value  => MONTH
    case g if g == WEEK.value   => WEEK
    case g if g == DAY.value    => DAY
    case g if g == HOUR.value   => HOUR
    case g if g == MINUTE.value => MINUTE
    case g if g == SECOND.value => SECOND
  }

  case class Month(value: String) extends Granularity {
    def arity = 2
    def getUnits(n: Int) = Months.months(n)
    def numIn(interval: ReadableInterval) = Months.monthsIn(interval).getMonths
    def truncate(time: DateTime) = {
      val mutableDateTime = time.toMutableDateTime(ISOChronology.getInstanceUTC)
      mutableDateTime.setMillisOfDay(0)
      mutableDateTime.setDayOfMonth(1)
      mutableDateTime.toDateTime(ISOChronology.getInstanceUTC)
    }
  }

  case class Week(value: String) extends Granularity {
    def arity = DAY.arity
    def getUnits(n: Int) = Weeks.weeks(n)
    def numIn(interval: ReadableInterval) = Weeks.weeksIn(interval).getWeeks
    def truncate(time: DateTime) = {
      val mutableDateTime = time.toMutableDateTime(ISOChronology.getInstanceUTC)
      mutableDateTime.setMillisOfDay(0)
      mutableDateTime.setDayOfWeek(1)
      mutableDateTime.toDateTime(ISOChronology.getInstanceUTC)
    }
  }

  case class Day(value: String) extends Granularity {
    def arity = 3
    def getUnits(n: Int) = Days.days(n)
    def numIn(interval: ReadableInterval): Int = Days.daysIn(interval).getDays
    def truncate(time: DateTime): DateTime = {
      val mutableDateTime = time.toMutableDateTime(ISOChronology.getInstanceUTC)
      mutableDateTime.setMillisOfDay(0)
      mutableDateTime.toDateTime(ISOChronology.getInstanceUTC)
    }
  }

  case class Hour(value: String) extends Granularity {
    def arity = 4
    def getUnits(n: Int) = Hours.hours(n)
    def numIn(interval: ReadableInterval) = Hours.hoursIn(interval).getHours
    def truncate(time: DateTime): DateTime = {
      val mutableDateTime = time.toMutableDateTime(ISOChronology.getInstanceUTC)
      mutableDateTime.setMillisOfSecond(0)
      mutableDateTime.setSecondOfMinute(0)
      mutableDateTime.setMinuteOfHour(0)
      mutableDateTime.toDateTime(ISOChronology.getInstanceUTC)
    }
  }

  case class Minute(value: String) extends Granularity {
    def arity = 5
    def getUnits(count: Int) = Minutes.minutes(count)
    def numIn(interval: ReadableInterval) = Minutes.minutesIn(interval).getMinutes
    def truncate(time: DateTime) = {
      val mutableDateTime = time.toMutableDateTime(ISOChronology.getInstanceUTC)
      mutableDateTime.setMillisOfSecond(0)
      mutableDateTime.setSecondOfMinute(0)
      mutableDateTime.toDateTime(ISOChronology.getInstanceUTC)
    }
  }

  case class Second(value: String) extends Granularity {
    def arity = 6
    def getUnits(count: Int) = Seconds.seconds(count)
    def numIn(interval: ReadableInterval) = Seconds.secondsIn(interval).getSeconds
    def truncate(time: DateTime) = {
      val mutableDateTime = time.toMutableDateTime(ISOChronology.getInstanceUTC)
      mutableDateTime.setMillisOfSecond(0)
      mutableDateTime.toDateTime(ISOChronology.getInstanceUTC)
    }
  }

}
