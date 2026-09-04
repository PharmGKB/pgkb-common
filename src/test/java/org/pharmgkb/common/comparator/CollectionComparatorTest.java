package org.pharmgkb.common.comparator;

import com.google.common.collect.ImmutableSortedSet;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


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

  @Test
  void testReverseWithNullOperand() {
    // no existing test exercised SortOrder.REVERSE with a null operand - null-vs-empty equivalence (see
    // testNull() below) must still hold, and REVERSE must still be antisymmetric (compare(a,b) ==
    // -compare(b,a)) with null on either side, not just with two non-null collections
    CollectionComparator comparator = new CollectionComparator(SortOrder.REVERSE);
    assertEquals(0, comparator.compare(null, List.of()));
    assertEquals(0, comparator.compare(List.of(), null));
    assertEquals(1, comparator.compare(null, List.of(1)));
    assertEquals(-1, comparator.compare(List.of(1), null));
  }

  @Test
  void testAbstractComparatorReverseOfZeroStaysZero() {
    // round 34 finding 6: AbstractComparator.reverse(0) is unpinned within this repo - both real call sites
    // (modReturn() above, at rez != 0 checks) are guarded so reverse() is only ever invoked with a non-zero
    // val here - but reverse()/modReturn() are protected final subclass API, and real PharmGKB subclasses
    // (SubmissionComparator, SubmissionEventComparator, UserComparator) pass values that CAN be 0, including
    // UserComparator(SortOrder.REVERSE) used as a real TreeSet comparator - a wrong reverse(0) there would
    // silently break reflexivity/dedup. Mutation-verified: flipping "return 0;" to "return 1;" leaves the
    // whole suite green. AbstractComparator is abstract, so this is exercised via a same-package subclass
    // instance rather than directly - reverse()/modReturn() are package-visible (protected) from here either
    // way, subclass or not
    assertEquals(0, new CollectionComparator(SortOrder.REVERSE).reverse(0));
  }

  @Test
  void testNull() {

    //noinspection EqualsWithItself
    assertEquals(0, CollectionComparator.getComparator().compare(null, null));
    // null is treated the same as (not distinct from) an empty collection - a caller like PharmGKB's
    // VariantLocation.getGenes() (@Nullable, starts null, only ever lazily populated or emptied via
    // removeAllGenes() without being nulled again) needs "never populated" and "populated then fully
    // removed" to compare as equal
    assertEquals(0, CollectionComparator.getComparator().compare(null, new HashSet<Integer>()));
    assertEquals(0, CollectionComparator.getComparator().compare(new HashSet<Integer>(), null));
    assertEquals(0, CollectionComparator.getComparator().compare(null, List.of()));
    assertEquals(0, CollectionComparator.getComparator().compare(List.of(), null));
  }

  @Test
  void testConstructorRejectsNullOrder() {
    // AbstractComparator(SortOrder)'s Preconditions.checkNotNull(order) guard - without it, a null order
    // would silently behave as NATURAL instead of failing fast
    assertThrows(NullPointerException.class, () -> new CollectionComparator(null));
  }

  @Test
  void testNullElement() {

    // a null element within an otherwise-comparable collection must not NPE; null sorts before non-null,
    // matching the convention used elsewhere in this package
    assertEquals(-1, CollectionComparator.getComparator().compare(Arrays.asList(1, null), Arrays.asList(1, 2)));
    assertEquals(1, CollectionComparator.getComparator().compare(Arrays.asList(1, 2), Arrays.asList(1, null)));
    //noinspection EqualsWithItself
    assertEquals(0, CollectionComparator.getComparator().compare(Arrays.asList(1, null), Arrays.asList(1, null)));
  }

  @Test
  void testSetsWithDifferentIterationOrderCompareEqual() {
    // LinkedHashSet has a deterministic iteration order (insertion order), but that order isn't part of its
    // equals() contract - these two sets are equals()-equal despite iterating differently, so the comparator must
    // canonicalize (sort) before comparing rather than rely on raw iteration order. Wrapped in
    // Collections.unmodifiableSet(...) (not on the round-11 equals()-shortcut allowlist, unlike a bare
    // LinkedHashSet) so the shortcut can't return 0 without ever running the canonicalization (sorted())
    // logic this test exists to exercise
    // b's own insertion order already happens to be sorted, so only asserting compare(a, b) can't tell
    // canonicalizing BOTH operands apart from canonicalizing just a - compare(b, a) must be asserted too
    Set<Integer> a = new LinkedHashSet<>(Arrays.asList(3, 1, 2));
    Set<Integer> b = new LinkedHashSet<>(Arrays.asList(1, 2, 3));
    assertEquals(a, b);
    assertEquals(0, CollectionComparator.getComparator().compare(
        Collections.unmodifiableSet(a), Collections.unmodifiableSet(b)));
    assertEquals(0, CollectionComparator.getComparator().compare(
        Collections.unmodifiableSet(b), Collections.unmodifiableSet(a)));
  }

  @Test
  void testSetsWithNullElementDoNotNpe() {
    // a null element within a Set must not NPE when canonicalizing (sorting) for comparison; null sorts before
    // non-null, matching the convention used for Lists (testNullElement) and elsewhere in this package
    Set<Integer> a = new HashSet<>(Arrays.asList(null, 1));
    Set<Integer> b = new HashSet<>(Arrays.asList(null, 2));
    assertEquals(-1, CollectionComparator.getComparator().compare(a, b));
    assertEquals(1, CollectionComparator.getComparator().compare(b, a));
    //noinspection EqualsWithItself
    assertEquals(0, CollectionComparator.getComparator().compare(a, new HashSet<>(Arrays.asList(1, null))));
  }

  @Test
  void testSetsWithNullInOnlyOneOperandSortsNullFirst() {
    // testSetsWithNullElementDoNotNpe above puts a null in BOTH operands, so its own "null sorts before
    // non-null" claim is never actually exercised - the null's sort position cancels out and the real
    // differentiator ends up being the OTHER element (1 vs 2). Here only one operand has a null, so the
    // null's own sort position is what decides the result
    Set<String> withNull = new HashSet<>(Arrays.asList(null, "b"));
    Set<String> withoutNull = new HashSet<>(Arrays.asList("a", "b"));
    assertEquals(-1, CollectionComparator.getComparator().compare(withNull, withoutNull));
    assertEquals(1, CollectionComparator.getComparator().compare(withoutNull, withNull));
  }

  @Test
  void testSortedCanonicalizesNullPositionRegardlessOfIterationOrder() {
    // round 34 finding 5: sorted()'s own "x == null" arm (distinct from the "y == null" arm the test above
    // already covers) had no test that actually REACHED it - that test uses a HashSet, where null always
    // hashes to bucket 0 and iterates first, so TimSort's pivot is always the non-null element and only the
    // "y == null" arm is ever exercised. Defeating the equals()-shortcut allowlist (round 18 technique -
    // Collections.unmodifiableSet(...) isn't itself instanceof HashSet/ImmutableSet, even though the
    // LinkedHashSet it wraps is) while controlling iteration order via LinkedHashSet lets both null-position
    // orderings actually reach sorted()'s comparator. Mutation-verified: flipping "x == null"'s return from
    // -1 to 1 leaves the whole suite green without this
    Set<String> nullFirst = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(null, "b")));
    Set<String> nullSecond = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList("b", null)));
    assertEquals(0, CollectionComparator.getComparator().compare(nullFirst, nullSecond),
        "two Sets with identical content (a null and \"b\") must compare equal regardless of iteration order");
  }

  @Test
  void testEqualSetsShortCircuitWithoutComparingElements() {
    // two equals()-equal Sets shouldn't need to canonicalize (sort) and pairwise-compareTo() their elements
    // at all - an equals()-equal set pair is already known to compare as 0 without doing that work
    CountingComparable.resetCount();
    Set<CountingComparable> a = new LinkedHashSet<>(
        Arrays.asList(new CountingComparable(3), new CountingComparable(1), new CountingComparable(2)));
    Set<CountingComparable> b = new LinkedHashSet<>(
        Arrays.asList(new CountingComparable(1), new CountingComparable(2), new CountingComparable(3)));
    assertEquals(a, b);
    assertEquals(0, CollectionComparator.getComparator().compare(a, b));
    assertEquals(0, CountingComparable.getCount(), "compareTo() should not have been called for equals()-equal sets");
  }

  private static final class CountingComparable implements Comparable<CountingComparable> {
    private static int count;
    private final int m_value;

    CountingComparable(int value) {
      m_value = value;
    }

    static void resetCount() {
      count = 0;
    }

    static int getCount() {
      return count;
    }

    @Override
    public int compareTo(CountingComparable o) {
      count++;
      return Integer.compare(m_value, o.m_value);
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof CountingComparable other && other.m_value == m_value;
    }

    @Override
    public int hashCode() {
      return m_value;
    }
  }

  @Test
  void testEqualsShortCircuitRequiresBothDirectionsForAsymmetricEquals() {
    // Set.equals() is not symmetric when one side is a TreeSet whose comparator is inconsistent with
    // equals() (AbstractSet.equals()/containsAll() uses the RECEIVER's own contains(), so which side is
    // "this" matters): ChromosomeNameComparator deliberately treats "chr1" and "1" as equal, so a
    // TreeSet<>(ChromosomeNameComparator) containing "chr1" .equals() a plain Set containing "1", but not
    // the other way around. A one-directional equals() short-circuit would return 0 in one direction and a
    // non-zero value in the other - a hard antisymmetry violation - so the short-circuit must require BOTH
    // a.equals(b) and b.equals(a) before trusting it
    Set<String> tree = new TreeSet<>(ChromosomeNameComparator.getComparator());
    tree.add("chr1");
    Set<String> hash = new LinkedHashSet<>(List.of("1"));
    assertTrue(tree.equals(hash) && !hash.equals(tree),
        "test setup assumption broken - expected an asymmetric equals() pair (tree.equals(hash) true, " +
            "hash.equals(tree) false) to exercise the fix");

    int forward = CollectionComparator.getComparator().compare(tree, hash);
    int backward = CollectionComparator.getComparator().compare(hash, tree);
    assertEquals(Integer.signum(forward), -Integer.signum(backward),
        "compare(a,b) and compare(b,a) must have opposite signs (or both be 0) - got " + forward + " and " + backward);
  }

  @Test
  void testEqualsShortCircuitDoesNotBreakTransitivityForSharedCustomComparator() {
    // two Sets backed by the SAME custom (non-natural) comparator can be MUTUALLY equals()-true per that
    // comparator's own notion of equality (ChromosomeNameComparator treats "chr1" and "1" as the same
    // element) - the bidirectional equals() check from testEqualsShortCircuitRequiresBothDirectionsFor... is
    // satisfied here, so it would short-circuit to 0. But the canonical (sorted-by-natural-String-order)
    // path used for a third, plain Set disagrees with that comparator's notion of equality - x compares
    // greater than y, and y compares greater than z, via the canonical path, so x must also compare greater
    // than z; short-circuiting x-vs-z to 0 instead breaks transitivity
    Set<String> x = new TreeSet<>(ChromosomeNameComparator.getComparator());
    x.add("chr1");
    Set<String> y = new HashSet<>(List.of("2"));
    Set<String> z = new TreeSet<>(ChromosomeNameComparator.getComparator());
    z.add("1");

    CollectionComparator cc = CollectionComparator.getComparator();
    int xy = cc.compare(x, y);
    int yz = cc.compare(y, z);
    assertTrue(xy > 0 && yz > 0, "test setup assumption broken - expected x>y>z via the canonical path");

    int xz = cc.compare(x, z);
    assertTrue(xz > 0, "compare(x,z) must be consistent with compare(x,y)>0 and compare(y,z)>0 " +
        "(transitivity) - got " + xz);
  }

  @Test
  void testEqualsShortCircuitSkippedForWrappedSetWithCustomComparator() {
    // the allowlist only trusts a small set of known-safe concrete types (HashSet/ImmutableSet) - it doesn't
    // need to catch an equals()-delegating WRAPPER around a TreeSet by name, since
    // Collections.unmodifiableSet(...) isn't itself instanceof HashSet/ImmutableSet either. x and z below
    // are wrapped TreeSet<>(comparator)s that are still MUTUALLY equals()-true per that comparator's own
    // notion of equality (same setup as testEqualsShortCircuitDoesNotBreakTransitivityForSharedCustomComparator,
    // just wrapped) - the allowlist must still exclude them from the short-circuit, or transitivity breaks
    // the same way
    Set<String> xInner = new TreeSet<>(ChromosomeNameComparator.getComparator());
    xInner.add("chr1");
    Set<String> x = Collections.unmodifiableSet(xInner);
    Set<String> y = new HashSet<>(List.of("2"));
    Set<String> zInner = new TreeSet<>(ChromosomeNameComparator.getComparator());
    zInner.add("1");
    Set<String> z = Collections.unmodifiableSet(zInner);

    CollectionComparator cc = CollectionComparator.getComparator();
    int xy = cc.compare(x, y);
    int yz = cc.compare(y, z);
    assertTrue(xy > 0 && yz > 0, "test setup assumption broken - expected x>y>z via the canonical path");

    int xz = cc.compare(x, z);
    assertTrue(xz > 0, "compare(x,z) must be consistent with compare(x,y)>0 and compare(y,z)>0 " +
        "(transitivity) - got " + xz);
  }

  @Test
  void testEqualsShortCircuitNotTrickedByImmutableSortedSet() {
    // the allowlist checks `instanceof HashSet || instanceof ImmutableSet`, but instanceof is
    // subclass-permeable: Guava's ImmutableSortedSet IS an ImmutableSet (RegularImmutableSortedSet extends
    // ImmutableSet), so it passes the allowlist despite its equals()/containsAll() being just as
    // comparator-driven as a TreeSet's - same setup as
    // testEqualsShortCircuitDoesNotBreakTransitivityForSharedCustomComparator, just using
    // ImmutableSortedSet instead of TreeSet, to prove the allowlist doesn't actually exclude it
    Set<String> x = ImmutableSortedSet.copyOf(ChromosomeNameComparator.getComparator(), List.of("chr1"));
    Set<String> y = new HashSet<>(List.of("2"));
    Set<String> z = ImmutableSortedSet.copyOf(ChromosomeNameComparator.getComparator(), List.of("1"));

    CollectionComparator cc = CollectionComparator.getComparator();
    int xy = cc.compare(x, y);
    int yz = cc.compare(y, z);
    assertTrue(xy > 0 && yz > 0, "test setup assumption broken - expected x>y>z via the canonical path");

    int xz = cc.compare(x, z);
    assertTrue(xz > 0, "compare(x,z) must be consistent with compare(x,y)>0 and compare(y,z)>0 " +
        "(transitivity) - got " + xz);
  }

  @Test
  void testListVsSetThrows() {
    List<Integer> list = Arrays.asList(1, 2);
    Set<Integer> set = new HashSet<>(Arrays.asList(1, 2));
    assertThrows(IllegalArgumentException.class, () -> CollectionComparator.getComparator().compare(list, set));
    assertThrows(IllegalArgumentException.class, () -> CollectionComparator.getComparator().compare(set, list));
  }

  @Test
  void testListVsSetThrowsRegardlessOfSize() {
    // the type-mismatch check must not depend on the two collections happening to be the same size - the
    // class javadoc's "comparing a List against a Set... throws" claim is unconditional, but the check used
    // to run only after a same-size early return, so a List/Set pair of DIFFERENT sizes (or two EMPTY ones)
    // silently compared by size instead of throwing; a caller sorting a mix of Lists and Sets could then see
    // this throw only sporadically, whenever two mismatched entries happened to tie in size
    assertThrows(IllegalArgumentException.class,
        () -> CollectionComparator.getComparator().compare(List.of(1, 2), Set.of(1)));
    assertThrows(IllegalArgumentException.class,
        () -> CollectionComparator.getComparator().compare(Set.of(1), List.of(1, 2)));
    assertThrows(IllegalArgumentException.class,
        () -> CollectionComparator.getComparator().compare(List.of(), Set.of()));
  }

  @Test
  void testUnsupportedCollectionTypeThrows() {
    Deque<Integer> a = new ArrayDeque<>(Arrays.asList(1, 2));
    Deque<Integer> b = new ArrayDeque<>(Arrays.asList(1, 2));
    assertThrows(IllegalArgumentException.class, () -> CollectionComparator.getComparator().compare(a, b));
  }

  @Test
  void testSelfComparisonOfUnsupportedCollectionTypeShortCircuitsBeforeTypeCheck() {
    // compare()'s "if (a == b) return 0;" identity fast path runs BEFORE the kind-compatibility check above -
    // so a self-comparison of an otherwise-unsupported collection type (e.g. a Deque, which
    // testUnsupportedCollectionTypeThrows above confirms throws for two DIFFERENT Deque instances) must
    // return 0 rather than throw, since "compare(x, x) == 0" is a basic comparator contract requirement that
    // takes priority - this line was mutation-verified unpinned (round 28): removing it left the whole suite
    // green, since every other test either compares two DISTINCT operands or never happens to exercise the
    // exact-same-reference case for an unsupported type
    Deque<Integer> deque = new ArrayDeque<>(Arrays.asList(1, 2));
    assertEquals(0, CollectionComparator.getComparator().compare(deque, deque));
  }

  @Test
  void testUnsupportedCollectionTypeMixedWithSetThrows() {
    // "must be a List or Set" also applies when only ONE side is the unsupported kind - a Deque paired with a
    // genuine Set is just as invalid as two Deques, and both orderings must throw
    Set<Integer> set = Set.of(1, 2);
    Deque<Integer> deque = new ArrayDeque<>(Arrays.asList(1, 2));
    assertThrows(IllegalArgumentException.class, () -> CollectionComparator.getComparator().compare(set, deque));
    assertThrows(IllegalArgumentException.class, () -> CollectionComparator.getComparator().compare(deque, set));
  }
}
