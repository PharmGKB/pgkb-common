package org.pharmgkb.common.util;

import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;

import static org.junit.Assert.*;


/**
 * JUnit test for {@link PathUtils}.
 *
 * @author Mark Woon
 */
public class PathUtilsTest {


  @Test
  public void testGetFilename() {

    assertEquals("foo.xml", PathUtils.getFilename(Paths.get("/a/dir/for/foo.xml")));
    assertEquals("foo.xml", PathUtils.getFilename(Paths.get("/a/dir/for/foo.xml")));
    assertEquals("foo", PathUtils.getFilename(Paths.get("/a/dir/for/foo")));
  }

  @Test
  public void testGetFileExtension() {

    assertEquals("ai", PathUtils.getFileExtension(Paths.get("/a/dir/foo.ai")));
    assertEquals("asdfasdf", PathUtils.getFileExtension(Paths.get("/a/dir/foo.asdfasdf")));
    assertNull(PathUtils.getFileExtension(Paths.get("/a/dir/foo")));
    assertNull(PathUtils.getFileExtension(Paths.get("/a/dir/foo.")));
  }

  @Test
  public void testGetBaseFilename() {

    assertEquals("foo", PathUtils.getBaseFilename(Paths.get("/a/dir/for/foo.xml")));
    assertEquals("foo", PathUtils.getBaseFilename(Paths.get("/a/dir/for/foo.xml")));
    assertEquals("foo", PathUtils.getBaseFilename(Paths.get("/a/dir/for/foo")));
    assertEquals("foo.bar", PathUtils.getBaseFilename(Paths.get("/a/dir/for/foo.bar.xml")));
  }


  @Test
  public void testGetPathToResourceGood() {

    assertTrue(Files.exists(PathUtils.getPathToResource("org/pharmgkb/common/util/PathUtilsTest.txt")));
    assertTrue(Files.exists(PathUtils.getPathToResource("/org/pharmgkb/common/util/PathUtilsTest.txt")));
    assertTrue(Files.exists(PathUtils.getPathToResource(PathUtils.class, "PathUtilsTest.txt")));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testGetPathToResourceBad() {

    PathUtils.getPathToResource("org/pharmgkb/common/util/nonexistent.tsv");
    fail("Should not be able to find file");
  }
}
