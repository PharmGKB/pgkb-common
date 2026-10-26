package org.pharmgkb.common.util;

import org.jspecify.annotations.Nullable;


/**
 * All enums should implement this interface.
 * Implementors should also make available all accessor methods from {@link ExtendedEnumHelper} as static methods.
 * <p>
 * The goal is to be able to specify an ID, short name and a display name for enums that do not change if the enum
 * itself gets moved/renamed, and to allow reverse lookups.
 *
 * @author Mark Woon
 */
public interface ExtendedEnum {


  /**
   * Gets the ID of this enum.
   */
  int getId();


  /**
   * Gets the short name of this enum.
   */
  String getShortName();


  /**
   * Gets the display name of this enum. Defaults to {@link #getShortName()}.
   * <p>
   * Never {@code null} for an enum registered via {@link ExtendedEnumHelper#register(Class)}, which validates it;
   * an enum registered by calling {@code add()} directly validates the name it passes instead.
   */
  default String getDisplayName() {
    return getShortName();
  }


  /**
   * Gets additional names this constant should be reachable by via {@link ExtendedEnumHelper#lookupByName}, beyond
   * its short name and display name. Only used by {@link ExtendedEnumHelper#register(Class)}; with
   * {@code add()}, pass them as its {@code additionalNames} instead.
   */
  default String @Nullable [] getAdditionalNames() {
    return null;
  }
}
