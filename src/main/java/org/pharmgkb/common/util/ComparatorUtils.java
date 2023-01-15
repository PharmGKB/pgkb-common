package org.pharmgkb.common.util;

import java.util.Collection;
import org.checkerframework.checker.nullness.qual.Nullable;
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
   *
   * @param a the first number to compare
   * @param b the second number to compare
   * @return a negative integer, zero, or a positive integer if the first
   * object is less than, equal to, or greater than the second object
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
    return Double.compare(Double.parseDouble(a), Double.parseDouble(b));
  }


  public static int compareCollection(@Nullable Collection<? extends Comparable> a,
      @Nullable Collection<? extends Comparable> b) {
    return CollectionComparator.getComparator().compare(a, b);
  }
}
