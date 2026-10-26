package org.pharmgkb.common.comparator;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
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
  void testNaturalOrderSortedSetMatchesSortedCopy() {
    // a natural-order TreeSet is compared by iterating it directly; that must agree with the sorted copy
    // used for other Sets
    Set<String> tree = new TreeSet<>(List.of("c", "a"));
    Set<String> linked = new LinkedHashSet<>(List.of("c", "a"));
    assertEquals(0, CollectionComparator.getComparator().compare(tree, linked));
    assertEquals(0, CollectionComparator.getComparator().compare(linked, tree));

    Set<String> other = new LinkedHashSet<>(List.of("b", "a"));
    assertEquals(1, CollectionComparator.getComparator().compare(tree, other));
    assertEquals(-1, CollectionComparator.getComparator().compare(other, tree));
  }


  @Test
  void testListComparedAgainstSortedSetOrder() {
    // a List is compared in its own order, a SortedSet in its sorted order
    List<String> list = List.of("a", "b");
    Set<String> sorted = new TreeSet<>(List.of("b", "a"));
    assertEquals(0, CollectionComparator.getComparator().compare(list, sorted));
    assertEquals(0, CollectionComparator.getComparator().compare(sorted, list));
    assertTrue(CollectionComparator.getComparator().compare(List.of("b", "a"), sorted) > 0);
  }

  @Test
  void testCustomComparatorSortedSetUsesItsOwnOrder() {
    Set<String> reversed = new TreeSet<>(Comparator.reverseOrder());
    reversed.addAll(List.of("a", "b", "c"));
    assertEquals(0, CollectionComparator.getComparator().compare(reversed, List.of("c", "b", "a")));
  }

  @Test
  void testOtherCollectionTypesComparedInNaturalOrder() {
    Deque<Integer> a = new ArrayDeque<>(Arrays.asList(2, 1));
    Deque<Integer> b = new ArrayDeque<>(Arrays.asList(1, 2));
    assertEquals(0, CollectionComparator.getComparator().compare(a, b));
  }


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
    // AbstractComparator.reverse(0) must return 0: subclasses can pass it 0, and a wrong reverse(0) would break
    // reflexivity (e.g. in a TreeSet). AbstractComparator is abstract, so this goes through a subclass
    assertEquals(0, new CollectionComparator(SortOrder.REVERSE).reverse(0));
  }

  @Test
  void testNull() {

    //noinspection EqualsWithItself
    assertEquals(0, CollectionComparator.getComparator().compare(null, null));
    // null is treated the same as an empty collection, so "never populated" and "populated then emptied"
    // compare as equal
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
    // reach sorted()'s "x == null" arm: with a HashSet, null always iterates first, so only the "y == null"
    // arm runs. An unmodifiable LinkedHashSet both controls iteration order and skips the equals() shortcut
    // (it isn't on that allowlist)
    Set<String> nullFirst = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(null, "b")));
    Set<String> nullSecond = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList("b", null)));
    assertEquals(0, CollectionComparator.getComparator().compare(nullFirst, nullSecond),
        "two Sets with identical content (a null and \"b\") must compare equal regardless of iteration order");
  }


}
