package org.pharmgkb.common.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.WriterOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * This is a JUnit test for {@link StreamUtils}.
 *
 * @author Mark Woon
 */
class StreamUtilsTest {

  @Test
  void testCopyUrlToFile() throws IOException {
    Path file = Files.createTempFile("testCopyUrlToFile", ".txt");
    StreamUtils.copyUrlToFile("https://gist.githubusercontent.com/pharmgen/6d1a49c8807fe6f9d6cc9656a1922330/raw/685b330643ff60a87cdcdd8d749f1f4d5f5d1101/test.txt", file);

    StringWriter writer = new StringWriter();
    try (BufferedReader reader = StreamUtils.openReader(file)) {
      IOUtils.copy(reader, writer);
    }
    assertEquals("hello, world", writer.toString());
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
}
