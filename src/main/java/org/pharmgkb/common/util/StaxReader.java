package org.pharmgkb.common.util;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.XMLEvent;
import com.google.common.io.Closeables;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Convenience wrapper around {@link XMLStreamReader} using the Cursor API.
 * <p>
 * Stax References:
 * <ul>
 * <li><a href="http://docs.oracle.com/cd/E17802_01/webservices/webservices/docs/1.6/tutorial/doc/SJSXP3.html#wp101587">http://docs.oracle.com/cd/E17802_01/webservices/webservices/docs/1.6/tutorial/doc/SJSXP3.html#wp101587</a>
 * <li><a href="http://today.java.net/pub/a/today/2006/07/20/introduction-to-stax.html">http://today.java.net/pub/a/today/2006/07/20/introduction-to-stax.html</a></li>
 * </ul>
 *
 * @author Mark Woon
 */
public class StaxReader implements Closeable, AutoCloseable {
  private static final Logger sf_logger = LoggerFactory.getLogger(StaxReader.class);
  private final InputStream m_inputStream;
  private final XMLStreamReader m_xmlReader;


  public StaxReader(File file) throws IOException {
    this(file.toPath());
  }

  public StaxReader(Path file) throws IOException {
    try {
      m_inputStream = StreamUtils.openInputStream(file);
      m_xmlReader = XMLInputFactory.newInstance().createXMLStreamReader(m_inputStream);
    } catch (XMLStreamException ex) {
      throw new IOException("Error reading " + file, ex);
    }
  }


  @Override
  public void close() {

    try {
      m_xmlReader.close();
    } catch (Exception ex) {
      sf_logger.warn("Error closing XML reader", ex);
    }
    Closeables.closeQuietly(m_inputStream);
  }


  /**
   * See {@link XMLStreamReader#hasNext()}.
   */
  public boolean hasNext() throws XMLStreamException {
    return m_xmlReader.hasNext();
  }

  /**
   * See {@link XMLStreamReader#next()}.
   */
  public int next() throws XMLStreamException {
    return m_xmlReader.next();
  }


  /**
   * Gets the value for the named attribute.
   *
   * @param attName attribute name (case-insensitive)
   * @return the attribute value if it's available, null otherwise
   */
  public @Nullable String getAttributeValue(String attName) {

    for (int x = 0; x < m_xmlReader.getAttributeCount(); x++) {
      if (m_xmlReader.getAttributeLocalName(x).equalsIgnoreCase(attName)) {
        return StringUtils.stripToNull(m_xmlReader.getAttributeValue(x));
      }
    }
    return null;
  }


  /**
   * See {@link XMLStreamReader#getLocalName()}.
   */
  public String getLocalName() {
    return m_xmlReader.getLocalName();
  }


  /**
   * See {@link XMLStreamReader#getText()}.
   */
  public @Nullable String getTextTrimmedToNull() throws XMLStreamException {

    while (m_xmlReader.hasNext()) {
      switch (m_xmlReader.next()) {
        case XMLEvent.CHARACTERS:
          return StringUtils.trimToNull(m_xmlReader.getText());
        case XMLEvent.END_ELEMENT:
          return null;
      }
    }
    return null;
  }


  /**
   * Proceeds to the next start element with the given name.
   */
  public @Nullable StaxReader startElement(String name) throws XMLStreamException {
    return startElement(name, null);
  }

  /**
   * Proceeds to the next start element with the given name, unless the closing parent element is encountered first.
   */
  public @Nullable StaxReader startElement(String name, @Nullable String parentName) throws XMLStreamException {
    while (m_xmlReader.hasNext()) {
      int code = m_xmlReader.next();
      switch (code) {
        case XMLEvent.START_ELEMENT:
          if (name.equals(m_xmlReader.getLocalName())) {
            return this;
          }
          break;
        case XMLEvent.END_ELEMENT:
          if (parentName != null && parentName.equals(m_xmlReader.getLocalName())) {
            return null;
          }
      }
    }
    return null;
  }

  /**
   * Proceeds to the next start element with the given name, unless a start element in <code>untilStartNames</code> is
   * encountered or the closing parent element is encountered.
   */
  public @Nullable StaxReader startElementUnless(String name, @Nullable String parentName,
      String... untilStartNames) throws XMLStreamException {
    while (m_xmlReader.hasNext()) {
      int code = m_xmlReader.next();
      switch (code) {
        case XMLEvent.START_ELEMENT:
          String localName = m_xmlReader.getLocalName();
          if (name.equals(localName)) {
            return this;
          }
          for (String n : untilStartNames) {
            if (n.equals(localName)) {
              return null;
            }
          }
          break;
        case XMLEvent.END_ELEMENT:
          if (parentName != null && parentName.equals(m_xmlReader.getLocalName())) {
            return null;
          }
      }
    }
    return null;
  }


  /**
   * Proceed to the next end element with the given name.
   */
  public @Nullable StaxReader endElement(String name) throws XMLStreamException {
    while (m_xmlReader.hasNext()) {
      if (m_xmlReader.next() == XMLEvent.END_ELEMENT && name.equals(m_xmlReader.getLocalName())) {
        return this;
      }
    }
    return null;
  }
}
