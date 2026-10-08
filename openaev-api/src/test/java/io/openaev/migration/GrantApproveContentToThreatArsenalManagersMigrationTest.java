package io.openaev.migration;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Tenant;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies the upgrade that gives "Approve content" to tenant roles that can manage the threat
 * arsenal: who receives it, who does not, idempotence and the upgrade log.
 *
 * <p>Roles are seeded with native SQL so their stored capabilities are exactly the ones under test
 * (the entity would add implied parents). {@code @Transactional} rolls everything back.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Grant approve content to threat arsenal managers migration")
class GrantApproveContentToThreatArsenalManagersMigrationTest extends IntegrationTest {

  @Autowired
  private V6_20261008160000000__Grant_approve_content_to_threat_arsenal_managers migration;

  /** {@code tenantId} null seeds a platform role. */
  private String insertRole(String name, String tenantId, Capability... capabilities) {
    String roleId = UUID.randomUUID().toString();
    if (tenantId == null) {
      // Literal NULL: an untyped null parameter cannot be bound
      entityManager
          .createNativeQuery(
              "INSERT INTO roles (role_id, role_name, tenant_id) VALUES (:id, :name, NULL)")
          .setParameter("id", roleId)
          .setParameter("name", name)
          .executeUpdate();
    } else {
      entityManager
          .createNativeQuery(
              "INSERT INTO roles (role_id, role_name, tenant_id) VALUES (:id, :name, :tenant)")
          .setParameter("id", roleId)
          .setParameter("name", name)
          .setParameter("tenant", tenantId)
          .executeUpdate();
    }
    for (Capability capability : capabilities) {
      entityManager
          .createNativeQuery(
              "INSERT INTO roles_capabilities (role_id, capability) VALUES (:id, :capability)")
          .setParameter("id", roleId)
          .setParameter("capability", capability.name())
          .executeUpdate();
    }
    return roleId;
  }

  private String tenantRole(String name, Capability... capabilities) {
    return insertRole(name, Tenant.DEFAULT_TENANT_UUID, capabilities);
  }

  @SuppressWarnings("unchecked")
  private List<String> capabilitiesOf(String roleId) {
    return entityManager
        .createNativeQuery(
            "SELECT capability FROM roles_capabilities WHERE role_id = :id ORDER BY capability")
        .setParameter("id", roleId)
        .getResultList();
  }

  private List<String> sorted(Capability... capabilities) {
    return Arrays.stream(capabilities).map(Capability::name).sorted().toList();
  }

  private List<String> runMigration() {
    List<String> updated = new ArrayList<>();
    entityManager
        .unwrap(Session.class)
        .doWork(
            connection -> {
              try {
                updated.addAll(migration.grant(connection));
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
    return updated;
  }

  @Nested
  @DisplayName("Who receives approve content")
  class WhoReceivesIt {

    @Test
    @DisplayName("given_tenantRoleWithManage_should_receiveApproveContent")
    void given_tenantRoleWithManage_should_receiveApproveContent() {
      // -- ARRANGE --
      String authors =
          tenantRole(
              "Authors", Capability.ACCESS_THREAT_ARSENALS, Capability.MANAGE_THREAT_ARSENALS);

      // -- ACT --
      List<String> updated = runMigration();

      // -- ASSERT --
      assertThat(updated).contains(authors);
      assertThat(capabilitiesOf(authors))
          .containsExactlyElementsOf(
              sorted(
                  Capability.ACCESS_THREAT_ARSENALS,
                  Capability.MANAGE_THREAT_ARSENALS,
                  Capability.APPROVE_THREAT_ARSENALS));
    }

    @Test
    @DisplayName("given_tenantRoleWithOnlyDelete_should_receiveApproveContentAndAccess")
    void given_tenantRoleWithOnlyDelete_should_receiveApproveContentAndAccess() {
      // -- ARRANGE --
      // Delete implies Manage, but older roles may hold it without its parents stored
      String cleaners = tenantRole("Cleaners", Capability.DELETE_THREAT_ARSENALS);

      // -- ACT --
      runMigration();

      // -- ASSERT --
      assertThat(capabilitiesOf(cleaners))
          .containsExactlyElementsOf(
              sorted(
                  Capability.DELETE_THREAT_ARSENALS,
                  Capability.APPROVE_THREAT_ARSENALS,
                  Capability.ACCESS_THREAT_ARSENALS));
    }

    @Test
    @DisplayName("given_tenantRoleWithoutManage_should_stayUnchanged")
    void given_tenantRoleWithoutManage_should_stayUnchanged() {
      // -- ARRANGE --
      String readers =
          tenantRole("Readers", Capability.ACCESS_THREAT_ARSENALS, Capability.ACCESS_ASSESSMENT);

      // -- ACT --
      List<String> updated = runMigration();

      // -- ASSERT --
      assertThat(updated).doesNotContain(readers);
      assertThat(capabilitiesOf(readers))
          .containsExactlyElementsOf(
              sorted(Capability.ACCESS_THREAT_ARSENALS, Capability.ACCESS_ASSESSMENT));
    }

    @Test
    @DisplayName("given_platformRoleWithManage_should_stayUnchanged")
    void given_platformRoleWithManage_should_stayUnchanged() {
      // -- ARRANGE --
      // Cannot happen through the API (tenant-only capability): seeded to prove the scope filter
      String platform =
          insertRole(
              "Platform authors",
              null,
              Capability.ACCESS_THREAT_ARSENALS,
              Capability.MANAGE_THREAT_ARSENALS);

      // -- ACT --
      List<String> updated = runMigration();

      // -- ASSERT --
      assertThat(updated).doesNotContain(platform);
      assertThat(capabilitiesOf(platform))
          .containsExactlyElementsOf(
              sorted(Capability.ACCESS_THREAT_ARSENALS, Capability.MANAGE_THREAT_ARSENALS));
    }
  }

  @Nested
  @DisplayName("Idempotence and trace")
  class IdempotenceAndTrace {

    @Test
    @DisplayName("given_secondRun_should_changeNothing")
    void given_secondRun_should_changeNothing() {
      // -- ARRANGE --
      String authors =
          tenantRole(
              "Authors", Capability.ACCESS_THREAT_ARSENALS, Capability.MANAGE_THREAT_ARSENALS);
      runMigration();
      List<String> afterFirstRun = capabilitiesOf(authors);

      // -- ACT --
      List<String> updated = runMigration();

      // -- ASSERT --
      assertThat(updated).isEmpty();
      assertThat(capabilitiesOf(authors)).isEqualTo(afterFirstRun);
    }

    // Tests log at ERROR only: capture this migration's INFO lines directly
    private Logger logger;
    private Level originalLevel;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void captureLogs() {
      logger =
          (Logger)
              LoggerFactory.getLogger(
                  V6_20261008160000000__Grant_approve_content_to_threat_arsenal_managers.class);
      originalLevel = logger.getLevel();
      logger.setLevel(Level.INFO);
      logs = new ListAppender<>();
      logs.start();
      logger.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
      logger.detachAppender(logs);
      logger.setLevel(originalLevel);
    }

    @Test
    @DisplayName("given_updatedRoles_should_logThemThenLogNothingToUpdate")
    void given_updatedRoles_should_logThemThenLogNothingToUpdate() {
      // -- ARRANGE --
      String authors =
          tenantRole(
              "Trace authors",
              Capability.ACCESS_THREAT_ARSENALS,
              Capability.MANAGE_THREAT_ARSENALS);

      // -- ACT --
      runMigration();
      runMigration();

      // -- ASSERT --
      List<String> messages = logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
      assertThat(messages).hasSize(2);
      assertThat(messages.get(0))
          .startsWith("Approve content granted to")
          .contains(
              "Trace authors (id " + authors + ", tenant " + Tenant.DEFAULT_TENANT_UUID + ")");
      assertThat(messages.get(1)).isEqualTo("Approve content: no tenant role to update");
    }
  }
}
