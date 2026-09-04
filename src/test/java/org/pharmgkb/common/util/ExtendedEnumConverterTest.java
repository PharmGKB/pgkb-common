package org.pharmgkb.common.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.atomic.DoubleAdder;
import org.apache.commons.beanutils.ConversionException;
import org.jspecify.annotations.NonNull;
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


  @Test
  void testConvertRejectsOutOfRangeLong() {
    // a Long id that doesn't fit in an int must fail loudly, not silently truncate (e.g. via intValue())
    // into the id of some unrelated enum constant - surfaced as the documented ConversionException, not a
    // raw ArithmeticException
    ConversionException ex = assertThrows(ConversionException.class,
        () -> ExtendedEnumConverter.getConverter().convert(Hero.class, 5000000000L));
    assertInstanceOf(ArithmeticException.class, ex.getCause());
  }


  @Test
  void testConvertRejectsFractionalNumber() {
    // Hero id 1 is WonderWoman - a fractional Number must fail loudly, not silently truncate (e.g. via
    // longValue()) into the id of some other, unrelated but valid enum constant
    assertThrows(ConversionException.class,
        () -> ExtendedEnumConverter.getConverter().convert(Hero.class, new BigDecimal("1.9")));
    assertThrows(ConversionException.class,
        () -> ExtendedEnumConverter.getConverter().convert(Hero.class, 1.9));
    assertThrows(ConversionException.class,
        () -> ExtendedEnumConverter.getConverter().convert(Hero.class, 1.9f));
  }


  @Test
  void testConvertRejectsOutOfRangeBigInteger() {
    // a BigInteger can exceed 64 bits, so routing it through longValue() before an int-range check would
    // itself silently wrap (e.g. 2^64+1 -> 1) into the id of some other, unrelated but valid enum constant
    BigInteger huge = BigInteger.valueOf(2).pow(64).add(BigInteger.ONE);
    assertThrows(ConversionException.class, () -> ExtendedEnumConverter.getConverter().convert(Hero.class, huge));
  }


  @Test
  void testConvertRejectsHighPrecisionFractionalBigDecimal() {
    // Hero id 1 is WonderWoman - a BigDecimal fractional check done via doubleValue() would round this to
    // exactly 1.0 (its fractional part is far below double's precision), wrongly passing validation and then
    // truncating to id 1
    BigDecimal barelyFractional = new BigDecimal("1.0000000000000000001");
    assertThrows(ConversionException.class,
        () -> ExtendedEnumConverter.getConverter().convert(Hero.class, barelyFractional));
  }


  @Test
  void testConvertRejectsFractionalNumberOfOtherTypes() {
    // Hero id 1 is WonderWoman - the fractional check must not be limited to just Double/Float; any other
    // fractional Number subtype (e.g. DoubleAdder) must be rejected too, not silently truncated via longValue()
    DoubleAdder adder = new DoubleAdder();
    adder.add(1.5);
    assertThrows(ConversionException.class, () -> ExtendedEnumConverter.getConverter().convert(Hero.class, adder));
  }


  @Test
  void testConvertWrapsUnregisteredExtendedEnumIllegalStateException() {
    // ExtendedEnumHelper.getExtendedEnumHelper() throws IllegalStateException for an ExtendedEnum class that
    // was never registered via add() - convert()'s otherwise-uniform ConversionException failure contract
    // must not let that escape unwrapped
    assertThrows(ConversionException.class,
        () -> ExtendedEnumConverter.getConverter().convert(Unregistered.class, 1));
  }


  private enum Unregistered implements ExtendedEnum {
    ONE;

    @Override
    public int getId() {
      return 1;
    }

    @Override
    public @NonNull String getShortName() {
      return "one";
    }

    @Override
    public @NonNull String getDisplayName() {
      return "One";
    }
  }
}
