package org.pharmgkb.common.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * @author Mark Woon
 */
class NullSafeComparisonChainTest {

  @Test
  void testChain() {
    assertTrue(new NullSafeComparisonChain()
        .compare("a", "A")
        .result() > 0);

    assertEquals(0, new NullSafeComparisonChain()
        .compareIgnoreCase("a", "A")
        .result());

    // round 34 finding 3: every existing compareIgnoreCase() use in this file expects exactly 0, so
    // NullSafeComparisonChain.compareIgnoreCase()'s own delegation to ComparatorUtils.compareIgnoreCase() was never
    // actually observed to carry through a non-zero result - mutation-verified: making it a complete no-op
    // (never touching m_comparison at all) left the whole suite green
    assertTrue(new NullSafeComparisonChain()
        .compareIgnoreCase("a", "B")
        .result() < 0);
    assertTrue(new NullSafeComparisonChain()
        .compareIgnoreCase("B", "a")
        .result() > 0);

    assertTrue(new NullSafeComparisonChain()
        .compare("a", "b")
        .result() < 0);

    assertTrue(new NullSafeComparisonChain()
        .compare("1", "2")
        .result() < 0);

    assertTrue(new NullSafeComparisonChain()
        .compare("12", "2")
        .result() < 0);

    assertTrue(new NullSafeComparisonChain()
        .compareNumbers("12", "2")
        .result() > 0);


    List<String> listA = new ArrayList<>();
    listA.add("a");
    List<String> listB = new ArrayList<>();
    listB.add("b");

    assertEquals(1, new NullSafeComparisonChain()
        .compare("a", "a")
        .compare(listB, listA)
        .result());

    assertEquals(1, new NullSafeComparisonChain()
        .compare(listB, listA)
        .compare("a", "b")
        .result());


    Map<String, String> mapA = new HashMap<>();
    mapA.put("a", "A");
    Map<String, String> mapB = new HashMap<>();
    mapB.put("b", "B");

    assertEquals(-1, new NullSafeComparisonChain()
        .compare(listA, listA)
        .compare(mapA, mapB)
        .result());


    List<Map<String, String>> listMapA = new ArrayList<>();
    listMapA.add(mapA);
    List<Map<String, String>> listMapB = new ArrayList<>();
    listMapB.add(mapB);
    assertEquals(-1, new NullSafeComparisonChain()
        .compareIgnoreCase("a", "A")
        .compare(listB, listB)
        .compareCollectionOfMaps(listMapA, listMapB)
        .result());


    assertEquals(-1, new NullSafeComparisonChain()
        .compareChromosomePositions("chr1:4", "chr1:100")
        .result());
    assertEquals(1, new NullSafeComparisonChain()
        .compareChromosomePositions("chr4:100", "chr1:400")
        .result());


    assertEquals(-1, new NullSafeComparisonChain()
        .compareChromosomeNames("chr2", "chr11")
        .result());
    assertEquals(1, new NullSafeComparisonChain()
        .compareHaplotypeNames("chr11", "chr2")
        .result());
  }

  @Test
  void testShortCircuitSkipsAllChainedMethodsOnceDecided() {
    // every chain method opens with "if (m_comparison != 0) { return this; }" - once an earlier call has
    // already decided the result, every later chained call must be skipped entirely (not just its result
    // discarded): verified here by passing each one arguments that would both flip the already-decided
    // result AND (for compareChromosomePositions) throw on a malformed input, if actually evaluated
    assertEquals(1, new NullSafeComparisonChain().compare("b", "a").compareIgnoreCase("a", "b").result());
    assertEquals(1, new NullSafeComparisonChain().compare("b", "a").compareNumbers("1", "2").result());
    assertEquals(1, new NullSafeComparisonChain().compare("b", "a")
        .compare(List.of(1), List.of(2))
        .result());
    assertEquals(1, new NullSafeComparisonChain().compare("b", "a")
        .compare(Map.of("a", 1), Map.of("a", 2))
        .result());
    assertEquals(1, new NullSafeComparisonChain().compare("b", "a")
        .compareCollectionOfMaps(List.of(Map.of("a", 1)), List.of(Map.of("a", 2)))
        .result());
    assertEquals(1, new NullSafeComparisonChain().compare("b", "a")
        .compareChromosomeNames("chr1", "chr2")
        .result());
    assertEquals(1, new NullSafeComparisonChain().compare("b", "a")
        .compareChromosomePositions("not-a-position", "chr1:1")
        .result());
    assertEquals(1, new NullSafeComparisonChain().compare("b", "a")
        .compareHaplotypeNames("*1", "*2")
        .result());
  }
}
