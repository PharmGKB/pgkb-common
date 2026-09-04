package org.pharmgkb.common.util;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;


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
  public static String getFilename(Path file) {
    Preconditions.checkNotNull(file);
    Preconditions.checkArgument(file.getNameCount() > 0, "Path has no filename: '%s'", file);

    return file.getName(file.getNameCount() - 1).toString();
  }


  /**
   * Gets the file extension if one exists.  Does not include ".".
   * Does not validate if {@code file} is a regular file.
   */
  public static @Nullable String getFileExtension(Path file) {
    String filename = getFilename(file);
    int idx = filename.lastIndexOf(".");
    if (idx <= 0) {
      // idx == 0 means the "." is the leading character of a dotfile (e.g. ".gitignore"), not an extension
      return null;
    }
    return StringUtils.stripToNull(filename.substring(idx + 1));
  }

  /**
   * Gets the base name of the file (i.e. file name without the extension).
   * Does not validate if {@code file} is a regular file.
   */
  public static String getBaseFilename(Path file) {
    Preconditions.checkNotNull(file);

    String fileName = getFilename(file);
    int idx = fileName.lastIndexOf(".");
    if (idx <= 0) {
      // idx == 0 means the "." is the leading character of a dotfile (e.g. ".gitignore"), not an extension
      return fileName;
    }
    return fileName.substring(0, idx);
  }


  /**
   * Converts a resource file name into a {@link Path}.
   * <p>
   * If the resource is inside a jar, the jar's {@link java.nio.file.FileSystem} is opened and left registered
   * for the JVM's lifetime (closing it here would invalidate the returned {@code Path}, since this method has
   * no way to know when a caller is done with it). This is bounded by the number of distinct jar files ever
   * accessed this way, not by the number of calls to this method - repeat calls for the same jar reuse its
   * already-open filesystem.
   *
   * @param filename a relative filename from root (e.g. {@code org/pharmgkb/common/file.txt})
   */
  public static Path getPathToResource(String filename) {
    Preconditions.checkNotNull(filename);

    if (filename.startsWith("/")) {
      filename = filename.substring(1);
    }

    URL url = PathUtils.class.getClassLoader().getResource(filename);
    return getPath(url, filename);
  }

  /**
   * Converts a resource file name into a {@link Path}.
   * <p>
   * See {@link #getPathToResource(String)} for a note on jar filesystem lifecycle.
   *
   * @param clz the class the filename is relative to
   * @param filename a filename relative to {@code clz}'s package (e.g. {@code file.txt} for a file alongside
   * {@code clz}), or classpath-root-relative if it starts with "/" (e.g. {@code /org/pharmgkb/common/file.txt}) -
   * see {@link Class#getResource(String)}, which this delegates to
   */
  public static Path getPathToResource(Class clz, String filename) {
    Preconditions.checkNotNull(clz, "clz is null");
    Preconditions.checkNotNull(filename);

    URL url = clz.getResource(filename);
    return getPath(url, filename);
  }


  private static Path getPath(@Nullable URL url, String filename) {
    if (url == null) {
      throw new IllegalArgumentException("No such resource: " + filename);
    }
    try {
      URI uri = url.toURI();
      if (url.getProtocol().equalsIgnoreCase("jar")) {
        try {
          FileSystems.getFileSystem(uri);
        } catch (FileSystemNotFoundException ex) {
          // intentionally not closed - Paths.get(uri) below requires this filesystem to stay registered
          try {
            FileSystems.newFileSystem(uri, Collections.emptyMap());
          } catch (FileSystemAlreadyExistsException ex2) {
            // another thread won the race between getFileSystem() and newFileSystem() above - fine, it's registered
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
