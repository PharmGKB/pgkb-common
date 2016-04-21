/*
 ----- BEGIN LICENSE BLOCK -----
 This Source Code Form is subject to the terms of the Mozilla Public License, v.2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ----- END LICENSE BLOCK -----
 */
package org.pharmgkb.common.comparator;

import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.ObjectUtils;


/**
 * Comparator for chromosomal positions (in the format chrX:1234).
 *
 * @author Mark Woon
 */
public class ChromosomePositionComparator implements Comparator<String> {
  public static final Comparator<String> sf_comparator = new ChromosomePositionComparator();
  private static final Pattern sf_pattern = Pattern.compile("(?:chr)?(\\w{1,2}):(\\d+)");

  /**
   * Gets an instance of this comparator.
   *
   * @return an instance of this comparator
   */
  public static Comparator<String> getComparator() {
    return sf_comparator;
  }


  @Override
  public int compare(String o1, String o2) {

    //noinspection StringEquality
    if (o1 == o2) {
      return 0;
    }
    if (o1 == null) {
      return -1;
    } else if (o2 == null) {
      return 1;
    }

    Matcher m1 = sf_pattern.matcher(o1);
    if (!m1.matches()) {
      throw new IllegalArgumentException("'" + o1 + "' is not in the expected chromosomal position format");
    }
    Matcher m2 = sf_pattern.matcher(o2);
    if (!m2.matches()) {
      throw new IllegalArgumentException("'" + o2 + "' is not in the expected chromosomal position format");
    }

    int rez = ChromosomeNameComparator.getComparator().compare(m1.group(1), m2.group(1));
    if (rez != 0) {
      return rez;
    }
    Integer n1 = Integer.parseInt(m1.group(2));
    Integer n2 = Integer.valueOf(m2.group(2));
    return ObjectUtils.compare(n1, n2);
  }
}
