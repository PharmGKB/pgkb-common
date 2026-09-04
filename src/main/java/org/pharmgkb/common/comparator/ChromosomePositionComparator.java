package org.pharmgkb.common.comparator;

import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.ObjectUtils;
import org.jspecify.annotations.Nullable;


/**
 * Comparator for chromosomal positions (in the format chrX:1234).
 *
 * @author Mark Woon
 */
public class ChromosomePositionComparator implements Comparator<String> {
  private static final Comparator<String> sf_comparator = new ChromosomePositionComparator();
  private static final Pattern sf_pattern = Pattern.compile("(?:chr)?(\\w+):(\\d+)", Pattern.CASE_INSENSITIVE);

  /**
   * Gets an instance of this comparator.
   *
   * @return an instance of this comparator
   */
  public static Comparator<String> getComparator() {
    return sf_comparator;
  }


  @Override
  public int compare(@Nullable String o1, @Nullable String o2) {

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

    int rez;
    try {
      rez = ChromosomeNameComparator.getComparator().compare(m1.group(1), m2.group(1));
    } catch (IllegalArgumentException ex) {
      // ChromosomeNameComparator only sees the chr-stripped number, not the full "chr:position" string, so
      // its own message can't name which input caused the failure - rethrow with both full positions attached
      throw new IllegalArgumentException(
          "Error comparing chromosome positions '" + o1 + "' and '" + o2 + "': " + ex.getMessage(), ex);
    }
    if (rez != 0) {
      return rez;
    }
    Long n1 = parsePosition(o1, m1.group(2));
    Long n2 = parsePosition(o2, m2.group(2));
    rez = ObjectUtils.compare(n1, n2);
    if (rez == 0) {
      // tie-break on raw digits so e.g. "01" doesn't compare equal to "1"
      rez = m1.group(2).compareTo(m2.group(2));
    }
    return rez;
  }

  private static long parsePosition(String original, String position) {
    try {
      return Long.parseLong(position);
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(
          "Position in '" + original + "' is too large to compare (max " + Long.MAX_VALUE + ")", ex);
    }
  }
}
