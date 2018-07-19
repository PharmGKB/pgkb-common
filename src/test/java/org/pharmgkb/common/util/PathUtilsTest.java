/*
 ----- BEGIN LICENSE BLOCK -----
 This Source Code Form is subject to the terms of the Mozilla Public License, v.2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ----- END LICENSE BLOCK -----
 */
package org.pharmgkb.common.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import net.trajano.commons.testing.UtilityClassTestUtil;
import org.junit.Test;

import static org.junit.Assert.*;


/**
 * JUnit test for {@link PathUtils}.
 *
 * @author Mark Woon
 */
public class PathUtilsTest {


  @Test
  public void testUtilityClass() throws Exception {
    UtilityClassTestUtil.assertUtilityClassWellDefined(PathUtils.class);
  }


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
  }

  @Test(expected = IllegalArgumentException.class)
  public void testGetPathToResourceBad() {

    Path p = PathUtils.getPathToResource("org/pharmgkb/common/util/nonexistent.tsv");
    fail("Should not be able to find file");
  }
}
