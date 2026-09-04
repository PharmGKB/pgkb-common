package org.pharmgkb.common.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.google.common.collect.Lists;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;


/**
 * JUnit test for {@link NoDuplicateMergeFunction}.
 *
 * @author Mark Woon
 */
class NoDuplicateMergeFunctionTest {

  @Test
  void testMerge() {

    List<String> list = Lists.newArrayList("C", "B", "A");
    SortedMap<String, String> sortedMap = list.stream()
        .collect(Collectors.toMap(s -> "key:" + s, Function.identity(), new NoDuplicateMergeFunction<>(), TreeMap::new));
    List<String> orderedList = new ArrayList<>(sortedMap.keySet());
    assertThat(Lists.newArrayList("key:A", "key:B", "key:C"), equalTo(orderedList));
  }

  @Test
  void testMergeDup() {

    Assertions.assertThrows(IllegalStateException.class, () -> {
      List<String> list = Lists.newArrayList("C", "C", "A");
      Map rez = list.stream()
          .collect(Collectors.toMap(s -> "key:" + s, Function.identity(), new NoDuplicateMergeFunction<>(), TreeMap::new));
      System.out.println(rez.size());
    });
  }

  @Test
  void testMergeDupMessageDoesNotClaimToShowKey() {
    // apply() only ever receives the colliding VALUES (per Collectors.toMap()'s merge-function contract), not
    // the key - the message must not mislabel a value as "the duplicate key", which differs from the actual
    // map key whenever the key-mapper isn't the identity function (as in this class's own javadoc example)
    List<String> list = Lists.newArrayList("C", "C", "A");
    IllegalStateException ex = Assertions.assertThrows(IllegalStateException.class, () ->
        list.stream().collect(Collectors.toMap(s -> "key:" + s, Function.identity(), new NoDuplicateMergeFunction<>(), TreeMap::new)));
    assertThat(ex.getMessage(), equalTo("Duplicate value C (colliding with C)"));
  }

  @Test
  void testMergeDupMessageIncludesBothCollidingValues() {
    // Collectors.toMap()'s own default merge function reports both colliding values in its message; dropping
    // the second makes a collision much harder to diagnose whenever the two colliding values actually differ
    List<Integer> list = Lists.newArrayList(1, 11);
    IllegalStateException ex = Assertions.assertThrows(IllegalStateException.class, () ->
        list.stream().collect(Collectors.toMap(i -> i % 10, Function.identity(), new NoDuplicateMergeFunction<>(), TreeMap::new)));
    assertThat(ex.getMessage(), equalTo("Duplicate value 1 (colliding with 11)"));
  }
}
