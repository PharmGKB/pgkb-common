package org.pharmgkb.common.util;

/**
 * This interface should be implemented by all enums.  Implementors should also make available
 * all accessor methods from {@link ExtendedEnumHelper} as static methods.
 * <p />
 * The goal is to be able to specify an Id, short name and a display name for enums that do not change if the enum
 * itself gets moved/renamed, and to allow reverse lookups.
 *
 * @author Mark Woon
 */
public interface ExtendedEnum {


  /**
   * Gets the Id of this enum.
   */
  int getId();


  /**
   * Gets the short name of this enum.
   */
  String getShortName();


  /**
   * Gets the display name of this enum.  Will return short name if no display name is defined.
   */
  String getDisplayName();
}
