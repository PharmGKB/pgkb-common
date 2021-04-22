package org.pharmgkb.common.util;

import java.io.StringReader;
import org.junit.jupiter.api.Test;


/**
 * This is a Junit test for {@link IoUtils}.
 *
 * @author Mark Woon
 */
class IoUtilsTest {

  @Test
  void closeQuietly() {

    // close cleanly
    IoUtils.closeQuietly(new StringReader("all ok"));


    // close with exception
    StringReader reader = new StringReader("not ok") {
      @Override
      public void close() {
        throw new RuntimeException("expected");
      }
    };
    IoUtils.closeQuietly(reader);
  }
}
