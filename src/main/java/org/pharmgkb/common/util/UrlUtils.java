package org.pharmgkb.common.util;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.google.common.base.Splitter;
import com.google.common.collect.Sets;


/**
 * Utility methods for working with URLs.
 *
 * @author Mark Woon
 */
public class UrlUtils {
  private static final Set<String> sf_webSchemes = Sets.newHashSet("http", "https");
  private static final Set<String> sf_urlSchemes = Sets.newHashSet("http", "https", "ftp");
  private static final Pattern sf_172Pattern = Pattern.compile("^172\\.(\\d+)\\.\\d+\\.\\d+$");
  /** Maximum amount to wait while trying to connect in ms. */
  private static final int sf_timeout = 1000;



  /**
   * Private constructor to prevent instantiation of utility class.
   */
  private UrlUtils() {
  }


  /**
   * Checks if {@code urlString} is a syntactically valid {@code http} or {@code https} URL.
   * <ul>
   *   <li>Does NOT allow localhost.</li>
   *   <li>Does NOT allow private IPs.</li>
   * </ul>
   */
  public static boolean isValidWebUrl(String urlString) {
    return isValid(urlString, true, false, false, false);
  }


  /**
   * Checks if {@code urlString} is a syntactically valid {@code http}, {@code https} or {@code ftp} URL.
   * <ul>
   *   <li>Does NOT allow localhost.</li>
   *   <li>Does NOT allow private IPs.</li>
   * </ul>
   */
  public static boolean isValid(String urlString) {
    return isValid(urlString, false, false, false, false);
  }


  /**
   * Checks if {@code urlString} is a valid {@code http}, {@code https} or {@code ftp} URL.
   *
   * @param noFtp true if ftp protocol is not allowed
   * @param allowLocalhost true if localhost should be allowed
   * @param allowPrivateIp true if private IP addresses should be allowed
   * @param verify true to check if URL actually points to something (see caveats for {@link #isReachable(URL)})
   */
  public static boolean isValid(String urlString, boolean noFtp, boolean allowLocalhost,
      boolean allowPrivateIp, boolean verify) {

    try {
      URL url = new URL(urlString);
      URI uri = url.toURI();
      Set<String> schemes = noFtp ? sf_webSchemes : sf_urlSchemes;
      if (!schemes.contains(uri.getScheme())) {
        return false;
      }
      if (uri.getHost() == null) {
        return false;
      }
      if (uri.getRawUserInfo() != null && Splitter.on(":").splitToList(uri.getRawUserInfo()).size() > 2) {
        return false;
      }

      if (!allowLocalhost) {
        if (uri.getHost().equals("localhost") || uri.getHost().startsWith("localhost.")) {
          return false;
        }
      }
      if (!allowPrivateIp) {
        // ipv6
        if (uri.getHost().startsWith("[fd") ||
            // ipv4
            uri.getHost().equals("127.0.0.1") ||
            uri.getHost().startsWith("10.") ||
            uri.getHost().startsWith("192.168.")) {
          return false;
        }
        Matcher m = sf_172Pattern.matcher(uri.getHost());
        if (m.matches() && Integer.parseInt(m.group(1)) >= 16 && Integer.parseInt(m.group(1)) <= 32) {
          return false;
        }
      }

      if (verify) {
        return isReachable(url);
      }
      return true;

    } catch (Exception e) {
      return false;
    }
  }


  /**
   * For HTTP URLs, this will perform a HEAD request and checks for a non-error response.
   * For FTP URLs, this will only verify that we can connect to the server, not whether the resource is actually
   * available.
   */
  public static boolean isReachable(URL url) {
    try {
      if (sf_webSchemes.contains(url.getProtocol())) {
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        connection.setConnectTimeout(sf_timeout);
        connection.setReadTimeout(sf_timeout);
        connection.setRequestMethod("HEAD");
        // use no encoding to avoid gzip, HEAD has no body anyway (see https://issuetracker.google.com/issues/36939140)
        connection.setRequestProperty("Accept-Encoding", "");
        int responseCode = connection.getResponseCode();
        return (200 <= responseCode && responseCode <= 399);

      } else {
        URLConnection connection = url.openConnection();
        connection.setConnectTimeout(sf_timeout);
        connection.setReadTimeout(sf_timeout);
        // TODO(markwoon): this only checks to see if we can connect to the server
        connection.connect();
        // TODO(markwoon): we're not actually verifying if the file is available, or if it is even a file
        /* unreliable, fails for ftp://anonymous:foo%40bar.com@ftp.swfwmd.state.fl.us/pub/README.txt
        try (InputStream in = connection.getInputStream()) {
        }
        */
        return true;
      }

    } catch (Exception ex) {
      return false;
    }
  }
}
