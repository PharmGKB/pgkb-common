package org.pharmgkb.common.util;

import org.junit.Test;

import static org.junit.Assert.*;


/**
 * This is a JUnit test for {@link ExtendedEnumHelper}.
 *
 * @author Mark Woon
 */
public class ExtendedEnumHelperTest {


  @Test
  public void testCamelCaseFormat() {

    assertEquals("helloWorld", ExtendedEnumHelper.camelCaseFormat("Hello World"));
    assertEquals("helloWorld", ExtendedEnumHelper.camelCaseFormat("Hello, World"));
    assertEquals("helloWorld", ExtendedEnumHelper.camelCaseFormat("Hello, World!"));
    assertEquals("helloWorld", ExtendedEnumHelper.camelCaseFormat(" Hello - World"));
    try {
      ExtendedEnumHelper.camelCaseFormat("!!!");
      fail("Empty string");
    } catch (IllegalArgumentException ex) {
      // expected
    }
  }



  @Test
  public void testAdd() {

    ExtendedEnumHelper<Foo> enumHelper = new ExtendedEnumHelper<>(Foo.class);
    enumHelper.add(Foo.ONE, 1, "one", "One");
    try {
      enumHelper.add(Foo.ONE, 1, "one", "One");
      fail("Duplicate id");
    } catch (IllegalArgumentException ex) {
      assertTrue(ex.getMessage(), ex.getMessage().startsWith("Duplicate ID"));
    }

    try {
      enumHelper.add(Foo.ONE, 2, " ", "One");
      fail("Empty shortName");
    } catch (IllegalArgumentException ex) {
      assertTrue(ex.getMessage(), ex.getMessage().startsWith("Empty shortName"));
    }
    try {
      enumHelper.add(Foo.ONE, 2, "o n e", "One");
      fail("Spaces in shortName");
    } catch (IllegalArgumentException ex) {
      assertTrue(ex.getMessage(), ex.getMessage().startsWith("Spaces in shortName"));
    }
    try {
      enumHelper.add(Foo.ONE, 2, "1", "One");
      fail("Numeric shortName");
    } catch (IllegalArgumentException ex) {
      assertTrue(ex.getMessage(), ex.getMessage().startsWith("Numeric shortName"));
    }
    try {
      enumHelper.add(Foo.ONE, 2, "one", "One");
      fail("Duplicate shortName");
    } catch (IllegalArgumentException ex) {
      assertTrue(ex.getMessage(), ex.getMessage().startsWith("Duplicate shortName"));
    }

    try {
      enumHelper.add(Foo.ONE, 2, "two", "One");
      fail("Duplicate displayName");
    } catch (IllegalArgumentException ex) {
      assertTrue(ex.getMessage(), ex.getMessage().startsWith("Duplicate displayName"));
    }
  }


  private enum Foo implements ExtendedEnum {
    ONE,
    TWO;

    @Override
    public int getId() {
      return 0;
    }

    @Override
    public String getShortName() {
      return "shortName";
    }

    @Override
    public String getDisplayName() {
      return "displayName";
    }
  }
}
