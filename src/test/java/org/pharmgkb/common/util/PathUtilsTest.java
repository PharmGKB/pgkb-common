/*
 ----- BEGIN LICENSE BLOCK -----
 This Source Code Form is subject to the terms of the Mozilla Public License, v.2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ----- END LICENSE BLOCK -----
 */
package org.pharmgkb.common.util;

import java.nio.file.Paths;
import org.junit.Test;

import static org.junit.Assert.assertEquals;


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
  public void testGetBaseFilename() {

    assertEquals("foo", PathUtils.getBaseFilename(Paths.get("/a/dir/for/foo.xml")));
    assertEquals("foo", PathUtils.getBaseFilename(Paths.get("/a/dir/for/foo.xml")));
    assertEquals("foo", PathUtils.getBaseFilename(Paths.get("/a/dir/for/foo")));
  }
}
