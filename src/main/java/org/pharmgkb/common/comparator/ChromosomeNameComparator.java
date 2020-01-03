package org.pharmgkb.common.comparator;

import java.util.Comparator;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;


/**
 * Comparator for chromosome names.  This expects the chromosome name to either be numeric, or start with "chr".
 *
 * @author Mark Woon
 */
public class ChromosomeNameComparator implements Comparator<String> {
  private static final Comparator<String> sf_comparator = new ChromosomeNameComparator();

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

    if (name1.startsWith("chr")) {
      name1 = name1.substring(3);
    }
    boolean isName1Numeric = StringUtils.isNumeric(name1);
    if (name2.startsWith("chr")) {
      name2 = name2.substring(3);
    }
    boolean isName2Numeric = StringUtils.isNumeric(name2);

    // assume non-numeric is either X or Y
    if (isName1Numeric) {
      if (isName2Numeric) {
        return ObjectUtils.compare(Integer.parseInt(name1), Integer.parseInt(name2));
      } else {
        return -1;
      }
    } else {
      if (isName2Numeric) {
        return 1;
      } else {
        return ObjectUtils.compare(name1, name2);
      }
    }
  }
}
