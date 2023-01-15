package org.pharmgkb.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * This is a JUnit test for {@link ComparatorUtils}.
 *
 * @author Mark Woon
 */
class ComparatorUtilsTest {


  @Test
  void testCompareNumber() {

    assertEquals(-1, ComparatorUtils.compareNumbers("1", "2"));
    assertEquals(1, ComparatorUtils.compareNumbers("11", "2"));
    assertEquals(-1, ComparatorUtils.compareNumbers("1.2", "2"));
    assertEquals(-1, ComparatorUtils.compareNumbers("1.2", "2.3"));
    assertEquals(-1, ComparatorUtils.compareNumbers("1.2", "2.0"));
    assertEquals(-1, ComparatorUtils.compareNumbers("2", "11"));
    assertEquals(1, ComparatorUtils.compareNumbers("2", "1.2"));
    assertEquals(1, ComparatorUtils.compareNumbers("2.3", "1.2"));
    assertEquals(1, ComparatorUtils.compareNumbers("2.0", "1.2"));
  }
}
