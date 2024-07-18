package org.pharmgkb.common.comparator;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;


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
  public int compare(String o1, String o2) {
    List<Object> parts1 = partsOf(o1);
    List<Object> parts2 = partsOf(o2);
    while (!parts1.isEmpty() && !parts2.isEmpty()) {
      Object part1 = parts1.remove(0);
      Object part2 = parts2.remove(0);
      int cmp;
      if (part1 instanceof Integer && part2 instanceof Integer) {
        cmp = Integer.compare((Integer)part1, (Integer)part2);
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
          parts.add(wasDigit? Integer.valueOf(part) : part);
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
};
