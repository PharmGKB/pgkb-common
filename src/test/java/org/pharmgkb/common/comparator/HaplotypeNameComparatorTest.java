package org.pharmgkb.common.comparator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * JUnit test for {@link HaplotypeNameComparator}.
 *
 * @author Mark Woon
 */
class HaplotypeNameComparatorTest {


  @Test
  void testComparator() {

    //noinspection EqualsWithItself
    assertEquals(0, HaplotypeNameComparator.getComparator().compare(null, null));
    assertEquals(-1, HaplotypeNameComparator.getComparator().compare(null, "*1"));
    assertEquals(1, HaplotypeNameComparator.getComparator().compare("*1", null));

    //noinspection EqualsWithItself
    assertEquals(0, HaplotypeNameComparator.getComparator().compare("*1", "*1"));
    assertEquals(-1, HaplotypeNameComparator.getComparator().compare("*1", "*11"));
    assertEquals(-1, HaplotypeNameComparator.getComparator().compare("*1", "*100"));
    assertEquals(-1, HaplotypeNameComparator.getComparator().compare("*2", "*11"));
    assertEquals(1, HaplotypeNameComparator.getComparator().compare("*13", "*3"));
    assertEquals(1, HaplotypeNameComparator.getComparator().compare("H3", "H2"));

    assertEquals(-1, HaplotypeNameComparator.getComparator().compare("Any", "*1"));
    assertEquals(1, HaplotypeNameComparator.getComparator().compare("*1", "Any"));
    assertEquals(-1, HaplotypeNameComparator.getComparator().compare("*1", "Other"));
    assertEquals(1, HaplotypeNameComparator.getComparator().compare("Unknown", "*1"));

    assertEquals(-1, HaplotypeNameComparator.getComparator().compare("CYP2C6*2", "CYP2D6*2"));
    assertEquals(1, HaplotypeNameComparator.getComparator().compare("CYP2D6*10", "CYP2D6*2"));

    //noinspection EqualsWithItself
    assertEquals(0, HaplotypeNameComparator.getComparator().compare("H2 EM", "H2 EM"));
    assertTrue(HaplotypeNameComparator.getComparator().compare("H2 EM", "H2 PM") < 0);
    assertTrue(HaplotypeNameComparator.getComparator().compare("H2 PM", "H2 EM") > 0);

    assertTrue(HaplotypeNameComparator.getComparator().compare("*1", "H2") < 0);
    assertTrue(HaplotypeNameComparator.getComparator().compare("*1", "TPMT") < 0);
    assertTrue(HaplotypeNameComparator.getComparator().compare("TPMT", "BRCA") > 0);

    assertEquals(1, HaplotypeNameComparator.getComparator().compare("Unknown", "Unknown function"));
  }

  @Test
  void testCollectionSorting() {
    List<String> input = new ArrayList<>();
    input.add("*3");
    input.add("Other");
    input.add("*100");
    input.add("Any");
    input.add("*1");

    Set<String> orderedSet = new TreeSet<>(HaplotypeNameComparator.getComparator());
    orderedSet.addAll(input);

    Iterator<String> it = orderedSet.iterator();
    assertEquals("Any", it.next());
    assertEquals("*1", it.next());
    assertEquals("*3", it.next());
    assertEquals("*100", it.next());
    assertEquals("Other", it.next());
  }
}
