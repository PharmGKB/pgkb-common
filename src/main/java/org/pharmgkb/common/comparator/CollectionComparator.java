package org.pharmgkb.common.comparator;

import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import org.jspecify.annotations.Nullable;


/**
 * This comparator compares {@link Collection}s that hold {@link Comparable} elements and sorts them first by size, then
 * elements. A {@code null} collection is treated the same as (not distinct from) an empty one of either kind - a
 * caller that only ever lazily creates its collection when the first element is added, and empties (but doesn't
 * null out) that same collection when the last element is removed, needs "never populated" and "populated then
 * fully emptied" to compare as equal. Note this equivalence is size-based, not kind-based: a {@code null} operand
 * is never itself kind-checked against the other (non-null) operand, so e.g. comparing {@code null} against a
 * non-empty {@code Collection} of some unsupported kind returns a (negative) size-based result rather than
 * throwing - only comparing two non-null operands validates kind-compatibility (see below).
 * <p>
 * Both collections being compared must be the same kind: either both {@link List}s, compared in iteration/positional
 * order (matching {@code List.equals()}), or both {@link Set}s, compared using a canonicalized (sorted) copy of
 * each - a {@code Set}'s iteration order is not guaranteed to be consistent with its {@code equals()} (e.g. a
 * {@code LinkedHashSet} has a deterministic insertion order that's still irrelevant to its {@code equals()}), so
 * comparing raw iteration order could report two {@code equals()} sets as non-equal. Comparing a {@code List}
 * against a {@code Set}, or any other kind of {@code Collection}, throws {@link IllegalArgumentException} -
 * unless one of them is {@code null} (see above).
 * <p>
 * Like {@link Comparable#compareTo}/{@link java.util.TreeSet} elsewhere in the JDK, comparing two same-sized
 * collections whose elements are not mutually comparable (e.g. one holds {@code Integer}s and the other
 * {@code String}s) will throw {@link ClassCastException}.
 * <p>
 * The two rules above combine into a real, if narrow, transitivity gap: {@code compare(null, List.of())} and
 * {@code compare(null, Set.of())} both return {@code 0} (null-vs-empty equivalence, size-based, no kind
 * check), but {@code compare(List.of(), Set.of())} throws {@link IllegalArgumentException} (kind check
 * applies once both operands are non-null) - so {@code null}, an empty {@code List} and an empty {@code Set}
 * are pairwise "equal" in two of three comparisons and incompatible in the third. This is unreachable via
 * {@link #compare} alone unless a caller actually mixes {@code null}/{@code List}/{@code Set} operands of the
 * same logical collection in one sorted context (e.g. a single {@link java.util.TreeSet} or {@code sort()}
 * call) - verified no real caller does today - but a caller relying on strict transitivity across a mix of
 * these three shapes should not.
 *
 * @author Mark Woon
 */
public class CollectionComparator extends AbstractComparator<Collection<? extends Comparable>> {
  private static final CollectionComparator sf_comparator = new CollectionComparator();


  /**
   * Gets an instance of this comparator.
   */
  public static CollectionComparator getComparator() {
    return sf_comparator;
  }


  /**
   * Default constructor, sorts in ascending order.
   */
  public CollectionComparator() {
  }


  /**
   * Instantiates a comparator sorts with the specified order.
   *
   * @param order specify the order in which results should be returned
   */
  public CollectionComparator(SortOrder order) {
    super(order);
  }



  @Override
  public int compare(@Nullable Collection<? extends Comparable> a, @Nullable Collection<? extends Comparable> b) {
    if (a == b) {
      return 0;
    }
    // kind-compatibility is validated unconditionally (when both are non-null), not just when they happen to
    // be the same size - see the class javadoc's blanket "comparing a List against a Set... throws" claim.
    // A null operand has no "kind" of its own (null-vs-empty equivalence is handled below via size, not
    // here), so it's exempt from this check the same way it's exempt from the size-based comparison.
    boolean aIsList = a instanceof List;
    boolean bIsList = b instanceof List;
    if (a != null && b != null) {
      if (aIsList != bIsList) {
        throw new IllegalArgumentException("Cannot compare a List with a non-List collection (" +
            a.getClass().getName() + " vs " + b.getClass().getName() + ")");
      }
      if (!aIsList && (!(a instanceof Set) || !(b instanceof Set))) {
        throw new IllegalArgumentException("Unsupported collection type for comparison, must be a List or Set (" +
            a.getClass().getName() + " vs " + b.getClass().getName() + ")");
      }
    }
    // null is treated as size 0 (i.e. equivalent to an empty collection), not as distinct from/sorting before
    // one - see the class javadoc. By the time we get past the aSize==0 check, both a and b are guaranteed
    // non-null (if either were null, its size would be 0, and since sizes are equal at that point, both
    // would be size 0 and we'd have already returned)
    int aSize = a == null ? 0 : a.size();
    int bSize = b == null ? 0 : b.size();
    int rez = Integer.compare(aSize, bSize);
    if (rez != 0) {
      return modReturn(rez);
    }
    if (aSize == 0) {
      return 0;
    }
    // a Set canonicalization (below) is O(n log n) per comparison - skip it entirely when the two Sets are
    // already known to be equals()-equal (an O(n) hash-based check), which is the common case when comparing
    // sorted/deduplicated collections that mostly don't change between comparisons. Both directions must be
    // checked, not just a.equals(b): Set.equals() is NOT guaranteed symmetric when one side is a TreeSet
    // whose comparator is inconsistent with equals() (AbstractSet.equals()/containsAll() uses the receiver's
    // own contains(), which for a TreeSet means the receiver's comparator, not equals()) - e.g. a
    // TreeSet<>(ChromosomeNameComparator) containing "chr1" .equals() a plain Set containing "1" (the
    // comparator says they're equal), but that plain Set does NOT .equals() the TreeSet back (its own
    // standard String.equals() says "chr1" != "1"). Trusting a one-directional equals() here would make
    // compare(a,b) and compare(b,a) both report the SAME sign instead of opposite ones - a hard antisymmetry
    // violation - so both directions must agree before this short-circuit is safe to take.
    // Even with both directions agreeing, the short-circuit is only safe when BOTH Sets' concrete runtime
    // types are on a small ALLOWLIST of known-safe, non-comparator-driven implementations (see
    // isSafeForEqualsShortCircuit() below) - e.g. two TreeSet<>(ChromosomeNameComparator)s holding "chr1" and
    // "1" respectively are mutually equals()-true under that comparator's own notion of equality, while the
    // canonicalized comparison below - which always sorts by the elements' plain natural ordering, not any
    // Set's own comparator - disagrees. Trusting the short-circuit there breaks TRANSITIVITY against a third,
    // differently-backed Set: x (custom-comparator "chr1") could compare > y (plain "2") and y compare > z
    // (custom-comparator "1") via the canonical path, while x-vs-z gets short-circuited to 0 instead of
    // consistently > 0.
    if (!aIsList && isSafeForEqualsShortCircuit(a) && isSafeForEqualsShortCircuit(b) && a.equals(b) && b.equals(a)) {
      return 0;
    }
    // Sets have no iteration order guaranteed to be consistent with equals(), so compare a canonicalized (sorted)
    // copy instead - otherwise two equals() Sets populated/iterated in a different order could incorrectly
    // compare as non-zero
    Collection<? extends Comparable> orderedA = aIsList ? a : sorted(a);
    Collection<? extends Comparable> orderedB = aIsList ? b : sorted(b);
    Iterator<? extends Comparable> aIt = orderedA.iterator();
    @SuppressWarnings({ "ConstantConditions" })
    Iterator<? extends Comparable> bIt = orderedB.iterator();
    while (aIt.hasNext()) {
      Comparable elemA = aIt.next();
      Comparable elemB = bIt.next();
      if (elemA == elemB) {
        rez = 0;
      } else if (elemA == null) {
        rez = -1;
      } else if (elemB == null) {
        rez = 1;
      } else {
        //noinspection unchecked
        rez = elemA.compareTo(elemB);
      }
      if (rez != 0) {
        return modReturn(rez);
      }
    }
    return 0;
  }


  /**
   * Returns whether {@code set}'s concrete runtime type is one of a small ALLOWLIST of known-safe,
   * non-comparator-driven {@link Set} implementations - safe to trust {@code equals()} for the short-circuit
   * in {@link #compare}. A blacklist (rejecting only recognized-unsafe types) can never enumerate every
   * {@code equals()}-delegating wrapper - many, e.g. {@code java.util.Collections$UnmodifiableSet}, are
   * package-private JDK internals that can't even be {@code instanceof}-checked - so this is an allowlist
   * instead: anything not on it (every wrapper, every {@code TreeSet}/{@code SortedSet} regardless of
   * comparator, any custom {@code Set} implementation) simply skips the shortcut, which is always safe (just
   * slower).
   * <p>
   * The {@code SortedSet} exclusion is explicit, not implied by "not {@code HashSet}/{@code ImmutableSet}":
   * {@code instanceof} is subclass-permeable, and Guava's {@code ImmutableSortedSet} - despite being just as
   * comparator-driven as a {@code TreeSet} - IS an {@code ImmutableSet} (its concrete implementation extends
   * it), so it would otherwise pass this allowlist.
   */
  private static boolean isSafeForEqualsShortCircuit(Collection<? extends Comparable> set) {
    return (set instanceof HashSet || set instanceof ImmutableSet) && !(set instanceof SortedSet);
  }


  /**
   * Returns a sorted copy of {@code collection}, used to compare {@link Set}s in a canonical order since their
   * iteration order isn't guaranteed to be consistent with {@code equals()}. Sorts null-first, matching the
   * null-handling convention used elsewhere in this class - {@link Collections#sort} would otherwise NPE on a
   * {@code null} element.
   */
  private static List<Comparable> sorted(Collection<? extends Comparable> collection) {
    List<Comparable> list = new ArrayList<>(collection);
    list.sort((x, y) -> {
      if (x == null && y == null) {
        return 0;
      } else if (x == null) {
        return -1;
      } else if (y == null) {
        return 1;
      } else {
        //noinspection unchecked
        return x.compareTo(y);
      }
    });
    return list;
  }
}
