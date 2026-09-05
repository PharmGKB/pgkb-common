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
   * Gets the display name of this enum. Never {@code null} for an implementor registered via
   * {@link ExtendedEnumHelper#register(Class)} - that path passes this method's own return value as
   * {@code add(ExtendedEnum, int, String, String, String...)}'s {@code displayName} argument, which enforces
   * non-null (and non-blank) at registration time. An implementor using {@code add()} directly (the original,
   * manually-called API) isn't held to this, since that path validates its own {@code displayName} argument,
   * not this method's return value.
   * <p>
   * The default implementation falls back to {@link #getShortName()} - implementors with no distinct display
   * name can rely on this default instead of overriding {@code getDisplayName()} themselves. Implementors that
   * already override {@code getDisplayName()} directly (the original way to implement this interface) are
   * unaffected - their own override always takes precedence over this default.
   */
  default String getDisplayName() {
    return getShortName();
  }


  /**
   * Gets additional names this constant should be reachable by via {@link ExtendedEnumHelper#lookupByName}, beyond
   * its short name and display name. Only consulted by {@link ExtendedEnumHelper#register(Class)} - see
   * {@link ExtendedEnumHelper#add(ExtendedEnum, int, String, String, String...)}'s {@code additionalNames}
   * parameter for the original, manually-called equivalent.
   */
  default String @Nullable [] getAdditionalNames() {
    return null;
  }
}
