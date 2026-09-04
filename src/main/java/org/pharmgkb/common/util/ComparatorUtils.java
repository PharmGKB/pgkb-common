package org.pharmgkb.common.util;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import org.jspecify.annotations.Nullable;
import org.pharmgkb.common.comparator.CollectionComparator;


/**
 * This class contains utilities to assist in comparisons.
 *
 * @author Mark Woon
 */
public class ComparatorUtils {


  /**
   * Private constructor.
   */
  private ComparatorUtils() {
  }


  /**
   * Do a standard comparison between two comparable objects, handling nulls
   * appropriately.
   *
   * @param a the first object to compare
   * @param b the second object to compare
   * @return a negative integer, zero, or a positive integer if the first
   * object is less than, equal to, or greater than the second object
   * @throws ClassCastException if {@code a} and {@code b} are not mutually {@link Comparable} - this method's
   * siblings ({@link #compareCollection}, {@link #compareMap}, {@link #compareCollectionOfMaps}) already
   * document this same unchecked propagation
   */
  public static int compare(@Nullable Comparable a, @Nullable Comparable b) {

    if (a == b) {
      return 0;
    }
    if (a == null) {
      // b != null
      return -1;
    } else {
      // a != null
      if (b == null) {
        return 1;
      } else {
        // b != null
        //noinspection unchecked
        return (a.compareTo(b));
      }
    }
  }


  /**
   * Do a comparison between two strings, ignoring case and handling nulls appropriately.
   *
   * @param a the first string to compare
   * @param b the second string to compare
   * @return a negative integer, zero, or a positive integer if the first
   * string is less than, equal to, or greater than the second string
   */
  public static int compareIgnoreCase(@Nullable String a, @Nullable String b) {

    //noinspection StringEquality
    if (a == b) {
      return 0;
    }
    if (a == null) {
      // b != null
      return -1;
    } else {
      // a != null
      if (b == null) {
        return 1;
      } else {
        // b != null
        return (a.compareToIgnoreCase(b));
      }
    }
  }


  /**
   * Do a comparison between numbers (in String form).
   * <p>
   * Parses via {@link BigDecimal} for exact precision (unlike {@code Double}, no precision loss above 2^53 and
   * no {@code -0.0}/{@code 0.0} sign mismatch). This means IEEE 754 special values like {@code "NaN"} and
   * {@code "Infinity"}, and non-decimal forms like hexadecimal floating-point literals, are NOT accepted -
   * {@code BigDecimal} has no representation for them.
   *
   * @param a the first number to compare
   * @param b the second number to compare
   * @return a negative integer, zero, or a positive integer if the first
   * object is less than, equal to, or greater than the second object
   * @throws NumberFormatException if a or b is not a valid decimal number
   */
  public static int compareNumbers(@Nullable String a, @Nullable String b) {

    //noinspection StringEquality
    if (a == b) {
      return 0;
    }
    if (a == null) {
      return -1;
    } else if (b == null) {
      return 1;
    }
    // BigDecimal(String), unlike the Double.parseDouble() this replaced, doesn't tolerate leading/trailing
    // whitespace - strip it first so whitespace-padded input (already assumed normal elsewhere in this
    // codebase, e.g. CliHelper.getValue()/ExtendedEnumHelper.add()) doesn't newly throw. strip() (not
    // trim()) to match those callers' own StringUtils.stripToNull, which is Character.isWhitespace()-based -
    // trim() only strips <= U+0020, missing Unicode whitespace like U+2003 (EM SPACE) that stripToNull would
    // treat as padding.
    return parseNumber(a.strip()).compareTo(parseNumber(b.strip()));
  }

  private static BigDecimal parseNumber(String strippedValue) {
    if (strippedValue.isEmpty()) {
      // new BigDecimal("") throws NumberFormatException with a null message; restore the informative
      // "empty String" message Double.parseDouble("") (the previous implementation) gave
      throw new NumberFormatException("empty String");
    }
    return new BigDecimal(strippedValue);
  }


  /**
   * Compare two collections.
   * <p>
   * See {@link CollectionComparator} for the exact rules (List/Set-only restriction, Set canonicalization,
   * and null-vs-empty equivalence).
   *
   * @param a the first collection to compare
   * @param b the second collection to compare
   * @return a negative integer, zero, or a positive integer if the first
   * collection is less than, equal to, or greater than the second collection
   * @throws IllegalArgumentException if {@code a} and {@code b} are both non-{@code null} and aren't the
   * same kind (both {@link List} or both {@link java.util.Set}), or either is some other kind of
   * {@link Collection} - see {@link CollectionComparator}
   * @throws ClassCastException if the collections' elements are not mutually {@link Comparable}
   */
  public static int compareCollection(@Nullable Collection<? extends Comparable> a,
      @Nullable Collection<? extends Comparable> b) {
    return CollectionComparator.getComparator().compare(a, b);
  }


  /**
   * Compare two collections of maps.
   * <p>
   * First compare by collection size, then compare by map (using {@link #compareMap(Map, Map)}). A {@code null}
   * collection is treated the same as (not distinct from) an empty one, consistent with {@link #compareCollection}
   * - note this differs from {@link #compareMap}, which does treat a {@code null} map as sorting strictly before
   * an empty one.
   *
   * @param a the first collection to compare
   * @param b the second collection to compare
   * @return a negative integer, zero, or a positive integer if the first
   * collection is less than, equal to, or greater than the second collection
   * @throws ClassCastException if collections do not contain maps
   * @throws IllegalArgumentException if {@code a} or {@code b} is a non-{@code null} value that isn't a
   * {@link List} - unlike {@link #compareCollection}, {@code Map} isn't {@link Comparable}, so a
   * {@code Set} of maps can't be canonicalized into a well-defined order; comparing by raw iteration order
   * could pair up unrelated maps. A {@code null} operand is exempt from this check (it has no "kind" of its
   * own - see the null-vs-empty handling above)
   * @throws UnsupportedOperationException if a pair of maps being compared has a value under the same key
   * that isn't mutually comparable between the two maps - see {@link #compareMap(Map, Map)}, which this
   * delegates to per-element
   */
  public static int compareCollectionOfMaps(@Nullable Collection a, @Nullable Collection b) throws ClassCastException {
    if (a == b) {
      return 0;
    }
    // type is validated unconditionally (when both are non-null), not just when they happen to be the same
    // size - matching CollectionComparator's own equivalent fix. A null operand has no "kind" of its own
    // (null-vs-empty equivalence is handled below via size, not here), so it's exempt from this check the
    // same way it's exempt from the size-based comparison.
    if (a != null && b != null && (!(a instanceof List) || !(b instanceof List))) {
      throw new IllegalArgumentException("Unsupported collection type for comparison, must be a List (" +
          a.getClass().getName() + " vs " + b.getClass().getName() + ")");
    }
    // null is treated as size 0 (i.e. equivalent to an empty collection), not as distinct from/sorting
    // before one - consistent with compareCollection(). By the time we get past the isEmpty() check, both a
    // and b are guaranteed non-null (if either were null, its size would be 0, and since sizes are equal at
    // that point, both would be size 0 and we'd have already returned)
    int aSize = a == null ? 0 : a.size();
    int bSize = b == null ? 0 : b.size();
    int rez = Integer.compare(aSize, bSize);
    if (rez != 0) {
      return rez;
    }
    if (aSize == 0) {
      return 0;
    }
    @SuppressWarnings("unchecked")
    Iterator<Map> aIt = a.iterator();
    @SuppressWarnings({ "unchecked" })
    Iterator<Map> bIt = b.iterator();
    while (aIt.hasNext()) {
      rez = compareMap(aIt.next(), bIt.next());
      if (rez != 0) {
        return rez;
      }
    }
    return 0;
  }


  /**
   * Compare two maps.
   * <p>
   * First compare by keys, then compare by values. A {@code null} value sorts strictly before a non-null
   * value - including an empty {@link Collection} value - matching this method's own null-map handling
   * (a {@code null} map also sorts strictly before an empty one). This is different from
   * {@link #compareCollection}/{@link #compareCollectionOfMaps}, where a {@code null} collection is treated
   * as equivalent to (not distinct from) an empty one; that's the deliberate exception here, not the rule -
   * kept as-is since there's no real usage needing a {@code null} map value to compare equal to an empty
   * collection value.
   *
   * @param a the first map to compare
   * @param b the second map to compare
   * @return a negative integer, zero, or a positive integer if the first
   * map is less than, equal to, or greater than the second map
   * @throws UnsupportedOperationException if a shared key's value is of an unsupported type, or if the two maps
   * have a shared key whose value is a {@link Collection} in one map but not the other (or vice versa for
   * {@link Comparable})
   * @throws IllegalArgumentException if a shared key's {@link Collection} value is a {@code List} in one map
   * and a {@code Set} in the other, or is some other unsupported kind of {@link Collection} in both
   * (see {@link #compareCollection})
   * @throws ClassCastException if the maps' keys are not mutually {@link Comparable}
   */
  public static int compareMap(@Nullable Map a, @Nullable Map b) {
    if (a == b) {
      return 0;
    }
    if (a == null) {
      return -1;
    } else if (b == null) {
      return 1;
    }
    // sorting keys/values (below) is O(n log n) per comparison - skip it entirely when the two maps are
    // already known to be equals()-equal (an O(n) hash-based check), which is the common case when comparing
    // maps that mostly don't change between comparisons. Both directions must be checked, not just
    // a.equals(b): Map.equals() is NOT guaranteed symmetric when one side is a TreeMap whose key comparator
    // is inconsistent with equals() (AbstractMap.equals() looks up each of the OTHER map's keys via the
    // receiver's own get(), which for a TreeMap means the receiver's comparator, not equals()) - e.g. a
    // TreeMap<>(ChromosomeNameComparator) keyed on "chr1" .equals() a plain Map keyed on "1" with the same
    // value (the comparator says the keys are equal), but that plain Map does NOT .equals() the TreeMap back
    // (its own standard String.equals() says "chr1" != "1"). Trusting a one-directional equals() here would
    // make compareMap(a,b) and compareMap(b,a) both report the SAME sign instead of opposite ones - a hard
    // antisymmetry violation - so both directions must agree before this short-circuit is safe to take.
    // Even with both directions agreeing, the short-circuit is only safe when BOTH maps pass
    // isSafeForEqualsShortCircuit() below - a small ALLOWLIST of known-safe, non-comparator-driven Map
    // implementations whose values also don't hide an unsafe Set - e.g. two TreeMap<>(ChromosomeNameComparator)s
    // keyed on "chr1" and "1" respectively are mutually equals()-true per that comparator's own notion of key
    // equality, while the canonicalized key comparison below - which always sorts by the keys' plain natural
    // ordering, not any Map's own comparator - disagrees. Trusting the short-circuit there breaks
    // TRANSITIVITY against a third, differently-backed Map, the same way it does for CollectionComparator's
    // own equivalent fix.
    if (isSafeForEqualsShortCircuit(a) && isSafeForEqualsShortCircuit(b) && a.equals(b) && b.equals(a)) {
      return 0;
    }

    //noinspection unchecked
    int rez = compareCollection(a.keySet(), b.keySet());
    if (rez != 0) {
      return rez;
    }

    // compareCollection() above pairs up keys in SORTED (canonical) order via compareTo, not equals()/hashCode()
    // or raw iteration order - so values must be paired up the same way, by sorting each map's entries by key.
    // Looking values up via a.get(ka)/b.get(ka) with the same key object would incorrectly assume the two maps
    // share equals()-equal keys, which isn't guaranteed even when keys are compareTo-equal (e.g. BigDecimal
    // "1.0" vs "1.00"), and could make this method violate Comparator antisymmetry (both directions reporting
    // "greater than" when a lookup misses both ways).
    Comparator<Map.Entry> byKey = (e1, e2) -> {
      //noinspection unchecked
      int rez2 = compare((Comparable)e1.getKey(), (Comparable)e2.getKey());
      if (rez2 == 0) {
        // compareTo() can tie for genuinely different keys (e.g. BigDecimal "1.0" vs "1.00", where compareTo()
        // isn't consistent with equals()) - if a single map holds more than one such tied key, sorting by
        // compareTo() alone isn't deterministic (a stable sort just preserves each map's own, possibly
        // different, insertion order for the tied entries). Falling back to toString() is a pure function of
        // the key itself, so it produces the same order regardless of either map's iteration order.
        rez2 = String.valueOf(e1.getKey()).compareTo(String.valueOf(e2.getKey()));
      }
      return rez2;
    };
    List<Map.Entry> aEntries = new ArrayList<>(a.entrySet());
    List<Map.Entry> bEntries = new ArrayList<>(b.entrySet());
    aEntries.sort(byKey);
    bEntries.sort(byKey);
    Iterator<Map.Entry> aValueIt = aEntries.iterator();
    Iterator<Map.Entry> bValueIt = bEntries.iterator();
    while (aValueIt.hasNext()) {
      Object va = aValueIt.next().getValue();
      Object vb = bValueIt.next().getValue();
      if (va == null && vb == null) {
        rez = 0;
      } else if (va == null) {
        rez = -1;
      } else if (vb == null) {
        rez = 1;
      } else if (va instanceof Collection) {
        if (!(vb instanceof Collection)) {
          throw new UnsupportedOperationException("Can't compare " + va.getClass() + " to " + vb.getClass());
        }
        try {
          //noinspection unchecked
          rez = compareCollection((Collection)va, (Collection)vb);
        } catch (ClassCastException ex) {
          // both are Collections, but their elements are of mutually incompatible Comparable types (e.g.
          // Integer vs String) - same treatment as the scalar-value branch below
          throw new UnsupportedOperationException("Can't compare " + va.getClass() + " to " + vb.getClass(), ex);
        }
      } else if (va instanceof Comparable) {
        if (!(vb instanceof Comparable)) {
          throw new UnsupportedOperationException("Can't compare " + va.getClass() + " to " + vb.getClass());
        }
        try {
          rez = compare((Comparable)va, (Comparable)vb);
        } catch (ClassCastException ex) {
          // both are Comparable, but of mutually incompatible concrete types (e.g. Integer vs String)
          throw new UnsupportedOperationException("Can't compare " + va.getClass() + " to " + vb.getClass(), ex);
        }
      } else {
        throw new UnsupportedOperationException("Don't know how to compare " + va.getClass());
      }
      if (rez != 0) {
        return rez;
      }
    }
    return 0;
  }


  /**
   * Returns whether {@code map}'s concrete runtime type is one of a small ALLOWLIST of known-safe,
   * non-comparator-driven {@link Map} implementations - and none of its values hide an unsafe {@link Set} one
   * level down (e.g. a plain {@code HashMap} whose value is a {@code TreeSet<>(customComparator)}) - safe to
   * trust {@code equals()} for the short-circuit in {@link #compareMap}. A blacklist (rejecting only
   * recognized-unsafe types) can never enumerate every {@code equals()}-delegating wrapper - many, e.g.
   * {@code java.util.Collections$UnmodifiableMap}, are package-private JDK internals that can't even be
   * {@code instanceof}-checked - so this is an allowlist instead: anything not on it (every wrapper, every
   * {@code TreeMap}/{@code SortedMap} regardless of comparator, any custom {@code Map} implementation, or a
   * map with an unsafe {@code Set} value) simply skips the shortcut, which is always safe (just slower).
   * {@code Map}-typed values don't need this same recursive check - {@link #compareMap} doesn't support
   * nested {@code Map} values at all today (a non-{@code Collection}, non-{@code Comparable} value already
   * throws via "Don't know how to compare").
   * <p>
   * The {@code SortedMap}/{@code SortedSet} exclusions (for the map itself and for any nested {@code Set}
   * value, respectively) are explicit, not implied by "not {@code HashMap}/{@code HashSet}/{@code
   * ImmutableMap}/{@code ImmutableSet}": {@code instanceof} is subclass-permeable, and Guava's
   * {@code ImmutableSortedMap}/{@code ImmutableSortedSet} - despite being just as comparator-driven as a
   * {@code TreeMap}/{@code TreeSet} - ARE an {@code ImmutableMap}/{@code ImmutableSet} (their concrete
   * implementations extend them), so they would otherwise pass this allowlist.
   */
  private static boolean isSafeForEqualsShortCircuit(Map<?, ?> map) {
    if (!(map instanceof HashMap || map instanceof ImmutableMap) || map instanceof SortedMap) {
      return false;
    }
    for (Object value : map.values()) {
      if (value instanceof Set<?> set &&
          (!(set instanceof HashSet || set instanceof ImmutableSet) || set instanceof SortedSet)) {
        return false;
      }
    }
    return true;
  }
}
