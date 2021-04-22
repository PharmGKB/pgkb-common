package org.pharmgkb.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


/**
 * This is a JUnit test for {@link ExtendedEnumHelper}.
 *
 * @author Mark Woon
 */
class ExtendedEnumHelperTest {


  @Test
  void testCamelCaseFormat() {

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
  void testGetAllSorted() {
    ExtendedEnumHelper<Hero> helper = ExtendedEnumHelper.getExtendedEnumHelper(Hero.class);
    assertArrayEquals(
        new Hero[] {Hero.WonderWoman, Hero.Superman, Hero.Batman},
        helper.getAllSortedById().toArray(new Hero[0])
    );
    assertArrayEquals(
        new Hero[] {Hero.Batman, Hero.Superman, Hero.WonderWoman},
        helper.getAllSortedByName().toArray(new Hero[0])
    );
  }

  @Test
  void testLookup() {
    ExtendedEnumHelper<Hero> helper = ExtendedEnumHelper.getExtendedEnumHelper(Hero.class);

    assertEquals(Hero.WonderWoman, helper.lookupByName("Diana"));
    assertEquals(Hero.WonderWoman, helper.lookupByName("Diana Prince"));
    assertEquals(Hero.WonderWoman, helper.lookupByName("diana"));
    assertNull(helper.lookupByName("1"));
    assertNull(helper.lookupByName("Diana Ross"));

    assertEquals(Hero.WonderWoman, helper.lookupByNameOrThrow("Diana"));
    assertEquals(Hero.WonderWoman, helper.lookupByNameOrThrow("Diana Prince"));
    assertEquals(Hero.WonderWoman, helper.lookupByNameOrThrow("diana"));
    assertEquals(Hero.WonderWoman, helper.fromString("1"));
    assertThrows(IllegalArgumentException.class, () -> {
      helper.lookupByNameOrThrow("1");
    });
    assertThrows(IllegalArgumentException.class, () -> {
      helper.lookupByNameOrThrow("Diana Ross");
    });

    assertEquals(Hero.WonderWoman, helper.fromString("Diana"));
    assertEquals(Hero.WonderWoman, helper.fromString("Diana Prince"));
    assertEquals(Hero.WonderWoman, helper.fromString("diana"));
    assertEquals(Hero.WonderWoman, helper.fromString("1"));
    assertNull(helper.lookupByName("Diana Ross"));
  }


  @Test
  void testAdd() {

    ExtendedEnumHelper<Foo> enumHelper = new ExtendedEnumHelper<>(Foo.class);
    enumHelper.add(Foo.ONE, 1, "one", "One");
    try {
      enumHelper.add(Foo.ONE, 1, "one", "One");
      fail("Duplicate id");
    } catch (IllegalArgumentException ex) {
      assertTrue(ex.getMessage().startsWith("Duplicate ID"), ex.getMessage());
    }

    try {
      enumHelper.add(Foo.ONE, 2, " ", "One");
      fail("Empty shortName");
    } catch (IllegalArgumentException ex) {
      assertTrue(ex.getMessage().startsWith("Empty shortName"), ex.getMessage());
    }
    try {
      enumHelper.add(Foo.ONE, 2, "o n e", "One");
      fail("Spaces in shortName");
    } catch (IllegalArgumentException ex) {
      assertTrue(ex.getMessage().startsWith("Spaces in shortName"), ex.getMessage());
    }
    try {
      enumHelper.add(Foo.ONE, 2, "1", "One");
      fail("Numeric shortName");
    } catch (IllegalArgumentException ex) {
      assertTrue(ex.getMessage().startsWith("Numeric shortName"), ex.getMessage());
    }
    try {
      enumHelper.add(Foo.ONE, 2, "one", "One");
      fail("Duplicate shortName");
    } catch (IllegalArgumentException ex) {
      assertTrue(ex.getMessage().startsWith("Duplicate shortName"), ex.getMessage());
    }

    try {
      enumHelper.add(Foo.ONE, 2, "two", "One");
      fail("Duplicate displayName");
    } catch (IllegalArgumentException ex) {
      assertTrue(ex.getMessage().startsWith("Duplicate displayName"), ex.getMessage());
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
