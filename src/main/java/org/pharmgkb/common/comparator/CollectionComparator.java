package org.pharmgkb.common.comparator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import org.jspecify.annotations.Nullable;


/**
 * This comparator compares {@link Collection}s that hold {@link Comparable} elements and sorts them first by size, then
 * elements. A {@code null} collection is treated the same as an empty one, so a collection that was never populated
 * compares equal to one that was populated and then emptied.
 * <p>
 * Elements are compared in order: a {@link List}'s own order, a {@link SortedSet}'s own (sorted) order, and for any
 * other collection (e.g. a {@link java.util.HashSet}, whose iteration order isn't meaningful), natural order. A
 * {@code null} element sorts first. A {@code SortedSet} with a custom comparator is therefore compared in that
 * comparator's order, which may not match the natural order used for other collections.
 * <p>
 * As with {@link java.util.TreeSet}, comparing same-sized collections whose elements aren't mutually comparable
 * (e.g. {@code Integer}s and {@code String}s) throws {@link ClassCastException}.
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
    // null counts as size 0; past the aSize == 0 check below, both a and b are non-null
    int aSize = a == null ? 0 : a.size();
    int bSize = b == null ? 0 : b.size();
    int rez = Integer.compare(aSize, bSize);
    if (rez != 0) {
      return modReturn(rez);
    }
    if (aSize == 0) {
      return 0;
    }
    Iterator<? extends Comparable> aIt = ordered(a).iterator();
    @SuppressWarnings({ "ConstantConditions" })
    Iterator<? extends Comparable> bIt = ordered(b).iterator();
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
   * Returns {@code collection} as-is if it has a meaningful order ({@link List} or {@link SortedSet}), otherwise a
   * copy sorted in natural order, nulls first.
   */
  private static Collection<? extends Comparable> ordered(Collection<? extends Comparable> collection) {
    if (collection instanceof List || collection instanceof SortedSet) {
      return collection;
    }
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
