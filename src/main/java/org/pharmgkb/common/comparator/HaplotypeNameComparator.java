package org.pharmgkb.common.comparator;

import java.util.Comparator;
import java.util.List;
import com.google.common.collect.ImmutableList;
import org.jspecify.annotations.Nullable;


/**
 * Comparator for haplotype names.  This takes common allele nomenclature into account (i.e. treat numbers as numbers,
 * not strings).
 * <p>
 * The rules for how this sorts:
 * <ol>
 *   <li>The terms <code>Any</code>, <code>All</code>, and <code>Reference</code> (case-insensitive) always get
 *   sorted to the beginning</li>
 *   <li>The terms <code>Unknown</code> and <code>Other</code> (case-insensitive) always get sorted to the end</li>
 *   <li>Anything else is compared using {@link NaturalStringComparator}, which tokenizes and compares
 *   numerically when appropriate (and falls back to regular String comparison otherwise)</li>
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
  public int compare(@Nullable String name1, @Nullable String name2) {

    //noinspection StringEquality
    if (name1 == name2) {
      return 0;
    }
    if (name1 == null) {
      return -1;
    }
    if (name2 == null) {
      return 1;
    }
    if (name1.equals(name2)) {
      return 0;
    }

    boolean top1 = containsIgnoreCase(sf_topTerms, name1);
    boolean top2 = containsIgnoreCase(sf_topTerms, name2);
    if (top1 != top2) {
      return top1 ? -1 : 1;
    }
    boolean bottom1 = containsIgnoreCase(sf_bottomTerms, name1);
    boolean bottom2 = containsIgnoreCase(sf_bottomTerms, name2);
    if (bottom1 != bottom2) {
      return bottom1 ? 1 : -1;
    }

    return NaturalStringComparator.getComparator().compare(name1, name2);
  }

  private static boolean containsIgnoreCase(List<String> terms, String name) {
    for (String term : terms) {
      if (term.equalsIgnoreCase(name)) {
        return true;
      }
    }
    return false;
  }
}
