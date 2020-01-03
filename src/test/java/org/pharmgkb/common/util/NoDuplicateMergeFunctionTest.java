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

    Assertions.assertThrows(RuntimeException.class, () -> {
      List<String> list = Lists.newArrayList("C", "C", "A");
      Map rez = list.stream()
          .collect(Collectors.toMap(s -> "key:" + s, Function.identity(), new NoDuplicateMergeFunction<>(), TreeMap::new));
      System.out.println(rez.size());
    });
  }
}
