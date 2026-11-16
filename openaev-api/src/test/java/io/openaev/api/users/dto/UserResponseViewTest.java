package io.openaev.api.users.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.openaev.database.model.User;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("The response view of a user")
class UserResponseViewTest {

  private static final String TENANT_ID = "tenant-of-the-request";

  private static List<Field> persistedFields() {
    return List.of(User.class.getDeclaredFields()).stream()
        .filter(field -> !Modifier.isStatic(field.getModifiers()))
        .filter(field -> !Modifier.isFinal(field.getModifiers()))
        .filter(field -> !field.isSynthetic())
        .filter(field -> !field.getName().equals("organizationTenantId"))
        .toList();
  }

  private static Object distinctValueOf(Field field) {
    Class<?> type = field.getType();
    if (type == String.class) {
      return "value-of-" + field.getName();
    } else if (type == String[].class) {
      return new String[] {"value-of-" + field.getName()};
    } else if (type == Short.class) {
      return (short) 77;
    } else if (type == Instant.class) {
      return Instant.ofEpochSecond(1_700_000_000L);
    } else if (type == boolean.class) {
      return true;
    } else if (type == List.class) {
      return new ArrayList<>();
    } else if (type == Set.class) {
      return new HashSet<>();
    }
    throw new IllegalStateException(
        "UserResponseViewTest does not know how to fill " + field.getName() + ": " + type);
  }

  @Nested
  @DisplayName("Building it")
  class Building {

    @Test
    @DisplayName("It carries every field of the user, so a field added later cannot be dropped")
    void given_aUserWithEveryFieldSet_should_copyEveryField() throws Exception {
      // -- PREPARE --
      User user = new User();
      for (Field field : persistedFields()) {
        field.setAccessible(true);
        field.set(user, distinctValueOf(field));
      }

      // -- EXECUTE --
      User view = UserMapper.toResponseView(user, TENANT_ID);

      // -- ASSERT --
      assertNotSame(user, view);
      for (Field field : persistedFields()) {
        field.setAccessible(true);
        Object expected = field.get(user);
        Object actual = field.get(view);
        if (expected instanceof String[] expectedArray) {
          assertEquals(List.of(expectedArray), List.of((String[]) actual), field.getName());
        } else if (expected instanceof List || expected instanceof Set) {
          assertSame(expected, actual, field.getName());
        } else {
          assertEquals(expected, actual, field.getName());
        }
      }
    }

    @Test
    @DisplayName("The tenant of the request stays on the view and never reaches the user itself")
    void given_aUser_should_keepTheTenantOnTheViewOnly() throws Exception {
      // -- PREPARE --
      User user = new User();
      Field tenantField = User.class.getDeclaredField("organizationTenantId");
      tenantField.setAccessible(true);

      // -- EXECUTE --
      User view = UserMapper.toResponseView(user, TENANT_ID);

      // -- ASSERT --
      assertNull(tenantField.get(user));
      assertEquals(TENANT_ID, tenantField.get(view));
    }
  }
}
