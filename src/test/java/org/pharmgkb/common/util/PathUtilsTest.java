package org.pharmgkb.common.util;

import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


/**
 * JUnit test for {@link PathUtils}.
 *
 * @author Mark Woon
 */
class PathUtilsTest {


  @Test
  void testGetFilename() {

    assertEquals("foo.xml", PathUtils.getFilename(Paths.get("/a/dir/for/foo.xml")));
    assertEquals("foo.xml", PathUtils.getFilename(Paths.get("/a/dir/for/foo.xml")));
    assertEquals("foo", PathUtils.getFilename(Paths.get("/a/dir/for/foo")));
  }

  @Test
  void testGetFileExtension() {

    assertEquals("ai", PathUtils.getFileExtension(Paths.get("/a/dir/foo.ai")));
    assertEquals("asdfasdf", PathUtils.getFileExtension(Paths.get("/a/dir/foo.asdfasdf")));
    assertNull(PathUtils.getFileExtension(Paths.get("/a/dir/foo")));
    assertNull(PathUtils.getFileExtension(Paths.get("/a/dir/foo.")));
  }

  @Test
  void testGetBaseFilename() {

    assertEquals("foo", PathUtils.getBaseFilename(Paths.get("/a/dir/for/foo.xml")));
    assertEquals("foo", PathUtils.getBaseFilename(Paths.get("/a/dir/for/foo.xml")));
    assertEquals("foo", PathUtils.getBaseFilename(Paths.get("/a/dir/for/foo")));
    assertEquals("foo.bar", PathUtils.getBaseFilename(Paths.get("/a/dir/for/foo.bar.xml")));
  }


  @Test
  void testGetPathToResourceGood() {

    assertTrue(Files.exists(PathUtils.getPathToResource("org/pharmgkb/common/util/PathUtilsTest.txt")));
    assertTrue(Files.exists(PathUtils.getPathToResource("/org/pharmgkb/common/util/PathUtilsTest.txt")));
    assertTrue(Files.exists(PathUtils.getPathToResource(PathUtils.class, "PathUtilsTest.txt")));
  }

  @Test
  void testGetPathToResourceBad() {

    Assertions.assertThrows(IllegalArgumentException.class, () ->
        PathUtils.getPathToResource("org/pharmgkb/common/util/nonexistent.tsv"));
  }
}
