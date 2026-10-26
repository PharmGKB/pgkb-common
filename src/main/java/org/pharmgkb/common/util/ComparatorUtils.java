package org.pharmgkb.common.util;

import java.util.Collection;
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
   * @throws ClassCastException if {@code a} and {@code b} are not mutually {@link Comparable}
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
   * Compare two collections.
   * <p>
   * See {@link CollectionComparator} for the rules.
   *
   * @param a the first collection to compare
   * @param b the second collection to compare
   * @return a negative integer, zero, or a positive integer if the first
   * collection is less than, equal to, or greater than the second collection
   * @throws ClassCastException if the collections' elements are not mutually {@link Comparable}
   */
  public static int compareCollection(@Nullable Collection<? extends Comparable> a,
      @Nullable Collection<? extends Comparable> b) {
    return CollectionComparator.getComparator().compare(a, b);
  }
}
