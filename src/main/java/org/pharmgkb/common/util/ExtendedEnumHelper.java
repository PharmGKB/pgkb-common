/*
 ----- BEGIN LICENSE BLOCK -----
 This Source Code Form is subject to the terms of the Mozilla Public License, v.2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ----- END LICENSE BLOCK -----
 */
package org.pharmgkb.common.util;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;


/**
 * This is a helper class that handles all the lookup work for enums.
 *
 * @author Mark Woon
 */
public class ExtendedEnumHelper<T extends ExtendedEnum> {
  private static final Map<Class, ExtendedEnumHelper> sf_enumMap = Maps.newHashMap();
  private Map<Integer, T> m_idMap = Maps.newTreeMap();
  private Map<String, T> m_shortNameMap = Maps.newTreeMap();
  private Map<String, T> m_lcShortNameMap = Maps.newTreeMap();
  private Map<String, T> m_displayNameMap = Maps.newTreeMap();
  private Map<String, T> m_additionalNamesMap = Maps.newTreeMap();


  /**
   * This constructor registers the {@link ExtendedEnum} for conversion by BeanUtils.
   */
  public ExtendedEnumHelper(@Nonnull Class clz) {
    Preconditions.checkNotNull(clz, "clz is null");
    ConvertUtils.register(ExtendedEnumConverter.getConverter(), clz);
  }


  /**
   * Adds the given enum to the maps.
   *
   * @throws IllegalStateException if the enum being added has a id, name or display name that's
   * already being used
   * @throws IllegalArgumentException if the short name has a space in it
   */
  public void add(T theEnum, int id, @Nonnull String shortName, @Nullable String displayName,
      @Nullable String... additionalNames) {
    Preconditions.checkNotNull(shortName, "shortName is null");

    // log as well as throw any errors because it may get swallowed and hidden since this is going to be called
    // during enum class instantiation
    if (m_idMap.containsKey(id)) {
      throw new IllegalStateException("An enum already exists with Id '" + id + "' for " +
          theEnum.getClass().getSimpleName());
    }
    m_idMap.put(id, theEnum);

    //noinspection ConstantConditions
    shortName = StringUtils.stripToNull(shortName);
    if (shortName == null) {
      throw new IllegalArgumentException("Short name is required (for " + theEnum.getClass().getSimpleName() + ")");
    }
    if (shortName.contains(" ")) {
      throw new IllegalArgumentException("Short name cannot have any spaces: '" + shortName + "' for " +
          theEnum.getClass().getSimpleName());
    }
    if (StringUtils.isNumeric(shortName)) {
      throw new IllegalArgumentException("Short name cannot be numeric: '" + shortName + "' for " +
          theEnum.getClass().getSimpleName());
    }
    if (m_shortNameMap.containsKey(shortName)) {
      throw new IllegalStateException("An enum already exists with the name '" + shortName + "' for " +
          theEnum.getClass().getSimpleName());
    }
    m_shortNameMap.put(shortName, theEnum);
    m_lcShortNameMap.put(shortName.toLowerCase(), theEnum);

    displayName = StringUtils.stripToNull(displayName);
    if (displayName != null) {
      if (m_displayNameMap.containsKey(displayName)) {
        throw new IllegalStateException("An enum already exists with the display name '" + displayName + "' for " +
            theEnum.getClass().getSimpleName());
      }
      m_displayNameMap.put(displayName, theEnum);
    }
    if (!sf_enumMap.containsKey(theEnum.getClass())) {
      sf_enumMap.put(theEnum.getClass(), this);
    }
    if (additionalNames != null) {
      for (String additionalName : additionalNames) {
        m_additionalNamesMap.put(additionalName, theEnum);
      }
    }
  }


  /**
   * Looks for the enum with the given Id.
   */
  public @Nullable
  T lookupById(int id) {
    return m_idMap.get(id);
  }

  /**
   * Looks for the enum with the given name.
   *
   * @return the enum for the given name, or null if none can be found
   */
  public @Nullable T lookupByName(@Nonnull String name) {
    Preconditions.checkNotNull(name, "name is null");

    if (m_displayNameMap.containsKey(name)) {
      return m_displayNameMap.get(name);
    }
    if (m_shortNameMap.containsKey(name)) {
      return m_shortNameMap.get(name);
    }
    if (m_additionalNamesMap.containsKey(name)) {
      return m_additionalNamesMap.get(name);
    }
    return m_lcShortNameMap.get(name.toLowerCase());
  }


  /**
   * If value is an integer return enum with given Id, otherwise return enum with given name.
   * Helps provide functionality for type conversion in RESTful services.
   */
  public @Nullable T fromString(String value) {

    if (StringUtils.isNumeric(value)) {
      return lookupById(Integer.parseInt(value));
    } else {
      return lookupByName(value);
    }
  }


  /**
   * Gets all the enums sorted by Id.
   */
  public @Nonnull Collection<T> getAllSortedById() {
    return m_idMap.values();
  }

  /**
   * Gets all the enums sorted by name.
   */
  public @Nonnull Collection<T> getAllSortedByName() {

    if (m_displayNameMap.isEmpty()) {
      return m_shortNameMap.values();
    }
    return m_displayNameMap.values();
  }


  /**
   * Gets the ExtendedEnumHelper to use to perform lookups for the specified ExtendedEnum class.
   */
  public static @Nullable ExtendedEnumHelper getExtendedEnumHelper(@Nonnull Class enumClass) {
    Preconditions.checkNotNull(enumClass, "enumClass is null");

    if (!sf_enumMap.containsKey(enumClass)) {
      // make sure the enum is properly initialized
      Field[] fields = enumClass.getFields();
      if (fields.length > 0) {
        try {
          fields[0].get(enumClass);
        } catch (IllegalAccessException ex) {
          throw new IllegalStateException("Cannot access enums in class " + enumClass.getName(), ex);
        }
      } else {
        throw new IllegalStateException("No enumerations in class " + enumClass.getName());
      }
    }
    return sf_enumMap.get(enumClass);
  }


  private static final Pattern sf_punctuationPattern = Pattern.compile("\\p{Punct}");
  /**
   * Converts the given name into camel case format.
   */
  public static @Nonnull String camelCaseFormat(@Nonnull String name) {
    Preconditions.checkNotNull(name, "name is null");
    name = sf_punctuationPattern.matcher(name.toLowerCase()).replaceAll("");
    return StringUtils.deleteWhitespace(StringUtils.uncapitalize(WordUtils.capitalize(name)));
  }
}
