package org.pharmgkb.common.util;

import com.google.common.net.InetAddresses;
import com.google.common.net.InternetDomainName;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jspecify.annotations.Nullable;


/**
 * This class contains useful stream convenience functions.
 * <p>
 * Merely referencing this class at all (any static method, not just {@link #copyUrlToFile(String, Path)})
 * attempts one permanent, JVM-wide side effect: it raises {@code jdk.httpclient.redirects.retrylimit} to 50
 * for every {@link java.net.http.HttpClient} in the whole process - including ones this library has nothing
 * to do with - unless that property was already set via {@code -D}/{@link System#setProperty}. This is a
 * best-effort mitigation, not a guarantee: the JDK reads this property exactly once, on the first redirect
 * ANY {@code HttpClient} anywhere in the JVM follows, and setting it afterward - even from a brand-new
 * client built after this class has loaded - is a no-op for the rest of the JVM's life. If a host
 * application follows its own redirect (via its own, unrelated {@code HttpClient} usage) before ever
 * referencing this library at all, this side effect cannot retroactively fix that. (A JDK "net property"
 * like this one can also be defaulted via {@code $JAVA_HOME/conf/net.properties}, but that channel is
 * invisible to this check and gets overridden regardless - see the class's own static initializer for why
 * that gap can't be closed.) See that same static initializer for the full rationale for why this can't be
 * scoped any narrower than class-load time.
 *
 * @author Mark Woon
 */
public class StreamUtils {

  // java.util.logging.LogManager holds loggers via WeakReference - a Logger configured (setLevel(), etc.)
  // without anything else holding a STRONG reference to it can be garbage-collected at any time, silently
  // discarding that configuration; the next Logger.getLogger(name) call (e.g. from inside
  // CookieManager.put() itself) then creates a brand-new, unconfigured instance. A local variable inside
  // buildHttpClient() below isn't enough - it goes out of scope the moment the method returns. This field
  // exists solely to keep that Logger instance (and therefore its Level.OFF setting) alive for the JVM's
  // whole lifetime - verified empirically: without this field, the suppression below is real but silently
  // reverts under enough GC pressure (reproduced by running the full test suite, where it's flaky, vs. a
  // single class in isolation, where the Logger survives long enough to look reliable)
  private static final Logger sf_cookieManagerLogger = Logger.getLogger("java.net.CookieManager");

  // java.net.http.HttpClient has no per-client (let alone per-request) knob for its redirect-hop limit -
  // it's read once, JVM-wide, from this system property, by the FIRST HttpClient anywhere in the JVM (not
  // just this library's own) to follow a redirect. That makes "set it right before building OUR client"
  // (the previous, lazy placement, inside buildHttpClient()) unreliable: it only works if THIS library's own
  // HttpClient happens to be the first one to follow a redirect anywhere in the process - any unrelated
  // earlier HttpClient usage elsewhere in the same JVM (an application's own auth client, for instance) reads
  // the JDK's un-raised default first and permanently locks it in for the rest of the JVM's life, with no way
  // for this library to detect or recover from that after the fact. So this is set here instead - eagerly,
  // as soon as this class loads at all, not lazily inside buildHttpClient() - which narrows that race window
  // (this class tends to load well before copyUrlToFile() is actually invoked) but does NOT close it: the
  // property is read exactly once, by the first redirect ANY HttpClient anywhere follows, and setting it
  // AFTER that read - even from a brand-new HttpClient instance built after this static initializer runs -
  // is a proven no-op for the rest of the JVM's life (verified directly: build a client, have it follow a
  // redirect with the property unset, THEN set the property, THEN build and use a second, brand-new client -
  // the second client is still capped at the JDK's original default). So if a host application makes its own
  // unrelated HttpClient call that follows a redirect before ever referencing this library at all, this
  // static initializer running afterward cannot retroactively fix that - deliberately accepting this as the
  // best available mitigation, not a guarantee, given a library has no way to run before arbitrary
  // unrelated code in the same JVM. The JDK's own default (jdk.httpclient.redirects.retrylimit=5) is
  // effectively 4 FOLLOWED hops (one of the 5 attempts is the initial, non-redirect request) - far short of
  // main's Apache HttpClient RequestConfig default of 50, and exceeding it doesn't throw a clear "too many
  // redirects" error like main did: HttpClient just returns the last (redirect) response as-is, which
  // copyUrlToFile() below reports as a plain "Error downloading <url>: 302" - indistinguishable from a
  // genuinely terminal redirect. Only set if not already configured via the -D/System.setProperty channel -
  // a deployer who explicitly set THAT must not have their choice silently overwritten. This does NOT cover
  // every channel, though: jdk.httpclient.* is a JDK "net property" (sun.net.NetProperties), which ALSO reads
  // $JAVA_HOME/conf/net.properties as a fallback default BELOW the -D system property in precedence but is
  // otherwise invisible to System.getProperty(...) - a deployer using that file instead is still silently
  // overwritten by the setProperty(...) call below, since setting a System property always outranks the
  // properties-file default regardless of which one "got there first". No accessible fix exists
  // (sun.net.NetProperties isn't exported, and hand-parsing conf/net.properties would be fragile and
  // layout-dependent) - documented as a known, narrow limitation rather than fixed.
  static {
    if (System.getProperty("jdk.httpclient.redirects.retrylimit") == null) {
      System.setProperty("jdk.httpclient.redirects.retrylimit", "50");
    }
  }


  /**
   * Static class.
   */
  private StreamUtils() {
  }


  // Works around several bugs in java.net.HttpCookie.domainMatches() (used internally by
  // CookiePolicy.ACCEPT_ORIGINAL_SERVER, the default) - most importantly, that it incorrectly REJECTS a
  // Domain attribute written as a parent domain without a leading dot (e.g. "Domain=example.com" from host
  // "www.example.com"), the RFC 6265 form essentially every modern server sends - silently dropping the
  // cookie and reintroducing the exact bare-403-on-a-session-gated-redirect failure buildHttpClient()'s own
  // per-call CookieManager was added to fix. This method originally (rounds 19-28) delegated to the JDK's
  // own method FIRST rather than reimplementing RFC 6265 domain-matching from scratch, only adding fallbacks
  // for specific gaps - domainMatches() had other JDK-specific behavior worth keeping (e.g. CookieManager.put()
  // rewrites a dotless host's cookie domain to "<host>.local", which this same JDK method has a matching
  // special case for). That general delegation was dropped entirely in round 29 (finding 3): the JDK method
  // has its OWN bug that delegation couldn't be worked around narrowly - it accepts a suffix match with no
  // actual "." label boundary (e.g. "example.com" wrongly matches "evilexample.com"/"notexample.com" just as
  // readily as a real subdomain), a genuine cross-host cookie-injection vector confirmed end-to-end and a
  // real regression vs main (which rejects it). By round 29, this method's own guards and label-boundary
  // fallback (rounds 20-28) already covered everything the delegate call was doing double duty for -
  // general exact match, the dotless ".local" convention, and a host N labels below the cookie domain - so
  // removing the delegate closed the injection gap with no loss of the legitimate behavior it used to provide.
  //
  // Also forces every accepted cookie to RFC 6265 "version 0" (cookie.setVersion(0), a mutation of the
  // actual HttpCookie instance CookieManager.put() is about to store - the only hook this functional
  // interface exposes for it): HttpCookie.parse()'s guessCookieVersion() classifies any Set-Cookie
  // containing a Max-Age or Version attribute as RFC 2965 "version 1", and CookieManager then renders the
  // follow-up Cookie header in RFC 2965 syntax (`$Version="1"; name="value";$Path="...";$Domain="..."`)
  // instead of the plain `name=value` form essentially every modern server expects - silently dropping a
  // real session cookie (Max-Age is far more common on those than the bare/Domain-only shapes tested above)
  // and reintroducing the same bare-403-on-a-session-gated-redirect failure this whole cookie feature exists
  // to fix. This is main-parity or better either way (main's Apache HttpClient rejects an RFC-2965-tagged
  // quoted value/explicit Version=1 outright; this doesn't) - see review.log round 22 for the verified
  // before/after comparison. Forcing version 0 also makes RETRIEVAL use
  // java.net.InMemoryCookieStore.netscapeDomainMatches() instead of HttpCookie.domainMatches() - this does
  // NOT make retrieval safer, despite this class's own comment claiming so through round 29: read directly
  // from JDK 21 source, netscapeDomainMatches() has the IDENTICAL non-label-boundary bug this class's own
  // domainMatches() above works around at storage time (round 29 finding 3) - and is actually LAXER, since
  // it has no depth limit at all (HttpCookie.domainMatches() at least requires the extra host prefix to
  // contain no dot). So a cookie legitimately ACCEPTED at storage time by this class's own domainMatches()
  // (e.g. Domain=example.com from host www.example.com) was still being replayed to a completely unrelated
  // host on RETRIEVAL (e.g. "notexample.com", which merely happens to share example.com's trailing 11
  // characters with no "." boundary) - the mirror image of round 29 finding 3's injection bug, but on the
  // exfiltration side, and confirmed end-to-end via a real CookieManager/InMemoryCookieStore round-trip
  // (round 30 finding 1). Fixed below by normalizing every accepted cookie's domain to the leading-dot
  // (Netscape/RFC 2109) form - netscapeDomainMatches() (and HttpCookie.domainMatches(), for that matter)
  // both correctly enforce the "." boundary once the dot is actually part of the compared string, since the
  // dot then has to line up exactly at the boundary character rather than being inferred from position
  // alone. Verified this has zero effect on rendering (a version-0 cookie's Cookie header is always plain
  // "name=value", HttpCookie.toNetscapeHeaderString() never includes Domain) and zero effect on the two
  // existing redirect-carries-cookie tests
  private static final CookiePolicy sf_cookiePolicy = StreamUtils::shouldAcceptCookie;

  // package-private (not private) so it can be unit-tested directly against a real CookieManager/
  // InMemoryCookieStore, without needing any network I/O or DNS faking - CookieStore.get(URI) never
  // resolves the URI's host, so testing the retrieval-side leak this closes (round 30 finding 1) just needs
  // a manually-constructed URI naming an unrelated host, not a real second server
  static boolean shouldAcceptCookie(URI uri, HttpCookie cookie) {
    cookie.setVersion(0);
    String domain = cookie.getDomain();
    String host = uri.getHost();
    if (domain != null && domain.isEmpty()) {
      // a literal "Domain=;" (or bare "Domain=") attribute parses via HttpCookie.parse() to a non-null,
      // EMPTY domain (verified directly) - a real, distinct third shape from both "no Domain attribute at
      // all" (null, auto-filled by CookieManager.put() to the host - handled by isEffectivelyHostOnly() below)
      // and "an explicit Domain matching the host" (also isEffectivelyHostOnly()). isEffectivelyHostOnly()
      // itself bails on domain.isEmpty(), and the leading-dot normalization further down is guarded on
      // !domain.isEmpty() too, so this shape used to fall all the way through to domainMatches("", host) -
      // true, per that method's own documented empty-domain short-circuit - with the cookie's domain left as
      // "" (non-null). HostOnlyCookieStore only recognizes a NULL domain as host-only, so a non-null "" was
      // silently delegated to the plain InMemoryCookieStore, reopening round 32 finding 1's exact
      // HttpCookie.equals() cross-host collision for this one shape (round 33 finding 1)
      cookie.setDomain(null);
      return true;
    }
    if (isEffectivelyHostOnly(domain, host)) {
      // an EXACT match (including the trailing-dot/IP-literal/dotless-".local" forms domainMatches() itself
      // already treats as exact - round 28/round 30 finding 3) is handled here DIRECTLY, WITHOUT ever
      // consulting domainMatches() - stored as a TRUE host-only cookie (domain cleared to null;
      // InMemoryCookieStore.add() skips domainIndex entirely for one, verified directly from JDK 21 source)
      // rather than as a domain cookie that just happens to equal the host (round 31 finding 2). Doing this
      // BEFORE domainMatches() runs (not by changing what domainMatches() itself accepts) is deliberate:
      // domainMatches()'s own unconditional reject of a public-suffix/single-label domain - even an exact
      // match, round 27 finding 1 - is still exactly correct for its own narrower question ("is it safe to
      // accept this AS A DOMAIN cookie, matched by suffix on retrieval") and every existing test exercising
      // domainMatches() directly for that question is unaffected on purpose. This method's own broader
      // question - "should this cookie be accepted at all, and how should it be stored" - has a second, safer
      // answer domainMatches() alone doesn't have access to: converting an exact match to host-only sidesteps
      // the domain-safety question entirely, rather than answering it more permissively. CookieManager.put()
      // auto-fills an absent Domain attribute verbatim to the host, so a plain host-only cookie (no Domain
      // attribute at all - the most common real-world session-cookie shape) is indistinguishable here from an
      // explicit self-referential Domain=<host> attribute, and both need this fix for the same reason: once
      // stored with a non-null domain equal to the host, RFC 6265 domain-matching correctly (not buggily)
      // treats it as a real PARENT domain, replaying it to every genuine subdomain the setting host itself
      // never asserted - e.g. a plain session cookie for hgdownload.soe.ucsc.edu (this library's own real
      // download target) was also being sent to evil.hgdownload.soe.ucsc.edu, a real regression vs main
      // (Apache HttpClient enforces host-only for a cookie with no explicit Domain attribute). This one
      // change retroactively closes round 29 finding 2 (public-suffix host) and round 30 finding 3 (dotless
      // ".local" host) too - all three share this exact root cause and fix. The one known behavior
      // narrowing: an EXPLICIT self-referential Domain=<host> attribute is now also host-only. Per RFC 6265
      // section 5.3 steps 6-7, this is NOT redundant with omitting Domain entirely (round 32 finding 4
      // corrected this comment's earlier, inaccurate "redundant - same effect" claim): ANY non-empty Domain
      // attribute - self-referential or not - sets host-only-flag to false, which is precisely how a server
      // asks for subdomain matching; omitting Domain is the only way to get host-only-flag true. So a rare
      // server that deliberately sends Domain=<host> to opt IN to subdomain matching for its own future use
      // loses that ability here, where main still honors it - accepted as the cost of closing the other three
      // real gaps (all far more common shapes) with a single mechanism
      cookie.setDomain(null);
      return true;
    }
    if (!domainMatches(domain, host)) {
      return false;
    }
    if (domain != null && !domain.isEmpty() && !domain.startsWith(".")) {
      cookie.setDomain("." + domain);
    }
    return true;
  }

  /**
   * Returns whether {@code domain} (a cookie's {@code Domain} attribute, possibly {@code null}) and
   * {@code host} (the request host that set it) are an EXACT match once each is canonicalized the same way
   * {@link #domainMatches} itself does for its own exact-match cases - a leading dot stripped from
   * {@code domain}, a trailing dot stripped from both, and the dotless-host {@code ".local"} auto-fill
   * convention treated as exact too. This mirrors {@code domainMatches}'s own notion of
   * "exact" for every ordinary input, but is a genuinely independent, narrower check, not a delegation to it -
   * for two unusual explicit-{@code Domain} shapes a real server could send, the two methods disagree:
   * {@code domainMatches("127.0.0.1.local", "127.0.0.1")} is {@code true} (its IP-literal
   * branch's {@code ".local"} exemption has no dotless-HOST requirement, only this method's own generalized
   * {@code ".local"} clause does), and {@code domainMatches("localhost.local.", "localhost")} is {@code false}
   * (its trailing-dot rejection only strips a trailing dot for the exact-match test, not the {@code ".local"}
   * clause below it, while this method strips it unconditionally). Both disagreements are harmless either way
   * (the caller ends up with host-only storage or a same-host-only domain cookie, never a leak) and neither
   * has been found reachable from any real Domain attribute in practice - documented here as a precision
   * correction, not a behavior change.
   * <p>
   * Does not itself special-case an empty {@code domain} - {@link #shouldAcceptCookie} (this method's only
   * caller) already handles that shape entirely on its own, before ever reaching here, so a redundant
   * {@code domain.isEmpty()} check here would be unreachable dead code (mutation-verified: removing it left
   * the whole suite green).
   */
  private static boolean isEffectivelyHostOnly(@Nullable String domain, @Nullable String host) {
    if (domain == null || host == null) {
      return false;
    }
    String bareDomain = domain.startsWith(".") ? domain.substring(1) : domain;
    bareDomain = bareDomain.endsWith(".") ? bareDomain.substring(0, bareDomain.length() - 1) : bareDomain;
    String canonHost = host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
    return bareDomain.equalsIgnoreCase(canonHost) ||
        (canonHost.indexOf('.') < 0 && bareDomain.equalsIgnoreCase(canonHost + ".local"));
  }

  // package-private (not private) so it can be unit-tested directly, same pattern as
  // mapDownloadExecutionCause() below - triggering the real bug this works around from inside a full
  // copyUrlToFile() HttpServer round-trip would need a real, non-localhost DNS name (CookieManager rewrites
  // a dotless host's cookie domain to "<host>.local", which sidesteps the bug entirely), and
  // jdk.net.hosts.file (the usual way to fake DNS in a test) is itself a JVM-wide, read-once-per-process
  // property - unreliable to set dynamically mid-test-run, the same category of hazard as
  // jdk.httpclient.redirects.retrylimit above
  static boolean domainMatches(String cookieDomain, String host) {
    if (cookieDomain == null || cookieDomain.isEmpty()) {
      // no explicit Domain attribute IS possible from a real server (a bare "Domain=" or "Domain=;" attribute
      // parses to an empty, non-null domain, and CookieManager.put() only fills a domain in when it's null,
      // not when it's already empty) - but this is harmless either way: shouldAcceptCookie() below only gates
      // storage, and an empty cookieDomain would fail InMemoryCookieStore.netscapeDomainMatches() on
      // retrieval too (the matcher every accepted, version-0 cookie actually goes through - round 22), so
      // this just short-circuits to the same effectively-host-only-cookie outcome retrieval would already
      // produce
      return true;
    }
    // the IP-literal and public-suffix guards below run BEFORE the general exact-match/label-boundary logic
    // further down - historically (rounds 20-28) this ordering mattered because both guards previously ran
    // AFTER a now-removed HttpCookie.domainMatches() delegate call, which was itself permissive for exactly
    // the shapes these guards exist to reject whenever the cookie domain carried a LEADING DOT (round 26):
    // ".co.uk"/".github.io" matched any single-level subdomain, and ".0.0.1"/".127.0.0.1" matched an
    // IP-address host one label below, the same way a real DNS name would - defeating round 25's public-
    // suffix guard and round 22's IP-literal guard entirely. The delegate itself is gone (round 29 finding
    // 3), but the ordering is kept as-is since it's still correct and there's no reason to disturb it.
    String domain = cookieDomain.startsWith(".") ? cookieDomain.substring(1) : cookieDomain;
    // RFC 6265 section 5.1.2 requires the request-host to be CANONICALIZED (a trailing dot, the DNS
    // "fully-qualified" form, stripped) before domain-matching even begins - round 28's own trailing-dot
    // handling only ever applied this to the exact-match test a few lines below, not to the delegate call or
    // the label-boundary fallback further down, both of which still compared against the raw host - so a
    // genuine PARENT-domain match (no explicit leading dot, the common real-world RFC 6265 form) failed
    // whenever the REQUEST HOST itself happened to be in trailing-dot FQDN form (round 29 finding 1). This
    // canonicalization is host-only, not symmetric with domain's own trailing dot (which remains malformed
    // per RFC 6265 and is still rejected below, unaffected by this)
    String canonHost = host != null && host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
    if (canonHost != null) {
      if (InetAddresses.isUriInetAddress(canonHost)) {
        // RFC 6265 section 5.1.3's domain-match algorithm never applies to an IP-address host - it has no
        // "labels" in the domain-name sense, so no suffix match (label-boundary fallback below) must ever
        // succeed for one (e.g. a cookie domain of "0.1" must not match host "127.0.0.1" just because the
        // tail characters happen to line up at a dot). The one exception is an EXACT match - a host-only
        // cookie for a host that happens to BE an IP literal is legitimate (RFC 6265 domain-matching never
        // applies to an IP host in EITHER direction, so "domain equals host" here genuinely means "exact
        // match", not "suffix match"). Also accepts the JDK's own dotless-host ".local" auto-fill
        // convention (round 20): CookieManager.put() appends ".local" to any DOTLESS host - including a
        // bracketed IPv6 literal like "[::1]", which contains no dot - when auto-filling an absent Domain
        // attribute, so a plain host-only cookie for an IPv6-literal host is actually stored as
        // "<host>.local", not "<host>" verbatim. Missed by round 26's narrower probe set (round 28 finding
        // 2) - accepting this extra form carries no leak risk: no URI-legal host can ever literally equal
        // "<some-other-host>.local" (a bracket can't appear mid-label), and retrieval for the SAME host
        // doesn't even go through domain matching at all (InMemoryCookieStore.getEffectiveURI() keys its
        // lookup on host alone)
        if (!domain.equalsIgnoreCase(canonHost) && !domain.equalsIgnoreCase(canonHost + ".local")) {
          return false;
        }
        return true;
      } else {
        // a trailing dot (the DNS "fully-qualified" form, RFC 1035) on DOMAIN is malformed per RFC 6265 and
        // must be rejected UNLESS domain and host, once each stripped of their own trailing dot (if any),
        // are an EXACT match - CookieManager.put() auto-fills an absent Domain attribute verbatim from
        // uri.getHost(), so a plain host-only cookie for a trailing-dot-form host (e.g. "www.example.com.")
        // is stored WITH that same trailing dot, and must not be rejected just for it (round 28 finding 1:
        // round 27's unconditional reject didn't account for a trailing-dot HOST, only ever tested a
        // trailing-dot DOMAIN against a host it doesn't exactly match). This exemption is narrower than the
        // IP-host branch's own exact-match exemption above though - it ONLY widens what counts as an exact
        // match for the trailing-dot check specifically; the single-label/IP-shaped/public-suffix checks
        // below still run UNCONDITIONALLY on the trailing-dot-stripped domain, exact match or not (same
        // round-27-finding-1 reasoning as before - an exact match against a public suffix domain is still a
        // real retrieval-time leak, trailing dot or not), so a trailing-dot public suffix (e.g. "co.uk.") is
        // still rejected even when it exactly equals a same-form host
        String bareDomain = domain.endsWith(".") ? domain.substring(0, domain.length() - 1) : domain;
        String bareHost = canonHost;
        // the single-label/IP-shaped/public-suffix checks must reject UNCONDITIONALLY - even an EXACT match
        // against a domain that's itself a single-label or multi-label public suffix (round 27 finding 1:
        // round 26 wrongly exempted this case, reasoning it was "a legitimate exact match, not a suffix
        // match" and that "main's own PublicSuffixDomainFilter permits it too" - both wrong). Cookie
        // RETRIEVAL for a version-0 cookie (forced by sf_cookiePolicy above) goes through the JDK's
        // netscapeDomainMatches() - a PLAIN SUFFIX match, not an exact-string check - so accepting
        // Domain=co.uk from host co.uk still lets that cookie be replayed to any unrelated sibling under the
        // same suffix (e.g. b.co.uk) on retrieval, verified end-to-end through the real copyUrlToFile()
        // path. Apache HttpClient 4.5.14's own PublicSuffixDomainFilter source confirms its
        // equalsIgnoreCase(origin.getHost()) exemption exists ONLY for a DOTLESS (single-label) domain - for
        // a DOTTED (multi-label) public suffix it rejects unconditionally, exact match or not - so an
        // unconditional reject here for BOTH shapes is main-parity for the multi-label case and, if
        // anything, more defensive than main for the single-label case (zero real usage either way makes the
        // extra strictness free). Checked on the trailing-dot-STRIPPED domain so a trailing-dot public
        // suffix (e.g. "co.uk.") is caught the same way as the bare form.
        //
        // This guard REJECTS a plain HOST-ONLY cookie (no explicit Domain attribute at all) whenever the
        // REQUEST HOST ITSELF happens to be a public suffix (e.g. a real path-style S3 URL,
        // https://s3.amazonaws.com/bucket/key) or a single-label name in trailing-dot FQDN form (e.g.
        // http://localhost./...) - CookieManager.put() auto-fills such a cookie's domain to exactly the
        // host string, making it indistinguishable here from an explicit self-referential Domain attribute.
        // Round 29 finding 2 originally left this documented-but-unfixed (this guard alone can't safely
        // accept it - relaxing just this guard's answer, without also changing how the cookie gets stored,
        // would reopen the exact retrieval-time leak it exists to prevent). Round 31 finding 2 closed it at
        // the CALLER instead: shouldAcceptCookie() above now recognizes an exact domain-equals-host match
        // BEFORE ever calling this method at all, and stores it as a genuinely host-only cookie
        // (HttpCookie.setDomain(null)) - this method's own unconditional reject here is UNCHANGED and still
        // exactly correct for the question it actually answers ("is it safe to accept this domain as a
        // SUFFIX-matched domain cookie") - it's simply no longer the only path a real exact match can take
        if (bareDomain.indexOf('.') <= 0 || InetAddresses.isUriInetAddress(bareDomain) ||
            (InternetDomainName.isValid(bareDomain) && InternetDomainName.from(bareDomain).isPublicSuffix())) {
          return false;
        }
        // an EXACT match, once each side is stripped of its own trailing dot (if any), is legitimate - same
        // "domain equals host genuinely means exact match" reasoning as the IP-host branch above, and
        // resolved as a direct return rather than falling through to the label-boundary fallback below,
        // which only ever strips domain's LEADING dot, never a trailing one, so it would wrongly reject e.g.
        // domain="www.example.test." against host="www.example.test" (round 28 finding 1) despite them
        // naming the identical DNS name - the trailing dot is purely cosmetic (RFC 1035's "fully-qualified"
        // root indicator), not a different domain
        if (bareDomain.equalsIgnoreCase(bareHost)) {
          return true;
        }
        // a domain that still has its OWN trailing dot at this point is malformed (RFC 6265 domain names
        // don't have one) and didn't turn out to be an exact match above - reject rather than let it fall
        // through to the fallback below, which (per the same asymmetric-length reasoning just above) can't
        // be trusted to handle a trailing-dot domain's suffix-matching correctly either way
        if (domain.endsWith(".")) {
          return false;
        }
      }
    }
    // the JDK's own dotless-host ".local" auto-fill convention (round 20, generalized beyond the IP-literal
    // branch's own version of this check in round 29 finding 3): CookieManager.put() appends ".local" to ANY
    // dotless host - not just an IP literal - when auto-filling an absent Domain attribute, e.g. a plain
    // host-only cookie for host "localhost" is actually stored with domain "localhost.local", not
    // "localhost" verbatim. This exact case is the ORIGINAL, historical reason this method delegated to
    // HttpCookie.domainMatches() at all (see this class's own now-superseded design-history comment above) -
    // removing that general delegation to close finding 3's cross-host injection bug needed this explicit
    // replacement so the plain localhost-to-localhost case doesn't break.
    //
    // This check ACCEPTS "<host>.local" as a matching domain for any dotless host - but unlike the
    // IP-literal branch's own version of this check, "no URI-legal host can ever literally equal this" does
    // NOT make this generalized form leak-free on its own: once accepted as a DOMAIN cookie, "<host>.local"
    // is a real, retrieval-time-matchable domain (RFC 6265 domain-matching correctly treats it as a
    // legitimate parent), so it would be replayed to any genuine subdomain of it too, including an
    // attacker-controlled one the original server never asserted at all (e.g. a bare host-only cookie for
    // "localhost" replayed to "evil.localhost.local" - a phantom domain invented entirely by the JDK's own
    // auto-fill convention). Round 30 finding 1's leading-dot storage normalization does NOT close this - the
    // dot-boundary it enforces is already legitimately present here (there genuinely is a "." right before
    // "localhost.local" in "evil.localhost.local"). Round 30 finding 3 originally left this documented-but-
    // unfixed for the same reason as the public-suffix case above. Round 31 finding 2 closed it the same way:
    // shouldAcceptCookie() recognizes this exact dotless-".local" match BEFORE ever reaching this method at
    // all, and stores it as a genuinely host-only cookie - this check's own `return true` below is
    // UNCHANGED and still correct for its own question (a real, explicit Domain=<host>.local sent by some
    // OTHER host still needs to match here), it's simply no longer the only path the common auto-fill case
    // takes
    if (canonHost != null && canonHost.indexOf('.') < 0 && domain.equalsIgnoreCase(canonHost + ".local")) {
      return true;
    }
    if (canonHost == null || InetAddresses.isUriInetAddress(canonHost)) {
      return false;
    }
    // RFC 6265 section 5.1.3's real label-boundary suffix match - the general (non-exact, non-".local",
    // non-IP) case falls all the way through to here. This used to run only as a fallback AFTER first trying
    // java.net.HttpCookie.domainMatches() (which has its own bug: it accepts one extra label with no embedded
    // dot, but never actually checks that the extra label is separated from the cookie domain by a "."
    // boundary character - "example.com" wrongly matches "evilexample.com"/"notexample.com" just as readily
    // as a real subdomain like "www.example.com", a genuine cross-host cookie-injection vector confirmed
    // end-to-end through the real copyUrlToFile() path and a real regression vs main, which rejects it - round
    // 29 finding 3). That delegate call is no longer made at all: every case it was doing double duty for -
    // general exact match (handled above in the non-IP branch), the dotless ".local" convention (handled
    // just above), and a host one label below the cookie domain - is now covered by this method's OWN logic,
    // and this label-boundary check (originally added in round 21 to rescue a host MULTIPLE labels below the
    // cookie domain, e.g. this library's real https:// download target, hgdownload.soe.ucsc.edu, 4 labels
    // deep - see round 21) already correctly enforces the "." boundary the delegate never did, for hosts at
    // ANY depth, not just one label below: require host (canonicalized - see canonHost above) to end with
    // ".<domain>" - domain has already been validated (embedded dot, no trailing dot, not IP-shaped, not a
    // public suffix) by the guard above
    return canonHost.length() > domain.length()
        && canonHost.charAt(canonHost.length() - domain.length() - 1) == '.'
        && canonHost.regionMatches(true, canonHost.length() - domain.length(), domain, 0, domain.length());
  }


  /**
   * Builds a new {@link HttpClient} for a single {@link #copyUrlToFile(String, Path)} call.
   * <p>
   * Deliberately built fresh per call, matching main's Apache HttpClient, which was also built fresh per
   * call - among other things, this is what makes the {@code cookieHandler(...)} below safe: a redirect
   * hop that sets a session cookie the next hop requires (a common pattern for gated downloads) needs
   * somewhere to keep it, but a client-WIDE, long-lived cookie store would let cookies from one download
   * leak into an unrelated later download in the same JVM run, which main never did either. The main
   * downside - no connection-pool reuse across separate {@code copyUrlToFile} calls - is immaterial for
   * this library's real usage (infrequent, one-off large-file downloads, not high-volume repeated calls).
   * <p>
   * Also applies a JVM-global side effect (a JDK logger level) needed by the {@link HttpClient} this builds -
   * done here, not in a static initializer, so calling an unrelated method elsewhere in this class (e.g.
   * {@link #md5InBase64(byte[])}) can't silently trigger it just by loading this class. (The redirect-
   * retrylimit system property is a separate, class-load-time side effect - see the static initializer near
   * the top of this class for why THAT one can't wait until here.) This guard is idempotent, so repeating it
   * on every call (rather than only the first) is harmless - see its own comment below for what it does and
   * why.
   */
  private static HttpClient buildHttpClient(Duration idleTimeout) {
    // java.net.CookieManager.put() logs "SEVERE: Invalid cookie for <url>: <full Set-Cookie value>" via its
    // own JUL logger whenever HttpCookie.parse() rejects a Set-Cookie header (e.g. an unquoted comma inside a
    // Max-Age-tagged cookie value's RFC-2965 comma-splitting - see round 23 in review.log) - by default (the
    // JVM's built-in root-logger ConsoleHandler, which every JVM has unless a deployer replaced it) this
    // reaches stderr with zero logging configuration of the caller's own, printing a potential session-cookie
    // VALUE into logs main's Apache HttpClient never touched at all (it never used java.util.logging for
    // this). Only suppressed if this specific logger doesn't already have an explicit level set
    // (getLevel() == null means "inheriting from its parent/root logger", not "explicitly configured") - a
    // deployer who specifically configured this logger (e.g. to actually see this diagnostic while debugging)
    // must not have that choice silently overwritten
    if (sf_cookieManagerLogger.getLevel() == null) {
      sf_cookieManagerLogger.setLevel(Level.OFF);
    }
    return HttpClient.newBuilder()
        // matches the previous Apache HttpClient LaxRedirectStrategy, which followed cross-protocol
        // redirects in either direction (unlike HttpClient.Redirect.NORMAL, which blocks HTTPS->HTTP)
        .followRedirects(HttpClient.Redirect.ALWAYS)
        // no longer hardcoded to a fixed 30s now that this is built per call instead of once, shared,
        // for the process's whole lifetime - matches the non-HTTP branch's own idleTimeout-derived
        // setConnectTimeout() below
        .connectTimeout(idleTimeout)
        // a fresh cookie store per call - see this method's own javadoc above for why per-call (not
        // client-wide/shared) is what makes this safe. sf_cookiePolicy (not the default
        // ACCEPT_ORIGINAL_SERVER) works around java.net.HttpCookie.domainMatches()'s own bugs - see its
        // own comment above. Wrapped in HostOnlyCookieStore (round 32 finding 1) so two DIFFERENT hosts'
        // host-only cookies (shouldAcceptCookie() above clears domain to null for these) sharing a name/path
        // don't collide via HttpCookie.equals() (which never considers the host) - see that class's own
        // javadoc for the full rationale
        .cookieHandler(new CookieManager(new HostOnlyCookieStore(new CookieManager().getCookieStore()),
            sf_cookiePolicy))
        // pinned client-wide (not just per-request) so this also covers an https:// URL that redirects to a
        // cleartext http:// target: a request's HTTP version applies uniformly across all of ITS automatic
        // redirect hops, so a per-request override on just the initial request can't vary by hop - only a
        // client-wide default reaches every hop, cleartext or not. Without this, the default (HTTP/2) means
        // every cleartext request/hop sends an unsolicited "h2c" (HTTP/2-over-cleartext) upgrade attempt
        // (Connection: Upgrade, Upgrade: h2c, Http2-Settings: ...) that main's Apache HttpClient 4.5
        // (HTTP/1.1-only) never sent, and that some older/strict servers, proxies, and WAFs react badly to -
        // full main parity here was chosen over keeping HTTP/2 for the common https-only case, since the
        // gap otherwise silently reopens for any https:// download that happens to redirect to http://
        .version(HttpClient.Version.HTTP_1_1)
        .build();
  }


  /**
   * Opens an {@link InputStream} to the specified file.
   * Automatically unwraps .gz or .zip files.
   * <p>
   * In zip files, this will look for a file minus the ".zip" extension (i.e. expect file named {@code foo.txt} if
   * zipped filename is {@code foo.txt.zip}.
   */
  public static InputStream openInputStream(Path path) throws IOException {

    if (!Files.exists(path)) {
      // the 1-arg constructor takes the FILE, not a message - using it here would drop the actual path
      throw new NoSuchFileException(path.toString(), null, "File does not exist");
    }
    if (!Files.isRegularFile(path)) {
      throw new NoSuchFileException(path.toString(), null, "Path does not lead to a regular file");
    }
    String origFilename = PathUtils.getFilename(path);
    String filename = origFilename.toLowerCase(Locale.ROOT);
    if (filename.endsWith(".gz")) {
      InputStream in = Files.newInputStream(path);
      try {
        return new GZIPInputStream(in, 65536);
      } catch (IOException ex) {
        // a close() failure here must not replace/discard the real diagnosis (e.g. "not a gzip file") with a
        // less useful one - preserve it as a suppressed exception, matching ZippedFileInputStream's own
        // close-on-failure convention
        try {
          in.close();
        } catch (IOException closeEx) {
          ex.addSuppressed(closeEx);
        }
        // this method's own pre-checks above (Files.exists()/isRegularFile()) both carefully name path, but
        // a malformed-gzip-header failure here didn't (round 25) - the same "failure path doesn't name its
        // input" shape rounds 9/14/15/17 all fixed elsewhere for copyUrlToFile()/decompressGzipInPlace()
        throw new IOException("Error opening " + path, ex);
      }
    } else if (filename.endsWith(".zip")) {
      origFilename = origFilename.substring(0, origFilename.length() - 4);
      try {
        return new ZippedFileInputStream(Files.newInputStream(path), origFilename);
      } catch (IOException | RuntimeException ex) {
        // ZippedFileInputStream's own constructor (findFile()) already closes the underlying stream on any
        // failure - no fd leak here (known non-issue #23) - but it can throw an unchecked
        // IllegalArgumentException (not just the checked FileNotFoundException/IOException), for an entry
        // name that's invalid UTF-8 with the UTF-8 (EFS) flag set, which escaped this method's own declared
        // "throws IOException" entirely, and neither failure named path - unlike this method's own
        // NoSuchFileException pre-checks a few lines above, and unlike the .gz branch's equivalent fix in
        // round 25 (round 26)
        throw new IOException("Error opening " + path, ex);
      }
    } else {
      return Files.newInputStream(path);
    }
  }

  /**
   * Opens an {@link Reader} to the specified file.
   * Automatically unwraps .gz or .zip files.
   * <p>
   * In zip files, this will look for a file minus the ".zip" extension (i.e. expect file named {@code foo.txt} if
   * zipped filename is {@code foo.txt.zip}.
   * <p>
   * The file's contents are decoded strictly as UTF-8: a byte sequence that isn't valid UTF-8 throws
   * {@link java.nio.charset.MalformedInputException} (an {@link IOException} subclass) from a read call on
   * the returned {@link BufferedReader}, rather than being silently replaced with the Unicode replacement
   * character.
   */
  public static BufferedReader openReader(Path path) throws IOException {
    // delegates to openInputStream() above for the actual file-existence/regular-file checks, filename-based
    // .gz/.zip detection, and exception wrapping/naming, instead of duplicating all of it here - only the
    // decoding step is specific to this method. A CharsetDecoder (not a bare Charset) is required so
    // malformed input is reported (throws MalformedInputException) rather than silently replacing malformed
    // bytes, matching Files.newBufferedReader()'s own strict decoding for a plain (uncompressed) file -
    // verified this still holds for that branch too via testPlainReaderRejectsMalformedUtf8
    return new BufferedReader(new InputStreamReader(openInputStream(path), StandardCharsets.UTF_8.newDecoder()));
  }


  /**
   * Copies contents of a {@code url} to a {@code file}.  If {@code file} already exists, it will be overwritten.
   * <p>
   * Use this instead of {@link FileUtils#copyURLToFile(URL, File)} or {@link IOUtils#copy(URL, File)}
   * when you need to follow redirects.
   * <p>
   * Bounded by an idle (stall) timeout, not a total-time deadline: as long as the download keeps making
   * progress, it can take arbitrarily long - only a server that stops sending data entirely for the timeout's
   * duration is treated as hung and aborted (this includes the initial wait for response headers, not just
   * stalls mid-body). "Progress" here means the destination file growing, so this does NOT cover the time
   * spent following a long chain of {@code 3xx} redirects (each hop returns no body of its own) - a chain
   * whose TOTAL time (not any single hop) exceeds the idle timeout fails as "no progress", even if every
   * individual hop answered promptly.
   * <p>
   * A {@code gzip} {@code Content-Encoding} response is transparently decoded - the saved file always
   * contains the original, uncompressed bytes.
   */
  public static void copyUrlToFile(String url, Path file) throws IOException {
    copyUrlToFile(url, file, Duration.ofSeconds(30));
  }

  /**
   * Package-private overload so tests can use a short idle timeout instead of waiting out the real 30s default.
   */
  static void copyUrlToFile(String url, Path file, Duration idleTimeout) throws IOException {

    // parse once, then dispatch on the parsed protocol (matching UrlUtils.isReachable(URL)'s own
    // sf_webSchemes.contains(url.getProtocol()) check) rather than a raw-string prefix check on `url` -
    // the parse below normalizes away both case ("HTTP://...") and leading/trailing whitespace
    // ("  http://..."), and UrlUtils.isValid() agrees both are valid http URLs, but a raw-string prefix
    // check doesn't recognize either, silently losing redirect-following/error-body-saving/the idle-timeout
    // watchdog below for a validly-cased-or-padded URL by falling into the generic branch meant for
    // FTP-like URLs.
    //
    // java.net.URL(String) is deprecated since JDK 20 in favor of URI(String).toURL() - see
    // UrlUtils.isValid() for the full rationale (round 30 finding 6). strip() and the literal-space-to-%20
    // substitution below restore (rather than silently drop) the two specific leniencies this method's own
    // callers were actually relying on from URL's own historically loose parsing: whitespace padding
    // (String.strip()'s Character.isWhitespace()-based rule and URL's own "<= ' '" tokenizer rule are NOT
    // identical - e.g. strip() also discards U+2003 EM SPACE, which URL's own tokenizer does not, and URL's
    // tokenizer also discards ASCII control characters like NUL, which strip() does not - round 31 finding 4
    // corrected this comment's earlier, inaccurate "not itself a behavior change" claim; the divergence is
    // real but limited to exotic control/Unicode-space codepoints no real caller has been found to rely on
    // either way) and an unencoded space in the path/query (a %20 substitution is always a safe, meaning-
    // preserving encoding for a literal space in any URI component). Scheme case-insensitivity doesn't need
    // special handling: URL.getProtocol() (via uri.toURL() below) already lowercases it the same way
    // constructing a URL directly always has
    URI uri;
    URL parsedUrl;
    try {
      uri = new URI(url.strip().replace(" ", "%20"));
      parsedUrl = uri.toURL();
    } catch (URISyntaxException | MalformedURLException | IllegalArgumentException ex) {
      // e.g. an unsupported protocol like "gopher://..." (URISyntaxException/MalformedURLException) - this
      // happens before the http/https-vs-generic branch below even exists, so both branches' "every failure
      // path names the url" convention depends on this shared prologue naming it too. Also catches
      // IllegalArgumentException: URI.toURL() throws this UNCHECKED (not MalformedURLException) for any
      // RELATIVE uri (no scheme at all, e.g. a config value missing its "http://" prefix, or "") - a real
      // regression introduced by round 30's migration off the deprecated URL(String) constructor, which used
      // to throw the CHECKED MalformedURLException("no protocol: ...") for this exact input instead (round
      // 31 finding 1) - must not let this undeclared RuntimeException escape past this method's own
      // documented "throws IOException", matching every other "unchecked exception must not escape" guard
      // already in this same method
      throw new IOException("Malformed URL: " + url, ex);
    }
    if ("http".equals(parsedUrl.getProtocol()) || "https".equals(parsedUrl.getProtocol())) {
      HttpRequest request;
      try {
        // no .timeout(idleTimeout) here - that bounds the wait for response headers specifically, which
        // would duplicate (and race with) the idle watchdog below, and on a timeout surfaces a raw,
        // uninformative HttpTimeoutException instead of the watchdog's friendly message. A stalled
        // pre-header wait is already "no progress" by the watchdog's own definition (Files.size(file)
        // stays unchanged/nonexistent), so it's already caught by that one mechanism alone
        // main's Apache HttpClient sent this by default and transparently decoded a compressed response;
        // java.net.http.HttpClient does neither on its own, so this is requested explicitly and decoded
        // below (see the Content-Encoding check after future.get()) to restore that behavior
        // no per-request .version(...) override needed here - buildHttpClient() already pins HTTP/1.1
        // client-wide (see its own comment for why that has to be client-wide, not just per-request)
        request = HttpRequest.newBuilder(uri)
            .header("Accept-Encoding", "gzip")
            .GET()
            .build();
      } catch (IllegalArgumentException ex) {
        // e.g. a hostless "http:///some/path" URI - toURI() above accepts it, but HttpRequest doesn't
        throw new IOException("Malformed URL: " + url, ex);
      }
      // built fresh per call (see buildHttpClient()'s own javadoc for why) and closed once this call is
      // done - close() blocks until any in-flight exchange completes, but by the time control reaches it
      // below, the future has always already resolved, thrown, or been cancelled by the watchdog, so this
      // never blocks on live work
      HttpClient client;
      try {
        // constructing an HttpClient can throw (wrapped in UncheckedIOException, e.g. Selector.open()
        // failing under fd exhaustion) - main's Apache builder never opened anything at build() time, so
        // this is a hazard specific to this per-call construction; must not escape past this method's
        // declared throws IOException the same way every other failure path here is caught and named.
        // Also catches IllegalArgumentException - connectTimeout(idleTimeout) rejects a non-positive
        // Duration (only reachable via the test-only idleTimeout overload, not the public 30s-default API)
        client = buildHttpClient(idleTimeout);
      } catch (UncheckedIOException ex) {
        throw new IOException("Error downloading " + url, ex.getCause());
      } catch (IllegalArgumentException ex) {
        throw new IOException("Error downloading " + url, ex);
      }
      try (client) {
        // save to file even if there's an error, so we can see what the error is. ofFile(Path) alone defaults
        // to CREATE, WRITE (no TRUNCATE_EXISTING) - explicit TRUNCATE_EXISTING is required so re-downloading a
        // shorter body into an existing, longer file doesn't leave the old file's tail bytes in place
        CompletableFuture<HttpResponse<Path>> future = client.sendAsync(request, HttpResponse.BodyHandlers.ofFile(
            file, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING));
        // idle/stall watchdog: java.net.http.HttpClient has no built-in read-timeout knob (unlike the
        // URLConnection-based FTP branch below, whose setReadTimeout() already bounds stalls, not total time),
        // so this polls the output file's size and cancels the download only if it stops growing for
        // idleTimeout - a slow-but-steadily-progressing download is never capped, no matter how long it takes
        // in total. This also covers a server that never responds at all: Files.size(file) keeps throwing
        // (the file doesn't exist yet) for as long as headers haven't arrived, which the poll loop below
        // treats identically to "size unchanged" - i.e. the same idle/no-progress condition, so the pre-header
        // wait is bounded by this one mechanism too, not a second, separate cap. Checking every idleTimeout
        // (rather than more finely) means worst-case detection latency is up to ~2x idleTimeout - an
        // acceptable trade-off for a hang-prevention safety net, not a precise deadline.
        ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(r -> {
          Thread t = new Thread(r, "copyUrlToFile-idle-watchdog");
          t.setDaemon(true);
          return t;
        });
        ScheduledFuture<?> watch;
        try {
          AtomicLong lastSize = new AtomicLong(-1);
          AtomicLong lastProgressNanos = new AtomicLong(System.nanoTime());
          long idleNanos = idleTimeout.toNanos();
          watch = watchdog.scheduleWithFixedDelay(() -> {
            long size;
            try {
              size = Files.size(file);
            } catch (IOException ex) {
              // file not created yet (still waiting on response headers) - not itself progress, but also not
              // yet actionable; the elapsed-time check below still applies using the last known state
              size = -1;
            }
            if (size != lastSize.get()) {
              lastSize.set(size);
              lastProgressNanos.set(System.nanoTime());
            } else if (System.nanoTime() - lastProgressNanos.get() > idleNanos) {
              future.cancel(true);
            }
          }, idleTimeout.toMillis(), idleTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RuntimeException ex) {
          // idleTimeout.toNanos() (ArithmeticException, an idleTimeout beyond ~292 years) and
          // scheduleWithFixedDelay() (IllegalArgumentException, a positive-but-sub-millisecond idleTimeout -
          // toMillis() truncates it to 0, which scheduleWithFixedDelay() rejects as non-positive) can both
          // throw unchecked here, after sendAsync() above has already dispatched the request - same
          // "must not escape this method's declared throws IOException" hazard as buildHttpClient()'s own
          // guard just above. The future is already in flight, so it must be explicitly cancelled here,
          // matching the InterruptedException catch below
          future.cancel(true);
          watchdog.shutdown();
          throw new IOException("Error downloading " + url, ex);
        }
        try {
          // no timeout here - only the watchdog above can end this early; a merely slow (not stalled)
          // download is meant to run for as long as it takes
          HttpResponse<Path> response = future.get();
          // RFC 7230 section 3.3.3: a response with BOTH Transfer-Encoding and Content-Length, or with
          // multiple DIFFERING Content-Length values, is invalid and MUST NOT have Content-Length trusted
          // for framing - but java.net.http.HttpClient's own Http1Response.fixupContentLen() does the exact
          // opposite (only consults Transfer-Encoding when Content-Length is ABSENT, and doesn't cross-check
          // multiple Content-Length values at all), so Content-Length silently wins/is picked arbitrarily,
          // truncating the file on disk while reporting a normal 200 - the only failure mode found across
          // this whole review series that silently corrupts the destination while reporting SUCCESS. Checked
          // before the gzip decode below - a response with ambiguous framing can't be trusted to have a
          // meaningful body to decode in the first place. Not a regression vs main (Apache HttpClient rejects
          // both shapes outright too), but a hazard specific to this branch's implementation
          List<String> transferEncodings = response.headers().allValues("Transfer-Encoding");
          String framingError = checkAmbiguousFraming(transferEncodings, response);
          if (framingError != null) {
            // matching the same "prefer the status if already non-200, preserve the diagnostic via
            // addSuppressed()" convention the Content-Encoding checks below already use (round 24) - this
            // check didn't follow it until round 25, so the status was discarded entirely rather than just
            // deprioritized
            if (response.statusCode() != 200) {
              IOException statusEx = new IOException("Error downloading " + url + ": " + response.statusCode());
              statusEx.addSuppressed(new IOException(framingError));
              throw statusEx;
            }
            throw new IOException("Error downloading " + url + ": " + framingError);
          }
          // RFC 7230 section 3.3.1: Transfer-Encoding codings are meant to be stripped by the immediate
          // recipient (this HTTP stack) as part of parsing the message itself - unlike Content-Encoding, an
          // end-to-end, application-level encoding this method explicitly decodes below. Neither this
          // branch's java.net.http.HttpClient nor main's Apache HttpClient implements that for anything other
          // than exactly one header line reading "chunked" (dechunking) - a shape this stack doesn't actually
          // recognize (e.g. a bare "Transfer-Encoding: gzip" with no "chunked" at all, "chunked;foo=bar", or
          // "chunked, chunked") leaves raw, un-de-transfer-coded bytes on disk with a normal 200 status - the
          // same silent "corrupted but reported as SUCCESS" shape as every check around this one, just at the
          // wire-framing layer instead of the Content-Encoding layer. main behaves identically for most of
          // these inputs (verified via a raw-socket capture, so this isn't a regression for those), but real
          // servers essentially never use anything but plain "chunked" in the first place (RFC 7230 requires
          // chunked to be the LAST coding applied if others are used, which neither client actually
          // implements support for) - cheap to reject outright rather than leave silently corrupted. Empty
          // response bodies are exempt, same rationale as the Content-Encoding checks below
          if (isUnsupportedTransferEncoding(transferEncodings) && Files.size(file) > 0) {
            // same status-preference convention as the framing check above (round 25)
            if (response.statusCode() != 200) {
              IOException statusEx = new IOException("Error downloading " + url + ": " + response.statusCode());
              statusEx.addSuppressed(new IOException("unsupported Transfer-Encoding " + transferEncodings));
              throw statusEx;
            }
            throw new IOException("Error downloading " + url + ": unsupported Transfer-Encoding "
                + transferEncodings);
          }
          // a server can send a gzip-encoded body even when it wasn't asked to (or regardless of what was
          // asked for) - decode it in place so the file on disk always matches what main's Apache HttpClient
          // would have written, not raw compressed bytes. Done BEFORE the status check below: main's Apache
          // HttpClient decoded gzip unconditionally (gated only on content-compression being enabled and a
          // non-empty entity, never on the status code), so an error response's body must be just as readable
          // as it was on main, not left undecoded just because it happens to accompany a non-200 status
          List<String> codings = listContentEncodingCodings(response);
          if (codings.size() == 1 && isGzipCoding(codings.get(0))) {
            try {
              decompressGzipInPlace(file);
            } catch (IOException ex) {
              // a proxy/CDN that blanket-adds Content-Encoding: gzip to a canned (non-gzip) error page is a
              // realistic way to hit both problems at once - if the status is already non-200, that's the more
              // diagnostically useful thing to report (naming the url and status, matching every other failure
              // path in this method) rather than a bare, url-less decode exception; the decode failure itself
              // is preserved as a suppressed exception, not discarded
              if (response.statusCode() != 200) {
                IOException statusEx = new IOException("Error downloading " + url + ": " + response.statusCode());
                statusEx.addSuppressed(ex);
                throw statusEx;
              }
              throw new IOException("Error downloading " + url, ex);
            }
          } else if (!codings.isEmpty() && Files.size(file) > 0) {
            // a server can send whatever Content-Encoding it wants regardless of what was requested (the
            // same premise listContentEncodingCodings()'s own javadoc relies on for gzip) - main's Apache
            // HttpClient also decoded "deflate" (round 15 deliberately didn't extend gzip-detection to it,
            // since this method only ever requests "Accept-Encoding: gzip"), so a server sending deflate (or
            // any other coding this method doesn't recognize) anyway would otherwise leave raw, undecodable
            // compressed bytes on disk with a normal 200 status. The same is true of MULTIPLE stacked codings
            // even when one of them is "gzip" (e.g. "deflate, gzip", or even "gzip, gzip" twice) - a single
            // decompressGzipInPlace() pass can only undo ONE layer, so the gzip-decode branch above
            // deliberately requires EXACTLY one recognized coding before taking that path; anything else falls
            // through here and must fail loudly rather than leave a still-partially- (or, for "gzip, gzip",
            // still fully-) compressed file on disk. Empty response bodies are exempt, matching
            // decompressGzipInPlace()'s own zero-size guard (see its javadoc) - a Content-Encoding header on
            // an empty body has nothing to actually decode either way. Fail loudly, naming the coding(s),
            // matching the same "prioritize the status if already non-200" convention as the gzip-decode-
            // failure path above
            String description = codings.size() == 1 ? "\"" + codings.get(0) + "\"" : codings.toString();
            if (response.statusCode() != 200) {
              IOException statusEx = new IOException("Error downloading " + url + ": " + response.statusCode());
              statusEx.addSuppressed(new IOException("unrecognized Content-Encoding " + description));
              throw statusEx;
            }
            throw new IOException("Error downloading " + url + ": unrecognized Content-Encoding " + description);
          }
          if (response.statusCode() != 200) {
            throw new IOException("Error downloading " + url + ": " + response.statusCode());
          }
        } catch (InterruptedException ex) {
          future.cancel(true);
          Thread.currentThread().interrupt();
          throw new IOException("Interrupted while downloading " + url, ex);
        } catch (CancellationException ex) {
          // the watchdog cancelled the future above after detecting a stall
          throw new IOException("Timed out downloading " + url + " (no progress for " + idleTimeout + ")", ex);
        } catch (ExecutionException ex) {
          throw mapDownloadExecutionCause(url, idleTimeout, ex.getCause());
        } finally {
          watch.cancel(true);
          watchdog.shutdown();
        }
      }
    } else {
      // every failure path in the http/https branch above names the url ("Error downloading <url>", etc.) -
      // this generic (e.g. ftp/file) branch must follow the same convention rather than let a bare
      // ConnectException/FileNotFoundException/etc. (which commonly carry little or no message of their own)
      // escape unnamed
      try {
        URLConnection conn = parsedUrl.openConnection();
        // bound how long a stalled/unresponsive server can hang the calling thread - setReadTimeout() is
        // already a genuine idle timeout (each individual read() is bounded, but the total transfer isn't
        // capped as long as data keeps arriving), matching the semantics of the HTTP branch's watchdog above.
        // A positive-but-sub-millisecond idleTimeout truncates to 0 here, and URLConnection.setConnectTimeout(0)/
        // setReadTimeout(0) are documented as INFINITE, not "as fast as possible" - silently disabling the very
        // guarantee this idleTimeout parameter exists to provide, the opposite of what a caller passing a very
        // short timeout would expect. Only reachable via the test-only idleTimeout overload (round 22 finding 2
        // fixed the equivalent input for the http/https branch's watchdog setup; this closes the sibling gap)
        int idleMillis = Math.toIntExact(idleTimeout.toMillis());
        if (idleMillis <= 0) {
          throw new IllegalArgumentException("idleTimeout too small to enforce a timeout: " + idleTimeout);
        }
        conn.setConnectTimeout(idleMillis);
        conn.setReadTimeout(idleMillis);
        try (InputStream in = conn.getInputStream();
             OutputStream out = Files.newOutputStream(file)) {
          IOUtils.copy(in,out);
        }
      } catch (IOException | RuntimeException ex) {
        // RuntimeException too, not just IOException - e.g. an out-of-range port (Socket's constructor
        // throws an unchecked IllegalArgumentException) or an idleTimeout over ~24.8 days
        // (Math.toIntExact() above throws ArithmeticException) must not escape this method's declared
        // throws IOException unwrapped and unnamed, matching the http/https branch's own
        // mapDownloadExecutionCause()-based guard against undeclared RuntimeExceptions
        throw new IOException("Error downloading " + url, ex);
      }
    }
  }

  /**
   * Maps an {@link ExecutionException}'s cause (from {@code future.get()} in {@link #copyUrlToFile}) to the
   * {@link IOException} that should be thrown for it, following the same "every failure path names url"
   * convention every other branch in that method uses.
   * <p>
   * {@link Error}s are rethrown as-is, never wrapped: {@link CompletableFuture#get()} wraps ANY
   * {@link Throwable} that completed the future exceptionally in an {@code ExecutionException}, including a
   * JVM-fatal-adjacent condition like {@link OutOfMemoryError} surfacing from inside the JDK's async HTTP
   * pipeline - wrapping that in an {@code IOException} would let a caller written as
   * {@code catch (IOException e) { retry/log/skip }} silently absorb it as an ordinary, retryable download
   * failure. {@code main}'s synchronous Apache HttpClient never had this hazard - a call that threw an
   * {@code Error} simply propagated it.
   * <p>
   * Package-private (rather than private) so tests can exercise it directly.
   */
  static IOException mapDownloadExecutionCause(String url, Duration idleTimeout, Throwable cause) {
    // this JDK's sendAsync()-returned future doesn't surface cancel(true) as a direct CancellationException
    // from get() - it wraps one inside an ExecutionException instead, so this must be checked before the
    // generic fallback below (which would otherwise wrap the CancellationException verbatim, bypassing the
    // informative message here)
    if (cause instanceof CancellationException) {
      return new IOException("Timed out downloading " + url + " (no progress for " + idleTimeout + ")", cause);
    }
    if (cause instanceof IOException ioCause) {
      // e.g. java.net.ConnectException/UnresolvedAddressException-wrapping - these commonly carry no
      // message of their own, so rethrowing verbatim (as before) could surface a completely blank
      // exception naming neither the url nor the underlying problem; wrap with the same url-naming
      // convention every other failure path in this method already follows
      return new IOException("Error downloading " + url, ioCause);
    }
    if (cause instanceof UncheckedIOException uncheckedCause) {
      // the JDK's own redirect-following internals wrap some IOExceptions in this rather than throwing
      // them directly (e.g. an empty/missing redirect Location) - unwrap so the reported cause is the
      // real IOException, not an extra layer of indirection
      return new IOException("Error downloading " + url, uncheckedCause.getCause());
    }
    if (cause instanceof Error err) {
      throw err;
    }
    // deliberately no special case for RuntimeException here (there used to be one, rethrowing it verbatim) -
    // a malformed redirect Location (e.g. a hostless "http:///path") makes the JDK's internals throw an
    // unchecked IllegalArgumentException/NullPointerException while building the follow-up request, same
    // shape as the IllegalArgumentException the initial request already guards against a few lines above in
    // copyUrlToFile() - rethrowing it bare would let an undeclared RuntimeException escape past this method's
    // documented "throws IOException", unlike every other failure path here (and unlike main's Apache
    // HttpClient, which always threw IOException for this)
    return new IOException("Error downloading " + url, cause);
  }

  /**
   * Lists every non-{@code identity} coding named across a response's {@code Content-Encoding} header(s), in
   * the order they appear.
   * <p>
   * Per RFC 7231 section 3.1.2.2, {@code Content-Encoding} may be a comma-separated list of codings, each
   * optionally followed by a {@code ;param=value} suffix - an exact, whole-header match against {@code "gzip"}
   * misses both that list form and the {@code "x-gzip"} legacy alias, which main's Apache HttpClient decoded
   * identically to {@code "gzip"} (see {@code ResponseContentEncoding}'s default codec registry).
   * <p>
   * Per RFC 7230 section 3.2.2, multiple header lines with the same field name are also semantically
   * identical to one comma-joined line - {@code allValues(...)} (not just {@code firstValue(...)}) must be
   * checked so a repeated {@code Content-Encoding} line isn't missed.
   * <p>
   * An empty result means there's nothing to decode (no header, an {@code identity}-only header, or a blank
   * value). The call site requires EXACTLY one recognized ({@link #isGzipCoding}) entry before taking the
   * gzip-decode path - a single {@link #decompressGzipInPlace} pass can only undo one layer, so a list
   * containing more than one coding (even "gzip, gzip", or "deflate, gzip") must fall through to the
   * unrecognized-coding failure path instead of being silently (mis)treated as plain gzip.
   */
  private static List<String> listContentEncodingCodings(HttpResponse<Path> response) {
    List<String> codings = new ArrayList<>();
    for (String header : response.headers().allValues("Content-Encoding")) {
      for (String coding : header.split(",")) {
        int paramIdx = coding.indexOf(';');
        String name = (paramIdx < 0 ? coding : coding.substring(0, paramIdx)).strip();
        if (!name.isEmpty() && !name.equalsIgnoreCase("identity")) {
          codings.add(name);
        }
      }
    }
    return codings;
  }

  /**
   * Checks whether a single {@code Content-Encoding} coding (as returned by {@link #listContentEncodingCodings})
   * is one {@link #decompressGzipInPlace} can decode.
   */
  private static boolean isGzipCoding(String coding) {
    return coding.equalsIgnoreCase("gzip") || coding.equalsIgnoreCase("x-gzip");
  }

  /**
   * Returns whether {@code transferEncodings} (a response's {@code Transfer-Encoding} header values, per
   * {@link HttpResponse.ResponseInfo#headers()}) are exactly the one shape this HTTP stack's own
   * {@code jdk.internal.net.http.Http1Response.fixupContentLen()} actually dechunks: a SINGLE header line
   * whose value, verbatim (read directly from that method's source - it does a WHOLE-STRING match, not a
   * per-coding comma-split), stripped, case-insensitively equals {@code "chunked"}. Anything else - multiple
   * header lines (even duplicate/equivalent ones, which RFC 7230 section 3.3.1 forbids applying
   * {@code chunked} more than once for anyway), a value with trailing parameters (e.g.
   * {@code "chunked;foo=bar"}), or a value that's actually a comma-separated list (e.g.
   * {@code "chunked, chunked"} or {@code "identity, chunked"}) - makes the JDK instead read the body until
   * connection close WITHOUT dechunking it, leaving literal chunk-envelope bytes on disk if the wire body was
   * actually chunk-framed - unlike {@link #listContentEncodingCodings}, which legitimately comma-splits/
   * param-strips its value for {@code Content-Encoding}, {@code Transfer-Encoding} chunked-detection isn't a
   * coding LIST at all from the JDK's point of view, just one literal string compared as a whole.
   */
  private static boolean isPlainChunkedTransferEncoding(List<String> transferEncodings) {
    return transferEncodings.size() == 1 && transferEncodings.get(0).strip().equalsIgnoreCase("chunked");
  }

  /**
   * Returns whether {@code transferEncodings} are something other than absent, exactly one
   * {@link #isPlainChunkedTransferEncoding} line, exactly one EMPTY line (not a legal RFC 7230 value at all,
   * but treated as equivalent to absent), or exactly one line reading {@code "identity"} (a no-op
   * coding - not itself registered by RFC 7230, which de-registered it, but RFC 2616 section 3.6 explicitly
   * permitted it and main's Apache HttpClient still recognizes and correctly leaves undecoded, so treated as
   * safe here too, matching {@link #checkAmbiguousFraming}'s own treatment of it). Both the empty-line and
   * {@code "identity"} exemptions are correct only when the wire body genuinely isn't chunk-framed - a
   * self-contradictory server sending either value alongside an actually chunk-framed body still gets its
   * literal chunk envelope written to disk with a normal 200 status; this matches existing behavior (verified
   * against Apache HttpClient 4.5.14's own {@code LaxContentLengthStrategy}), zero real exposure, and a
   * deliberate leniency choice, not an oversight. See the call site's own comment for why anything else means
   * this HTTP stack can't be trusted to have handed back a correctly de-transfer-coded body.
   */
  private static boolean isUnsupportedTransferEncoding(List<String> transferEncodings) {
    if (transferEncodings.isEmpty() || isPlainChunkedTransferEncoding(transferEncodings)) {
      return false;
    }
    if (transferEncodings.size() == 1 && transferEncodings.get(0).strip().isEmpty()) {
      // an empty value isn't a legal RFC 7230 Transfer-Encoding at all (its own grammar -
      // "1#transfer-coding" - requires at least one coding), but both this stack and main's Apache
      // HttpClient already deliver the body correctly for it WHEN THE WIRE BODY GENUINELY ISN'T CHUNK-FRAMED
      // (treated the same as an absent header, not as a genuinely unrecognized coding like
      // "gzip"/"chunked;a=b") - rejecting it was stricter than necessary for that (overwhelmingly common)
      // case, and its own rejection message rendered misleadingly (List.of("").toString() prints "[]",
      // identical to an empty list) (round 26). A self-contradictory server that sends this empty value
      // alongside a body that IS actually chunk-framed still gets its chunk envelope written raw to disk with
      // a normal 200 status - same caveat, and same main-parity, as "identity"'s exemption below in
      // checkAmbiguousFraming() (round 27) - a deliberate, already-decided leniency tradeoff, not reversed
      // here, just documented precisely rather than as an unconditional "delivers the body correctly" claim
      return false;
    }
    return transferEncodings.size() != 1 || !transferEncodings.get(0).strip().equalsIgnoreCase("identity");
  }

  /**
   * Checks a response for the two RFC 7230 section 3.3.3 framing-ambiguity shapes {@code fixupContentLen()}
   * doesn't itself guard against (see the call site's own comment for why this matters) - returns a
   * human-readable description of the problem, or {@code null} if framing is unambiguous.
   */
  private static @Nullable String checkAmbiguousFraming(List<String> transferEncodings, HttpResponse<Path> response) {
    List<String> contentLengths = response.headers().allValues("Content-Length");
    // a genuinely PLAIN CHUNKED Transfer-Encoding conflicts with Content-Length directly (the JDK dechunks
    // it, making Content-Length redundant/stale framing information); an UNSUPPORTED Transfer-Encoding
    // (isUnsupportedTransferEncoding() - anything that isn't plain chunked and isn't the "identity" no-op)
    // conflicts with it too, just less directly: the JDK does NOT dechunk it, so it falls back to reading
    // raw bytes framed by Content-Length instead - meaning a genuinely chunk-framed wire body (e.g.
    // "Transfer-Encoding: chunked;a=b", which fails the JDK's whole-string "chunked" match) gets silently
    // reframed by whatever Content-Length claims (even "Content-Length: 0"), NOT by the real chunk envelope,
    // corrupting the file on disk while reporting a normal 200 status (round 26 finding 2: round 25's
    // narrowing from "any Transfer-Encoding" down to "only plain chunked" went one step too far and exempted
    // this case too, along with "identity", which genuinely doesn't conflict). Only "identity" (a true no-op
    // coding that leaves Content-Length as the sole, authoritative length) must NOT trip this check (round
    // 25: this used to disagree with isUnsupportedTransferEncoding() above, which already treated "identity"
    // as safe - a response with "Transfer-Encoding: identity" plus a correct Content-Length was being
    // rejected here even though the file on disk was entirely correct FOR A NON-CHUNK-FRAMED BODY, verified
    // against both this stack and main. That qualifier matters (round 27): a self-contradictory server that
    // sends "identity" (or, since round 26, an empty value) alongside a body that IS actually chunk-framed
    // still gets its literal chunk envelope written to disk with a normal 200 status either way - same
    // main-parity, zero-real-exposure, deliberately-chosen leniency tradeoff as isUnsupportedTransferEncoding()'s
    // own exemptions above, not reversed here, just documented precisely rather than as an unconditional
    // "delivers the body correctly" claim)
    if ((isPlainChunkedTransferEncoding(transferEncodings) || isUnsupportedTransferEncoding(transferEncodings))
        && !contentLengths.isEmpty()) {
      return "response has both Transfer-Encoding and Content-Length headers (ambiguous framing)";
    }
    // RFC 7230 section 3.3.2 explicitly permits multiple Content-Length header fields whose field-values are
    // the same DECIMAL VALUE (e.g. "32" and "032") - comparing the raw strings would wrongly reject that
    // legal, unambiguous case as if it were framing-ambiguous, so parse each value before de-duplicating. A
    // value that isn't even a valid decimal number is itself a framing problem, not something to silently pass
    // through
    Set<Long> distinctLengths = new HashSet<>();
    for (String contentLength : contentLengths) {
      try {
        distinctLengths.add(Long.parseLong(contentLength.strip()));
      } catch (NumberFormatException ex) {
        return "response has a malformed Content-Length header \"" + contentLength + "\" (ambiguous framing)";
      }
    }
    if (distinctLengths.size() > 1) {
      return "response has multiple differing Content-Length headers " + contentLengths + " (ambiguous framing)";
    }
    return null;
  }

  /**
   * Gunzips {@code file} in place: decompresses it into a separate temp file, then writes that temp file's
   * content back into {@code file} only once decompression fully succeeds - if the claimed-gzip body is
   * actually malformed (e.g. truncated, or not gzip at all despite the response's Content-Encoding header),
   * the original downloaded bytes are left in {@code file} untouched instead of being destroyed, matching
   * this method's own "save to file even if there's an error, so we can see what the error is" principle for
   * a non-200 status.
   * <p>
   * The final step writes THROUGH {@code file}'s existing identity (opening it directly and truncating,
   * which follows a symlink to its real target the same way {@link Files#newInputStream} already does for
   * this method's initial read - so if {@code file} is a symlink, the symlink itself is left untouched and
   * its real target ends up holding the decompressed content) rather than replacing it via
   * {@link Files#move}/{@link Files#copy} with {@code REPLACE_EXISTING} - both of those unlink-and-recreate
   * rather than truncate-in-place (verified empirically), which would silently reset a pre-existing
   * destination's permissions/ownership to the process's defaults, orphan any other name hard-linked to it,
   * AND (unlike a plain open, which follows a symlink) replace the symlink itself with a new regular file.
   * Writing in place isn't atomic against a concurrent reader of {@code file} or
   * against an I/O failure mid-write (unlike a swap, a failure here can leave {@code file} partially
   * overwritten) - but {@code main}'s original behavior (decoding gzip on the fly straight into the
   * destination while downloading) was never atomic either, and the specific failure mode this method exists
   * to guard against - a MALFORMED gzip body - is still fully caught before {@code file} is ever touched,
   * by the decompress-to-temp-file step below.
   * <p>
   * The temp file is created via {@link Files#createTempFile(String, String)} (a random name in the JVM's
   * default temp directory) rather than a PREDICTABLE path derived from {@code file}'s own name (e.g. the
   * previous {@code "<file>.decompressed.tmp"}) - a predictable, attacker-writable-directory-adjacent path
   * can be pre-planted as a symlink, which {@code CREATE + WRITE + TRUNCATE_EXISTING} would follow, writing
   * the downloaded payload through it onto an arbitrary file the process can write to (the symlink is then
   * deleted, leaving no trace - CWE-59/CWE-377); can silently destroy an unrelated pre-existing file already
   * occupying that exact path; and its length depends on {@code file}'s own filename, so a long enough
   * destination filename plus the temp suffix can exceed the filesystem's {@code NAME_MAX}, failing an
   * otherwise-successful download. A random, destination-independent temp path closes all three. It also
   * FULLY delivers the "no parent-directory write permission needed" property the write-in-place final step
   * above was already designed around - the previous sibling-path temp file still needed that permission
   * just to be created, even though neither the final write-through step nor this method's own initial read
   * of {@code file} ever did.
   * <p>
   * A zero-length {@code file} is left as-is rather than fed to {@link GZIPInputStream} (which would throw
   * {@link java.io.EOFException} for an empty stream) - matching how {@code main}'s Apache HttpClient
   * skipped decoding entirely for a zero-length entity, since there's nothing to decode either way.
   * <p>
   * Package-private (rather than private) so tests can exercise it directly.
   */
  static void decompressGzipInPlace(Path file) throws IOException {
    if (Files.size(file) == 0) {
      return;
    }
    Path decompressed = Files.createTempFile("pgkb-common-decompress-", ".tmp");
    try {
      // Files.newInputStream(file) is deliberately NOT itself a try-with-resources resource here: if the
      // GZIPInputStream constructor below throws (e.g. a malformed 2-byte magic header), the raw stream it
      // was constructing from would never be assigned to a resource variable and so would never be closed -
      // matching the leak-avoidance pattern openInputStream()/openReader() above already use for the same
      // GZIPInputStream-construction-can-throw hazard.
      InputStream fileIn = Files.newInputStream(file);
      GZIPInputStream in;
      try {
        in = new GZIPInputStream(fileIn);
      } catch (IOException ex) {
        // see openInputStream() above for why a close() failure here must be suppressed, not swap out ex
        try {
          fileIn.close();
        } catch (IOException closeEx) {
          ex.addSuppressed(closeEx);
        }
        throw ex;
      }
      try (in; OutputStream out = Files.newOutputStream(decompressed, StandardOpenOption.WRITE,
          StandardOpenOption.TRUNCATE_EXISTING)) {
        IOUtils.copy(in, out);
      }
      try (InputStream decompressedIn = Files.newInputStream(decompressed);
           OutputStream out = Files.newOutputStream(file, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
        IOUtils.copy(decompressedIn, out);
      }
    } finally {
      // best-effort cleanup only - a failure here must not mask whatever exception (if any) the try block
      // above already threw, nor cause this method to report failure for an operation that actually
      // succeeded; an orphaned temp file in the system temp directory is a minor housekeeping concern the
      // OS/JVM's own temp-cleanup already handles
      try {
        Files.deleteIfExists(decompressed);
      } catch (IOException ignored) {
        // best-effort - see above
      }
    }
  }


  /**
   * Calculates md5 hash for a file.
   */
  public static byte[] md5(Path file) throws IOException {
    try (InputStream is = Files.newInputStream(file)) {
      return DigestUtils.md5(is);
    } catch (IOException ex) {
      // e.g. a directory (Files.newInputStream() throws a bare "Is a directory" with no path at all) -
      // must name the file, matching every other failure path in this class (round 25)
      throw new IOException("Error reading " + file, ex);
    }
  }

  /**
   * Calculates md5 hash for a file and return its Base64 representation.
   */
  public static String md5InBase64(Path file) throws IOException {
    return Base64.getEncoder().encodeToString(md5(file));
  }

  /**
   * Calculates md5 hash for the {@code data} and return its Base64 representation.
   */
  public static String md5InBase64(byte[] data) {
    return Base64.getEncoder().encodeToString(DigestUtils.md5(data));
  }
}
