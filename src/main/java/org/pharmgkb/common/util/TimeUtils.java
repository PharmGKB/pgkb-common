package org.pharmgkb.common.util;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import com.google.common.base.Preconditions;


/**
 * Utility methods for working with time.
 *
 * @author Mark Woon
 */
public class TimeUtils {
  // formats are pinned to Locale.US so behavior is consistent regardless of the JVM's default locale
  private static final DateTimeFormatter sf_mediumDateFormatter = DateTimeFormatter.ofPattern("M/d/yyyy", Locale.US);
  // a 2-digit year means the 100-year window from 80 years ago to 20 years ahead (as in SimpleDateFormat),
  // not ofPattern("M/d/yy")'s fixed 2000-2099
  private static final DateTimeFormatter sf_shortDateFormatter = new DateTimeFormatterBuilder()
      .appendPattern("M/d/")
      // maxWidth 4: a year outside the window is formatted in full rather than wrapping to the wrong century
      .appendValueReduced(ChronoField.YEAR, 2, 4, LocalDate.now().minusYears(80))
      .toFormatter(Locale.US)
      .withZone(ZoneId.systemDefault());
  // parsing uses exactly 2 digits - sf_shortDateFormatter's maxWidth of 4 would accept a 3-digit year (e.g.
  // "12/25/202"). Its base date must match sf_shortDateFormatter's
  private static final DateTimeFormatter sf_shortDateParser = new DateTimeFormatterBuilder()
      .appendPattern("M/d/")
      .appendValueReduced(ChronoField.YEAR, 2, 2, LocalDate.now().minusYears(80))
      .toFormatter(Locale.US)
      .withZone(ZoneId.systemDefault());
  private static final DateTimeFormatter sf_longDateFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US)
      .withZone(ZoneId.systemDefault());

  private static final DateTimeFormatter sf_simpleDateTimeFormatter =
      DateTimeFormatter.ofPattern("M/d/yy h:mm a z", Locale.US).withZone(ZoneId.systemDefault());


  /**
   * Formats {@code date} as "M/d/yy" for a year within the 100-year window from 80 years ago to 20 years
   * ahead, or "M/d/yyyy" outside it. A year before 1000 doesn't round-trip; see {@link #parseToDate(String)}.
   */
  public static String humanReadableDate(Date date) {
    // not Date.toInstant(), which java.sql.Date/Time override to throw
    return humanReadableDate(Instant.ofEpochMilli(date.getTime()));
  }

  /**
   * Formats {@code date} as "M/d/yy" for a year within the 100-year window from 80 years ago to 20 years
   * ahead, or "M/d/yyyy" outside it. A year before 1000 doesn't round-trip; see {@link #parseToDate(String)}.
   */
  public static String humanReadableDate(TemporalAccessor time) {
    return sf_shortDateFormatter.format(time);
  }


  /**
   * Parses "M/d/yy", "M/d/yyyy" or "MMMM d, yyyy" formatted strings into a {@link Date}.
   * <p>
   * A year before 1000 doesn't round-trip through {@link #humanReadableDate}: it is formatted with 1-3 digits,
   * which fails to parse, or (e.g. year 1 as "01") parses into the wrong century.
   */
  public static Date parseToDate(String time) throws DateTimeParseException {
    return parseToDate(time, ZoneId.systemDefault());
  }

  /**
   * Parses "M/d/yy", "M/d/yyyy" or "MMMM d, yyyy" formatted strings into a {@link Date}, treating the parsed
   * date as midnight in {@code zone} (rather than the JVM's default zone) when converting it to an instant.
   * <p>
   * A year before 1000 doesn't round-trip; see {@link #parseToDate(String)}.
   */
  public static Date parseToDate(String time, ZoneId zone) throws DateTimeParseException {
    // validate up front so this always throws NullPointerException for a null zone, regardless of whether
    // "time" itself happens to be parseable - otherwise an invalid "time" would throw DateTimeParseException
    // instead, before ever reaching the zone
    Preconditions.checkNotNull(zone, "zone cannot be null");
    TemporalAccessor temporalAccessor;
    try {
      temporalAccessor = sf_shortDateParser.parse(time);
    } catch (DateTimeParseException ex) {
      try {
        temporalAccessor = sf_mediumDateFormatter.parse(time);
      } catch (DateTimeParseException ex2) {
        try {
          temporalAccessor = sf_longDateFormatter.parse(time);
        } catch (DateTimeParseException ex3) {
          // don't silently discard why the earlier formats failed too
          ex3.addSuppressed(ex);
          ex3.addSuppressed(ex2);
          throw ex3;
        }
      }
    }
    LocalDate ld = LocalDate.from(temporalAccessor);
    return Date.from(ld.atStartOfDay(zone).toInstant());
  }


  /**
   * Formats {@code date} as "M/d/yy h:mm a z".
   */
  public static String humanReadableDateTime(Date date) {
    // see humanReadableDate(Date) above for why this can't just call date.toInstant()
    return humanReadableDateTime(Instant.ofEpochMilli(date.getTime()));
  }

  /**
   * Formats {@code date} as "M/d/yy h:mm a z".
   */
  public static String humanReadableDateTime(TemporalAccessor time) {
    return sf_simpleDateTimeFormatter.format(time);
  }



  /**
   * Formats {@code duration} as "w days, x hours, y minutes and z seconds".
   * <p>
   * See {@link #humanReadablePreciseDuration(Duration)} for a variant that also shows milliseconds for
   * sub-minute durations.
   *
   * @throws IllegalArgumentException if {@code duration} is negative
   * @throws ArithmeticException if {@code duration}'s length in milliseconds overflows a {@code long}
   * (i.e. is longer than roughly 292 million years)
   */
  public static String humanReadableDuration(Duration duration) {

    Preconditions.checkArgument(!duration.isNegative(), "Duration cannot be negative: %s", duration);
    if (duration.toMillis() < 1000) {
      return duration.toMillis() + " ms";
    }
    long days = duration.toDays();
    long hours = duration.toHours() - TimeUnit.DAYS.toHours(duration.toDays());
    long mins = duration.toMinutes() - TimeUnit.HOURS.toMinutes(duration.toHours());
    long secs = duration.getSeconds() - TimeUnit.MINUTES.toSeconds(duration.toMinutes());

    StringBuilder stringBuilder = new StringBuilder();
    boolean hasAnd = false;
    if (secs > 0) {
      // stringBuilder is always empty here, so addCommas() is a no-op - hasAnd stays false
      prependDuration(stringBuilder, secs, "second", "seconds");
    }
    handleDuration(stringBuilder, days, hours, mins, hasAnd);
    return stringBuilder.toString();
  }

  private static void handleDuration(StringBuilder stringBuilder, long days, long hours, long mins, boolean hasAnd) {
    if (mins > 0) {
      hasAnd = addCommas(stringBuilder, hasAnd);
      prependDuration(stringBuilder, mins, "minute", "minutes");
    }
    if (hours > 0) {
      hasAnd = addCommas(stringBuilder, hasAnd);
      prependDuration(stringBuilder, hours, "hour", "hours");
    }
    if (days > 0) {
      addCommas(stringBuilder, hasAnd);
      prependDuration(stringBuilder, days, "day", "days");
    }
  }

  /**
   * Like {@link #humanReadableDuration(Duration)}, but also shows a milliseconds component - e.g.
   * "1 second and 39 ms" - for durations under a minute. Once the duration is a minute or longer, the millisecond
   * remainder is truncated just as it is in {@link #humanReadableDuration(Duration)}, and the two methods produce
   * identical output.
   *
   * @throws IllegalArgumentException if {@code duration} is negative
   * @throws ArithmeticException if {@code duration}'s length in milliseconds overflows a {@code long}
   * (i.e. is longer than roughly 292 million years)
   */
  public static String humanReadablePreciseDuration(Duration duration) {

    Preconditions.checkArgument(!duration.isNegative(), "Duration cannot be negative: %s", duration);
    if (duration.toMillis() < 1000) {
      return duration.toMillis() + " ms";
    }

    long days = duration.toDays();
    long hours = duration.toHours() - TimeUnit.DAYS.toHours(duration.toDays());
    long mins = duration.toMinutes() - TimeUnit.HOURS.toMinutes(duration.toHours());
    long secs = duration.getSeconds() - TimeUnit.MINUTES.toSeconds(duration.toMinutes());
    long ms = TimeUnit.NANOSECONDS.toMillis(duration.getNano());

    StringBuilder stringBuilder = new StringBuilder();
    boolean hasAnd = false;

    if (ms > 0 && mins == 0 && hours == 0 && days == 0) {
      prependDuration(stringBuilder, ms, "ms", "ms");
    }
    if (secs > 0) {
      hasAnd = addCommas(stringBuilder, false);
      prependDuration(stringBuilder, secs, "second", "seconds");
    }
    handleDuration(stringBuilder, days, hours, mins, hasAnd);
    return stringBuilder.toString();
  }

  private static boolean addCommas(StringBuilder stringBuilder, boolean hasAnd) {
    if (stringBuilder.length() > 0) {
      if (hasAnd) {
        stringBuilder.insert(0, ", ");
      } else {
        stringBuilder.insert(0, " and ");
        hasAnd = true;
      }
    }
    return hasAnd;
  }


  private static void prependDuration(StringBuilder stringBuilder, long duration, String singular, String plural) {
    String period = duration > 1 ? plural : singular;
    stringBuilder
        .insert(0, period)
        .insert(0, " ")
        .insert(0, duration);
  }
}
