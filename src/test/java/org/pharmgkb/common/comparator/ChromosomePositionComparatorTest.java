package org.pharmgkb.common.comparator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * Junit test for {@link ChromosomePositionComparator}.
 *
 * @author Mark Woon
 */
class ChromosomePositionComparatorTest {


  @Test
  void testComparator() {

    //noinspection EqualsWithItself
    assertEquals(0, ChromosomePositionComparator.getComparator().compare(null, null));
    assertEquals(-1, ChromosomePositionComparator.getComparator().compare(null, "chr1:1"));
    assertEquals(1, ChromosomePositionComparator.getComparator().compare("chr1:1", null));

    assertEquals(-1, ChromosomePositionComparator.getComparator().compare("chr1:4", "chr1:100"));
    //noinspection EqualsWithItself
    assertEquals(0, ChromosomePositionComparator.getComparator().compare("chr1:4", "chr1:4"));
    assertEquals(1, ChromosomePositionComparator.getComparator().compare("chr3:4", "chr1:4"));
    assertEquals(1, ChromosomePositionComparator.getComparator().compare("chr4:100", "chr1:400"));
  }


  @Test
  void testBadArg1() throws IllegalArgumentException {
    Assertions.assertThrows(IllegalArgumentException.class, () -> {
      ChromosomePositionComparator.getComparator().compare("chr1", "chr1:100");
    });
  }


  @Test
  void testBadArg2() throws IllegalArgumentException {
    Assertions.assertThrows(IllegalArgumentException.class, () -> {
      ChromosomePositionComparator.getComparator().compare("chr1:1", ":100");
    });
  }
}
