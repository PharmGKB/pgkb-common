package org.pharmgkb.common.util;

import org.apache.commons.beanutils.ConversionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


/**
 * This is a JUnit test for {@link ExtendedEnumConverter}.
 *
 * @author Mark Woon
 */
class ExtendedEnumConverterTest {


  @Test
  void testConversion() {

    assertEquals(Hero.Batman, ExtendedEnumConverter.getConverter().convert(Hero.class, 3));
    assertEquals(Hero.Batman, ExtendedEnumConverter.getConverter().convert(Hero.class, "Bruce"));

    assertEquals(Hero.Superman, ExtendedEnumConverter.getConverter().convert(Hero.class, 2));
    assertEquals(Hero.Superman, ExtendedEnumConverter.getConverter().convert(Hero.class, "Clark Kent"));

    assertEquals(Hero.WonderWoman, ExtendedEnumConverter.getConverter().convert(Hero.class, "Diana"));
    assertEquals(Hero.WonderWoman, ExtendedEnumConverter.getConverter().convert(Hero.class, "Diana Prince"));

    assertNull(ExtendedEnumConverter.getConverter().convert(Hero.class, 0));
    assertNull(ExtendedEnumConverter.getConverter().convert(Hero.class, 4));
    assertNull(ExtendedEnumConverter.getConverter().convert(Hero.class, "Beast"));
    assertNull(ExtendedEnumConverter.getConverter().convert(Hero.class, ""));
    assertNull(ExtendedEnumConverter.getConverter().convert(Hero.class, null));

    assertThrows(ConversionException.class, () -> ExtendedEnumConverter.getConverter().convert(Hero.class, Hero.class));
  }


  @Test
  void testNonExtendedEnum() {
    assertThrows(ConversionException.class, () -> ExtendedEnumConverter.getConverter().convert(getClass(), 0));
  }
}
