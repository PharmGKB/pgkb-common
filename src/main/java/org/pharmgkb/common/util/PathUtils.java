/*
 ----- BEGIN LICENSE BLOCK -----
 This Source Code Form is subject to the terms of the Mozilla Public License, v.2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ----- END LICENSE BLOCK -----
 */
package org.pharmgkb.common.util;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
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
    int idx = fileName.lastIndexOf(".");
    if (idx == -1) {
      return fileName;
    }
    return fileName.substring(0, idx);
  }


  /**
   * Converts a resource file name into a {@link Path}.
   *
   * @param filename a relative filename (e.g. {@code org/pharmgkb/common/file.txt})
   */
  public static @Nonnull Path getPathToResource(@Nonnull String filename) {

    Preconditions.checkNotNull(filename);
    URL url = PathUtils.class.getClassLoader().getResource(filename);
    if (url == null) {
      throw new IllegalArgumentException("No such resource: " + filename);
    }
    try {
      URI uri = url.toURI();
      if (url.getProtocol().equalsIgnoreCase("jar")) {
        try {
          FileSystems.getFileSystem(uri);
        } catch (FileSystemNotFoundException ex) {
          try {
            FileSystems.newFileSystem(uri, Collections.emptyMap());
          } catch (IOException ex2) {
            throw new IllegalStateException("Unable to create zip/jar filesystem", ex2);
          }
        }
      }
      return Paths.get(uri);
    } catch (URISyntaxException ex) {
      // should never happen
      throw new IllegalStateException("Filename '" + filename + "' translated to invalid URI (" + url + ")", ex);
    }
  }
}
