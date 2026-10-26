package org.pharmgkb.common.util;

import java.util.ArrayList;
import java.util.List;
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

    assertTrue(new NullSafeComparisonChain()
        .compare("a", "b")
        .result() < 0);

    assertTrue(new NullSafeComparisonChain()
        .compare("1", "2")
        .result() < 0);

    assertTrue(new NullSafeComparisonChain()
        .compare("12", "2")
        .result() < 0);


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


    assertEquals(-1, new NullSafeComparisonChain()
        .compareChromosomeNames("chr2", "chr11")
        .result());
    assertEquals(1, new NullSafeComparisonChain()
        .compareHaplotypeNames("chr11", "chr2")
        .result());
  }

  @Test
  void testShortCircuitSkipsAllChainedMethodsOnceDecided() {
    // once an earlier call has decided the result, every later chained call must be skipped, so each is passed
    // arguments that would flip the result if it were evaluated
    assertEquals(1, new NullSafeComparisonChain().compare("b", "a")
        .compare(List.of(1), List.of(2))
        .result());
    assertEquals(1, new NullSafeComparisonChain().compare("b", "a")
        .compareChromosomeNames("chr1", "chr2")
        .result());
    assertEquals(1, new NullSafeComparisonChain().compare("b", "a")
        .compareHaplotypeNames("*1", "*2")
        .result());
  }
}
