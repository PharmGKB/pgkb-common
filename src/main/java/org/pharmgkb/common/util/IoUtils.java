package org.pharmgkb.common.util;

import java.lang.invoke.MethodHandles;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Utility methods for working with I/O.
 *
 * @author Mark Woon
 */
public class IoUtils {
  private static final Logger sf_logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());


  /**
   * Private constructor to prevent instantiation of utility class.
   */
  private IoUtils() {
  }


  public static void closeQuietly(@Nullable AutoCloseable closeable) {
    if (closeable != null) {
      try {
        closeable.close();
      } catch (Exception ex) {
        sf_logger.warn("Error closing {}", closeable.getClass(), ex);
      }
    }
  }
}
