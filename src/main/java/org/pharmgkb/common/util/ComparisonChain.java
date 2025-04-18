package org.pharmgkb.common.util;

import java.util.Collection;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.pharmgkb.common.comparator.ChromosomeNameComparator;
import org.pharmgkb.common.comparator.ChromosomePositionComparator;
import org.pharmgkb.common.comparator.HaplotypeNameComparator;


/**
 * This is a helper class to simplify implementing {@link Comparable}.
 *
 * @author Mark Woon
 */
public class ComparisonChain {
  private int m_comparison = 0;


  @SuppressWarnings("rawtypes")
  public ComparisonChain compare(@Nullable Comparable a, @Nullable Comparable b) {
    if (m_comparison != 0) {
      return this;
    }
    m_comparison = ComparatorUtils.compare(a, b);
    return this;
  }


  public ComparisonChain compareIgnoreCase(@Nullable String a, @Nullable String b) {
    if (m_comparison != 0) {
      return this;
    }
    m_comparison = ComparatorUtils.compareIgnoreCase(a, b);
    return this;
  }


  public ComparisonChain compareNumbers(@Nullable String a, @Nullable String b) {
    if (m_comparison != 0) {
      return this;
    }
    m_comparison = ComparatorUtils.compareNumbers(a, b);
    return this;
  }


  @SuppressWarnings("rawtypes")
  public ComparisonChain compare(@Nullable Collection<? extends Comparable> a,
      @Nullable Collection<? extends Comparable> b) {
    if (m_comparison != 0) {
      return this;
    }
    m_comparison = ComparatorUtils.compareCollection(a, b);
    return this;
  }


  @SuppressWarnings("rawtypes")
  public ComparisonChain compare(@Nullable Map a, @Nullable Map b) {
    if (m_comparison != 0) {
      return this;
    }
    m_comparison = ComparatorUtils.compareMap(a, b);
    return this;
  }


  public ComparisonChain compareCollectionOfMaps(@Nullable Collection a, @Nullable Collection b) {
    if (m_comparison != 0) {
      return this;
    }
    m_comparison = ComparatorUtils.compareCollectionOfMaps(a, b);
    return this;
  }


  /**
   * Compares chromosome names.
   */
  public ComparisonChain compareChromosomeNames(@Nullable String a, @Nullable String b) {
    if (m_comparison != 0) {
      return this;
    }
    m_comparison = ChromosomeNameComparator.getComparator().compare(a, b);
    return this;
  }


  /**
   * Compares chromosomal positions (in the format chrX:1234).
   */
  public ComparisonChain compareChromosomePositions(@Nullable String a, @Nullable String b) {
    if (m_comparison != 0) {
      return this;
    }
    m_comparison = ChromosomePositionComparator.getComparator().compare(a, b);
    return this;
  }


  /**
   * Compares haplotype names.
   */
  public ComparisonChain compareHaplotypeNames(@Nullable String a, @Nullable String b) {
    if (m_comparison != 0) {
      return this;
    }
    m_comparison = HaplotypeNameComparator.getComparator().compare(a, b);
    return this;
  }


  public int result() {
    return m_comparison;
  }
}
