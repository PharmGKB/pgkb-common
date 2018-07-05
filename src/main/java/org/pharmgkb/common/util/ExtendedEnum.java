/*
 ----- BEGIN LICENSE BLOCK -----
 This Source Code Form is subject to the terms of the Mozilla Public License, v.2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ----- END LICENSE BLOCK -----
 */
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
