package org.pharmgkb.common.comparator;

import java.util.Comparator;
import java.util.List;
import com.google.common.collect.ImmutableList;


/**
 * Comparator for haplotype names.  This takes common allele nomenclature into account (i.e. treat numbers as numbers,
 * not strings).
 * <p>
 * The rules for how this sorts:
 * <ol>
 *   <li>The terms <code>Any</code>, <code>All</code>, and <code>Reference</code> always get sorted to the beginning</li>
 *   <li>The terms <code>Unknown</code> and <code>Other</code> always get sorted to the end</li>
 *   <li>The terms are compared using {@link NaturalStringComparator} which will tokenize and compare numerically when appropriate</li>
 *   <li>Otherwise just do regular String sorting</li>
 * </ol>
 *
 * @author Ryan Whaley
 */
public class HaplotypeNameComparator implements Comparator<String> {
  private static final Comparator<String> sf_comparator = new HaplotypeNameComparator();
  private static final List<String> sf_topTerms = ImmutableList.of("Any", "All", "Reference");
  private static final List<String> sf_bottomTerms = ImmutableList.of("Other","Unknown");

  /**
   * Gets an instance of this comparator.
   *
   * @return an instance of this comparator
   */
  public static Comparator<String> getComparator() {
    return sf_comparator;
  }


  @Override
  public int compare(String name1, String name2) {

    //noinspection StringEquality
    if (name1 == name2) {
      return 0;
    }
    if (name1 == null) {
      return -1;
    } else if (name2 == null) {
      return 1;
    }
    
    if (sf_topTerms.contains(name1) || sf_bottomTerms.contains(name2)) {
      return -1;
    }
    if (sf_topTerms.contains(name2) || sf_bottomTerms.contains(name1)) {
      return 1;
    }

    return NaturalStringComparator.getComparator().compare(name1, name2);
  }
}
