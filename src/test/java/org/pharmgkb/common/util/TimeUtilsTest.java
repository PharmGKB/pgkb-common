package org.pharmgkb.common.util;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


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
  void testHumanReadableDateAcceptsSqlDateAndTime() {
    // java.sql.Date/Time override toInstant() to unconditionally throw UnsupportedOperationException -
    // humanReadableDate() must not route a java.util.Date through Date.toInstant() directly, or it inherits
    // that failure for any java.sql.Date/Time caller
    assertEquals("5/21/19", TimeUtils.humanReadableDate(new java.sql.Date(1558462184401L)));
    assertEquals("5/21/19", TimeUtils.humanReadableDate(new java.sql.Time(1558462184401L)));
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
  void testParseDateWithExplicitZone() {
    // explicitly passing the system default zone must match the no-zone overload exactly
    assertEquals(new Date(1558249200000L), TimeUtils.parseToDate("5/19/19", ZoneId.systemDefault()));
    // America/New_York is 3 hours ahead of America/Los_Angeles (both observe US DST on the same dates, so
    // the offset is constant year-round) - midnight in New York is therefore 3 hours earlier, in UTC terms,
    // than midnight in Los Angeles on the same calendar date
    assertEquals(new Date(1558238400000L), TimeUtils.parseToDate("5/19/19", ZoneId.of("America/New_York")));
  }

  @Test
  void testParseDateWithExplicitZoneCoversLongFormat() {
    // sf_shortDateParser isn't the only formatter built with its own attached .withZone(...) -
    // sf_longDateFormatter is too, and testParseDateWithExplicitZone() above only exercises the short format,
    // so cover the long format's zone handling here too
    assertEquals(new Date(1558238400000L), TimeUtils.parseToDate("May 19, 2019", ZoneId.of("America/New_York")));
  }

  @Test
  void testParseDateWithExplicitZoneRejectsNullZone() {
    // must throw NullPointerException regardless of whether "time" itself is parseable - without an
    // upfront null check, a valid date string reaches the null zone (NPE from atStartOfDay) but an invalid
    // one never gets that far, throwing DateTimeParseException instead - an inconsistent contract depending
    // on an unrelated argument
    assertThrows(NullPointerException.class, () -> TimeUtils.parseToDate("not a date", null));
  }


  @Test
  void testParseToDateAttachesEarlierFormatFailuresAsSuppressed() {
    // the short/medium format parse attempts' failures must not be silently discarded before the long
    // format's own failure escapes alone - they're attached as suppressed exceptions on the one that's
    // finally thrown, so a caller/log can see why every format failed, not just the last one
    DateTimeParseException ex = assertThrows(DateTimeParseException.class,
        () -> TimeUtils.parseToDate("not a date"));
    assertEquals(2, ex.getSuppressed().length);
  }


  @Test
  void testParseToDateRejectsThreeDigitYear() {
    // sf_shortDateFormatter's maxWidth=4 (widened from 2 to fix the format-side round-trip - see
    // testDateFlowRoundTripsDatesWellBeyondCurrentYear) also widened the PARSE side to accept a variable
    // 2-to-4-digit year, so "12/25/202" was silently accepted as year 202 AD instead of throwing - a real
    // validation-bypass regression vs main, which only ever accepted exactly 2 or exactly 4 digits. Worse,
    // "5/19/019" (year 19 AD) parsed successfully but reformatted to "5/19/19", indistinguishable from (and
    // re-parsing as) 2019 - a round-trip inconsistency, not just a rejected-input regression.
    assertThrows(DateTimeParseException.class, () -> TimeUtils.parseToDate("12/25/202"));
    assertThrows(DateTimeParseException.class, () -> TimeUtils.parseToDate("5/19/019"));
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
  void testDateFlowRoundTripsDatesWellBeforeCurrentYear() {
    // the M/d/yy short format's 2-digit year must resolve relative to "now" (matching
    // java.text.SimpleDateFormat's own long-standing 80-years-back/20-years-forward convention), not a
    // fixed 2000-2099 window - otherwise a date well in the past round-trips into the wrong century
    // (verified separately: 5/19/1985 formats to "5/19/85", which a fixed 2000-2099 window would then
    // re-parse as 2085). Uses an offset relative to LocalDate.now() (not a hardcoded year) so this stays
    // valid no matter when the suite runs.
    LocalDate pastLocalDate = LocalDate.now().minusYears(40).withDayOfMonth(1);
    Date pastDate = Date.from(pastLocalDate.atStartOfDay(ZoneId.systemDefault()).toInstant());

    String dateString = TimeUtils.humanReadableDate(pastDate);
    assertEquals(pastDate, TimeUtils.parseToDate(dateString));
  }


  @Test
  void testDateFlowRoundTripsDatesWellBeyondCurrentYear() {
    // a date more than ~20 years in the future falls outside the rolling pivot's 2-digit window (the same
    // window that makes testDateFlowRoundTripsDatesWellBeforeCurrentYear pass) - appendValueReduced's
    // maxWidth must be wider than its minWidth (4, not 2) so it falls back to a full 4-digit year for a
    // value it can't represent unambiguously in 2 digits, instead of silently wrapping to the wrong century
    // (e.g. 2050 -> "50" -> re-parsed as 1950 under the old maxWidth=2 implementation). Uses an offset
    // relative to LocalDate.now() (not a hardcoded year) so this stays valid no matter when the suite runs.
    LocalDate futureLocalDate = LocalDate.now().plusYears(30).withDayOfMonth(1);
    Date futureDate = Date.from(futureLocalDate.atStartOfDay(ZoneId.systemDefault()).toInstant());

    String dateString = TimeUtils.humanReadableDate(futureDate);
    assertEquals(futureDate, TimeUtils.parseToDate(dateString));
  }


  @Test
  void testDateFormatIsLocaleIndependent() {
    // formats are documented as fixed (e.g. "M/d/yy"), so they must not vary with the JVM's default locale
    Locale defaultLocale = Locale.getDefault();
    try {
      Locale.setDefault(Locale.GERMANY);
      assertEquals("5/21/19", TimeUtils.humanReadableDate(new Date(1558462184401L)));
      assertEquals(new Date(1558249200000L), TimeUtils.parseToDate("5/19/19"));
      assertEquals(new Date(1558249200000L), TimeUtils.parseToDate("May 19, 2019"));
    } finally {
      Locale.setDefault(defaultLocale);
    }
  }


  @Test
  void testDateTime() {

    assertEquals("5/21/19 5:53 AM PDT", TimeUtils.humanReadableDateTime(new Date(1558443184000L)));
    assertEquals("12/1/18 10:16 PM PST", TimeUtils.humanReadableDateTime(new Date(1543731364000L)));
  }


  @Test
  void testHumanReadableDateTimeAcceptsSqlDateAndTime() {
    assertEquals("5/21/19 5:53 AM PDT", TimeUtils.humanReadableDateTime(new java.sql.Date(1558443184000L)));
    assertEquals("5/21/19 5:53 AM PDT", TimeUtils.humanReadableDateTime(new java.sql.Time(1558443184000L)));
  }



  @Test
  void testDuration() {

    Duration duration = Duration.ofMillis(39);
    assertEquals("39 ms", TimeUtils.humanReadablePreciseDuration(duration));
    // humanReadableDuration's own sub-second early return (not just humanReadablePreciseDuration's) - the two
    // are documented to produce identical output below a minute, but weren't both exercised at this length
    assertEquals("39 ms", TimeUtils.humanReadableDuration(duration));

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


  /**
   * Exhaustively covers every non-empty combination of zero/nonzero days, hours, minutes and seconds for
   * {@link TimeUtils#humanReadableDuration(Duration)}, to characterize the exact "and"/comma placement produced
   * by {@code handleDuration}/{@code addCommas}.
   */
  @Test
  void testDurationAllUnitCombinations() {

    // single unit
    assertEquals("2 days", TimeUtils.humanReadableDuration(Duration.ofDays(2)));
    assertEquals("3 hours", TimeUtils.humanReadableDuration(Duration.ofHours(3)));
    assertEquals("4 minutes", TimeUtils.humanReadableDuration(Duration.ofMinutes(4)));
    assertEquals("5 seconds", TimeUtils.humanReadableDuration(Duration.ofSeconds(5)));

    // two units
    assertEquals("2 days and 3 hours",
        TimeUtils.humanReadableDuration(Duration.ofDays(2).plusHours(3)));
    assertEquals("2 days and 4 minutes",
        TimeUtils.humanReadableDuration(Duration.ofDays(2).plusMinutes(4)));
    assertEquals("2 days and 5 seconds",
        TimeUtils.humanReadableDuration(Duration.ofDays(2).plusSeconds(5)));
    assertEquals("3 hours and 4 minutes",
        TimeUtils.humanReadableDuration(Duration.ofHours(3).plusMinutes(4)));
    assertEquals("3 hours and 5 seconds",
        TimeUtils.humanReadableDuration(Duration.ofHours(3).plusSeconds(5)));
    assertEquals("4 minutes and 5 seconds",
        TimeUtils.humanReadableDuration(Duration.ofMinutes(4).plusSeconds(5)));

    // three units
    assertEquals("2 days, 3 hours and 4 minutes",
        TimeUtils.humanReadableDuration(Duration.ofDays(2).plusHours(3).plusMinutes(4)));
    assertEquals("2 days, 3 hours and 5 seconds",
        TimeUtils.humanReadableDuration(Duration.ofDays(2).plusHours(3).plusSeconds(5)));
    assertEquals("2 days, 4 minutes and 5 seconds",
        TimeUtils.humanReadableDuration(Duration.ofDays(2).plusMinutes(4).plusSeconds(5)));
    assertEquals("3 hours, 4 minutes and 5 seconds",
        TimeUtils.humanReadableDuration(Duration.ofHours(3).plusMinutes(4).plusSeconds(5)));

    // four units
    assertEquals("2 days, 3 hours, 4 minutes and 5 seconds",
        TimeUtils.humanReadableDuration(Duration.ofDays(2).plusHours(3).plusMinutes(4).plusSeconds(5)));
  }


  /**
   * Exhaustively covers every non-empty combination of zero/nonzero days, hours, minutes and seconds for
   * {@link TimeUtils#humanReadablePreciseDuration(Duration)}, mirroring
   * {@link #testDurationAllUnitCombinations()}. Since these durations have no sub-second (ms) remainder, output
   * should match {@link TimeUtils#humanReadableDuration(Duration)} exactly.
   */
  @Test
  void testPreciseDurationAllUnitCombinations() {

    // single unit
    assertEquals("2 days", TimeUtils.humanReadablePreciseDuration(Duration.ofDays(2)));
    assertEquals("3 hours", TimeUtils.humanReadablePreciseDuration(Duration.ofHours(3)));
    assertEquals("4 minutes", TimeUtils.humanReadablePreciseDuration(Duration.ofMinutes(4)));
    assertEquals("5 seconds", TimeUtils.humanReadablePreciseDuration(Duration.ofSeconds(5)));

    // two units
    assertEquals("2 days and 3 hours",
        TimeUtils.humanReadablePreciseDuration(Duration.ofDays(2).plusHours(3)));
    assertEquals("2 days and 4 minutes",
        TimeUtils.humanReadablePreciseDuration(Duration.ofDays(2).plusMinutes(4)));
    assertEquals("2 days and 5 seconds",
        TimeUtils.humanReadablePreciseDuration(Duration.ofDays(2).plusSeconds(5)));
    assertEquals("3 hours and 4 minutes",
        TimeUtils.humanReadablePreciseDuration(Duration.ofHours(3).plusMinutes(4)));
    assertEquals("3 hours and 5 seconds",
        TimeUtils.humanReadablePreciseDuration(Duration.ofHours(3).plusSeconds(5)));
    assertEquals("4 minutes and 5 seconds",
        TimeUtils.humanReadablePreciseDuration(Duration.ofMinutes(4).plusSeconds(5)));

    // three units
    assertEquals("2 days, 3 hours and 4 minutes",
        TimeUtils.humanReadablePreciseDuration(Duration.ofDays(2).plusHours(3).plusMinutes(4)));
    assertEquals("2 days, 3 hours and 5 seconds",
        TimeUtils.humanReadablePreciseDuration(Duration.ofDays(2).plusHours(3).plusSeconds(5)));
    assertEquals("2 days, 4 minutes and 5 seconds",
        TimeUtils.humanReadablePreciseDuration(Duration.ofDays(2).plusMinutes(4).plusSeconds(5)));
    assertEquals("3 hours, 4 minutes and 5 seconds",
        TimeUtils.humanReadablePreciseDuration(Duration.ofHours(3).plusMinutes(4).plusSeconds(5)));

    // four units
    assertEquals("2 days, 3 hours, 4 minutes and 5 seconds",
        TimeUtils.humanReadablePreciseDuration(Duration.ofDays(2).plusHours(3).plusMinutes(4).plusSeconds(5)));
  }


  @Test
  void testDurationRejectsNegative() {
    // negative durations aren't a meaningful "elapsed time" and would otherwise produce a nonsensical
    // string, e.g. Duration.ofSeconds(-90) would render as "-90000 ms" instead of a proper breakdown
    assertThrows(IllegalArgumentException.class,
        () -> TimeUtils.humanReadableDuration(Duration.ofSeconds(-90)));
    assertThrows(IllegalArgumentException.class,
        () -> TimeUtils.humanReadablePreciseDuration(Duration.ofSeconds(-90)));
  }
}
