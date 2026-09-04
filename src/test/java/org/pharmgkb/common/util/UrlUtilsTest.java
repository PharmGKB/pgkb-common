package org.pharmgkb.common.util;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * JUnit test for {@link UrlUtils}.
 *
 * @author Mark Woon
 */
class UrlUtilsTest {


  @Test
  void testWebUrls() {
    assertTrue(UrlUtils.isValidWebUrl("http://www.clinpgx.org"));
    assertTrue(UrlUtils.isValidWebUrl("http://www.clinpgx.org/bar.txt"));
    assertTrue(UrlUtils.isValidWebUrl("http://www.clinpgx.org:8080"));
    assertTrue(UrlUtils.isValidWebUrl("http://www.clinpgx.org:8080/bar.txt"));
    assertTrue(UrlUtils.isValidWebUrl("http://hi:there@www.clinpgx.org/"));

    assertTrue(UrlUtils.isValidWebUrl("https://www.clinpgx.org"));
    assertTrue(UrlUtils.isValidWebUrl("https://www.clinpgx.org/bar.txt"));
    assertTrue(UrlUtils.isValidWebUrl("https://www.clinpgx.org:8080"));
    assertTrue(UrlUtils.isValidWebUrl("https://www.clinpgx.org:8080/bar.txt"));
    assertTrue(UrlUtils.isValidWebUrl("https://hi:there@www.clinpgx.org/"));

    assertFalse(UrlUtils.isValidWebUrl("ftp://www.clinpgx.org"));
    assertFalse(UrlUtils.isValidWebUrl("ftp://www.clinpgx.org/bar.txt"));
    assertFalse(UrlUtils.isValidWebUrl("ftp://hi:there@www.clinpgx.org:8080"));
    assertFalse(UrlUtils.isValidWebUrl("ftp://hi:there@www.clinpgx.org:8080/bar.txt"));

    assertTrue(UrlUtils.isValidWebUrl("http://171.67.192.16"));
    assertTrue(UrlUtils.isValidWebUrl("http://171.67.192.16:8080"));
    assertTrue(UrlUtils.isValidWebUrl("http://172.168.15.1"));
    assertTrue(UrlUtils.isValidWebUrl("http://172.168.33.1"));
    // ipv6
    assertTrue(UrlUtils.isValidWebUrl("http://[2001:db8:a0b:12f0::1]/index.html"));
    assertTrue(UrlUtils.isValidWebUrl("http://[2001:db8:a0b:12f0::1]:80/index.html"));

    // localhost
    assertFalse(UrlUtils.isValidWebUrl("http://localhost"));
    // localhost check must be case-insensitive
    assertFalse(UrlUtils.isValidWebUrl("http://LOCALHOST"));
    assertFalse(UrlUtils.isValidWebUrl("http://LocalHost"));
    assertFalse(UrlUtils.isValidWebUrl("http://LOCALHOST.evil.com"));
    // RFC 6761 6.3 reserves the *.localhost SUFFIX form (e.g. "foo.localhost") as resolving to loopback -
    // this machine's own resolver confirms it (InetAddress.getByName("foo.localhost").isLoopbackAddress()
    // is true) - so this must be rejected the same as bare "localhost", not just the "localhost." PREFIX
    // form (which isn't actually reserved by the RFC, but was already blocked defensively above)
    assertFalse(UrlUtils.isValidWebUrl("http://foo.localhost"));
    assertFalse(UrlUtils.isValidWebUrl("http://FOO.LOCALHOST"));
    assertFalse(UrlUtils.isValidWebUrl("http://a.b.localhost"));
    // the DNS "fully-qualified" trailing-dot form (RFC 1035) names the exact same host, and this
    // machine's resolver still sends it to loopback - it must not bypass the checks above
    assertFalse(UrlUtils.isValidWebUrl("http://localhost."));
    assertFalse(UrlUtils.isValidWebUrl("http://foo.localhost."));
    assertFalse(UrlUtils.isValidWebUrl("http://FOO.LOCALHOST."));
    assertFalse(UrlUtils.isValidWebUrl("http://a.b.localhost."));
    // "ip6-localhost"/"ip6-loopback" are a second, separate loopback alias (not a form of "localhost") that
    // every Debian/Ubuntu/WSL machine's default /etc/hosts maps to "::1" - a pure string check, not a DNS
    // resolution, so this doesn't depend on this test machine's own /etc/hosts contents
    assertFalse(UrlUtils.isValidWebUrl("http://ip6-localhost"));
    assertFalse(UrlUtils.isValidWebUrl("http://IP6-LOCALHOST"));
    assertFalse(UrlUtils.isValidWebUrl("http://ip6-loopback"));
    assertFalse(UrlUtils.isValidWebUrl("http://IP6-LOOPBACK"));
    // private ip
    assertFalse(UrlUtils.isValidWebUrl("http://127.0.0.1"));
    assertFalse(UrlUtils.isValidWebUrl("http://10.0.0.1:8080"));
    assertFalse(UrlUtils.isValidWebUrl("http://172.16.1.1"));
    assertFalse(UrlUtils.isValidWebUrl("http://172.22.1.1"));
    assertFalse(UrlUtils.isValidWebUrl("http://172.31.1.1"));
    // 172.32.0.0/16 is public, not part of the 172.16.0.0/12 private range
    assertTrue(UrlUtils.isValidWebUrl("http://172.32.1.1"));
    // link-local (169.254.0.0/16) - was otherwise unpinned by any assertion despite being the single most-
    // targeted SSRF destination (169.254.169.254 is the AWS/GCP/Azure cloud metadata endpoint)
    assertFalse(UrlUtils.isValidWebUrl("http://169.254.169.254/latest/meta-data/"));
    assertFalse(UrlUtils.isValidWebUrl("http://[fe80::1]/"));
    assertFalse(UrlUtils.isValidWebUrl("http://[fd01:db8:a0b:12f0::1]:80/index.html"));
    // ipv6 unique local range is fc00::/7, not just the "fd" half
    assertFalse(UrlUtils.isValidWebUrl("http://[fc00::1]/"));
    // ipv6 loopback
    assertFalse(UrlUtils.isValidWebUrl("http://[::1]/"));
    // ipv4-mapped ipv6 loopback
    assertFalse(UrlUtils.isValidWebUrl("http://[::ffff:127.0.0.1]/"));
    // wildcard/unspecified address - many network stacks route this to localhost
    assertFalse(UrlUtils.isValidWebUrl("http://0.0.0.0/"));
    assertFalse(UrlUtils.isValidWebUrl("http://[::]/"));
    // zero-padded octets - Guava's IP parser rejects these outright (avoiding octal ambiguity), which
    // previously caused isValid() to skip private-IP detection entirely and treat the host as valid; but
    // InetAddress.getByName() (what actually resolves the host later) parses each octet as plain decimal
    // regardless of padding, so these must still be caught as private/loopback
    assertFalse(UrlUtils.isValidWebUrl("http://192.168.001.001/"));
    assertFalse(UrlUtils.isValidWebUrl("http://010.000.000.001/"));
    assertFalse(UrlUtils.isValidWebUrl("http://172.016.0.1/"));
    assertFalse(UrlUtils.isValidWebUrl("http://172.16.0.01/"));
    // the real resolver strips ANY number of leading zeros, not just up to 3 digits total - a 4+ digit
    // octet like "0192" or "00192" must be caught too, not just the common 3-digit-with-one-leading-zero case
    assertFalse(UrlUtils.isValidWebUrl("http://0192.168.1.1/"));
    assertFalse(UrlUtils.isValidWebUrl("http://00192.168.1.1/"));
    assertFalse(UrlUtils.isValidWebUrl("http://192.0168.1.1/"));
    assertFalse(UrlUtils.isValidWebUrl("http://192.168.0001.1/"));
    // a genuinely public IP with a zero-padded octet must still be allowed through
    assertTrue(UrlUtils.isValidWebUrl("http://008.008.008.008/"));

    assertFalse(UrlUtils.isValidWebUrl("http://"));
    assertFalse(UrlUtils.isValidWebUrl("http://."));
    assertFalse(UrlUtils.isValidWebUrl("http://.com"));
    assertFalse(UrlUtils.isValidWebUrl("https:// "));
    assertFalse(UrlUtils.isValidWebUrl("ftp://"));
    assertFalse(UrlUtils.isValidWebUrl("ftp://::::@example.com"));
    assertFalse(UrlUtils.isValidWebUrl("mailto:foo@bar.com"));
  }

  @Test
  void testIsValidTreatsUnescapedSpaceInPathAsValid() {
    // round 30 finding 6: migrating off the deprecated java.net.URL(String) constructor (in favor of
    // URI(String).toURL()) percent-encodes a literal space to "%20" before parsing, rather than letting
    // RFC-3986-compliant URI reject it outright the way URL's own historically loose parsing never did -
    // deliberately preserving (not silently dropping) this leniency, since it's a safe, meaning-preserving
    // substitution for a literal space in any URI component
    assertTrue(UrlUtils.isValidWebUrl("http://www.clinpgx.org/a b"));
    // leading/trailing whitespace around the WHOLE url is unrelated (already covered by
    // StreamUtilsTest.testCopyUrlToFileTakesHttpBranchWithLeadingWhitespace) - stripped away entirely, not
    // encoded as content
    assertTrue(UrlUtils.isValidWebUrl(" http://www.clinpgx.org/a b "));
  }


  @Test
  void testUrls() {
    assertTrue(UrlUtils.isValid("http://www.clinpgx.org"));
    assertTrue(UrlUtils.isValid("http://www.clinpgx.org/bar.txt"));
    assertTrue(UrlUtils.isValid("http://www.clinpgx.org:8080"));
    assertTrue(UrlUtils.isValid("http://www.clinpgx.org:8080/bar.txt"));
    assertTrue(UrlUtils.isValid("http://hi:there@www.clinpgx.org/"));

    assertTrue(UrlUtils.isValid("https://www.clinpgx.org"));
    assertTrue(UrlUtils.isValid("https://www.clinpgx.org/bar.txt"));
    assertTrue(UrlUtils.isValid("https://www.clinpgx.org:8080"));
    assertTrue(UrlUtils.isValid("https://www.clinpgx.org:8080/bar.txt"));
    assertTrue(UrlUtils.isValid("https://hi:there@www.clinpgx.org/"));

    assertTrue(UrlUtils.isValid("ftp://www.clinpgx.org"));
    assertTrue(UrlUtils.isValid("ftp://www.clinpgx.org/bar.txt"));
    assertTrue(UrlUtils.isValid("ftp://hi:there@www.clinpgx.org:8080"));
    assertTrue(UrlUtils.isValid("ftp://hi:there@www.clinpgx.org:8080/bar.txt"));

    assertTrue(UrlUtils.isValid("http://171.67.192.16"));
    assertTrue(UrlUtils.isValid("http://171.67.192.16:8080"));
    assertTrue(UrlUtils.isValid("http://172.168.15.1"));
    assertTrue(UrlUtils.isValid("http://172.168.33.1"));
    // ipv6
    assertTrue(UrlUtils.isValid("http://[2001:db8:a0b:12f0::1]/index.html"));
    assertTrue(UrlUtils.isValid("http://[2001:db8:a0b:12f0::1]:80/index.html"));

    // localhost
    assertFalse(UrlUtils.isValid("http://localhost"));
    // private ip
    assertFalse(UrlUtils.isValid("http://127.0.0.1"));
    assertFalse(UrlUtils.isValid("http://10.0.0.1:8080"));
    assertFalse(UrlUtils.isValid("http://172.16.1.1"));
    assertFalse(UrlUtils.isValid("http://172.22.1.1"));
    assertFalse(UrlUtils.isValid("http://172.31.1.1"));
    // 172.32.0.0/16 is public, not part of the 172.16.0.0/12 private range
    assertTrue(UrlUtils.isValid("http://172.32.1.1"));
    assertFalse(UrlUtils.isValid("http://[fd01:db8:a0b:12f0::1]:80/index.html"));

    assertFalse(UrlUtils.isValid("http://"));
    assertFalse(UrlUtils.isValid("http://."));
    assertFalse(UrlUtils.isValid("http://.com"));
    assertFalse(UrlUtils.isValid("https:// "));
    assertFalse(UrlUtils.isValid("ftp://"));
    assertFalse(UrlUtils.isValid("ftp://::::@example.com"));
    // percent-encoded colons must not bypass the multi-colon userinfo check ("a:b:c" decoded)
    assertFalse(UrlUtils.isValid("http://a%3Ab%3Ac@example.com/"));
    assertFalse(UrlUtils.isValid("mailto:foo@bar.com"));
  }


  @Test
  void testLocalAndPrivateUrls() {

    // localhost
    assertTrue(UrlUtils.isValid("http://localhost", true, true, true, false));
    // private ip
    assertTrue(UrlUtils.isValid("http://127.0.0.1", true, true, true, false));
    assertTrue(UrlUtils.isValid("http://10.0.0.1:8080", true, true, true, false));
    assertTrue(UrlUtils.isValid("http://172.16.1.1", true, true, true, false));
    assertTrue(UrlUtils.isValid("http://172.22.1.1", true, true, true, false));
    assertTrue(UrlUtils.isValid("http://172.32.1.1", true, true, true, false));
    assertTrue(UrlUtils.isValid("http://[fd01:db8:a0b:12f0::1]:80/index.html", true, true, true, false));

    // isValid()'s "uri.getHost() == null" early return is only independently observable (rather than
    // incidentally caught by the outer try/catch via an NPE further down) when allowLocalhost AND
    // allowPrivateIp are both true, as here - a hostless URL must still be rejected
    assertFalse(UrlUtils.isValid("http:///foo", true, true, true, false));
  }


  @Test
  void testIsReachableUrl() throws Exception {
    // uses a local server, not a real external host, so this doesn't depend on outbound network access
    // (previously hit real external hosts directly - fails offline/in a sandboxed CI, and can flake on the
    // external server's own downtime, unrelated to this method's correctness). HTTP and HTTPS share
    // identical logic in isReachable() (both go through the same sf_webSchemes branch via HttpURLConnection),
    // so a single local plain-HTTP check exercises that branch fully - a dedicated HTTPS check would only
    // exercise the JDK's own TLS handshake machinery, not this method. The FTP branch's "connects to a real
    // server" coverage already lives in testIsReachableFtpBranchConnectsToRealServer() below, via the same
    // local-server pattern - not duplicated here.
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/", exchange -> {
      exchange.sendResponseHeaders(200, -1);
      exchange.close();
    });
    server.start();
    try {
      assertTrue(UrlUtils.isReachable(new URL("http://localhost:" + server.getAddress().getPort() + "/")));
    } finally {
      server.stop(0);
    }
  }


  @Test
  void testVerify() throws Exception {
    // see testIsReachableUrl() above for why a local server, not a real external host, is used for the
    // reachable case
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/", exchange -> {
      exchange.sendResponseHeaders(200, -1);
      exchange.close();
    });
    server.start();
    try {
      String url = "http://localhost:" + server.getAddress().getPort() + "/";
      assertTrue(UrlUtils.isValid(url, true, true, false, true));
    } finally {
      server.stop(0);
    }
    // a nonexistent domain fails DNS resolution regardless of network connectivity, so this doesn't need a
    // real network dependency either way
    assertFalse(UrlUtils.isValid("http://www.clinpgxoops.org", true, false, false, true));
  }


  @Test
  void testIsReachableFtpBranchConnectsToRealServer() throws Exception {
    // round 25: the non-HTTP(S) branch was rewritten from URLConnection.connect() (which has no close()/
    // disconnect() at all, leaking the underlying socket on every FAILED probe - see the fd-leak test below)
    // to a plain Socket connect - verify it still does the one thing this method's own contract promises for
    // this branch: confirm we can connect to the server, nothing more
    try (ServerSocket server = new ServerSocket(0)) {
      Thread serverThread = new Thread(() -> {
        try (Socket accepted = server.accept()) {
          // accept and immediately let the try-with-resources close it - the client side only cares that
          // the connection was accepted at all
        } catch (IOException ignored) {
          // test failure surfaces via the client-side assertion below, not here
        }
      });
      serverThread.start();
      URL url = new URL("ftp://localhost:" + server.getLocalPort() + "/");
      assertTrue(UrlUtils.isReachable(url));
      serverThread.join();
    }
  }

  @Test
  void testIsReachableFtpBranchReturnsFalseWhenNothingListening() throws Exception {
    // a closed/never-bound port must fail the connectivity check, not be silently treated as reachable
    int port;
    try (ServerSocket probe = new ServerSocket(0)) {
      port = probe.getLocalPort();
    }
    URL url = new URL("ftp://localhost:" + port + "/");
    assertFalse(UrlUtils.isReachable(url));
  }

  @Test
  void testIsReachableFtpBranchDoesNotLeakFileDescriptorsOnFailure() throws Exception {
    // the previous URLConnection-based implementation had no close()/disconnect() method at all - for FTP,
    // connect() eagerly performs the full login handshake, so a slow/hanging server left an already-opened
    // control socket open on failure, reclaimed only by GC (round 25). The new Socket-based implementation
    // always closes in a finally block regardless of success or failure, so there's no equivalent failure
    // shape left to reproduce against THIS implementation specifically (a plain connection refusal never
    // opened a socket to leak in the first place, under either implementation) - reproduced instead as a
    // direct structural check: repeated failed connectivity probes must not accumulate open file
    // descriptors, counted via /proc/self/fd (Linux-only)
    Path proc = Path.of("/proc/self/fd");
    Assumptions.assumeTrue(Files.isDirectory(proc), "requires /proc/self/fd (Linux)");

    int port;
    try (ServerSocket probe = new ServerSocket(0)) {
      port = probe.getLocalPort();
    }
    URL url = new URL("ftp://localhost:" + port + "/");

    int attempts = 50;
    long before = countOpenFds(proc);
    for (int i = 0; i < attempts; i++) {
      assertFalse(UrlUtils.isReachable(url));
    }
    long after = countOpenFds(proc);

    assertTrue(after - before < attempts,
        "expected no per-attempt fd growth after " + attempts + " failed attempts, but open fd count went " +
            "from " + before + " to " + after);
  }

  @Test
  void testIsReachableFtpBranchDoesNotLeakFileDescriptorsOnSuccess() throws Exception {
    // the success path opens a real socket too - must be closed just as reliably as the failure path above
    Path proc = Path.of("/proc/self/fd");
    Assumptions.assumeTrue(Files.isDirectory(proc), "requires /proc/self/fd (Linux)");

    try (ServerSocket server = new ServerSocket(0)) {
      Thread serverThread = new Thread(() -> {
        while (!Thread.currentThread().isInterrupted()) {
          try (Socket accepted = server.accept()) {
            // accept and immediately close - the client side only cares that the connection was accepted
          } catch (IOException ignored) {
            break;
          }
        }
      });
      serverThread.setDaemon(true);
      serverThread.start();

      URL url = new URL("ftp://localhost:" + server.getLocalPort() + "/");
      int attempts = 50;
      long before = countOpenFds(proc);
      for (int i = 0; i < attempts; i++) {
        assertTrue(UrlUtils.isReachable(url));
      }
      long after = countOpenFds(proc);

      assertTrue(after - before < attempts,
          "expected no per-attempt fd growth after " + attempts + " successful attempts, but open fd count " +
              "went from " + before + " to " + after);
    }
  }

  private static long countOpenFds(Path proc) throws IOException {
    try (var stream = Files.list(proc)) {
      return stream.count();
    }
  }

  @Test
  void testIsReachableDisconnectsHttpConnection() throws Exception {
    // the HTTP(S) branch of isReachable() only reads the response code (never the body), so without an
    // explicit disconnect() the underlying socket can't be returned to the keep-alive pool
    AtomicBoolean disconnected = new AtomicBoolean(false);
    URL url = new URL(null, "http://host/path", new URLStreamHandler() {
      @Override
      protected URLConnection openConnection(URL u) {
        return new HttpURLConnection(u) {
          @Override
          public void disconnect() {
            disconnected.set(true);
          }

          @Override
          public boolean usingProxy() {
            return false;
          }

          @Override
          public void connect() {
            // no-op: simulates a successful connect()
          }

          @Override
          public int getResponseCode() {
            return 200;
          }
        };
      }
    });

    assertTrue(UrlUtils.isReachable(url));
    assertTrue(disconnected.get(), "connection should have been disconnected");
  }


  @Test
  void testIsReachableSurvivesDisconnectUncheckedFailure() throws Exception {
    // the HTTP(S) branch's disconnect() runs in a finally block after a successful 200 response is already
    // read - an unchecked exception there must not flip an already-confirmed-reachable server to
    // unreachable, matching the same guarantee the non-HTTP branch's own close() finally-block gets from
    // testIsReachableFtpBranchDoesNotLeakFileDescriptorsOnSuccess above (round 25 replaced the old
    // URLConnection-based mechanism this comment used to reference here, testIsReachableSurvivesGetInput-
    // StreamUncheckedFailure, which no longer exists)
    URL url = new URL(null, "http://host/path", new URLStreamHandler() {
      @Override
      protected URLConnection openConnection(URL u) {
        return new HttpURLConnection(u) {
          @Override
          public void disconnect() {
            throw new IllegalStateException("simulated unchecked disconnect failure");
          }

          @Override
          public boolean usingProxy() {
            return false;
          }

          @Override
          public void connect() {
            // no-op: simulates a successful connect()
          }

          @Override
          public int getResponseCode() {
            return 200;
          }
        };
      }
    });

    assertTrue(UrlUtils.isReachable(url));
  }

}
