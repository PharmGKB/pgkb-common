/*
 ----- BEGIN LICENSE BLOCK -----
 This Source Code Form is subject to the terms of the Mozilla Public License, v.2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ----- END LICENSE BLOCK -----
 */
package org.pharmgkb.common.util;

import java.nio.file.Path;
import javax.annotation.Nonnull;
import com.google.common.base.Preconditions;


/**
 * Utility methods for working with {@link Path}s.
 *
 * @author Mark Woon
 */
public final class PathUtils {

  /**
   * Private constructor to prevent instantiation of utility class.
   */
  private PathUtils() {
  }


  /**
   * Gets the name of the file.
   * Does not validate if {@code file} is a regular file.
   */
  public static @Nonnull String getFilename(@Nonnull Path file) {
    Preconditions.checkNotNull(file);

    return file.getName(file.getNameCount() - 1).toString();
  }

  /**
   * Gets the base name of the file (i.e. file name without the extension).
   * Does not validate if {@code file} is a regular file.
   */
  public static @Nonnull String getBaseFilename(@Nonnull Path file) {
    Preconditions.checkNotNull(file);

    String fileName = getFilename(file);
    int idx = fileName.indexOf(".");
    if (idx == -1) {
      return fileName;
    }
    return fileName.substring(0, fileName.indexOf("."));
  }
}
