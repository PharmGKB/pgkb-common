package org.pharmgkb.common.util;

import java.util.Collection;
import org.jspecify.annotations.Nullable;
import org.pharmgkb.common.comparator.ChromosomeNameComparator;
import org.pharmgkb.common.comparator.HaplotypeNameComparator;


/**
 * This is a helper class to simplify implementing {@link Comparable}.
 * <p>
 * Like Guava's {@link com.google.common.collect.ComparisonChain}, except that {@code null} sorts before
 * non-{@code null} (as in {@link ComparatorUtils#compare(Comparable, Comparable)}) instead of throwing
 * {@link NullPointerException}.
 *
 * @author Mark Woon
 */
public class NullSafeComparisonChain {
  private int m_comparison = 0;


  /**
   * See {@link ComparatorUtils#compare(Comparable, Comparable)}.
   *
   * @throws ClassCastException if {@code a} and {@code b} are not mutually {@link Comparable}
   */
  @SuppressWarnings("rawtypes")
  public NullSafeComparisonChain compare(@Nullable Comparable a, @Nullable Comparable b) {
    if (m_comparison != 0) {
      return this;
    }
    m_comparison = ComparatorUtils.compare(a, b);
    return this;
  }


  /**
   * See {@link ComparatorUtils#compareCollection(Collection, Collection)}.
   *
   * @throws ClassCastException if the collections' elements are not mutually {@link Comparable}
   */
  @SuppressWarnings("rawtypes")
  public NullSafeComparisonChain compare(@Nullable Collection<? extends Comparable> a,
      @Nullable Collection<? extends Comparable> b) {
    if (m_comparison != 0) {
      return this;
    }
    m_comparison = ComparatorUtils.compareCollection(a, b);
    return this;
  }


  /**
   * Compares chromosome names.
   *
   * @throws IllegalArgumentException only if {@code a} and {@code b} are both non-{@code null} and both
   * numeric (after stripping any {@code chr} prefix), and either exceeds {@link Long#MAX_VALUE}
   */
  public NullSafeComparisonChain compareChromosomeNames(@Nullable String a, @Nullable String b) {
    if (m_comparison != 0) {
      return this;
    }
    m_comparison = ChromosomeNameComparator.getComparator().compare(a, b);
    return this;
  }


  /**
   * Compares haplotype names.
   *
   * @throws IllegalArgumentException only if neither {@code a} nor {@code b} is {@code null}, equal, or one of
   * the special-cased top/bottom terms (see {@link HaplotypeNameComparator}), and a numeric component of
   * either exceeds {@link Long#MAX_VALUE}
   */
  public NullSafeComparisonChain compareHaplotypeNames(@Nullable String a, @Nullable String b) {
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
