package org.pharmgkb.common.util;

import java.util.SortedMap;
import java.util.function.BinaryOperator;


/**
 * This is a {@link BinaryOperator} to use as the merge function for {@link java.util.stream.Collectors#toMap},
 * which rejects merging (i.e. throws) when two different inputs map to the same key. Used to collect a stream
 * into a {@link SortedMap}.
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
    // a merge function only gets the two colliding values, never the key
    throw new IllegalStateException(String.format("Duplicate value %s (colliding with %s)", o, o2));
  }
}
