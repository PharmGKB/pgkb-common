/*
 ----- BEGIN LICENSE BLOCK -----
 This Source Code Form is subject to the terms of the Mozilla Public License, v.2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ----- END LICENSE BLOCK -----
 */
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
 *
 * @author Ryan Whaley
 */
public class HaplotypeNameComparator implements Comparator<String> {
  private static final Pattern sf_starPattern = Pattern.compile("(.*)(\\d+)(.*)");
  private static final Comparator<String> sf_comparator = new HaplotypeNameComparator();
  private static final List<String> sf_topTerms = ImmutableList.of("Any","All");
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

    Matcher matcher1 = sf_starPattern.matcher(name1);
    Matcher matcher2 = sf_starPattern.matcher(name2);
    if (matcher1.matches() && matcher2.matches()) {
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
    return ObjectUtils.compare(name1, name2);
  }
}
