package org.pharmgkb.common.util;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.time.temporal.TemporalAccessor;
import java.util.Date;
import java.util.concurrent.TimeUnit;


/**
 * Utility methods for working with time.
 *
 * @author Mark Woon
 */
public class TimeUtils {
  private static final DateTimeFormatter sf_mediumDateFormatter = DateTimeFormatter.ofPattern("M/d/yyyy");
  private static final DateTimeFormatter sf_shortDateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
      .withZone(ZoneId.systemDefault());
  private static final DateTimeFormatter sf_longDateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
      .withZone(ZoneId.systemDefault());

  private static final DateTimeFormatter sf_simpleDateTimeFormatter = DateTimeFormatter.ofPattern("M/d/yy h:mm a z")
      .withZone(ZoneId.systemDefault());


  /**
   * Formats {@code date} as "M/d/yy".
   */
  public static String humanReadableDate(Date date) {
    return humanReadableDate(date.toInstant());
  }

  /**
   * Formats {@code date} as "M/d/yy'.
   */
  public static String humanReadableDate(TemporalAccessor time) {
    return sf_shortDateFormatter.format(time);
  }


  /**
   * Parses "M/d/yy" or "MMMM d, yyyy" formatted strings into a {@link Date}.
   */
  public static Date parseToDate(String time) throws DateTimeParseException {
    TemporalAccessor temporalAccessor;
    try {
      temporalAccessor = sf_shortDateFormatter.parse(time);
    } catch (DateTimeParseException ex) {
      try {
        temporalAccessor = sf_mediumDateFormatter.parse(time);
      } catch (DateTimeParseException ex2) {
        temporalAccessor = sf_longDateFormatter.parse(time);
      }
    }
    LocalDate ld = LocalDate.from(temporalAccessor);
    return Date.from(ld.atStartOfDay(ZoneId.systemDefault()).toInstant());
  }


  /**
   * Formats {@code date} as "M/d/yy HH:mm z".
   */
  public static String humanReadableDateTime(Date date) {
    return humanReadableDateTime(date.toInstant());
  }

  /**
   * Formats {@code date} as "M/d/yy HH:mm z".
   */
  public static String humanReadableDateTime(TemporalAccessor time) {
    return sf_simpleDateTimeFormatter.format(time);
  }



  /**
   * Formats {@code duration} as "w days, x hours, y minutes and z seconds".
   */
  public static String humanReadableDuration(Duration duration) {

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
      prependDuration(stringBuilder, secs, "second", "seconds");
    }
    handleDuration(stringBuilder, days, hours, mins, hasAnd);
    return stringBuilder.toString();
  }

  private static void handleDuration(StringBuilder stringBuilder, long days, long hours, long mins, boolean hasAnd) {
    if (mins > 0) {
      hasAnd = addCommas(stringBuilder, false);
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

  public static String humanReadablePreciseDuration(Duration duration) {

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
