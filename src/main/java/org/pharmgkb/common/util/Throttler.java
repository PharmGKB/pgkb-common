package org.pharmgkb.common.util;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.TimeUnit;
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
  private long m_minTime;
  /** Time of last operation. */
  private long m_lastScheduledAction = System.currentTimeMillis();


  /**
   * Constructor.
   *
   * @param minTime the minimum time to wait (must be resolvable to milliseconds)
   * @param unit the unit the minimum time is in
   */
  public Throttler(long minTime, TimeUnit unit) {
    Preconditions.checkArgument(minTime > 0);
    m_minTime = unit.toMillis(minTime);
    Preconditions.checkArgument(m_minTime > 0, "Minimum time must be greater than 0 milliseconds");
  }


  public void reset() {
    synchronized(this) {
      m_lastScheduledAction = System.currentTimeMillis();
    }
  }


  /**
   * Returns when next operation can performed.
   * This will block until the minimum time has elapsed.
   */
  public void next() {

    synchronized(this) {
      long curTime = System.currentTimeMillis();
      long timeLeft = m_lastScheduledAction + m_minTime - curTime;
      if (timeLeft > 0) {
        // if needed, wait for our time
        try {
          sf_logger.debug("Throttling for {}ms", timeLeft);
          Thread.sleep(timeLeft);
        } catch (InterruptedException ex) {
          sf_logger.warn("Thread interrupted");
          Thread.currentThread().interrupt();
        }
      }
      m_lastScheduledAction = System.currentTimeMillis();
    }
  }
}
