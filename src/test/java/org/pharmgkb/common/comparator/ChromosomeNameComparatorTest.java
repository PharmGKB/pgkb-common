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
 * JUnit test for {@link ChromosomeNameComparator}.
 *
 * @author Mark Woon
 */
public class ChromosomeNameComparatorTest {


  @Test
  public void testComparator() {

    assertEquals(0, ChromosomeNameComparator.getComparator().compare(null, null));
    assertEquals(-1, ChromosomeNameComparator.getComparator().compare(null, "chr1"));
    assertEquals(1, ChromosomeNameComparator.getComparator().compare("chr1", null));

    assertEquals(0, ChromosomeNameComparator.getComparator().compare("chr1", "chr1"));
    assertEquals(-1, ChromosomeNameComparator.getComparator().compare("chr1", "chr3"));
    assertEquals(1, ChromosomeNameComparator.getComparator().compare("chr13", "chr1"));
    assertEquals(-1, ChromosomeNameComparator.getComparator().compare("chr13", "chr21"));
    assertEquals(-1, ChromosomeNameComparator.getComparator().compare("chr13", "chrX"));
    assertEquals(-1, ChromosomeNameComparator.getComparator().compare("chr20", "chrX"));
    assertEquals(-1, ChromosomeNameComparator.getComparator().compare("chrX", "chrY"));
    assertEquals(0, ChromosomeNameComparator.getComparator().compare("chrX", "chrX"));

    assertEquals(0, ChromosomeNameComparator.getComparator().compare("1", "1"));
    assertEquals(-1, ChromosomeNameComparator.getComparator().compare("1", "3"));
    assertEquals(1, ChromosomeNameComparator.getComparator().compare("13", "1"));
    assertEquals(-1, ChromosomeNameComparator.getComparator().compare("13", "21"));
    assertEquals(-1, ChromosomeNameComparator.getComparator().compare("13", "X"));
    assertEquals(-1, ChromosomeNameComparator.getComparator().compare("20", "X"));
    assertEquals(-1, ChromosomeNameComparator.getComparator().compare("X", "Y"));
    assertEquals(0, ChromosomeNameComparator.getComparator().compare("X", "X"));
  }
}
