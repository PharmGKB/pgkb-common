package org.pharmgkb.common.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;


/**
 * A {@link PrintWriter} that does not swallow I/O errors. Use it instead of {@link PrintWriter}.
 * <p>
 * {@link PrintWriter} catches every {@link IOException} and only sets a flag, which {@link #checkError()} reports
 * without the cause, so a write that fails (a full disk, say) leaves a truncated file and no error. This class
 * records the first {@link IOException} its target throws and rethrows it, cause and all, as an
 * {@link UncheckedIOException} from the next write, {@code println()}, {@link #flush()} or {@link #close()}.
 * {@link #close()} matters most: the final flush of a buffered target happens there.
 * <p>
 * Checking costs nothing per write: nothing is flushed to find out, unlike calling {@link #checkError()}.
 *
 * @author Mark Woon
 */
public class StrictPrintWriter extends PrintWriter {
  private final RecordingWriter m_target;
  private final @Nullable Path m_file;


  /**
   * Writes to {@code file}, as UTF-8, naming it in any error.
   */
  public StrictPrintWriter(Path file) throws IOException {
    this(new RecordingWriter(Files.newBufferedWriter(file)), file);
  }

  public StrictPrintWriter(Writer out) {
    this(new RecordingWriter(out), null);
  }

  private StrictPrintWriter(RecordingWriter target, @Nullable Path file) {
    super(target);
    m_target = target;
    m_file = file;
  }


  @Override
  public void write(int c) {
    super.write(c);
    rethrow();
  }

  @Override
  public void write(char[] buf, int off, int len) {
    super.write(buf, off, len);
    rethrow();
  }

  @Override
  public void write(String s, int off, int len) {
    super.write(s, off, len);
    rethrow();
  }

  /**
   * {@link PrintWriter} writes the line separator straight to its target, past the write methods.
   */
  @Override
  public void println() {
    super.println();
    rethrow();
  }

  @Override
  public void flush() {
    super.flush();
    rethrow();
  }

  @Override
  public void close() {
    super.close();
    rethrow();
  }


  private void rethrow() {
    IOException failure = m_target.m_failure;
    if (failure != null) {
      if (m_file != null) {
        throw new UncheckedIOException("Failed to write " + m_file, failure);
      }
      throw new UncheckedIOException(failure);
    }
  }


  /**
   * Passes everything to its target and keeps the first {@link IOException} the target throws, which
   * {@link PrintWriter} would otherwise catch and discard.
   */
  private static class RecordingWriter extends Writer {
    private final Writer m_out;
    private @Nullable IOException m_failure;

    RecordingWriter(Writer out) {
      m_out = out;
    }

    @Override
    public void write(char[] buf, int off, int len) throws IOException {
      try {
        m_out.write(buf, off, len);
      } catch (IOException ex) {
        throw record(ex);
      }
    }

    @Override
    public void write(String str, int off, int len) throws IOException {
      try {
        m_out.write(str, off, len);
      } catch (IOException ex) {
        throw record(ex);
      }
    }

    @Override
    public void flush() throws IOException {
      try {
        m_out.flush();
      } catch (IOException ex) {
        throw record(ex);
      }
    }

    @Override
    public void close() throws IOException {
      try {
        m_out.close();
      } catch (IOException ex) {
        throw record(ex);
      }
    }

    private IOException record(IOException ex) {
      if (m_failure == null) {
        m_failure = ex;
      }
      return ex;
    }
  }
}
