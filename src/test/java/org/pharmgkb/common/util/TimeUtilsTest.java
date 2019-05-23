package org.pharmgkb.common.util;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.Date;
import java.util.TimeZone;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;


/**
 * This is a JUnit test for {@link TimeUtils}.
 *
 * @author Mark Woon
 */
public class TimeUtilsTest {

  @BeforeClass
  public static void beforeClass() {
    // make sure test timezone is consistent
    TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
  }


  @Test
  public void testDate() {

    assertEquals("5/21/19", TimeUtils.humanReadableDate(new Date(1558462184401L)));
    assertEquals("5/1/19", TimeUtils.humanReadableDate(new Date(1556733184000L)));
  }


  @Test
  public void testDateInstant() {

    assertEquals("5/19/19", TimeUtils.humanReadableDate(LocalDateTime.of(2019, Month.MAY, 19, 1, 10)));
    assertEquals("5/2/19", TimeUtils.humanReadableDate(LocalDateTime.of(2019, Month.MAY, 2, 1, 10)));
  }


  @Test
  public void testParseDate() {
    assertEquals(new Date(1558249200000L), TimeUtils.parseToDate("5/19/19"));
    assertEquals(new Date(1558249200000L), TimeUtils.parseToDate("May 19, 2019"));
  }


  @Test
  public void testDateFlow() {

    Date date = new Date(1558249200000L);
    String dateString = TimeUtils.humanReadableDate(date);
    assertEquals(date, TimeUtils.parseToDate(dateString));

    dateString = "05/01/19";
    date = TimeUtils.parseToDate(dateString);
    assertEquals("5/1/19", TimeUtils.humanReadableDate(date));
  }


  @Test
  public void testDateTime() {

    assertEquals("5/21/19 5:53 AM PDT", TimeUtils.humanReadableDateTime(new Date(1558443184000L)));
    assertEquals("12/1/18 10:16 PM PST", TimeUtils.humanReadableDateTime(new Date(1543731364000L)));
  }



  @Test
  public void testDuration() {

    Duration duration = Duration.ofSeconds(1);
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
}
