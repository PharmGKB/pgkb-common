package org.pharmgkb.common.util;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * This is a JUnit test for {@link ZippedFileInputStream}.
 *
 * @author Mark Woon
 */
class ZippedFileInputStreamTest {
  private static final String sf_zipFilename = "ZippedFileInputStreamTest.txt.zip";
  private static final String sf_filename = "ZippedFileInputStreamTest.txt";


  private Path getPathToTestFile() {
    return PathUtils.getPathToResource(ZippedFileInputStreamTest.class, sf_zipFilename);
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
  void testUppercaseExtensionIsLocaleIndependent(@TempDir Path tempDir) throws Exception {
    // the ".zip" extension check must not depend on the JVM's default locale - under Turkish/Azeri locales,
    // "ZIP".toLowerCase() produces "zıp" (dotless i), not "zip"
    Locale defaultLocale = Locale.getDefault();
    try {
      Locale.setDefault(new Locale("tr", "TR"));
      Path upperCaseZip = tempDir.resolve("test.ZIP");
      Files.copy(getPathToTestFile(), upperCaseZip);

      try (ZippedFileInputStream zfIs = new ZippedFileInputStream(upperCaseZip, sf_filename)) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(zfIs));
        assertEquals("hello, world", reader.readLine());
      }
    } finally {
      Locale.setDefault(defaultLocale);
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


  @Test
  void closesUnderlyingStreamWhenFileNotFound() throws Exception {

    boolean[] closed = { false };
    try (InputStream raw = getClass().getResourceAsStream(sf_zipFilename)) {
      InputStream tracked = new FilterInputStream(raw) {
        @Override
        public void close() throws IOException {
          closed[0] = true;
          super.close();
        }
      };
      assertThrows(FileNotFoundException.class, () -> new ZippedFileInputStream(tracked, "does-not-exist.txt"));
    }
    assertTrue(closed[0]);
  }


  @Test
  void preservesOriginalExceptionWhenCloseAlsoFails() throws Exception {

    try (InputStream raw = getClass().getResourceAsStream(sf_zipFilename)) {
      InputStream tracked = new FilterInputStream(raw) {
        @Override
        public void close() throws IOException {
          throw new IOException("close failed");
        }
      };
      FileNotFoundException ex = assertThrows(FileNotFoundException.class,
          () -> new ZippedFileInputStream(tracked, "does-not-exist.txt"));
      assertEquals(1, ex.getSuppressed().length);
      assertEquals("close failed", ex.getSuppressed()[0].getMessage());
    }
  }


  @Test
  void closesUnderlyingStreamWhenEntryNameIsMalformed() throws Exception {
    // ZipInputStream.getNextEntry() throws an unchecked IllegalArgumentException (not IOException) for an
    // entry name that's invalid UTF-8 with the UTF-8 (EFS) flag set - that must still close the underlying
    // stream, not just the checked-IOException case
    byte[] malformedZip = buildLocalFileHeaderWithMalformedUtf8Name();
    boolean[] closed = { false };
    InputStream tracked = new FilterInputStream(new java.io.ByteArrayInputStream(malformedZip)) {
      @Override
      public void close() throws IOException {
        closed[0] = true;
        super.close();
      }
    };
    assertThrows(IllegalArgumentException.class, () -> new ZippedFileInputStream(tracked, "irrelevant.txt"));
    assertTrue(closed[0]);
  }

  /**
   * Hand-builds the bytes of a single zip local file header whose general-purpose flag declares UTF-8 (EFS,
   * bit 11) encoding, but whose entry name is not valid UTF-8 - triggering an {@link IllegalArgumentException}
   * from the JDK's zip entry-name decoder.
   */
  private static byte[] buildLocalFileHeaderWithMalformedUtf8Name() throws IOException {
    byte[] nameBytes = { (byte)0xC3, (byte)0x28 }; // invalid UTF-8 sequence
    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
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
  void constructorForNamelessPathHasInformativeMessage() {
    // zipFile.getName(zipFile.getNameCount() - 1) for a nameless path (e.g. root, "/") passes -1 to
    // Path.getName(), which throws IllegalArgumentException with a bare null message - PathUtils.getFilename()
    // guards this explicitly instead, naming the actual path
    Path root = Path.of("/");
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> new ZippedFileInputStream(root));
    assertEquals("Path has no filename: '" + root + "'", ex.getMessage());
  }
}
