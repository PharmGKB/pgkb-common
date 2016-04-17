/*
 ----- BEGIN LICENSE BLOCK -----
 This Source Code Form is subject to the terms of the Mozilla Public License, v.2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ----- END LICENSE BLOCK -----
 */
package org.pharmgkb.common.comparator;

import org.junit.Test;

import static org.junit.Assert.assertEquals;


/**
 * JUnit test for {@link HaplotypeNameComparator}.
 *
 * @author Mark Woon
 */
public class HaplotypeNameComparatorTest {


  @Test
  public void testComparator() {

    assertEquals(0, HaplotypeNameComparator.getComparator().compare(null, null));
    assertEquals(-1, HaplotypeNameComparator.getComparator().compare(null, "*1"));
    assertEquals(1, HaplotypeNameComparator.getComparator().compare("*1", null));

    assertEquals(0, HaplotypeNameComparator.getComparator().compare("*1", "*1"));
    assertEquals(-1, HaplotypeNameComparator.getComparator().compare("*1", "*11"));
    assertEquals(-1, HaplotypeNameComparator.getComparator().compare("*2", "*11"));
    assertEquals(1, HaplotypeNameComparator.getComparator().compare("*13", "*3"));
    assertEquals(1, HaplotypeNameComparator.getComparator().compare("H3", "H2"));
  }
}
