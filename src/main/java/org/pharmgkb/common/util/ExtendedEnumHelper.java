package org.pharmgkb.common.util;

import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.regex.Pattern;
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.WordUtils;
import org.jspecify.annotations.Nullable;


/**
 * This is a helper class that handles all the lookup work for enums.
 *
 * @author Mark Woon
 */
public class ExtendedEnumHelper<T extends ExtendedEnum> {
  private static final Map<Class, ExtendedEnumHelper> sf_enumMap = new ConcurrentHashMap<>();
  private final Class m_enumClass;
  // ConcurrentSkipListMap (not TreeMap) so lookups during a concurrent add() see a weakly-consistent
  // snapshot instead of risking undefined behavior/corruption from an unsynchronized read-vs-write race
  private final Map<Integer, T> m_idMap = new ConcurrentSkipListMap<>();
  private final Map<String, T> m_shortNameMap = new ConcurrentSkipListMap<>();
  private final Map<String, T> m_displayNameMap = new ConcurrentSkipListMap<>();
  private final Map<String, T> m_lcNameMap = new ConcurrentSkipListMap<>();
  private final Map<String, T> m_additionalNamesMap = new ConcurrentSkipListMap<>();
  // live, unmodifiable views, wrapped once rather than on every call
  private final Collection<T> m_idMapValues = Collections.unmodifiableCollection(m_idMap.values());
  private final Collection<T> m_displayNameMapValues = Collections.unmodifiableCollection(m_displayNameMap.values());


  public ExtendedEnumHelper(Class clz) {
    Preconditions.checkNotNull(clz, "clz is null");
    m_enumClass = clz;
  }


  /**
   * Adds the given enum to the maps.
   * <p>
   * Synchronized, so concurrent {@code add()} calls can't race on validation, but a reader can still see a
   * partially-populated helper while another thread is adding constants: finish all {@code add()} calls for an
   * enum (conventionally from its static initializer) before other threads look it up.
   *
   * @param shortName stripped, then must be non-blank, not purely numeric, contain no internal whitespace, and
   * not collide (case-insensitively) with a name registered for a different enum constant. The names in a single
   * call aren't checked against each other, so the same string may be used for {@code shortName} and
   * {@code displayName}
   * @param displayName stripped and validated like {@code shortName}, except that internal whitespace is allowed
   * @param additionalNames each non-null entry is stripped and validated like {@code displayName}; {@code null}
   * entries are ignored
   * @throws NullPointerException if {@code theEnum}, {@code shortName}, or {@code displayName} is {@code null}
   * @throws IllegalArgumentException if {@code theEnum} isn't an instance of this helper's enum class; if
   * {@code id} is already registered; if {@code shortName}, {@code displayName} or a non-null
   * {@code additionalNames} entry is blank or purely numeric once stripped; if {@code shortName} contains internal
   * whitespace once stripped; or if any of those names collides (case-insensitively) with a name already
   * registered for a different enum constant
   */
  public synchronized void add(T theEnum, int id, String shortName, String displayName,
      String @Nullable ... additionalNames) {

    Preconditions.checkArgument(m_enumClass.isInstance(theEnum), "%s is not an instance of %s",
        theEnum.getClass().getSimpleName(), m_enumClass.getSimpleName());
    Preconditions.checkArgument(!m_idMap.containsKey(id), "Duplicate ID '%s' for %s", id,
        m_enumClass.getSimpleName());

    Preconditions.checkNotNull(shortName, "shortName is null");
    String strippedShortName = StringUtils.stripToNull(shortName);
    Preconditions.checkArgument(strippedShortName != null, "Empty shortName for %s",
        m_enumClass.getSimpleName());
    // any whitespace, including NBSP (which Character.isWhitespace() excludes, hence isSpaceChar())
    Preconditions.checkArgument(
        strippedShortName.chars().noneMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c)),
        "Spaces in shortName for %s (%s)", m_enumClass.getSimpleName(), shortName);
    Preconditions.checkArgument(!StringUtils.isNumeric(strippedShortName), "Numeric shortName for %s (%s)",
        m_enumClass.getSimpleName(), shortName);
    Preconditions.checkArgument(!m_shortNameMap.containsKey(strippedShortName),
        "Duplicate shortName '%s' for %s", strippedShortName, m_enumClass.getSimpleName());
    String lcShortName = strippedShortName.toLowerCase(Locale.ROOT);
    ExtendedEnum shortNameCollision = m_lcNameMap.get(lcShortName);
    if (shortNameCollision != null && shortNameCollision != theEnum) {
      throw new IllegalArgumentException(String.format("shortName '%s' for %s mapped to %s and %s",
          strippedShortName, m_enumClass.getSimpleName(), shortNameCollision, theEnum));
    }

    Preconditions.checkNotNull(displayName, "displayName is null");
    String strippedDisplayName = StringUtils.stripToNull(displayName);
    Preconditions.checkArgument(strippedDisplayName != null, "Empty displayName for %s",
        m_enumClass.getSimpleName());
    Preconditions.checkArgument(!StringUtils.isNumeric(strippedDisplayName), "Numeric displayName for %s (%s)",
        m_enumClass.getSimpleName(), displayName);
    Preconditions.checkArgument(!m_displayNameMap.containsKey(strippedDisplayName),
        "Duplicate displayName '%s' for %s", strippedDisplayName, m_enumClass.getSimpleName());
    String lcDisplayName = strippedDisplayName.toLowerCase(Locale.ROOT);
    ExtendedEnum displayNameCollision = m_lcNameMap.get(lcDisplayName);
    if (displayNameCollision != null && displayNameCollision != theEnum) {
      throw new IllegalArgumentException(String.format("displayName '%s' for %s mapped to %s and %s",
          strippedDisplayName, m_enumClass.getSimpleName(), displayNameCollision, theEnum));
    }

    if (additionalNames != null) {
      // validate everything before mutating anything, so a collision can't leave a partial registration
      for (String additionalName : additionalNames) {
        if (additionalName != null) {
          String strippedAdditionalName = StringUtils.stripToNull(additionalName);
          Preconditions.checkArgument(strippedAdditionalName != null, "Empty additional name for %s",
              m_enumClass.getSimpleName());
          Preconditions.checkArgument(!StringUtils.isNumeric(strippedAdditionalName),
              "Numeric additional name for %s (%s)", m_enumClass.getSimpleName(), additionalName);
          ExtendedEnum additionalNameCollision = m_lcNameMap.get(strippedAdditionalName.toLowerCase(Locale.ROOT));
          if (additionalNameCollision != null && additionalNameCollision != theEnum) {
            throw new IllegalArgumentException(String.format("Additional name '%s' for %s mapped to %s and %s",
                strippedAdditionalName, m_enumClass.getSimpleName(), additionalNameCollision, theEnum));
          }
        }
      }
    }

    // all validation passed - safe to mutate state now
    m_idMap.put(id, theEnum);
    m_shortNameMap.put(strippedShortName, theEnum);
    m_lcNameMap.put(lcShortName, theEnum);
    m_displayNameMap.put(strippedDisplayName, theEnum);
    m_lcNameMap.put(lcDisplayName, theEnum);
    // putIfAbsent: sf_enumMap is shared across helper instances, which add()'s lock doesn't cover
    sf_enumMap.putIfAbsent(m_enumClass, this);
    if (additionalNames != null) {
      for (String additionalName : additionalNames) {
        String strippedAdditionalName = StringUtils.stripToNull(additionalName);
        if (strippedAdditionalName != null) {
          m_additionalNamesMap.put(strippedAdditionalName, theEnum);
          m_lcNameMap.put(strippedAdditionalName.toLowerCase(Locale.ROOT), theEnum);
        }
      }
    }
  }


  /**
   * Automatically registers every constant of the given {@link ExtendedEnum} enum, calling {@link #add} once per
   * constant returned by {@link Class#getEnumConstants()} using {@link ExtendedEnum#getId()},
   * {@link ExtendedEnum#getShortName()}, {@link ExtendedEnum#getDisplayName()} and
   * {@link ExtendedEnum#getAdditionalNames()} - an alternative to each constant's constructor calling {@link #add}
   * itself, for implementors that don't need per-constant control over registration.
   * <p>
   * Intended to be called once, from a {@code private static final ExtendedEnumHelper<MyEnum> s_helper =
   * ExtendedEnumHelper.register(MyEnum.class);} field initializer - enum constants are initialized before any other
   * static field, so {@link Class#getEnumConstants()} sees complete instances.
   *
   * @throws IllegalArgumentException if any constant's id, name or display name collides with another, as in
   * {@link #add}
   * @throws IllegalStateException if {@code clz} is already registered, either before this call or during it (e.g.
   * by {@code clz}'s own static initializer, triggered by {@link Class#getEnumConstants()}). Exactly one
   * registration wins, but which one isn't controlled: if another caller beats {@code clz}'s own static
   * initializer, that initializer fails and {@code clz} becomes unusable ({@link NoClassDefFoundError}), so only
   * register from the enum's own static initializer.
   */
  public static <T extends Enum<T> & ExtendedEnum> ExtendedEnumHelper<T> register(Class<T> clz) {
    Preconditions.checkNotNull(clz, "clz is null");
    Preconditions.checkState(!sf_enumMap.containsKey(clz), "%s is already registered", clz);
    ExtendedEnumHelper<T> helper = new ExtendedEnumHelper<>(clz);
    for (T constant : clz.getEnumConstants()) {
      helper.add(constant, constant.getId(), constant.getShortName(), constant.getDisplayName(),
          constant.getAdditionalNames());
    }
    // add() already registered the helper for a non-empty enum; this covers a zero-constant one. A different
    // helper here means clz's static initializer registered itself during getEnumConstants() above
    ExtendedEnumHelper<?> existing = sf_enumMap.putIfAbsent(clz, helper);
    if (existing != null && existing != helper) {
      throw new IllegalStateException(clz + " is already registered");
    }
    return helper;
  }


  /**
   * Looks for the enum with the given ID.
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

    // add() stores names stripped
    String strippedName = StringUtils.stripToNull(name);
    if (strippedName == null) {
      return null;
    }
    if (m_displayNameMap.containsKey(strippedName)) {
      return m_displayNameMap.get(strippedName);
    }
    if (m_shortNameMap.containsKey(strippedName)) {
      return m_shortNameMap.get(strippedName);
    }
    if (m_additionalNamesMap.containsKey(strippedName)) {
      return m_additionalNamesMap.get(strippedName);
    }
    return m_lcNameMap.get(strippedName.toLowerCase(Locale.ROOT));
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
    // strip before the numeric check, or a padded ID (e.g. " 1 ") is looked up as a name
    String strippedValue = StringUtils.stripToNull(value);
    if (strippedValue == null) {
      return null;
    }
    if (StringUtils.isNumeric(strippedValue)) {
      try {
        return lookupById(Integer.parseInt(strippedValue));
      } catch (NumberFormatException ex) {
        // all digits, but too large for an int - no enum has such an ID
        return null;
      }
    } else {
      return lookupByName(strippedValue);
    }
  }


  /**
   * Gets all the enums sorted by Id. The returned collection is unmodifiable and reflects any enum
   * registered after this call.
   */
  public Collection<T> getAllSortedById() {
    return m_idMapValues;
  }

  /**
   * Gets all the enums sorted by name. The returned collection is unmodifiable and reflects any enum
   * registered after this call.
   */
  public Collection<T> getAllSortedByName() {
    return m_displayNameMapValues;
  }


  /**
   * Gets the ExtendedEnumHelper to use to perform lookups for the specified ExtendedEnum class.
   *
   * @throws IllegalStateException if attempting to lookup an {@link ExtendedEnum} that hasn't been registered
   */
  public static <T extends ExtendedEnum> ExtendedEnumHelper<T> getExtendedEnumHelper(Class<T> enumClass) {
    Preconditions.checkNotNull(enumClass, "enumClass is null");

    if (!sf_enumMap.containsKey(enumClass)) {
      // initialize the class so its add() calls run - this works for any class, not just enums
      try {
        Class.forName(enumClass.getName(), true, enumClass.getClassLoader());
      } catch (ClassNotFoundException ex) {
        // should never happen - we already have a live Class object for this exact class
        throw new IllegalStateException("Unable to initialize " + enumClass.getName(), ex);
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
   * <p>
   * A non-breaking space is not treated as whitespace (unlike in {@link Strings}), so it survives into the result.
   *
   * @throws IllegalArgumentException if {@code name} strips down to nothing (e.g. it's entirely punctuation)
   */
  public static String camelCaseFormat(String name) {
    Preconditions.checkNotNull(name, "name is null");
    String strippedName = StringUtils.stripToNull(sf_punctuationPattern.matcher(name.toLowerCase(Locale.ROOT)).replaceAll(""));
    if (strippedName == null) {
      throw new IllegalArgumentException("'" + name + "' converts to empty string");
    }
    return StringUtils.deleteWhitespace(StringUtils.uncapitalize(WordUtils.capitalize(strippedName)));
  }
}
