package org.pharmgkb.common.util;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This class throttles operations by guaranteeing a minimum time between operations.
 *
 * @author Mark Woon
 */
public class Throttler {
  private static final Logger sf_logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  /** Minimum time between requests in millisecond. */
  private final long m_minTime;
  /**
   * Source of {@link System#nanoTime()}-style timestamps (not {@link System#currentTimeMillis()} - the wall
   * clock isn't monotonic, so an NTP adjustment or manual clock change could make this either block far
   * longer than intended, or skip throttling entirely). Overridable only for tests.
   */
  private final LongSupplier m_nanoClock;
  /** Time of last operation, per {@link #m_nanoClock}. */
  private long m_lastScheduledAction;


  /**
   * Constructor.
   *
   * @param minTime the minimum time to wait (must be resolvable to milliseconds)
   * @param unit the unit the minimum time is in
   */
  public Throttler(long minTime, TimeUnit unit) {
    this(minTime, unit, System::nanoTime);
  }

  /**
   * For tests only, to control the passage of time deterministically. Note {@link #next()} still does a real
   * (if normally brief) {@link Object#wait(long)} - a clock that never advances (e.g. a mock always returning
   * the same value) makes {@link #next()} loop forever, since {@code timeLeft} never reaches 0; a real,
   * advancing clock (even a scaled-speed fake one) is required, unlike a plain non-advancing stub.
   */
  Throttler(long minTime, TimeUnit unit, LongSupplier nanoClock) {
    Preconditions.checkArgument(minTime > 0);
    m_minTime = unit.toMillis(minTime);
    Preconditions.checkArgument(m_minTime > 0, "Minimum time must be greater than 0 milliseconds");
    m_nanoClock = nanoClock;
    m_lastScheduledAction = m_nanoClock.getAsLong();
  }


  public void reset() {
    synchronized(this) {
      m_lastScheduledAction = m_nanoClock.getAsLong();
    }
  }


  /**
   * Returns when next operation can performed.
   * This will block until the minimum time has elapsed.
   *
   * @throws InterruptedException if interrupted while waiting - the wait is not recorded as completed, so
   * the minimum interval wasn't actually honored and the next call to this method will need to wait out (up
   * to) the same interval again. This propagates rather than being swallowed so a caller can't be silently
   * left with its interrupt status set and no way to know throttling didn't happen.
   */
  public void next() throws InterruptedException {

    synchronized(this) {
      // wait(timeLeft) in a loop, not Thread.sleep(timeLeft): sleep() holds this monitor for the entire
      // sleep, so any OTHER thread contending for next()/reset() is BLOCKED on monitor entry for that whole
      // time - and a BLOCKED thread can't be woken by Thread.interrupt() the way a WAITING/TIMED_WAITING one
      // can, so it wouldn't even notice an interrupt until it finally acquired the monitor. wait() releases
      // the monitor while waiting, so a contending thread instead does its own wait() (interruptible) rather
      // than blocking on entry - the loop re-checks elapsed time after each wait to handle both a spurious
      // wakeup and another thread having advanced m_lastScheduledAction in the meantime
      while (true) {
        long curTime = m_nanoClock.getAsLong();
        long elapsed = TimeUnit.NANOSECONDS.toMillis(curTime - m_lastScheduledAction);
        long timeLeft = m_minTime - elapsed;
        if (timeLeft <= 0) {
          break;
        }
        sf_logger.debug("Throttling for {}ms", timeLeft);
        wait(timeLeft);
      }
      m_lastScheduledAction = m_nanoClock.getAsLong();
    }
  }
}
