package org.pharmgkb.common.util;

import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;


/**
 * A {@link CookieStore} decorator that stores host-only cookies (an {@link HttpCookie} with a {@code null}
 * domain - see {@code StreamUtils.shouldAcceptCookie}) in a side table keyed by request host, instead of
 * handing them to {@code delegate}.
 * <p>
 * {@link HttpCookie#equals(Object)} considers only name, domain, and path - never the host a cookie was set
 * from or is being matched against - so two DIFFERENT hosts' host-only cookies that happen to share a name
 * and path are indistinguishable to {@code equals()}. The JDK's own {@code java.net.InMemoryCookieStore}
 * (read directly from JDK 21 source) relies on {@code equals()} both to de-duplicate on {@code add()}
 * ({@code cookieJar.remove(cookie)}, run unconditionally before a new cookie is added or an existing one
 * deleted via {@code Max-Age=0}) and to look up on {@code remove()} - so once two unrelated hosts' cookies
 * are stored with the same {@code (name, null, path)} identity, adding or clearing one silently evicts the
 * other, regardless of which host it actually belongs to (verified via a real {@code CookieManager}/
 * {@code InMemoryCookieStore} round-trip).
 * <p>
 * This decorator sidesteps the problem by never handing a host-only cookie to {@code delegate} at all -
 * {@code delegate}'s own domain-matching machinery is only needed for a cookie that HAS a domain, so every
 * domain cookie is delegated unchanged (subdomain replay, RFC 2965-vs-Netscape dispatch, etc. all still work
 * exactly as {@code delegate} implements them standalone), while every host-only cookie is tracked
 * separately, keyed by the exact (lowercased) request host it was added for. This mirrors, but does not
 * reuse, {@code InMemoryCookieStore.getEffectiveURI()}'s own host-only key shape (host alone, no path or
 * scheme) - which is itself why the JDK's own {@code uriIndex}-based host-only retrieval doesn't path-match
 * either; replicating that (rather than inventing a stricter path-match this decorator's domain-cookie side
 * doesn't have either) keeps behavior consistent between the two paths.
 *
 * @author Mark Woon
 */
class HostOnlyCookieStore implements CookieStore {
  private final CookieStore m_delegate;
  private final Map<String, List<HttpCookie>> m_hostOnly = new HashMap<>();
  private final ReentrantLock m_lock = new ReentrantLock();


  HostOnlyCookieStore(CookieStore delegate) {
    m_delegate = delegate;
  }


  @Override
  public void add(URI uri, HttpCookie cookie) {
    if (cookie.getDomain() != null || uri == null || uri.getHost() == null) {
      m_delegate.add(uri, cookie);
      return;
    }
    String host = uri.getHost().toLowerCase(Locale.ROOT);
    m_lock.lock();
    try {
      List<HttpCookie> cookies = m_hostOnly.computeIfAbsent(host, h -> new ArrayList<>());
      // matches InMemoryCookieStore.add()'s own unconditional de-dup-then-conditionally-re-add shape, scoped
      // to this one host's own list instead of the JDK's single global cookieJar
      cookies.remove(cookie);
      if (cookie.getMaxAge() != 0) {
        cookies.add(cookie);
      }
      pruneIfEmpty(host, cookies);
    } finally {
      m_lock.unlock();
    }
  }


  /**
   * Removes {@code host}'s own entry from {@link #m_hostOnly} if {@code cookies} (that same entry's value) is
   * now empty - so a host with zero actual host-only cookies left doesn't linger in {@link #getURIs()}. This
   * is EAGERER than {@code InMemoryCookieStore.getURIs()}'s own pruning (verified directly against JDK
   * source): the JDK only prunes a {@code uriIndex} entry lazily, the next time {@code get(URI)}
   * happens to touch it - {@code add()} with {@code Max-Age=0}, {@code remove()}, and {@code getCookies()}'s
   * own expiry sweep all drop the cookie from {@code cookieJar} but leave the dangling index entry in place,
   * so the JDK's own {@code getURIs()} keeps reporting a host with zero live cookies until the next {@code
   * get()} call happens to visit it - only "never successfully added at all" is pruned immediately by both.
   * Calling this eagerly at every other mutation site that touches {@link #m_hostOnly} (rather than only
   * from {@code get()}, matching the JDK's own laziness) is a deliberate, stricter choice - {@link
   * #getCookies()} achieves the same effect inline instead of calling this method, since it's already
   * iterating {@link #m_hostOnly} directly, and {@link #removeAll()} has no need for it at all, since it
   * clears {@link #m_hostOnly} wholesale rather than per-host. It costs nothing extra
   * ({@code cookies.isEmpty()} is O(1)) and {@link #getURIs()} isn't reachable from any real caller of this
   * package-private class anyway (only ever wrapped in a per-call {@code CookieManager} that calls just
   * {@code add()}/{@code get()}), so being more correct than the JDK here has no downside. Must be called
   * with {@link #m_lock} already held.
   */
  private void pruneIfEmpty(String host, List<HttpCookie> cookies) {
    if (cookies.isEmpty()) {
      m_hostOnly.remove(host);
    }
  }


  @Override
  public List<HttpCookie> get(URI uri) {
    if (uri == null) {
      throw new NullPointerException("uri is null");
    }
    List<HttpCookie> result = new ArrayList<>(m_delegate.get(uri));
    if (uri.getHost() != null) {
      boolean secureLink = "https".equalsIgnoreCase(uri.getScheme());
      String host = uri.getHost().toLowerCase(Locale.ROOT);
      m_lock.lock();
      try {
        List<HttpCookie> cookies = m_hostOnly.get(host);
        if (cookies != null) {
          Iterator<HttpCookie> it = cookies.iterator();
          while (it.hasNext()) {
            HttpCookie c = it.next();
            if (c.hasExpired()) {
              it.remove();
              continue;
            }
            if (secureLink || !c.getSecure()) {
              result.add(c);
            }
          }
          pruneIfEmpty(host, cookies);
        }
      } finally {
        m_lock.unlock();
      }
    }
    return Collections.unmodifiableList(result);
  }


  @Override
  public List<HttpCookie> getCookies() {
    List<HttpCookie> result = new ArrayList<>(m_delegate.getCookies());
    m_lock.lock();
    try {
      Iterator<Map.Entry<String, List<HttpCookie>>> hostEntries = m_hostOnly.entrySet().iterator();
      while (hostEntries.hasNext()) {
        Map.Entry<String, List<HttpCookie>> entry = hostEntries.next();
        List<HttpCookie> cookies = entry.getValue();
        cookies.removeIf(HttpCookie::hasExpired);
        result.addAll(cookies);
        if (cookies.isEmpty()) {
          hostEntries.remove();
        }
      }
    } finally {
      m_lock.unlock();
    }
    return Collections.unmodifiableList(result);
  }


  @Override
  public List<URI> getURIs() {
    // a LinkedHashSet (not the ArrayList this decorator's other list-returning methods use) so a host present
    // in BOTH the delegate's own getURIs() (holding a domain cookie) and this decorator's own host-only table
    // (round 33 finding 4) is reported once, matching InMemoryCookieStore.getURIs()'s own uriIndex-keySet-
    // based de-duplication
    LinkedHashSet<URI> uris = new LinkedHashSet<>(m_delegate.getURIs());
    m_lock.lock();
    try {
      for (String host : m_hostOnly.keySet()) {
        uris.add(URI.create("http://" + host));
      }
    } finally {
      m_lock.unlock();
    }
    return Collections.unmodifiableList(new ArrayList<>(uris));
  }


  @Override
  public boolean remove(URI uri, HttpCookie cookie) {
    // harmless to also ask the delegate even for an ordinary host-only cookie (domain == null) - it was never
    // added there in the first place (add() only delegates a null-domain cookie for the corner case of a null
    // uri/host, which this method's own guard below then also routes through unchanged - round 33 finding 5
    // corrected this comment's earlier, too-broad "never added to it" claim), so this is simply a harmless
    // no-op miss otherwise, rather than needing to branch on cookie.getDomain() == null the same way add() does
    boolean removedFromDelegate = m_delegate.remove(uri, cookie);
    if (uri == null || uri.getHost() == null) {
      return removedFromDelegate;
    }
    String host = uri.getHost().toLowerCase(Locale.ROOT);
    m_lock.lock();
    try {
      List<HttpCookie> cookies = m_hostOnly.get(host);
      boolean removedFromHostOnly = cookies != null && cookies.remove(cookie);
      if (cookies != null) {
        pruneIfEmpty(host, cookies);
      }
      return removedFromDelegate || removedFromHostOnly;
    } finally {
      m_lock.unlock();
    }
  }


  @Override
  public boolean removeAll() {
    boolean delegateRemoved = m_delegate.removeAll();
    m_lock.lock();
    try {
      boolean hadHostOnly = !m_hostOnly.isEmpty();
      m_hostOnly.clear();
      return delegateRemoved || hadHostOnly;
    } finally {
      m_lock.unlock();
    }
  }
}
