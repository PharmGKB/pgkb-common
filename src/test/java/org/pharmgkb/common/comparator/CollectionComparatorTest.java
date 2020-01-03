package org.pharmgkb.common.comparator;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * This is a JUnit test for {@link CollectionComparator}.
 *
 * @author Mark Woon
 */
class CollectionComparatorTest {


  @Test
  void testNatural() {

    Set<Integer> a = new HashSet<>();
    Set<Integer> b = new HashSet<>();

    assertEquals(0, CollectionComparator.getComparator().compare(a, b));
    a.add(1);
    assertEquals(1, CollectionComparator.getComparator().compare(a, b));
    b.add(1);
    assertEquals(0, CollectionComparator.getComparator().compare(a, b));
    b.remove(1);
    b.add(2);
    assertEquals(-1, CollectionComparator.getComparator().compare(a, b));
    a.add(2);
    assertEquals(1, CollectionComparator.getComparator().compare(a, b));
  }

  @Test
  void testReverse() {

    Set<Integer> a = new HashSet<>();
    Set<Integer> b = new HashSet<>();
    CollectionComparator comparator = new CollectionComparator(SortOrder.REVERSE);

    assertEquals(0, comparator.compare(a, b));
    a.add(1);
    assertEquals(-1, comparator.compare(a, b));
    b.add(1);
    assertEquals(0, comparator.compare(a, b));
    b.remove(1);
    b.add(2);
    assertEquals(1, comparator.compare(a, b));
    a.add(2);
    assertEquals(-1, comparator.compare(a, b));
  }
}
