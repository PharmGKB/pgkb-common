package org.pharmgkb.common.comparator;

import java.util.TreeSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * JUnit test for {@link NaturalStringComparator}.
 *
 * @author Mark Woon
 */
class NaturalStringComparatorTest {

  @Test
  void testComparator() {

    assertEquals(0, NaturalStringComparator.getComparator().compare(null, null));
    assertEquals(-1, NaturalStringComparator.getComparator().compare(null, "*1"));
    assertEquals(1, NaturalStringComparator.getComparator().compare("*1", null));

    assertTrue(NaturalStringComparator.getComparator().compare("*2", "*11") < 0);
    assertTrue(NaturalStringComparator.getComparator().compare("*11", "*2") > 0);
  }

  @Test
  void testNumberBeyondIntegerRange() {
    // numeric tokens larger than Integer.MAX_VALUE (2147483647) must still be comparable, not throw
    assertTrue(NaturalStringComparator.getComparator().compare("rs2", "rs123456789012") < 0);
  }

  @Test
  void testLeadingZeroNotEqual() {
    // "*1" and "*01" are different strings and must not compare as equal
    assertNotEquals(0, NaturalStringComparator.getComparator().compare("*1", "*01"));
    // the sign of that raw-digit tie-break (num1.raw().compareTo(num2.raw()), a plain lexicographic compare
    // of the digit substrings) was itself unpinned (round 25) - a reversed tie-break would still satisfy
    // "not equal" above without anyone noticing. "01".compareTo("1") is negative (a leading '0' sorts before
    // '1' character-wise), so "*01" sorts before "*1"
    assertTrue(NaturalStringComparator.getComparator().compare("*1", "*01") > 0);
    assertTrue(NaturalStringComparator.getComparator().compare("*01", "*1") < 0);

    // demonstrates the real-world failure: TreeSet silently drops the second value
    TreeSet<String> set = new TreeSet<>(NaturalStringComparator.getComparator());
    set.add("*1");
    assertTrue(set.add("*01"));
    assertEquals(2, set.size());
  }

  @Test
  void testNumberBeyondLongRangeThrows() {
    String tooLarge = "rs" + "9".repeat(30);
    IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class, () ->
        NaturalStringComparator.getComparator().compare("rs2", tooLarge));
    assertTrue(ex.getMessage().contains(tooLarge), ex.getMessage());
  }

  @Test
  void testStringPartSortsAfterNumericPartAtSamePosition() {
    // documented rule (compare()'s own "XXXa > XXX1" comment): when the same token position is a numeric
    // part in one operand and a plain string part in the other, the string part sorts AFTER the numeric one
    assertTrue(NaturalStringComparator.getComparator().compare("a1", "1a") > 0);
    assertTrue(NaturalStringComparator.getComparator().compare("1a", "a1") < 0);
  }

  @Test
  void testPrefixOperandSortsFirst() {
    // documented rule (compare()'s own "return parts1.isEmpty() ? -1 : 1;" tail): when one operand's parts
    // are exhausted first but the other still has a trailing part, the shorter/prefix operand sorts first -
    // e.g. real allele/haplotype nomenclature like "*1" before "*1A", or "H2" before "H2 EM"
    assertTrue(NaturalStringComparator.getComparator().compare("*1", "*1A") < 0);
    assertTrue(NaturalStringComparator.getComparator().compare("*1A", "*1") > 0);
  }
}
