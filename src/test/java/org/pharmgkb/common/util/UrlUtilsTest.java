package org.pharmgkb.common.util;

import net.trajano.commons.testing.UtilityClassTestUtil;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


/**
 * JUnit test for {@link UrlUtils}.
 *
 * @author Mark Woon
 */
public class UrlUtilsTest {

  @Test
  public void testUtilityClass() throws Exception {
    UtilityClassTestUtil.assertUtilityClassWellDefined(PathUtils.class);
  }


  @Test
  public void testUrls() {
    assertTrue(UrlUtils.isValidWebUrl("http://www.pharmgkb.org"));
    assertTrue(UrlUtils.isValidWebUrl("http://www.pharmgkb.org/bar.txt"));
    assertTrue(UrlUtils.isValidWebUrl("http://www.pharmgkb.org:8080"));
    assertTrue(UrlUtils.isValidWebUrl("http://www.pharmgkb.org:8080/bar.txt"));
    assertTrue(UrlUtils.isValidWebUrl("http://hi:there@www.pharmgkb.org/"));

    assertTrue(UrlUtils.isValidWebUrl("https://www.pharmgkb.org"));
    assertTrue(UrlUtils.isValidWebUrl("https://www.pharmgkb.org/bar.txt"));
    assertTrue(UrlUtils.isValidWebUrl("https://www.pharmgkb.org:8080"));
    assertTrue(UrlUtils.isValidWebUrl("https://www.pharmgkb.org:8080/bar.txt"));
    assertTrue(UrlUtils.isValidWebUrl("https://hi:there@www.pharmgkb.org/"));

    assertFalse(UrlUtils.isValidWebUrl("ftp://www.pharmgkb.org"));
    assertFalse(UrlUtils.isValidWebUrl("ftp://www.pharmgkb.org/bar.txt"));
    assertFalse(UrlUtils.isValidWebUrl("ftp://hi:there@www.pharmgkb.org:8080"));
    assertFalse(UrlUtils.isValidWebUrl("ftp://hi:there@www.pharmgkb.org:8080/bar.txt"));

    assertTrue(UrlUtils.isValidWebUrl("http://localhost"));
    assertTrue(UrlUtils.isValidWebUrl("http://127.0.0.1"));
    assertTrue(UrlUtils.isValidWebUrl("http://10.0.0.1:8080"));

    // ipv6
    assertTrue(UrlUtils.isValidWebUrl("http://[2001:db8:a0b:12f0::1]/index.html"));
    assertTrue(UrlUtils.isValidWebUrl("http://[2001:db8:a0b:12f0::1]:80/index.html"));

    assertFalse(UrlUtils.isValidWebUrl("http://"));
    assertFalse(UrlUtils.isValidWebUrl("http://."));
    assertFalse(UrlUtils.isValidWebUrl("https:// "));
  }
}
