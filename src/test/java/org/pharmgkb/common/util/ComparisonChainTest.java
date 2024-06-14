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
class ComparisonChainTest {

  @Test
  void testChain() {
    assertTrue(new ComparisonChain()
        .compare("a", "A")
        .result() > 0);

    assertEquals(0, new ComparisonChain()
        .compareIgnoreCase("a", "A")
        .result());

    assertTrue(new ComparisonChain()
        .compare("a", "b")
        .result() < 0);

    assertTrue(new ComparisonChain()
        .compare("1", "2")
        .result() < 0);

    assertTrue(new ComparisonChain()
        .compare("12", "2")
        .result() < 0);

    assertTrue(new ComparisonChain()
        .compareNumbers("12", "2")
        .result() > 0);


    List<String> listA = new ArrayList<>();
    listA.add("a");
    List<String> listB = new ArrayList<>();
    listB.add("b");

    assertEquals(1, new ComparisonChain()
        .compare("a", "a")
        .compare(listB, listA)
        .result());

    assertEquals(1, new ComparisonChain()
        .compare(listB, listA)
        .compare("a", "b")
        .result());


    Map<String, String> mapA = new HashMap<>();
    mapA.put("a", "A");
    Map<String, String> mapB = new HashMap<>();
    mapB.put("b", "B");

    assertEquals(-1, new ComparisonChain()
        .compare(listA, listA)
        .compare(mapA, mapB)
        .result());


    List<Map<String, String>> listMapA = new ArrayList<>();
    listMapA.add(mapA);
    List<Map<String, String>> listMapB = new ArrayList<>();
    listMapB.add(mapB);
    assertEquals(-1, new ComparisonChain()
        .compareIgnoreCase("a", "A")
        .compare(listB, listB)
        .compareCollectionOfMaps(listMapA, listMapB)
        .result());


    assertEquals(-1, new ComparisonChain()
        .compareChromosomePositions("chr1:4", "chr1:100")
        .result());
    assertEquals(1, new ComparisonChain()
        .compareChromosomePositions("chr4:100", "chr1:400")
        .result());


    assertEquals(-1, new ComparisonChain()
        .compareChromosomeNames("chr2", "chr11")
        .result());
    assertEquals(1, new ComparisonChain()
        .compareHaplotypeNames("chr11", "chr2")
        .result());
  }
}
