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
  void compareWithNullSortsBeforeNonNull() {
    assertEquals(-1, ComparatorUtils.compare(null, "a"));
    assertEquals(1, ComparatorUtils.compare("a", null));
    assertEquals(0, ComparatorUtils.compare(null, null));
  }
}
