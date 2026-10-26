package org.pharmgkb.common.util;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
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
    // localhost check must be case-insensitive
    assertFalse(UrlUtils.isValidWebUrl("http://LOCALHOST"));
    assertFalse(UrlUtils.isValidWebUrl("http://LocalHost"));
    assertFalse(UrlUtils.isValidWebUrl("http://LOCALHOST.evil.com"));
    // RFC 6761 6.3 reserves the *.localhost SUFFIX form (e.g. "foo.localhost") as resolving to loopback -
    // this machine's own resolver confirms it (InetAddress.getByName("foo.localhost").isLoopbackAddress()
    // is true) - so this must be rejected the same as bare "localhost", not just the "localhost." PREFIX
    // form (which isn't actually reserved by the RFC, but was already blocked defensively above)
    assertFalse(UrlUtils.isValidWebUrl("http://foo.localhost"));
    assertFalse(UrlUtils.isValidWebUrl("http://FOO.LOCALHOST"));
    assertFalse(UrlUtils.isValidWebUrl("http://a.b.localhost"));
    // the DNS "fully-qualified" trailing-dot form (RFC 1035) names the exact same host, and this
    // machine's resolver still sends it to loopback - it must not bypass the checks above
    assertFalse(UrlUtils.isValidWebUrl("http://localhost."));
    assertFalse(UrlUtils.isValidWebUrl("http://foo.localhost."));
    assertFalse(UrlUtils.isValidWebUrl("http://FOO.LOCALHOST."));
    assertFalse(UrlUtils.isValidWebUrl("http://a.b.localhost."));
    // "ip6-localhost"/"ip6-loopback" are a second, separate loopback alias (not a form of "localhost") that
    // every Debian/Ubuntu/WSL machine's default /etc/hosts maps to "::1" - a pure string check, not a DNS
    // resolution, so this doesn't depend on this test machine's own /etc/hosts contents
    assertFalse(UrlUtils.isValidWebUrl("http://ip6-localhost"));
    assertFalse(UrlUtils.isValidWebUrl("http://IP6-LOCALHOST"));
    assertFalse(UrlUtils.isValidWebUrl("http://ip6-loopback"));
    assertFalse(UrlUtils.isValidWebUrl("http://IP6-LOOPBACK"));
    // private ip
    assertFalse(UrlUtils.isValidWebUrl("http://127.0.0.1"));
    assertFalse(UrlUtils.isValidWebUrl("http://10.0.0.1:8080"));
    assertFalse(UrlUtils.isValidWebUrl("http://172.16.1.1"));
    assertFalse(UrlUtils.isValidWebUrl("http://172.22.1.1"));
    assertFalse(UrlUtils.isValidWebUrl("http://172.31.1.1"));
    // 172.32.0.0/16 is public, not part of the 172.16.0.0/12 private range
    assertTrue(UrlUtils.isValidWebUrl("http://172.32.1.1"));
    // link-local (169.254.0.0/16) - was otherwise unpinned by any assertion despite being the single most-
    // targeted SSRF destination (169.254.169.254 is the AWS/GCP/Azure cloud metadata endpoint)
    assertFalse(UrlUtils.isValidWebUrl("http://169.254.169.254/latest/meta-data/"));
    assertFalse(UrlUtils.isValidWebUrl("http://[fe80::1]/"));
    assertFalse(UrlUtils.isValidWebUrl("http://[fd01:db8:a0b:12f0::1]:80/index.html"));
    // ipv6 unique local range is fc00::/7, not just the "fd" half
    assertFalse(UrlUtils.isValidWebUrl("http://[fc00::1]/"));
    // ipv6 loopback
    assertFalse(UrlUtils.isValidWebUrl("http://[::1]/"));
    // ipv4-mapped ipv6 loopback
    assertFalse(UrlUtils.isValidWebUrl("http://[::ffff:127.0.0.1]/"));
    // wildcard/unspecified address - many network stacks route this to localhost
    assertFalse(UrlUtils.isValidWebUrl("http://0.0.0.0/"));
    assertFalse(UrlUtils.isValidWebUrl("http://[::]/"));
    // zero-padded octets - Guava's IP parser rejects these outright (avoiding octal ambiguity), which
    // previously caused isValid() to skip private-IP detection entirely and treat the host as valid; but
    // InetAddress.getByName() (what actually resolves the host later) parses each octet as plain decimal
    // regardless of padding, so these must still be caught as private/loopback
    assertFalse(UrlUtils.isValidWebUrl("http://192.168.001.001/"));
    assertFalse(UrlUtils.isValidWebUrl("http://010.000.000.001/"));
    assertFalse(UrlUtils.isValidWebUrl("http://172.016.0.1/"));
    assertFalse(UrlUtils.isValidWebUrl("http://172.16.0.01/"));
    // the real resolver strips ANY number of leading zeros, not just up to 3 digits total - a 4+ digit
    // octet like "0192" or "00192" must be caught too, not just the common 3-digit-with-one-leading-zero case
    assertFalse(UrlUtils.isValidWebUrl("http://0192.168.1.1/"));
    assertFalse(UrlUtils.isValidWebUrl("http://00192.168.1.1/"));
    assertFalse(UrlUtils.isValidWebUrl("http://192.0168.1.1/"));
    assertFalse(UrlUtils.isValidWebUrl("http://192.168.0001.1/"));
    // a genuinely public IP with a zero-padded octet must still be allowed through
    assertTrue(UrlUtils.isValidWebUrl("http://008.008.008.008/"));

    assertFalse(UrlUtils.isValidWebUrl("http://"));
    assertFalse(UrlUtils.isValidWebUrl("http://."));
    assertFalse(UrlUtils.isValidWebUrl("http://.com"));
    assertFalse(UrlUtils.isValidWebUrl("https:// "));
    assertFalse(UrlUtils.isValidWebUrl("ftp://"));
    assertFalse(UrlUtils.isValidWebUrl("ftp://::::@example.com"));
    assertFalse(UrlUtils.isValidWebUrl("mailto:foo@bar.com"));
  }

  @Test
  void testIsValidTreatsUnescapedSpaceInPathAsValid() {
    // a literal space is encoded as %20 rather than rejected, as java.net.URL(String) tolerated it
    assertTrue(UrlUtils.isValidWebUrl("http://www.clinpgx.org/a b"));
    // leading/trailing whitespace around the WHOLE url is unrelated (already covered by
    // StreamUtilsTest.testCopyUrlToFileTakesHttpBranchWithLeadingWhitespace) - stripped away entirely, not
    // encoded as content
    assertTrue(UrlUtils.isValidWebUrl(" http://www.clinpgx.org/a b "));
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
    // a percent-encoded ':' is part of the user or password, not a separator (RFC 3986 3.2.1)
    assertTrue(UrlUtils.isValid("ftp://user:pa%3Ass@www.clinpgx.org/bar.txt"));
    assertTrue(UrlUtils.isValid("http://a%3Ab%3Ac@www.clinpgx.org/"));

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
    assertFalse(UrlUtils.isValid("http://172.31.1.1"));
    // 172.32.0.0/16 is public, not part of the 172.16.0.0/12 private range
    assertTrue(UrlUtils.isValid("http://172.32.1.1"));
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
    assertTrue(UrlUtils.isValid("http://localhost", true, true, true));
    // private ip
    assertTrue(UrlUtils.isValid("http://127.0.0.1", true, true, true));
    assertTrue(UrlUtils.isValid("http://10.0.0.1:8080", true, true, true));
    assertTrue(UrlUtils.isValid("http://172.16.1.1", true, true, true));
    assertTrue(UrlUtils.isValid("http://172.22.1.1", true, true, true));
    assertTrue(UrlUtils.isValid("http://172.32.1.1", true, true, true));
    assertTrue(UrlUtils.isValid("http://[fd01:db8:a0b:12f0::1]:80/index.html", true, true, true));

    // isValid()'s "uri.getHost() == null" early return is only independently observable (rather than
    // incidentally caught by the outer try/catch via an NPE further down) when allowLocalhost AND
    // allowPrivateIp are both true, as here - a hostless URL must still be rejected
    assertFalse(UrlUtils.isValid("http:///foo", true, true, true));
  }


  private static long countOpenFds(Path proc) throws IOException {
    try (var stream = Files.list(proc)) {
      return stream.count();
    }
  }


}
