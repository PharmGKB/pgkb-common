package org.pharmgkb.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * This is a Junit test for {@link IoUtils}.
 *
 * @author Mark Woon
 */
class IoUtilsTest {

  @Test
  void closeQuietlyClosesCleanly() {
    boolean[] closed = { false };
    IoUtils.closeQuietly(() -> closed[0] = true);
    assertTrue(closed[0]);
  }

  @Test
  void closeQuietlySuppressesExceptionFromClose() {
    boolean[] attempted = { false };
    AutoCloseable throwing = () -> {
      attempted[0] = true;
      throw new RuntimeException("expected");
    };
    assertDoesNotThrow(() -> IoUtils.closeQuietly(throwing));
    assertTrue(attempted[0]);
  }

  @Test
  void closeQuietlyAcceptsNull() {
    assertDoesNotThrow(() -> IoUtils.closeQuietly(null));
  }
}
