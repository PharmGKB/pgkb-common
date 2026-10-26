package org.pharmgkb.common.comparator;

import java.util.Comparator;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;


/**
 * Comparator for chromosome names.  This expects the chromosome name to either be numeric or start with "chr"
 * (case-insensitive).
 * <p>
 * Numeric names sort first (numerically), followed by X and Y, followed by the mitochondrial chromosome
 * ("MT" or "M", case-insensitive). Any other non-numeric name is sorted lexicographically alongside X/Y.
 *
 * @author Mark Woon
 */
public class ChromosomeNameComparator implements Comparator<String> {
  private static final ChromosomeNameComparator sf_comparator = new ChromosomeNameComparator();

  /**
   * Gets an instance of this comparator.
   *
   * @return an instance of this comparator
   */
  public static ChromosomeNameComparator getComparator() {
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
    } else if (name2 == null) {
      return 1;
    }

    String origName1 = name1;
    String origName2 = name2;
    if (name1.regionMatches(true, 0, "chr", 0, 3)) {
      name1 = name1.substring(3);
    }
    boolean isName1Numeric = StringUtils.isNumeric(name1);
    if (name2.regionMatches(true, 0, "chr", 0, 3)) {
      name2 = name2.substring(3);
    }
    boolean isName2Numeric = StringUtils.isNumeric(name2);

    // assume non-numeric is X, Y or the mitochondrial chromosome (MT/M), which sorts after X/Y
    if (isName1Numeric) {
      if (isName2Numeric) {
        int cmp = ObjectUtils.compare(parseChromosomeNumber(origName1, name1), parseChromosomeNumber(origName2, name2));
        if (cmp == 0) {
          // tie-break on raw digits so e.g. "01" doesn't compare equal to "1"
          cmp = name1.compareTo(name2);
        }
        return cmp;
      } else {
        return -1;
      }
    } else {
      if (isName2Numeric) {
        return 1;
      } else {
        boolean isName1Mito = isMitochondrial(name1);
        boolean isName2Mito = isMitochondrial(name2);
        if (isName1Mito != isName2Mito) {
          return isName1Mito ? 1 : -1;
        }
        int cmp = name1.compareToIgnoreCase(name2);
        if (cmp == 0 && !isName1Mito && !isCanonicalSingleLetter(name1)) {
          // equal ignoring case, and not X/Y/mito (which are the same chromosome in any case): tie-break on
          // case so distinct contig names (e.g. "chrUn_GL000195v1" vs "chrUn_gl000195v1") don't collapse into
          // one, e.g. in a TreeSet
          cmp = name1.compareTo(name2);
        }
        return cmp;
      }
    }
  }

  private static boolean isMitochondrial(String name) {
    return name.equalsIgnoreCase("MT") || name.equalsIgnoreCase("M");
  }

  private static boolean isCanonicalSingleLetter(String name) {
    return name.equalsIgnoreCase("X") || name.equalsIgnoreCase("Y");
  }

  private static long parseChromosomeNumber(String original, String number) {
    try {
      return Long.parseLong(number);
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(
          "Chromosome number in '" + original + "' is too large to compare (max " + Long.MAX_VALUE + ")", ex);
    }
  }
}
