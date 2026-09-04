package org.pharmgkb.common.comparator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * JUnit test for {@link ChromosomeNameComparator}.
 *
 * @author Mark Woon
 */
class ChromosomeNameComparatorTest {


  @Test
  void testComparator() {

    //noinspection EqualsWithItself
    assertEquals(0, ChromosomeNameComparator.getComparator().compare(null, null));
    assertEquals(-1, ChromosomeNameComparator.getComparator().compare(null, "chr1"));
    assertEquals(1, ChromosomeNameComparator.getComparator().compare("chr1", null));

    // new String(...) (not a literal) defeats interning, so this doesn't take the leading
    // if (name1 == name2) return 0; identity short-circuit, which would let the parsing logic below go
    // untested (same footgun round 11 fixed for ChromosomePositionComparatorTest.testLongChromosomeName)
    assertEquals(0, ChromosomeNameComparator.getComparator().compare(new String("chr1"), new String("chr1")));
    assertEquals(-1, ChromosomeNameComparator.getComparator().compare("chr1", "chr3"));
    assertEquals(1, ChromosomeNameComparator.getComparator().compare("chr13", "chr1"));
    assertEquals(-1, ChromosomeNameComparator.getComparator().compare("chr13", "chr21"));
    assertEquals(-1, ChromosomeNameComparator.getComparator().compare("chr13", "chrX"));
    assertEquals(-1, ChromosomeNameComparator.getComparator().compare("chr20", "chrX"));
    assertEquals(-1, ChromosomeNameComparator.getComparator().compare("chrX", "chrY"));
    assertEquals(0, ChromosomeNameComparator.getComparator().compare(new String("chrX"), new String("chrX")));

    assertEquals(0, ChromosomeNameComparator.getComparator().compare(new String("1"), new String("1")));
    assertEquals(-1, ChromosomeNameComparator.getComparator().compare("1", "3"));
    assertEquals(1, ChromosomeNameComparator.getComparator().compare("13", "1"));
    assertEquals(-1, ChromosomeNameComparator.getComparator().compare("13", "21"));
    assertEquals(-1, ChromosomeNameComparator.getComparator().compare("13", "X"));
    assertEquals(-1, ChromosomeNameComparator.getComparator().compare("20", "X"));
    assertEquals(1, ChromosomeNameComparator.getComparator().compare("X", "20"));
    assertEquals(-1, ChromosomeNameComparator.getComparator().compare("X", "Y"));
    assertEquals(0, ChromosomeNameComparator.getComparator().compare(new String("X"), new String("X")));
  }

  @Test
  void testMitochondrial() {

    // MT/M sorts after X and Y, not lexicographically before X
    assertEquals(1, ChromosomeNameComparator.getComparator().compare("chrMT", "chrX"));
    assertEquals(-1, ChromosomeNameComparator.getComparator().compare("chrX", "chrMT"));
    assertEquals(1, ChromosomeNameComparator.getComparator().compare("MT", "Y"));
    assertEquals(1, ChromosomeNameComparator.getComparator().compare("chrM", "chrY"));
    assertEquals(1, ChromosomeNameComparator.getComparator().compare("MT", "13"));
    assertEquals(0, ChromosomeNameComparator.getComparator().compare(new String("MT"), new String("MT")));
  }

  @Test
  void testCasePrefixInsensitive() {

    // the "chr" prefix strip must be case-insensitive, so casing doesn't change numeric or MT/M detection
    //noinspection EqualsWithItself
    assertEquals(0, ChromosomeNameComparator.getComparator().compare("CHR1", "chr1"));
    assertEquals(-1, ChromosomeNameComparator.getComparator().compare("CHR1", "CHR3"));
    assertEquals(1, ChromosomeNameComparator.getComparator().compare("CHRMT", "CHRX"));
    assertEquals(1, ChromosomeNameComparator.getComparator().compare("Chrmt", "ChrY"));
    // every assertion above only ever puts the uppercase "chr" form in the FIRST position - a comparator that
    // only strips the prefix case-insensitively from the first operand (leaving the second operand's own
    // strip case-sensitive) would still pass every one of them. This one puts the uppercase form second, and
    // is chosen so the wrong (unstripped) second operand actually flips the sign of the result, not just
    // happens to agree with it
    assertEquals(1, ChromosomeNameComparator.getComparator().compare("chr3", "CHR1"));

    // X/Y and MT/M themselves must compare case-insensitively too, not just the "chr" prefix
    //noinspection EqualsWithItself
    assertEquals(0, ChromosomeNameComparator.getComparator().compare("chrX", "chrx"));
    //noinspection EqualsWithItself
    assertEquals(0, ChromosomeNameComparator.getComparator().compare("chrY", "chry"));
    //noinspection EqualsWithItself
    assertEquals(0, ChromosomeNameComparator.getComparator().compare("chrMT", "chrmt"));
    //noinspection EqualsWithItself
    assertEquals(0, ChromosomeNameComparator.getComparator().compare("X", "x"));
    //noinspection EqualsWithItself
    assertEquals(0, ChromosomeNameComparator.getComparator().compare("Y", "y"));
  }

  @Test
  void testLeadingZeroNotEqual() {
    // "1" and "01" are different strings and must not compare as equal
    assertNotEquals(0, ChromosomeNameComparator.getComparator().compare("1", "01"));
    assertNotEquals(0, ChromosomeNameComparator.getComparator().compare("chr1", "chr01"));
    // the sign of that raw-digit tie-break (name1.compareTo(name2), a plain lexicographic compare) was itself
    // unpinned (round 25) - a reversed tie-break would still satisfy "not equal" above without anyone
    // noticing. "1".compareTo("01") is positive (a leading '0' sorts before '1' character-wise), so "01"
    // sorts before "1"
    assertTrue(ChromosomeNameComparator.getComparator().compare("1", "01") > 0);
    assertTrue(ChromosomeNameComparator.getComparator().compare("01", "1") < 0);
  }

  @Test
  void testScaffoldNamesDifferingOnlyByCaseAreNotEqual() {
    // arbitrary non-canonical (non-numeric, non-X/Y/M/MT) names, like scaffold/contig identifiers, must not
    // collapse to equal just because they're case-insensitively the same - unlike X/x and MT/mt (canonical
    // names this comparator deliberately treats as literally the same chromosome regardless of case), two
    // distinct-case scaffold names are two different strings and a TreeSet must not silently drop one
    assertNotEquals(0, ChromosomeNameComparator.getComparator().compare("chrUn_GL000195v1", "chrUn_gl000195v1"));
    // the sign of that same tie-break was itself unpinned too (round 25) - "G".compareTo("g") is negative
    // (uppercase sorts before lowercase character-wise), so the uppercase form sorts first
    assertTrue(ChromosomeNameComparator.getComparator().compare("chrUn_GL000195v1", "chrUn_gl000195v1") < 0);
    assertTrue(ChromosomeNameComparator.getComparator().compare("chrUn_gl000195v1", "chrUn_GL000195v1") > 0);
    // canonical names must still compare case-insensitively-equal (unaffected by the tie-break above)
    //noinspection EqualsWithItself
    assertEquals(0, ChromosomeNameComparator.getComparator().compare("chrX", "chrx"));
    //noinspection EqualsWithItself
    assertEquals(0, ChromosomeNameComparator.getComparator().compare("chrMT", "chrmt"));
  }

  @Test
  void testNumberBeyondIntegerRange() {
    // chromosome numbers larger than Integer.MAX_VALUE (2147483647) must still be comparable, not throw
    assertEquals(-1, ChromosomeNameComparator.getComparator().compare("chr1", "chr123456789012"));
  }

  @Test
  void testNumberBeyondLongRangeThrows() {
    String tooLarge = "chr" + "9".repeat(30);
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
        ChromosomeNameComparator.getComparator().compare("chr1", tooLarge));
    assertTrue(ex.getMessage().contains(tooLarge), ex.getMessage());
  }
}
