package org.pharmgkb.common.util;

import java.net.URI;
import java.net.URL;
import java.util.Set;
import com.google.common.collect.Sets;


/**
 * Utility methods for working with URLs.
 *
 * @author Mark Woon
 */
public class UrlUtils {
  private static final Set<String> sf_webSchemes = Sets.newHashSet("http", "https");



  /**
   * Private constructor to prevent instantiation of utility class.
   */
  private UrlUtils() {
  }


  /**
   * Checks if {@code urlString} is a valid {@code http} or {@code https} URL.
   */
  public static boolean isValidWebUrl(String urlString) {

    try {
      URI uri = new URL(urlString).toURI();
      if (!sf_webSchemes.contains(uri.getScheme())) {
        return false;
      }
      if (uri.getHost() == null) {
        return false;
      }
      return true;

    } catch (Exception e) {
      return false;
    }
  }
}
