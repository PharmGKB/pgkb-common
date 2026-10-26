package org.pharmgkb.common.util;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
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
    // a long redirect chain, well within HttpClient's default limit of 50
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
    // a redirect that sets a session cookie the follow-up hop requires (a common pattern for gated downloads)
    // must work
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
    // a host-only cookie (no Domain attribute) from one host must survive another host's same-named cookie
    // being set and then cleared (Max-Age=0); both host names resolve to the same loopback HttpServer
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
  void testCopyUrlToFileCarriesCookieWithMaxAgeAcrossRedirect(@TempDir Path tempDir) throws IOException {
    // a session cookie with Max-Age must still be sent back as a plain RFC 6265 "name=value" cookie
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
    // testCopyUrlToFileSucceedsOnSlowButProgressingDownload for why it must not be a total-time deadline),
    // and the partial file must not be left behind. The package-private overload lets this test use a short
    // timeout instead of waiting out the real 30s default
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
      assertFalse(Files.exists(file));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void testCopyUrlToFileWithCorruptGzipThrows(@TempDir Path tempDir) throws IOException {
    // a body that claims Content-Encoding: gzip but isn't gzip must fail, naming the url, and not leave a
    // partial file behind
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
      assertTrue(ex.getMessage() != null && ex.getMessage().contains(url), String.valueOf(ex.getMessage()));
      assertFalse(Files.exists(file));
    } finally {
      server.stop(0);
    }
  }


  @Test
  void testCopyUrlToFileDecodesGzipContentEncoding(@TempDir Path tempDir) throws IOException {
    // a gzip-encoded response must be saved decoded
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
    // "x-gzip" is a legacy alias for "gzip" that some older servers still send
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
    // RFC 9110 allows Content-Encoding to be a comma-separated list of codings
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/", exchange -> {
      ByteArrayOutputStream gz = new ByteArrayOutputStream();
      try (GZIPOutputStream gzOut = new GZIPOutputStream(gz)) {
        gzOut.write("hello, world".getBytes(StandardCharsets.UTF_8));
      }
      byte[] body = gz.toByteArray();
      exchange.getResponseHeaders().add("Content-Encoding", "identity, gzip");
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
  void testCopyUrlToFileRejectsContentEncodingAcrossRepeatedHeaderLines(@TempDir Path tempDir) throws IOException {
    // RFC 9110 lets a Content-Encoding list be split across header lines, but HttpClient only decodes the first
    // line - that must fail rather than silently save the body still gzipped
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
      IOException ex = assertThrows(IOException.class, () -> StreamUtils.copyUrlToFile(url, file));
      assertTrue(ex.getMessage().contains("Content-Encoding"), ex.getMessage());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void testCopyUrlToFileDecodesDeflateContentEncoding(@TempDir Path tempDir) throws IOException {
    // a server can send deflate even though gzip is preferred
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
      StreamUtils.copyUrlToFile(url, file);
      assertEquals("hello, world", Files.readString(file, StandardCharsets.UTF_8));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void testCopyUrlToFileDecodesStackedContentEncodings(@TempDir Path tempDir) throws IOException {
    // stacked codings (body = gzip(deflate(x))) must all be undone, not just the outermost one
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
      StreamUtils.copyUrlToFile(url, file);
      assertEquals("hello, world", Files.readString(file, StandardCharsets.UTF_8));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void testCopyUrlToFileDecodesRepeatedGzipContentEncoding(@TempDir Path tempDir) throws IOException {
    // "gzip, gzip" (body double-gzipped) must be decoded twice, not left half-compressed
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
      StreamUtils.copyUrlToFile(url, file);
      assertEquals("hello, world", Files.readString(file, StandardCharsets.UTF_8));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void testCopyUrlToFileWithEmptyUnrecognizedContentEncodingBodySucceeds(@TempDir Path tempDir) throws IOException {
    // an empty body has nothing to decode, whatever its Content-Encoding claims
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
  void testCopyUrlToFileRejectsContentEncodingWithParameter(@TempDir Path tempDir) throws IOException {
    // content codings take no parameters (RFC 9110 8.4.1) - an unexpected one must fail, not be guessed at
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
      IOException ex = assertThrows(IOException.class, () -> StreamUtils.copyUrlToFile(url, file));
      assertTrue(ex.getMessage().contains("identity;q=1"), ex.getMessage());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void testCopyUrlToFileDecodesGzipErrorBody(@TempDir Path tempDir) throws IOException {
    // the saved body of a non-200 response must be decoded too, so the error can be read
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
  void testCopyUrlToFileWithEmptyGzipBodySucceeds(@TempDir Path tempDir) throws IOException {
    // an empty body from a gzip-enabled server must succeed, not fail feeding zero bytes to a gzip decoder
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


  private static long countOpenFds(Path proc) throws IOException {
    try (var stream = Files.list(proc)) {
      return stream.count();
    }
  }


  @Test
  void testCopyUrlToFileTimesOutOnStalledHeaders(@TempDir Path tempDir) throws IOException {
    // a server that never sends response headers is as much a stall as a mid-body one
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
      assertTrue(ex.getMessage().contains(url) && ex.getMessage().contains("timed out"), ex.getMessage());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void testCopyUrlToFileSucceedsOnSlowButProgressingDownload(@TempDir Path tempDir) throws IOException {
    // the idle timeout bounds stalls, not total download time
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


  private static boolean hasNamedCookie(java.util.List<java.net.HttpCookie> cookies, String name) {
    return cookies.stream().anyMatch(c -> name.equals(c.getName()));
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
    // a relative URL (e.g. a value missing its "http://") must surface as the declared IOException, not the
    // IllegalArgumentException URI.toURL() throws for it
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
    // an idle timeout too long to pass to URLConnection (over ~24.8 days) must fail as an IOException
    String url = "ftp://example.com/x";
    Path file = tempDir.resolve("out.txt");
    IOException ex = assertThrows(IOException.class,
        () -> StreamUtils.copyUrlToFile(url, file, Duration.ofDays(30)));
    assertTrue(ex.getMessage() != null && ex.getMessage().contains(url), String.valueOf(ex.getMessage()));
  }

  @Test
  void testCopyUrlToFileWithNonPositiveIdleTimeoutThrowsIOException(@TempDir Path tempDir) {
    // a non-positive idle timeout would mean "no timeout" to HttpClient, so it must be rejected
    String url = "http://example.com/x";
    Path file = tempDir.resolve("out.txt");
    IOException ex = assertThrows(IOException.class, () -> StreamUtils.copyUrlToFile(url, file, Duration.ZERO));
    assertTrue(ex.getMessage() != null && ex.getMessage().contains(url), String.valueOf(ex.getMessage()));
  }

  @Test
  void testCopyUrlToFileWithSubMillisecondIdleTimeoutThrowsIOException(@TempDir Path tempDir) throws IOException {
    // a sub-millisecond idle timeout truncates to 0 ("no timeout"), so it must be rejected
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
  void testCopyUrlToFileWithHostlessUrlThrowsIOException(@TempDir Path tempDir) {
    // a hostless "http:///path" URI parses, but can't be requested - it must surface as the declared IOException
    String url = "http:///some/path";
    Path file = tempDir.resolve("out.txt");
    IOException ex = assertThrows(IOException.class, () -> StreamUtils.copyUrlToFile(url, file));
    assertTrue(ex.getMessage().contains(url), ex.getMessage());
  }

  @Test
  void testCopyUrlToFileWithMalformedRedirectLocationThrowsIOException(@TempDir Path tempDir) throws IOException {
    // a malformed (hostless) redirect Location must surface as the declared IOException
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
    // a connect failure must name the url - ConnectException's own message often doesn't
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
  void testCopyUrlToFileConnectFailureLeavesExistingFile(@TempDir Path tempDir) throws IOException {
    // a download that fails before any body arrives must not destroy an earlier copy of the file
    int port;
    try (ServerSocket socket = new ServerSocket(0)) {
      port = socket.getLocalPort();
    }
    Path file = tempDir.resolve("out.txt");
    Files.writeString(file, "previous download");
    assertThrows(IOException.class, () -> StreamUtils.copyUrlToFile("http://localhost:" + port + "/", file));
    assertEquals("previous download", Files.readString(file));
  }


  @Test
  void testCopyUrlToFileTimesOutOnStalledNonHttpConnection(@TempDir Path tempDir) throws IOException {
    // the non-HTTP (e.g. ftp) branch must honor the idle timeout - a server that accepts the connection
    // but never sends the initial FTP banner would otherwise hang the calling thread
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
    // a sub-millisecond idle timeout truncates to 0, which URLConnection treats as infinite - it must be
    // rejected rather than silently disable the timeout. assertTimeoutPreemptively because the failure mode is
    // a hang
    try (ServerSocket socket = new ServerSocket(0)) {
      String url = "ftp://localhost:" + socket.getLocalPort() + "/path";
      Path file = tempDir.resolve("out.txt");
      assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
          assertThrows(IOException.class,
              () -> StreamUtils.copyUrlToFile(url, file, Duration.ofNanos(500_000))));
    }
  }

  @Test
  void testCopyUrlToFileUsesTransferEncodingOverContentLength(@TempDir Path tempDir)
      throws IOException, InterruptedException {
    // RFC 9112 6.3: when both are sent, Transfer-Encoding overrides Content-Length - the whole chunked body must
    // be saved, not just the 5 bytes Content-Length claims
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
      StreamUtils.copyUrlToFile(url, file);
      assertEquals(realBody, Files.readString(file, StandardCharsets.US_ASCII));
      serverThread.join();
    }
  }

  @Test
  void testCopyUrlToFileRejectsMultipleDifferingContentLengthHeaders(@TempDir Path tempDir)
      throws IOException, InterruptedException {
    // differing Content-Length values (RFC 9110 8.6) are ambiguous framing and must fail
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
  void testCopyUrlToFileRejectsMultipleContentLengthHeadersWithSameValue(@TempDir Path tempDir)
      throws IOException, InterruptedException {
    // HttpClient rejects any repeated Content-Length, even one with the same value - stricter than RFC 9110
    // requires, but it fails loudly rather than guessing
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
      IOException ex = assertThrows(IOException.class, () -> StreamUtils.copyUrlToFile(url, file));
      assertTrue(ex.getMessage().contains("Content-Length"), ex.getMessage());
      serverThread.join();
    }
  }

  @Test
  void testCopyUrlToFileRejectsMalformedSecondContentLengthHeader(@TempDir Path tempDir)
      throws IOException, InterruptedException {
    // a valid Content-Length followed by a malformed one is ambiguous framing and must fail
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
      assertTrue(ex.getMessage() != null && ex.getMessage().contains("Content-Length"), String.valueOf(ex.getMessage()));
      serverThread.join();
    }
  }

  @Test
  void testCopyUrlToFileRejectsUnsupportedTransferEncoding(@TempDir Path tempDir)
      throws IOException, InterruptedException {
    // only "chunked" is supported as a transfer coding - anything else (e.g. a bare "gzip") must fail rather
    // than save the still-encoded bytes
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
  void testCopyUrlToFileRejectsEmptyTransferEncodingHeaderValue(@TempDir Path tempDir)
      throws IOException, InterruptedException {
    // an empty Transfer-Encoding isn't a legal value (RFC 9112 6.1 requires at least one coding), so it must fail
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
      IOException ex = assertThrows(IOException.class, () -> StreamUtils.copyUrlToFile(url, file));
      assertTrue(ex.getMessage().toLowerCase(Locale.ROOT).contains("transfer encoding"), ex.getMessage());
      serverThread.join();
    }
  }

  @Test
  void testCopyUrlToFileWithEmptyBodyAndUnsupportedTransferEncodingThrows(@TempDir Path tempDir)
      throws IOException, InterruptedException {
    // an unsupported Transfer-Encoding fails even when the body is empty
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
      IOException ex = assertThrows(IOException.class, () -> StreamUtils.copyUrlToFile(url, file));
      assertTrue(ex.getMessage().toLowerCase(Locale.ROOT).contains("transfer encoding"), ex.getMessage());
      serverThread.join();
    }
  }

  @Test
  void testCopyUrlToFileRejectsTransferEncodingChunkedWithParameter(@TempDir Path tempDir)
      throws IOException, InterruptedException {
    // "chunked" takes no parameters (RFC 9112 7.1) - a parameterized one must fail rather than save the raw
    // chunk framing
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
    // "identity, chunked" uses an unregistered coding, so it must fail rather than save the raw chunk framing
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
    // RFC 9112 7 forbids applying "chunked" more than once
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
  void testCopyUrlToFileRejectsTransferEncodingIdentity(@TempDir Path tempDir)
      throws IOException, InterruptedException {
    // "identity" is no longer a registered transfer coding (RFC 9112 7) - HttpClient rejects it
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
      IOException ex = assertThrows(IOException.class, () -> StreamUtils.copyUrlToFile(url, file));
      assertTrue(ex.getMessage().contains("identity"), ex.getMessage());
      serverThread.join();
    }
  }

  @Test
  void testCopyUrlToFileRejectsNonChunkedTransferEncodingCombinedWithContentLength(@TempDir Path tempDir)
      throws IOException, InterruptedException {
    // a parameterized chunked coding with a contradictory Content-Length must fail, not save a 0-byte file
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
      assertTrue(ex.getMessage() != null && ex.getMessage().toLowerCase(Locale.ROOT).contains("transfer encoding"), String.valueOf(ex.getMessage()));
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
    // a whitespace-padded URL (plausible from a spreadsheet/TSV column) must still take the HTTP branch -
    // asserting the 204 status pins that, since the parse-failure message would also contain the url
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
    // java.net.URL(String) tolerated a literal space in the path; java.net.URI does not, so it is encoded as
    // %20 before parsing
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
    // malformed UTF-8 must throw, naming the file, not be silently replaced with U+FFFD
    Path file = tempDir.resolve("malformed.txt");
    byte[] malformedUtf8 = { (byte)0xC3, (byte)0x28 }; // invalid UTF-8 sequence
    Files.write(file, malformedUtf8);

    try (BufferedReader reader = StreamUtils.openReader(file)) {
      java.nio.charset.MalformedInputException ex =
          assertThrows(java.nio.charset.MalformedInputException.class, reader::readLine);
      assertTrue(ex.getMessage().contains(file.toString()), ex.getMessage());
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
    // malformed UTF-8 must throw, naming the file, not be silently replaced with U+FFFD
    Path file = tempDir.resolve("malformed.txt.gz");
    byte[] malformedUtf8 = { (byte)0xC3, (byte)0x28 }; // invalid UTF-8 sequence
    try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
      out.write(malformedUtf8);
    }

    try (BufferedReader reader = StreamUtils.openReader(file)) {
      java.nio.charset.MalformedInputException ex =
          assertThrows(java.nio.charset.MalformedInputException.class, reader::readLine);
      assertTrue(ex.getMessage().contains(file.toString()), ex.getMessage());
    }
  }

  @Test
  void testZipReaderRejectsMalformedUtf8(@TempDir Path tempDir) throws IOException {
    // malformed UTF-8 must throw, naming the file, not be silently replaced with U+FFFD
    Path file = tempDir.resolve("malformed.txt.zip");
    byte[] malformedUtf8 = { (byte)0xC3, (byte)0x28 }; // invalid UTF-8 sequence
    try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file))) {
      out.putNextEntry(new ZipEntry("malformed.txt"));
      out.write(malformedUtf8);
      out.closeEntry();
    }

    try (BufferedReader reader = StreamUtils.openReader(file)) {
      java.nio.charset.MalformedInputException ex =
          assertThrows(java.nio.charset.MalformedInputException.class, reader::readLine);
      assertTrue(ex.getMessage().contains(file.toString()), ex.getMessage());
    }
  }

  @Test
  void testBadGzInputStreamThrows(@TempDir Path tempDir) throws IOException {
    // a file misnamed with a .gz extension should fail to parse as gzip, keeping the ZipException as the cause
    Path file = tempDir.resolve("notActuallyGzipped.gz");
    Files.writeString(file, "hello, world");

    IOException ex = assertThrows(IOException.class, () -> StreamUtils.openInputStream(file));
    assertTrue(ex.getMessage() != null && ex.getMessage().contains(file.toString()), String.valueOf(ex.getMessage()));
    assertInstanceOf(ZipException.class, ex.getCause());
  }

  @Test
  void testOpenInputStreamDoesNotLeakFileDescriptorOnMalformedGzipHeader(@TempDir Path tempDir) throws IOException {
    // a malformed gzip header must not leak the underlying file stream
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
    // a malformed gzip header must name the path, like openInputStream()'s other failures
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
    // ZipInputStream.getNextEntry() throws an unchecked IllegalArgumentException on JDK 21 (a ZipException on
    // JDK 25) for an entry name that's invalid UTF-8 with the UTF-8 (EFS) flag set - it must surface as the
    // declared IOException, naming the path
    Path file = tempDir.resolve("malformed.txt.zip");
    Files.write(file, buildLocalFileHeaderWithMalformedUtf8EntryName());
    IOException ex = assertThrows(IOException.class, () -> StreamUtils.openInputStream(file));
    assertTrue(ex.getMessage() != null && ex.getMessage().contains(file.toString()), String.valueOf(ex.getMessage()));
    assertTrue(ex.getCause() instanceof IllegalArgumentException || ex.getCause() instanceof ZipException,
        String.valueOf(ex.getCause()));
  }

  @Test
  void testOpenReaderMalformedZipEntryNameNamesThePath(@TempDir Path tempDir) throws IOException {
    // same gap as testOpenInputStreamMalformedZipEntryNameNamesThePath above, but for openReader()
    Path file = tempDir.resolve("malformed.txt.zip");
    Files.write(file, buildLocalFileHeaderWithMalformedUtf8EntryName());
    IOException ex = assertThrows(IOException.class, () -> StreamUtils.openReader(file));
    assertTrue(ex.getMessage() != null && ex.getMessage().contains(file.toString()), String.valueOf(ex.getMessage()));
    assertTrue(ex.getCause() instanceof IllegalArgumentException || ex.getCause() instanceof ZipException,
        String.valueOf(ex.getCause()));
  }

  /**
   * Hand-builds the bytes of a single zip local file header whose general-purpose flag declares UTF-8 (EFS,
   * bit 11) encoding, but whose entry name is not valid UTF-8 - triggering an {@link IllegalArgumentException}
   * from the JDK 21 zip entry-name decoder (a {@link ZipException} on JDK 25). Same layout as
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
}
