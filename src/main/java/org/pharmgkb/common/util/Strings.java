package org.pharmgkb.common.util;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;


/**
 * Utility functions for {@link String}s.
 *
 * @author Mark Woon
 */
public class Strings {

  /**
   * Private constructor to prevent instantiation of utility class.
   */
  private Strings() {
  }


  /**
   * Wraps {@link StringUtils#stripToEmpty(String)} to also strip out {@code &nbsp;} (\u00A0).
   */
  public static String stripToEmpty(@Nullable String str) {
    if (str == null) {
      return "";
    }
    return StringUtils.stripToEmpty(str.replaceAll("\u00A0", ""));
  }


  /**
   * Wraps {@link StringUtils#stripToNull(String)} to also strip out {@code &nbsp;} (\u00A0).
   */
  public static @Nullable String stripToNull(@Nullable String str) {
    if (str == null) {
      return null;
    }
    return StringUtils.stripToNull(str.replaceAll("\u00A0", ""));
  }
}
