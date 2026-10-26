package org.pharmgkb.common.comparator;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import org.jspecify.annotations.Nullable;


/**
 * Comparator to "naturally" sort strings by sorting numerically when numbers are embedded in comparable places within
 * the Strings. For example, "*2" will be sorted before "*10"
 * <p>
 * This was inspired by <a href="https://stackoverflow.com/a/27170019">StackOverflow</a>
 */
public class NaturalStringComparator implements Comparator<String> {
  private static final Comparator<String> instance = new NaturalStringComparator();

  public static Comparator<String> getComparator() {
    return instance;
  }

  @Override
  public int compare(@Nullable String o1, @Nullable String o2) {

    //noinspection StringEquality
    if (o1 == o2) {
      return 0;
    }
    if (o1 == null) {
      return -1;
    }
    if (o2 == null) {
      return 1;
    }
    if (o1.equals(o2)) {
      return 0;
    }

    List<Object> parts1 = partsOf(o1);
    List<Object> parts2 = partsOf(o2);
    while (!parts1.isEmpty() && !parts2.isEmpty()) {
      Object part1 = parts1.remove(0);
      Object part2 = parts2.remove(0);
      int cmp;
      if (part1 instanceof NumericPart && part2 instanceof NumericPart) {
        NumericPart num1 = (NumericPart)part1;
        NumericPart num2 = (NumericPart)part2;
        cmp = Long.compare(num1.value(), num2.value());
        if (cmp == 0) {
          // tie-break on raw digits so e.g. "*01" doesn't compare equal to "*1"
          cmp = num1.raw().compareTo(num2.raw());
        }
      } else if (part1 instanceof String && part2 instanceof String) {
        cmp = ((String) part1).compareTo((String) part2);
      } else {
        cmp = part1 instanceof String ? 1 : -1; // XXXa > XXX1
      }
      if (cmp != 0) {
        return cmp;
      }
    }
    if (parts1.isEmpty() && parts2.isEmpty()) {
      return 0;
    }
    return parts1.isEmpty() ? -1 : 1;
  }

  private List<Object> partsOf(String s) {
    List<Object> parts = new LinkedList<>();
    int pos0 = 0;
    int pos = 0;
    boolean wasDigit = false;
    while (true) {
      if (pos >= s.length()
          || Character.isDigit(s.charAt(pos)) != wasDigit) {
        if (pos > pos0) {
          String part = s.substring(pos0, pos);
          if (wasDigit) {
            try {
              parts.add(new NumericPart(Long.parseLong(part), part));
            } catch (NumberFormatException ex) {
              throw new IllegalArgumentException(
                  "Numeric portion of '" + s + "' is too large to compare (max " + Long.MAX_VALUE + ")", ex);
            }
          } else {
            parts.add(part);
          }
          pos0 = pos;
        }
        if (pos >= s.length()) {
          break;
        }
        wasDigit = !wasDigit;
      }
      ++pos;
    }
    return parts;
  }

  /**
   * Holder for a parsed numeric substring, retaining both the parsed value (for numeric ordering) and the raw
   * digit substring (as a tie-break when the parsed values are equal, e.g. "01" vs. "1").
   */
  private record NumericPart(long value, String raw) {
  }
};
