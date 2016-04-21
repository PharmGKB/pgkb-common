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
 * Junit test for {@link ChromosomePositionComparator}.
 *
 * @author Mark Woon
 */
public class ChromosomePositionComparatorTest {


  @Test
  public void testComparator() {

    assertEquals(0, ChromosomePositionComparator.getComparator().compare(null, null));
    assertEquals(-1, ChromosomePositionComparator.getComparator().compare(null, "chr1:1"));
    assertEquals(1, ChromosomePositionComparator.getComparator().compare("chr1:1", null));

    assertEquals(-1, ChromosomePositionComparator.getComparator().compare("chr1:4", "chr1:100"));
    assertEquals(0, ChromosomePositionComparator.getComparator().compare("chr1:4", "chr1:4"));
    assertEquals(1, ChromosomePositionComparator.getComparator().compare("chr3:4", "chr1:4"));
    assertEquals(1, ChromosomePositionComparator.getComparator().compare("chr4:100", "chr1:400"));
  }


  @Test(expected = IllegalArgumentException.class)
  public void testBadArg1() throws IllegalArgumentException {
    ChromosomePositionComparator.getComparator().compare("chr1", "chr1:100");
  }


  @Test(expected = IllegalArgumentException.class)
  public void testBadArg2() throws IllegalArgumentException {
    ChromosomePositionComparator.getComparator().compare("chr1:1", ":100");
  }
}
