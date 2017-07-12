package org.pharmgkb.common.util;

import java.net.URL;
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
  public void testWebUrls() {
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

    assertTrue(UrlUtils.isValidWebUrl("http://171.67.192.16"));
    assertTrue(UrlUtils.isValidWebUrl("http://171.67.192.16:8080"));
    assertTrue(UrlUtils.isValidWebUrl("http://172.168.15.1"));
    assertTrue(UrlUtils.isValidWebUrl("http://172.168.33.1"));
    // ipv6
    assertTrue(UrlUtils.isValidWebUrl("http://[2001:db8:a0b:12f0::1]/index.html"));
    assertTrue(UrlUtils.isValidWebUrl("http://[2001:db8:a0b:12f0::1]:80/index.html"));

    // localhost
    assertFalse(UrlUtils.isValidWebUrl("http://localhost"));
    // private ip
    assertFalse(UrlUtils.isValidWebUrl("http://127.0.0.1"));
    assertFalse(UrlUtils.isValidWebUrl("http://10.0.0.1:8080"));
    assertFalse(UrlUtils.isValidWebUrl("http://172.16.1.1"));
    assertFalse(UrlUtils.isValidWebUrl("http://172.22.1.1"));
    assertFalse(UrlUtils.isValidWebUrl("http://172.32.1.1"));
    assertFalse(UrlUtils.isValidWebUrl("http://[fd01:db8:a0b:12f0::1]:80/index.html"));

    assertFalse(UrlUtils.isValidWebUrl("http://"));
    assertFalse(UrlUtils.isValidWebUrl("http://."));
    assertFalse(UrlUtils.isValidWebUrl("http://.com"));
    assertFalse(UrlUtils.isValidWebUrl("https:// "));
    assertFalse(UrlUtils.isValidWebUrl("ftp://"));
    assertFalse(UrlUtils.isValidWebUrl("ftp://::::@example.com"));
    assertFalse(UrlUtils.isValidWebUrl("mailto:foo@bar.com"));
  }


  @Test
  public void testUrls() {
    assertTrue(UrlUtils.isValidUrl("http://www.pharmgkb.org"));
    assertTrue(UrlUtils.isValidUrl("http://www.pharmgkb.org/bar.txt"));
    assertTrue(UrlUtils.isValidUrl("http://www.pharmgkb.org:8080"));
    assertTrue(UrlUtils.isValidUrl("http://www.pharmgkb.org:8080/bar.txt"));
    assertTrue(UrlUtils.isValidUrl("http://hi:there@www.pharmgkb.org/"));

    assertTrue(UrlUtils.isValidUrl("https://www.pharmgkb.org"));
    assertTrue(UrlUtils.isValidUrl("https://www.pharmgkb.org/bar.txt"));
    assertTrue(UrlUtils.isValidUrl("https://www.pharmgkb.org:8080"));
    assertTrue(UrlUtils.isValidUrl("https://www.pharmgkb.org:8080/bar.txt"));
    assertTrue(UrlUtils.isValidUrl("https://hi:there@www.pharmgkb.org/"));

    assertTrue(UrlUtils.isValidUrl("ftp://www.pharmgkb.org"));
    assertTrue(UrlUtils.isValidUrl("ftp://www.pharmgkb.org/bar.txt"));
    assertTrue(UrlUtils.isValidUrl("ftp://hi:there@www.pharmgkb.org:8080"));
    assertTrue(UrlUtils.isValidUrl("ftp://hi:there@www.pharmgkb.org:8080/bar.txt"));

    assertTrue(UrlUtils.isValidUrl("http://171.67.192.16"));
    assertTrue(UrlUtils.isValidUrl("http://171.67.192.16:8080"));
    assertTrue(UrlUtils.isValidUrl("http://172.168.15.1"));
    assertTrue(UrlUtils.isValidUrl("http://172.168.33.1"));
    // ipv6
    assertTrue(UrlUtils.isValidUrl("http://[2001:db8:a0b:12f0::1]/index.html"));
    assertTrue(UrlUtils.isValidUrl("http://[2001:db8:a0b:12f0::1]:80/index.html"));

    // localhost
    assertFalse(UrlUtils.isValidUrl("http://localhost"));
    // private ip
    assertFalse(UrlUtils.isValidUrl("http://127.0.0.1"));
    assertFalse(UrlUtils.isValidUrl("http://10.0.0.1:8080"));
    assertFalse(UrlUtils.isValidUrl("http://172.16.1.1"));
    assertFalse(UrlUtils.isValidUrl("http://172.22.1.1"));
    assertFalse(UrlUtils.isValidUrl("http://172.32.1.1"));
    assertFalse(UrlUtils.isValidUrl("http://[fd01:db8:a0b:12f0::1]:80/index.html"));

    assertFalse(UrlUtils.isValidUrl("http://"));
    assertFalse(UrlUtils.isValidUrl("http://."));
    assertFalse(UrlUtils.isValidUrl("http://.com"));
    assertFalse(UrlUtils.isValidUrl("https:// "));
    assertFalse(UrlUtils.isValidUrl("ftp://"));
    assertFalse(UrlUtils.isValidUrl("ftp://::::@example.com"));
    assertFalse(UrlUtils.isValidUrl("mailto:foo@bar.com"));
  }


  @Test
  public void testLocalAndPrivateUrls() {

    // localhost
    assertTrue(UrlUtils.isValidUrl("http://localhost", true, true, true, false));
    // private ip
    assertTrue(UrlUtils.isValidUrl("http://127.0.0.1", true, true, true, false));
    assertTrue(UrlUtils.isValidUrl("http://10.0.0.1:8080", true, true, true, false));
    assertTrue(UrlUtils.isValidUrl("http://172.16.1.1", true, true, true, false));
    assertTrue(UrlUtils.isValidUrl("http://172.22.1.1", true, true, true, false));
    assertTrue(UrlUtils.isValidUrl("http://172.32.1.1", true, true, true, false));
    assertTrue(UrlUtils.isValidUrl("http://[fd01:db8:a0b:12f0::1]:80/index.html", true, true, true, false));
  }


  @Test
  public void testIsReachableUrl() throws Exception {

    assertTrue(UrlUtils.isReachableUrl(new URL("http://www.pharmgkb.org")));
    assertTrue(UrlUtils.isReachableUrl(new URL("https://www.pharmgkb.org")));
    assertTrue(UrlUtils.isReachableUrl(new URL("ftp://mirrors.sonic.net/cygwin/")));
    assertTrue(UrlUtils.isReachableUrl(new URL("ftp://anonymous:foo%40bar.com@ftp.swfwmd.state.fl.us/pub/README.txt")));
  }


  @Test
  public void testVerify() {

    assertTrue(UrlUtils.isValidUrl("http://www.pharmgkb.org", true, false, false, true));
    assertFalse(UrlUtils.isValidUrl("http://www.pharmgkboo.org", true, false, false, true));
  }
}
