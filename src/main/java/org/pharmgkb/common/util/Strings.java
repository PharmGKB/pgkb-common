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
   * Wraps {@link StringUtils#stripToEmpty(String)} to also treat {@code &nbsp;} (\u00A0) as whitespace: converted
   * to a regular space (preserving word boundaries) before leading/trailing whitespace is stripped.
   */
  public static String stripToEmpty(@Nullable String str) {
    if (str == null) {
      return "";
    }
    return StringUtils.stripToEmpty(str.replace("\u00A0", " "));
  }


  /**
   * Wraps {@link StringUtils#stripToNull(String)} to also treat {@code &nbsp;} (\u00A0) as whitespace: converted
   * to a regular space (preserving word boundaries) before leading/trailing whitespace is stripped.
   */
  public static @Nullable String stripToNull(@Nullable String str) {
    if (str == null) {
      return null;
    }
    return StringUtils.stripToNull(str.replace("\u00A0", " "));
  }
}
