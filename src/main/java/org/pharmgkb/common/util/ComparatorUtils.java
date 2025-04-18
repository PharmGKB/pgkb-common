package org.pharmgkb.common.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
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


  /**
   * Compare two collections.
   *
   * @param a the first collection to compare
   * @param b the second collection to compare
   * @return a negative integer, zero, or a positive integer if the first
   * map is less than, equal to, or greater than the second collection
   */
  public static int compareCollection(@Nullable Collection<? extends Comparable> a,
      @Nullable Collection<? extends Comparable> b) {
    return CollectionComparator.getComparator().compare(a, b);
  }


  /**
   * Compare two collections of maps.
   * <p>
   * First compare by collection size, then compare by map (using {@link #compareMap(Map, Map)}).
   *
   * @param a the first collection to compare
   * @param b the second collection to compare
   * @return a negative integer, zero, or a positive integer if the first
   * map is less than, equal to, or greater than the second collection
   * @throws ClassCastException if collections do not contain maps
   */
  public static int compareCollectionOfMaps(@Nullable Collection a, @Nullable Collection b) throws ClassCastException {
    if (a == b) {
      return 0;
    }
    int aSize = 0;
    if (a != null) {
      aSize = a.size();
    }
    int bSize = 0;
    if (b != null) {
      bSize = b.size();
    }
    if (aSize == 0 && bSize == 0) {
      return 0;
    }
    int rez = Integer.compare(aSize, bSize);
    if (rez != 0) {
      return rez;
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
   * First compare by keys, then compare by values.
   *
   * @param a the first map to compare
   * @param b the second map to compare
   * @return a negative integer, zero, or a positive integer if the first
   * map is less than, equal to, or greater than the second map
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

    //noinspection unchecked
    int rez = compareCollection(a.keySet(), b.keySet());
    if (rez != 0) {
      return rez;
    }

    for (Object ka : a.keySet()) {
      Object va = a.get(ka);
      Object vb = b.get(ka);
      if (va instanceof Collection) {
        //noinspection unchecked
        rez = compareCollection((Collection)va, (Collection)vb);
      } else if (va instanceof Comparable) {
        rez = compare((Comparable)va, (Comparable)vb);
      } else {
        throw new UnsupportedOperationException("Don't know how to compare " + va.getClass());
      }
      if (rez != 0) {
        return rez;
      }
    }
    return 0;
  }
}
