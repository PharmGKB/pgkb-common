package org.pharmgkb.common.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * This is a JUnit test for {@link ComparatorUtils}.
 *
 * @author Mark Woon
 */
class ComparatorUtilsTest {


  @Test
  void testCompareNumber() {

    assertEquals(-1, ComparatorUtils.compareNumbers("1", "2"));
    assertEquals(1, ComparatorUtils.compareNumbers("11", "2"));
    assertEquals(-1, ComparatorUtils.compareNumbers("1.2", "2"));
    assertEquals(-1, ComparatorUtils.compareNumbers("1.2", "2.3"));
    assertEquals(-1, ComparatorUtils.compareNumbers("1.2", "2.0"));
    assertEquals(-1, ComparatorUtils.compareNumbers("2", "11"));
    assertEquals(1, ComparatorUtils.compareNumbers("2", "1.2"));
    assertEquals(1, ComparatorUtils.compareNumbers("2.3", "1.2"));
    assertEquals(1, ComparatorUtils.compareNumbers("2.0", "1.2"));
  }


  @Test
  void compareMap() {

    Map<String, List<String>> m1 = new HashMap<>();
    Map<String, List<String>> m2 = new HashMap<>();
    assertEquals(0, ComparatorUtils.compareMap(m1, m2));

    m1.put("a", new ArrayList<>());
    assertEquals(1, ComparatorUtils.compareMap(m1, m2));

    m2.put("a", new ArrayList<>());
    assertEquals(0, ComparatorUtils.compareMap(m1, m2));

    m1.get("a").add("1");
    assertEquals(1, ComparatorUtils.compareMap(m1, m2));

    m2.get("a").add("1");
    assertEquals(0, ComparatorUtils.compareMap(m1, m2));

    m2.put("b", new ArrayList<>());
    assertEquals(-1, ComparatorUtils.compareMap(m1, m2));

    m1.put("b", new ArrayList<>());
    m1.get("b").add("2");
    assertEquals(1, ComparatorUtils.compareMap(m1, m2));

    m2.get("b").add("1");
    assertEquals(1, ComparatorUtils.compareMap(m1, m2));

    m2.put("b", new ArrayList<>());
    m2.get("b").add("3");
    assertEquals(-1, ComparatorUtils.compareMap(m1, m2));
  }



  @Test
  void compareCollectionOfMaps() {

    List<Map<String, List<String>>> a = new ArrayList<>();
    List<Map> b = new ArrayList<>();

    Map<String, List<String>> m1 = new HashMap<>();
    Map<String, List<String>> m2 = new HashMap<>();
    a.add(m1);
    b.add(m2);
    assertEquals(0, ComparatorUtils.compareCollectionOfMaps(a, b));

    m1.put("a", new ArrayList<>());
    assertEquals(1, ComparatorUtils.compareCollectionOfMaps(a, b));

    m2.put("a", new ArrayList<>());
    assertEquals(0, ComparatorUtils.compareCollectionOfMaps(a, b));

    m1.get("a").add("1");
    assertEquals(1, ComparatorUtils.compareCollectionOfMaps(a, b));

    m2.get("a").add("1");
    assertEquals(0, ComparatorUtils.compareCollectionOfMaps(a, b));

    m2.put("b", new ArrayList<>());
    assertEquals(-1, ComparatorUtils.compareCollectionOfMaps(a, b));

    m1.put("b", new ArrayList<>());
    m1.get("b").add("2");
    assertEquals(1, ComparatorUtils.compareCollectionOfMaps(a, b));

    m2.get("b").add("1");
    assertEquals(1, ComparatorUtils.compareCollectionOfMaps(a, b));

    m2.put("b", new ArrayList<>());
    m2.get("b").add("3");
    assertEquals(-1, ComparatorUtils.compareCollectionOfMaps(a, b));


    m1 = new HashMap<>();
    a.add(m1);
    assertEquals(1, ComparatorUtils.compareCollectionOfMaps(a, b));


    m2 = new HashMap<>();
    b.add(m2);
    assertEquals(-1, ComparatorUtils.compareCollectionOfMaps(a, b));
  }

}
