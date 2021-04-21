package org.pharmgkb.common.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * This is a JUnit test for {@link ZippedFileInputStream}.
 *
 * @author Mark Woon
 */
class ZippedFileInputStreamTest {
  private static final String sf_zipFilename = "ZippedFileInputStreamTest.txt.zip";
  private static final String sf_filename = "ZippedFileInputStreamTest.txt";


  private Path getPathToTestFile() throws URISyntaxException {
    URL url = getClass().getResource(sf_zipFilename);
    return Paths.get(url.toURI());
  }


  @Test
  void defaultConstructor() throws Exception {

    try (ZippedFileInputStream zfIs = new ZippedFileInputStream(getPathToTestFile())) {
      BufferedReader reader = new BufferedReader(new InputStreamReader(zfIs));
      String line = reader.readLine();
      assertEquals("hello, world", line);
    }
  }


  @Test
  void readFromPath() throws Exception {

    try (ZippedFileInputStream zfIs = new ZippedFileInputStream(getPathToTestFile(), sf_filename)) {
      BufferedReader reader = new BufferedReader(new InputStreamReader(zfIs));
      String line = reader.readLine();
      assertEquals("hello, world", line);
    }
  }


  @Test
  void readFromPlainInputStream() throws Exception {

    try (InputStream in = getClass().getResourceAsStream(sf_zipFilename)) {
      ZippedFileInputStream zfIs = new ZippedFileInputStream(in, sf_filename);
      BufferedReader reader = new BufferedReader(new InputStreamReader(zfIs));
      String line = reader.readLine();
      assertEquals("hello, world", line);
    }
  }


  @Test
  void readFromZipInputStream() throws Exception {

    try (InputStream in = getClass().getResourceAsStream(sf_zipFilename)) {
      ZippedFileInputStream zfIs = new ZippedFileInputStream(new ZipInputStream(in), sf_filename);
      BufferedReader reader = new BufferedReader(new InputStreamReader(zfIs));
      String line = reader.readLine();
      assertEquals("hello, world", line);
    }
  }
}
