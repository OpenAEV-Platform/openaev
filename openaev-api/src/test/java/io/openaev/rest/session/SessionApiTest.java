package io.openaev.rest.session;

import static io.openaev.config.TenantUriUtils.TENANT_ID_PATH_VARIABLE;
import static io.openaev.rest.session.SessionApi.SESSION_URI;
import static io.openaev.rest.session.SessionApi.TENANT_SESSION_URI;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.multitenancy.DependenciesManagerException;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class SessionApiTest extends IntegrationTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private EntityManager entityManager;
  @Autowired private io.openaev.utils.mockUser.TestUserHolder testUserHolder;
  @Autowired private TenantIsolationTestHelper tenantIsolationTestHelper;
  @Autowired private UserComposer userComposer;

  @Nested
  @DisplayName("With no privileges")
  @WithMockUser
  class WithNoPrivileges {
    @Test
    @DisplayName("Can not read all active sessions")
    void canNotReadAllActiveSessions() throws Exception {
      mockMvc
          .perform(get(SESSION_URI).contentType(MediaType.APPLICATION_JSON).with(csrf()))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Can not read all specific user active sessions")
    void canNotReadSpecificUserActiveSessions() throws Exception {
      User me = testUserHolder.get();
      mockMvc
          .perform(
              get(SESSION_URI + "/user/" + me.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Can not delete specific active session")
    void canNotDeleteAllActiveSessions() throws Exception {
      mockMvc
          .perform(
              delete(SESSION_URI + "/" + UUID.randomUUID())
                  .contentType(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Can not delete all specific user active sessions")
    void canNotDeleteSpecificUserActiveSessions() throws Exception {
      User me = testUserHolder.get();
      mockMvc
          .perform(
              delete(SESSION_URI + "/user/" + me.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  @DisplayName("With privileges")
  @WithMockUser(withCapabilities = {Capability.MANAGE_SESSIONS})
  class WithPrivileges {

    @Nested
    @DisplayName("Within same tenant")
    class WithinSameTenant {
      @Test
      @DisplayName("Can read all active sessions")
      void canReadAllActiveSessions() throws Exception {
        mockMvc
            .perform(get(SESSION_URI).contentType(MediaType.APPLICATION_JSON).with(csrf()))
            .andExpect(status().isOk());
      }

      @Test
      @DisplayName("Can read all specific user active sessions")
      void canReadSpecificUserActiveSessions() throws Exception {
        User me = testUserHolder.get();
        mockMvc
            .perform(
                get(SESSION_URI + "/user/" + me.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().isOk());
      }

      @Test
      @DisplayName("Given an active session, then the listing names its owner")
      void given_anActiveSession_then_listingNamesItsOwner() throws Exception {
        User me = testUserHolder.get();
        String uri = tenantUri(TENANT_SESSION_URI);
        String sessionId = persistSessionFor(me);

        mockMvc
            .perform(get(uri).contentType(MediaType.APPLICATION_JSON).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$[?(@.session_id == '%s')].session_user_name".formatted(sessionId))
                    .value(hasItem(me.getNameOrEmail())));
      }

      private String persistSessionFor(User user) {
        String sessionId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        entityManager
            .createNativeQuery(
                "insert into spring_session(primary_id, session_id, creation_time,"
                    + " last_access_time, max_inactive_interval, expiry_time, principal_name)"
                    + " values (?, ?, ?, ?, ?, ?, ?)")
            .setParameter(1, UUID.randomUUID().toString())
            .setParameter(2, sessionId)
            .setParameter(3, now)
            .setParameter(4, now)
            .setParameter(5, 3600)
            .setParameter(6, now + 3_600_000)
            .setParameter(7, user.getId())
            .executeUpdate();
        return sessionId;
      }

      @Test
      @DisplayName("Given no active session, then can not find session to delete")
      void given_noActiveSession_then_canNotFindSessionToDelete() throws Exception {
        mockMvc
            .perform(
                delete(SESSION_URI + "/" + UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().isNotFound());
      }

      @Test
      @DisplayName("Given no active session, then can not find user sessions to delete")
      void given_noActiveSession_then_canNotFindUserSessionsToDelete() throws Exception {
        User me = testUserHolder.get();
        mockMvc
            .perform(
                delete(SESSION_URI + "/user/" + me.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().isNotFound());
      }
    }

    @Nested
    @DisplayName("Within other tenant")
    class WithinOtherTenant {
      private UserComposer.Composer getNewUserWrapper() {
        UserComposer.Composer wrapper = userComposer.forUser(UserFixture.getUser()).persist();
        entityManager.flush();
        entityManager.clear();
        return wrapper;
      }

      private Tenant getNewTenant() throws DependenciesManagerException {
        Tenant tenant =
            tenantIsolationTestHelper.createTenantWithCapabilities(
                "new-tenant", Set.of(Capability.MANAGE_SESSIONS));
        entityManager.flush();
        entityManager.clear();
        return tenant;
      }

      private String buildUri(Tenant tenant) {
        return TENANT_SESSION_URI.replace("{" + TENANT_ID_PATH_VARIABLE + "}", tenant.getId());
      }

      @Test
      @DisplayName("Can read all active sessions")
      void canReadAllActiveSessions() throws Exception {
        mockMvc
            .perform(
                get(buildUri(getNewTenant())).contentType(MediaType.APPLICATION_JSON).with(csrf()))
            .andExpect(status().isOk());
      }

      @Test
      @DisplayName("Can read all specific user active sessions")
      void canReadSpecificUserActiveSessions() throws Exception {
        User otherUser = getNewUserWrapper().get();
        mockMvc
            .perform(
                get(buildUri(getNewTenant()) + "/user/" + otherUser.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().isOk());
      }

      @Test
      @DisplayName("Given no active session, then can not find session to delete")
      void given_noActiveSession_then_canNotFindSessionToDelete() throws Exception {
        mockMvc
            .perform(
                delete(buildUri(getNewTenant()) + "/" + UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().isNotFound());
      }

      @Test
      @DisplayName("Given no active session, then can not find user sessions to delete")
      void given_noActiveSession_then_canNotFindUserSessionsToDelete() throws Exception {
        User otherUser = getNewUserWrapper().get();
        mockMvc
            .perform(
                delete(buildUri(getNewTenant()) + "/user/" + otherUser.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().isNotFound());
      }
    }
  }
}
