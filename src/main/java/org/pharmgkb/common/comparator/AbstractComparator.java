package org.pharmgkb.common.comparator;

import java.util.Comparator;
import com.google.common.base.Preconditions;


/**
 * Abstract comparator allowing sort order to be specified.
 *
 * @author Mark Woon
 */
public abstract class AbstractComparator<T> implements Comparator<T>{
  private final SortOrder m_order;


  /**
   * Default constructor, sorts in natural order.
   */
  protected AbstractComparator() {
    this(SortOrder.NATURAL);
  }

  /**
   * Instantiates a comparator that sorts with the specified order.
   *
   * @param order the order in which results should be returned
   */
  protected AbstractComparator(SortOrder order) {
    Preconditions.checkNotNull(order);
    m_order = order;
  }


  /**
   * Gets the order in which results should be returned.

   * @return the order in which results should be returned
   */
  public final SortOrder getOrder() {
    return m_order;
  }


  /**
   * Modifies the return value appropriately based on sort direction.
   *
   * @param val the value to modify
   * @return the value based on sort direction
   */
  protected final int modReturn(int val) {
    if (m_order == SortOrder.REVERSE) {
      return reverse(val);
    }
    return val;
  }

  /**
   * Reverses the return value.
   *
   * @param val the value to modify
   * @return the reversed value
   */
  protected final int reverse(int val) {
    if (val > 0) {
      return -1;
    } else if (val < 0) {
      return 1;
    }
    return 0;
  }
}
