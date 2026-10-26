package org.pharmgkb.common.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FilterReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.util.Timeout;


/**
 * This class contains useful stream convenience functions.
 *
 * @author Mark Woon
 */
public class StreamUtils {

  /**
   * Static class.
   */
  private StreamUtils() {
  }


  /**
   * Opens an {@link InputStream} to the specified file.
   * Automatically unwraps .gz or .zip files.
   * <p>
   * In zip files, this will look for a file minus the ".zip" extension (i.e. expect file named {@code foo.txt} if
   * zipped filename is {@code foo.txt.zip}.
   */
  public static InputStream openInputStream(Path path) throws IOException {

    if (!Files.exists(path)) {
      // the 1-arg constructor takes the FILE, not a message - using it here would drop the actual path
      throw new NoSuchFileException(path.toString(), null, "File does not exist");
    }
    if (!Files.isRegularFile(path)) {
      throw new NoSuchFileException(path.toString(), null, "Path does not lead to a regular file");
    }
    String origFilename = PathUtils.getFilename(path);
    String filename = origFilename.toLowerCase(Locale.ROOT);
    if (filename.endsWith(".gz")) {
      InputStream in = Files.newInputStream(path);
      try {
        return new GZIPInputStream(in, 65536);
      } catch (IOException ex) {
        // e.g. not a gzip file - close without letting a close() failure replace that diagnosis
        try {
          in.close();
        } catch (IOException closeEx) {
          ex.addSuppressed(closeEx);
        }
        throw new IOException("Error opening " + path, ex);
      }
    } else if (filename.endsWith(".zip")) {
      origFilename = origFilename.substring(0, origFilename.length() - 4);
      try {
        return new ZippedFileInputStream(Files.newInputStream(path), origFilename);
      } catch (IOException | RuntimeException ex) {
        // ZippedFileInputStream closes the underlying stream on failure; it can also throw an unchecked
        // IllegalArgumentException for an entry name that isn't valid UTF-8
        throw new IOException("Error opening " + path, ex);
      }
    } else {
      return Files.newInputStream(path);
    }
  }

  /**
   * Opens an {@link Reader} to the specified file.
   * Automatically unwraps .gz or .zip files.
   * <p>
   * In zip files, this will look for a file minus the ".zip" extension (i.e. expect file named {@code foo.txt} if
   * zipped filename is {@code foo.txt.zip}.
   * <p>
   * The file's contents are decoded strictly as UTF-8: a byte sequence that isn't valid UTF-8 throws
   * {@link MalformedInputException} (an {@link IOException} subclass) from a read call on the returned
   * {@link BufferedReader}, with a message naming the file, rather than being silently replaced with the Unicode
   * replacement character.
   */
  public static BufferedReader openReader(Path path) throws IOException {
    // a CharsetDecoder (not a bare Charset) is what makes InputStreamReader report malformed input
    Reader reader = new InputStreamReader(openInputStream(path), StandardCharsets.UTF_8.newDecoder());
    return new BufferedReader(new MalformedInputNamingReader(reader, path));
  }

  /**
   * Rethrows a {@link MalformedInputException} with a message that names the file being read.
   */
  private static class MalformedInputNamingReader extends FilterReader {
    private final Path m_path;

    MalformedInputNamingReader(Reader in, Path path) {
      super(in);
      m_path = path;
    }

    @Override
    public int read() throws IOException {
      try {
        return super.read();
      } catch (MalformedInputException ex) {
        throw new NamedMalformedInputException(ex, m_path);
      }
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
      try {
        return super.read(cbuf, off, len);
      } catch (MalformedInputException ex) {
        throw new NamedMalformedInputException(ex, m_path);
      }
    }
  }

  /**
   * A {@link MalformedInputException} whose message names the file being read.
   */
  private static class NamedMalformedInputException extends MalformedInputException {
    private final String m_message;

    NamedMalformedInputException(MalformedInputException cause, Path path) {
      super(cause.getInputLength());
      m_message = "Invalid UTF-8 in " + path + " (" + cause.getMessage() + ")";
      initCause(cause);
    }

    @Override
    public String getMessage() {
      return m_message;
    }
  }


  /**
   * Copies contents of a {@code url} to a {@code file}.  If {@code file} already exists, it will be overwritten.
   * <p>
   * Use this instead of {@link FileUtils#copyURLToFile(URL, File)} or {@link IOUtils#copy(URL, File)}
   * when you need to follow redirects.
   * <p>
   * For http/https URLs, redirects are followed (carrying cookies across them), and a compressed response is
   * decoded as it is saved. The response body is saved even if the status isn't 200, so the error can be
   * inspected, but an {@link IOException} is still thrown.
   * <p>
   * Bounded by an idle timeout (no data received for 30 seconds), not a total-time deadline: a download that
   * keeps making progress can take as long as it needs.
   */
  public static void copyUrlToFile(String url, Path file) throws IOException {
    copyUrlToFile(url, file, Duration.ofSeconds(30));
  }

  /**
   * Package-private overload so tests can use a short idle timeout instead of waiting out the real 30s default.
   */
  static void copyUrlToFile(String url, Path file, Duration idleTimeout) throws IOException {

    URI uri;
    URL parsedUrl;
    try {
      // tolerates surrounding whitespace and an unencoded space, as the deprecated URL(String) constructor did
      uri = new URI(url.strip().replace(" ", "%20"));
      parsedUrl = uri.toURL();
    } catch (URISyntaxException | MalformedURLException | IllegalArgumentException ex) {
      // URI.toURL() throws IllegalArgumentException for a relative URI (e.g. one missing its "http://")
      throw new IOException("Malformed URL: " + url, ex);
    }
    long idleMillis = idleTimeout.toMillis();
    // both HttpClient and URLConnection treat a timeout of 0 as "no timeout"
    if (idleMillis <= 0 || idleMillis > Integer.MAX_VALUE) {
      throw new IOException("Error downloading " + url,
          new IllegalArgumentException("Unsupported idle timeout: " + idleTimeout));
    }

    int status;
    try {
      if ("http".equals(parsedUrl.getProtocol()) || "https".equals(parsedUrl.getProtocol())) {
        status = copyHttpToFile(uri, file, Timeout.ofMilliseconds(idleMillis));
      } else {
        URLConnection conn = parsedUrl.openConnection();
        conn.setConnectTimeout((int)idleMillis);
        conn.setReadTimeout((int)idleMillis);
        try (InputStream in = conn.getInputStream()) {
          copyToFile(in, file);
        }
        return;
      }
    } catch (IOException | RuntimeException ex) {
      // name the url, and surface the underlying problem in the message itself - the exception that describes
      // it is often nested below one that says little (e.g. ClientProtocolException wrapping HttpException)
      throw new IOException("Error downloading " + url + describeCause(ex), ex);
    }
    if (status != 200) {
      throw new IOException("Error downloading " + url + ": " + status);
    }
  }

  /**
   * Returns ": " followed by the first non-blank message in {@code ex}'s cause chain, or "" if there isn't one.
   */
  private static String describeCause(Throwable ex) {
    for (Throwable t = ex; t != null; t = t.getCause()) {
      if (t.getMessage() != null && !t.getMessage().isBlank()) {
        return ": " + t.getMessage();
      }
    }
    return "";
  }

  /**
   * Downloads {@code uri} to {@code file}, saving the body regardless of status.
   *
   * @return the response's status code
   */
  private static int copyHttpToFile(URI uri, Path file, Timeout idleTimeout) throws IOException {
    ConnectionConfig connectionConfig = ConnectionConfig.custom()
        .setConnectTimeout(idleTimeout)
        .setSocketTimeout(idleTimeout)
        .build();
    try (CloseableHttpClient client = HttpClients.custom()
        .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
            .setDefaultConnectionConfig(connectionConfig)
            .build())
        .setDefaultRequestConfig(RequestConfig.custom()
            .setResponseTimeout(idleTimeout)
            .build())
        // HttpClient only decodes the first Content-Encoding header line, so one split across several lines
        // would otherwise be saved still encoded, as if it had succeeded
        .addResponseInterceptorFirst((response, entity, context) -> {
          if (response.countHeaders(HttpHeaders.CONTENT_ENCODING) > 1) {
            throw new ProtocolException("Multiple Content-Encoding headers");
          }
        })
        .build()) {
      return client.execute(new HttpGet(uri), response -> {
        HttpEntity entity = response.getEntity();
        try (InputStream in = entity == null ? InputStream.nullInputStream() : entity.getContent()) {
          copyToFile(in, file);
        }
        return response.getCode();
      });
    }
  }

  /**
   * Copies {@code in} to {@code file}, deleting {@code file} if the copy fails part way (e.g. a timeout or a
   * corrupt compressed body), so a truncated download can't be mistaken for a complete one.
   */
  private static void copyToFile(InputStream in, Path file) throws IOException {
    // opened outside the try below: if opening fails, an existing file is left as it was
    OutputStream out = Files.newOutputStream(file);
    try (out) {
      IOUtils.copy(in, out);
    } catch (IOException | RuntimeException ex) {
      try {
        Files.deleteIfExists(file);
      } catch (IOException deleteEx) {
        ex.addSuppressed(deleteEx);
      }
      throw ex;
    }
  }


  /**
   * Calculates md5 hash for a file.
   */
  public static byte[] md5(Path file) throws IOException {
    try (InputStream is = Files.newInputStream(file)) {
      return DigestUtils.md5(is);
    } catch (IOException ex) {
      // e.g. a directory - Files.newInputStream() throws a bare "Is a directory" that doesn't name the path
      throw new IOException("Error reading " + file, ex);
    }
  }

  /**
   * Calculates md5 hash for a file and return its Base64 representation.
   */
  public static String md5InBase64(Path file) throws IOException {
    return Base64.getEncoder().encodeToString(md5(file));
  }

  /**
   * Calculates md5 hash for the {@code data} and return its Base64 representation.
   */
  public static String md5InBase64(byte[] data) {
    return Base64.getEncoder().encodeToString(DigestUtils.md5(data));
  }
}
