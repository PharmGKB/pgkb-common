package org.pharmgkb.common.util;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import java.util.stream.IntStream;
import com.google.common.base.Stopwatch;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


/**
 * This is a JUnit test for {@link Throttler}.
 *
 * @author Mark Woon
 */
class ThrottlerTest {


  @Test
  void testNoThrottler() {
    Stopwatch stopwatch = Stopwatch.createStarted();
    IntStream.range(0, 10)
        .parallel()
        .forEach(x -> System.out.print("."));
    long elapsed = stopwatch.elapsed(TimeUnit.MILLISECONDS);
    System.out.println();
    System.out.printf("%,d ms%n", elapsed);
    assertTrue(elapsed < 1000);
  }

  @Test
  void test1Second() {
    // 3 calls, not 10 - each call (including the first, since the constructor's own initial timestamp is
    // effectively "now") waits close to the full interval, so N calls cost ~N intervals of real time. The
    // injectable clock (see testNextDoesNotBlockWhenClockShowsEnoughTimeElapsed below) can't help here, since
    // next()'s wait() is always real regardless of how elapsed time is measured; real cross-thread
    // serialization under genuine contention is already covered by testInterruptDoesNotShortenThrottle/
    // testNextThrowsInterruptedExceptionWhenInterruptedDuringWait below, so this only needs enough calls to
    // confirm the SECONDS-unit conversion multiplies correctly across more than one wait, not 10 of them
    Throttler throttler = new Throttler(1, TimeUnit.SECONDS);
    Stopwatch stopwatch = Stopwatch.createStarted();
    IntStream.range(0, 3)
        .parallel()
        .forEach(x -> nextUnchecked(throttler));
    long elapsed = stopwatch.elapsed(TimeUnit.MILLISECONDS);
    System.out.printf("%,d ms%n", elapsed);
    assertTrue(elapsed >= 2900);
    assertTrue(elapsed < 3400);
  }


  @Test
  void test500Milliseconds() {
    // see test1Second() above for why 3 calls, not 10
    Throttler throttler = new Throttler(500, TimeUnit.MILLISECONDS);
    Stopwatch stopwatch = Stopwatch.createStarted();
    IntStream.range(0, 3)
        .parallel()
        .forEach(x -> nextUnchecked(throttler));
    long elapsed = stopwatch.elapsed(TimeUnit.MILLISECONDS);
    System.out.printf("%,d ms%n", elapsed);
    assertTrue(elapsed >= 1450);
    assertTrue(elapsed < 1700);
  }

  /**
   * Calls {@link Throttler#next()}, rethrowing {@link InterruptedException} unchecked - only for use where the
   * calling context (e.g. a {@link java.util.function.Consumer} lambda) can't declare a checked exception.
   */
  private static void nextUnchecked(Throttler throttler) {
    try {
      throttler.next();
    } catch (InterruptedException ex) {
      throw new RuntimeException(ex);
    }
  }


  @Test
  void testInterruptDoesNotShortenThrottle() throws InterruptedException {
    Throttler throttler = new Throttler(500, TimeUnit.MILLISECONDS);
    throttler.next();

    // interrupt a throttled wait partway through
    Thread t = new Thread(() -> {
      try {
        throttler.next();
      } catch (InterruptedException ex) {
        // expected: this thread is deliberately interrupted mid-wait below
        Thread.currentThread().interrupt();
      }
    });
    t.start();
    Thread.sleep(50);
    t.interrupt();
    t.join();

    // since the interrupted wait didn't complete, the next call must still wait out close to the
    // remaining interval rather than being let through immediately
    Stopwatch stopwatch = Stopwatch.createStarted();
    throttler.next();
    long elapsed = stopwatch.elapsed(TimeUnit.MILLISECONDS);
    System.out.printf("%,d ms%n", elapsed);
    assertTrue(elapsed > 300);
  }

  @Test
  void testNextThrowsInterruptedExceptionWhenInterruptedDuringWait() throws InterruptedException {
    // next() must not silently swallow an interrupted wait and return as if nothing happened - the old
    // behavior (catch, restore the interrupt flag, return without recording a completed wait) left the
    // interrupt flag set with nothing to clear it, so every subsequent next() call on that same thread would
    // immediately re-interrupt itself and silently skip throttling too, forever. Propagating the exception
    // instead forces the caller to explicitly decide how to handle it.
    Throttler throttler = new Throttler(1, TimeUnit.SECONDS);
    throttler.next();

    AtomicReference<Exception> caught = new AtomicReference<>();
    Thread t = new Thread(() -> {
      try {
        throttler.next();
      } catch (InterruptedException ex) {
        caught.set(ex);
      }
    });
    t.start();
    Thread.sleep(50);
    t.interrupt();
    t.join();

    assertInstanceOf(InterruptedException.class, caught.get());
  }

  @Test
  void testInterruptWakesThreadWaitingForItsTurn() throws InterruptedException {
    // next() previously held the monitor for the entire Thread.sleep(), so a thread waiting for a DIFFERENT
    // thread's turn to finish is BLOCKED on monitor entry - Thread.interrupt() cannot wake a BLOCKED thread,
    // only a WAITING/TIMED_WAITING one, so it wouldn't notice the interrupt until it finally acquired the
    // monitor (i.e. after "first" below finished its own ~1-second wait). This confirms "second", contending
    // for the monitor while "first" holds it, is interruptible promptly instead.
    Throttler throttler = new Throttler(1, TimeUnit.SECONDS);
    throttler.next(); // consume the initial "free" call

    AtomicReference<Exception> caught = new AtomicReference<>();
    Thread first = new Thread(() -> nextUnchecked(throttler));
    Thread second = new Thread(() -> {
      try {
        throttler.next();
      } catch (InterruptedException ex) {
        caught.set(ex);
      }
    });
    first.start();
    Thread.sleep(50); // let "first" acquire the monitor and start waiting out its own interval
    second.start();
    Thread.sleep(50); // let "second" start contending too

    Stopwatch stopwatch = Stopwatch.createStarted();
    second.interrupt();
    second.join();
    long elapsed = stopwatch.elapsed(TimeUnit.MILLISECONDS);

    assertInstanceOf(InterruptedException.class, caught.get());
    // well under "first"'s ~1-second wait confirms "second" was actually interruptible, not stuck BLOCKED
    assertTrue(elapsed < 500, "elapsed=" + elapsed);
    first.join();
  }

  @Test
  void testNextDoesNotBlockWhenClockShowsEnoughTimeElapsed() throws InterruptedException {
    // Throttler measures elapsed time via a monotonic nanosecond clock (see the package-private constructor
    // used here, and its delegation from the public constructor via System::nanoTime) rather than the wall
    // clock (System.currentTimeMillis(), which an NTP adjustment or manual clock change could jump either
    // direction). Mockito can't mock java.lang.System itself (it's explicitly disallowed, to avoid
    // interfering with class loading), so this verifies the same interval-tracking logic deterministically
    // via the injectable clock instead, with no real wait().
    LongSupplier nanoClock = mock(LongSupplier.class);
    when(nanoClock.getAsLong())
        .thenReturn(0L)                                  // constructor: sets the initial timestamp
        .thenReturn(TimeUnit.MILLISECONDS.toNanos(250));  // next(): simulate 250ms already elapsed

    Throttler throttler = new Throttler(200, TimeUnit.MILLISECONDS, nanoClock);
    Stopwatch stopwatch = Stopwatch.createStarted();
    throttler.next();
    long elapsed = stopwatch.elapsed(TimeUnit.MILLISECONDS);

    // 250ms already elapsed per the clock, more than the 200ms minimum - next() must return immediately
    assertTrue(elapsed < 100, "elapsed=" + elapsed);
  }
}
