/*
 ----- BEGIN LICENSE BLOCK -----
 This Source Code Form is subject to the terms of the Mozilla Public License, v.2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ----- END LICENSE BLOCK -----
 */
package org.pharmgkb.common.util;

import javax.annotation.Nullable;
import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.Converter;


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
  public @Nullable <T> T convert(Class<T> aClass, Object o) {

    ExtendedEnumHelper helper = ExtendedEnumHelper.getExtendedEnumHelper(aClass);
    if (helper != null && ExtendedEnum.class.isAssignableFrom(aClass)) {
      if (o instanceof String) {
        return aClass.cast(helper.fromString((String)o));

      } else if (o instanceof Number) {
        return aClass.cast(helper.lookupById(((Number)o).intValue()));

      } else {
        throw new ConversionException("Don't know how to translate " + o.getClass() + " to " + aClass);
      }
    }
    throw new ConversionException("Don't know how to convert to " + aClass);
  }
}
