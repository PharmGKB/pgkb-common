package org.pharmgkb.common.comparator;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import javax.annotation.Nullable;


/**
 * This comparator compares {@link Collection}s that hold {@link Comparable} elements and sorts them first by size, then
 * elements.
 *
 * @author Mark Woon
 */
public class CollectionComparator extends AbstractComparator<Collection<? extends Comparable>> {
  private static final Comparator<Collection<? extends Comparable>> sf_comparator = new CollectionComparator();


  /**
   * Gets an instance of this comparator.
   */
  public static Comparator<Collection<? extends Comparable>> getComparator() {
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
    setOrder(order);
  }



  @Override
  public int compare(@Nullable Collection<? extends Comparable> a, @Nullable Collection<? extends Comparable> b) {
    int aSize = 0;
    if (a != null) {
      aSize = a.size();
    }
    int bSize = 0;
    if (b != null) {
      bSize = b.size();
    }
    int rez = Integer.compare(aSize, bSize);
    if (rez != 0) {
      return modReturn(rez);
    }
    // sizes are the same, so make sure it's not empty
    if (aSize == 0) {
      return 0;
    }
    Iterator<? extends Comparable> aIt = a.iterator();
    @SuppressWarnings({ "ConstantConditions" })
    Iterator<? extends Comparable> bIt = b.iterator();
    while (aIt.hasNext()) {
      //noinspection unchecked
      rez = aIt.next().compareTo(bIt.next());
      if (rez != 0) {
        return modReturn(rez);
      }
    }
    return 0;
  }
}
