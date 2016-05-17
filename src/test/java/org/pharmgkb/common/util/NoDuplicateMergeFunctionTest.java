package org.pharmgkb.common.util;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.google.common.collect.Lists;
import org.junit.Test;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;


/**
 * JUnit test for {@link NoDuplicateMergeFunction}.
 *
 * @author Mark Woon
 */
public class NoDuplicateMergeFunctionTest {

  @Test
  public void testMerge() {

    List<String> list = Lists.newArrayList("C", "B", "A");
    SortedMap<String, String> sortedMap = list.stream()
        .collect(Collectors.toMap(s -> "key:" + s, Function.identity(), new NoDuplicateMergeFunction<>(), TreeMap::new));
    List<String> orderedList = new ArrayList<>(sortedMap.keySet());
    assertThat(Lists.newArrayList("key:A", "key:B", "key:C"), equalTo(orderedList));
  }

  @Test(expected = RuntimeException.class)
  public void testMergeDup() {

    List<String> list = Lists.newArrayList("C", "C", "A");
    list.stream()
        .collect(Collectors.toMap(s -> "key:" + s, Function.identity(), new NoDuplicateMergeFunction<>(), TreeMap::new));
    fail("Should never get here");
  }
}
