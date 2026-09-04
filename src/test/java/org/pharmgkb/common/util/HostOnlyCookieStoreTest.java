package org.pharmgkb.common.util;

import java.net.CookieManager;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * @author Mark Woon
 */
class HostOnlyCookieStoreTest {

  private static boolean hasNamedCookie(java.util.List<HttpCookie> cookies, String name) {
    return cookies.stream().anyMatch(c -> name.equals(c.getName()));
  }


  @Test
  void testHostOnlyCookiesFromDifferentHostsDoNotCollide() throws java.net.URISyntaxException {
    // round 32 finding 1: java.net.HttpCookie.equals() considers only name/domain/path - never the host a
    // cookie was set from - so two DIFFERENT hosts' host-only cookies (domain == null, StreamUtils's own
    // mechanism for storing a truly host-only cookie - see shouldAcceptCookie()) that share a name and path
    // are equals()-equal to each other. java.net.InMemoryCookieStore.add() unconditionally does
    // "cookieJar.remove(cookie)" before adding/re-adding, so adding host B's cookie silently evicts host A's
    // from the underlying store's own cookieJar - and if host B's cookie is later cleared (an ordinary
    // Max-Age=0 logout), host A's is destroyed along with it, even though the two hosts share nothing.
    // Verified via a real CookieManager/InMemoryCookieStore round-trip before this fix existed.
    CookieStore delegate = new CookieManager().getCookieStore();
    HostOnlyCookieStore store = new HostOnlyCookieStore(delegate);

    HttpCookie cookieA = new HttpCookie("sid", "FROM_DATA");
    cookieA.setPath("/");
    URI dataUri = new URI("http://data.example.com/");
    store.add(dataUri, cookieA);

    HttpCookie cookieB = new HttpCookie("sid", "FROM_AUTH");
    cookieB.setPath("/");
    URI authUri = new URI("http://auth.example.com/");
    store.add(authUri, cookieB);

    assertTrue(hasNamedCookie(store.get(dataUri), "sid"), "host A's cookie must survive host B's add");
    assertEquals("FROM_DATA", store.get(dataUri).get(0).getValue());
    assertTrue(hasNamedCookie(store.get(authUri), "sid"));
    assertEquals("FROM_AUTH", store.get(authUri).get(0).getValue());

    // host B clears its own cookie (an ordinary Max-Age=0 logout) - host A's must be unaffected
    HttpCookie clearB = new HttpCookie("sid", "");
    clearB.setPath("/");
    clearB.setMaxAge(0);
    store.add(authUri, clearB);

    assertTrue(hasNamedCookie(store.get(dataUri), "sid"), "host A's cookie must survive host B's own clear");
    assertFalse(hasNamedCookie(store.get(authUri), "sid"), "host B's own cookie must actually be cleared");
  }


  @Test
  void testHostOnlyCookieFromSameHostIsOverwritten() throws java.net.URISyntaxException {
    // within a SINGLE host, the usual overwrite-by-(name,domain,path) semantics must still apply - this
    // decorator only needs to stop DIFFERENT hosts from colliding, not disable same-host de-duplication
    CookieStore delegate = new CookieManager().getCookieStore();
    HostOnlyCookieStore store = new HostOnlyCookieStore(delegate);
    URI uri = new URI("http://example.com/");

    HttpCookie first = new HttpCookie("sid", "FIRST");
    first.setPath("/");
    store.add(uri, first);
    HttpCookie second = new HttpCookie("sid", "SECOND");
    second.setPath("/");
    store.add(uri, second);

    assertEquals(1, store.get(uri).size());
    assertEquals("SECOND", store.get(uri).get(0).getValue());
  }


  @Test
  void testHostOnlyCookieWithZeroMaxAgeIsNeverStored() throws java.net.URISyntaxException {
    // matches java.net.InMemoryCookieStore.add()'s own "if (cookie.getMaxAge() != 0)" guard (JDK 21 source) -
    // a fresh cookie that already carries Max-Age=0 must never be stored at all, not stored-then-immediately-
    // expired. Asserting only get()/getCookies() (round 33 finding 5, mutation-verified) is a PROVABLE
    // EQUIVALENT MUTANT of removing this guard entirely: both of those already filter out an expired cookie
    // (HttpCookie.hasExpired() is true for maxAge == 0) regardless of whether it was ever added in the first
    // place. getURIs(), called BEFORE any get()/getCookies() call has a chance to trigger that same expiry-
    // based pruning, is what actually distinguishes "never stored" (host never gets an entry at all, pruned
    // immediately by add() itself - round 33 finding 4) from "stored, then found expired later"
    CookieStore delegate = new CookieManager().getCookieStore();
    HostOnlyCookieStore store = new HostOnlyCookieStore(delegate);
    URI uri = new URI("http://example.com/");

    HttpCookie cookie = new HttpCookie("sid", "SECRET");
    cookie.setPath("/");
    cookie.setMaxAge(0);
    store.add(uri, cookie);

    assertTrue(store.getURIs().isEmpty(), "a Max-Age=0 cookie must never create a getURIs() entry at all");
    assertFalse(hasNamedCookie(store.get(uri), "sid"));
    assertFalse(hasNamedCookie(store.getCookies(), "sid"));
  }


  @Test
  void testHostOnlyCookieRespectsSecureFlag() throws java.net.URISyntaxException {
    CookieStore delegate = new CookieManager().getCookieStore();
    HostOnlyCookieStore store = new HostOnlyCookieStore(delegate);
    URI httpUri = new URI("http://example.com/");
    URI httpsUri = new URI("https://example.com/");

    HttpCookie secureCookie = new HttpCookie("sid", "SECRET");
    secureCookie.setPath("/");
    secureCookie.setSecure(true);
    store.add(httpsUri, secureCookie);

    assertTrue(hasNamedCookie(store.get(httpsUri), "sid"));
    assertFalse(hasNamedCookie(store.get(httpUri), "sid"), "a Secure cookie must not be sent over plain http");
  }


  @Test
  void testHostOnlyCookieExpiryIsPrunedFromGet() throws java.net.URISyntaxException, InterruptedException {
    CookieStore delegate = new CookieManager().getCookieStore();
    HostOnlyCookieStore store = new HostOnlyCookieStore(delegate);
    URI uri = new URI("http://example.com/");

    HttpCookie cookie = new HttpCookie("sid", "SECRET");
    cookie.setPath("/");
    cookie.setMaxAge(1); // seconds; HttpCookie has no sub-second granularity
    store.add(uri, cookie);
    assertTrue(hasNamedCookie(store.get(uri), "sid"));

    // HttpCookie.hasExpired() compares (elapsed millis / 1000) > maxAge - integer division means a 1.1s
    // sleep against maxAge=1 isn't reliably past the boundary; 2.1s guarantees it
    Thread.sleep(2100);
    assertFalse(hasNamedCookie(store.get(uri), "sid"), "expired host-only cookie must not be returned by get()");
    // round 34 finding 2: get()'s own pruneIfEmpty() call was unpinned - nothing checked getURIs() after an
    // expiry-driven get()
    assertTrue(store.getURIs().isEmpty(),
        "get()'s own expiry pruning must also remove the now-empty host entry from getURIs()");
  }


  @Test
  void testHostOnlyCookieExpiryIsPrunedFromGetCookies() throws java.net.URISyntaxException, InterruptedException {
    // independent of get() (round 34 finding 2): the previous version of this test called get() first, which
    // already pruned the expired cookie - making its own getCookies() assertion a PROVABLE EQUIVALENT MUTANT
    // of getCookies()'s own expiry-pruning logic, the same "vacuous assertion" shape round 33 finding 5 found
    // and fixed for testHostOnlyCookieWithZeroMaxAgeIsNeverStored
    CookieStore delegate = new CookieManager().getCookieStore();
    HostOnlyCookieStore store = new HostOnlyCookieStore(delegate);
    URI uri = new URI("http://example.com/");

    HttpCookie cookie = new HttpCookie("sid", "SECRET");
    cookie.setPath("/");
    cookie.setMaxAge(1);
    store.add(uri, cookie);

    Thread.sleep(2100);
    assertFalse(hasNamedCookie(store.getCookies(), "sid"),
        "expired host-only cookie must not be returned by getCookies()");
    assertTrue(store.getURIs().isEmpty(),
        "getCookies()'s own expiry pruning must also remove the now-empty host entry from getURIs()");
  }


  @Test
  void testHostOnlyCookieMatchesRequestHostCaseInsensitively() throws java.net.URISyntaxException {
    // round 33 finding 3: java.net.URI.getHost() preserves the request URI's original case (verified
    // directly), and an ordinary redirect Location header differing in host case from the original request
    // is unremarkable - java.net.InMemoryCookieStore itself matches host-only cookies case-insensitively
    // (its uriIndex is keyed by URI, and URI.compareTo() compares the host component case-insensitively).
    // This decorator's own host-only side table is keyed by a plain String, so it must lowercase explicitly
    // on both add() and get() - mutation-verified gap: every other test in this suite (and this class' own
    // callers in StreamUtils) happens to use an already-lowercase host
    CookieStore delegate = new CookieManager().getCookieStore();
    HostOnlyCookieStore store = new HostOnlyCookieStore(delegate);

    HttpCookie cookie = new HttpCookie("sid", "SECRET");
    cookie.setPath("/");
    store.add(new URI("http://EXAMPLE.com/"), cookie);

    assertTrue(hasNamedCookie(store.get(new URI("http://example.com/")), "sid"),
        "a host-only cookie set at a mixed-case host must still be returned for the same host in a "
            + "different case");
    assertTrue(hasNamedCookie(store.get(new URI("http://Example.COM/")), "sid"));
  }

  @Test
  void testHostOnlyCookieDoesNotMatchHostThatMerelyEndsWithTheStoredHost() throws java.net.URISyntaxException {
    // coverage gap found by an independent review: every other multi-host test in this class uses SIBLING
    // hosts (e.g. data.example.com / auth.example.com below), never a host that is a plain string suffix of
    // another (e.g. "evil-example.com" or "notexample.com" both literally end with "example.com", with no
    // "." boundary). get()/add() key their host-only side table by exact String equality
    // (m_hostOnly.get(host)/.put(host, ...)), not a suffix scan - but no test before this one would have
    // caught a regression to suffix-based matching, since none used a host shaped like this
    CookieStore delegate = new CookieManager().getCookieStore();
    HostOnlyCookieStore store = new HostOnlyCookieStore(delegate);

    HttpCookie cookie = new HttpCookie("sid", "SECRET");
    cookie.setPath("/");
    store.add(new URI("http://example.com/"), cookie);

    assertFalse(hasNamedCookie(store.get(new URI("http://evil-example.com/")), "sid"),
        "a cookie scoped to example.com must not leak to a host that merely ends with that string");
    assertFalse(hasNamedCookie(store.get(new URI("http://notexample.com/")), "sid"));
    assertTrue(hasNamedCookie(store.get(new URI("http://example.com/")), "sid"),
        "sanity check: the cookie is still returned for its own actual host");
  }


  @Test
  void testDomainCookiesAreDelegatedUnchanged() throws java.net.URISyntaxException {
    // a cookie WITH a domain must be handled entirely by the delegate, unaffected by this decorator - the
    // delegate's own domain-matching (subdomain replay, etc.) must still work exactly as it does standalone
    CookieStore delegate = new CookieManager().getCookieStore();
    HostOnlyCookieStore store = new HostOnlyCookieStore(delegate);

    HttpCookie cookie = new HttpCookie("sid", "SECRET");
    cookie.setDomain(".example.com");
    cookie.setPath("/");
    store.add(new URI("http://www.example.com/"), cookie);

    assertTrue(hasNamedCookie(store.get(new URI("http://www.example.com/")), "sid"));
    assertTrue(hasNamedCookie(store.get(new URI("http://other.example.com/")), "sid"),
        "a domain cookie must still be replayed to a genuine sibling subdomain");
    assertTrue(hasNamedCookie(delegate.get(new URI("http://www.example.com/")), "sid"),
        "a domain cookie must actually live in the delegate, not this decorator's own side table");
  }


  @Test
  void testGetCookiesIncludesBothHostOnlyAndDomainCookies() throws java.net.URISyntaxException {
    CookieStore delegate = new CookieManager().getCookieStore();
    HostOnlyCookieStore store = new HostOnlyCookieStore(delegate);

    HttpCookie hostOnly = new HttpCookie("hostOnlySid", "A");
    hostOnly.setPath("/");
    store.add(new URI("http://example.com/"), hostOnly);

    HttpCookie domainCookie = new HttpCookie("domainSid", "B");
    domainCookie.setDomain(".example.com");
    domainCookie.setPath("/");
    store.add(new URI("http://www.example.com/"), domainCookie);

    assertTrue(hasNamedCookie(store.getCookies(), "hostOnlySid"));
    assertTrue(hasNamedCookie(store.getCookies(), "domainSid"));
  }


  @Test
  void testRemoveRemovesHostOnlyCookieOnlyFromItsOwnHost() throws java.net.URISyntaxException {
    CookieStore delegate = new CookieManager().getCookieStore();
    HostOnlyCookieStore store = new HostOnlyCookieStore(delegate);
    URI dataUri = new URI("http://data.example.com/");
    URI authUri = new URI("http://auth.example.com/");

    HttpCookie cookieA = new HttpCookie("sid", "FROM_DATA");
    cookieA.setPath("/");
    store.add(dataUri, cookieA);
    HttpCookie cookieB = new HttpCookie("sid", "FROM_AUTH");
    cookieB.setPath("/");
    store.add(authUri, cookieB);

    assertTrue(store.remove(authUri, cookieB));
    assertFalse(hasNamedCookie(store.get(authUri), "sid"));
    assertTrue(hasNamedCookie(store.get(dataUri), "sid"), "removing host B's cookie must not affect host A's");
  }


  @Test
  void testRemoveMatchesRequestHostCaseInsensitively() throws java.net.URISyntaxException {
    // round 34 finding 2: round 33 finding 3 closed this same case-normalization gap for add()/get() but
    // missed remove() - mutation-verified: dropping remove()'s own toLowerCase(Locale.ROOT) call left the
    // whole suite green
    CookieStore delegate = new CookieManager().getCookieStore();
    HostOnlyCookieStore store = new HostOnlyCookieStore(delegate);

    HttpCookie cookie = new HttpCookie("sid", "SECRET");
    cookie.setPath("/");
    store.add(new URI("http://example.com/"), cookie);

    // remove() is called with a DIFFERENT case than add() used - add()'s own lowercasing already stored the
    // map key as "example.com", so remove() must independently lowercase its own already-lowercase-looking
    // "example.com" input too for this to actually exercise anything; using an upper-case host here is what
    // makes the mutation (dropping remove()'s own toLowerCase()) actually observable
    assertTrue(store.remove(new URI("http://EXAMPLE.COM/"), cookie),
        "remove() must match the same host regardless of case, matching add()/get()");
    assertFalse(hasNamedCookie(store.get(new URI("http://example.com/")), "sid"));
  }


  @Test
  void testRemoveActuallyRemovesADomainCookieFromTheDelegate() throws java.net.URISyntaxException {
    // round 33 finding 5: every existing remove() test only ever removes a HOST-ONLY cookie - this decorator
    // must also actually forward a domain cookie's removal to the delegate (mutation-verified gap: replacing
    // "m_delegate.remove(uri, cookie)" with a hardcoded false left the whole suite green)
    CookieStore delegate = new CookieManager().getCookieStore();
    HostOnlyCookieStore store = new HostOnlyCookieStore(delegate);
    URI uri = new URI("http://www.example.com/");

    HttpCookie domainCookie = new HttpCookie("sid", "SECRET");
    domainCookie.setDomain(".example.com");
    domainCookie.setPath("/");
    store.add(uri, domainCookie);

    assertTrue(store.remove(uri, domainCookie));
    assertFalse(hasNamedCookie(store.get(uri), "sid"), "a removed domain cookie must actually be gone");
  }


  @Test
  void testRemoveAllReturnsTrueForHostOnlyCookiesAlone() throws java.net.URISyntaxException {
    // round 33 finding 5: every existing removeAll() test mixes a host-only cookie with a domain cookie, so
    // the delegate's own true return value masks whether "|| hadHostOnly" is actually needed (mutation-
    // verified gap: dropping it left the whole suite green)
    CookieStore delegate = new CookieManager().getCookieStore();
    HostOnlyCookieStore store = new HostOnlyCookieStore(delegate);

    HttpCookie hostOnly = new HttpCookie("sid", "SECRET");
    hostOnly.setPath("/");
    store.add(new URI("http://example.com/"), hostOnly);

    assertTrue(store.removeAll(), "removeAll() must report true when only HOST-ONLY cookies existed");
  }


  @Test
  void testRemoveAllClearsBothHostOnlyAndDomainCookies() throws java.net.URISyntaxException {
    CookieStore delegate = new CookieManager().getCookieStore();
    HostOnlyCookieStore store = new HostOnlyCookieStore(delegate);

    HttpCookie hostOnly = new HttpCookie("hostOnlySid", "A");
    hostOnly.setPath("/");
    store.add(new URI("http://example.com/"), hostOnly);
    HttpCookie domainCookie = new HttpCookie("domainSid", "B");
    domainCookie.setDomain(".example.com");
    domainCookie.setPath("/");
    store.add(new URI("http://www.example.com/"), domainCookie);

    assertTrue(store.removeAll());
    assertTrue(store.getCookies().isEmpty());
  }


  @Test
  void testGetUrisPrunesEmptyHostEntryAfterZeroMaxAgeAdd() throws java.net.URISyntaxException {
    // round 33 finding 4: add()'s computeIfAbsent() creates the host's list entry BEFORE the
    // "cookie.getMaxAge() != 0" guard runs, so a FIRST-EVER add for a host that already carries Max-Age=0
    // (never actually stored) left an empty list entry behind - getCookies() correctly returned [] (it
    // iterates values, an empty list contributes nothing) but getURIs() still reported that host, diverging
    // from java.net.InMemoryCookieStore.getURIs()'s own explicit empty-entry pruning and from this
    // interface's own javadoc ("cookies store... has NO cookie associated with this URI" is what getURIs()
    // must exclude)
    CookieStore delegate = new CookieManager().getCookieStore();
    HostOnlyCookieStore store = new HostOnlyCookieStore(delegate);

    HttpCookie cookie = new HttpCookie("sid", "SECRET");
    cookie.setPath("/");
    cookie.setMaxAge(0);
    store.add(new URI("http://example.com/"), cookie);

    assertTrue(store.getURIs().isEmpty());
  }


  @Test
  void testGetUrisPrunesHostEntryAfterAllCookiesRemoved() throws java.net.URISyntaxException {
    CookieStore delegate = new CookieManager().getCookieStore();
    HostOnlyCookieStore store = new HostOnlyCookieStore(delegate);
    URI uri = new URI("http://example.com/");

    HttpCookie cookie = new HttpCookie("sid", "SECRET");
    cookie.setPath("/");
    store.add(uri, cookie);
    assertFalse(store.getURIs().isEmpty());

    store.remove(uri, cookie);
    assertTrue(store.getURIs().isEmpty(), "removing the only cookie for a host must prune its getURIs() entry");
  }


  @Test
  void testGetUrisDoesNotDuplicateHostPresentInBothDelegateAndHostOnlyTable() throws java.net.URISyntaxException {
    // round 33 finding 4: a host holding both a host-only cookie AND a domain cookie (delegated unchanged)
    // must appear only ONCE in getURIs(), matching java.net.InMemoryCookieStore's own uriIndex-keySet-based
    // de-duplication - a plain ArrayList concatenation of the delegate's own getURIs() and this decorator's
    // own host-only keys does not de-duplicate on its own
    CookieStore delegate = new CookieManager().getCookieStore();
    HostOnlyCookieStore store = new HostOnlyCookieStore(delegate);
    URI uri = new URI("http://www.example.com/");

    HttpCookie hostOnly = new HttpCookie("hostOnlySid", "A");
    hostOnly.setPath("/");
    store.add(uri, hostOnly);

    HttpCookie domainCookie = new HttpCookie("domainSid", "B");
    domainCookie.setDomain(".example.com");
    domainCookie.setPath("/");
    store.add(uri, domainCookie);

    long matchingCount = store.getURIs().stream()
        .filter(u -> "www.example.com".equalsIgnoreCase(u.getHost()))
        .count();
    assertEquals(1, matchingCount);
  }


  @Test
  void testGetUrisIncludesHostOnlyHosts() throws java.net.URISyntaxException {
    CookieStore delegate = new CookieManager().getCookieStore();
    HostOnlyCookieStore store = new HostOnlyCookieStore(delegate);

    HttpCookie hostOnly = new HttpCookie("sid", "A");
    hostOnly.setPath("/");
    store.add(new URI("http://example.com/"), hostOnly);

    assertTrue(store.getURIs().stream().anyMatch(u -> "example.com".equalsIgnoreCase(u.getHost())));
  }


  @Test
  void testGetUrisIncludesDelegateOnlyHosts() throws java.net.URISyntaxException {
    // round 35 finding 1: every existing getURIs() test either uses an empty delegate or only checks that a
    // HOST-ONLY host is present - none confirm the delegate's OWN contribution actually reaches the merged
    // result. Mutation-verified: replacing "new LinkedHashSet<>(m_delegate.getURIs())" with an empty
    // LinkedHashSet left the whole suite green
    CookieStore delegate = new CookieManager().getCookieStore();
    HostOnlyCookieStore store = new HostOnlyCookieStore(delegate);

    HttpCookie domainCookie = new HttpCookie("sid", "A");
    domainCookie.setDomain(".example.com");
    domainCookie.setPath("/");
    store.add(new URI("http://www.example.com/"), domainCookie);

    assertTrue(store.getURIs().stream().anyMatch(u -> "www.example.com".equalsIgnoreCase(u.getHost())),
        "a host holding only a delegated domain cookie must still appear in getURIs()");
  }


  @Test
  void testAddDelegatesNullDomainCookieForNullUriOrHost() throws java.net.URISyntaxException {
    // round 35 finding 2: add()'s "uri == null || uri.getHost() == null" delegation terms (the corner case
    // this class's own remove()-comment already refers to) had no direct test - mutation-verified: narrowing
    // the guard to just "cookie.getDomain() != null" left the whole suite green. A null uri/host means this
    // class's own host-only side table (keyed by host) can't be used at all, so the only place left for such
    // a cookie is the delegate - matching java.net.InMemoryCookieStore.add(), which stores an add(null,
    // cookie) cookie in its cookieJar only, never indexed (so it can never actually be retrieved - harmless,
    // not this decorator's own concern to fix)
    HttpCookie cookie = new HttpCookie("sid", "A");
    cookie.setPath("/");
    CookieStore delegate = new CookieManager().getCookieStore();
    HostOnlyCookieStore store = new HostOnlyCookieStore(delegate);

    store.add(null, cookie);

    assertTrue(delegate.getCookies().contains(cookie), "a null-uri host-only cookie must be delegated");
  }


  @Test
  void testGetReturnsOnlyDelegateResultsForHostlessUri() throws java.net.URISyntaxException {
    // round 35 finding 2: get()'s own "uri.getHost() != null" guard had no direct test - mutation-verified:
    // replacing it with "if (true)" left the whole suite green (an NPE at uri.getHost().toLowerCase(...) for
    // a host-less URI like "mailto:a@b.com" is caught nowhere upstream in a real caller either, since
    // CookieManager.get() is never invoked for a non-http(s) request - but this class's own contract should
    // still degrade to "delegate-only, no NPE" rather than throw)
    CookieStore delegate = new CookieManager().getCookieStore();
    HostOnlyCookieStore store = new HostOnlyCookieStore(delegate);

    assertEquals(java.util.List.of(), store.get(new URI("mailto:a@b.com")));
  }
}
