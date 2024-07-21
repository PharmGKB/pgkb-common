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
  void testReference() {
    // cannot use 2 strings or compiler will optimize it away and use the same String object
    String[] dip = "Reference/Reference".split("/");
    assertEquals(0, HaplotypeNameComparator.getComparator().compare(dip[0], dip[1]));
  }

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

  /**
   * This test demonstrates how the {@link HaplotypeNameComparator} handles a mix of different styles of haplotype names
   * all in one collection.
   */
  @Test
  void testMixedNomenclature() {
    String na1 = "c.1371C>T";
    String na2 = "c.557A>G";
    String na3 = "c.1627A>G (*5)";
    String na4 = "c.85T>C (*9 A)";

    List<String> items = new ArrayList<>();
    items.add(na1);
    items.add(na2);
    items.add(na3);
    items.add(na4);
    items.sort(HaplotypeNameComparator.getComparator());
    System.out.println(items);

    List<String> checkItems = new ArrayList<>();
    checkItems.add(na4);
    checkItems.add(na3);
    checkItems.add(na2);
    checkItems.add(na1);
    checkItems.sort(HaplotypeNameComparator.getComparator());
    System.out.println(checkItems);
    assertEquals(items, checkItems);
  }

  @Test
  void testCombinations() {
    String na1 = "*3 + c.123A>T";
    String na2 = "*1";
    String na3 = "*3 + c.125C>G";
    String na4 = "*3 + c.123T>C";
    String na5 = "c.123A>T (*9a)";
    String na6 = "c.123A>T";

    List<String> expected = new ArrayList<>();
    expected.add(na2);
    expected.add(na1);
    expected.add(na4);
    expected.add(na3);
    expected.add(na6);
    expected.add(na5);

    List<String> items = new ArrayList<>();
    items.add(na1);
    items.add(na2);
    items.add(na3);
    items.add(na4);
    items.add(na5);
    items.add(na6);
    items.sort(HaplotypeNameComparator.getComparator());
    System.out.println(items);
    assertEquals(expected, items);

    List<String> checkItems = new ArrayList<>();
    checkItems.add(na6);
    checkItems.add(na5);
    checkItems.add(na4);
    checkItems.add(na3);
    checkItems.add(na2);
    checkItems.add(na1);
    checkItems.sort(HaplotypeNameComparator.getComparator());
    System.out.println(checkItems);
    assertEquals(items, checkItems);
  }
}
