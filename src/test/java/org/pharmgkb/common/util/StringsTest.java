package org.pharmgkb.common.util;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


/**
 * This is a JUnit test for {@link Strings}.
 *
 * @author Mark Woon
 */
class StringsTest {

  @Test
  void testStripToEmpty() {
    String text = "\u00A0\u00A0\u00A0foo\u00A0\u00A0\u00A0";

    assertEquals(9, text.length());
    assertEquals(9, StringUtils.stripToEmpty(text).length());
    assertEquals(3, Strings.stripToEmpty(text).length());
  }


  @Test
  void testStripToNull() {
    String text = "\u00A0\u00A0\u00A0";

    assertEquals(3, text.length());
    String stringUtilsRez = StringUtils.stripToNull(text);
    assertNotNull(stringUtilsRez);
    assertEquals(3, stringUtilsRez.length());
    assertNull(Strings.stripToNull(text));
  }
}
