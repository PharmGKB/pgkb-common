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
    // per Collectors.toMap()'s merge-function contract, apply() only ever receives the two colliding VALUES,
    // never the key - so this must not claim to show "the duplicate key" (which differs from the value
    // whenever the key-mapper isn't the identity function, as in this class's own javadoc example). Both
    // colliding values are included (not just the first), matching Collectors.toMap()'s own default merge
    // function's message - dropping the second makes a collision much harder to diagnose when they differ.
    throw new IllegalStateException(String.format("Duplicate value %s (colliding with %s)", o, o2));
  }
}
