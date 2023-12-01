package org.pharmgkb.common.comparator;

import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;


/**
 * Comparator for haplotype names.  This takes common allele nomenclature into account (i.e. treat numbers as numbers,
 * not strings).
 * <p>
 * <b>WARNING!  DANGER!</b> This comparator does NOT comply with the comparator contract.
 * It DOES NOT GUARANTEE TRANSITIVITY (i.e. if A > B and B > C, then A > C) if all haplotype names being compared are
 * not of like kind (i.e. all strict star pattern, all loose star pattern, or all non-star pattern).
 * See {@code HaplotypeNameComparatorTest.testMixedNomenclature()} for an example.
 * <p>
 * The rules for how this sorts:
 * <ol>
 *   <li>The terms <code>Any</code>, <code>All</code>, and <code>Reference</code> always get sorted to the beginning</li>
 *   <li>The terms <code>Unknown</code> and <code>Other</code> always get sorted to the end</li>
 *   <li>if the term contains numbers (e.g. *2, H3, *3B) the terms will be String sorted by the initial character(s), 
 *   Integer sorted by the following numbers, then String sorted by any following characters</li>
 *   <li>Otherwise just do regular String sorting</li>
 * </ol>
 *
 * @author Ryan Whaley
 */
public class HaplotypeNameComparator implements Comparator<String> {
  private static final Pattern sf_strictStarPattern = Pattern.compile("(.*)\\*(\\d+)(.*)");
  private static final Pattern sf_looseStarPattern = Pattern.compile("(\\D*)(\\d+)(.*)");
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

    Matcher matcher1 = sf_strictStarPattern.matcher(name1);
    Matcher matcher2 = sf_strictStarPattern.matcher(name2);
    if (matcher1.matches() && matcher2.matches()) {
      return compareMatchers(matcher1, matcher2);
    }

    matcher1 = sf_looseStarPattern.matcher(name1);
    matcher2 = sf_looseStarPattern.matcher(name2);
    if (matcher1.matches() && matcher2.matches()) {
      return compareMatchers(matcher1, matcher2);
    }
    return ObjectUtils.compare(name1, name2);
  }

  private int compareMatchers(Matcher matcher1, Matcher matcher2) {
    String prePortion1 = StringUtils.trimToNull(matcher1.group(1));
    String prePortion2 = StringUtils.trimToNull(matcher2.group(1));
    int rez = ObjectUtils.compare(prePortion1, prePortion2);
    if (rez != 0) {
      return rez;
    }

    String starPortion1 = matcher1.group(2);
    String starPortion2 = matcher2.group(2);
    int star1 = Integer.parseInt(starPortion1);
    int star2 = Integer.parseInt(starPortion2);
    rez = ObjectUtils.compare(star1, star2);
    if (rez != 0) {
      return rez;
    }

    String restPortion1 = StringUtils.trimToNull(matcher1.group(3));
    String restPortion2 = StringUtils.trimToNull(matcher2.group(3));
    return ObjectUtils.compare(restPortion1, restPortion2);
  }
}
