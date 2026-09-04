package org.pharmgkb.common.util;

import java.util.Collection;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.pharmgkb.common.comparator.ChromosomeNameComparator;
import org.pharmgkb.common.comparator.ChromosomePositionComparator;
import org.pharmgkb.common.comparator.HaplotypeNameComparator;


/**
 * This is a helper class to simplify implementing {@link Comparable}.
 * <p>
 * Deliberately not named {@code ComparisonChain} - that name collides with
 * {@link com.google.common.collect.ComparisonChain}, which this class otherwise resembles. The difference
 * that justifies keeping a separate class instead of using Guava's directly: Guava's
 * {@code compare(Comparable, Comparable)} calls {@code left.compareTo(right)} with no null check at all
 * (throwing {@link NullPointerException} for a null operand), while every method here treats {@code null}
 * as sorting before non-{@code null}, matching {@link ComparatorUtils#compare(Comparable, Comparable)}'s own
 * null-safe semantics.
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
   * See {@link ComparatorUtils#compareIgnoreCase(String, String)}. Unlike this class's other chain methods,
   * cannot throw - {@link String#compareToIgnoreCase} never does.
   */
  public NullSafeComparisonChain compareIgnoreCase(@Nullable String a, @Nullable String b) {
    if (m_comparison != 0) {
      return this;
    }
    m_comparison = ComparatorUtils.compareIgnoreCase(a, b);
    return this;
  }


  /**
   * See {@link ComparatorUtils#compareNumbers(String, String)}.
   *
   * @throws NumberFormatException if {@code a} or {@code b} is a non-{@code null} value that isn't a valid
   * decimal number
   */
  public NullSafeComparisonChain compareNumbers(@Nullable String a, @Nullable String b) {
    if (m_comparison != 0) {
      return this;
    }
    m_comparison = ComparatorUtils.compareNumbers(a, b);
    return this;
  }


  /**
   * See {@link ComparatorUtils#compareCollection(Collection, Collection)}.
   *
   * @throws IllegalArgumentException if {@code a} and {@code b} are both non-{@code null} and aren't the
   * same kind (both {@link java.util.List} or both {@link java.util.Set}), or either is some other kind of
   * {@link Collection}
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
   * See {@link ComparatorUtils#compareMap(Map, Map)}.
   *
   * @throws UnsupportedOperationException if a shared key's value is of an unsupported type, or if the two
   * maps have a shared key whose value is a {@link Collection} in one map but not the other (or vice versa
   * for {@link Comparable})
   * @throws IllegalArgumentException if a shared key's {@link Collection} value is a {@link java.util.List} in
   * one map and a {@link java.util.Set} in the other, or is some other unsupported kind of {@link Collection}
   * in both
   * @throws ClassCastException if the maps' keys are not mutually {@link Comparable}
   */
  @SuppressWarnings("rawtypes")
  public NullSafeComparisonChain compare(@Nullable Map a, @Nullable Map b) {
    if (m_comparison != 0) {
      return this;
    }
    m_comparison = ComparatorUtils.compareMap(a, b);
    return this;
  }


  /**
   * See {@link ComparatorUtils#compareCollectionOfMaps(Collection, Collection)}.
   *
   * @throws ClassCastException if collections do not contain maps
   * @throws IllegalArgumentException if {@code a} or {@code b} is a non-{@code null} value that isn't a
   * {@link java.util.List}
   * @throws UnsupportedOperationException if a pair of maps being compared has a value under the same key
   * that isn't mutually comparable between the two maps
   */
  public NullSafeComparisonChain compareCollectionOfMaps(@Nullable Collection a, @Nullable Collection b) {
    if (m_comparison != 0) {
      return this;
    }
    m_comparison = ComparatorUtils.compareCollectionOfMaps(a, b);
    return this;
  }


  /**
   * Compares chromosome names.
   *
   * @throws IllegalArgumentException if a numeric component of {@code a} or {@code b} exceeds
   * {@link Long#MAX_VALUE}
   */
  public NullSafeComparisonChain compareChromosomeNames(@Nullable String a, @Nullable String b) {
    if (m_comparison != 0) {
      return this;
    }
    m_comparison = ChromosomeNameComparator.getComparator().compare(a, b);
    return this;
  }


  /**
   * Compares chromosomal positions (in the format chrX:1234).
   *
   * @throws IllegalArgumentException if {@code a} or {@code b} is a non-{@code null} value that isn't in the
   * expected {@code chrX:1234} format, or if the position component exceeds {@link Long#MAX_VALUE}
   */
  public NullSafeComparisonChain compareChromosomePositions(@Nullable String a, @Nullable String b) {
    if (m_comparison != 0) {
      return this;
    }
    m_comparison = ChromosomePositionComparator.getComparator().compare(a, b);
    return this;
  }


  /**
   * Compares haplotype names.
   *
   * @throws IllegalArgumentException if a numeric component of {@code a} or {@code b} exceeds
   * {@link Long#MAX_VALUE}
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
