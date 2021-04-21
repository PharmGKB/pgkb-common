package org.pharmgkb.common.util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


/**
 * Simple InputStream for reading a zipped file (i.e. zip file only contains itself, like a gzipped file).
 *
 * @author Mark Woon
 */
public class ZippedFileInputStream extends InputStream {
  private ZipInputStream m_zipInputStream;


  public ZippedFileInputStream(Path zipFile) throws IOException {
    String zipFilename = zipFile.getName(zipFile.getNameCount() - 1).toString();
    if (!zipFilename.toLowerCase().endsWith(".zip")) {
      throw new IllegalArgumentException("File does not end with .zip");
    }

    String baseFilename = zipFilename.substring(0, zipFilename.length() - 4);
    findFile(Files.newInputStream(zipFile), baseFilename);
  }


  public ZippedFileInputStream(Path zipFile, String filename) throws IOException {
    String zipFilename = zipFile.getName(zipFile.getNameCount() - 1).toString();
    if (!zipFilename.toLowerCase().endsWith(".zip")) {
      throw new IllegalArgumentException("File does not end with .zip");
    }

    findFile(Files.newInputStream(zipFile), filename);
  }


  public ZippedFileInputStream(InputStream in, String filename) throws IOException {
    findFile(in, filename);
  }


  private void findFile(InputStream in, String filename) throws IOException {
    if (in instanceof ZipInputStream) {
      m_zipInputStream = (ZipInputStream)in;
    } else {
      m_zipInputStream = new ZipInputStream(in);
    }
    boolean foundFile = false;
    ZipEntry entry;
    while((entry = m_zipInputStream.getNextEntry()) != null) {
      if (entry.getName().equals(filename)) {
        foundFile = true;
        break;
      }
    }
    if (!foundFile) {
      throw new FileNotFoundException("Cannot find " + filename + " in zipped file");
    }
  }




  @Override
  public int available() throws IOException {
    return m_zipInputStream.available();
  }

  @Override
  public void close() throws IOException {
    m_zipInputStream.close();
  }

  @Override
  public synchronized void mark(int readlimit) {
    m_zipInputStream.mark(readlimit);
  }

  @Override
  public boolean markSupported() {
    return m_zipInputStream.markSupported();
  }

  @Override
  public int read() throws IOException {
    return m_zipInputStream.read();
  }

  @Override
  public int read(byte[] b) throws IOException {
    return m_zipInputStream.read(b);
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    return m_zipInputStream.read(b, off, len);
  }

  @Override
  public synchronized void reset() throws IOException {
    m_zipInputStream.reset();
  }

  @Override
  public long skip(long n) throws IOException {
    return m_zipInputStream.skip(n);
  }
}
