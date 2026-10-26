package org.pharmgkb.common.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.Converter;
import org.jspecify.annotations.Nullable;


/**
 * This is a BeanUtils {@link Converter} for {@link ExtendedEnum}s.
 *
 * @author Mark Woon
 */
public class ExtendedEnumConverter implements Converter {
  private static final ExtendedEnumConverter sf_converter = new ExtendedEnumConverter();


  public static ExtendedEnumConverter getConverter() {
    return sf_converter;
  }


  @Override
  public @Nullable <T> T convert(Class<T> aClass, @Nullable Object o) {

    if (o == null) {
      return null;
    }
    if (ExtendedEnum.class.isAssignableFrom(aClass)) {
      ExtendedEnumHelper helper;
      try {
        //noinspection unchecked
        helper = ExtendedEnumHelper.getExtendedEnumHelper((Class<? extends ExtendedEnum>)aClass);
      } catch (IllegalStateException ex) {
        // keep this method's failure contract uniform - every other failure path below throws
        // ConversionException, so an unregistered ExtendedEnum class shouldn't be the one exception that
        // instead lets ExtendedEnumHelper's own IllegalStateException escape unwrapped
        throw new ConversionException("No ExtendedEnumHelper registered for " + aClass, ex);
      }
      if (o instanceof String) {
        return aClass.cast(helper.fromString((String)o));

      } else if (o instanceof Number) {
        // intValue() would silently truncate a Number that doesn't fit in an int (e.g. a Long id) into the
        // id of some unrelated enum constant - toIntExact() throws instead
        Number number = (Number)o;
        try {
          int id;
          if (number instanceof BigInteger) {
            // a BigInteger can exceed 64 bits, so routing it through longValue() (like the other branches
            // below) would itself silently wrap - intValueExact() checks the int range directly, with no
            // lossy intermediate step
            id = ((BigInteger)number).intValueExact();
          } else if (number instanceof BigDecimal) {
            // intValueExact() rejects both non-integral values and out-of-range values exactly; checking
            // via doubleValue() instead (like the Double/Float branch below) can itself lose precision for
            // a high-scale BigDecimal, e.g. "1.0000000000000000001" rounds to exactly 1.0 as a double
            id = ((BigDecimal)number).intValueExact();
          } else {
            // longValue() on a fractional Number silently truncates too (e.g. 1.9 -> 1) into the id of some
            // other, unrelated but valid enum constant - reject non-integral values instead. Not limited to
            // Double/Float: any Number subtype that can hold a fractional value (e.g. DoubleAdder) needs this
            // check too. Safe for exactly-integral types (Long, Integer, etc.) - their doubleValue() is
            // already a whole number (even if imprecisely rounded due to magnitude), so rint() is a no-op.
            if (number.doubleValue() != Math.rint(number.doubleValue())) {
              throw new ConversionException("Not an integral value: " + o);
            }
            // must go through longValue() (the full-width value) rather than intValue() here - intValue() is
            // itself a silent narrowing conversion (keeps only the low 32 bits) and would defeat the whole
            // point of toIntExact(), which needs the untruncated value to detect and reject the out-of-range
            // case
            id = Math.toIntExact(number.longValue());
          }
          return aClass.cast(helper.lookupById(id));
        } catch (ArithmeticException ex) {
          throw new ConversionException("Not a valid int value: " + o, ex);
        }

      } else {
        throw new ConversionException("Don't know how to translate " + o.getClass() + " to " + aClass);
      }
    }
    throw new ConversionException("Don't know how to convert to " + aClass);
  }
}
