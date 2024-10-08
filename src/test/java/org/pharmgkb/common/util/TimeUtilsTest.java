package org.pharmgkb.common.util;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.Date;
import java.util.TimeZone;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * This is a JUnit test for {@link TimeUtils}.
 *
 * @author Mark Woon
 */
class TimeUtilsTest {

  @BeforeAll
  static void beforeClass() {
    // make sure the timezone is consistent in the test
    TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
  }


  @Test
  void testDate() {

    assertEquals("5/21/19", TimeUtils.humanReadableDate(new Date(1558462184401L)));
    assertEquals("5/1/19", TimeUtils.humanReadableDate(new Date(1556733184000L)));
  }


  @Test
  void testDateInstant() {

    assertEquals("5/19/19", TimeUtils.humanReadableDate(LocalDateTime.of(2019, Month.MAY, 19, 1, 10)));
    assertEquals("5/2/19", TimeUtils.humanReadableDate(LocalDateTime.of(2019, Month.MAY, 2, 1, 10)));
  }


  @Test
  void testParseDate() {
    assertEquals(new Date(1558249200000L), TimeUtils.parseToDate("5/19/19"));
    assertEquals(new Date(1558249200000L), TimeUtils.parseToDate("05/19/19"));
    assertEquals(new Date(1558249200000L), TimeUtils.parseToDate("5/19/2019"));
    assertEquals(new Date(1558249200000L), TimeUtils.parseToDate("May 19, 2019"));
  }


  @Test
  void testDateFlow() {

    Date date = new Date(1558249200000L);
    String dateString = TimeUtils.humanReadableDate(date);
    assertEquals(date, TimeUtils.parseToDate(dateString));

    dateString = "05/01/19";
    date = TimeUtils.parseToDate(dateString);
    assertEquals("5/1/19", TimeUtils.humanReadableDate(date));
  }


  @Test
  void testDateTime() {

    assertEquals("5/21/19 5:53 AM PDT", TimeUtils.humanReadableDateTime(new Date(1558443184000L)));
    assertEquals("12/1/18 10:16 PM PST", TimeUtils.humanReadableDateTime(new Date(1543731364000L)));
  }



  @Test
  void testDuration() {

    Duration duration = Duration.ofMillis(39);
    assertEquals("39 ms", TimeUtils.humanReadablePreciseDuration(duration));

    duration = duration.plusSeconds(1);
    assertEquals("1 second", TimeUtils.humanReadableDuration(duration));

    duration = Duration.ofSeconds(39);
    assertEquals("39 seconds", TimeUtils.humanReadableDuration(duration));

    duration = duration.plusMinutes(1);
    assertEquals("1 minute and 39 seconds", TimeUtils.humanReadableDuration(duration));

    duration = duration.plusMinutes(3);
    assertEquals("4 minutes and 39 seconds", TimeUtils.humanReadableDuration(duration));

    duration = duration.plusHours(1);
    assertEquals("1 hour, 4 minutes and 39 seconds", TimeUtils.humanReadableDuration(duration));

    duration = duration.plusHours(1);
    assertEquals("2 hours, 4 minutes and 39 seconds", TimeUtils.humanReadableDuration(duration));

    duration = duration.plusDays(1);
    assertEquals("1 day, 2 hours, 4 minutes and 39 seconds", TimeUtils.humanReadableDuration(duration));

    duration = duration.plusDays(4);
    assertEquals("5 days, 2 hours, 4 minutes and 39 seconds", TimeUtils.humanReadableDuration(duration));

    duration = Duration.ofDays(5);
    assertEquals("5 days", TimeUtils.humanReadableDuration(duration));

    duration = duration.plusSeconds(16);
    assertEquals("5 days and 16 seconds", TimeUtils.humanReadableDuration(duration));

    duration = duration.plusMinutes(1);
    assertEquals("5 days, 1 minute and 16 seconds", TimeUtils.humanReadableDuration(duration));
  }


  @Test
  void testPreciseDuration() {

    Duration duration = Duration.ofMillis(39);
    assertEquals("39 ms", TimeUtils.humanReadablePreciseDuration(duration));

    duration = duration.plusSeconds(1);
    assertEquals("1 second and 39 ms", TimeUtils.humanReadablePreciseDuration(duration));

    duration = duration.plusSeconds(38);
    assertEquals("39 seconds and 39 ms", TimeUtils.humanReadablePreciseDuration(duration));

    duration = duration.plusMinutes(1);
    assertEquals("1 minute and 39 seconds", TimeUtils.humanReadablePreciseDuration(duration));

    duration = duration.plusMinutes(3);
    assertEquals("4 minutes and 39 seconds", TimeUtils.humanReadablePreciseDuration(duration));

    duration = duration.plusHours(1);
    assertEquals("1 hour, 4 minutes and 39 seconds", TimeUtils.humanReadablePreciseDuration(duration));

    duration = duration.plusHours(1);
    assertEquals("2 hours, 4 minutes and 39 seconds", TimeUtils.humanReadablePreciseDuration(duration));

    duration = duration.plusDays(1);
    assertEquals("1 day, 2 hours, 4 minutes and 39 seconds", TimeUtils.humanReadablePreciseDuration(duration));

    duration = duration.plusDays(4);
    assertEquals("5 days, 2 hours, 4 minutes and 39 seconds", TimeUtils.humanReadablePreciseDuration(duration));

    duration = Duration.ofDays(5);
    assertEquals("5 days", TimeUtils.humanReadablePreciseDuration(duration));

    duration = duration.plusSeconds(16);
    assertEquals("5 days and 16 seconds", TimeUtils.humanReadablePreciseDuration(duration));

    duration = duration.plusMinutes(1);
    assertEquals("5 days, 1 minute and 16 seconds", TimeUtils.humanReadablePreciseDuration(duration));
  }
}
