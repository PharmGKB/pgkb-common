package org.pharmgkb.common.comparator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * Junit test for {@link ChromosomePositionComparator}.
 *
 * @author Mark Woon
 */
class ChromosomePositionComparatorTest {


  @Test
  void testComparator() {

    //noinspection EqualsWithItself
    assertEquals(0, ChromosomePositionComparator.getComparator().compare(null, null));
    assertEquals(-1, ChromosomePositionComparator.getComparator().compare(null, "chr1:1"));
    assertEquals(1, ChromosomePositionComparator.getComparator().compare("chr1:1", null));

    assertEquals(-1, ChromosomePositionComparator.getComparator().compare("chr1:4", "chr1:100"));
    // new String(...) (not a literal) defeats interning so this doesn't take the leading if (o1 == o2)
    // return 0; identity short-circuit - see testLongChromosomeName below for the same footgun
    assertEquals(0, ChromosomePositionComparator.getComparator().compare(new String("chr1:4"), new String("chr1:4")));
    assertEquals(1, ChromosomePositionComparator.getComparator().compare("chr3:4", "chr1:4"));
    assertEquals(1, ChromosomePositionComparator.getComparator().compare("chr4:100", "chr1:400"));
  }


  @Test
  void testChrPrefixIsOptional() {
    // the pattern's "chr" prefix is optional ("(?:chr)?") - every other test here uses the "chr"-prefixed
    // form exclusively, so the bare form (and the two forms' equivalence) had zero coverage
    assertEquals(-1, ChromosomePositionComparator.getComparator().compare("1:100", "2:100"));
    assertEquals(0, ChromosomePositionComparator.getComparator().compare(new String("1:100"), new String("chr1:100")));
  }

  @Test
  void testBadArg1() throws IllegalArgumentException {
    Assertions.assertThrows(IllegalArgumentException.class, () -> {
      ChromosomePositionComparator.getComparator().compare("chr1", "chr1:100");
    });
  }


  @Test
  void testBadArg2() throws IllegalArgumentException {
    Assertions.assertThrows(IllegalArgumentException.class, () -> {
      ChromosomePositionComparator.getComparator().compare("chr1:1", ":100");
    });
  }

  @Test
  void testPositionBeyondIntegerRange() {
    // positions larger than Integer.MAX_VALUE (2147483647) must still be comparable, not throw
    assertEquals(-1, ChromosomePositionComparator.getComparator().compare("chr1:4", "chr1:123456789012"));
  }

  @Test
  void testPositionBeyondLongRangeThrows() {
    String tooLarge = "chr1:" + "9".repeat(30);
    IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class, () ->
        ChromosomePositionComparator.getComparator().compare("chr1:4", tooLarge));
    Assertions.assertTrue(ex.getMessage().contains(tooLarge),
        "expected message to contain '" + tooLarge + "' but was: " + ex.getMessage());
  }

  @Test
  void testChromosomeNumberBeyondLongRangeMessageNamesFullPosition() {
    // the chromosome-number half of the string is passed to ChromosomeNameComparator without the "chr:"
    // position part, so its own overflow message only names the bare number - the caller can't tell which
    // position string in their data caused it. The full original position string must appear in the message.
    String tooLarge = "chr" + "9".repeat(30) + ":100";
    IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class, () ->
        ChromosomePositionComparator.getComparator().compare("chr1:4", tooLarge));
    Assertions.assertTrue(ex.getMessage().contains(tooLarge),
        "expected message to contain full position '" + tooLarge + "' but was: " + ex.getMessage());
  }

  @Test
  void testCasePrefixInsensitive() {
    // an uppercase/mixed-case "chr" prefix must still compare equal to its lowercase form - though this is
    // actually guaranteed by the DELEGATED ChromosomeNameComparator.compare() stripping "chr" case-
    // insensitively itself (regionMatches(true, ...)), not by this class's own Pattern.CASE_INSENSITIVE flag:
    // mutation-verified that removing that flag from sf_pattern leaves this specific assertion passing (the
    // flag still matters for its own edge cases, e.g. a doubled "CHRchr1:4" prefix - see known non-issue #15)
    //noinspection EqualsWithItself
    assertEquals(0, ChromosomePositionComparator.getComparator().compare("CHR1:4", "chr1:4"));
    assertEquals(-1, ChromosomePositionComparator.getComparator().compare("CHR1:4", "CHR1:100"));
    // X/Y/MT chromosome letters must also compare case-insensitively, not just the "chr" prefix
    //noinspection EqualsWithItself
    assertEquals(0, ChromosomePositionComparator.getComparator().compare("chrX:4", "chrx:4"));
  }

  @Test
  void testLeadingZeroNotEqual() {
    // "01" and "1" parse to the same Long position value, but the strings aren't equal - same footgun already
    // fixed for NaturalStringComparator/ChromosomeNameComparator's numeric tokens
    int rez = ChromosomePositionComparator.getComparator().compare("chr1:01", "chr1:1");
    int reverseRez = ChromosomePositionComparator.getComparator().compare("chr1:1", "chr1:01");
    Assertions.assertNotEquals(0, rez);
    Assertions.assertNotEquals(0, reverseRez);
    // must stay antisymmetric
    Assertions.assertEquals(Integer.signum(rez), -Integer.signum(reverseRez));
    // the antisymmetry check above would still pass even if the tie-break's sign were reversed (round 25) -
    // pin the actual direction too: "01".compareTo("1") is negative (a leading '0' sorts before '1'
    // character-wise), so "chr1:01" sorts before "chr1:1"
    Assertions.assertTrue(rez < 0);
  }

  @Test
  void testLongChromosomeName() {
    // chromosome names/scaffold IDs longer than 2 characters (e.g. >99-chromosome genomes, or contig IDs)
    // must still be recognized, not just 1-2 character names. new String(...) (not a literal) is used for
    // the equal-value cases so they don't take the leading if (o1 == o2) return 0; identity short-circuit,
    // which would let the regex-parsing logic these cases are meant to exercise go untested
    assertEquals(0, ChromosomePositionComparator.getComparator().compare(
        new String("chr123:4"), new String("chr123:4")));
    assertEquals(-1, ChromosomePositionComparator.getComparator().compare("chr123:4", "chr123:100"));
    assertEquals(0, ChromosomePositionComparator.getComparator().compare(
        new String("chr1_KI270706v1_random:100"), new String("chr1_KI270706v1_random:100")));
  }
}
