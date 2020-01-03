package org.pharmgkb.common.util;

import java.util.SortedMap;
import java.util.function.BinaryOperator;


/**
 * This is a merge function that rejects duplicate keys.
 * Used to collect a stream into a {@link SortedMap}.
 * <p>
 * Example:
 * {@code
 * SortedMap<String, String> sortedMap = list.stream()
 *   .collect(Collectors.toMap(s -> "key:" + s, Function.identity(), new NoDuplicateMergeFunction<>(), TreeMap::new));
 * }
 *
 * @author Mark Woon
 */
public class NoDuplicateMergeFunction<T> implements BinaryOperator<T> {

  @Override
  public T apply(T o, T o2) {
    throw new RuntimeException(String.format("Duplicate key %s", o));
  }
}
