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
  /** Monotonic {@link System#nanoTime()}-style clock (not the wall clock). Overridable only for tests. */
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
   * For tests only. {@link #next()} still waits for real, so {@code nanoClock} must advance, or {@link #next()}
   * loops forever.
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
   * @throws InterruptedException if interrupted while waiting; the wait isn't recorded, so the next call waits
   * again
   */
  public void next() throws InterruptedException {

    synchronized(this) {
      // wait() rather than Thread.sleep(), which would hold the monitor and leave other callers blocked
      // (uninterruptibly) for the whole interval. Loop for spurious wakeups and other threads' progress
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
