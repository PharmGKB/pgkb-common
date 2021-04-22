package org.pharmgkb.common.util;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.stream.events.XMLEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


/**
 * This is a JUnit test for {@link StaxReader}.
 *
 * @author Mark Woon
 */
public class StaxReaderTest {

  @Test
  void simpleTest() throws Exception {
    Path file = PathUtils.getPathToResource(getClass(), "StaxReaderTest.xml");

    try (StaxReader reader = new StaxReader(file)) {
      assertTrue(reader.hasNext());
      List<String> entities = new ArrayList<>();
      while (reader.hasNext()) {
        int code = reader.next();
        if (code == XMLEvent.START_ELEMENT) {
          entities.add(reader.getLocalName());
          switch (reader.getLocalName()) {
            case "foo":
              assertNull(reader.getTextTrimmedToNull());
              break;
            case "bar":
              assertEquals("hello", reader.getTextTrimmedToNull());
              break;
            case "baz":
              assertEquals("world", reader.getTextTrimmedToNull());
              break;
          }
        }
      }

      assertArrayEquals(new String[] {"foo", "bar", "baz"}, entities.toArray(new String[0]));
    }
  }

  @Test
  void testStartElement() throws Exception {
    Path file = PathUtils.getPathToResource(getClass(), "StaxReaderTest.xml");

    try (StaxReader reader = new StaxReader(file)) {
      StaxReader subReader = reader.startElement("baz");
      assertNotNull(subReader);
      assertEquals("baz", subReader.getLocalName());
      assertEquals("1", subReader.getAttributeValue("z"));
    }
  }


  @Test
  void testStartElementUnless() throws Exception {
    Path file = PathUtils.getPathToResource(getClass(), "StaxReaderTest.xml");

    try (StaxReader reader = new StaxReader(file)) {
      assertNull(reader.startElementUnless("baz", "bar"));
    }
  }
}
