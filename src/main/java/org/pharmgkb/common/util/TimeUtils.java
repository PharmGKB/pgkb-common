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
  // a 2-digit year is inherently ambiguous as to century - appendValueReduced()'s baseDate anchors the
  // 100-year window to "now" (80 years back, 20 years forward), matching java.text.SimpleDateFormat's own
  // long-standing convention, instead of DateTimeFormatter.ofPattern("M/d/yy")'s fixed 2000-2099 window
  // (which would silently re-parse a formatted pre-2000 date, e.g. "5/19/85" for 1985, as 2085)
  private static final DateTimeFormatter sf_shortDateFormatter = new DateTimeFormatterBuilder()
      .appendPattern("M/d/")
      // maxWidth is 4, not 2: with maxWidth==minWidth, a value outside [base, base+100) has no way to
      // round-trip and silently wraps to the wrong century (e.g. a date 30 years from now formats to a
      // 2-digit year that re-parses 100 years too early) - a wider maxWidth makes appendValueReduced fall
      // back to the full 4-digit year for exactly those out-of-window values instead, while still using the
      // compact 2-digit form for the common case of a date within the rolling window
      .appendValueReduced(ChronoField.YEAR, 2, 4, LocalDate.now().minusYears(80))
      .toFormatter(Locale.US)
      .withZone(ZoneId.systemDefault());
  // parseToDate() below parses through THIS formatter (minWidth == maxWidth == 2), not sf_shortDateFormatter
  // above, for the short-date slot: reusing sf_shortDateFormatter's wider maxWidth=4 for parsing as well as
  // formatting would accept a 3-digit year (e.g. "12/25/202") literally instead of throwing, since
  // appendValueReduced's max/minWidth window applies to BOTH directions - main only ever accepted exactly 2
  // or exactly 4 digits, never 3. This formatter's own base date must stay identical to
  // sf_shortDateFormatter's, or the two would disagree on which century a given 2-digit year resolves to.
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
   * Formats {@code date} as "M/d/yy" for a year within the rolling 100-year window anchored to "now" (80
   * years back, 20 years forward), or "M/d/yyyy" for a year outside it - except a year well before 1000 AD,
   * which can still format to a bare 2-digit year that doesn't correspond to that window; see
   * {@link #parseToDate(String)} for the resulting round-trip caveat.
   */
  public static String humanReadableDate(Date date) {
    // Date.toInstant() is unconditionally overridden to throw UnsupportedOperationException by
    // java.sql.Date/Time - going via the epoch millis instead works for every Date subtype
    return humanReadableDate(Instant.ofEpochMilli(date.getTime()));
  }

  /**
   * Formats {@code date} as "M/d/yy" for a year within the rolling 100-year window anchored to "now" (80
   * years back, 20 years forward), or "M/d/yyyy" for a year outside it - except a year well before 1000 AD,
   * which can still format to a bare 2-digit year that doesn't correspond to that window; see
   * {@link #parseToDate(String)} for the resulting round-trip caveat.
   */
  public static String humanReadableDate(TemporalAccessor time) {
    return sf_shortDateFormatter.format(time);
  }


  /**
   * Parses "M/d/yy", "M/d/yyyy" or "MMMM d, yyyy" formatted strings into a {@link Date}.
   * <p>
   * A year before 1000 doesn't round-trip through {@link #humanReadableDate}: that method's natural digit
   * count for such a year (1-3 digits) is neither the exactly-2 this method's short-date parser requires nor
   * the exactly-4 the medium-date parser requires, so it fails to parse (or, for a year that happens to
   * format to exactly 2 digits, like year 1 as "01", silently reparses under the wrong century). Not fixed -
   * no real caller of this class works with a date before 1000 AD.
   */
  public static Date parseToDate(String time) throws DateTimeParseException {
    return parseToDate(time, ZoneId.systemDefault());
  }

  /**
   * Parses "M/d/yy", "M/d/yyyy" or "MMMM d, yyyy" formatted strings into a {@link Date}, treating the parsed
   * date as midnight in {@code zone} (rather than the JVM's default zone) when converting it to an instant.
   * <p>
   * See {@link #parseToDate(String)} for the pre-1000-AD round-trip caveat, which applies here too.
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
      // use the incoming hasAnd (whether a "seconds" component was already prepended by the caller), not a
      // hardcoded false - discarding it here happened to produce the same result for every currently
      // possible caller (secs is always the only thing that could precede mins), but only by coincidence
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
