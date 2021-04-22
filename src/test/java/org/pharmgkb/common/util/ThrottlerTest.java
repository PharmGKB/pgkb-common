package org.pharmgkb.common.util;

import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import com.google.common.base.Stopwatch;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;


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
    Throttler throttler = new Throttler(1, TimeUnit.SECONDS);
    Stopwatch stopwatch = Stopwatch.createStarted();
    IntStream.range(0, 10)
        .parallel()
        .forEach(x -> throttler.next());
    long elapsed = stopwatch.elapsed(TimeUnit.MILLISECONDS);
    System.out.printf("%,d ms%n", elapsed);
    assertTrue(elapsed >= 9000);
    assertTrue(elapsed < 10200);
  }


  @Test
  void test500Milliseconds() {
    Throttler throttler = new Throttler(500, TimeUnit.MILLISECONDS);
    Stopwatch stopwatch = Stopwatch.createStarted();
    IntStream.range(0, 10)
        .parallel()
        .forEach(x -> throttler.next());
    long elapsed = stopwatch.elapsed(TimeUnit.MILLISECONDS);
    System.out.printf("%,d ms%n", elapsed);
    assertTrue(elapsed >= 4500);
    assertTrue(elapsed < 5510);
  }
}
