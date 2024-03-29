package org.pharmgkb.common.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.LaxRedirectStrategy;


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
      throw new NoSuchFileException("File does not exist");
    }
    if (!Files.isRegularFile(path)) {
      throw new NoSuchFileException("Path does not lead to a regular file");
    }
    String origFilename = path.getFileName().toString();
    String filename = origFilename.toLowerCase();
    if (filename.endsWith(".gz")) {
      return new GZIPInputStream(Files.newInputStream(path), 65536);
    } else if (filename.endsWith(".zip")) {
      origFilename = origFilename.substring(0, origFilename.length() - 4);
      return new ZippedFileInputStream(Files.newInputStream(path), origFilename);
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
   */
  public static BufferedReader openReader(Path path) throws IOException {

    if (!Files.exists(path)) {
      throw new NoSuchFileException("File does not exist");
    }
    if (!Files.isRegularFile(path)) {
      throw new NoSuchFileException("Path does not lead to a regular file");
    }
    String origFilename = path.getFileName().toString();
    String filename = origFilename.toLowerCase();
    if (filename.endsWith(".gz")) {
      return new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(path), 65536)));
    } else if (filename.endsWith(".zip")) {
      origFilename = origFilename.substring(0, origFilename.length() - 4);
      return new BufferedReader(new InputStreamReader(new ZippedFileInputStream(Files.newInputStream(path), origFilename)));
    } else {
      return Files.newBufferedReader(path);
    }
  }


  /**
   * Copies contents of a {@code url} to a {@code file}.  If {@code file} already exists, it will be overwritten.
   * <p>
   * Use this instead of {@link FileUtils#copyURLToFile(URL, File)} or {@link IOUtils#copy(URL, File)}
   * when you need to follow redirects.
   */
  public static void copyUrlToFile(String url, Path file) throws IOException {

    if (url.startsWith("http://") || url.startsWith("https://")) {
      try (CloseableHttpClient httpClient = HttpClientBuilder.create().setRedirectStrategy(new LaxRedirectStrategy()).build()) {
        HttpGet httpget = new HttpGet(url);
        try (CloseableHttpResponse response = httpClient.execute(httpget)) {
          // save to file even if there's an error, so we can see what the error is
          try (InputStream in = response.getEntity().getContent();
               OutputStream out = Files.newOutputStream(file)) {
            IOUtils.copy(in, out);
          }
          if (response.getStatusLine().getStatusCode() != 200) {
            throw new IOException("Error downloading " + url + ": " + response.getStatusLine());
          }
        }
      }
    } else {
      URL ftpUrl = new URL(url);
      URLConnection conn = ftpUrl.openConnection();
      try (InputStream in = conn.getInputStream();
           OutputStream out = Files.newOutputStream(file)) {
        IOUtils.copy(in,out);
      }
    }
  }


  /**
   * Calculates md5 hash for a file.
   */
  public static byte[] md5(Path file) throws IOException {
    try (InputStream is = Files.newInputStream(file)) {
      return DigestUtils.md5(is);
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
  public static String md5InBase64(byte[] data) throws IOException {
    return Base64.getEncoder().encodeToString(DigestUtils.md5(data));
  }
}
