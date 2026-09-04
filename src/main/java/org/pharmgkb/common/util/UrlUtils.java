package org.pharmgkb.common.util;

import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
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
  /** Maximum amount to wait while trying to connect in ms. */
  private static final int sf_timeout = 1000;
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
   * <p>
   * Private-IP detection (when {@code allowPrivateIp} is {@code false}) only examines the literal host in the URL -
   * it does NOT resolve hostnames, so a hostname that resolves to a private/loopback address (including via DNS
   * rebinding) is not caught. This is still true when {@code verify} is {@code true}: {@link #isReachable(URL)}
   * will still connect to whatever address the hostname resolves to, with no re-check against private ranges.
   * The same applies to HTTP redirects for an {@code http}/{@code https} URL: {@link #isReachable(URL)} never
   * disables {@link java.net.HttpURLConnection}'s default redirect-following, so a URL that itself passes this
   * validation can still redirect to an arbitrary private/loopback target, and the reachability result reflects
   * that FINAL hop, not the originally-validated URL - this matches existing behavior, not re-checked either way.
   * <p>
   * For literal IP hosts, only standard IPv4 dotted-decimal (each octet's leading zeros are normalized away
   * first, matching {@link InetAddress#getByName}'s own decimal-only interpretation of them - NOT treated as
   * octal, unlike some other platforms' resolvers) and IPv6 literal forms are recognized. This does NOT catch
   * the single-decimal-integer legacy encoding of an IPv4 address that some platforms will still resolve
   * (e.g. {@code http://2130706433/}, an alternate representation of {@code 127.0.0.1}) - verified the JDK's
   * own resolver does not recognize a hex-per-octet form (e.g. {@code http://0x7f.0.0.1/}) at all, so that
   * particular legacy form isn't a real gap. It also does NOT catch the deprecated IPv4-compatible IPv6 form
   * (e.g. {@code http://[::127.0.0.1]/}) - unlike the IPv4-mapped form ({@code ::ffff:127.0.0.1}), which IS
   * caught.
   *
   * @param noFtp true if ftp protocol is not allowed
   * @param allowLocalhost true if localhost should be allowed
   * @param allowPrivateIp true if private IP addresses should be allowed
   * @param verify true to check if URL actually points to something (see caveats for {@link #isReachable(URL)})
   */
  public static boolean isValid(String urlString, boolean noFtp, boolean allowLocalhost,
      boolean allowPrivateIp, boolean verify) {

    try {
      // java.net.URL(String) is deprecated since JDK 20 in favor of URI(String).toURL() - RFC 3986-compliant
      // URI parsing is intentionally stricter than URL's own historically loose parsing, which is exactly
      // why the JDK deprecated it, so this migration explicitly restores (rather than silently drops) the
      // two specific leniencies this method's own callers were actually relying on: strip() approximates
      // URL's discarding of leading/trailing whitespace (String.strip()'s Character.isWhitespace()-based rule
      // and URL's own "<= ' '" tokenizer rule are NOT identical - e.g. strip() also discards U+2003 EM SPACE,
      // which URL's own tokenizer does not, and URL's tokenizer also discards ASCII control characters like
      // NUL, which strip() does not - round 31 finding 4 corrected this comment's earlier, inaccurate "not
      // itself a behavior change" claim; the divergence is real but limited to exotic control/Unicode-space
      // codepoints no real caller has been found to rely on either way), and replacing a literal space with
      // "%20" replicates URL's tolerance of an unencoded space in the path/query (round 30 finding 6) - a
      // %20 substitution is always a safe, meaning-preserving encoding for a literal space in any URI
      // component. Scheme case-insensitivity ("HTTP://...") doesn't need special handling here:
      // URL.getProtocol() (via uri.toURL() below) already lowercases it the same way constructing a URL
      // directly always has
      URI uri = new URI(urlString.strip().replace(" ", "%20"));
      URL url = uri.toURL();
      Set<String> schemes = noFtp ? sf_webSchemes : sf_urlSchemes;
      if (!schemes.contains(url.getProtocol())) {
        return false;
      }
      if (uri.getHost() == null) {
        return false;
      }
      // use decoded userinfo so a percent-encoded colon (e.g. "a%3Ab%3Ac") can't bypass this check
      if (uri.getUserInfo() != null && Splitter.on(":").splitToList(uri.getUserInfo()).size() > 2) {
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
        // "ip6-localhost"/"ip6-loopback" (round 25) are a second, separate loopback alias, not a form of
        // "localhost" at all - every Debian/Ubuntu/WSL machine's default /etc/hosts maps both to "::1"
        // (`::1 ip6-localhost ip6-loopback`), machine-independent across the whole Debian family, unlike an
        // arbitrary local hostname alias (e.g. this machine's own name resolving via /etc/hosts, which the
        // existing "not resolving hostnames" limitation - known non-issue #1 - already covers)
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
        // resolves the host, e.g. in isReachable() below) has no such scruple and parses each octet as plain
        // decimal regardless of padding. Normalize the padding away first so a zero-padded private/loopback
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

      if (verify) {
        return isReachable(url);
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


  /**
   * For HTTP URLs, this will perform a HEAD request and checks for a non-error response.
   * For FTP URLs, this will only verify that we can connect to the server, not whether the resource is available.
   * <p>
   * Only {@code http}/{@code https}/{@code ftp} URLs are supported (matching {@link #isValid}) - a URL with no
   * network endpoint at all (e.g. {@code file:}, {@code jar:}, {@code mailto:}) always returns {@code false}:
   * the non-HTTP branch's {@link Socket} probe below needs a host/port to connect to, which
   * {@link URL#getHost()}/{@link URL#getDefaultPort()} don't provide for those schemes. This is a real
   * behavior difference from this method's earlier {@code URLConnection}-based implementation, which happened
   * to return {@code true} for an existing local {@code file:} URL - not fixed, since it's outside this
   * method's own documented contract and there's no real caller of {@code isReachable} today.
   */
  public static boolean isReachable(URL url) {
    try {
      if (sf_webSchemes.contains(url.getProtocol())) {
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        try {
          connection.setConnectTimeout(sf_timeout);
          connection.setReadTimeout(sf_timeout);
          connection.setRequestMethod("HEAD");
          // use no encoding to avoid gzip, HEAD has no body anyway (see https://issuetracker.google.com/issues/36939140)
          connection.setRequestProperty("Accept-Encoding", "");
          int responseCode = connection.getResponseCode();
          return (200 <= responseCode && responseCode <= 399);
        } finally {
          // disconnect() actually closes the socket rather than returning it to the keep-alive pool (contrary
          // to what the name suggests), so this costs a fresh connection on the next request to this host -
          // but it's needed to drain/discard any unread error-response body so the connection doesn't get
          // reused in a bad state. Best-effort only: an exception here (checked or, for a misbehaving
          // HttpURLConnection, unchecked) must not flip an already-confirmed-reachable server to unreachable -
          // matching the guarantee the non-HTTP branch's own cleanup below already has
          try {
            connection.disconnect();
          } catch (Exception ignored) {
            // best-effort - see above
          }
        }

      } else {
        // this only checks to see if we can connect to the server, per this method's own documented
        // contract for the non-HTTP (FTP) case - not whether the resource is actually available, or even a
        // file. Previously done via URLConnection.connect() + getInputStream().close() - but URLConnection
        // has no close()/disconnect() method at all, so a FAILED probe (connect()/getInputStream() itself
        // throwing, before the close() a few lines later ever ran) leaked the underlying socket, reclaimed
        // only by GC (round 25). A plain Socket is itself closeable and matches this method's own
        // "connectivity only" contract more directly than URLConnection.connect() did anyway - for FTP,
        // that actually opened a full control-connection session (login, etc.), heavier than a bare
        // connectivity check
        int port = url.getPort() != -1 ? url.getPort() : url.getDefaultPort();
        Socket socket = new Socket();
        try {
          socket.connect(new InetSocketAddress(url.getHost(), port), sf_timeout);
          return true;
        } finally {
          // best-effort cleanup - a failure here must not flip an already-confirmed-reachable server to
          // unreachable, matching the HTTP branch's own close()-failure guarantee above
          try {
            socket.close();
          } catch (Exception ignored) {
            // best-effort - see above
          }
        }
      }

    } catch (Exception ex) {
      return false;
    }
  }
}
