package org.pharmgkb.common.util;

import java.beans.PropertyDescriptor;
import java.util.List;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


/**
 * This is a JUnit test for {@link BeanProperties}.
 *
 * @author Mark Woon
 */
class BeanPropertiesTest {

  public static class Person {
    private final String m_name;
    private final @Nullable Person m_friend;

    public Person(String name, @Nullable Person friend) {
      m_name = name;
      m_friend = friend;
    }

    public String getName() {
      return m_name;
    }

    public @Nullable Person getFriend() {
      return m_friend;
    }

    public void setNickname(String nickname) {
      // write-only
    }

    public Object getAnything() {
      return new Person("Pet", null);
    }

    public String getBroken() {
      throw new UnsupportedOperationException("broken getter");
    }
  }

  public static class Pilot extends Person {
    public Pilot(String name, @Nullable Person friend) {
      super(name, friend);
    }

    public String getPower() {
      return "flight";
    }
  }


  @Test
  void getPropertiesListsReadablePropertiesOnly() {
    List<String> names = BeanProperties.getProperties(Person.class).stream()
        .map(PropertyDescriptor::getName)
        .sorted()
        .collect(Collectors.toList());
    assertEquals(List.of("anything", "broken", "friend", "name"), names);
  }


  @Test
  void getPropertyDescriptorFollowsAPath() {
    Person bob = new Person("Bob", new Person("Alice", null));

    PropertyDescriptor pd = BeanProperties.getPropertyDescriptor(bob, "name");
    assertNotNull(pd);
    assertEquals(String.class, pd.getPropertyType());

    pd = BeanProperties.getPropertyDescriptor(bob, "friend.name");
    assertNotNull(pd);
    assertEquals("name", pd.getName());
  }

  @Test
  void getPropertyDescriptorUsesEachValuesRuntimeClass() {
    // anything is declared as Object, but its value is a Person, so it has a name
    PropertyDescriptor pd = BeanProperties.getPropertyDescriptor(new Person("Bob", null), "anything.name");
    assertNotNull(pd);
    // and the bean itself is looked at by its runtime class too
    Person carol = new Pilot("Carol", null);
    assertNotNull(BeanProperties.getPropertyDescriptor(carol, "power"));
  }

  @Test
  void getPropertyDescriptorReturnsNullForAnUnknownProperty() {
    Person bob = new Person("Bob", new Person("Alice", null));
    assertNull(BeanProperties.getPropertyDescriptor(bob, "age"));
    assertNull(BeanProperties.getPropertyDescriptor(bob, "friend.age"));
    assertNull(BeanProperties.getPropertyDescriptor(bob, "age.years"));
    // write-only properties are not readable, so not found
    assertNull(BeanProperties.getPropertyDescriptor(bob, "nickname"));
  }

  @Test
  void getPropertyDescriptorRejectsANullPartWayAlong() {
    Person bob = new Person("Bob", null);
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> BeanProperties.getPropertyDescriptor(bob, "friend.name"));
    assertTrue(ex.getMessage().contains("friend.name"), ex.getMessage());
  }


  @Test
  void getPropertyReadsAPath() {
    Person bob = new Person("Bob", new Person("Alice", null));
    assertEquals("Bob", BeanProperties.getProperty(bob, "name"));
    assertEquals("Alice", BeanProperties.getProperty(bob, "friend.name"));
    assertNull(BeanProperties.getProperty(new Person("Bob", null), "friend"));
  }

  @Test
  void getPropertyRejectsAnUnknownPropertyAndANullPartWayAlong() {
    Person bob = new Person("Bob", null);
    assertThrows(IllegalArgumentException.class, () -> BeanProperties.getProperty(bob, "age"));
    assertThrows(IllegalArgumentException.class, () -> BeanProperties.getProperty(bob, "friend.name"));
  }

  @Test
  void getPropertyReportsAFailingGetter() {
    IllegalStateException ex = assertThrows(IllegalStateException.class,
        () -> BeanProperties.getProperty(new Person("Bob", null), "broken"));
    assertInstanceOf(UnsupportedOperationException.class, ex.getCause());
  }


  @Test
  void pathsAcceptPlainDottedNamesOnly() {
    Person bob = new Person("Bob", new Person("Alice", null));
    for (String path : new String[] {"class", "class.classLoader", "friend.class", "friend[0]", "friend(key)",
        "", ".", "friend.", ".name", "friend..name", "na me", "1name"}) {
      assertThrows(IllegalArgumentException.class, () -> BeanProperties.getPropertyDescriptor(bob, path), path);
      assertThrows(IllegalArgumentException.class, () -> BeanProperties.getProperty(bob, path), path);
    }
  }


  @Test
  void convertsStrings() {
    assertEquals("abc", BeanProperties.convert("abc", String.class));
    // not stripped: a String property is matched as given
    assertEquals(" abc ", BeanProperties.convert(" abc ", String.class));
  }

  @Test
  void convertsNumbersExactly() {
    assertEquals(12, BeanProperties.convert("12", int.class));
    assertEquals(12, BeanProperties.convert(" 12 ", Integer.class));
    assertEquals(12L, BeanProperties.convert("12", long.class));
    assertEquals(12L, BeanProperties.convert("12", Long.class));
    assertEquals((short)12, BeanProperties.convert("12", short.class));
    assertEquals((byte)12, BeanProperties.convert("12", Byte.class));
    assertEquals(1.5f, BeanProperties.convert("1.5", float.class));
    assertEquals(1.5f, BeanProperties.convert("1.5", Float.class));
    assertEquals(1.5d, BeanProperties.convert("1.5", double.class));
    assertEquals(1.5d, BeanProperties.convert("1.5", Double.class));

    for (String bad : new String[] {"abc", "1.5", "", "12abc", "99999999999"}) {
      assertThrows(IllegalArgumentException.class, () -> BeanProperties.convert(bad, Integer.class), bad);
    }
    assertThrows(IllegalArgumentException.class, () -> BeanProperties.convert("128", byte.class));
    assertThrows(IllegalArgumentException.class, () -> BeanProperties.convert("abc", double.class));
  }

  @Test
  void convertsBooleanWords() {
    for (String yes : new String[] {"true", "TRUE", "yes", "y", "on", "1", " True "}) {
      assertEquals(Boolean.TRUE, BeanProperties.convert(yes, boolean.class), yes);
      assertEquals(Boolean.TRUE, BeanProperties.convert(yes, Boolean.class), yes);
    }
    for (String no : new String[] {"false", "no", "N", "off", "0"}) {
      assertEquals(Boolean.FALSE, BeanProperties.convert(no, Boolean.class), no);
    }
    for (String bad : new String[] {"maybe", "12", "", "t"}) {
      assertThrows(IllegalArgumentException.class, () -> BeanProperties.convert(bad, Boolean.class), bad);
    }
  }

  @Test
  void convertsASingleCharacter() {
    assertEquals('a', BeanProperties.convert("a", char.class));
    assertEquals('a', BeanProperties.convert("a", Character.class));
    assertThrows(IllegalArgumentException.class, () -> BeanProperties.convert("abc", char.class));
    assertThrows(IllegalArgumentException.class, () -> BeanProperties.convert("", Character.class));
  }

  @Test
  void convertsExtendedEnumsByIdOrName() {
    assertEquals(Hero.Batman,
        BeanProperties.convert("3", Hero.class));
    assertEquals(Hero.Batman,
        BeanProperties.convert("Bruce", Hero.class));
    assertEquals(Hero.Superman,
        BeanProperties.convert("Clark Kent", Hero.class));

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> BeanProperties.convert("Beast", Hero.class));
    // names the accepted values, so a caller can pass the message on
    assertTrue(ex.getMessage().contains("Bruce"), ex.getMessage());
    assertThrows(IllegalArgumentException.class, () -> BeanProperties.convert("4", Hero.class));
  }

  @Test
  void anUnregisteredExtendedEnumIsAProgrammingError() {
    assertThrows(IllegalStateException.class, () -> BeanProperties.convert("one", Unregistered.class));
  }

  @Test
  void refusesTypesItCannotConvert() {
    assertFalse(BeanProperties.isConvertible(Person.class));
    assertFalse(BeanProperties.isConvertible(Object.class));
    assertFalse(BeanProperties.isConvertible(Class.class));
    assertFalse(BeanProperties.isConvertible(java.time.Instant.class));
    assertThrows(IllegalArgumentException.class, () -> BeanProperties.convert("x", Person.class));
    assertThrows(IllegalArgumentException.class, () -> BeanProperties.convert("java.lang.String", Class.class));

    assertTrue(BeanProperties.isConvertible(String.class));
    assertTrue(BeanProperties.isConvertible(int.class));
    assertTrue(BeanProperties.isConvertible(Boolean.class));
    assertTrue(BeanProperties.isConvertible(Hero.class));
  }


  private enum Unregistered implements ExtendedEnum {
    ONE;

    @Override
    public int getId() {
      return 1;
    }

    @Override
    public String getShortName() {
      return "one";
    }

    @Override
    public String getDisplayName() {
      return "One";
    }
  }
}
