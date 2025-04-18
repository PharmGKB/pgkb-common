package org.pharmgkb.common.util;

import java.util.Collection;
import org.jspecify.annotations.Nullable;


/**
 * ExtendedEnum for use in tests.
 *
 * @author Mark Woon
 */
enum Hero implements ExtendedEnum {
  WonderWoman(1, "Diana", "Diana Prince"),
  Superman(2, "Clark", "Clark Kent"),
  Batman(3, "Bruce", "Bruce Wayne");

  @SuppressWarnings("NotNullFieldNotInitialized")
  private static ExtendedEnumHelper<Hero> s_extendedEnumHelper;
  private final int m_id;
  private final String m_shortName;
  private final String m_displayName;

  Hero(int id, String shortName, String displayName) {
    m_id = id;
    m_shortName = shortName;
    m_displayName = displayName;
    init();
  }


  //-- BEGIN ExtendedEnum methods --//
  private synchronized void init() {
    //noinspection ConstantValue
    if (s_extendedEnumHelper == null) {
      s_extendedEnumHelper = new ExtendedEnumHelper<>(getClass());
    }
    s_extendedEnumHelper.add(this, m_id, m_shortName, m_displayName);
  }

  @Override
  public int getId() {
    return m_id;
  }

  @Override
  public String getShortName() {
    return m_shortName;
  }

  @Override
  public String getDisplayName() {
    return m_displayName;
  }
  //-- END ExtendedEnum methods --//

  //-- BEGIN ExtendedEnum statics --//
  public static @Nullable Hero lookupById(int id) {
    return s_extendedEnumHelper.lookupById(id);
  }

  public static @Nullable Hero lookupByName(String text) {
    return s_extendedEnumHelper.lookupByName(text);
  }

  public static Collection<Hero> getAllSortedById() {
    return s_extendedEnumHelper.getAllSortedById();
  }

  public static Collection<Hero> getAllSortedByName() {
    return s_extendedEnumHelper.getAllSortedByName();
  }
  //-- END ExtendedEnum statics --//
}
