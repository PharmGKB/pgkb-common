package org.pharmgkb.common.util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;


/**
 * This is a JUnit test for {@link StrictPrintWriter}.
 *
 * @author Mark Woon
 */
class StrictPrintWriterTest {

  @Test
  void writesLikeAPrintWriter() {
    StringWriter text = new StringWriter();
    try (StrictPrintWriter writer = new StrictPrintWriter(text)) {
      writer.print("a");
      writer.print('b');
      writer.println(1);
      writer.printf("%s-%d", "c", 2);
    }
    assertEquals("ab1" + System.lineSeparator() + "c-2", text.toString());
  }

  @Test
  void writeErrorIsRethrownWithItsCause() {
    IOException diskFull = new IOException("No space left on device");
    TargetWriter target = new TargetWriter(diskFull, false);

    StrictPrintWriter writer = new StrictPrintWriter(target);
    UncheckedIOException ex = assertThrows(UncheckedIOException.class, () -> writer.print("x"));
    assertSame(diskFull, ex.getCause());
  }

  @Test
  void eachWayOfWritingRethrows() {
    for (Consumer<StrictPrintWriter> write : List.<Consumer<StrictPrintWriter>>of(
        (w) -> w.write('x'),
        (w) -> w.write(new char[] {'x'}),
        (w) -> w.write("x"),
        (w) -> w.println(),
        (w) -> w.println("x"),
        (w) -> w.printf("%s", "x"))) {
      StrictPrintWriter writer = new StrictPrintWriter(new TargetWriter(new IOException("fail"), false));
      assertThrows(UncheckedIOException.class, () -> write.accept(writer));
    }
  }

  /**
   * A buffered target writes nothing until it flushes, and the last flush happens in close(), which is where a full
   * disk usually shows. PrintWriter swallows that one too.
   */
  @Test
  void closeRethrowsTheFinalFlushError() {
    IOException diskFull = new IOException("No space left on device");
    StrictPrintWriter writer = new StrictPrintWriter(new BufferedWriter(new TargetWriter(diskFull, false)));
    writer.print("x");

    UncheckedIOException ex = assertThrows(UncheckedIOException.class, writer::close);
    assertSame(diskFull, ex.getCause());
  }

  @Test
  void flushRethrows() {
    StrictPrintWriter writer = new StrictPrintWriter(new TargetWriter(new IOException("fail"), true));
    assertThrows(UncheckedIOException.class, writer::flush);
  }

  /** Checking for an error must not cost a flush per write, which would undo the target's buffering. */
  @Test
  void writingDoesNotFlush() {
    TargetWriter target = new TargetWriter(null, false);
    StrictPrintWriter writer = new StrictPrintWriter(target);
    for (int i = 0; i < 100; i++) {
      writer.println("line " + i);
    }
    assertEquals(0, target.flushes);
  }

  @Test
  void pathConstructorWritesUtf8(@TempDir Path tempDir) throws IOException {
    Path file = tempDir.resolve("out.tsv");
    try (StrictPrintWriter writer = new StrictPrintWriter(file)) {
      writer.println("é");
    }
    assertEquals("é" + System.lineSeparator(), Files.readString(file));
  }


  /**
   * A target that throws {@code failure} from every write (or, if {@code onlyFlushFails}, only from flush), and
   * counts flushes.
   */
  private static class TargetWriter extends Writer {
    private final IOException m_failure;
    private final boolean m_onlyFlushFails;
    int flushes;

    TargetWriter(IOException failure, boolean onlyFlushFails) {
      m_failure = failure;
      m_onlyFlushFails = onlyFlushFails;
    }

    @Override
    public void write(char[] buf, int off, int len) throws IOException {
      if (m_failure != null && !m_onlyFlushFails) {
        throw m_failure;
      }
    }

    @Override
    public void flush() throws IOException {
      flushes++;
      if (m_failure != null) {
        throw m_failure;
      }
    }

    @Override
    public void close() {
    }
  }
}
