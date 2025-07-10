package org.pharmgkb.common.util;

import java.net.URL;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * JUnit test for {@link UrlUtils}.
 *
 * @author Mark Woon
 */
class UrlUtilsTest {


  @Test
  void testWebUrls() {
    assertTrue(UrlUtils.isValidWebUrl("http://www.clinpgx.org"));
    assertTrue(UrlUtils.isValidWebUrl("http://www.clinpgx.org/bar.txt"));
    assertTrue(UrlUtils.isValidWebUrl("http://www.clinpgx.org:8080"));
    assertTrue(UrlUtils.isValidWebUrl("http://www.clinpgx.org:8080/bar.txt"));
    assertTrue(UrlUtils.isValidWebUrl("http://hi:there@www.clinpgx.org/"));

    assertTrue(UrlUtils.isValidWebUrl("https://www.clinpgx.org"));
    assertTrue(UrlUtils.isValidWebUrl("https://www.clinpgx.org/bar.txt"));
    assertTrue(UrlUtils.isValidWebUrl("https://www.clinpgx.org:8080"));
    assertTrue(UrlUtils.isValidWebUrl("https://www.clinpgx.org:8080/bar.txt"));
    assertTrue(UrlUtils.isValidWebUrl("https://hi:there@www.clinpgx.org/"));

    assertFalse(UrlUtils.isValidWebUrl("ftp://www.clinpgx.org"));
    assertFalse(UrlUtils.isValidWebUrl("ftp://www.clinpgx.org/bar.txt"));
    assertFalse(UrlUtils.isValidWebUrl("ftp://hi:there@www.clinpgx.org:8080"));
    assertFalse(UrlUtils.isValidWebUrl("ftp://hi:there@www.clinpgx.org:8080/bar.txt"));

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
  void testUrls() {
    assertTrue(UrlUtils.isValid("http://www.clinpgx.org"));
    assertTrue(UrlUtils.isValid("http://www.clinpgx.org/bar.txt"));
    assertTrue(UrlUtils.isValid("http://www.clinpgx.org:8080"));
    assertTrue(UrlUtils.isValid("http://www.clinpgx.org:8080/bar.txt"));
    assertTrue(UrlUtils.isValid("http://hi:there@www.clinpgx.org/"));

    assertTrue(UrlUtils.isValid("https://www.clinpgx.org"));
    assertTrue(UrlUtils.isValid("https://www.clinpgx.org/bar.txt"));
    assertTrue(UrlUtils.isValid("https://www.clinpgx.org:8080"));
    assertTrue(UrlUtils.isValid("https://www.clinpgx.org:8080/bar.txt"));
    assertTrue(UrlUtils.isValid("https://hi:there@www.clinpgx.org/"));

    assertTrue(UrlUtils.isValid("ftp://www.clinpgx.org"));
    assertTrue(UrlUtils.isValid("ftp://www.clinpgx.org/bar.txt"));
    assertTrue(UrlUtils.isValid("ftp://hi:there@www.clinpgx.org:8080"));
    assertTrue(UrlUtils.isValid("ftp://hi:there@www.clinpgx.org:8080/bar.txt"));

    assertTrue(UrlUtils.isValid("http://171.67.192.16"));
    assertTrue(UrlUtils.isValid("http://171.67.192.16:8080"));
    assertTrue(UrlUtils.isValid("http://172.168.15.1"));
    assertTrue(UrlUtils.isValid("http://172.168.33.1"));
    // ipv6
    assertTrue(UrlUtils.isValid("http://[2001:db8:a0b:12f0::1]/index.html"));
    assertTrue(UrlUtils.isValid("http://[2001:db8:a0b:12f0::1]:80/index.html"));

    // localhost
    assertFalse(UrlUtils.isValid("http://localhost"));
    // private ip
    assertFalse(UrlUtils.isValid("http://127.0.0.1"));
    assertFalse(UrlUtils.isValid("http://10.0.0.1:8080"));
    assertFalse(UrlUtils.isValid("http://172.16.1.1"));
    assertFalse(UrlUtils.isValid("http://172.22.1.1"));
    assertFalse(UrlUtils.isValid("http://172.32.1.1"));
    assertFalse(UrlUtils.isValid("http://[fd01:db8:a0b:12f0::1]:80/index.html"));

    assertFalse(UrlUtils.isValid("http://"));
    assertFalse(UrlUtils.isValid("http://."));
    assertFalse(UrlUtils.isValid("http://.com"));
    assertFalse(UrlUtils.isValid("https:// "));
    assertFalse(UrlUtils.isValid("ftp://"));
    assertFalse(UrlUtils.isValid("ftp://::::@example.com"));
    assertFalse(UrlUtils.isValid("mailto:foo@bar.com"));
  }


  @Test
  void testLocalAndPrivateUrls() {

    // localhost
    assertTrue(UrlUtils.isValid("http://localhost", true, true, true, false));
    // private ip
    assertTrue(UrlUtils.isValid("http://127.0.0.1", true, true, true, false));
    assertTrue(UrlUtils.isValid("http://10.0.0.1:8080", true, true, true, false));
    assertTrue(UrlUtils.isValid("http://172.16.1.1", true, true, true, false));
    assertTrue(UrlUtils.isValid("http://172.22.1.1", true, true, true, false));
    assertTrue(UrlUtils.isValid("http://172.32.1.1", true, true, true, false));
    assertTrue(UrlUtils.isValid("http://[fd01:db8:a0b:12f0::1]:80/index.html", true, true, true, false));
  }


  @Test
  void testIsReachableUrl() throws Exception {

    assertTrue(UrlUtils.isReachable(new URL("http://www.clinpgx.org")));
    assertTrue(UrlUtils.isReachable(new URL("https://www.clinpgx.org")));
    assertTrue(UrlUtils.isReachable(new URL("ftp://mirrors.sonic.net/cygwin/")));
    assertTrue(UrlUtils.isReachable(new URL("ftp://anonymous:foo%40bar.com@ftp.swfwmd.state.fl.us/pub/README.txt")));
  }


  @Test
  void testVerify() {

    assertTrue(UrlUtils.isValid("http://www.clinpgx.org", true, false, false, true));
    assertFalse(UrlUtils.isValid("http://www.clinpgxoops.org", true, false, false, true));
  }
}
