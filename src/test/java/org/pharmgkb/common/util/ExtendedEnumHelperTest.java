package org.pharmgkb.common.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


/**
 * This is a JUnit test for {@link ExtendedEnumHelper}.
 *
 * @author Mark Woon
 */
class ExtendedEnumHelperTest {


  @Test
  void testCamelCaseFormat() {

    assertEquals("helloWorld", ExtendedEnumHelper.camelCaseFormat("Hello World"));
    assertEquals("helloWorld", ExtendedEnumHelper.camelCaseFormat("Hello, World"));
    assertEquals("helloWorld", ExtendedEnumHelper.camelCaseFormat("Hello, World!"));
    assertEquals("helloWorld", ExtendedEnumHelper.camelCaseFormat(" Hello - World"));
    try {
      ExtendedEnumHelper.camelCaseFormat("!!!");
      fail("Empty string");
    } catch (IllegalArgumentException ex) {
      // expected
    }
  }

  @Test
  void testCamelCaseFormatIsLocaleIndependent() {
    // toLowerCase() must not depend on the JVM's default locale - under Turkish/Azeri locales,
    // "IDENTIFIER".toLowerCase() produces "ıdentıfıer" (dotless i), not "identifier"
    Locale defaultLocale = Locale.getDefault();
    try {
      Locale.setDefault(new Locale("tr", "TR"));
      assertEquals("identifierTag", ExtendedEnumHelper.camelCaseFormat("IDENTIFIER TAG"));
    } finally {
      Locale.setDefault(defaultLocale);
    }
  }

  @Test
  void testLookupByNameIsLocaleIndependent() {
    // the case-insensitive name lookup must not depend on the JVM's default locale - under Turkish/Azeri
    // locales, "ID".toLowerCase() produces "ıd" (dotless i), which wouldn't match "id".toLowerCase()
    Locale defaultLocale = Locale.getDefault();
    try {
      Locale.setDefault(new Locale("tr", "TR"));
      ExtendedEnumHelper<Foo> enumHelper = new ExtendedEnumHelper<>(Foo.class);
      enumHelper.add(Foo.ONE, 1, "ID", "ID", "TAG");

      assertEquals(Foo.ONE, enumHelper.lookupByName("id"));
      assertEquals(Foo.ONE, enumHelper.lookupByName("tag"));
    } finally {
      Locale.setDefault(defaultLocale);
    }
  }

  @Test
  void testGetAllSorted() {
    ExtendedEnumHelper<Hero> helper = ExtendedEnumHelper.getExtendedEnumHelper(Hero.class);
    assertArrayEquals(
        new Hero[] {Hero.WonderWoman, Hero.Superman, Hero.Batman},
        helper.getAllSortedById().toArray(new Hero[0])
    );
    assertArrayEquals(
        new Hero[] {Hero.Batman, Hero.Superman, Hero.WonderWoman},
        helper.getAllSortedByName().toArray(new Hero[0])
    );
  }

  @Test
  void testGetAllSortedReturnsUnmodifiableCollection() {
    // a caller calling .clear() (or any other mutator) on the returned collection must not corrupt the
    // actual registry - the returned collection may still reflect future add() calls (it's a cached
    // unmodifiable wrapper around the live map view, not a copy), but it must reject direct mutation
    ExtendedEnumHelper<Hero> helper = ExtendedEnumHelper.getExtendedEnumHelper(Hero.class);

    assertThrows(UnsupportedOperationException.class, () -> helper.getAllSortedById().clear());
    assertThrows(UnsupportedOperationException.class, () -> helper.getAllSortedByName().clear());
    // the registry itself must be unaffected by the attempted (and rejected) mutation
    assertEquals(3, helper.getAllSortedById().size());
    assertEquals(Hero.WonderWoman, helper.lookupById(1));
  }

  @Test
  void testGetAllSortedByIdReflectsSubsequentAdd() {
    // the returned collection is cached once (for efficiency, not re-wrapped per call) but must still be a
    // live view - it must reflect constants added after the collection reference was first obtained, not a
    // frozen snapshot from whenever the helper happened to be constructed
    ExtendedEnumHelper<Foo> enumHelper = new ExtendedEnumHelper<>(Foo.class);
    Collection<Foo> view = enumHelper.getAllSortedById();
    assertEquals(0, view.size());

    enumHelper.add(Foo.ONE, 1, "one", "One");
    assertEquals(1, view.size());
    assertTrue(view.contains(Foo.ONE));
  }

  @Test
  void testAddRejectsNullDisplayName() {
    ExtendedEnumHelper<Foo> enumHelper = new ExtendedEnumHelper<>(Foo.class);
    assertThrows(NullPointerException.class, () -> enumHelper.add(Foo.ONE, 1, "one", null));
  }

  @Test
  void testAddRejectsEmptyDisplayName() {
    ExtendedEnumHelper<Foo> enumHelper = new ExtendedEnumHelper<>(Foo.class);
    try {
      enumHelper.add(Foo.ONE, 1, "one", " ");
      fail("Empty displayName");
    } catch (IllegalArgumentException ex) {
      assertTrue(ex.getMessage().startsWith("Empty displayName"), ex.getMessage());
    }
  }

  @Test
  void testLookup() {
    ExtendedEnumHelper<Hero> helper = ExtendedEnumHelper.getExtendedEnumHelper(Hero.class);

    assertEquals(Hero.WonderWoman, helper.lookupByName("Diana"));
    assertEquals(Hero.WonderWoman, helper.lookupByName("Diana Prince"));
    assertEquals(Hero.WonderWoman, helper.lookupByName("diana"));
    assertNull(helper.lookupByName("1"));
    assertNull(helper.lookupByName("Diana Ross"));

    assertEquals(Hero.WonderWoman, helper.lookupByNameOrThrow("Diana"));
    assertEquals(Hero.WonderWoman, helper.lookupByNameOrThrow("Diana Prince"));
    assertEquals(Hero.WonderWoman, helper.lookupByNameOrThrow("diana"));
    assertEquals(Hero.WonderWoman, helper.fromString("1"));
    assertThrows(IllegalArgumentException.class, () -> {
      helper.lookupByNameOrThrow("1");
    });
    assertThrows(IllegalArgumentException.class, () -> {
      helper.lookupByNameOrThrow("Diana Ross");
    });

    assertEquals(Hero.WonderWoman, helper.fromString("Diana"));
    assertEquals(Hero.WonderWoman, helper.fromString("Diana Prince"));
    assertEquals(Hero.WonderWoman, helper.fromString("diana"));
    assertEquals(Hero.WonderWoman, helper.fromString("1"));
    assertNull(helper.lookupByName("Diana Ross"));
  }

  @Test
  void testFromStringWithOutOfRangeNumericStringReturnsNull() {
    // StringUtils.isNumeric() accepts any all-digit string, but Integer.parseInt() can't handle every one -
    // an out-of-range numeric string is a lookup miss (no such ID), not an unchecked NumberFormatException
    ExtendedEnumHelper<Hero> helper = ExtendedEnumHelper.getExtendedEnumHelper(Hero.class);
    assertNull(helper.fromString("99999999999"));
  }

  @Test
  void testFromStringStripsQuery() {
    // lookupByName() already strips its query to match add()'s stripped names - fromString() must strip
    // before its "is this numeric" check too, or a padded numeric ID falls through to the name lookup
    ExtendedEnumHelper<Hero> helper = ExtendedEnumHelper.getExtendedEnumHelper(Hero.class);
    assertEquals(Hero.WonderWoman, helper.fromString(" 1 "));
    assertEquals(Hero.WonderWoman, helper.fromString(" Diana "));
  }


  @Test
  void testConcurrentAddWithSameIdIsAtomic() throws InterruptedException {
    // add()'s duplicate-ID check-then-mutate must be atomic: concurrent add() calls racing on the same ID
    // must result in exactly one success and every other caller throwing IllegalArgumentException - an
    // unsynchronized check-then-put lets multiple callers pass the containsKey() check before any of them
    // calls put(), silently violating the "duplicate ID throws" contract
    ExtendedEnumHelper<Foo> enumHelper = new ExtendedEnumHelper<>(Foo.class);
    int threadCount = 50;
    CountDownLatch ready = new CountDownLatch(threadCount);
    CountDownLatch go = new CountDownLatch(1);
    AtomicInteger successCount = new AtomicInteger();
    List<Thread> threads = new ArrayList<>();
    for (int i = 0; i < threadCount; i++) {
      int idx = i;
      Thread t = new Thread(() -> {
        ready.countDown();
        try {
          go.await();
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
          return;
        }
        try {
          enumHelper.add(idx % 2 == 0 ? Foo.ONE : Foo.TWO, 1, "short" + idx, "short" + idx);
          successCount.incrementAndGet();
        } catch (IllegalArgumentException ex) {
          // expected for all but one caller
        }
      });
      threads.add(t);
      t.start();
    }
    ready.await();
    go.countDown();
    for (Thread t : threads) {
      t.join();
    }
    assertEquals(1, successCount.get());
  }

  @Test
  void testConcurrentReadDuringAddDoesNotThrow() throws InterruptedException {
    // lookups must never throw/corrupt while a concurrent add() is in progress (e.g. via
    // ExtendedEnumConverter.convert(), reachable from another thread with just a bare Class reference, while
    // an enum's own static initializer is still registering later constants on a different thread) - a plain
    // TreeMap would risk ConcurrentModificationException/undefined behavior here
    ExtendedEnumHelper<Foo> enumHelper = new ExtendedEnumHelper<>(Foo.class);
    AtomicBoolean stop = new AtomicBoolean(false);
    AtomicReference<Throwable> readerError = new AtomicReference<>();
    Thread reader = new Thread(() -> {
      while (!stop.get()) {
        try {
          for (Foo ignored : enumHelper.getAllSortedById()) {
            // just iterate
          }
          enumHelper.lookupById(0);
        } catch (Throwable t) {
          readerError.set(t);
          return;
        }
      }
    });
    reader.start();
    for (int i = 0; i < 2000; i++) {
      enumHelper.add(i % 2 == 0 ? Foo.ONE : Foo.TWO, i, "short" + i, "short" + i);
    }
    stop.set(true);
    reader.join();
    assertNull(readerError.get());
  }


  @Test
  void testAdd() {

    ExtendedEnumHelper<Foo> enumHelper = new ExtendedEnumHelper<>(Foo.class);
    enumHelper.add(Foo.ONE, 1, "one", "One");
    try {
      enumHelper.add(Foo.ONE, 1, "one", "One");
      fail("Duplicate id");
    } catch (IllegalArgumentException ex) {
      assertTrue(ex.getMessage().startsWith("Duplicate ID"), ex.getMessage());
    }

    try {
      enumHelper.add(Foo.ONE, 2, " ", "One");
      fail("Empty shortName");
    } catch (IllegalArgumentException ex) {
      assertTrue(ex.getMessage().startsWith("Empty shortName"), ex.getMessage());
    }
    try {
      enumHelper.add(Foo.ONE, 2, "o n e", "One");
      fail("Spaces in shortName");
    } catch (IllegalArgumentException ex) {
      assertTrue(ex.getMessage().startsWith("Spaces in shortName"), ex.getMessage());
    }
    try {
      enumHelper.add(Foo.ONE, 2, "1", "One");
      fail("Numeric shortName");
    } catch (IllegalArgumentException ex) {
      assertTrue(ex.getMessage().startsWith("Numeric shortName"), ex.getMessage());
    }
    try {
      enumHelper.add(Foo.ONE, 2, "one", "One");
      fail("Duplicate shortName");
    } catch (IllegalArgumentException ex) {
      assertTrue(ex.getMessage().startsWith("Duplicate shortName"), ex.getMessage());
    }

    try {
      enumHelper.add(Foo.ONE, 2, "two", "One");
      fail("Duplicate displayName");
    } catch (IllegalArgumentException ex) {
      assertTrue(ex.getMessage().startsWith("Duplicate displayName"), ex.getMessage());
    }

  }

  @Test
  void testAddRejectsWhitespaceInShortNameBeyondLiteralSpace() {
    // the "no spaces" check must reject any whitespace character (tab, newline, non-breaking space), not
    // just literal U+0020 - a shortName like "o\tne" would otherwise be silently accepted despite looking
    // identical to an already-rejected "o ne"
    ExtendedEnumHelper<Foo> enumHelper = new ExtendedEnumHelper<>(Foo.class);
    assertThrows(IllegalArgumentException.class, () -> enumHelper.add(Foo.ONE, 1, "o\tne", "One"));

    ExtendedEnumHelper<Foo> enumHelper2 = new ExtendedEnumHelper<>(Foo.class);
    assertThrows(IllegalArgumentException.class, () -> enumHelper2.add(Foo.ONE, 1, "o\nne", "One"));

    ExtendedEnumHelper<Foo> enumHelper3 = new ExtendedEnumHelper<>(Foo.class);
    assertThrows(IllegalArgumentException.class, () -> enumHelper3.add(Foo.ONE, 1, "o\u00A0ne", "One"));
  }

  @Test
  void testAddRejectsNumericDisplayNameAndAdditionalName() {
    // numeric names (like numeric shortNames) are ambiguous with fromString()'s "numeric string means ID"
    // convention, so they must be rejected the same way for displayName/additionalNames too
    ExtendedEnumHelper<Foo> enumHelper = new ExtendedEnumHelper<>(Foo.class);
    try {
      enumHelper.add(Foo.ONE, 1, "one", "42");
      fail("Numeric displayName");
    } catch (IllegalArgumentException ex) {
      assertTrue(ex.getMessage().startsWith("Numeric displayName"), ex.getMessage());
    }
    try {
      enumHelper.add(Foo.ONE, 1, "one", "One", "42");
      fail("Numeric additional name");
    } catch (IllegalArgumentException ex) {
      assertTrue(ex.getMessage().startsWith("Numeric additional name"), ex.getMessage());
    }

    // a rejected add() must not leave partial state behind
    assertNull(enumHelper.lookupById(1));
  }

  @Test
  void testAddStripsAdditionalNames() {
    // additionalNames must be stripped the same way shortName/displayName already are, or a whitespace-padded
    // additional name is stored but never reachable via a normally-typed lookupByName() call
    ExtendedEnumHelper<Foo> enumHelper = new ExtendedEnumHelper<>(Foo.class);
    enumHelper.add(Foo.ONE, 1, "one", "One", " alias ");

    assertEquals(Foo.ONE, enumHelper.lookupByName("alias"));
  }

  @Test
  void testLookupByNameStripsQuery() {
    // add() stores names stripped, so the query side must strip too, or a caller passing an
    // already-padded value (e.g. from user input) silently gets null instead of a match
    ExtendedEnumHelper<Foo> enumHelper = new ExtendedEnumHelper<>(Foo.class);
    enumHelper.add(Foo.ONE, 1, "one", "One", "alias");

    assertEquals(Foo.ONE, enumHelper.lookupByName(" one "));
    assertEquals(Foo.ONE, enumHelper.lookupByName(" One "));
    assertEquals(Foo.ONE, enumHelper.lookupByName(" alias "));
  }

  @Test
  void testAddCaseInsensitiveShortNameCollision() {
    ExtendedEnumHelper<Foo> enumHelper = new ExtendedEnumHelper<>(Foo.class);
    enumHelper.add(Foo.ONE, 1, "one", "One");
    try {
      enumHelper.add(Foo.TWO, 2, "ONE", "Two");
      fail("Case-insensitive collision with shortName");
    } catch (IllegalArgumentException ex) {
      assertTrue(ex.getMessage().startsWith("shortName"), ex.getMessage());
    }

    // a rejected add() must not leave partial state behind
    assertNull(enumHelper.lookupById(2));
    assertEquals(Foo.ONE, enumHelper.lookupByName("one"));
  }

  @Test
  void testAddCaseInsensitiveDisplayNameCollision() {
    ExtendedEnumHelper<Foo> enumHelper = new ExtendedEnumHelper<>(Foo.class);
    enumHelper.add(Foo.ONE, 1, "one", "One");
    try {
      enumHelper.add(Foo.TWO, 2, "two", "one");
      fail("Case-insensitive collision with displayName");
    } catch (IllegalArgumentException ex) {
      assertTrue(ex.getMessage().startsWith("displayName"), ex.getMessage());
    }
  }

  @Test
  void testAddCaseInsensitiveAdditionalNameCollisionDoesNotCorruptOtherEnum() {
    ExtendedEnumHelper<Foo> enumHelper = new ExtendedEnumHelper<>(Foo.class);
    enumHelper.add(Foo.ONE, 1, "one", "One", "foo");
    try {
      enumHelper.add(Foo.TWO, 2, "two", "Two", "FOO");
      fail("Case-insensitive collision with additional name");
    } catch (IllegalArgumentException ex) {
      assertTrue(ex.getMessage().startsWith("Additional name"), ex.getMessage());
    }

    // the collision must not corrupt lookups for the enum that already owned the additional name
    assertEquals(Foo.ONE, enumHelper.lookupByName("foo"));
    assertEquals(Foo.ONE, enumHelper.lookupByName("FOO"));
  }

  @Test
  void testAddAdditionalNameCollisionDoesNotPartiallyRegisterRejectedEnum() {
    // add() validates every additional name (along with id/shortName/displayName) before mutating anything -
    // a rejected add() must not leave any of those pieces committed
    ExtendedEnumHelper<Foo> enumHelper = new ExtendedEnumHelper<>(Foo.class);
    enumHelper.add(Foo.ONE, 1, "one", "One", "alias");
    assertThrows(IllegalArgumentException.class, () -> enumHelper.add(Foo.TWO, 2, "two", "Two", "ALIAS"));

    assertNull(enumHelper.lookupById(2));
    assertNull(enumHelper.lookupByName("two"));
  }

  @Test
  void testAddWithConstantSpecificClassBody() {
    // Op.ADD.getClass() is a synthetic anonymous subclass, not Op.class - registration must still key off
    // the helper's declared enum class so getExtendedEnumHelper(Op.class) can find it
    ExtendedEnumHelper<Op> enumHelper = new ExtendedEnumHelper<>(Op.class);
    for (Op op : Op.values()) {
      enumHelper.add(op, op.getId(), op.getShortName(), op.getShortName());
    }

    assertSame(enumHelper, ExtendedEnumHelper.getExtendedEnumHelper(Op.class));
    assertEquals(Op.ADD, ExtendedEnumHelper.getExtendedEnumHelper(Op.class).lookupById(1));
  }

  @Test
  void testGetExtendedEnumHelperWorksForNonEnumImplementor() {
    // getExtendedEnumHelper() must be able to force a class's static initializer to run (so its add() calls
    // register it) for ANY ExtendedEnum implementor, not just actual enums - getEnumConstants() (used to
    // trigger initialization) returns null for a non-enum class, which must not be treated as "unregistered"
    ExtendedEnumHelper<NonEnumConstant> helper = ExtendedEnumHelper.getExtendedEnumHelper(NonEnumConstant.class);
    assertEquals(NonEnumConstant.ONE, helper.lookupById(1));
  }

  @Test
  void testAddRejectsMismatchedEnumType() {
    //noinspection rawtypes
    ExtendedEnumHelper rawHelper = new ExtendedEnumHelper(Foo.class);
    assertThrows(IllegalArgumentException.class, () -> rawHelper.add(Op.ADD, 1, "add", "Add"));
  }


  @Test
  void testRegisterAutoDiscoversConstantsWithDisplayNamesAndAdditionalNames() {
    // register() must call add() once per constant using getId()/getShortName()/getDisplayName()/
    // getAdditionalNames(), instead of each constant's constructor calling add() itself
    ExtendedEnumHelper<AutoWithDisplay> helper = ExtendedEnumHelper.getExtendedEnumHelper(AutoWithDisplay.class);
    assertEquals(AutoWithDisplay.ONE, helper.lookupById(1));
    assertEquals(AutoWithDisplay.ONE, helper.lookupByName("One"));
    assertEquals(AutoWithDisplay.ONE, helper.lookupByName("uno"));
    assertEquals("One", AutoWithDisplay.ONE.getDisplayName());
    assertArrayEquals(new AutoWithDisplay[] {AutoWithDisplay.ONE, AutoWithDisplay.TWO},
        helper.getAllSortedById().toArray(new AutoWithDisplay[0]));
  }

  @Test
  void testRegisterFallsBackToShortNameWhenDisplayNameNotOverridden() {
    // getDisplayName() isn't overridden here, so it uses ExtendedEnum's default, which falls back to
    // getShortName() - matching what every hand-written implementor with no distinct display name used to do
    // in its own getDisplayName() override (e.g. Ternary.java in PharmGKB)
    assertEquals("alpha", AutoWithoutDisplay.ALPHA.getDisplayName());
    assertEquals(AutoWithoutDisplay.ALPHA,
        ExtendedEnumHelper.getExtendedEnumHelper(AutoWithoutDisplay.class).lookupByName("alpha"));
  }

  @Test
  void testRegisterEnforcesSameValidationAsManualAdd() {
    // register() must delegate to add() itself, not reimplement/bypass its validation - AutoNullDisplay's
    // getDisplayName() override deliberately returns null (bypassing the interface's own default, which never
    // would), and add() rejects a null displayName; the exception is thrown from AutoNullDisplay's own static
    // field initializer, so the JVM wraps it in ExceptionInInitializerError
    ExceptionInInitializerError error = assertThrows(ExceptionInInitializerError.class,
        () -> { Object ignored = AutoNullDisplay.ONE; });
    assertInstanceOf(NullPointerException.class, error.getCause());
  }

  @Test
  void testRegisterRejectsSecondCallForSameClass() {
    // a second register(AutoWithoutDisplay.class) call must not silently build and discard an orphaned
    // second helper - getExtendedEnumHelper() forces AutoWithoutDisplay's static field to run first (test
    // execution order isn't guaranteed, so this can't rely on another test having already triggered it),
    // which registers it once
    ExtendedEnumHelper.getExtendedEnumHelper(AutoWithoutDisplay.class);
    assertThrows(IllegalStateException.class, () -> ExtendedEnumHelper.register(AutoWithoutDisplay.class));
    // the original registration must still be intact, not replaced or corrupted by the rejected attempt
    assertEquals(AutoWithoutDisplay.ALPHA,
        ExtendedEnumHelper.getExtendedEnumHelper(AutoWithoutDisplay.class).lookupByName("alpha"));
  }


  private enum AutoWithDisplay implements ExtendedEnum {
    ONE(1, "one", "One", "uno"),
    TWO(2, "two", "Two");

    private static final ExtendedEnumHelper<AutoWithDisplay> s_helper =
        ExtendedEnumHelper.register(AutoWithDisplay.class);

    private final int m_id;
    private final String m_shortName;
    private final String m_displayName;
    private final String @Nullable [] m_additionalNames;

    AutoWithDisplay(int id, String shortName, String displayName, String... additionalNames) {
      m_id = id;
      m_shortName = shortName;
      m_displayName = displayName;
      m_additionalNames = additionalNames.length == 0 ? null : additionalNames;
    }

    @Override
    public int getId() {
      return m_id;
    }

    @Override
    public @NonNull String getShortName() {
      return m_shortName;
    }

    @Override
    public @NonNull String getDisplayName() {
      return m_displayName;
    }

    @Override
    public String @Nullable [] getAdditionalNames() {
      return m_additionalNames;
    }
  }


  private enum AutoWithoutDisplay implements ExtendedEnum {
    ALPHA(1, "alpha"),
    BETA(2, "beta");

    private static final ExtendedEnumHelper<AutoWithoutDisplay> s_helper =
        ExtendedEnumHelper.register(AutoWithoutDisplay.class);

    private final int m_id;
    private final String m_shortName;

    AutoWithoutDisplay(int id, String shortName) {
      m_id = id;
      m_shortName = shortName;
    }

    @Override
    public int getId() {
      return m_id;
    }

    @Override
    public @NonNull String getShortName() {
      return m_shortName;
    }
    // getDisplayName()/getAdditionalNames() rely on ExtendedEnum's defaults
  }


  private enum AutoNullDisplay implements ExtendedEnum {
    ONE(1, "one");

    private static final ExtendedEnumHelper<AutoNullDisplay> s_helper =
        ExtendedEnumHelper.register(AutoNullDisplay.class);

    private final int m_id;
    private final String m_shortName;

    AutoNullDisplay(int id, String shortName) {
      m_id = id;
      m_shortName = shortName;
    }

    @Override
    public int getId() {
      return m_id;
    }

    @Override
    public @NonNull String getShortName() {
      return m_shortName;
    }

    @Override
    public @Nullable String getDisplayName() {
      // deliberately breaks the interface's non-null contract, to verify register() doesn't bypass add()'s
      // own runtime enforcement of it
      return null;
    }
  }


  private enum Op implements ExtendedEnum {
    ADD(1, "add") {
      @Override
      public int apply(int a, int b) {
        return a + b;
      }
    },
    SUBTRACT(2, "subtract") {
      @Override
      public int apply(int a, int b) {
        return a - b;
      }
    };

    private final int m_id;
    private final String m_shortName;

    Op(int id, String shortName) {
      m_id = id;
      m_shortName = shortName;
    }

    public abstract int apply(int a, int b);

    @Override
    public int getId() {
      return m_id;
    }

    @Override
    public @NonNull String getShortName() {
      return m_shortName;
    }

    @Override
    public @NonNull String getDisplayName() {
      return m_shortName;
    }
  }


  private enum Foo implements ExtendedEnum {
    ONE,
    TWO;

    @Override
    public int getId() {
      return 0;
    }

    @Override
    public @NonNull String getShortName() {
      return "shortName";
    }

    @Override
    public @NonNull String getDisplayName() {
      return "displayName";
    }
  }


  // ExtendedEnum is a plain interface, not restricted to actual enums - this is the typesafe-constant
  // pattern (a regular class with static final instances), a valid implementor too
  private static final class NonEnumConstant implements ExtendedEnum {
    static final NonEnumConstant ONE = new NonEnumConstant(1, "one");
    private static final ExtendedEnumHelper<NonEnumConstant> s_helper =
        new ExtendedEnumHelper<>(NonEnumConstant.class);
    static {
      s_helper.add(ONE, ONE.m_id, ONE.m_shortName, ONE.m_shortName);
    }

    private final int m_id;
    private final String m_shortName;

    private NonEnumConstant(int id, String shortName) {
      m_id = id;
      m_shortName = shortName;
    }

    @Override
    public int getId() {
      return m_id;
    }

    @Override
    public @NonNull String getShortName() {
      return m_shortName;
    }

    @Override
    public @NonNull String getDisplayName() {
      return m_shortName;
    }
  }
}
