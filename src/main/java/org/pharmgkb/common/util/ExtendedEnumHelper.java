package org.pharmgkb.common.util;

import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.regex.Pattern;
import com.google.common.base.Preconditions;
import org.apache.commons.beanutils.ConvertUtils;
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
  // wrapped once, not per-call: Map.values() is a live view, so wrapping it here still reflects every future
  // add() - this keeps getAllSortedById()/getAllSortedByName() from exposing a mutable view of the registry
  // without paying for a copy on every call
  private final Collection<T> m_idMapValues = Collections.unmodifiableCollection(m_idMap.values());
  private final Collection<T> m_displayNameMapValues = Collections.unmodifiableCollection(m_displayNameMap.values());


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
   * <p>
   * This method is synchronized so that concurrent {@code add()} calls can't race on the check-then-mutate
   * validation (e.g. two threads both passing the duplicate-ID check before either one calls {@code put()}).
   * This does NOT make the overall registration process safe to run concurrently across multiple threads:
   * a reader that already holds a reference to this helper (e.g. via {@link #getExtendedEnumHelper}) can
   * still observe a partially-populated helper while another thread is mid-way through calling {@code add()}
   * for every constant of an enum (as is conventional, from that enum's own static initializer). Callers
   * should ensure all {@code add()} calls for a given enum type complete on a single thread before any other
   * thread looks up that type.
   *
   * @throws IllegalArgumentException if the enum being added has an id, name or display name that's already
   * being used, or if the short name has a space in it
   */
  public synchronized void add(T theEnum, int id, String shortName, String displayName,
      String @Nullable ... additionalNames) {

    Preconditions.checkArgument(m_enumClass.isInstance(theEnum), "%s is not an instance of %s",
        theEnum.getClass().getSimpleName(), m_enumClass.getSimpleName());
    Preconditions.checkArgument(!m_idMap.containsKey(id), "Duplicate ID '%s' for %s", id,
        theEnum.getClass().getSimpleName());

    Preconditions.checkNotNull(shortName, "shortName is null");
    String strippedShortName = StringUtils.stripToNull(shortName);
    Preconditions.checkArgument(strippedShortName != null, "Empty shortName for %s",
        theEnum.getClass().getSimpleName());
    // .contains(" ") only rejects literal U+0020 - check every whitespace category (tab, newline, NBSP, etc.)
    // Character.isWhitespace() alone excludes non-breaking space by design, so isSpaceChar() is also needed
    Preconditions.checkArgument(
        strippedShortName.chars().noneMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c)),
        "Spaces in shortName for %s (%s)", theEnum.getClass().getSimpleName(), shortName);
    Preconditions.checkArgument(!StringUtils.isNumeric(strippedShortName), "Numeric shortName for %s (%s)",
        theEnum.getClass().getSimpleName(), shortName);
    Preconditions.checkArgument(!m_shortNameMap.containsKey(strippedShortName),
        "Duplicate shortName '%s' for %s", strippedShortName, theEnum.getClass().getSimpleName());
    String lcShortName = strippedShortName.toLowerCase(Locale.ROOT);
    ExtendedEnum shortNameCollision = m_lcNameMap.get(lcShortName);
    if (shortNameCollision != null && shortNameCollision != theEnum) {
      throw new IllegalArgumentException(String.format("shortName '%s' for %s mapped to %s and %s",
          strippedShortName, theEnum.getClass().getSimpleName(), shortNameCollision, theEnum));
    }

    Preconditions.checkNotNull(displayName, "displayName is null");
    String strippedDisplayName = StringUtils.stripToNull(displayName);
    Preconditions.checkArgument(strippedDisplayName != null, "Empty displayName for %s",
        theEnum.getClass().getSimpleName());
    Preconditions.checkArgument(!StringUtils.isNumeric(strippedDisplayName), "Numeric displayName for %s (%s)",
        theEnum.getClass().getSimpleName(), displayName);
    Preconditions.checkArgument(!m_displayNameMap.containsKey(strippedDisplayName),
        "Duplicate displayName '%s' for %s", strippedDisplayName, theEnum.getClass().getSimpleName());
    String lcDisplayName = strippedDisplayName.toLowerCase(Locale.ROOT);
    ExtendedEnum displayNameCollision = m_lcNameMap.get(lcDisplayName);
    if (displayNameCollision != null && displayNameCollision != theEnum) {
      throw new IllegalArgumentException(String.format("displayName '%s' for %s mapped to %s and %s",
          strippedDisplayName, theEnum.getClass().getSimpleName(), displayNameCollision, theEnum));
    }

    if (additionalNames != null) {
      // validate all additional names before mutating ANYTHING (including id/shortName/displayName below),
      // so a collision doesn't leave this enum partially registered or clobber another enum's lowercase
      // lookup entry
      for (String additionalName : additionalNames) {
        String strippedAdditionalName = StringUtils.stripToNull(additionalName);
        if (strippedAdditionalName != null) {
          Preconditions.checkArgument(!StringUtils.isNumeric(strippedAdditionalName),
              "Numeric additional name for %s (%s)", theEnum.getClass().getSimpleName(), additionalName);
          ExtendedEnum additionalNameCollision = m_lcNameMap.get(strippedAdditionalName.toLowerCase(Locale.ROOT));
          if (additionalNameCollision != null && additionalNameCollision != theEnum) {
            throw new IllegalArgumentException(String.format("Additional name '%s' for %s mapped to %s and %s",
                strippedAdditionalName, theEnum.getClass().getSimpleName(), additionalNameCollision, theEnum));
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
    // putIfAbsent, not containsKey+put: this is the only unsynchronized state shared across DIFFERENT
    // ExtendedEnumHelper instances (add() only synchronizes on `this`), so a check-then-put here could race
    // if two different helpers for the same class are (incorrectly) registered concurrently
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
   * ExtendedEnumHelper.register(MyEnum.class);} field initializer - by the time that initializer runs, every enum
   * constant is already fully constructed (the JLS guarantees enum constants initialize before any other static
   * field in the same class), so {@link Class#getEnumConstants()} sees complete instances, not nulls.
   *
   * @throws IllegalArgumentException if any constant's id, name or display name collides with another - same as
   * {@link #add}, since this delegates to it
   * @throws IllegalStateException if {@code clz} is already registered - a second call would silently build a
   * fully-populated but orphaned helper: {@code sf_enumMap}'s {@code putIfAbsent} (see {@link #add}) means the
   * FIRST helper registered for a class always wins that slot, so the second call's own return value would
   * disagree with (and never be reachable via) {@link #getExtendedEnumHelper}, {@link ExtendedEnumConverter} or
   * any other caller that looks a helper up by class rather than using the reference this method returns. This
   * check is a plain check-then-act, not atomic - it only protects the intended usage (a single static field
   * initializer per class, which the JLS already serializes via that class's own initialization lock), not two
   * threads calling {@code register(clz)} for the SAME class concurrently from outside that pattern.
   */
  public static <T extends Enum<T> & ExtendedEnum> ExtendedEnumHelper<T> register(Class<T> clz) {
    Preconditions.checkNotNull(clz, "clz is null");
    Preconditions.checkState(!sf_enumMap.containsKey(clz), "%s is already registered", clz);
    ExtendedEnumHelper<T> helper = new ExtendedEnumHelper<>(clz);
    for (T constant : clz.getEnumConstants()) {
      helper.add(constant, constant.getId(), constant.getShortName(), constant.getDisplayName(),
          constant.getAdditionalNames());
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

    // add() stores names stripped (shortName/displayName/additionalNames), so the query must be stripped too,
    // or a caller passing an already-padded value silently gets null instead of a match
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
    // lookupByName() strips its own query, but the numeric check below must strip first too, or a padded
    // numeric ID (e.g. " 1 ") fails isNumeric() and falls through to a name lookup that can never match
    String strippedValue = StringUtils.stripToNull(value);
    if (strippedValue == null) {
      return null;
    }
    if (StringUtils.isNumeric(strippedValue)) {
      try {
        return lookupById(Integer.parseInt(strippedValue));
      } catch (NumberFormatException ex) {
        // isNumeric() accepts any all-digit string, but not all of those fit in an int - there's no such
        // enum with an out-of-range ID, so this is a lookup miss, not an error
        return null;
      }
    } else {
      return lookupByName(strippedValue);
    }
  }


  /**
   * Gets all the enums sorted by Id.
   */
  public Collection<T> getAllSortedById() {
    return m_idMapValues;
  }

  /**
   * Gets all the enums sorted by name.
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
      // make sure the class is properly initialized (so its add() calls run) - Class.forName(name, true, ...)
      // triggers a class's static initializer for ANY class kind, unlike getEnumConstants() (returns null for
      // a non-enum class - ExtendedEnum is a plain interface, not restricted to actual enums, e.g. the
      // typesafe-constant pattern) or the old getFields()[0] reflection trick (relied on an enum-constant
      // field ordering the JDK doesn't actually guarantee)
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
