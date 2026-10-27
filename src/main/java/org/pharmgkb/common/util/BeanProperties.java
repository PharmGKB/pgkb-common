package org.pharmgkb.common.util;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.google.common.base.Splitter;
import org.jspecify.annotations.Nullable;


/**
 * Reads JavaBean properties by name, and converts strings to property types, for callers that take property names
 * and values from user input (such as a query string).
 * <p>
 * A property path is one or more property names joined by dots ({@code location.genes}). Each name must be a plain
 * Java identifier, and {@code class} is refused, so a path cannot reach {@code getClass()} and through it the class
 * loader. There is no indexed ({@code a[0]}) or mapped ({@code a(key)}) syntax.
 *
 * @author Mark Woon
 */
public class BeanProperties {
  private static final Splitter sf_dotSplitter = Splitter.on('.');
  private static final Set<String> sf_trueWords = Set.of("true", "yes", "y", "on", "1");
  private static final Set<String> sf_falseWords = Set.of("false", "no", "n", "off", "0");
  private static final Map<Class<?>, Class<?>> sf_primitiveWrappers = Map.of(
      boolean.class, Boolean.class,
      byte.class, Byte.class,
      short.class, Short.class,
      int.class, Integer.class,
      long.class, Long.class,
      float.class, Float.class,
      double.class, Double.class,
      char.class, Character.class);


  /**
   * Private constructor to prevent instantiation of utility class.
   */
  private BeanProperties() {
  }


  /**
   * Gets the readable properties of {@code type}, leaving out {@code class}.
   */
  public static List<PropertyDescriptor> getProperties(Class<?> type) {
    try {
      return Arrays.stream(Introspector.getBeanInfo(type).getPropertyDescriptors())
          .filter(pd -> pd.getReadMethod() != null && !pd.getName().equals("class"))
          .collect(Collectors.toList());
    } catch (IntrospectionException ex) {
      throw new IllegalArgumentException("Cannot introspect " + type.getName(), ex);
    }
  }


  /**
   * Gets the descriptor of the readable property at the end of {@code path}. Each name along the path is looked up on
   * the runtime class of the value before it, starting with {@code bean}'s, so a property of a subclass is found even
   * where the getter before it declares a supertype.
   *
   * @return the descriptor, or null if some name along the path is not a readable property
   * @throws IllegalArgumentException if {@code path} is not a plain dotted path, or a value part-way along it is null
   * @throws IllegalStateException if a getter part-way along the path throws
   */
  public static @Nullable PropertyDescriptor getPropertyDescriptor(Object bean, String path) {
    List<String> names = parsePath(path);
    Object current = bean;
    for (int x = 0; x < names.size(); x += 1) {
      PropertyDescriptor pd = findProperty(current.getClass(), names.get(x));
      if (pd == null) {
        return null;
      }
      if (x == names.size() - 1) {
        return pd;
      }
      current = readNonNull(current, pd, path);
    }
    throw new AssertionError("parsePath() never returns an empty list");
  }


  /**
   * Gets the value of the property at the end of {@code path}, following it as
   * {@link #getPropertyDescriptor(Object, String)} does.
   *
   * @throws IllegalArgumentException if {@code path} is not a plain dotted path, some name along it is not a readable
   * property, or a value part-way along it is null
   * @throws IllegalStateException if a getter throws
   */
  public static @Nullable Object getProperty(Object bean, String path) {
    List<String> names = parsePath(path);
    Object current = bean;
    for (int x = 0; x < names.size(); x += 1) {
      PropertyDescriptor pd = findProperty(current.getClass(), names.get(x));
      if (pd == null) {
        throw new IllegalArgumentException("No readable property '" + names.get(x) + "' on " +
            current.getClass().getSimpleName() + " in path '" + path + "'");
      }
      if (x == names.size() - 1) {
        return read(current, pd);
      }
      current = readNonNull(current, pd, path);
    }
    throw new AssertionError("parsePath() never returns an empty list");
  }


  /**
   * Checks whether {@link #convert(String, Class)} can convert to {@code type}: {@link String}, the primitives and
   * their wrappers, and {@link ExtendedEnum}s.
   */
  public static boolean isConvertible(Class<?> type) {
    return type == String.class || sf_primitiveWrappers.containsKey(type) ||
        sf_primitiveWrappers.containsValue(type) || ExtendedEnum.class.isAssignableFrom(type);
  }


  /**
   * Converts {@code value} to {@code type}, strictly: a value that does not exactly represent a {@code type} is
   * refused, never turned into a default such as 0 or false.
   * <ul>
   *   <li>A {@link String} is returned as given.</li>
   *   <li>A number is stripped of surrounding whitespace and must parse as {@code type}, in range.</li>
   *   <li>A boolean, stripped, is one of true/false, yes/no, y/n, on/off or 1/0, in any case.</li>
   *   <li>A character is exactly one character.</li>
   *   <li>An {@link ExtendedEnum} is an id or a name, as {@link ExtendedEnumHelper#fromString(String)} takes it.</li>
   * </ul>
   *
   * @return the converted value, boxed for a primitive type
   * @throws IllegalArgumentException if {@code type} is not {@link #isConvertible(Class) convertible}, or
   * {@code value} is not a valid {@code type}. For an {@link ExtendedEnum}, the message lists the accepted names.
   * @throws IllegalStateException if {@code type} is an {@link ExtendedEnum} with no {@link ExtendedEnumHelper}, which
   * is a programming error, not bad input
   */
  public static Object convert(String value, Class<?> type) {
    if (!isConvertible(type)) {
      throw new IllegalArgumentException("Cannot convert a string to " + type.getName());
    }
    if (type == String.class) {
      return value;
    }
    if (ExtendedEnum.class.isAssignableFrom(type)) {
      return convertExtendedEnum(value, type);
    }

    Class<?> wrapper = sf_primitiveWrappers.getOrDefault(type, type);
    if (wrapper == Character.class) {
      if (value.length() != 1) {
        throw new IllegalArgumentException("'" + value + "' is not a single character");
      }
      return value.charAt(0);
    }
    String stripped = value.strip();
    if (wrapper == Boolean.class) {
      String word = stripped.toLowerCase(Locale.ROOT);
      if (sf_trueWords.contains(word)) {
        return Boolean.TRUE;
      }
      if (sf_falseWords.contains(word)) {
        return Boolean.FALSE;
      }
      throw new IllegalArgumentException("'" + value + "' is not a boolean; use one of: true, false, yes, no, y, " +
          "n, on, off, 1, 0");
    }
    try {
      if (wrapper == Byte.class) {
        return Byte.parseByte(stripped);
      }
      if (wrapper == Short.class) {
        return Short.parseShort(stripped);
      }
      if (wrapper == Integer.class) {
        return Integer.parseInt(stripped);
      }
      if (wrapper == Long.class) {
        return Long.parseLong(stripped);
      }
      if (wrapper == Float.class) {
        return Float.parseFloat(stripped);
      }
      return Double.parseDouble(stripped);
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("'" + value + "' is not a valid " + type.getSimpleName(), ex);
    }
  }


  private static Object convertExtendedEnum(String value, Class<?> type) {
    //noinspection unchecked
    ExtendedEnumHelper<?> helper = ExtendedEnumHelper.getExtendedEnumHelper((Class<? extends ExtendedEnum>)type);
    Object converted = helper.fromString(value);
    if (converted == null) {
      throw new IllegalArgumentException("'" + value + "' is not a valid " + type.getSimpleName() + "; use one of: " +
          helper.getAllSortedByName().stream()
              .map(ExtendedEnum::getDisplayName)
              .collect(Collectors.joining(", ")));
    }
    return converted;
  }


  /**
   * Splits {@code path} into property names, refusing anything but plain Java identifiers and {@code class}.
   */
  private static List<String> parsePath(String path) {
    List<String> names = sf_dotSplitter.splitToList(path);
    for (String name : names) {
      if (!isIdentifier(name) || name.equals("class")) {
        throw new IllegalArgumentException("Not a valid property path: '" + path + "'");
      }
    }
    return names;
  }

  private static boolean isIdentifier(String name) {
    if (name.isEmpty() || !Character.isJavaIdentifierStart(name.charAt(0))) {
      return false;
    }
    return name.chars().skip(1).allMatch(Character::isJavaIdentifierPart);
  }

  private static @Nullable PropertyDescriptor findProperty(Class<?> type, String name) {
    for (PropertyDescriptor pd : getProperties(type)) {
      if (pd.getName().equals(name)) {
        return pd;
      }
    }
    return null;
  }

  private static Object readNonNull(Object bean, PropertyDescriptor pd, String path) {
    Object value = read(bean, pd);
    if (value == null) {
      throw new IllegalArgumentException("'" + pd.getName() + "' is null on " + bean.getClass().getSimpleName() +
          ", so path '" + path + "' cannot be followed");
    }
    return value;
  }

  private static @Nullable Object read(Object bean, PropertyDescriptor pd) {
    try {
      return pd.getReadMethod().invoke(bean);
    } catch (InvocationTargetException ex) {
      throw new IllegalStateException("Getter for '" + pd.getName() + "' on " + bean.getClass().getSimpleName() +
          " threw", ex.getCause());
    } catch (IllegalAccessException ex) {
      throw new IllegalStateException("Cannot call getter for '" + pd.getName() + "' on " +
          bean.getClass().getSimpleName(), ex);
    }
  }
}
