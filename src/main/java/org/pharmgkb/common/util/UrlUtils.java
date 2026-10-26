package org.pharmgkb.common.util;

import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.google.common.base.Splitter;
import com.google.common.collect.Sets;
import com.google.common.net.InetAddresses;


/**
 * Utility methods for working with URLs.
 *
 * @author Mark Woon
 */
public class UrlUtils {
  private static final Set<String> sf_webSchemes = Sets.newHashSet("http", "https");
  private static final Set<String> sf_urlSchemes = Sets.newHashSet("http", "https", "ftp");
  // InetAddress.getByName() strips ANY number of leading zeros from an octet, not just up to 3 digits
  // total (e.g. "00192" is parsed the same as "192") - the digit-count cap here (15) is just a sanity bound
  // to keep Long.parseLong() below safe, not a real limit on how many leading zeros are tolerated
  private static final Pattern sf_ipv4DottedQuadPattern =
      Pattern.compile("^(\\d{1,15})\\.(\\d{1,15})\\.(\\d{1,15})\\.(\\d{1,15})$");



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
    return isValid(urlString, true, false, false);
  }


  /**
   * Checks if {@code urlString} is a syntactically valid {@code http}, {@code https} or {@code ftp} URL.
   * <ul>
   *   <li>Does NOT allow localhost.</li>
   *   <li>Does NOT allow private IPs.</li>
   * </ul>
   */
  public static boolean isValid(String urlString) {
    return isValid(urlString, false, false, false);
  }


  /**
   * Checks if {@code urlString} is a valid {@code http}, {@code https} or {@code ftp} URL.
   * <p>
   * Private-IP detection only examines the literal host in the URL: hostnames aren't resolved, so a hostname that
   * resolves to a private or loopback address isn't caught.
   * <p>
   * Literal IPv4 (dotted decimal; leading zeros are read as decimal, as {@link InetAddress#getByName} does) and
   * IPv6 hosts are recognized, including IPv4-mapped IPv6 ({@code ::ffff:127.0.0.1}). Not caught: the
   * single-integer IPv4 form (e.g. {@code http://2130706433/}) and deprecated IPv4-compatible IPv6
   * ({@code http://[::127.0.0.1]/}).
   *
   * @param noFtp true if ftp protocol is not allowed
   * @param allowLocalhost true if localhost should be allowed
   * @param allowPrivateIp true if private IP addresses should be allowed
   */
  public static boolean isValid(String urlString, boolean noFtp, boolean allowLocalhost,
      boolean allowPrivateIp) {

    try {
      // java.net.URL(String) is deprecated in favor of URI(String).toURL(), which is stricter: strip() and
      // encoding a literal space as %20 keep the two leniencies callers relied on (surrounding whitespace, an
      // unencoded space in the path/query). strip() isn't an exact match for URL's own whitespace rule, but
      // only differs for control characters and exotic Unicode spaces
      URI uri = new URI(urlString.strip().replace(" ", "%20"));
      URL url = uri.toURL();
      Set<String> schemes = noFtp ? sf_webSchemes : sf_urlSchemes;
      if (!schemes.contains(url.getProtocol())) {
        return false;
      }
      if (uri.getHost() == null) {
        return false;
      }
      // use raw userinfo: RFC 3986 requires a ':' inside the user or password to be percent-encoded, so only
      // a raw ':' separates them
      if (uri.getRawUserInfo() != null && Splitter.on(":").splitToList(uri.getRawUserInfo()).size() > 2) {
        return false;
      }

      if (!allowLocalhost) {
        String host = uri.getHost();
        // the DNS "fully-qualified" trailing-dot form (RFC 1035) names the exact same host (and this
        // machine's resolver still sends it to loopback), so strip it before matching - otherwise appending
        // a single "." (e.g. "foo.localhost.") bypasses every check below
        if (host.endsWith(".")) {
          host = host.substring(0, host.length() - 1);
        }
        // the "localhost." PREFIX form (e.g. "localhost.evil.com") isn't itself reserved by RFC 6761, but is
        // blocked defensively anyway. The "*.localhost" SUFFIX form (e.g. "foo.localhost") IS what RFC 6761
        // 6.3 actually reserves as resolving to loopback - regionMatches() with a negative toffset (a host
        // shorter than ".localhost") safely returns false rather than throwing, so no length guard is needed.
        // "ip6-localhost"/"ip6-loopback" are a separate loopback alias: every Debian-family default /etc/hosts
        // maps both to "::1". Other local hostname aliases aren't caught, since hostnames aren't resolved
        if (host.equalsIgnoreCase("localhost") || host.equalsIgnoreCase("ip6-localhost") ||
            host.equalsIgnoreCase("ip6-loopback") || host.regionMatches(true, 0, "localhost.", 0, 10) ||
            host.regionMatches(true, host.length() - 10, ".localhost", 0, 10)) {
          return false;
        }
      }
      if (!allowPrivateIp) {
        String host = uri.getHost();
        // Guava's InetAddresses intentionally rejects a zero-padded octet (e.g. "192.168.001.001") to avoid
        // the classic decimal-vs-octal ambiguity, so isUriInetAddress() below would otherwise return false
        // and skip private-IP detection entirely for such a host - but InetAddress.getByName() (what actually
        // resolves the host when the URL is used) has no such scruple and parses each octet as plain decimal
        // regardless of padding. Normalize the padding away first so a zero-padded private/loopback
        // address can't bypass this check just by being spelled differently than the real resolver expects.
        Matcher m = sf_ipv4DottedQuadPattern.matcher(host);
        if (m.matches()) {
          StringBuilder normalized = new StringBuilder();
          boolean allOctetsValid = true;
          for (int i = 1; i <= 4; i++) {
            // Long (not int) so a 15-digit octet can't overflow into a false negative here - it will
            // always legitimately fail the > 255 check below instead
            long octet = Long.parseLong(m.group(i));
            if (octet > 255) {
              allOctetsValid = false;
              break;
            }
            if (i > 1) {
              normalized.append('.');
            }
            normalized.append(octet);
          }
          if (allOctetsValid) {
            host = normalized.toString();
          }
        }
        if (InetAddresses.isUriInetAddress(host)) {
          InetAddress addr = InetAddresses.forUriString(host);
          if (addr.isLoopbackAddress() || addr.isLinkLocalAddress() || addr.isSiteLocalAddress() ||
              addr.isAnyLocalAddress() || isUniqueLocalAddress(addr)) {
            return false;
          }
        }
      }

      return true;

    } catch (Exception e) {
      return false;
    }
  }


  /**
   * Checks if {@code addr} is an IPv6 unique local address (fc00::/7), which
   * {@link InetAddress#isSiteLocalAddress()} does not recognize (it only covers the deprecated fec0::/10 range).
   */
  private static boolean isUniqueLocalAddress(InetAddress addr) {
    byte[] bytes = addr.getAddress();
    return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
  }
}
