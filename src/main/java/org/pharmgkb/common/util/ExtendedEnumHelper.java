package org.pharmgkb.common.util;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import com.google.common.base.Preconditions;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.WordUtils;
import org.checkerframework.checker.nullness.qual.Nullable;


/**
 * This is a helper class that handles all the lookup work for enums.
 *
 * @author Mark Woon
 */
public class ExtendedEnumHelper<T extends ExtendedEnum> {
  private static final Map<Class, ExtendedEnumHelper> sf_enumMap = new HashMap<>();
  private final Class m_enumClass;
  private final Map<Integer, T> m_idMap = new TreeMap<>();
  private final Map<String, T> m_shortNameMap = new TreeMap<>();
  private final Map<String, T> m_displayNameMap = new TreeMap<>();
  private final Map<String, T> m_lcNameMap = new TreeMap<>();
  private final Map<String, T> m_additionalNamesMap = new TreeMap<>();


  /**
   * This constructor registers the {@link ExtendedEnum} for conversion by BeanUtils.
   */
  public ExtendedEnumHelper(Class clz) {
    Preconditions.checkNotNull(clz, "clz is null");
    m_enumClass = clz;
    ConvertUtils.register(ExtendedEnumConverter.getConverter(), clz);
  }


  /**
   * Adds the given enum to the maps.
   *
   * @throws IllegalStateException if the enum being added has a id, name or display name that's
   * already being used
   * @throws IllegalArgumentException if the short name has a space in it
   */
  public void add(T theEnum, int id, String shortName, @Nullable String displayName,
      String @Nullable ... additionalNames) {

    Preconditions.checkArgument(!m_idMap.containsKey(id), "Duplicate ID '%s' for %s", id,
        theEnum.getClass().getSimpleName());

    Preconditions.checkNotNull(shortName, "shortName is null");
    String strippedShortName = StringUtils.stripToNull(shortName);
    Preconditions.checkArgument(strippedShortName != null, "Empty shortName for %s",
        theEnum.getClass().getSimpleName());
    Preconditions.checkArgument(!strippedShortName.contains(" "), "Spaces in shortName for %s",
        theEnum.getClass().getSimpleName());
    Preconditions.checkArgument(!StringUtils.isNumeric(strippedShortName), "Numeric shortName for %s",
        theEnum.getClass().getSimpleName());
    Preconditions.checkArgument(!m_shortNameMap.containsKey(strippedShortName),
        "Duplicate shortName '%s' for %s", strippedShortName, theEnum.getClass().getSimpleName());

    String strippedDisplayName = StringUtils.stripToNull(displayName);
    if (strippedDisplayName != null) {
      Preconditions.checkArgument(!m_displayNameMap.containsKey(strippedDisplayName),
          "Duplicate displayName '%s' for %s", strippedDisplayName, theEnum.getClass().getSimpleName());
    }

    m_idMap.put(id, theEnum);

    m_shortNameMap.put(strippedShortName, theEnum);
    m_lcNameMap.put(strippedShortName.toLowerCase(), theEnum);

    if (strippedDisplayName != null) {
      m_displayNameMap.put(strippedDisplayName, theEnum);
      m_lcNameMap.put(strippedDisplayName.toLowerCase(), theEnum);
    }
    if (!sf_enumMap.containsKey(theEnum.getClass())) {
      sf_enumMap.put(theEnum.getClass(), this);
    }
    if (additionalNames != null) {
      for (String additionalName : additionalNames) {
        if (additionalName != null) {
          m_additionalNamesMap.put(additionalName, theEnum);
          ExtendedEnum oldVal = m_lcNameMap.put(additionalName.toLowerCase(), theEnum);
          if (oldVal != null && oldVal != theEnum) {
            throw new IllegalArgumentException(String.format("Additional name '%s' for %s mapped to %s and %s",
                additionalName, theEnum.getClass().getSimpleName(), oldVal, theEnum));
          }
        }
      }
    }
  }


  /**
   * Looks for the enum with the given Id.
   */
  public @Nullable T lookupById(int id) {
    return m_idMap.get(id);
  }

  /**
   * Looks for the enum with the given name.
   *
   * @return the enum for the given name, or null if none can be found
   */
  public @Nullable T lookupByName(String name) {
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
    return m_lcNameMap.get(name.toLowerCase());
  }

  /**
   * Looks for the enum with the given name.
   *
   * @return the enum for the given name
   * @throws IllegalArgumentException if no enum for the given name exists
   */
  public T lookupByNameOrThrow(String name) {
    T rez = lookupByName(name);
    if (rez == null) {
      throw new IllegalArgumentException("No such " + m_enumClass.getSimpleName() + ": '" + name + "'");
    }
    return rez;
  }


  /**
   * If value is an integer return enum with given Id, otherwise return enum with given name.
   * Helps provide functionality for type conversion in RESTful services.
   */
  public @Nullable T fromString(@Nullable String value) {

    if (value == null) {
      return null;
    }
    if (StringUtils.isNumeric(value)) {
      return lookupById(Integer.parseInt(value));
    } else {
      return lookupByName(value);
    }
  }


  /**
   * Gets all the enums sorted by Id.
   */
  public Collection<T> getAllSortedById() {
    return m_idMap.values();
  }

  /**
   * Gets all the enums sorted by name.
   */
  public Collection<T> getAllSortedByName() {

    if (m_displayNameMap.isEmpty()) {
      return m_shortNameMap.values();
    }
    return m_displayNameMap.values();
  }


  /**
   * Gets the ExtendedEnumHelper to use to perform lookups for the specified ExtendedEnum class.
   *
   * @throws IllegalStateException if attempting to lookup an {@link ExtendedEnum} that hasn't been registered
   */
  public static <T extends ExtendedEnum> ExtendedEnumHelper<T> getExtendedEnumHelper(Class<T> enumClass) {
    Preconditions.checkNotNull(enumClass, "enumClass is null");

    if (!sf_enumMap.containsKey(enumClass)) {
      // make sure the enum is properly initialized
      Field[] fields = enumClass.getFields();
      if (fields.length > 0) {
        try {
          //noinspection ResultOfMethodCallIgnored
          fields[0].get(enumClass);
        } catch (IllegalAccessException ex) {
          throw new IllegalStateException("Cannot access enums in class " + enumClass.getName(), ex);
        }
      } else {
        throw new IllegalStateException("No enumerations in class " + enumClass.getName());
      }
    }
    ExtendedEnumHelper extendedEnumHelper = sf_enumMap.get(enumClass);
    if (extendedEnumHelper == null) {
      throw new IllegalStateException("Unregistered ExtendedEnum: " + enumClass);
    }
    //noinspection unchecked
    return extendedEnumHelper;
  }


  private static final Pattern sf_punctuationPattern = Pattern.compile("\\p{Punct}");
  /**
   * Converts the given name into camel case format.
   */
  public static String camelCaseFormat(String name) {
    Preconditions.checkNotNull(name, "name is null");
    String strippedName = StringUtils.stripToNull(sf_punctuationPattern.matcher(name.toLowerCase()).replaceAll(""));
    if (strippedName == null) {
      throw new IllegalArgumentException("'" + name + "' converts to empty string");
    }
    return StringUtils.deleteWhitespace(StringUtils.uncapitalize(WordUtils.capitalize(strippedName)));
  }
}
