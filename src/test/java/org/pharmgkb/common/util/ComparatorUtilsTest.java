package org.pharmgkb.common.util;

import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.pharmgkb.common.comparator.ChromosomeNameComparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


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
  void testCompareNumberPrecisionBeyond2Pow53() {
    // Double.parseDouble() loses precision above 2^53, which would wrongly compare these as equal
    assertEquals(1, ComparatorUtils.compareNumbers("9007199254740993", "9007199254740992"));
    assertEquals(-1, ComparatorUtils.compareNumbers("9007199254740992", "9007199254740993"));
  }


  @Test
  void testCompareNumberNegativeZero() {
    // -0.0 == 0.0 numerically
    assertEquals(0, ComparatorUtils.compareNumbers("-0.0", "0.0"));
    assertEquals(0, ComparatorUtils.compareNumbers("0.0", "-0.0"));
  }


  @Test
  void testCompareNumberToleratesWhitespace() {
    // Double.parseDouble() (the previous implementation) tolerates leading/trailing whitespace; the switch to
    // BigDecimal (for precision) must not silently regress this, since callers elsewhere in this codebase
    // (CliHelper.getValue(), ExtendedEnumHelper.add()) already assume whitespace-padded input is normal
    assertEquals(0, ComparatorUtils.compareNumbers(" 1 ", "1"));
    assertEquals(0, ComparatorUtils.compareNumbers("1", "\t1\n"));
  }

  @Test
  void testCompareNumberStripsUnicodeWhitespace() {
    // the whitespace-tolerance fix's own rationale points at CliHelper.getValue()/ExtendedEnumHelper.add(),
    // both of which use StringUtils.stripToNull (Character.isWhitespace-based) - but String.trim() only
    // strips <= U+0020, so a Unicode whitespace char like U+2003 (EM SPACE) that those callers would treat
    // as padding still reaches BigDecimal and throws here
    assertEquals(0, ComparatorUtils.compareNumbers("\u20031", "1"));
  }

  @Test
  void testCompareNumberEmptyStringHasInformativeMessage() {
    // new BigDecimal("") throws NumberFormatException with a null message; Double.parseDouble("") (the
    // previous implementation) said "empty String" - restore that diagnosability instead of silently
    // regressing to an unhelpful null message
    NumberFormatException ex = assertThrows(NumberFormatException.class,
        () -> ComparatorUtils.compareNumbers("", "1"));
    assertEquals("empty String", ex.getMessage());
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
  void compareMapWithNullValues() {

    Map<String, String> m1 = new HashMap<>();
    Map<String, String> m2 = new HashMap<>();
    m1.put("a", null);
    m2.put("a", null);
    assertEquals(0, ComparatorUtils.compareMap(m1, m2));

    m2.put("a", "x");
    assertEquals(-1, ComparatorUtils.compareMap(m1, m2));
    assertEquals(1, ComparatorUtils.compareMap(m2, m1));
  }

  @Test
  void compareMapWithSharedNullValueOnOneKeyStillComparesOtherKeys() {
    // compareMapWithNullValues above only ever pairs two maps that are equals()-equal on their shared
    // null-valued key ({a:null} vs {a:null}) - the round-7/11 equals()-shortcut fires before the value-by-
    // value loop below ever runs, so the loop's own "if (va == null && vb == null) { rez = 0; }" branch was
    // never actually exercised by any dedicated test (round 29 finding 4, mutation-verified: changing that
    // branch's rez to 1 left the whole suite green). Here the two maps differ on a SECOND key, so they aren't
    // equals()-equal and the loop genuinely runs - a null-vs-null tie on "a" must fall through to comparing
    // "b" instead of being wrongly treated as a difference (which would return immediately, without ever
    // comparing "b" at all)
    Map<String, String> m1 = new HashMap<>();
    m1.put("a", null);
    m1.put("b", "1");
    Map<String, String> m2 = new HashMap<>();
    m2.put("a", null);
    m2.put("b", "2");
    assertEquals(-1, ComparatorUtils.compareMap(m1, m2));
    assertEquals(1, ComparatorUtils.compareMap(m2, m1));
  }

  @Test
  void compareMapWithNullMapSortsBeforeNonNullMap() {
    // a null MAP (not a null value, see compareMapWithNullValues above) sorts strictly before a non-null
    // one, including an empty map - deliberately different from compareCollection()'s null-equals-empty rule
    // (see known non-issue #28 in review.log)
    Map<String, String> empty = new HashMap<>();
    assertEquals(-1, ComparatorUtils.compareMap(null, empty));
    assertEquals(1, ComparatorUtils.compareMap(empty, null));
    assertEquals(0, ComparatorUtils.compareMap(null, null));
  }

  @Test
  void compareNumbersWithNullSortsBeforeNonNull() {
    assertEquals(-1, ComparatorUtils.compareNumbers(null, "1"));
    assertEquals(1, ComparatorUtils.compareNumbers("1", null));
    assertEquals(0, ComparatorUtils.compareNumbers(null, null));
  }

  @Test
  void compareWithNullSortsBeforeNonNull() {
    // compare()'s null handling had zero direct test coverage - it's exercised indirectly via
    // NullSafeComparisonChain.compare(), but never with a null argument there either
    assertEquals(-1, ComparatorUtils.compare(null, "a"));
    assertEquals(1, ComparatorUtils.compare("a", null));
    assertEquals(0, ComparatorUtils.compare(null, null));
  }

  @Test
  void compareIgnoreCaseWithNullSortsBeforeNonNull() {
    // same gap as compareWithNullSortsBeforeNonNull above, for compareIgnoreCase()
    assertEquals(-1, ComparatorUtils.compareIgnoreCase(null, "a"));
    assertEquals(1, ComparatorUtils.compareIgnoreCase("a", null));
    assertEquals(0, ComparatorUtils.compareIgnoreCase(null, null));
  }

  @Test
  void compareIgnoreCaseComparesActualContentIgnoringCase() {
    // round 34 finding 3: no existing test observes compareToIgnoreCase()'s actual delegated result - every
    // existing use either only exercises the null branches above or feeds it operands whose expected result
    // is already 0 (NullSafeComparisonChainTest's own uses) - mutation-verified: replacing this method's whole body
    // with "return 0;" left the entire suite green
    assertTrue(ComparatorUtils.compareIgnoreCase("a", "b") < 0);
    assertTrue(ComparatorUtils.compareIgnoreCase("b", "a") > 0);
    // must ignore case, not merely be non-zero for different content
    assertEquals(0, ComparatorUtils.compareIgnoreCase("A", "a"));
  }

  @Test
  void compareMapWithMismatchedValueTypesThrows() {

    Map<String, Object> m1 = new HashMap<>();
    Map<String, Object> m2 = new HashMap<>();
    m1.put("k", new ArrayList<>(List.of(1, 2)));
    m2.put("k", "notAList");

    assertThrows(UnsupportedOperationException.class, () -> ComparatorUtils.compareMap(m1, m2));
  }

  @Test
  void compareMapWithUnsupportedValueTypeThrows() {
    // round 34 finding 4: the OTHER "unsupported value type" throw - a value that's neither a Collection NOR
    // Comparable at all - had no test of its own. The only existing mismatched-value-type test above uses
    // Collection vs String, which takes the "va instanceof Collection" branch instead. Mutation-verified:
    // replacing this throw with "rez = 0;" left the whole suite green
    Map<String, Object> m1 = new HashMap<>();
    Map<String, Object> m2 = new HashMap<>();
    m1.put("k", new Object());
    m2.put("k", new Object());

    assertThrows(UnsupportedOperationException.class, () -> ComparatorUtils.compareMap(m1, m2));
  }

  @Test
  void compareMapWithCompareToEqualButNotEqualsKeysStaysAntisymmetric() {
    // BigDecimal("1.0").compareTo(BigDecimal("1.00")) == 0, but they're not .equals() - the key-set check
    // uses compareTo (via compareCollection), so value lookup must not assume the two maps share
    // equals()-equal key objects, or both directions can wrongly report "greater than" at once
    Map<BigDecimal, String> m1 = Map.of(new BigDecimal("1.0"), "v");
    Map<BigDecimal, String> m2 = Map.of(new BigDecimal("1.00"), "v");
    assertEquals(0, ComparatorUtils.compareMap(m1, m2));
    assertEquals(0, ComparatorUtils.compareMap(m2, m1));

    Map<BigDecimal, String> m3 = Map.of(new BigDecimal("1.0"), "a");
    Map<BigDecimal, String> m4 = Map.of(new BigDecimal("1.00"), "b");
    assertTrue(ComparatorUtils.compareMap(m3, m4) < 0);
    assertTrue(ComparatorUtils.compareMap(m4, m3) > 0);
  }

  @Test
  void compareMapWithMultipleCompareToEqualKeysStaysConsistentWithEquals() {
    // m1 and m2 hold the SAME two (BigDecimal, String) entries, just added in different order - since
    // BigDecimal.compareTo() ties for "1.0"/"1.00" but they're distinct (non-equals()) keys, sorting by
    // compareTo() alone isn't deterministic here: a stable sort preserves each map's own (differing)
    // insertion order for the tied pair, so the two maps could compare non-zero despite being equals()-equal.
    // Wrapped in Collections.unmodifiableMap(...) (not on the equals()-shortcut allowlist, unlike a bare
    // LinkedHashMap) so the round-7 equals()-shortcut can't return 0 without ever running the byKey
    // toString()-tie-break this test exists to exercise
    Map<BigDecimal, String> m1 = new LinkedHashMap<>();
    m1.put(new BigDecimal("1.0"), "a");
    m1.put(new BigDecimal("1.00"), "b");
    Map<BigDecimal, String> m2 = new LinkedHashMap<>();
    m2.put(new BigDecimal("1.00"), "b");
    m2.put(new BigDecimal("1.0"), "a");
    assertEquals(m1, m2);

    Map<BigDecimal, String> wrapped1 = Collections.unmodifiableMap(m1);
    Map<BigDecimal, String> wrapped2 = Collections.unmodifiableMap(m2);
    assertEquals(0, ComparatorUtils.compareMap(wrapped1, wrapped2));
    assertEquals(0, ComparatorUtils.compareMap(wrapped2, wrapped1));
  }

  @Test
  void compareMapShortCircuitsWithoutComparingKeysOrValuesWhenEqual() {
    // two equals()-equal maps shouldn't need to canonicalize (sort) keys/values and pairwise-compareTo()
    // them at all - an equals()-equal map pair is already known to compare as 0 without doing that work
    CountingKey.resetCount();
    Map<CountingKey, String> m1 = new LinkedHashMap<>();
    m1.put(new CountingKey(2), "b");
    m1.put(new CountingKey(1), "a");
    Map<CountingKey, String> m2 = new LinkedHashMap<>();
    m2.put(new CountingKey(1), "a");
    m2.put(new CountingKey(2), "b");

    assertEquals(m1, m2);
    assertEquals(0, ComparatorUtils.compareMap(m1, m2));
    assertEquals(0, CountingKey.getCount(), "compareTo() should not have been called for equals()-equal maps");
  }

  @Test
  void compareMapEqualsShortCircuitRequiresBothDirectionsForAsymmetricEquals() {
    // Map.equals() is not symmetric when one side is a TreeMap whose key comparator is inconsistent with
    // equals(): AbstractMap.equals() iterates the RECEIVER's own entries and looks each key up via the
    // ARGUMENT's get() - so for hm.equals(tm), hm's key "1" is looked up via tm.get("1"), and tm is a
    // TreeMap<>(ChromosomeNameComparator) whose comparator treats "1" and "chr1" as equal, so the lookup
    // succeeds; but for tm.equals(hm), tm's key "chr1" is looked up via hm.get("chr1") - hm is a plain Map
    // using standard String.equals(), which has no "chr1" key, so the lookup fails. A one-directional
    // equals() short-circuit would return 0 in one direction and a non-zero value in the other - a hard
    // antisymmetry violation - so the short-circuit must require BOTH a.equals(b) and b.equals(a)
    Map<String, String> tm = new TreeMap<>(ChromosomeNameComparator.getComparator());
    tm.put("chr1", "x");
    Map<String, String> hm = new LinkedHashMap<>();
    hm.put("1", "x");
    assertTrue(hm.equals(tm) && !tm.equals(hm),
        "test setup assumption broken - expected an asymmetric equals() pair (hm.equals(tm) true, " +
            "tm.equals(hm) false) to exercise the fix");

    int forward = ComparatorUtils.compareMap(tm, hm);
    int backward = ComparatorUtils.compareMap(hm, tm);
    assertTrue(Integer.signum(forward) == -Integer.signum(backward),
        "compareMap(a,b) and compareMap(b,a) must have opposite signs (or both be 0) - got " + forward +
            " and " + backward);
  }

  @Test
  void compareMapEqualsShortCircuitDoesNotBreakTransitivityForSharedCustomComparator() {
    // two Maps backed by the SAME custom (non-natural) key comparator can be MUTUALLY equals()-true per
    // that comparator's own notion of key equality (ChromosomeNameComparator treats "chr1" and "1" as the
    // same key) - the bidirectional equals() check from ...RequiresBothDirectionsForAsymmetricEquals is
    // satisfied here, so it would short-circuit to 0. But the canonical (sorted-by-natural-String-order)
    // key comparison used for a third, plain Map disagrees - x compares greater than y, and y compares
    // greater than z, via the canonical path, so x must also compare greater than z; short-circuiting
    // x-vs-z to 0 instead breaks transitivity
    Map<String, String> x = new TreeMap<>(ChromosomeNameComparator.getComparator());
    x.put("chr1", "v");
    Map<String, String> y = new HashMap<>();
    y.put("2", "v");
    Map<String, String> z = new TreeMap<>(ChromosomeNameComparator.getComparator());
    z.put("1", "v");

    int xy = ComparatorUtils.compareMap(x, y);
    int yz = ComparatorUtils.compareMap(y, z);
    assertTrue(xy > 0 && yz > 0, "test setup assumption broken - expected x>y>z via the canonical path");

    int xz = ComparatorUtils.compareMap(x, z);
    assertTrue(xz > 0, "compareMap(x,z) must be consistent with compareMap(x,y)>0 and compareMap(y,z)>0 " +
        "(transitivity) - got " + xz);
  }

  @Test
  void compareMapEqualsShortCircuitSkippedForWrappedMapWithCustomComparator() {
    // the allowlist only trusts a small set of known-safe concrete types (HashMap/ImmutableMap) - it doesn't
    // need to catch an equals()-delegating WRAPPER around a TreeMap by name, since
    // Collections.unmodifiableMap(...) isn't itself instanceof HashMap/ImmutableMap either. Same setup as
    // compareMapEqualsShortCircuitDoesNotBreakTransitivityForSharedCustomComparator, just wrapped - the
    // allowlist must still exclude x/z from the short-circuit, or transitivity breaks the same way
    Map<String, String> xInner = new TreeMap<>(ChromosomeNameComparator.getComparator());
    xInner.put("chr1", "v");
    Map<String, String> x = Collections.unmodifiableMap(xInner);
    Map<String, String> y = new HashMap<>();
    y.put("2", "v");
    Map<String, String> zInner = new TreeMap<>(ChromosomeNameComparator.getComparator());
    zInner.put("1", "v");
    Map<String, String> z = Collections.unmodifiableMap(zInner);

    int xy = ComparatorUtils.compareMap(x, y);
    int yz = ComparatorUtils.compareMap(y, z);
    assertTrue(xy > 0 && yz > 0, "test setup assumption broken - expected x>y>z via the canonical path");

    int xz = ComparatorUtils.compareMap(x, z);
    assertTrue(xz > 0, "compareMap(x,z) must be consistent with compareMap(x,y)>0 and compareMap(y,z)>0 " +
        "(transitivity) - got " + xz);
  }

  @Test
  void compareMapEqualsShortCircuitSkippedForCustomComparatorNestedInMapValue() {
    // the allowlist's top-level check (isSafeForEqualsShortCircuit(Map)) only trusts HashMap/ImmutableMap
    // itself - it also needs to scan each map's VALUES, or a custom comparator hiding one level down (a
    // plain HashMap whose value is a TreeSet<>(customComparator)) would slip through. x/z below are plain
    // HashMaps (allowlisted at the top level) whose single value is a TreeSet<>(ChromosomeNameComparator) -
    // both mutually equals()-true at every level (the outer HashMap.equals() delegates to the inner
    // TreeSet.equals(), which is symmetric here since both sides share the SAME comparator instance), so the
    // nested-value scan must still exclude x/z from the short-circuit or transitivity breaks: the nested
    // value comparison (via compareCollection(), which correctly skips ITS OWN short-circuit for a bare
    // TreeSet<>(customComparator)) makes x>y>z, but the outer short-circuit - blind to the nested comparator
    // without that scan - would report x==z instead of x>z
    Map<String, Set<String>> x = new HashMap<>();
    Set<String> xValue = new TreeSet<>(ChromosomeNameComparator.getComparator());
    xValue.add("chr1");
    x.put("k", xValue);
    Map<String, Set<String>> y = new HashMap<>();
    y.put("k", new HashSet<>(List.of("2")));
    Map<String, Set<String>> z = new HashMap<>();
    Set<String> zValue = new TreeSet<>(ChromosomeNameComparator.getComparator());
    zValue.add("1");
    z.put("k", zValue);

    int xy = ComparatorUtils.compareMap(x, y);
    int yz = ComparatorUtils.compareMap(y, z);
    assertTrue(xy > 0 && yz > 0, "test setup assumption broken - expected x>y>z via the canonical path");

    int xz = ComparatorUtils.compareMap(x, z);
    assertTrue(xz > 0, "compareMap(x,z) must be consistent with compareMap(x,y)>0 and compareMap(y,z)>0 " +
        "(transitivity) - got " + xz);
  }

  @Test
  void compareMapEqualsShortCircuitNotTrickedByImmutableSortedMap() {
    // the allowlist checks `instanceof HashMap || instanceof ImmutableMap`, but instanceof is
    // subclass-permeable: Guava's ImmutableSortedMap IS an ImmutableMap, so it passes the allowlist despite
    // its equals() being just as comparator-driven as a TreeMap's - same setup as
    // compareMapEqualsShortCircuitDoesNotBreakTransitivityForSharedCustomComparator, just using
    // ImmutableSortedMap instead of TreeMap, to prove the allowlist doesn't actually exclude it
    Map<String, String> x = ImmutableSortedMap.<String, String>orderedBy(ChromosomeNameComparator.getComparator())
        .put("chr1", "v").build();
    Map<String, String> y = new HashMap<>();
    y.put("2", "v");
    Map<String, String> z = ImmutableSortedMap.<String, String>orderedBy(ChromosomeNameComparator.getComparator())
        .put("1", "v").build();

    int xy = ComparatorUtils.compareMap(x, y);
    int yz = ComparatorUtils.compareMap(y, z);
    assertTrue(xy > 0 && yz > 0, "test setup assumption broken - expected x>y>z via the canonical path");

    int xz = ComparatorUtils.compareMap(x, z);
    assertTrue(xz > 0, "compareMap(x,z) must be consistent with compareMap(x,y)>0 and compareMap(y,z)>0 " +
        "(transitivity) - got " + xz);
  }

  @Test
  void compareMapEqualsShortCircuitNotTrickedByImmutableSortedSetNestedInMapValue() {
    // same subclass-permeability gap as compareMapEqualsShortCircuitNotTrickedByImmutableSortedMap, but for
    // the nested-value Set scan: a plain HashMap whose value is an ImmutableSortedSet<>(customComparator)
    // passes the "instanceof HashSet || instanceof ImmutableSet" nested-value check too, for the same reason
    Map<String, Set<String>> x = new HashMap<>();
    x.put("k", ImmutableSortedSet.copyOf(ChromosomeNameComparator.getComparator(), List.of("chr1")));
    Map<String, Set<String>> y = new HashMap<>();
    y.put("k", new HashSet<>(List.of("2")));
    Map<String, Set<String>> z = new HashMap<>();
    z.put("k", ImmutableSortedSet.copyOf(ChromosomeNameComparator.getComparator(), List.of("1")));

    int xy = ComparatorUtils.compareMap(x, y);
    int yz = ComparatorUtils.compareMap(y, z);
    assertTrue(xy > 0 && yz > 0, "test setup assumption broken - expected x>y>z via the canonical path");

    int xz = ComparatorUtils.compareMap(x, z);
    assertTrue(xz > 0, "compareMap(x,z) must be consistent with compareMap(x,y)>0 and compareMap(y,z)>0 " +
        "(transitivity) - got " + xz);
  }

  private static final class CountingKey implements Comparable<CountingKey> {
    private static int count;
    private final int m_value;

    CountingKey(int value) {
      m_value = value;
    }

    static void resetCount() {
      count = 0;
    }

    static int getCount() {
      return count;
    }

    @Override
    public int compareTo(CountingKey o) {
      count++;
      return Integer.compare(m_value, o.m_value);
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof CountingKey other && other.m_value == m_value;
    }

    @Override
    public int hashCode() {
      return m_value;
    }
  }

  @Test
  void compareMapPairsValuesByKeyNotIterationOrder() {
    // keys inserted in a different relative order between the two maps - compareMap must pair each value
    // with the value for the SAME (compareTo-equal) key in the other map, not whatever value happens to be
    // at the same raw iteration position. By key, key1 differs ("z" vs "a", so m1 > m2); pairing by raw
    // iteration position instead would compare key2's "a" against key1's "a" (equal), then key1's "z" against
    // key2's "z" (also equal), wrongly reporting the maps as equal.
    Map<Integer, String> m1 = new LinkedHashMap<>();
    m1.put(2, "a");
    m1.put(1, "z");
    Map<Integer, String> m2 = new LinkedHashMap<>();
    m2.put(1, "a");
    m2.put(2, "z");

    assertTrue(ComparatorUtils.compareMap(m1, m2) > 0);
    assertTrue(ComparatorUtils.compareMap(m2, m1) < 0);
  }

  @Test
  void compareMapWithMismatchedCollectionElementTypesThrows() {
    // both values are Lists, but their elements are mutually incompatible Comparable types - must still
    // surface as the documented UnsupportedOperationException, not a raw ClassCastException (the sibling
    // scalar-value branch just above already gets this right; the Collection-value branch didn't)
    Map<String, Object> m1 = new HashMap<>();
    Map<String, Object> m2 = new HashMap<>();
    m1.put("k", List.of(1));
    m2.put("k", List.of("notANumber"));

    assertThrows(UnsupportedOperationException.class, () -> ComparatorUtils.compareMap(m1, m2));
  }

  @Test
  void compareMapWithMismatchedComparableTypesThrows() {
    // both values are Comparable, but of mutually incompatible concrete types - must still surface as the
    // documented UnsupportedOperationException, not a raw ClassCastException
    Map<String, Object> m1 = new HashMap<>();
    Map<String, Object> m2 = new HashMap<>();
    m1.put("k", 1);
    m2.put("k", "notANumber");

    assertThrows(UnsupportedOperationException.class, () -> ComparatorUtils.compareMap(m1, m2));
  }



  @Test
  void compareCollectionOfMapsRejectsNonList() {
    // unlike CollectionComparator's Set support, Map isn't Comparable, so a Set<Map> can't be canonicalized
    // into a well-defined order - comparing by raw iteration order (like a plain HashSet would give) could
    // pair up unrelated maps and produce a wrong result, so non-List input must be rejected outright
    Set<Map> a = new HashSet<>();
    a.add(new HashMap<>());
    Set<Map> b = new HashSet<>();
    b.add(new HashMap<>());
    assertThrows(IllegalArgumentException.class, () -> ComparatorUtils.compareCollectionOfMaps(a, b));
  }

  @Test
  void compareCollectionOfMapsSelfComparisonOfUnsupportedTypeShortCircuitsBeforeTypeCheck() {
    // compareCollectionOfMaps()'s "if (a == b) return 0;" identity fast path runs BEFORE the
    // non-List-rejection check above - so a self-comparison of an otherwise-unsupported collection type (a
    // Set<Map>, which compareCollectionOfMapsRejectsNonList above confirms throws for two DIFFERENT Set
    // instances) must return 0 rather than throw, since "compare(x, x) == 0" takes priority over the type
    // check - unpinned before this test (round 28): removing this line left the whole suite green
    Set<Map> a = new HashSet<>();
    a.add(new HashMap<>());
    assertEquals(0, ComparatorUtils.compareCollectionOfMaps(a, a));
  }

  @Test
  void compareCollectionOfMapsRejectsNonListRegardlessOfSize() {
    // same fix as CollectionComparator.testListVsSetThrowsRegardlessOfSize - the type check used to run only
    // after a same-size early return, so a List/Set pair of DIFFERENT sizes (or two EMPTY ones) silently
    // compared by size instead of throwing
    Set<Map> set1 = new HashSet<>();
    set1.add(new HashMap<>());
    List<Map> list2 = new ArrayList<>();
    list2.add(new HashMap<>());
    list2.add(new HashMap<>());
    assertThrows(IllegalArgumentException.class, () -> ComparatorUtils.compareCollectionOfMaps(set1, list2));
    assertThrows(IllegalArgumentException.class, () -> ComparatorUtils.compareCollectionOfMaps(list2, set1));
    assertThrows(IllegalArgumentException.class,
        () -> ComparatorUtils.compareCollectionOfMaps(new HashSet<Map>(), new ArrayList<Map>()));
  }

  @Test
  void compareCollectionOfMapsNullVsEmptyIsConsistentWithCompareCollection() {
    // null is treated the same as (not distinct from) an empty collection here too, consistent with
    // compareCollection() - see CollectionComparator's class javadoc for why (a lazily-created-then-emptied
    // collection field needs "never populated" and "populated then fully emptied" to compare as equal).
    // Note this differs from compareMap(), which does treat null as sorting strictly before an empty map -
    // that's a separate, pre-existing convention unrelated to this collection-vs-collection comparison
    assertEquals(0, ComparatorUtils.compareCollectionOfMaps(null, new ArrayList<>()));
    assertEquals(0, ComparatorUtils.compareCollectionOfMaps(new ArrayList<>(), null));
    assertEquals(0, ComparatorUtils.compareCollectionOfMaps(null, null));
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
