package io.openaev.api.users.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.IntegrationTest;
import io.openaev.database.model.User;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.UserComposer;
import org.hibernate.Hibernate;
import org.hibernate.proxy.HibernateProxy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@DisplayName("The response view of a user held as a Hibernate proxy")
class UserResponseViewProxyTest extends IntegrationTest {

  private static final String TENANT_ID = "tenant-of-the-request";

  @Autowired private UserComposer userComposer;

  @Nested
  @DisplayName("Building it")
  class Building {

    @Test
    @DisplayName("It copies the fields of the user behind an uninitialised proxy")
    void given_anUninitialisedProxyOfASavedUser_should_copyTheFieldsOfTheUser() {
      // -- PREPARE --
      User saved = userComposer.forUser(UserFixture.getUserWithDefaultEmail()).persist().get();
      entityManager.flush();
      entityManager.clear();
      User proxy = entityManager.getReference(User.class, saved.getId());
      assertTrue(proxy instanceof HibernateProxy, "the fixture must hand over a proxy");
      assertFalse(Hibernate.isInitialized(proxy), "the proxy must not be initialised yet");

      // -- EXECUTE --
      User view = UserMapper.toResponseView(proxy, TENANT_ID);

      // -- ASSERT --
      assertNotSame(proxy, view);
      assertSame(User.class, view.getClass());
      assertEquals(saved.getId(), view.getId());
      assertEquals(saved.getEmail(), view.getEmail());
      assertEquals(saved.getFirstname(), view.getFirstname());
      assertEquals(saved.getLastname(), view.getLastname());
      assertEquals(TENANT_ID, ReflectionTestUtils.getField(view, "organizationTenantId"));
      assertTrue(Hibernate.isInitialized(proxy), "the copy initialises the proxy");
    }
  }
}
