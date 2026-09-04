package org.pharmgkb.common.util;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipOutputStream;
import com.sun.net.httpserver.HttpServer;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.WriterOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * This is a JUnit test for {@link StreamUtils}.
 *
 * @author Mark Woon
 */
class StreamUtilsTest {

  @Test
  void testCopyUrlToFile(@TempDir Path tempDir) throws IOException {
    // was a live fetch against a gist.githubusercontent.com URL - flaky and gates CI/publish on an external
    // network dependency, unlike every other test in this class, which already uses a local HttpServer
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/test.txt", exchange -> {
      byte[] body = "hello, world".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.start();
    try {
      String url = "http://localhost:" + server.getAddress().getPort() + "/test.txt";
      Path file = tempDir.resolve("out.txt");
      StreamUtils.copyUrlToFile(url, file);

      StringWriter writer = new StringWriter();
      try (BufferedReader reader = StreamUtils.openReader(file)) {
        IOUtils.copy(reader, writer);
      }
      assertEquals("hello, world", writer.toString());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void testCopyUrlToFileFollowsRedirects(@TempDir Path tempDir) throws IOException {
    // following redirects is this method's whole documented reason for existing over
    // FileUtils.copyURLToFile()/IOUtils.copy(URL, File)
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/redirect", exchange -> {
      exchange.getResponseHeaders().add("Location", "/target");
      exchange.sendResponseHeaders(302, -1);
      exchange.close();
    });
    server.createContext("/target", exchange -> {
      byte[] body = "hello, world".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.start();
    try {
      String url = "http://localhost:" + server.getAddress().getPort() + "/redirect";
      Path file = tempDir.resolve("out.txt");
      StreamUtils.copyUrlToFile(url, file);
      assertEquals("hello, world", Files.readString(file, StandardCharsets.UTF_8));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void testCopyUrlToFileFollowsLongRedirectChain(@TempDir Path tempDir) throws IOException {
    // java.net.http.HttpClient's own default redirect limit (jdk.httpclient.redirects.retrylimit) is
    // effectively 4 followed hops - far short of main's Apache HttpClient default of 50 - so a chain this
    // long would previously fail with a misleading "...: 302" message (looking like a real terminal
    // redirect, not a hop-count cutoff) even though every hop is individually well-formed
    int hopCount = 10;
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    for (int i = 0; i < hopCount; i++) {
      int next = i;
      server.createContext("/hop" + i, exchange -> {
        exchange.getResponseHeaders().add("Location", "/hop" + (next + 1));
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
      });
    }
    server.createContext("/hop" + hopCount, exchange -> {
      byte[] body = "hello, world".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.start();
    try {
      String url = "http://localhost:" + server.getAddress().getPort() + "/hop0";
      Path file = tempDir.resolve("out.txt");
      StreamUtils.copyUrlToFile(url, file);
      assertEquals("hello, world", Files.readString(file, StandardCharsets.UTF_8));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void testCopyUrlToFileCarriesCookieAcrossRedirect(@TempDir Path tempDir) throws IOException {
    // main's Apache HttpClient carried a per-call cookie store across redirect hops (a fresh one each call,
    // since main built a fresh client per call too) - a redirect that sets a session cookie the follow-up
    // hop then requires (a common pattern for gated downloads) must keep working
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/start", exchange -> {
      exchange.getResponseHeaders().add("Set-Cookie", "session=abc123");
      exchange.getResponseHeaders().add("Location", "/target");
      exchange.sendResponseHeaders(302, -1);
      exchange.close();
    });
    server.createContext("/target", exchange -> {
      String cookie = exchange.getRequestHeaders().getFirst("Cookie");
      if (cookie != null && cookie.contains("session=abc123")) {
        byte[] body = "hello, world".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
      } else {
        exchange.sendResponseHeaders(403, -1);
      }
      exchange.close();
    });
    server.start();
    try {
      String url = "http://localhost:" + server.getAddress().getPort() + "/start";
      Path file = tempDir.resolve("out.txt");
      StreamUtils.copyUrlToFile(url, file);
      assertEquals("hello, world", Files.readString(file, StandardCharsets.UTF_8));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void testCopyUrlToFileHostOnlyCookiesSurviveAcrossDifferentHostsInRedirectChain(@TempDir Path tempDir)
      throws IOException {
    // round 32 finding 1 introduced HostOnlyCookieStore specifically so a host-only cookie (no explicit
    // Domain attribute - the most common real-world session-cookie shape) from one host isn't silently
    // destroyed when an unrelated host's SAME-NAMED cookie is added or later cleared (HttpCookie.equals()
    // never considers the host - see that class's own javadoc). This production wiring
    // (buildHttpClient()'s .cookieHandler(...)) was previously unpinned by any test (round 33 finding 2):
    // every other server test in this file uses "localhost" and only "localhost", so reverting
    // buildHttpClient() back to a bare CookieManager(null, sf_cookiePolicy) was invisible to the whole
    // suite. This test uses two genuinely different host strings (both resolving to the same loopback
    // HttpServer, no DNS faking needed) across a 4-hop chain - host B's own logout (Max-Age=0) must not
    // destroy host A's unrelated cookie
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/start", exchange -> {
      exchange.getResponseHeaders().add("Set-Cookie", "sid=FROM_LOCALHOST");
      exchange.getResponseHeaders().add("Location", "http://127.0.0.1:" + server.getAddress().getPort() + "/other");
      exchange.sendResponseHeaders(302, -1);
      exchange.close();
    });
    server.createContext("/other", exchange -> {
      exchange.getResponseHeaders().add("Set-Cookie", "sid=FROM_IP");
      exchange.getResponseHeaders().add("Location", "/logout");
      exchange.sendResponseHeaders(302, -1);
      exchange.close();
    });
    server.createContext("/logout", exchange -> {
      exchange.getResponseHeaders().add("Set-Cookie", "sid=; Max-Age=0");
      exchange.getResponseHeaders().add("Location", "http://localhost:" + server.getAddress().getPort() + "/final");
      exchange.sendResponseHeaders(302, -1);
      exchange.close();
    });
    server.createContext("/final", exchange -> {
      String cookie = exchange.getRequestHeaders().getFirst("Cookie");
      if (cookie != null && cookie.contains("sid=FROM_LOCALHOST")) {
        byte[] body = "hello, world".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
      } else {
        exchange.sendResponseHeaders(403, -1);
      }
      exchange.close();
    });
    server.start();
    try {
      String url = "http://localhost:" + server.getAddress().getPort() + "/start";
      Path file = tempDir.resolve("out.txt");
      StreamUtils.copyUrlToFile(url, file);
      assertEquals("hello, world", Files.readString(file, StandardCharsets.UTF_8));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void testCopyUrlToFileSuppressesCookieManagerSevereLogging(@TempDir Path tempDir) throws IOException {
    // java.net.CookieManager.put() logs "SEVERE: Invalid cookie for <url>: <full Set-Cookie value>" via its
    // own JUL logger ("java.net.CookieManager") whenever HttpCookie.parse() rejects a Set-Cookie header (e.g.
    // an unquoted comma inside a Max-Age-tagged cookie value's RFC-2965 comma-splitting - see review.log
    // round 23 findings 4/6) - by default this reaches the console (stderr) with zero logging configuration
    // on the caller's part, printing a potential session-cookie VALUE into logs. main's Apache HttpClient
    // never used java.util.logging for this at all - unexpected stderr noise (and a credential-adjacent leak
    // into logs) from an ordinary library call, not something a caller of this library's public API would
    // reasonably anticipate. Attaching a Handler directly to the logger (rather than capturing System.err)
    // tests the actual suppression mechanism: JUL's Logger.log() checks isLoggable() BEFORE invoking any
    // handler, so if the logger's level is OFF, this custom handler's publish() is never called either
    Logger cookieLogger = Logger.getLogger("java.net.CookieManager");
    List<LogRecord> captured = new ArrayList<>();
    Handler handler = new Handler() {
      @Override
      public void publish(LogRecord record) {
        captured.add(record);
      }

      @Override
      public void flush() {
      }

      @Override
      public void close() {
      }
    };
    cookieLogger.addHandler(handler);
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
      server.createContext("/", exchange -> {
        // an unquoted comma inside a Max-Age-tagged value's RFC-2965 comma-splitting makes
        // HttpCookie.parse() throw internally, which CookieManager.put() catches and logs SEVERE for
        exchange.getResponseHeaders().add("Set-Cookie", "prefs=US,CA; Max-Age=3600");
        byte[] body = "hello, world".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
      });
      server.start();
      try {
        String url = "http://localhost:" + server.getAddress().getPort() + "/";
        Path file = tempDir.resolve("out.txt");
        StreamUtils.copyUrlToFile(url, file);
      } finally {
        server.stop(0);
      }
      assertTrue(captured.isEmpty(), "expected no CookieManager log records, got: " + captured);
    } finally {
      cookieLogger.removeHandler(handler);
    }
  }

  @Test
  void testCopyUrlToFileCarriesCookieWithMaxAgeAcrossRedirect(@TempDir Path tempDir) throws IOException {
    // testCopyUrlToFileCarriesCookieAcrossRedirect above only exercises a bare "Set-Cookie: session=abc123"
    // (no attributes) - java.net.HttpCookie.parse()'s guessCookieVersion() classifies any Set-Cookie
    // containing a Max-Age (or Version) attribute as RFC 2965 "version 1", and CookieManager then renders
    // the follow-up Cookie header in RFC 2965 syntax (`$Version="1"; session="abc123";$Path="/";$Domain="..."`)
    // instead of the plain RFC 6265 `session=abc123` form essentially every modern server expects - silently
    // dropping the cookie and reintroducing the exact bare-403-on-a-session-gated-redirect failure this whole
    // per-call cookie feature (round 19) exists to fix, just via a different, previously-untested attribute.
    // A real session cookie with Max-Age (far more common than the bare-cookie shape above) is exactly this case
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/start", exchange -> {
      exchange.getResponseHeaders().add("Set-Cookie", "session=abc123; Max-Age=3600");
      exchange.getResponseHeaders().add("Location", "/target");
      exchange.sendResponseHeaders(302, -1);
      exchange.close();
    });
    server.createContext("/target", exchange -> {
      String cookie = exchange.getRequestHeaders().getFirst("Cookie");
      if (cookie != null && cookie.contains("session=abc123") && !cookie.contains("$Version")) {
        byte[] body = "hello, world".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
      } else {
        exchange.sendResponseHeaders(403, -1);
      }
      exchange.close();
    });
    server.start();
    try {
      String url = "http://localhost:" + server.getAddress().getPort() + "/start";
      Path file = tempDir.resolve("out.txt");
      StreamUtils.copyUrlToFile(url, file);
      assertEquals("hello, world", Files.readString(file, StandardCharsets.UTF_8));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void testCopyUrlToFileTruncatesExistingLongerFile(@TempDir Path tempDir) throws IOException {
    // BodyHandlers.ofFile(Path) defaults to CREATE+WRITE, NOT TRUNCATE_EXISTING - re-downloading a shorter
    // body into an existing, longer file must not leave the old file's tail bytes in place
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/", exchange -> {
      byte[] body = "v2\n".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.start();
    try {
      String url = "http://localhost:" + server.getAddress().getPort() + "/";
      Path file = tempDir.resolve("out.txt");
      Files.writeString(file, "v1-line1\nv1-line2\nv1-line3\n", StandardCharsets.UTF_8);
      StreamUtils.copyUrlToFile(url, file);
      assertEquals("v2\n", Files.readString(file, StandardCharsets.UTF_8));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void testCopyUrlToFileTimesOutOnStalledBody(@TempDir Path tempDir) throws IOException {
    // a server that sends headers/a first chunk then stalls mid-body (no further progress) must not be
    // allowed to hang the calling thread indefinitely - this is an IDLE timeout (see
    // testCopyUrlToFileSucceedsOnSlowButProgressingDownload for why it must not be a total-time deadline).
    // The package-private overload lets this test use a short timeout instead of waiting out the real 30s
    // default
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/", exchange -> {
      byte[] chunk = "0123456789".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, chunk.length * 2);
      OutputStream body = exchange.getResponseBody();
      try {
        body.write(chunk);
        body.flush();
        Thread.sleep(10_000);
        body.write(chunk);
      } catch (InterruptedException ignored) {
        // test is tearing down
      }
      body.close();
      exchange.close();
    });
    server.start();
    try {
      String url = "http://localhost:" + server.getAddress().getPort() + "/";
      Path file = tempDir.resolve("out.txt");
      long start = System.currentTimeMillis();
      assertThrows(IOException.class, () -> StreamUtils.copyUrlToFile(url, file, Duration.ofSeconds(1)));
      long elapsed = System.currentTimeMillis() - start;
      assertTrue(elapsed < 5_000, "took " + elapsed + "ms - should have timed out around 1s, not hung");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void testCopyUrlToFileWithCorruptGzipPreservesRawBytes(@TempDir Path tempDir) throws IOException {
    // if the response claims Content-Encoding: gzip but the body isn't valid gzip, decompression fails -
    // the raw downloaded bytes must survive that failure (matching this method's own "save to file even if
    // there's an error, so we can see what the error is" principle for a non-200 status), not be destroyed
    byte[] notGzip = "not actually gzip".getBytes(StandardCharsets.UTF_8);
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/", exchange -> {
      exchange.getResponseHeaders().add("Content-Encoding", "gzip");
      exchange.sendResponseHeaders(200, notGzip.length);
      exchange.getResponseBody().write(notGzip);
      exchange.close();
    });
    server.start();
    try {
      String url = "http://localhost:" + server.getAddress().getPort() + "/";
      Path file = tempDir.resolve("out.txt");
      IOException ex = assertThrows(IOException.class, () -> StreamUtils.copyUrlToFile(url, file));
      assertArrayEquals(notGzip, Files.readAllBytes(file));
      // a 200-status decode failure is a bare rethrow of the decode exception unless it's wrapped - every
      // other failure path in this method names the url (matching the class javadoc's own convention), so
      // this one shouldn't be the lone exception
      assertTrue(ex.getMessage() != null && ex.getMessage().contains(url), String.valueOf(ex.getMessage()));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void testCopyUrlToFileDoesNotSendH2cUpgradeHeadersForPlainHttp(@TempDir Path tempDir) throws IOException {
    // every per-call HttpClient is pinned to HTTP/1.1 client-wide (see buildHttpClient()'s own comment
    // for why - in short, a per-request-only pin can't cover an https:// URL that redirects to a
    // cleartext http:// target, since a request's version applies uniformly across all its automatic
    // redirect hops) - without
    // that pin, a cleartext request would send an unsolicited "h2c" (HTTP/2-over-cleartext) upgrade attempt
    // (Connection: Upgrade, Upgrade: h2c, Http2-Settings: ...) that main's Apache HttpClient 4.5
    // (HTTP/1.1-only) never sent, and that some older/strict servers, proxies, and WAFs react badly to
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    AtomicReference<String> connectionHeaderSeen = new AtomicReference<>();
    AtomicReference<String> upgradeHeaderSeen = new AtomicReference<>();
    server.createContext("/", exchange -> {
      connectionHeaderSeen.set(exchange.getRequestHeaders().getFirst("Connection"));
      upgradeHeaderSeen.set(exchange.getRequestHeaders().getFirst("Upgrade"));
      byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.start();
    try {
      String url = "http://localhost:" + server.getAddress().getPort() + "/";
      Path file = tempDir.resolve("out.txt");
      StreamUtils.copyUrlToFile(url, file);
      assertEquals(null, upgradeHeaderSeen.get(), "expected no Upgrade header for a plain http:// request");
      assertTrue(connectionHeaderSeen.get() == null || !connectionHeaderSeen.get().contains("Upgrade"),
          "expected no Connection: Upgrade for a plain http:// request, got " + connectionHeaderSeen.get());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void testCopyUrlToFileDecodesGzipContentEncoding(@TempDir Path tempDir) throws IOException {
    // main's Apache HttpClient sent "Accept-Encoding: gzip,deflate" and transparently gunzipped a
    // gzip-encoded response - java.net.http.HttpClient does neither on its own, so without this a server
    // that compresses (some do so unconditionally, regardless of what was requested) would silently leave
    // raw gzip bytes on disk where the caller expects the original, decoded text
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    AtomicReference<String> acceptEncodingSeen = new AtomicReference<>();
    server.createContext("/", exchange -> {
      acceptEncodingSeen.set(exchange.getRequestHeaders().getFirst("Accept-Encoding"));
      ByteArrayOutputStream gz = new ByteArrayOutputStream();
      try (GZIPOutputStream gzOut = new GZIPOutputStream(gz)) {
        gzOut.write("hello, world".getBytes(StandardCharsets.UTF_8));
      }
      byte[] body = gz.toByteArray();
      exchange.getResponseHeaders().add("Content-Encoding", "gzip");
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.start();
    try {
      String url = "http://localhost:" + server.getAddress().getPort() + "/";
      Path file = tempDir.resolve("out.txt");
      StreamUtils.copyUrlToFile(url, file);
      assertEquals("hello, world", Files.readString(file, StandardCharsets.UTF_8));
      assertTrue(acceptEncodingSeen.get() != null && acceptEncodingSeen.get().contains("gzip"),
          "expected an Accept-Encoding request header requesting gzip, got " + acceptEncodingSeen.get());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void testCopyUrlToFileDecodesXGzipContentEncodingAlias(@TempDir Path tempDir) throws IOException {
    // "x-gzip" is a legacy alias for "gzip" (RFC 7231 mentions it as deprecated-but-still-live) that
    // main's Apache HttpClient decoded identically to "gzip" - an exact "gzip" string match misses it,
    // silently leaving raw gzip bytes on disk from any server (typically an older/non-compliant one) that
    // sends this alias
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/", exchange -> {
      ByteArrayOutputStream gz = new ByteArrayOutputStream();
      try (GZIPOutputStream gzOut = new GZIPOutputStream(gz)) {
        gzOut.write("hello, world".getBytes(StandardCharsets.UTF_8));
      }
      byte[] body = gz.toByteArray();
      exchange.getResponseHeaders().add("Content-Encoding", "x-gzip");
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.start();
    try {
      String url = "http://localhost:" + server.getAddress().getPort() + "/";
      Path file = tempDir.resolve("out.txt");
      StreamUtils.copyUrlToFile(url, file);
      assertEquals("hello, world", Files.readString(file, StandardCharsets.UTF_8));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void testCopyUrlToFileDecodesGzipContentEncodingInCommaSeparatedList(@TempDir Path tempDir) throws IOException {
    // RFC 7231 allows Content-Encoding to be a comma-separated list of codings (optionally with ";param"
    // suffixes) - main's Apache HttpClient parsed it as such; an exact "gzip" string match misses "gzip"
    // when it isn't the entire header value
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/", exchange -> {
      ByteArrayOutputStream gz = new ByteArrayOutputStream();
      try (GZIPOutputStream gzOut = new GZIPOutputStream(gz)) {
        gzOut.write("hello, world".getBytes(StandardCharsets.UTF_8));
      }
      byte[] body = gz.toByteArray();
      exchange.getResponseHeaders().add("Content-Encoding", "identity, gzip;q=1");
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.start();
    try {
      String url = "http://localhost:" + server.getAddress().getPort() + "/";
      Path file = tempDir.resolve("out.txt");
      StreamUtils.copyUrlToFile(url, file);
      assertEquals("hello, world", Files.readString(file, StandardCharsets.UTF_8));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void testCopyUrlToFileDecodesGzipContentEncodingAcrossRepeatedHeaderLines(@TempDir Path tempDir) throws IOException {
    // RFC 7230 section 3.2.2: multiple header lines with the same field name are semantically identical to
    // one comma-joined line - "Content-Encoding: identity" followed by a separate "Content-Encoding: gzip"
    // line means the same thing as "Content-Encoding: identity, gzip". Checking only headers().firstValue(...)
    // sees just the first line and misses gzip entirely, silently leaving raw gzip bytes on disk
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/", exchange -> {
      ByteArrayOutputStream gz = new ByteArrayOutputStream();
      try (GZIPOutputStream gzOut = new GZIPOutputStream(gz)) {
        gzOut.write("hello, world".getBytes(StandardCharsets.UTF_8));
      }
      byte[] body = gz.toByteArray();
      exchange.getResponseHeaders().add("Content-Encoding", "identity");
      exchange.getResponseHeaders().add("Content-Encoding", "gzip");
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.start();
    try {
      String url = "http://localhost:" + server.getAddress().getPort() + "/";
      Path file = tempDir.resolve("out.txt");
      StreamUtils.copyUrlToFile(url, file);
      assertEquals("hello, world", Files.readString(file, StandardCharsets.UTF_8));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void testCopyUrlToFileRejectsUnrecognizedContentEncoding(@TempDir Path tempDir) throws IOException {
    // main's Apache HttpClient (ResponseContentEncoding's default codec registry) also decoded "deflate" -
    // round 15 deliberately didn't extend isGzipEncoded() to it, since this method only ever REQUESTS
    // "Accept-Encoding: gzip", never deflate. But a server can send whatever Content-Encoding it wants
    // regardless of what was requested (the same premise isGzipEncoded()'s own javadoc already relies on for
    // gzip) - if it sends "deflate" (or any other coding this method doesn't recognize), the raw compressed
    // bytes are silently written to disk with a normal 200 status, indistinguishable from a genuinely
    // successful plain-text download until something tries to read it. Must fail loudly instead, naming the
    // unrecognized coding, rather than silently leave unusable bytes on disk
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/", exchange -> {
      ByteArrayOutputStream compressed = new ByteArrayOutputStream();
      try (java.util.zip.DeflaterOutputStream out = new java.util.zip.DeflaterOutputStream(compressed)) {
        out.write("hello, world".getBytes(StandardCharsets.UTF_8));
      }
      byte[] body = compressed.toByteArray();
      exchange.getResponseHeaders().add("Content-Encoding", "deflate");
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.start();
    try {
      String url = "http://localhost:" + server.getAddress().getPort() + "/";
      Path file = tempDir.resolve("out.txt");
      IOException ex = assertThrows(IOException.class, () -> StreamUtils.copyUrlToFile(url, file));
      assertTrue(ex.getMessage() != null && ex.getMessage().contains("deflate"),
          String.valueOf(ex.getMessage()));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void testCopyUrlToFileRejectsStackedContentEncodingIncludingGzip(@TempDir Path tempDir) throws IOException {
    // "contains gzip" is not the same test as "is exactly gzip" - a response claiming TWO stacked codings,
    // one of which happens to be gzip (e.g. "deflate, gzip", body = gzip(deflate(x))), can't be handled by a
    // single decompressGzipInPlace() pass, which only undoes ONE layer. Treating this the same as a plain
    // "gzip" response (as an exact-string-anywhere-in-the-list check would) would either fail with a
    // misleading ZipException (if the outer layer happens to gunzip cleanly but the inner bytes aren't valid
    // gzip) or, worse, silently write a still-partially-compressed file to disk with a normal 200 status
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/", exchange -> {
      ByteArrayOutputStream deflated = new ByteArrayOutputStream();
      try (java.util.zip.DeflaterOutputStream out = new java.util.zip.DeflaterOutputStream(deflated)) {
        out.write("hello, world".getBytes(StandardCharsets.UTF_8));
      }
      ByteArrayOutputStream gz = new ByteArrayOutputStream();
      try (GZIPOutputStream gzOut = new GZIPOutputStream(gz)) {
        gzOut.write(deflated.toByteArray());
      }
      byte[] body = gz.toByteArray();
      exchange.getResponseHeaders().add("Content-Encoding", "deflate, gzip");
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.start();
    try {
      String url = "http://localhost:" + server.getAddress().getPort() + "/";
      Path file = tempDir.resolve("out.txt");
      IOException ex = assertThrows(IOException.class, () -> StreamUtils.copyUrlToFile(url, file));
      assertTrue(ex.getMessage() != null && ex.getMessage().contains("deflate")
              && ex.getMessage().contains("gzip"),
          String.valueOf(ex.getMessage()));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void testCopyUrlToFileRejectsRepeatedGzipContentEncoding(@TempDir Path tempDir) throws IOException {
    // "Content-Encoding: gzip, gzip" (body double-gzipped) is the same "more than one layer" hazard as the
    // stacked-with-an-unknown-coding case above, just with BOTH codings individually recognized - a naive
    // "does the list contain gzip" check would take the decode path, undo exactly one of the two layers, and
    // silently leave the file still gzip-compressed on disk with a normal 200 status
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/", exchange -> {
      ByteArrayOutputStream innerGz = new ByteArrayOutputStream();
      try (GZIPOutputStream gzOut = new GZIPOutputStream(innerGz)) {
        gzOut.write("hello, world".getBytes(StandardCharsets.UTF_8));
      }
      ByteArrayOutputStream outerGz = new ByteArrayOutputStream();
      try (GZIPOutputStream gzOut = new GZIPOutputStream(outerGz)) {
        gzOut.write(innerGz.toByteArray());
      }
      byte[] body = outerGz.toByteArray();
      exchange.getResponseHeaders().add("Content-Encoding", "gzip, gzip");
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.start();
    try {
      String url = "http://localhost:" + server.getAddress().getPort() + "/";
      Path file = tempDir.resolve("out.txt");
      IOException ex = assertThrows(IOException.class, () -> StreamUtils.copyUrlToFile(url, file));
      assertTrue(ex.getMessage() != null && ex.getMessage().contains("gzip"), String.valueOf(ex.getMessage()));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void testCopyUrlToFileWithEmptyUnrecognizedContentEncodingBodySucceeds(@TempDir Path tempDir) throws IOException {
    // the gzip-decode path already exempts a zero-length body (testCopyUrlToFileWithEmptyGzipBodySucceeds) -
    // for the exact same reason (nothing to decode either way), a zero-length body must also be exempt from
    // the unrecognized-Content-Encoding rejection, not just the gzip one. main's Apache HttpClient succeeded
    // for a zero-length entity regardless of Content-Encoding
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/", exchange -> {
      exchange.getResponseHeaders().add("Content-Encoding", "deflate");
      exchange.sendResponseHeaders(200, -1);
      exchange.close();
    });
    server.start();
    try {
      String url = "http://localhost:" + server.getAddress().getPort() + "/";
      Path file = tempDir.resolve("out.txt");
      StreamUtils.copyUrlToFile(url, file);
      assertEquals(0, Files.size(file));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void testCopyUrlToFileWithUnrecognizedContentEncodingErrorBodyNamesTheStatus(@TempDir Path tempDir)
      throws IOException {
    // matches testCopyUrlToFileWithCorruptGzipErrorBodyNamesTheStatus's convention for the gzip-decode-failure
    // path: if the status is already non-200, that's the more diagnostically useful thing to report (naming
    // the url and status) rather than a bare "unrecognized Content-Encoding" message - the encoding diagnostic
    // itself should still be preserved as a suppressed exception, not discarded
    byte[] notDeflate = "<html>404 Not Found</html>".getBytes(StandardCharsets.UTF_8);
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/", exchange -> {
      exchange.getResponseHeaders().add("Content-Encoding", "deflate");
      exchange.sendResponseHeaders(404, notDeflate.length);
      exchange.getResponseBody().write(notDeflate);
      exchange.close();
    });
    server.start();
    try {
      String url = "http://localhost:" + server.getAddress().getPort() + "/";
      Path file = tempDir.resolve("out.txt");
      IOException ex = assertThrows(IOException.class, () -> StreamUtils.copyUrlToFile(url, file));
      assertTrue(ex.getMessage() != null && ex.getMessage().contains(url) && ex.getMessage().contains("404"),
          String.valueOf(ex.getMessage()));
      assertTrue(ex.getSuppressed().length == 1 && ex.getSuppressed()[0].getMessage() != null
              && ex.getSuppressed()[0].getMessage().contains("deflate"),
          "expected a suppressed exception naming the unrecognized coding, got: " + Arrays.toString(ex.getSuppressed()));
      assertArrayEquals(notDeflate, Files.readAllBytes(file));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void testCopyUrlToFileDecodesGzipContentEncodingWithEmptyCodingInList(@TempDir Path tempDir) throws IOException {
    // ", gzip" (a leading comma, splitting into ["", "gzip"]) must still be treated as exactly one recognized
    // coding, not two - the empty leading entry from the split must be filtered out rather than counted as a
    // second, unrecognized coding that would otherwise route this to the rejection path instead of decoding.
    // (String.split(",") silently discards TRAILING empty strings - "gzip," actually splits into just
    // ["gzip"], never reaching the empty-name guard at all - so a trailing comma can't exercise this; a
    // LEADING or interior empty entry can, and does)
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/", exchange -> {
      ByteArrayOutputStream gz = new ByteArrayOutputStream();
      try (GZIPOutputStream gzOut = new GZIPOutputStream(gz)) {
        gzOut.write("hello, world".getBytes(StandardCharsets.UTF_8));
      }
      byte[] body = gz.toByteArray();
      exchange.getResponseHeaders().add("Content-Encoding", ", gzip");
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.start();
    try {
      String url = "http://localhost:" + server.getAddress().getPort() + "/";
      Path file = tempDir.resolve("out.txt");
      StreamUtils.copyUrlToFile(url, file);
      assertEquals("hello, world", Files.readString(file, StandardCharsets.UTF_8));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void testCopyUrlToFileIgnoresIdentityWithParameterContentEncoding(@TempDir Path tempDir) throws IOException {
    // the ";param=value" strip (RFC 7231 section 3.1.2.2) must apply to "identity" too, not just "gzip" - an
    // unstripped "identity;q=1" wouldn't case-insensitively equal "identity" and would be misreported as an
    // unrecognized coding, even though it means exactly the same thing as a bare "identity"
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/", exchange -> {
      byte[] body = "hello, world".getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Encoding", "identity;q=1");
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.start();
    try {
      String url = "http://localhost:" + server.getAddress().getPort() + "/";
      Path file = tempDir.resolve("out.txt");
      StreamUtils.copyUrlToFile(url, file);
      assertEquals("hello, world", Files.readString(file, StandardCharsets.UTF_8));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void testCopyUrlToFileDecodesGzipErrorBody(@TempDir Path tempDir) throws IOException {
    // main's Apache HttpClient decoded gzip regardless of status code (ResponseContentEncoding gates only
    // on content-compression being enabled and a non-empty entity, not on the status) - the saved body for
    // a non-200 response must be just as readable as it was on main, not left as raw, undecodable gzip bytes
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/", exchange -> {
      ByteArrayOutputStream gz = new ByteArrayOutputStream();
      try (GZIPOutputStream gzOut = new GZIPOutputStream(gz)) {
        gzOut.write("not found".getBytes(StandardCharsets.UTF_8));
      }
      byte[] body = gz.toByteArray();
      exchange.getResponseHeaders().add("Content-Encoding", "gzip");
      exchange.sendResponseHeaders(404, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.start();
    try {
      String url = "http://localhost:" + server.getAddress().getPort() + "/";
      Path file = tempDir.resolve("out.txt");
      assertThrows(IOException.class, () -> StreamUtils.copyUrlToFile(url, file));
      assertEquals("not found", Files.readString(file, StandardCharsets.UTF_8));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void testCopyUrlToFileWithCorruptGzipErrorBodyNamesTheStatus(@TempDir Path tempDir) throws IOException {
    // a proxy/CDN that blanket-adds Content-Encoding: gzip to a canned (non-gzip) error page is a realistic
    // combination of the previous two tests' scenarios - decoding fails, but the failure must not swallow
    // the more diagnostically useful non-200 status: naming "404" beats a bare, url-less ZipException
    byte[] notGzip = "<html>404 Not Found</html>".getBytes(StandardCharsets.UTF_8);
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/", exchange -> {
      exchange.getResponseHeaders().add("Content-Encoding", "gzip");
      exchange.sendResponseHeaders(404, notGzip.length);
      exchange.getResponseBody().write(notGzip);
      exchange.close();
    });
    server.start();
    try {
      String url = "http://localhost:" + server.getAddress().getPort() + "/";
      Path file = tempDir.resolve("out.txt");
      IOException ex = assertThrows(IOException.class, () -> StreamUtils.copyUrlToFile(url, file));
      assertTrue(ex.getMessage() != null && ex.getMessage().contains(url) && ex.getMessage().contains("404"),
          String.valueOf(ex.getMessage()));
      assertArrayEquals(notGzip, Files.readAllBytes(file));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void testCopyUrlToFileWithEmptyGzipBodySucceeds(@TempDir Path tempDir) throws IOException {
    // main's Apache HttpClient skipped decoding entirely for a zero-length entity (ResponseContentEncoding's
    // getContentLength() != 0 guard) - a legitimately empty file from a gzip-enabled server must still
    // succeed, not fail with an EOFException from feeding zero bytes to GZIPInputStream
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/", exchange -> {
      exchange.getResponseHeaders().add("Content-Encoding", "gzip");
      exchange.sendResponseHeaders(200, -1);
      exchange.close();
    });
    server.start();
    try {
      String url = "http://localhost:" + server.getAddress().getPort() + "/";
      Path file = tempDir.resolve("out.txt");
      StreamUtils.copyUrlToFile(url, file);
      assertEquals(0, Files.size(file));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void testDecompressGzipInPlacePreservesSymlink(@TempDir Path tempDir) throws IOException {
    // Files.move(decompressed, file, REPLACE_EXISTING) replaces the LINK itself if file is a symlink, even
    // though the download that wrote the raw gzip bytes into file followed the link to its real target -
    // the decompressed content must end up back at the real target, with the symlink still a symlink
    // pointing at it, not orphaning the real target holding stale raw gzip bytes
    Path realTarget = tempDir.resolve("real.txt");
    try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(realTarget))) {
      out.write("hello".getBytes(StandardCharsets.UTF_8));
    }
    Path link = tempDir.resolve("link.txt");
    Files.createSymbolicLink(link, realTarget);

    StreamUtils.decompressGzipInPlace(link);

    assertTrue(Files.isSymbolicLink(link), "link.txt should still be a symlink");
    assertEquals(realTarget, Files.readSymbolicLink(link));
    assertEquals("hello", Files.readString(realTarget, StandardCharsets.UTF_8));
    assertEquals("hello", Files.readString(link, StandardCharsets.UTF_8));
  }

  @Test
  void testDecompressGzipInPlaceDoesNotLeakFileDescriptorOnMalformedHeader(@TempDir Path tempDir) throws IOException {
    // if the GZIPInputStream constructor throws (a malformed 2-byte magic header, not just a truncated-but-
    // otherwise-valid one), the raw Files.newInputStream(file) it was constructing from is never assigned to
    // the try-with-resources variable, so it's never closed. Reproduced by counting this process's open file
    // descriptors via /proc/self/fd (Linux-only - there's no portable JDK API for this, so this is skipped
    // elsewhere): a leak shows up as roughly one extra open fd per failed attempt; the fix must show no
    // per-attempt growth
    Path proc = Path.of("/proc/self/fd");
    Assumptions.assumeTrue(Files.isDirectory(proc), "requires /proc/self/fd (Linux)");

    Path file = tempDir.resolve("not-gzip.txt");
    Files.write(file, "not actually gzip".getBytes(StandardCharsets.UTF_8));

    int attempts = 50;
    long before = countOpenFds(proc);
    for (int i = 0; i < attempts; i++) {
      assertThrows(IOException.class, () -> StreamUtils.decompressGzipInPlace(file));
    }
    long after = countOpenFds(proc);

    assertTrue(after - before < attempts,
        "expected no per-attempt fd growth after " + attempts + " failed attempts, but open fd count went " +
            "from " + before + " to " + after);
  }

  private static long countOpenFds(Path proc) throws IOException {
    try (var stream = Files.list(proc)) {
      return stream.count();
    }
  }

  @Test
  void testDecompressGzipInPlacePreservesDestinationPermissions(@TempDir Path tempDir) throws IOException {
    // the final swap into place must write THROUGH the destination's existing identity, not replace it with
    // a new inode carrying default permissions - Files.move()/Files.copy() with REPLACE_EXISTING both
    // verified (via a standalone probe) to unlink-and-recreate rather than truncate-in-place on this
    // filesystem, silently resetting a deliberately-restricted destination to whatever the process's default
    // umask produces
    Path file = tempDir.resolve("out.txt");
    try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
      out.write("hello".getBytes(StandardCharsets.UTF_8));
    }
    Set<PosixFilePermission> restricted = PosixFilePermissions.fromString("rw-------");
    Files.setPosixFilePermissions(file, restricted);

    StreamUtils.decompressGzipInPlace(file);

    assertEquals("hello", Files.readString(file, StandardCharsets.UTF_8));
    assertEquals(restricted, Files.getPosixFilePermissions(file),
        "destination's pre-existing permissions must survive decompression");
  }

  @Test
  void testDecompressGzipInPlacePreservesHardLinks(@TempDir Path tempDir) throws IOException {
    // same identity concern as testDecompressGzipInPlacePreservesDestinationPermissions, but for hard links:
    // an unlink-and-recreate swap orphans any other name hard-linked to the same inode, leaving it holding
    // the stale raw gzip bytes instead of following along with the decompressed content
    Path file = tempDir.resolve("out.txt");
    try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
      out.write("hello".getBytes(StandardCharsets.UTF_8));
    }
    Path hardLink = tempDir.resolve("other.txt");
    Files.createLink(hardLink, file);

    StreamUtils.decompressGzipInPlace(file);

    assertEquals("hello", Files.readString(file, StandardCharsets.UTF_8));
    assertEquals("hello", Files.readString(hardLink, StandardCharsets.UTF_8),
        "a hard-linked name must see the decompressed content too, not be orphaned holding stale raw bytes");
  }

  @Test
  void testDecompressGzipInPlaceDoesNotFollowSymlinkAtPredictableTempPath(@TempDir Path tempDir) throws IOException {
    // the decompression temp file must not be a PREDICTABLE path derived from the destination's own name
    // (e.g. "<destination>.decompressed.tmp") - a symlink pre-planted at that exact path would be followed
    // when the temp file is opened for writing (CREATE + WRITE + TRUNCATE_EXISTING follows symlinks), letting
    // whoever can write anywhere in the destination's directory redirect the downloaded (attacker-influenced)
    // payload onto an arbitrary file the process can write to - with the symlink itself deleted afterward,
    // leaving no trace (CWE-59/CWE-377)
    Path victim = tempDir.resolve("victim.conf");
    Files.writeString(victim, "PRECIOUS CONFIG");
    Path file = tempDir.resolve("download.txt");
    try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
      out.write("payload".getBytes(StandardCharsets.UTF_8));
    }
    Path predictableTempPath = tempDir.resolve("download.txt.decompressed.tmp");
    Files.createSymbolicLink(predictableTempPath, victim);

    StreamUtils.decompressGzipInPlace(file);

    assertEquals("PRECIOUS CONFIG", Files.readString(victim, StandardCharsets.UTF_8),
        "a symlink planted at the predictable temp path must not redirect the download onto an arbitrary file");
  }

  @Test
  void testDecompressGzipInPlaceDoesNotDestroyUnrelatedFileAtPredictableTempPath(@TempDir Path tempDir)
      throws IOException {
    // same predictable-temp-path concern as ...DoesNotFollowSymlinkAtPredictableTempPath, but for an
    // unrelated REGULAR file (not a symlink) that happens to already occupy that exact path - it must
    // survive untouched, not be silently truncated and then deleted
    Path file = tempDir.resolve("download.txt");
    try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
      out.write("payload".getBytes(StandardCharsets.UTF_8));
    }
    Path predictableTempPath = tempDir.resolve("download.txt.decompressed.tmp");
    Files.writeString(predictableTempPath, "PRECIOUS UNRELATED DATA");

    StreamUtils.decompressGzipInPlace(file);

    assertTrue(Files.exists(predictableTempPath), "an unrelated file at the predictable temp path must survive");
    assertEquals("PRECIOUS UNRELATED DATA", Files.readString(predictableTempPath, StandardCharsets.UTF_8));
  }

  @Test
  void testDecompressGzipInPlaceSucceedsForLongDestinationFilename(@TempDir Path tempDir) throws IOException {
    // a predictable sibling temp path derived from the destination's filename (target's name plus the
    // 17-char ".decompressed.tmp" suffix) can exceed the filesystem's NAME_MAX (255 bytes on ext4/most Linux
    // filesystems) for an otherwise perfectly valid destination filename, failing an otherwise-successful
    // gzip download outright
    String longName = "y".repeat(240);
    Path file = tempDir.resolve(longName);
    try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
      out.write("payload".getBytes(StandardCharsets.UTF_8));
    }

    StreamUtils.decompressGzipInPlace(file);

    assertEquals("payload", Files.readString(file, StandardCharsets.UTF_8));
  }

  @Test
  void testDecompressGzipInPlaceDoesNotRequireParentDirectoryWritePermission(@TempDir Path tempDir)
      throws IOException {
    // the temp file must not need to be CREATED as a sibling of the destination - that requires WRITE
    // permission on the destination's PARENT DIRECTORY, which downloading into an already-existing
    // destination file never required before (only WRITE permission on the file itself) - the round-13
    // write-in-place fix for the FINAL step already avoided this, but creating the OLD predictable sibling
    // temp path still needed it, so the round-13 javadoc's "no longer requires parent-directory write
    // permission" claim wasn't actually fully true until this fix
    Path dir = tempDir.resolve("ro");
    Files.createDirectory(dir);
    Path file = dir.resolve("out.txt");
    try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
      out.write("payload".getBytes(StandardCharsets.UTF_8));
    }
    Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("r-x------"));
    try {
      StreamUtils.decompressGzipInPlace(file);
      assertEquals("payload", Files.readString(file, StandardCharsets.UTF_8));
    } finally {
      Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwx------"));
    }
  }

  @Test
  void testCopyUrlToFileTimesOutOnStalledHeaders(@TempDir Path tempDir) throws IOException {
    // a server that never even sends response headers is just as much a stall as
    // testCopyUrlToFileTimesOutOnStalledBody's mid-body case, and must surface the same friendly,
    // idle-timeout message - not a raw HttpTimeoutException naming neither the url nor the timeout, which
    // is what HttpRequest.Builder.timeout() (a separate, redundant header-wait cap) produces instead
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/", exchange -> {
      try {
        Thread.sleep(10_000);
      } catch (InterruptedException ignored) {
        // test is tearing down
      }
      exchange.sendResponseHeaders(200, -1);
      exchange.close();
    });
    server.start();
    try {
      String url = "http://localhost:" + server.getAddress().getPort() + "/";
      Path file = tempDir.resolve("out.txt");
      long start = System.currentTimeMillis();
      IOException ex = assertThrows(IOException.class,
          () -> StreamUtils.copyUrlToFile(url, file, Duration.ofSeconds(1)));
      long elapsed = System.currentTimeMillis() - start;
      assertTrue(elapsed < 5_000, "took " + elapsed + "ms - should have timed out around 1s, not hung");
      assertTrue(ex.getMessage().contains("Timed out downloading"), ex.getMessage());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void testCopyUrlToFileSucceedsOnSlowButProgressingDownload(@TempDir Path tempDir) throws IOException {
    // the idle timeout must bound STALLS, not total download time - a download that keeps making steady
    // progress (even if the whole thing takes much longer than the idle-timeout value) must still succeed,
    // matching main's behavior (no timeout at all) for any download that isn't actually stuck
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    int chunkCount = 8;
    server.createContext("/", exchange -> {
      byte[] chunk = "0123456789".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, (long) chunk.length * chunkCount);
      OutputStream body = exchange.getResponseBody();
      try {
        for (int i = 0; i < chunkCount; i++) {
          body.write(chunk);
          body.flush();
          Thread.sleep(150);
        }
      } catch (InterruptedException ignored) {
        // test is tearing down
      }
      body.close();
      exchange.close();
    });
    server.start();
    try {
      String url = "http://localhost:" + server.getAddress().getPort() + "/";
      Path file = tempDir.resolve("out.txt");
      // idle timeout (500ms) is well under the ~1.2s total download time, but each chunk arrives well
      // within that window, so a total-deadline-style timeout would wrongly kill this
      StreamUtils.copyUrlToFile(url, file, Duration.ofMillis(500));
      assertEquals("0123456789".repeat(chunkCount), Files.readString(file, StandardCharsets.UTF_8));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void testMapDownloadExecutionCauseRethrowsErrorUnwrapped() {
    // CompletableFuture.get() wraps ANY Throwable that completed the future exceptionally in an
    // ExecutionException, including a JVM-fatal-adjacent Error (e.g. OutOfMemoryError surfacing from inside
    // the JDK's async HTTP pipeline) - that must propagate as-is, not get wrapped in an IOException a caller
    // could silently absorb via catch (IOException e) { retry/log/skip }
    Error error = new OutOfMemoryError("test");
    Error thrown = assertThrows(OutOfMemoryError.class, () ->
        StreamUtils.mapDownloadExecutionCause("http://example.com/", Duration.ofSeconds(30), error));
    assertEquals(error, thrown);
  }

  @Test
  void testMapDownloadExecutionCauseUnwrapsUncheckedIOException() {
    // the JDK's own redirect-following internals wrap some IOExceptions in UncheckedIOException rather than
    // throwing them directly (e.g. an empty/missing redirect Location) - the reported cause must be the real
    // IOException, not an extra layer of indirection
    IOException realCause = new IOException("simulated real cause");
    UncheckedIOException wrapped = new UncheckedIOException(realCause);
    IOException result = StreamUtils.mapDownloadExecutionCause("http://example.com/", Duration.ofSeconds(30), wrapped);
    assertEquals(realCause, result.getCause());
  }

  @Test
  void testShouldAcceptCookieNormalizesDomainToLeadingDotForm() throws java.net.URISyntaxException {
    // shouldAcceptCookie() (the actual CookiePolicy implementation, extracted to a package-private static
    // method for testability - domainMatches() alone can't be unit-tested for this since the bug it closes
    // lives entirely in java.net.InMemoryCookieStore's own RETRIEVAL-time matching, not in this class's own
    // storage-time domainMatches() check) must rewrite an accepted cookie's domain to the leading-dot
    // (Netscape/RFC 2109) form before CookieManager.put() ever stores it - see the field javadoc above
    // sf_cookiePolicy for why (round 30 finding 1)
    java.net.HttpCookie cookie = new java.net.HttpCookie("sid", "SECRET");
    cookie.setDomain("example.com");
    assertTrue(StreamUtils.shouldAcceptCookie(new java.net.URI("http://www.example.com/"), cookie));
    assertEquals(".example.com", cookie.getDomain());
    assertEquals(0, cookie.getVersion());
    // a domain domainMatches() itself already rejects must still be rejected here, and left unmutated
    java.net.HttpCookie rejected = new java.net.HttpCookie("sid", "SECRET");
    rejected.setDomain("evil.example.org");
    assertFalse(StreamUtils.shouldAcceptCookie(new java.net.URI("http://www.example.com/"), rejected));
    assertEquals("evil.example.org", rejected.getDomain());
    // a domain ALREADY in leading-dot form must be left as-is, not double-dotted - mutation-verified gap
    // (round 31 finding 3): no existing test passed a leading-dot Domain through shouldAcceptCookie() at
    // all, and a double leading dot (".." + domain) makes netscapeDomainMatches() reject every host,
    // including the exact one the cookie was set from - reintroducing the bare-403-on-redirect failure this
    // whole cookie feature exists to fix, for any server that writes Domain in the legal leading-dot form
    java.net.HttpCookie alreadyDotted = new java.net.HttpCookie("sid", "SECRET");
    alreadyDotted.setDomain(".example.com");
    assertTrue(StreamUtils.shouldAcceptCookie(new java.net.URI("http://www.example.com/"), alreadyDotted));
    assertEquals(".example.com", alreadyDotted.getDomain());
  }

  @Test
  void testShouldAcceptCookiePreventsNonLabelBoundaryRetrievalLeak() throws java.net.URISyntaxException {
    // round 30 finding 1: java.net.InMemoryCookieStore.netscapeDomainMatches() (used at RETRIEVAL time for
    // every version-0 cookie, forced by shouldAcceptCookie() above) has the IDENTICAL non-label-boundary bug
    // this class's own domainMatches() already works around at STORAGE time (round 29 finding 3) - its own
    // "H" (extra host prefix) variable is computed and then never checked for an embedded dot at all, read
    // directly from JDK 21 source - and is actually LAXER than HttpCookie.domainMatches() (no depth limit
    // whatsoever). A cookie legitimately ACCEPTED by domainMatches() (Domain=example.com from host
    // www.example.com) was still being replayed to a totally unrelated host on retrieval - "notexample.com"
    // merely shares example.com's trailing 11 characters with no "." boundary between them. Verified via a
    // real java.net.CookieManager/InMemoryCookieStore round-trip - no network I/O or DNS faking needed,
    // since CookieStore.get(URI) never actually resolves the URI's host string
    java.net.HttpCookie cookie = new java.net.HttpCookie("sid", "SECRET");
    cookie.setDomain("example.com");
    cookie.setPath("/");
    java.net.URI settingUri = new java.net.URI("http://www.example.com/");
    assertTrue(StreamUtils.shouldAcceptCookie(settingUri, cookie));
    java.net.CookieStore store = new java.net.CookieManager().getCookieStore();
    store.add(settingUri, cookie);

    assertTrue(hasNamedCookie(store.get(new java.net.URI("http://www.example.com/")), "sid"),
        "cookie must still reach the exact host it was set for");
    assertTrue(hasNamedCookie(store.get(new java.net.URI("http://evil.example.com/")), "sid"),
        "cookie must still reach a genuine label-boundary subdomain");
    assertFalse(hasNamedCookie(store.get(new java.net.URI("http://notexample.com/")), "sid"),
        "cookie must not leak to an unrelated host sharing a suffix with no label boundary");
  }

  private static boolean hasNamedCookie(java.util.List<java.net.HttpCookie> cookies, String name) {
    return cookies.stream().anyMatch(c -> name.equals(c.getName()));
  }

  @Test
  void testShouldAcceptCookieStoresExactMatchAsTrulyHostOnly() throws java.net.URISyntaxException {
    // round 31 finding 2: CookieManager.put() auto-fills an absent Domain attribute verbatim to the request
    // host, so a plain host-only cookie (no explicit Domain at all - the most common real-world session-
    // cookie shape) is indistinguishable here from an explicit self-referential Domain=<host> attribute.
    // Previously BOTH shapes were stored with domain equal to the host (leading-dot-normalized, round 30
    // finding 1) - correct for the retrieval-time SIBLING-host leak that fix closed, but RFC 6265 domain-
    // matching then correctly (not buggily) treats ".example.com" as a real PARENT domain, so it was still
    // replayed to every genuine SUBDOMAIN of the setting host too, e.g. a session cookie set at
    // hgdownload.soe.ucsc.edu (this library's own real download target) was also sent to
    // evil.hgdownload.soe.ucsc.edu - a real regression vs main, which enforces host-only for a cookie with no
    // explicit Domain. Fixed by storing an EXACT match as a genuinely host-only cookie (domain cleared to
    // null - InMemoryCookieStore.add() skips domainIndex entirely for one, verified from JDK 21 source),
    // matched only by exact URI, not by any domain-matching at all
    java.net.HttpCookie cookie = new java.net.HttpCookie("sid", "SECRET");
    cookie.setDomain("www.example.com");
    cookie.setPath("/");
    java.net.URI settingUri = new java.net.URI("http://www.example.com/");
    assertTrue(StreamUtils.shouldAcceptCookie(settingUri, cookie));
    assertNull(cookie.getDomain());
    java.net.CookieStore store = new java.net.CookieManager().getCookieStore();
    store.add(settingUri, cookie);

    assertTrue(hasNamedCookie(store.get(new java.net.URI("http://www.example.com/")), "sid"),
        "cookie must still reach the exact host it was set for");
    assertFalse(hasNamedCookie(store.get(new java.net.URI("http://evil.www.example.com/")), "sid"),
        "cookie must not leak to a subdomain the setting host never asserted a Domain attribute for");
    assertFalse(hasNamedCookie(store.get(new java.net.URI("http://example.com/")), "sid"),
        "cookie must not leak to the setting host's own parent domain either");
  }

  @Test
  void testShouldAcceptCookieStoresEmptyDomainAsGenuinelyHostOnly() throws java.net.URISyntaxException {
    // round 33 finding 1: a literal "Domain=;" (or bare "Domain=") attribute parses via HttpCookie.parse() to
    // a non-null, EMPTY domain (verified directly) - a real, distinct third shape from both "no Domain
    // attribute at all" (null, auto-filled by CookieManager.put() to the host) and "an explicit Domain
    // matching the host" (isEffectivelyHostOnly() above). Neither isEffectivelyHostOnly() (bails on
    // domain.isEmpty()) nor the leading-dot normalization below (guarded on !domain.isEmpty()) touches this
    // shape, so it fell through to domainMatches("", host) - true, per that method's own documented empty-
    // domain short-circuit - with the cookie's domain left as "" (non-null). HostOnlyCookieStore.add() only
    // recognizes a NULL domain as host-only, so a non-null "" was silently delegated to the plain
    // InMemoryCookieStore, reopening round 32 finding 1's exact HttpCookie.equals() cross-host collision
    // (equalsIgnoreCase("", "") is true) for this one shape
    java.net.HttpCookie cookie = new java.net.HttpCookie("sid", "SECRET");
    cookie.setDomain("");
    cookie.setPath("/");
    java.net.URI uri = new java.net.URI("http://www.example.com/");
    assertTrue(StreamUtils.shouldAcceptCookie(uri, cookie));
    assertNull(cookie.getDomain());

    HostOnlyCookieStore store = new HostOnlyCookieStore(new java.net.CookieManager().getCookieStore());
    java.net.HttpCookie cookieA = new java.net.HttpCookie("sid", "FROM_DATA");
    cookieA.setDomain("");
    cookieA.setPath("/");
    java.net.URI dataUri = new java.net.URI("http://data.example.com/");
    assertTrue(StreamUtils.shouldAcceptCookie(dataUri, cookieA));
    store.add(dataUri, cookieA);

    java.net.HttpCookie cookieB = new java.net.HttpCookie("sid", "FROM_AUTH");
    cookieB.setDomain("");
    cookieB.setPath("/");
    java.net.URI authUri = new java.net.URI("http://auth.example.com/");
    assertTrue(StreamUtils.shouldAcceptCookie(authUri, cookieB));
    store.add(authUri, cookieB);

    java.net.HttpCookie clearB = new java.net.HttpCookie("sid", "");
    clearB.setDomain("");
    clearB.setPath("/");
    clearB.setMaxAge(0);
    assertTrue(StreamUtils.shouldAcceptCookie(authUri, clearB));
    store.add(authUri, clearB);

    assertTrue(hasNamedCookie(store.get(dataUri), "sid"),
        "host A's empty-Domain cookie must survive host B's own logout, not collide via equals()");
  }

  @Test
  void testShouldAcceptCookieAcceptsCookieWithNoDomainSetAtAll() throws java.net.URISyntaxException {
    // round 34 finding 7: isEffectivelyHostOnly()'s own null/host guard (all that remains of its early
    // return after round 34's dead-code cleanup removed the now-unreachable domain.isEmpty() term) had no
    // direct test - every existing shouldAcceptCookie() test either goes through a real CookieManager.put()
    // (which always auto-fills a null domain to the host before the policy ever runs) or explicitly calls
    // setDomain() itself. A cookie built without ever calling setDomain() at all (getDomain() == null by
    // default) falls through isEffectivelyHostOnly() (its own null guard) to domainMatches(null, host) -
    // true, per that method's own documented null/empty short-circuit - ending up accepted with domain left
    // null, which HostOnlyCookieStore already treats as host-only regardless of which path got it there
    java.net.HttpCookie cookie = new java.net.HttpCookie("sid", "SECRET");
    cookie.setPath("/");
    assertTrue(StreamUtils.shouldAcceptCookie(new java.net.URI("http://www.example.com/"), cookie));
    assertNull(cookie.getDomain());
  }

  @Test
  void testShouldAcceptCookieClosesPublicSuffixAndDotlessLocalHostOnlyLeaks() throws java.net.URISyntaxException {
    // the same fix as testShouldAcceptCookieStoresExactMatchAsTrulyHostOnly above also retroactively closes
    // two previously-documented-not-fixed limitations that share the identical root cause: round 29 finding
    // 2 (a host-only cookie for a request host that's itself a multi-label public suffix, e.g. a real
    // path-style S3 URL) and round 30 finding 3 (a host-only cookie for a dotless request host, auto-filled
    // by the JDK to "<host>.local" - a phantom domain that RFC 6265 domain-matching then legitimately treats
    // as real, replaying it to any attacker-controlled "*.<host>.local" subdomain)
    java.net.HttpCookie s3Cookie = new java.net.HttpCookie("sid", "SECRET");
    s3Cookie.setDomain("s3.amazonaws.com"); // as CookieManager.put() would auto-fill it
    s3Cookie.setPath("/");
    java.net.URI s3Uri = new java.net.URI("http://s3.amazonaws.com/");
    assertTrue(StreamUtils.shouldAcceptCookie(s3Uri, s3Cookie));
    assertNull(s3Cookie.getDomain());
    java.net.CookieStore s3Store = new java.net.CookieManager().getCookieStore();
    s3Store.add(s3Uri, s3Cookie);
    assertTrue(hasNamedCookie(s3Store.get(new java.net.URI("http://s3.amazonaws.com/")), "sid"));
    assertFalse(hasNamedCookie(s3Store.get(new java.net.URI("http://bucket.s3.amazonaws.com/")), "sid"),
        "round 29 finding 2's public-suffix-host leak must be closed");

    java.net.HttpCookie localCookie = new java.net.HttpCookie("sid", "SECRET");
    localCookie.setDomain("localhost.local"); // as CookieManager.put() would auto-fill it for host "localhost"
    localCookie.setPath("/");
    java.net.URI localUri = new java.net.URI("http://localhost/");
    assertTrue(StreamUtils.shouldAcceptCookie(localUri, localCookie));
    assertNull(localCookie.getDomain());
    java.net.CookieStore localStore = new java.net.CookieManager().getCookieStore();
    localStore.add(localUri, localCookie);
    assertTrue(hasNamedCookie(localStore.get(new java.net.URI("http://localhost/")), "sid"));
    assertFalse(hasNamedCookie(localStore.get(new java.net.URI("http://evil.localhost.local/")), "sid"),
        "round 30 finding 3's dotless-\".local\"-host leak must be closed");
  }

  @Test
  void testShouldAcceptCookieRecognizesHostOnlyAcrossDotNormalizationForms() throws java.net.URISyntaxException {
    // round 32 finding 2: isEffectivelyHostOnly() does its OWN independent leading-dot/trailing-dot
    // canonicalization (mirroring, but not delegating to, domainMatches()'s own equivalent canonicalization),
    // and every existing shouldAcceptCookie()/isEffectivelyHostOnly() test happened to use a domain/host pair
    // with neither a leading nor a trailing dot - so none of these canonicalization steps were actually
    // pinned. Mutation-verified: dropping either the leading-dot strip on domain, or the trailing-dot strip on
    // either side, left the whole suite green even though each one silently converts an exact match back into
    // the SUBDOMAIN-leaking domain-cookie shape round 31 finding 2 exists to prevent (or, for two of the
    // trailing-dot cases, wrongly rejects the cookie outright, reopening round 28 finding 1/round 29 finding 2
    // one layer up).
    //
    // an explicit LEADING-DOT self-referential Domain=.<host> is exactly as host-only as a bare Domain=<host>
    java.net.HttpCookie leadingDot = new java.net.HttpCookie("sid", "SECRET");
    leadingDot.setDomain(".www.example.com");
    leadingDot.setPath("/");
    java.net.URI uri = new java.net.URI("http://www.example.com/");
    assertTrue(StreamUtils.shouldAcceptCookie(uri, leadingDot));
    assertNull(leadingDot.getDomain());
    java.net.CookieStore leadingDotStore = new java.net.CookieManager().getCookieStore();
    leadingDotStore.add(uri, leadingDot);
    assertTrue(hasNamedCookie(leadingDotStore.get(uri), "sid"));
    assertFalse(hasNamedCookie(leadingDotStore.get(new java.net.URI("http://evil.www.example.com/")), "sid"));

    // a TRAILING DOT on the domain, the host, or both, is still an exact match (RFC 1035's cosmetic
    // "fully-qualified" form) and must be stored host-only the same way, not merely accepted by
    // domainMatches() as a non-leaking domain cookie one layer down
    java.net.URI dotHostUri = new java.net.URI("http://www.example.test./");
    java.net.HttpCookie domainDotted = new java.net.HttpCookie("sid", "SECRET");
    domainDotted.setDomain("www.example.test.");
    domainDotted.setPath("/");
    assertTrue(StreamUtils.shouldAcceptCookie(new java.net.URI("http://www.example.test/"), domainDotted));
    assertNull(domainDotted.getDomain());

    java.net.HttpCookie hostDotted = new java.net.HttpCookie("sid", "SECRET");
    hostDotted.setDomain("www.example.test");
    hostDotted.setPath("/");
    assertTrue(StreamUtils.shouldAcceptCookie(dotHostUri, hostDotted));
    assertNull(hostDotted.getDomain());

    java.net.HttpCookie bothDotted = new java.net.HttpCookie("sid", "SECRET");
    bothDotted.setDomain("www.example.test.");
    bothDotted.setPath("/");
    assertTrue(StreamUtils.shouldAcceptCookie(dotHostUri, bothDotted));
    assertNull(bothDotted.getDomain());

    // a DOTTED host explicitly sending Domain=<host>.local (the ".local" auto-fill convention only ever
    // applies to a DOTLESS host, so this is a genuinely foreign domain here) must be rejected outright, not
    // treated as host-only - matching domainMatches()'s own rejection of this shape one layer down
    // (testDomainMatchesAcceptsPlainDotlessHostLocalAutoFillForm)
    java.net.HttpCookie foreignDotLocal = new java.net.HttpCookie("sid", "SECRET");
    foreignDotLocal.setDomain("www.example.com.local");
    assertFalse(StreamUtils.shouldAcceptCookie(new java.net.URI("http://www.example.com/"), foreignDotLocal));
  }

  @Test
  void testDomainMatchesAcceptsParentDomainWithoutLeadingDot() {
    // java.net.HttpCookie.domainMatches() incorrectly REJECTS this - the RFC 6265 form ("Domain=example.com",
    // no leading dot) essentially every modern server sends for a session cookie scoped to a parent domain
    assertTrue(StreamUtils.domainMatches("example.com", "www.example.com"));
    // the leading-dot form (legal, less common today) must keep working too
    assertTrue(StreamUtils.domainMatches(".example.com", "www.example.com"));
    // exact host match, and no explicit Domain attribute at all (both host-only cookie shapes)
    assertTrue(StreamUtils.domainMatches("example.com", "example.com"));
    assertTrue(StreamUtils.domainMatches(null, "example.com"));
    assertTrue(StreamUtils.domainMatches("", "example.com"));
  }

  @Test
  void testDomainMatchesAcceptsAncestorDomainMultipleLabelsDeep() {
    // round 20's fallback (java.net.HttpCookie.domainMatches() with a leading dot prepended) only rescues a
    // host exactly ONE label below the cookie domain, since that JDK method itself requires the host's extra
    // prefix to contain no dot - a real host two or more labels below (e.g. "a.b.example.com" for
    // "Domain=example.com", or a real download target like "hgdownload.soe.ucsc.edu" for "Domain=ucsc.edu")
    // was still incorrectly rejected
    assertTrue(StreamUtils.domainMatches("example.com", "a.b.example.com"));
    // the leading-dot form must be rescued the same way
    assertTrue(StreamUtils.domainMatches(".example.com", "a.b.example.com"));
    assertTrue(StreamUtils.domainMatches("ucsc.edu", "hgdownload.soe.ucsc.edu"));
  }

  @Test
  void testDomainMatchesRejectsUnrelatedOrOverBroadDomains() {
    // must not become LAXER than the pre-existing (pre-round-19) CookiePolicy.ACCEPT_ORIGINAL_SERVER
    // default while fixing the leading-dot gap above - a genuinely foreign domain, a same-length-suffix-
    // but-wrong-boundary domain, and a bare single-label domain (a "supercookie" spanning every site under
    // that suffix) must all still be rejected
    assertFalse(StreamUtils.domainMatches("evil.example.org", "www.example.com"));
    assertFalse(StreamUtils.domainMatches("ample.com", "www.example.com"));
    assertFalse(StreamUtils.domainMatches("com", "www.example.com"));
    assertFalse(StreamUtils.domainMatches("example.com", null));
  }

  @Test
  void testDomainMatchesRejectsNonLabelBoundarySuffix() {
    // java.net.HttpCookie.domainMatches() (previously delegated to first - see round 29 finding 3) has its
    // own long-standing bug: its "one extra label, no embedded dot" check never actually requires that extra
    // label to be separated from the cookie domain by a "." boundary character - "notexample.com"/
    // "evilexample.com" both satisfy "host minus domain has no dot in it" just as well as a real subdomain
    // like "www.example.com" does. Verified end-to-end through the real copyUrlToFile() path: a redirect to
    // an attacker-controlled host like "evilexample.com" could set a Domain=example.com cookie that then got
    // sent to the real "example.com" on a later hop - a genuine cross-host cookie-injection vector, and a
    // real regression vs main (Apache HttpClient's DefaultCookieSpec rejects this exact response outright).
    // Not exploitable by any of this library's known callers today (all fixed, non-attacker-controlled
    // targets), but a real vector for any caller that ever does follow a less-trusted redirect chain. Fixed
    // by dropping the general delegation to HttpCookie.domainMatches() entirely (round 29) - the RFC 6265
    // section 5.1.3 label-boundary fallback a few lines below already enforces the "." boundary correctly for
    // every case that delegate was doing double duty for
    assertFalse(StreamUtils.domainMatches("example.com", "notexample.com"));
    assertFalse(StreamUtils.domainMatches("example.com", "evilexample.com"));
    // a genuine label-boundary-respecting subdomain must still match
    assertTrue(StreamUtils.domainMatches("example.com", "www.example.com"));
  }

  @Test
  void testDomainMatchesAcceptsPlainDotlessHostLocalAutoFillForm() {
    // java.net.CookieManager.put() appends ".local" to ANY dotless host (not just an IP literal - see
    // testDomainMatchesAcceptsIpv6LiteralHostLocalAutoFillForm below for that narrower case) when
    // auto-filling an absent Domain attribute - e.g. a plain host-only cookie for host "localhost" is
    // actually stored with domain "localhost.local", not "localhost" verbatim. This was previously handled
    // implicitly by delegating to HttpCookie.domainMatches(), which has this exact JDK-specific special case
    // built in (see this class's own historical note about "the plain localhost-to-localhost case" - the
    // ORIGINAL reason the delegate was kept in the first place, round 19/20) - removing that general
    // delegation (round 29 finding 3) needed its own explicit replacement for this one case, generalizing the
    // round-28 IPv6-literal-specific version of this same check to any dotless host
    assertTrue(StreamUtils.domainMatches("localhost.local", "localhost"));
    // an unrelated dotless host must still be rejected - the ".local" exemption is host-specific
    assertFalse(StreamUtils.domainMatches("localhost.local", "otherhost"));
    // the check requires the HOST to be dotless - a server explicitly sending a ".local"-suffixed domain for
    // a host that already HAS a dot (so CookieManager.put() would never have auto-filled this form itself)
    // must still be rejected the same way any other unrelated multi-label domain would be (round 30, test-
    // coverage gap, mutation-verified: dropping the dotless requirement here left the whole suite green)
    assertFalse(StreamUtils.domainMatches("www.example.com.local", "www.example.com"));
  }

  @Test
  void testDomainMatchesRejectsMultiLabelPublicSuffix() {
    // the "com" case above only catches a BARE, single-label supercookie domain - it does nothing for a
    // MULTI-label public suffix like "co.uk"/"com.au"/"github.io" (round 25): a cookie scoped to one
    // subdomain of a public suffix (e.g. Domain=co.uk from "a.co.uk") would otherwise be replayed to any
    // OTHER, completely unrelated site under the same suffix (e.g. "b.co.uk") - the classic "supercookie"
    // attack RFC 6265 section 5.3 point 12 warns against. Not a regression this branch introduced (main's
    // Apache HttpClient guarded against it via PublicSuffixMatcherLoader/PublicSuffixDomainFilter), but a
    // real gap unclosed since round 19 added cookie support at all
    assertFalse(StreamUtils.domainMatches("co.uk", "www.example.co.uk"));
    assertFalse(StreamUtils.domainMatches("com.au", "foo.bar.com.au"));
    assertFalse(StreamUtils.domainMatches("github.io", "victim.github.io"));
    // a real, non-public-suffix multi-label domain must still match - this check must not become laxer than
    // the pre-existing (round 20/21) intended behavior
    assertTrue(StreamUtils.domainMatches("example.com", "www.example.com"));
    assertTrue(StreamUtils.domainMatches("ucsc.edu", "hgdownload.soe.ucsc.edu"));
  }

  @Test
  void testDomainMatchesRejectsIpLiteralHost() {
    // RFC 6265 section 5.1.3's domain-match algorithm only applies to domain names, never IP addresses - the
    // round-21 label-boundary fallback (added to fix the multiple-labels-deep gap) has no such guard, so an
    // IP-literal host wrongly matched a shorter numeric "domain" the same way a real DNS name would (e.g.
    // "0.1" against "127.0.0.1", or "127.0.0.1" itself against "evil.127.0.0.1" - the latter would let a
    // cookie scoped by an attacker-controlled subdomain-shaped label match a literal IP host it was never
    // meant to). Not a regression vs main (Apache's DefaultCookieSpec explicitly rejects a non-domain-name
    // Domain attribute for a literal IP host). Since round 29 finding 3, these two inputs are actually caught
    // by the IP-literal-host guard and the IP-shaped-domain guard respectively (both above the label-boundary
    // fallback this test was originally written to pin) rather than reaching the fallback at all - still
    // correctly rejected either way
    assertFalse(StreamUtils.domainMatches("0.1", "127.0.0.1"));
    assertFalse(StreamUtils.domainMatches("127.0.0.1", "evil.127.0.0.1"));
  }

  @Test
  void testDomainMatchesRejectsLeadingDotPublicSuffixAndIpLiteral() {
    // round 25's public-suffix guard and round 22's IP-literal guard both only ran AFTER delegating to
    // java.net.HttpCookie.domainMatches() first - but that JDK method is itself permissive for exactly these
    // shapes when the cookie Domain carries a LEADING DOT (verified directly against the JDK method): a
    // leading-dot multi-label public suffix or a leading-dot IP-literal "domain" both match a host one label
    // below, defeating both guards entirely whenever the cookie happens to be written with a leading dot
    // (round 26). This is not a new, different bug from round 25/round 22's - it's the exact same supercookie
    // and IP-address-treated-as-suffix hazards those two guards exist to close, just reached through a form
    // (leading dot) that bypasses them by running before they ever get a chance to fire
    assertFalse(StreamUtils.domainMatches(".co.uk", "a.co.uk"));
    assertFalse(StreamUtils.domainMatches(".github.io", "victim.github.io"));
    assertFalse(StreamUtils.domainMatches(".0.0.1", "127.0.0.1"));
    assertFalse(StreamUtils.domainMatches(".127.0.0.1", "evil.127.0.0.1"));
    // an EXACT IP-literal host match must still be allowed - RFC 6265 domain-matching never applies to IP
    // hosts otherwise (in either direction), so this is the one case where "domain equals host" genuinely
    // means "legitimate exact match", not "suffix match" - main's own PublicSuffixDomainFilter has no IP
    // handling of its own at all (it's a DNS-name-only public suffix list), so this isn't a main-parity claim,
    // just RFC correctness
    assertTrue(StreamUtils.domainMatches(".127.0.0.1", "127.0.0.1"));
    assertTrue(StreamUtils.domainMatches("127.0.0.1", "127.0.0.1"));
    // an exact match against a MULTI-LABEL PUBLIC SUFFIX host, by contrast, must still be REJECTED even
    // though domain equals host exactly here too (round 27 finding 1 - round 26 wrongly exempted this case,
    // reasoning it was "a legitimate exact match, not a suffix match" and that "main's own
    // PublicSuffixDomainFilter permits it too"). Both of those were wrong: (a) cookie RETRIEVAL for a
    // version-0 cookie (forced by sf_cookiePolicy above) goes through the JDK's netscapeDomainMatches() - a
    // PLAIN SUFFIX match, not an exact-string check - so accepting Domain=co.uk from host co.uk still lets
    // that cookie be replayed to any unrelated sibling under the same suffix (e.g. b.co.uk) on retrieval,
    // verified end-to-end through the real copyUrlToFile() path with jdk.net.hosts.file-faked DNS; (b) Apache
    // HttpClient 4.5.14's real PublicSuffixDomainFilter source shows its own equalsIgnoreCase(origin.getHost())
    // exemption exists ONLY for a DOTLESS (single-label) domain - for a DOTTED (multi-label) public suffix it
    // rejects unconditionally, exact match or not, verified directly against DefaultCookieSpecProvider(
    // PublicSuffixMatcherLoader.getDefault()), the exact stack HttpClientBuilder.create().build() installs
    assertFalse(StreamUtils.domainMatches(".co.uk", "co.uk"));
    assertFalse(StreamUtils.domainMatches("co.uk", "co.uk"));
  }

  @Test
  void testDomainMatchesRejectsTrailingDotDomain() {
    // the round-21 label-boundary fallback's "domain.endsWith(\".\")" guard was unpinned - a cookie Domain
    // attribute with a trailing dot (malformed - RFC 6265 domain names don't have one) must still be rejected
    // rather than accidentally treated as a valid suffix to match against
    assertFalse(StreamUtils.domainMatches("example.com.", "www.example.com."));
    // a DOUBLE trailing dot on the HOST - only one dot is stripped into canonHost, so the remaining one
    // still needs this guard specifically (round 30, test-coverage gap, mutation-verified: the single-dot
    // case above is already rejected by the label-boundary fallback's own length mismatch regardless of this
    // guard, so it alone doesn't pin this line - removing it left the whole suite green until this assertion
    // was added)
    assertFalse(StreamUtils.domainMatches("example.com.", "www.example.com.."));
  }

  @Test
  void testDomainMatchesAcceptsExactTrailingDotHost() {
    // java.net.CookieManager.put() auto-fills an absent Domain attribute VERBATIM from uri.getHost() - so a
    // plain host-only Set-Cookie (no explicit Domain at all) for a trailing-dot-form host (e.g.
    // "www.example.test.", the DNS "fully-qualified" form, RFC 1035) is stored WITH that same trailing dot.
    // Round 27's unconditional trailing-dot-domain rejection only ever tested a trailing-dot DOMAIN against a
    // host it doesn't exactly match (testDomainMatchesRejectsTrailingDotDomain above) - it never tested an
    // EXACT match where BOTH domain and host share the same trailing dot, which it wrongly rejected too,
    // reintroducing the exact bare-403-on-a-session-gated-redirect failure the whole cookie feature (round
    // 19) exists to fix (round 28 finding 1). Verified end-to-end through the real copyUrlToFile() path with
    // a real HttpServer bound to a trailing-dot host name
    assertTrue(StreamUtils.domainMatches("www.example.test.", "www.example.test."));
    // the trailing dot is cosmetic (DNS treats a name and its FQDN form as identical) - a mismatched
    // trailing-dot STATE between domain and host (one has it, the other doesn't) for an otherwise-exact
    // match must be accepted too, same reasoning
    assertTrue(StreamUtils.domainMatches("www.example.test.", "www.example.test"));
    assertTrue(StreamUtils.domainMatches("www.example.test", "www.example.test."));
    // a trailing-dot MULTI-LABEL PUBLIC SUFFIX must still be rejected, exact match or not - the trailing-dot
    // exemption above only widens what counts as an EXACT match, not what a public-suffix domain is allowed
    // to match; round 27 finding 1's supercookie fix must not be reopened just by adding a trailing dot
    assertFalse(StreamUtils.domainMatches("co.uk.", "co.uk."));
    assertFalse(StreamUtils.domainMatches(".co.uk.", "a.co.uk."));
  }

  @Test
  void testDomainMatchesAcceptsParentDomainAgainstTrailingDotHost() {
    // round 28's trailing-dot canonicalization only ever applied to the EXACT-match test (bareDomain vs
    // bareHost) - the delegate call and the label-boundary fallback further down still compared against the
    // RAW, un-canonicalized host, so a genuine PARENT-domain match (no explicit leading dot, RFC 6265's most
    // common real-world form - see testDomainMatchesAcceptsParentDomainWithoutLeadingDot above) failed
    // whenever the REQUEST HOST itself happened to be in trailing-dot FQDN form, even though RFC 6265 section
    // 5.1.2 requires the request-host to be canonicalized (trailing dot stripped) before domain-matching in
    // the first place (round 29 finding 1). This is a real gap, not just the exact-match shape round 28
    // already covered - this library's real https:// download target (hgdownload.soe.ucsc.edu, see round 21)
    // would hit this if ever accessed via its own fully-qualified form
    assertTrue(StreamUtils.domainMatches("example.test", "www.example.test."));
    assertTrue(StreamUtils.domainMatches("ucsc.edu", "hgdownload.soe.ucsc.edu."));
  }

  @Test
  void testDomainMatchesAcceptsIpv6LiteralHostLocalAutoFillForm() {
    // java.net.CookieManager.put() appends ".local" to any DOTLESS host (not just a plain hostname) when
    // auto-filling an absent Domain attribute - a bracketed IPv6 literal like "[::1]" contains no dot, so a
    // plain host-only cookie for an IPv6-literal host is stored as "[::1].local", not "[::1]" verbatim.
    // Round 26's IP-literal exact-match exemption only ever checked the bare form, missing this JDK-specific
    // convention entirely (round 28 finding 2) - reintroducing the same bare-403 failure as finding 1 above,
    // this time for any https:// download target that happens to be an IPv6 literal
    assertTrue(StreamUtils.domainMatches("[::1].local", "[::1]"));
    // an unrelated IPv6 host must still be rejected - the ".local" exemption is host-specific, not a blanket
    // pass for anything ending in ".local"
    assertFalse(StreamUtils.domainMatches("[::1].local", "[::2]"));
  }

  @Test
  void testDomainMatchesRejectsLeadingDotAfterStrip() {
    // the round-21 fallback strips at most ONE leading dot before checking domain.indexOf('.') <= 0 (not
    // < 0) - a cookie Domain starting with TWO dots still has a leading dot left over after that single
    // strip, and REQUIRES the <= 0 form specifically: verified that with the weaker < 0 form, this exact
    // input incorrectly returns true (the leftover leading dot's own position, 0, satisfies "< 0" being
    // false, letting the boundary-match logic below run against a domain that's still malformed). Neither
    // the single-label/IP-shaped/public-suffix guard nor the exact-match check above intercepts this
    // multi-label-deep, non-exact shape, so this genuinely reaches and exercises the fallback
    assertFalse(StreamUtils.domainMatches("..example.com", "a.b..example.com"));
  }

  @Test
  void testDomainMatchesHandlesEqualLengthHostAndDomainWithoutCrashing() {
    // the round-21 fallback's "host.length() > domain.length()" guard was unpinned - no existing test passed
    // an equal-length, non-matching host/domain pair. This matters beyond mere coverage: a mutated ">=" here
    // (or any accidental removal of the length guard) would let host.charAt(host.length() - domain.length()
    // - 1) evaluate charAt(-1) for an equal-length pair, throwing an unchecked
    // StringIndexOutOfBoundsException past this method's own domain-matching contract, not just returning
    // the wrong boolean
    assertFalse(StreamUtils.domainMatches("aaa.example.com", "bbb.example.com"));
  }

  @Test
  void testDomainMatchesFallbackIsCaseInsensitive() {
    // every existing domainMatches() assertion uses all-lowercase operands, so the round-21 fallback's
    // regionMatches(true, ...) case-insensitivity flag itself was unpinned. Set-Cookie domain case isn't
    // normalized by servers, and this is a real shape: this library's actual https:// download target
    // (hgdownload.soe.ucsc.edu) is 4 labels below "ucsc.edu" (see round 21) - an upper/mixed-case Domain
    // attribute for the same host must still match
    assertTrue(StreamUtils.domainMatches("UCSC.EDU", "hgdownload.soe.ucsc.edu"));
  }

  @Test
  void testCopyUrlToFileWithUnsupportedProtocolNamesTheUrl(@TempDir Path tempDir) {
    // the shared URI/URL-parsing prologue (before either branch even exists) must follow the same
    // "every failure path names the url" convention both branches below it already do
    String url = "gopher://example.com/x";
    Path file = tempDir.resolve("out.txt");
    IOException ex = assertThrows(IOException.class, () -> StreamUtils.copyUrlToFile(url, file));
    assertTrue(ex.getMessage() != null && ex.getMessage().contains(url), String.valueOf(ex.getMessage()));
  }

  @Test
  void testCopyUrlToFileWithRelativeUrlThrowsIOException(@TempDir Path tempDir) {
    // round 30's migration off the deprecated URL(String) constructor introduced a regression: URI.toURL()
    // throws an UNCHECKED IllegalArgumentException("URI is not absolute") for any relative URI (no scheme at
    // all) - e.g. a config/spreadsheet value missing its "http://" prefix, or a bare empty string - where the
    // old URL(String)-first order threw the CHECKED MalformedURLException("no protocol: ...") this method's
    // own catch clause already expected. This lets an undeclared RuntimeException escape past this method's
    // documented "throws IOException" for an ordinary, easily-reachable bad input via the PUBLIC
    // copyUrlToFile(String, Path) API - the same "unchecked exception must not escape this method's declared
    // throws IOException" hazard rounds 16/17/20/21/22/23 each already fixed elsewhere in this same method
    // (round 31 finding 1)
    Path file = tempDir.resolve("out.txt");
    for (String url : new String[]{"", "foo.txt", "example.com/foo", "www.example.com/data.txt"}) {
      IOException ex = assertThrows(IOException.class, () -> StreamUtils.copyUrlToFile(url, file),
          "expected IOException for url \"" + url + "\"");
      assertTrue(ex.getMessage() != null && ex.getMessage().contains(url), String.valueOf(ex.getMessage()));
    }
  }

  @Test
  void testCopyUrlToFileWithNonHttpSchemeNamesTheUrlOnFailure(@TempDir Path tempDir) {
    // the generic (non-http/https) branch - used for ftp/file/etc. - must name the url on failure just like
    // the http/https branch above it does; a bare FileNotFoundException naming only the local path (not the
    // url that produced it) breaks that convention
    String url = "file://" + tempDir.resolve("definitely/does/not/exist/xyz.txt");
    Path file = tempDir.resolve("out.txt");
    IOException ex = assertThrows(IOException.class, () -> StreamUtils.copyUrlToFile(url, file));
    assertTrue(ex.getMessage() != null && ex.getMessage().contains(url), String.valueOf(ex.getMessage()));
  }

  @Test
  void testCopyUrlToFileWithNonHttpSchemeAndOutOfRangePortThrowsIOException(@TempDir Path tempDir) {
    // the non-HTTP branch's try/catch only catches IOException - an out-of-range port (e.g. 99999) makes
    // Socket's constructor throw an unchecked IllegalArgumentException instead, which escaped this method's
    // declared throws IOException entirely, unwrapped and not naming the url, unlike every other failure
    // path in this method (including the http/https branch, which already guards against undeclared
    // RuntimeExceptions the same way - see mapDownloadExecutionCause())
    String url = "ftp://example.com:99999/x";
    Path file = tempDir.resolve("out.txt");
    IOException ex = assertThrows(IOException.class, () -> StreamUtils.copyUrlToFile(url, file));
    assertTrue(ex.getMessage() != null && ex.getMessage().contains(url), String.valueOf(ex.getMessage()));
  }

  @Test
  void testCopyUrlToFileWithNonHttpSchemeAndExcessiveIdleTimeoutThrowsIOException(@TempDir Path tempDir) {
    // same widened-catch fix as the out-of-range-port test above, closing a second escape route through the
    // same try block: Math.toIntExact(idleTimeout.toMillis()) throws an unchecked ArithmeticException for an
    // idleTimeout longer than ~24.8 days (only reachable via the test-only idleTimeout overload, not the
    // public 30s-default API - see known non-issue #45 in review.log, now closed as a side effect of this fix)
    String url = "ftp://example.com/x";
    Path file = tempDir.resolve("out.txt");
    IOException ex = assertThrows(IOException.class,
        () -> StreamUtils.copyUrlToFile(url, file, Duration.ofDays(30)));
    assertTrue(ex.getMessage() != null && ex.getMessage().contains(url), String.valueOf(ex.getMessage()));
  }

  @Test
  void testCopyUrlToFileWithNonPositiveIdleTimeoutThrowsIOException(@TempDir Path tempDir) {
    // buildHttpClient()'s connectTimeout(idleTimeout) rejects a non-positive Duration with an unchecked
    // IllegalArgumentException - only reachable via this test-only idleTimeout overload (the public API
    // always passes a fixed 30-second default), but must still surface as the documented IOException, not
    // an undeclared RuntimeException, matching every other construction-time failure this method guards
    String url = "http://example.com/x";
    Path file = tempDir.resolve("out.txt");
    IOException ex = assertThrows(IOException.class, () -> StreamUtils.copyUrlToFile(url, file, Duration.ZERO));
    assertTrue(ex.getMessage() != null && ex.getMessage().contains(url), String.valueOf(ex.getMessage()));
  }

  @Test
  void testCopyUrlToFileWithSubMillisecondIdleTimeoutThrowsIOException(@TempDir Path tempDir) throws IOException {
    // a positive-but-sub-millisecond idleTimeout (e.g. 500 microseconds) is still a positive Duration, so
    // buildHttpClient()'s connectTimeout(idleTimeout) above doesn't reject it - but idleTimeout.toMillis()
    // truncates it to 0, and ScheduledExecutorService.scheduleWithFixedDelay() requires a strictly positive
    // delay, throwing an unchecked IllegalArgumentException AFTER sendAsync() has already been dispatched.
    // Only reachable via the test-only idleTimeout overload, but must not escape this method's declared
    // throws IOException, matching every other construction-time failure this method guards
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/", exchange -> {
      byte[] body = "hello".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.start();
    try {
      String url = "http://localhost:" + server.getAddress().getPort() + "/";
      Path file = tempDir.resolve("out.txt");
      IOException ex = assertThrows(IOException.class,
          () -> StreamUtils.copyUrlToFile(url, file, Duration.ofNanos(500_000)));
      assertTrue(ex.getMessage() != null && ex.getMessage().contains(url), String.valueOf(ex.getMessage()));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void testCopyUrlToFileWithExcessiveIdleTimeoutCancelsInFlightRequestPromptly(@TempDir Path tempDir)
      throws IOException {
    // testCopyUrlToFileWithSubMillisecondIdleTimeoutThrowsIOException/
    // testCopyUrlToFileWithIdleTimeoutBeyondNanosRangeThrowsIOException above both use a server that responds
    // IMMEDIATELY, so neither actually exercises (or pins) the watchdog-setup catch block's explicit
    // future.cancel(true)/watchdog.shutdown() cleanup - deleting that cleanup entirely leaves those two tests
    // (and the full suite) green regardless. Whether the cleanup is actually load-bearing turns out to depend
    // on WHICH of the two throwing inputs is used: for a sub-millisecond idleTimeout, buildHttpClient()'s own
    // connectTimeout(idleTimeout) is ALSO sub-millisecond, so the connection attempt fails on its own almost
    // immediately regardless of this cleanup (verified: still resolves in well under a second even with the
    // cleanup removed) - but for an idleTimeout of hundreds of years (the OTHER throwing input,
    // idleTimeout.toNanos() overflowing), connectTimeout() is given that same effectively-infinite duration,
    // so nothing else bounds a stalled connection attempt. Verified directly: against a server that accepts
    // the TCP connection but never responds, removing the cleanup left this exact scenario hanging past 8
    // seconds (client.close(), invoked implicitly by try(client) as the exception propagates, blocks until
    // the in-flight exchange completes - forever, here). assertTimeoutPreemptively (not a bare call) is
    // required for the same reason as the non-HTTP sibling test above: the un-fixed behavior hangs
    // indefinitely, not just slowly
    try (ServerSocket server = new ServerSocket(0)) {
      Thread serverThread = new Thread(() -> {
        try {
          server.accept();
          // deliberately never write a response and never close - simulates a server that accepted the
          // connection but is never going to answer
          Thread.sleep(60_000);
        } catch (IOException | InterruptedException ex) {
          // test failure surfaces via the client-side assertion below, not here
        }
      });
      serverThread.setDaemon(true);
      serverThread.start();
      String url = "http://localhost:" + server.getLocalPort() + "/";
      Path file = tempDir.resolve("out.txt");
      assertTimeoutPreemptively(Duration.ofSeconds(8), () ->
          assertThrows(IOException.class,
              () -> StreamUtils.copyUrlToFile(url, file, Duration.ofDays(365L * 300))));
    }
  }

  @Test
  void testCopyUrlToFileWithIdleTimeoutBeyondNanosRangeThrowsIOException(@TempDir Path tempDir) throws IOException {
    // idleTimeout.toNanos() throws an unchecked ArithmeticException (long overflow) for an idleTimeout beyond
    // ~292 years - only reachable via the test-only idleTimeout overload, but must not escape this method's
    // declared throws IOException, matching every other construction-time failure this method guards
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/", exchange -> {
      byte[] body = "hello".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.start();
    try {
      String url = "http://localhost:" + server.getAddress().getPort() + "/";
      Path file = tempDir.resolve("out.txt");
      IOException ex = assertThrows(IOException.class,
          () -> StreamUtils.copyUrlToFile(url, file, Duration.ofDays(365L * 300)));
      assertTrue(ex.getMessage() != null && ex.getMessage().contains(url), String.valueOf(ex.getMessage()));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void testCopyUrlToFileWithHostlessUrlThrowsIOException(@TempDir Path tempDir) {
    // a hostless "http:///path" URI passes both new URI(url) and uri.toURL() (neither validates semantics,
    // only syntax), but HttpRequest.newBuilder(uri) rejects it with an unchecked IllegalArgumentException -
    // that must still surface as the documented IOException, not an undeclared RuntimeException, matching
    // how a URISyntaxException a few lines above is already normalized to IOException
    String url = "http:///some/path";
    Path file = tempDir.resolve("out.txt");
    IOException ex = assertThrows(IOException.class, () -> StreamUtils.copyUrlToFile(url, file));
    assertTrue(ex.getMessage().contains(url), ex.getMessage());
  }

  @Test
  void testCopyUrlToFileWithMalformedRedirectLocationThrowsIOException(@TempDir Path tempDir) throws IOException {
    // testCopyUrlToFileWithHostlessUrlThrowsIOException above already covers a malformed URI in the INITIAL
    // request - but a redirect Location that's malformed the same way (e.g. hostless) is parsed by the JDK's
    // HttpClient internally, while following the redirect, deep inside the async pipeline - not by this
    // method's own HttpRequest.newBuilder(uri) call, so that existing try/catch never sees it. Verified this
    // surfaces as an unchecked NullPointerException (java.net.URI.getRawAuthority() is null) escaping past
    // the documented "throws IOException" on JDK 17, breaking any caller written as `catch (IOException e)`
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/redirect", exchange -> {
      exchange.getResponseHeaders().add("Location", "http:///some/path");
      exchange.sendResponseHeaders(302, -1);
      exchange.close();
    });
    server.start();
    try {
      String url = "http://localhost:" + server.getAddress().getPort() + "/redirect";
      Path file = tempDir.resolve("out.txt");
      assertThrows(IOException.class, () -> StreamUtils.copyUrlToFile(url, file));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void testCopyUrlToFileWithConnectionRefusedNamesTheUrl(@TempDir Path tempDir) throws IOException {
    // every other failure path in this method names the url ("Error downloading <url>", "Timed out
    // downloading <url>", "Malformed URL: <url>", "Interrupted while downloading <url>") - a connect
    // failure (the single most common real-world failure) must not be the one exception rethrown bare,
    // since java.net.http's ConnectException carries a null message on its own
    int port;
    try (ServerSocket socket = new ServerSocket(0)) {
      port = socket.getLocalPort();
    }
    // socket is now closed - nothing is listening on this port
    String url = "http://localhost:" + port + "/";
    Path file = tempDir.resolve("out.txt");
    IOException ex = assertThrows(IOException.class, () -> StreamUtils.copyUrlToFile(url, file));
    assertTrue(ex.getMessage() != null && ex.getMessage().contains(url), String.valueOf(ex.getMessage()));
  }

  @Test
  void testCopyUrlToFileTimesOutOnStalledNonHttpConnection(@TempDir Path tempDir) throws IOException {
    // the non-HTTP (e.g. ftp) branch's setConnectTimeout()/setReadTimeout() calls (added on this branch;
    // main had none at all) must actually be wired to the requested idle timeout - a server that accepts
    // the TCP connection but never sends the initial FTP banner would otherwise hang the calling thread
    // indefinitely waiting for a response that never arrives
    try (ServerSocket socket = new ServerSocket(0)) {
      String url = "ftp://localhost:" + socket.getLocalPort() + "/path";
      Path file = tempDir.resolve("out.txt");
      long start = System.currentTimeMillis();
      assertThrows(IOException.class, () -> StreamUtils.copyUrlToFile(url, file, Duration.ofSeconds(1)));
      long elapsed = System.currentTimeMillis() - start;
      assertTrue(elapsed < 5_000, "took " + elapsed + "ms - should have timed out around 1s, not hung");
    }
  }

  @Test
  void testCopyUrlToFileWithNonHttpSchemeAndSubMillisecondIdleTimeoutDoesNotHang(@TempDir Path tempDir)
      throws IOException {
    // a positive-but-sub-millisecond idleTimeout (e.g. 500 microseconds) truncates to 0 via
    // Math.toIntExact(idleTimeout.toMillis()) - URLConnection.setConnectTimeout(0)/setReadTimeout(0) are
    // documented as INFINITE (not "as fast as possible"), so this silently DISABLES both timeouts instead of
    // tightening them - the exact opposite of what a caller passing a very short idleTimeout would expect.
    // The http/https branch's equivalent watchdog-setup code (round 22 finding 2) throws for this same input
    // rather than silently behaving as unbounded; this branch, whose entire purpose is that setReadTimeout()
    // bounds stalls, must not have that guarantee silently disabled. assertTimeoutPreemptively (not a bare
    // call) is required here specifically because the un-fixed behavior hangs indefinitely, not just slowly -
    // a bare call would hang this test (and the whole suite) forever instead of failing
    try (ServerSocket socket = new ServerSocket(0)) {
      String url = "ftp://localhost:" + socket.getLocalPort() + "/path";
      Path file = tempDir.resolve("out.txt");
      assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
          assertThrows(IOException.class,
              () -> StreamUtils.copyUrlToFile(url, file, Duration.ofNanos(500_000))));
    }
  }

  @Test
  void testCopyUrlToFileRejectsConflictingTransferEncodingAndContentLength(@TempDir Path tempDir)
      throws IOException, InterruptedException {
    // RFC 7230 section 3.3.3 point 3: a message with BOTH Transfer-Encoding and Content-Length is invalid,
    // and a recipient MUST NOT use the Content-Length to determine framing - java.net.http.HttpClient's own
    // Http1Response.fixupContentLen() does the exact opposite (only consults Transfer-Encoding when
    // Content-Length is ABSENT, so Content-Length silently wins), truncating the destination file to whatever
    // Content-Length claims and reporting status 200 as if nothing were wrong - the only failure mode found in
    // this whole review series that silently corrupts the destination while reporting SUCCESS. Both headers
    // are visible via response.headers(), so this is a genuine "check and reject" fix, not a "no narrow fix
    // exists" tradeoff like known non-issues #41/#47/the round-22 dot-segment note
    try (ServerSocket server = new ServerSocket(0)) {
      String realBody = "THE-REAL-FILE-CONTENT-1234567890";
      Thread serverThread = new Thread(() -> {
        try (java.net.Socket socket = server.accept()) {
          InputStream in = socket.getInputStream();
          StringBuilder request = new StringBuilder();
          int c;
          while ((c = in.read()) != -1) {
            request.append((char) c);
            if (request.toString().endsWith("\r\n\r\n")) {
              break;
            }
          }
          String chunkedBody = Integer.toHexString(realBody.length()) + "\r\n" + realBody + "\r\n0\r\n\r\n";
          String response = "HTTP/1.1 200 OK\r\n"
              + "Transfer-Encoding: chunked\r\n"
              + "Content-Length: 5\r\n"
              + "Connection: close\r\n"
              + "\r\n" + chunkedBody;
          socket.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
        } catch (IOException ex) {
          // test failure surfaces via the client-side assertion below timing out/not throwing, not here
        }
      });
      serverThread.start();
      String url = "http://localhost:" + server.getLocalPort() + "/";
      Path file = tempDir.resolve("out.txt");
      assertThrows(IOException.class, () -> StreamUtils.copyUrlToFile(url, file));
      serverThread.join();
    }
  }

  @Test
  void testCopyUrlToFileRejectsMultipleDifferingContentLengthHeaders(@TempDir Path tempDir)
      throws IOException, InterruptedException {
    // RFC 7230 section 3.3.2: multiple Content-Length header fields with DIFFERING values (no
    // Transfer-Encoding involved at all here, unlike the sibling test above) is a second, separate framing-
    // ambiguity shape - java.net.http.HttpClient doesn't cross-check the values against each other, so one is
    // picked arbitrarily and the body is silently truncated/misread relative to the other
    try (ServerSocket server = new ServerSocket(0)) {
      String realBody = "THE-REAL-FILE-CONTENT-1234567890";
      Thread serverThread = new Thread(() -> {
        try (java.net.Socket socket = server.accept()) {
          InputStream in = socket.getInputStream();
          StringBuilder request = new StringBuilder();
          int c;
          while ((c = in.read()) != -1) {
            request.append((char) c);
            if (request.toString().endsWith("\r\n\r\n")) {
              break;
            }
          }
          String response = "HTTP/1.1 200 OK\r\n"
              + "Content-Length: 5\r\n"
              + "Content-Length: " + realBody.length() + "\r\n"
              + "Connection: close\r\n"
              + "\r\n" + realBody;
          socket.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
        } catch (IOException ex) {
          // test failure surfaces via the client-side assertion below timing out/not throwing, not here
        }
      });
      serverThread.start();
      String url = "http://localhost:" + server.getLocalPort() + "/";
      Path file = tempDir.resolve("out.txt");
      assertThrows(IOException.class, () -> StreamUtils.copyUrlToFile(url, file));
      serverThread.join();
    }
  }

  @Test
  void testCopyUrlToFileAcceptsContentLengthHeadersWithSameDecimalValueDifferentFormatting(@TempDir Path tempDir)
      throws IOException, InterruptedException {
    // RFC 7230 section 3.3.2 explicitly PERMITS multiple Content-Length header fields as long as they're all
    // the same decimal value - "32" and "032" are the same decimal value but different strings, so comparing
    // the raw header strings (rather than their parsed numeric value) would wrongly reject this legal,
    // unambiguous case the same way the sibling test above correctly rejects a genuinely differing pair
    try (ServerSocket server = new ServerSocket(0)) {
      String realBody = "THE-REAL-FILE-CONTENT-1234567890";
      Thread serverThread = new Thread(() -> {
        try (java.net.Socket socket = server.accept()) {
          InputStream in = socket.getInputStream();
          StringBuilder request = new StringBuilder();
          int c;
          while ((c = in.read()) != -1) {
            request.append((char) c);
            if (request.toString().endsWith("\r\n\r\n")) {
              break;
            }
          }
          String response = "HTTP/1.1 200 OK\r\n"
              + "Content-Length: 0" + realBody.length() + "\r\n"
              + "Content-Length: " + realBody.length() + "\r\n"
              + "Connection: close\r\n"
              + "\r\n" + realBody;
          socket.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
        } catch (IOException ex) {
          // test failure surfaces via the client-side assertion below not completing, not here
        }
      });
      serverThread.start();
      String url = "http://localhost:" + server.getLocalPort() + "/";
      Path file = tempDir.resolve("out.txt");
      StreamUtils.copyUrlToFile(url, file);
      assertEquals(realBody, Files.readString(file, StandardCharsets.US_ASCII));
      serverThread.join();
    }
  }

  @Test
  void testCopyUrlToFileRejectsMalformedSecondContentLengthHeader(@TempDir Path tempDir)
      throws IOException, InterruptedException {
    // the malformed-value branch of checkAmbiguousFraming()'s Content-Length loop had no dedicated test
    // (only reachable in theory, per its own javadoc) - confirmed reachable in practice: the JDK's own
    // Http1Response.fixupContentLen() only parses the FIRST Content-Length value for its own framing
    // decision (firstValueAsLong), so a valid first value followed by a malformed second one still lets the
    // JDK read a correct body, while this check's own loop (which walks ALL values via allValues()) reaches
    // the malformed one and must reject rather than silently ignore it
    try (ServerSocket server = new ServerSocket(0)) {
      String realBody = "THE-REAL-FILE-CONTENT-1234567890";
      Thread serverThread = new Thread(() -> {
        try (java.net.Socket socket = server.accept()) {
          InputStream in = socket.getInputStream();
          StringBuilder request = new StringBuilder();
          int c;
          while ((c = in.read()) != -1) {
            request.append((char) c);
            if (request.toString().endsWith("\r\n\r\n")) {
              break;
            }
          }
          String response = "HTTP/1.1 200 OK\r\n"
              + "Content-Length: " + realBody.length() + "\r\n"
              + "Content-Length: abc\r\n"
              + "Connection: close\r\n"
              + "\r\n" + realBody;
          socket.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
        } catch (IOException ex) {
          // test failure surfaces via the client-side assertion below not completing, not here
        }
      });
      serverThread.start();
      String url = "http://localhost:" + server.getLocalPort() + "/";
      Path file = tempDir.resolve("out.txt");
      IOException ex = assertThrows(IOException.class, () -> StreamUtils.copyUrlToFile(url, file));
      assertTrue(ex.getMessage() != null && ex.getMessage().contains("abc"), String.valueOf(ex.getMessage()));
      serverThread.join();
    }
  }

  @Test
  void testCopyUrlToFileRejectsUnsupportedTransferEncoding(@TempDir Path tempDir)
      throws IOException, InterruptedException {
    // RFC 7230 section 3.3.1: Transfer-Encoding codings are meant to be stripped by the HTTP client itself
    // while parsing the message - unlike Content-Encoding, this stack (like main's Apache HttpClient) only
    // implements that for "chunked" (dechunking), so a bare "Transfer-Encoding: gzip" (no "chunked" at all,
    // body terminated by closing the connection instead) leaves the raw gzip-compressed bytes on disk with a
    // normal 200 status - the same silent corruption-reported-as-success shape as the Content-Encoding/
    // framing checks around this one, just one layer down at the wire-framing level
    try (ServerSocket server = new ServerSocket(0)) {
      ByteArrayOutputStream gz = new ByteArrayOutputStream();
      try (GZIPOutputStream gzOut = new GZIPOutputStream(gz)) {
        gzOut.write("hello, world".getBytes(StandardCharsets.UTF_8));
      }
      byte[] body = gz.toByteArray();
      Thread serverThread = new Thread(() -> {
        try (java.net.Socket socket = server.accept()) {
          InputStream in = socket.getInputStream();
          StringBuilder request = new StringBuilder();
          int c;
          while ((c = in.read()) != -1) {
            request.append((char) c);
            if (request.toString().endsWith("\r\n\r\n")) {
              break;
            }
          }
          OutputStream out = socket.getOutputStream();
          out.write(("HTTP/1.1 200 OK\r\n"
              + "Transfer-Encoding: gzip\r\n"
              + "Connection: close\r\n"
              + "\r\n").getBytes(StandardCharsets.US_ASCII));
          out.write(body);
        } catch (IOException ex) {
          // test failure surfaces via the client-side assertion below not completing, not here
        }
      });
      serverThread.start();
      String url = "http://localhost:" + server.getLocalPort() + "/";
      Path file = tempDir.resolve("out.txt");
      IOException ex = assertThrows(IOException.class, () -> StreamUtils.copyUrlToFile(url, file));
      assertTrue(ex.getMessage() != null && ex.getMessage().contains("gzip"), String.valueOf(ex.getMessage()));
      serverThread.join();
    }
  }

  @Test
  void testCopyUrlToFileAcceptsEmptyTransferEncodingHeaderValue(@TempDir Path tempDir)
      throws IOException, InterruptedException {
    // an empty Transfer-Encoding value (e.g. "Transfer-Encoding: " with nothing after the colon) isn't a
    // legal RFC 7230 value at all (its own grammar - "1#transfer-coding" - requires at least one coding), but
    // both this stack and main's Apache HttpClient already deliver the body correctly for it (treated the
    // same as if the header were entirely absent) - so rejecting it the same way as a genuinely unrecognized
    // coding like "chunked;a=b" was stricter than necessary, and the rejection message rendered misleadingly
    // as "unsupported Transfer-Encoding []" (List.of("").toString() prints identically to an empty list -
    // indistinguishable from "no Transfer-Encoding header at all") (round 26 finding 5)
    try (ServerSocket server = new ServerSocket(0)) {
      String realBody = "THE-REAL-FILE-CONTENT-1234567890";
      Thread serverThread = new Thread(() -> {
        try (java.net.Socket socket = server.accept()) {
          InputStream in = socket.getInputStream();
          StringBuilder request = new StringBuilder();
          int c;
          while ((c = in.read()) != -1) {
            request.append((char) c);
            if (request.toString().endsWith("\r\n\r\n")) {
              break;
            }
          }
          String response = "HTTP/1.1 200 OK\r\n"
              + "Transfer-Encoding: \r\n"
              + "Connection: close\r\n"
              + "\r\n" + realBody;
          socket.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
        } catch (IOException ex) {
          // test failure surfaces via the client-side assertion below not completing, not here
        }
      });
      serverThread.start();
      String url = "http://localhost:" + server.getLocalPort() + "/";
      Path file = tempDir.resolve("out.txt");
      StreamUtils.copyUrlToFile(url, file);
      assertEquals(realBody, Files.readString(file, StandardCharsets.US_ASCII));
      serverThread.join();
    }
  }

  @Test
  void testCopyUrlToFileWithEmptyBodyAndUnsupportedTransferEncodingSucceeds(@TempDir Path tempDir)
      throws IOException, InterruptedException {
    // matches the Content-Encoding checks' own zero-length-body exemption - a genuinely empty body has
    // nothing for this HTTP stack to have mishandled either way, regardless of what Transfer-Encoding claims
    try (ServerSocket server = new ServerSocket(0)) {
      Thread serverThread = new Thread(() -> {
        try (java.net.Socket socket = server.accept()) {
          InputStream in = socket.getInputStream();
          StringBuilder request = new StringBuilder();
          int c;
          while ((c = in.read()) != -1) {
            request.append((char) c);
            if (request.toString().endsWith("\r\n\r\n")) {
              break;
            }
          }
          String response = "HTTP/1.1 200 OK\r\n"
              + "Transfer-Encoding: gzip\r\n"
              + "Connection: close\r\n"
              + "\r\n";
          socket.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
        } catch (IOException ex) {
          // test failure surfaces via the client-side assertion below not completing, not here
        }
      });
      serverThread.start();
      String url = "http://localhost:" + server.getLocalPort() + "/";
      Path file = tempDir.resolve("out.txt");
      StreamUtils.copyUrlToFile(url, file);
      assertEquals(0, Files.size(file));
      serverThread.join();
    }
  }

  @Test
  void testCopyUrlToFileRejectsTransferEncodingChunkedWithParameter(@TempDir Path tempDir)
      throws IOException, InterruptedException {
    // java.net.http.HttpClient's own Http1Response.fixupContentLen() dechunks a response only when the
    // ENTIRE Transfer-Encoding header value, verbatim, case-insensitively equals "chunked" - a value with a
    // trailing parameter like "chunked;foo=bar" fails that whole-string match, so the JDK falls back to
    // reading raw bytes until connection close instead of dechunking, even though the wire body genuinely IS
    // chunk-framed here - leaving the literal chunk-envelope bytes on disk with a normal 200 status (round 24's
    // own Transfer-Encoding guard didn't catch this: it comma-split/param-stripped the value the same way the
    // Content-Encoding checks legitimately do, but chunked-detection isn't a coding list to the JDK at all)
    try (ServerSocket server = new ServerSocket(0)) {
      String realBody = "THE-REAL-FILE-CONTENT-1234567890";
      Thread serverThread = new Thread(() -> {
        try (java.net.Socket socket = server.accept()) {
          InputStream in = socket.getInputStream();
          StringBuilder request = new StringBuilder();
          int c;
          while ((c = in.read()) != -1) {
            request.append((char) c);
            if (request.toString().endsWith("\r\n\r\n")) {
              break;
            }
          }
          String chunkedBody = Integer.toHexString(realBody.length()) + "\r\n" + realBody + "\r\n0\r\n\r\n";
          String response = "HTTP/1.1 200 OK\r\n"
              + "Transfer-Encoding: chunked;foo=bar\r\n"
              + "Connection: close\r\n"
              + "\r\n" + chunkedBody;
          socket.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
        } catch (IOException ex) {
          // test failure surfaces via the client-side assertion below not completing, not here
        }
      });
      serverThread.start();
      String url = "http://localhost:" + server.getLocalPort() + "/";
      Path file = tempDir.resolve("out.txt");
      IOException ex = assertThrows(IOException.class, () -> StreamUtils.copyUrlToFile(url, file));
      assertTrue(ex.getMessage() != null && ex.getMessage().contains("chunked"), String.valueOf(ex.getMessage()));
      serverThread.join();
    }
  }

  @Test
  void testCopyUrlToFileRejectsStackedTransferEncodingCodingsInSingleHeaderLine(@TempDir Path tempDir)
      throws IOException, InterruptedException {
    // "identity, chunked" as ONE header line is, again, not a whole-string match against "chunked" - the JDK
    // doesn't dechunk it, so the literal chunk-envelope bytes end up on disk. Confirmed against main's real
    // Apache HttpClient 4.5.14 that this specific input is a genuine regression (main dechunks it correctly,
    // since Apache's chunked-detection checks the LAST coding in the combined header value, not the whole
    // string) - unlike most of this test class's Transfer-Encoding checks, which are main-parity blind spots
    try (ServerSocket server = new ServerSocket(0)) {
      String realBody = "THE-REAL-FILE-CONTENT-1234567890";
      Thread serverThread = new Thread(() -> {
        try (java.net.Socket socket = server.accept()) {
          InputStream in = socket.getInputStream();
          StringBuilder request = new StringBuilder();
          int c;
          while ((c = in.read()) != -1) {
            request.append((char) c);
            if (request.toString().endsWith("\r\n\r\n")) {
              break;
            }
          }
          String chunkedBody = Integer.toHexString(realBody.length()) + "\r\n" + realBody + "\r\n0\r\n\r\n";
          String response = "HTTP/1.1 200 OK\r\n"
              + "Transfer-Encoding: identity, chunked\r\n"
              + "Connection: close\r\n"
              + "\r\n" + chunkedBody;
          socket.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
        } catch (IOException ex) {
          // test failure surfaces via the client-side assertion below not completing, not here
        }
      });
      serverThread.start();
      String url = "http://localhost:" + server.getLocalPort() + "/";
      Path file = tempDir.resolve("out.txt");
      assertThrows(IOException.class, () -> StreamUtils.copyUrlToFile(url, file));
      serverThread.join();
    }
  }

  @Test
  void testCopyUrlToFileRejectsDuplicateChunkedTransferEncodingHeaderLines(@TempDir Path tempDir)
      throws IOException, InterruptedException {
    // RFC 7230 section 3.3.1 forbids applying "chunked" more than once - two separate, literally-identical
    // "Transfer-Encoding: chunked" lines is itself a non-compliant shape, even though the JDK happens to
    // dechunk it correctly today anyway (Http1Response.fixupContentLen() only ever looks at the FIRST
    // Transfer-Encoding value, which is "chunked" here, so the duplicate second line is silently ignored).
    // Deliberately rejected rather than accommodated: requiring exactly ONE Transfer-Encoding header line is
    // what lets this check trust the JDK's own single-value dechunking decision at all (see
    // isPlainChunkedTransferEncoding()'s javadoc) - a real server has no legitimate reason to send a
    // duplicate line, and rejecting it converts a "correct only by JDK accident" case into a documented one,
    // not a loss of any real capability
    try (ServerSocket server = new ServerSocket(0)) {
      String realBody = "THE-REAL-FILE-CONTENT-1234567890";
      Thread serverThread = new Thread(() -> {
        try (java.net.Socket socket = server.accept()) {
          InputStream in = socket.getInputStream();
          StringBuilder request = new StringBuilder();
          int c;
          while ((c = in.read()) != -1) {
            request.append((char) c);
            if (request.toString().endsWith("\r\n\r\n")) {
              break;
            }
          }
          String chunkedBody = Integer.toHexString(realBody.length()) + "\r\n" + realBody + "\r\n0\r\n\r\n";
          String response = "HTTP/1.1 200 OK\r\n"
              + "Transfer-Encoding: chunked\r\n"
              + "Transfer-Encoding: chunked\r\n"
              + "Connection: close\r\n"
              + "\r\n" + chunkedBody;
          socket.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
        } catch (IOException ex) {
          // test failure surfaces via the client-side assertion below not completing, not here
        }
      });
      serverThread.start();
      String url = "http://localhost:" + server.getLocalPort() + "/";
      Path file = tempDir.resolve("out.txt");
      assertThrows(IOException.class, () -> StreamUtils.copyUrlToFile(url, file));
      serverThread.join();
    }
  }

  @Test
  void testCopyUrlToFileAcceptsTransferEncodingIdentityWithContentLength(@TempDir Path tempDir)
      throws IOException, InterruptedException {
    // "identity" is a no-op coding (RFC 2616 section 3.6) - unlike "chunked", it doesn't redefine framing, so
    // Content-Length remains authoritative and the two headers aren't actually in conflict. Before this round,
    // checkAmbiguousFraming() disagreed with isUnsupportedTransferEncoding() (which already treated "identity"
    // as safe) and rejected this combination even though the file on disk would have been entirely correct -
    // verified main's Apache HttpClient 4.5.14 succeeds for this exact input too
    try (ServerSocket server = new ServerSocket(0)) {
      String realBody = "THE-REAL-FILE-CONTENT-1234567890";
      Thread serverThread = new Thread(() -> {
        try (java.net.Socket socket = server.accept()) {
          InputStream in = socket.getInputStream();
          StringBuilder request = new StringBuilder();
          int c;
          while ((c = in.read()) != -1) {
            request.append((char) c);
            if (request.toString().endsWith("\r\n\r\n")) {
              break;
            }
          }
          String response = "HTTP/1.1 200 OK\r\n"
              + "Transfer-Encoding: identity\r\n"
              + "Content-Length: " + realBody.length() + "\r\n"
              + "Connection: close\r\n"
              + "\r\n" + realBody;
          socket.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
        } catch (IOException ex) {
          // test failure surfaces via the client-side assertion below not completing, not here
        }
      });
      serverThread.start();
      String url = "http://localhost:" + server.getLocalPort() + "/";
      Path file = tempDir.resolve("out.txt");
      StreamUtils.copyUrlToFile(url, file);
      assertEquals(realBody, Files.readString(file, StandardCharsets.US_ASCII));
      serverThread.join();
    }
  }

  @Test
  void testCopyUrlToFileRejectsNonChunkedTransferEncodingCombinedWithContentLength(@TempDir Path tempDir)
      throws IOException, InterruptedException {
    // round 25 finding 6 narrowed checkAmbiguousFraming()'s Transfer-Encoding+Content-Length conflict check
    // from "any Transfer-Encoding" to "only a PLAIN chunked Transfer-Encoding" (isPlainChunkedTransferEncoding()),
    // so that "identity" (a genuine no-op coding) would stop false-positively tripping it - but that also
    // exempted every OTHER non-plain-chunked value (e.g. "chunked;a=b", which isUnsupportedTransferEncoding()
    // itself already rejects) from this check specifically, letting it fall through to that sibling check
    // instead - which only fires when Files.size(file) > 0 (the empty-body exemption). A wire body that's
    // genuinely chunk-framed but, per fixupContentLen()'s exact whole-string-match rule, never gets dechunked
    // (because of the ";a=b" parameter) is instead framed by "Content-Length: 0" - the JDK reads zero bytes,
    // both checks pass (checkAmbiguousFraming() no longer fires since this isn't plain chunked;
    // isUnsupportedTransferEncoding() is gated on a non-empty file, which this isn't), and the result is a
    // SILENT 0-byte file reported as a normal 200 success - the exact "corrupts the destination while
    // reporting SUCCESS" class this whole check exists to prevent, reopened by round 25's own narrowing (round
    // 26 finding 2)
    try (ServerSocket server = new ServerSocket(0)) {
      String chunkedBody = "1\r\nA\r\n1\r\nB\r\n1\r\nC\r\n0\r\n\r\n"; // a real, non-empty chunk-framed body
      Thread serverThread = new Thread(() -> {
        try (java.net.Socket socket = server.accept()) {
          InputStream in = socket.getInputStream();
          StringBuilder request = new StringBuilder();
          int c;
          while ((c = in.read()) != -1) {
            request.append((char) c);
            if (request.toString().endsWith("\r\n\r\n")) {
              break;
            }
          }
          String response = "HTTP/1.1 200 OK\r\n"
              + "Transfer-Encoding: chunked;a=b\r\n"
              + "Content-Length: 0\r\n"
              + "Connection: close\r\n"
              + "\r\n" + chunkedBody;
          socket.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
        } catch (IOException ex) {
          // test failure surfaces via the client-side assertion below not completing, not here
        }
      });
      serverThread.start();
      String url = "http://localhost:" + server.getLocalPort() + "/";
      Path file = tempDir.resolve("out.txt");
      IOException ex = assertThrows(IOException.class, () -> StreamUtils.copyUrlToFile(url, file));
      assertTrue(ex.getMessage() != null && ex.getMessage().contains("Transfer-Encoding"), String.valueOf(ex.getMessage()));
      serverThread.join();
    }
  }

  @Test
  void testCopyUrlToFileWithAmbiguousFramingErrorBodyNamesTheStatus(@TempDir Path tempDir)
      throws IOException, InterruptedException {
    // matches the gzip-decode-failure/unrecognized-Content-Encoding path's own convention: if the status is
    // already non-200, that's the more diagnostically useful thing to report - the framing diagnostic itself
    // must still be preserved as a suppressed exception, not discarded (round 25: this check didn't follow
    // that convention at all before, so the status was always lost when framing was ALSO ambiguous)
    try (ServerSocket server = new ServerSocket(0)) {
      Thread serverThread = new Thread(() -> {
        try (java.net.Socket socket = server.accept()) {
          InputStream in = socket.getInputStream();
          StringBuilder request = new StringBuilder();
          int c;
          while ((c = in.read()) != -1) {
            request.append((char) c);
            if (request.toString().endsWith("\r\n\r\n")) {
              break;
            }
          }
          String response = "HTTP/1.1 404 Not Found\r\n"
              + "Transfer-Encoding: chunked\r\n"
              + "Content-Length: 5\r\n"
              + "Connection: close\r\n"
              + "\r\n5\r\nhello\r\n0\r\n\r\n";
          socket.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
        } catch (IOException ex) {
          // test failure surfaces via the client-side assertion below not completing, not here
        }
      });
      serverThread.start();
      String url = "http://localhost:" + server.getLocalPort() + "/";
      Path file = tempDir.resolve("out.txt");
      IOException ex = assertThrows(IOException.class, () -> StreamUtils.copyUrlToFile(url, file));
      assertTrue(ex.getMessage() != null && ex.getMessage().contains(url) && ex.getMessage().contains("404"),
          String.valueOf(ex.getMessage()));
      assertTrue(ex.getSuppressed().length == 1 && ex.getSuppressed()[0].getMessage() != null
              && ex.getSuppressed()[0].getMessage().contains("ambiguous framing"),
          "expected a suppressed exception naming the framing problem, got: " + Arrays.toString(ex.getSuppressed()));
      serverThread.join();
    }
  }

  @Test
  void testCopyUrlToFileWithUnsupportedTransferEncodingErrorBodyNamesTheStatus(@TempDir Path tempDir)
      throws IOException, InterruptedException {
    // same status-preference convention as testCopyUrlToFileWithAmbiguousFramingErrorBodyNamesTheStatus above
    byte[] notGzip = "<html>404 Not Found</html>".getBytes(StandardCharsets.UTF_8);
    try (ServerSocket server = new ServerSocket(0)) {
      Thread serverThread = new Thread(() -> {
        try (java.net.Socket socket = server.accept()) {
          InputStream in = socket.getInputStream();
          StringBuilder request = new StringBuilder();
          int c;
          while ((c = in.read()) != -1) {
            request.append((char) c);
            if (request.toString().endsWith("\r\n\r\n")) {
              break;
            }
          }
          OutputStream out = socket.getOutputStream();
          out.write(("HTTP/1.1 404 Not Found\r\n"
              + "Transfer-Encoding: gzip\r\n"
              + "Connection: close\r\n"
              + "\r\n").getBytes(StandardCharsets.US_ASCII));
          out.write(notGzip);
        } catch (IOException ex) {
          // test failure surfaces via the client-side assertion below not completing, not here
        }
      });
      serverThread.start();
      String url = "http://localhost:" + server.getLocalPort() + "/";
      Path file = tempDir.resolve("out.txt");
      IOException ex = assertThrows(IOException.class, () -> StreamUtils.copyUrlToFile(url, file));
      assertTrue(ex.getMessage() != null && ex.getMessage().contains(url) && ex.getMessage().contains("404"),
          String.valueOf(ex.getMessage()));
      assertTrue(ex.getSuppressed().length == 1 && ex.getSuppressed()[0].getMessage() != null
              && ex.getSuppressed()[0].getMessage().contains("Transfer-Encoding"),
          "expected a suppressed exception naming the unsupported coding, got: " + Arrays.toString(ex.getSuppressed()));
      serverThread.join();
    }
  }

  @Test
  void testCopyUrlToFileWithNoResponseBodyThrowsIOException(@TempDir Path tempDir) throws IOException {
    // a non-200 status (e.g. 204 No Content) must surface as an informative IOException naming the URL
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/", exchange -> {
      exchange.sendResponseHeaders(204, -1);
      exchange.close();
    });
    server.start();
    try {
      String url = "http://localhost:" + server.getAddress().getPort() + "/";
      Path file = tempDir.resolve("out.txt");
      IOException ex = assertThrows(IOException.class, () -> StreamUtils.copyUrlToFile(url, file));
      assertTrue(ex.getMessage().contains(url), ex.getMessage());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void testCopyUrlToFileTakesHttpBranchWithLeadingWhitespace(@TempDir Path tempDir) throws IOException {
    // url.strip() (round 30) strips leading/trailing whitespace (and UrlUtils.isValid() agrees this is a
    // valid http URL), but a raw-string prefix check like "url.startsWith(\"http://\")" doesn't - a
    // whitespace-padded URL (plausible from a curation spreadsheet/TSV column) must still take the HTTP(S)
    // branch (redirect-following + status-code handling + error-body-saving), not silently fall into the
    // generic else branch meant for FTP-like URLs. Asserting only that the failure message contains the url
    // was insufficient (round 32 finding 3, mutation-verified): the parsing prologue's OWN failure message
    // ("Malformed URL: " + url) also contains the url, so this test passed even with url.strip() removed
    // entirely (the padded url would then fail to parse at all, never reaching either branch) - asserting the
    // response's actual 204 status code pins that the HTTP branch, not the parsing failure, is what ran
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/", exchange -> {
      exchange.sendResponseHeaders(204, -1);
      exchange.close();
    });
    server.start();
    try {
      String url = " http://localhost:" + server.getAddress().getPort() + "/";
      Path file = tempDir.resolve("out.txt");
      IOException ex = assertThrows(IOException.class, () -> StreamUtils.copyUrlToFile(url, file));
      assertTrue(ex.getMessage().contains(url), ex.getMessage());
      assertTrue(ex.getMessage().contains("204"), ex.getMessage());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void testCopyUrlToFileEncodesUnescapedSpaceInPath(@TempDir Path tempDir) throws IOException {
    // java.net.URL(String) tolerated a literal, unencoded space in the path (deferring character validation
    // to the protocol handler); RFC-3986-compliant java.net.URI does not. Migrating off the deprecated
    // URL(String) constructor (round 30 finding 6) percent-encodes a literal space to "%20" before parsing,
    // rather than silently dropping this leniency - verified this isn't just preserving old behavior, it's
    // fixing a previously-broken case: URL(String) itself accepted an unescaped space, but the very next
    // line (parsedUrl.toURI()) already rejected it regardless, so this specific leniency was never actually
    // reachable at this call site before either way
    AtomicReference<String> capturedPath = new AtomicReference<>();
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/", exchange -> {
      capturedPath.set(exchange.getRequestURI().getRawPath());
      exchange.sendResponseHeaders(204, -1);
      exchange.close();
    });
    server.start();
    try {
      String url = "http://localhost:" + server.getAddress().getPort() + "/a b";
      Path file = tempDir.resolve("out.txt");
      // 204 is a non-200 status, so this still throws - the point being verified is that the request was
      // actually SENT (and reached the server with a correctly percent-encoded path), not blocked at parse
      // time by a "Malformed URL" IOException the way it was before this fix
      IOException ex = assertThrows(IOException.class, () -> StreamUtils.copyUrlToFile(url, file));
      assertTrue(ex.getMessage().contains("204"), ex.getMessage());
      assertEquals("/a%20b", capturedPath.get());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void testCopyUrlToFileSchemeCheckIsCaseInsensitive(@TempDir Path tempDir) throws IOException {
    // URI schemes are case-insensitive (UrlUtils.isValid("HTTP://...") accepts this) - an uppercase-scheme
    // URL must still take the HTTP(S) branch (redirect-following + status-code handling), not silently fall
    // into the generic else branch meant for FTP-like URLs
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/", exchange -> {
      exchange.sendResponseHeaders(204, -1);
      exchange.close();
    });
    server.start();
    try {
      String url = "HTTP://localhost:" + server.getAddress().getPort() + "/";
      Path file = tempDir.resolve("out.txt");
      IOException ex = assertThrows(IOException.class, () -> StreamUtils.copyUrlToFile(url, file));
      assertTrue(ex.getMessage().contains(url), ex.getMessage());
    } finally {
      server.stop(0);
    }
  }


  @Test
  void testOpenInputStreamNoSuchFileExceptionNamesThePath(@TempDir Path tempDir) {
    // NoSuchFileException(String) takes the FILE, not a message - using it for a human-readable message
    // means the actual path never appears anywhere in the exception
    Path missing = tempDir.resolve("does-not-exist.txt");
    NoSuchFileException ex = assertThrows(NoSuchFileException.class, () -> StreamUtils.openInputStream(missing));
    assertEquals(missing.toString(), ex.getFile());
  }

  @Test
  void testOpenInputStreamThrowsForDirectory(@TempDir Path tempDir) {
    // a directory exists (so the earlier Files.exists() check doesn't catch it) but doesn't lead to a
    // regular file - must be rejected with the same "Path does not lead to a regular file" diagnosis as
    // openReader() below, not a bare, less-informative "Is a directory" IOException
    NoSuchFileException ex = assertThrows(NoSuchFileException.class, () -> StreamUtils.openInputStream(tempDir));
    assertEquals(tempDir.toString(), ex.getFile());
  }

  @Test
  void testOpenReaderThrowsForDirectory(@TempDir Path tempDir) {
    NoSuchFileException ex = assertThrows(NoSuchFileException.class, () -> StreamUtils.openReader(tempDir));
    assertEquals(tempDir.toString(), ex.getFile());
  }

  @Test
  void testInputStream() throws IOException {
    Path file = PathUtils.getPathToResource(getClass(), "StreamUtilsTest.txt");

    StringWriter writer = new StringWriter();
    try (InputStream inputStream = StreamUtils.openInputStream(file);
         WriterOutputStream outputStream = new WriterOutputStream(writer, StandardCharsets.UTF_8)) {
      IOUtils.copy(inputStream, outputStream);
    }
    assertEquals("hello, world", StringUtils.stripToEmpty(writer.toString()));
  }

  @Test
  void testUppercaseExtensionIsLocaleIndependent(@TempDir Path tempDir) throws IOException {
    // the ".zip"/".gz" extension checks must not depend on the JVM's default locale - under Turkish/Azeri
    // locales, "ZIP".toLowerCase() produces "zıp" (dotless i), which silently fails the ".zip" check and
    // returns the raw zip container bytes instead of unwrapping the entry
    Locale defaultLocale = Locale.getDefault();
    try {
      Locale.setDefault(new Locale("tr", "TR"));
      Path source = PathUtils.getPathToResource(getClass(), "StreamUtilsTest.txt.zip");
      // only the extension's case changes - openInputStream() looks for a zip entry matching the base
      // filename (minus ".zip"), which must stay "StreamUtilsTest.txt" to match the entry inside the zip
      Path upperCaseZip = tempDir.resolve("StreamUtilsTest.txt.ZIP");
      Files.copy(source, upperCaseZip);

      StringWriter writer = new StringWriter();
      try (InputStream inputStream = StreamUtils.openInputStream(upperCaseZip);
           WriterOutputStream outputStream = new WriterOutputStream(writer, StandardCharsets.UTF_8)) {
        IOUtils.copy(inputStream, outputStream);
      }
      assertEquals("hello, world", StringUtils.stripToEmpty(writer.toString()));
    } finally {
      Locale.setDefault(defaultLocale);
    }
  }

  @Test
  void testZipInputStream() throws IOException {
    Path file = PathUtils.getPathToResource(getClass(), "StreamUtilsTest.txt.zip");

    StringWriter writer = new StringWriter();
    try (InputStream inputStream = StreamUtils.openInputStream(file);
         WriterOutputStream outputStream = new WriterOutputStream(writer, StandardCharsets.UTF_8)) {
      IOUtils.copy(inputStream, outputStream);
    }
    assertEquals("hello, world", StringUtils.stripToEmpty(writer.toString()));
  }

  @Test
  void testGzInputStream() throws IOException {
    Path file = PathUtils.getPathToResource(getClass(), "StreamUtilsTest.txt.gz");

    StringWriter writer = new StringWriter();
    try (InputStream inputStream = StreamUtils.openInputStream(file);
         WriterOutputStream outputStream = new WriterOutputStream(writer, StandardCharsets.UTF_8)) {
      IOUtils.copy(inputStream, outputStream);
    }
    assertEquals("hello, world", StringUtils.stripToEmpty(writer.toString()));
  }


  @Test
  void testReader() throws IOException {
    Path file = PathUtils.getPathToResource(getClass(), "StreamUtilsTest.txt");

    StringWriter writer = new StringWriter();
    try (BufferedReader reader = StreamUtils.openReader(file)) {
      IOUtils.copy(reader, writer);
    }
    assertEquals("hello, world", StringUtils.stripToEmpty(writer.toString()));
  }

  @Test
  void testPlainReaderRejectsMalformedUtf8(@TempDir Path tempDir) throws IOException {
    // characterizes openReader()'s plain (uncompressed) branch's own strict-decoding guarantee, which
    // testGzReaderRejectsMalformedUtf8/testZipReaderRejectsMalformedUtf8 below both cite as the reference
    // behavior the .gz/.zip branches must match, but never test directly themselves
    Path file = tempDir.resolve("malformed.txt");
    byte[] malformedUtf8 = { (byte)0xC3, (byte)0x28 }; // invalid UTF-8 sequence
    Files.write(file, malformedUtf8);

    try (BufferedReader reader = StreamUtils.openReader(file)) {
      assertThrows(java.nio.charset.MalformedInputException.class, reader::readLine);
    }
  }

  @Test
  void testZipReader() throws IOException {
    Path file = PathUtils.getPathToResource(getClass(), "StreamUtilsTest.txt.zip");

    StringWriter writer = new StringWriter();
    try (BufferedReader reader = StreamUtils.openReader(file)) {
      IOUtils.copy(reader, writer);
    }
    assertEquals("hello, world", StringUtils.stripToEmpty(writer.toString()));
  }

  @Test
  void testGzReader() throws IOException {
    Path file = PathUtils.getPathToResource(getClass(), "StreamUtilsTest.txt.gz");

    StringWriter writer = new StringWriter();
    try (BufferedReader reader = StreamUtils.openReader(file)) {
      IOUtils.copy(reader, writer);
    }
    assertEquals("hello, world", StringUtils.stripToEmpty(writer.toString()));
  }

  @Test
  void testGzReaderIsUtf8(@TempDir Path tempDir) throws IOException {
    // openReader must decode as UTF-8 regardless of the JVM's platform default charset
    Path file = tempDir.resolve("utf8.txt.gz");
    try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
      out.write("café 日本語".getBytes(StandardCharsets.UTF_8));
    }

    StringWriter writer = new StringWriter();
    try (BufferedReader reader = StreamUtils.openReader(file)) {
      IOUtils.copy(reader, writer);
    }
    assertEquals("café 日本語", writer.toString());
  }

  @Test
  void testZipReaderIsUtf8(@TempDir Path tempDir) throws IOException {
    // openReader must decode as UTF-8 regardless of the JVM's platform default charset
    Path file = tempDir.resolve("utf8.txt.zip");
    try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file))) {
      out.putNextEntry(new ZipEntry("utf8.txt"));
      out.write("café 日本語".getBytes(StandardCharsets.UTF_8));
      out.closeEntry();
    }

    StringWriter writer = new StringWriter();
    try (BufferedReader reader = StreamUtils.openReader(file)) {
      IOUtils.copy(reader, writer);
    }
    assertEquals("café 日本語", writer.toString());
  }

  @Test
  void testGzReaderRejectsMalformedUtf8(@TempDir Path tempDir) throws IOException {
    // openReader() for a plain (uncompressed) file uses Files.newBufferedReader(), whose decoder reports
    // malformed input as a MalformedInputException; the .gz/.zip branches must behave the same way, not
    // silently replace malformed bytes with U+FFFD (which is what happens when a Charset, rather than a
    // CharsetDecoder, is passed to InputStreamReader)
    Path file = tempDir.resolve("malformed.txt.gz");
    byte[] malformedUtf8 = { (byte)0xC3, (byte)0x28 }; // invalid UTF-8 sequence
    try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
      out.write(malformedUtf8);
    }

    try (BufferedReader reader = StreamUtils.openReader(file)) {
      assertThrows(java.nio.charset.MalformedInputException.class, reader::readLine);
    }
  }

  @Test
  void testZipReaderRejectsMalformedUtf8(@TempDir Path tempDir) throws IOException {
    // same guarantee as testGzReaderRejectsMalformedUtf8 above, but for the .zip branch - both branches were
    // switched to the strict UTF_8.newDecoder() in the same commit, but only the .gz branch had a dedicated
    // regression test for it
    Path file = tempDir.resolve("malformed.txt.zip");
    byte[] malformedUtf8 = { (byte)0xC3, (byte)0x28 }; // invalid UTF-8 sequence
    try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file))) {
      out.putNextEntry(new ZipEntry("malformed.txt"));
      out.write(malformedUtf8);
      out.closeEntry();
    }

    try (BufferedReader reader = StreamUtils.openReader(file)) {
      assertThrows(java.nio.charset.MalformedInputException.class, reader::readLine);
    }
  }

  @Test
  void testBadGzInputStreamThrows(@TempDir Path tempDir) throws IOException {
    // a file misnamed with a .gz extension but containing plain text should fail to parse as gzip,
    // not leak the underlying file stream. The wrapper naming the path (round 25) must preserve the
    // original ZipException as its cause, not just discard it
    Path file = tempDir.resolve("notActuallyGzipped.gz");
    Files.writeString(file, "hello, world");

    IOException ex = assertThrows(IOException.class, () -> StreamUtils.openInputStream(file));
    assertTrue(ex.getMessage() != null && ex.getMessage().contains(file.toString()), String.valueOf(ex.getMessage()));
    assertInstanceOf(ZipException.class, ex.getCause());
  }

  @Test
  void testOpenInputStreamDoesNotLeakFileDescriptorOnMalformedGzipHeader(@TempDir Path tempDir) throws IOException {
    // same GZIPInputStream-construction-can-throw fd-leak-avoidance pattern as
    // testDecompressGzipInPlaceDoesNotLeakFileDescriptorOnMalformedHeader above (see that test for why),
    // applied here too - but openInputStream()'s own .gz branch had no dedicated test of its own confirming
    // the fix actually prevents the leak, only testBadGzInputStreamThrows confirming the exception type
    Path proc = Path.of("/proc/self/fd");
    Assumptions.assumeTrue(Files.isDirectory(proc), "requires /proc/self/fd (Linux)");

    Path file = tempDir.resolve("notActuallyGzipped.gz");
    Files.writeString(file, "hello, world");

    int attempts = 50;
    long before = countOpenFds(proc);
    for (int i = 0; i < attempts; i++) {
      assertThrows(IOException.class, () -> StreamUtils.openInputStream(file));
    }
    long after = countOpenFds(proc);

    assertTrue(after - before < attempts,
        "expected no per-attempt fd growth after " + attempts + " failed attempts, but open fd count went " +
            "from " + before + " to " + after);
  }

  @Test
  void testOpenReaderDoesNotLeakFileDescriptorOnMalformedGzipHeader(@TempDir Path tempDir) throws IOException {
    // same fd-leak-avoidance pattern as testOpenInputStreamDoesNotLeakFileDescriptorOnMalformedGzipHeader
    // above, but for openReader()'s own .gz branch, which had no dedicated test of its own either
    Path proc = Path.of("/proc/self/fd");
    Assumptions.assumeTrue(Files.isDirectory(proc), "requires /proc/self/fd (Linux)");

    Path file = tempDir.resolve("notActuallyGzipped.gz");
    Files.writeString(file, "hello, world");

    int attempts = 50;
    long before = countOpenFds(proc);
    for (int i = 0; i < attempts; i++) {
      assertThrows(IOException.class, () -> StreamUtils.openReader(file));
    }
    long after = countOpenFds(proc);

    assertTrue(after - before < attempts,
        "expected no per-attempt fd growth after " + attempts + " failed attempts, but open fd count went " +
            "from " + before + " to " + after);
  }

  @Test
  void testOpenInputStreamMalformedGzipHeaderNamesThePath(@TempDir Path tempDir) throws IOException {
    // round 25: openInputStream()'s .gz decode failure didn't name the path at all (a bare ZipException),
    // unlike its own pre-checks (NoSuchFileException) a few lines earlier in the same method
    Path file = tempDir.resolve("notActuallyGzipped.gz");
    Files.writeString(file, "hello, world");
    IOException ex = assertThrows(IOException.class, () -> StreamUtils.openInputStream(file));
    assertTrue(ex.getMessage() != null && ex.getMessage().contains(file.toString()), String.valueOf(ex.getMessage()));
  }

  @Test
  void testOpenReaderMalformedGzipHeaderNamesThePath(@TempDir Path tempDir) throws IOException {
    // same gap as testOpenInputStreamMalformedGzipHeaderNamesThePath above, but for openReader()
    Path file = tempDir.resolve("notActuallyGzipped.gz");
    Files.writeString(file, "hello, world");
    IOException ex = assertThrows(IOException.class, () -> StreamUtils.openReader(file));
    assertTrue(ex.getMessage() != null && ex.getMessage().contains(file.toString()), String.valueOf(ex.getMessage()));
    assertInstanceOf(ZipException.class, ex.getCause());
  }

  @Test
  void testOpenInputStreamMalformedZipEntryNameNamesThePath(@TempDir Path tempDir) throws IOException {
    // ZipInputStream.getNextEntry() throws an unchecked IllegalArgumentException (not IOException) for an
    // entry name that's invalid UTF-8 with the UTF-8 (EFS) flag set (see ZippedFileInputStreamTest's own
    // closesUnderlyingStreamWhenEntryNameIsMalformed - that class's constructor already closes the underlying
    // stream for this case, so no fd leak) - but neither openInputStream()'s nor openReader()'s .zip branch
    // caught it at all, so it escaped past this method's own declared "throws IOException" entirely, unlike
    // every other decode-failure path in this class (round 26 finding 3, a gap left over from before the .gz
    // branch's own path-naming fix in round 25)
    Path file = tempDir.resolve("malformed.txt.zip");
    Files.write(file, buildLocalFileHeaderWithMalformedUtf8EntryName());
    IOException ex = assertThrows(IOException.class, () -> StreamUtils.openInputStream(file));
    assertTrue(ex.getMessage() != null && ex.getMessage().contains(file.toString()), String.valueOf(ex.getMessage()));
    assertInstanceOf(IllegalArgumentException.class, ex.getCause());
  }

  @Test
  void testOpenReaderMalformedZipEntryNameNamesThePath(@TempDir Path tempDir) throws IOException {
    // same gap as testOpenInputStreamMalformedZipEntryNameNamesThePath above, but for openReader()
    Path file = tempDir.resolve("malformed.txt.zip");
    Files.write(file, buildLocalFileHeaderWithMalformedUtf8EntryName());
    IOException ex = assertThrows(IOException.class, () -> StreamUtils.openReader(file));
    assertTrue(ex.getMessage() != null && ex.getMessage().contains(file.toString()), String.valueOf(ex.getMessage()));
    assertInstanceOf(IllegalArgumentException.class, ex.getCause());
  }

  /**
   * Hand-builds the bytes of a single zip local file header whose general-purpose flag declares UTF-8 (EFS,
   * bit 11) encoding, but whose entry name is not valid UTF-8 - triggering an {@link IllegalArgumentException}
   * from the JDK's zip entry-name decoder. Same layout as
   * {@code ZippedFileInputStreamTest.buildLocalFileHeaderWithMalformedUtf8Name()} - not shared between the two
   * test classes since it's a small, self-contained fixture rather than real production/test-utility code.
   */
  private static byte[] buildLocalFileHeaderWithMalformedUtf8EntryName() throws IOException {
    byte[] nameBytes = { (byte)0xC3, (byte)0x28 }; // invalid UTF-8 sequence
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    java.io.DataOutputStream dos = new java.io.DataOutputStream(bos);
    dos.writeInt(Integer.reverseBytes(0x04034b50)); // local file header signature
    dos.writeShort(Short.reverseBytes((short)20)); // version needed to extract
    dos.writeShort(Short.reverseBytes((short)0x0800)); // general purpose flag: UTF-8 (EFS)
    dos.writeShort(Short.reverseBytes((short)0)); // compression method: stored
    dos.writeShort(Short.reverseBytes((short)0)); // last mod time
    dos.writeShort(Short.reverseBytes((short)0x21)); // last mod date
    dos.writeInt(0); // crc-32
    dos.writeInt(0); // compressed size
    dos.writeInt(0); // uncompressed size
    dos.writeShort(Short.reverseBytes((short)nameBytes.length)); // filename length
    dos.writeShort(Short.reverseBytes((short)0)); // extra field length
    dos.write(nameBytes);
    return bos.toByteArray();
  }

  @Test
  void testMd5ProducesCorrectHash(@TempDir Path tempDir) throws IOException {
    // no existing test asserted an actual hash value for any of the 3 md5 overloads - all 3 could return an
    // empty/wrong value and the suite would stay green. Reference values independently verified via
    // `openssl dgst -md5` / `printf 'hello' | openssl dgst -md5 -binary | base64`.
    byte[] expectedBytes = {
        (byte) 0x5d, 0x41, 0x40, 0x2a, (byte) 0xbc, 0x4b, 0x2a, 0x76,
        (byte) 0xb9, 0x71, (byte) 0x9d, (byte) 0x91, 0x10, 0x17, (byte) 0xc5, (byte) 0x92,
    };
    Path file = tempDir.resolve("hello.txt");
    Files.writeString(file, "hello", StandardCharsets.UTF_8);

    assertArrayEquals(expectedBytes, StreamUtils.md5(file));
    assertEquals("XUFAKrxLKna5cZ2REBfFkg==", StreamUtils.md5InBase64(file));
    assertEquals("XUFAKrxLKna5cZ2REBfFkg==",
        StreamUtils.md5InBase64("hello".getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void testMd5ForDirectoryNamesThePath(@TempDir Path tempDir) {
    // Files.newInputStream(file) throws a bare "Is a directory" IOException with no path at all for a
    // directory - md5() had no path-naming wrapper at all, unlike every other failure path in this class
    IOException ex = assertThrows(IOException.class, () -> StreamUtils.md5(tempDir));
    assertTrue(ex.getMessage() != null && ex.getMessage().contains(tempDir.toString()), String.valueOf(ex.getMessage()));
  }

  @Test
  void testUnrelatedCallDoesNotMutateHttpClientGlobalState() {
    // buildHttpClient()'s two JVM-global side effects (the redirect-retrylimit system property, the
    // CookieManager JUL logger's level) must only apply when something actually builds an HttpClient
    // (copyUrlToFile()), not merely from loading this class - calling an unrelated pure function like
    // md5InBase64() must not touch either. Asserted as "unchanged before vs. after" (not an absolute value)
    // so this doesn't depend on whether some OTHER test in this suite already triggered copyUrlToFile()
    // first, sharing this JVM.
    String propertyBefore = System.getProperty("jdk.httpclient.redirects.retrylimit");
    Level levelBefore = Logger.getLogger("java.net.CookieManager").getLevel();

    StreamUtils.md5InBase64(new byte[]{1, 2, 3});

    assertEquals(propertyBefore, System.getProperty("jdk.httpclient.redirects.retrylimit"));
    assertEquals(levelBefore, Logger.getLogger("java.net.CookieManager").getLevel());
  }
}
