package io.openaev.telemetry.metric_collectors;

import static io.openaev.integration.impl.secrets.local.LocalSecretsProviderIntegration.LOCAL_SECRETS_PROVIDER_ID;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.CredentialSecretReference.CREDENTIAL_AUTH_METHOD;
import io.openaev.database.model.CredentialSecretReference.CREDENTIAL_TYPE;
import io.openaev.database.model.SecretReference.SECRET_REFERENCE_TYPE;
import io.openaev.database.model.SecretReference.SECRET_STATUS;
import io.openaev.service.credential.CredentialService;
import io.opentelemetry.api.common.Attributes;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The credential gauges are deliberately platform-wide: telemetry is per-instance, not per-tenant.
 * {@code secret_references} is v2-active, so without the {@code TxCtx.allTenants()} scope opened by
 * {@link CredentialService} the inspector would make every row invisible and the gauges would
 * silently report zero.
 *
 * <p>The class is not {@code @Transactional}: the scoped primitive refuses to open inside an active
 * transaction, so seeding goes through an auto-committing {@link JdbcTemplate}, which also bypasses
 * the inspector and therefore sees every row whatever the scope.
 */
@DisplayName("the credential gauges count across tenants")
class CredentialMetricTenantScopeTest extends IntegrationTest {

  @Autowired private CredentialService credentialService;
  @Autowired private MetricRegistry metricRegistry;
  @Autowired private DataSource dataSource;

  /** Built here, never injected, so the test measures the real gauge and not a context mock. */
  private CredentialMetricCollector collector;

  private JdbcTemplate jdbc;
  private final List<String> seededTenants = new ArrayList<>();
  private final List<String> seededUsers = new ArrayList<>();
  private long credentialBaseline;
  private long injectBaseline;
  private long attachedCredentialBaseline;
  private long userBaseline;
  private long awsWithoutInjectorUsageBaseline;

  @BeforeEach
  void seedTwoTenantsWithOneCredentialAndInjectEach() {
    jdbc = new JdbcTemplate(dataSource);
    collector = new CredentialMetricCollector(metricRegistry, credentialService);
    // A delta, never an absolute count: the test database is shared, and this test must never
    // delete rows it did not create.
    credentialBaseline =
        requireNonNull(
            jdbc.queryForObject(
                "SELECT count(*) FROM secret_references WHERE secret_reference_type = ?",
                Long.class,
                SECRET_REFERENCE_TYPE.CREDENTIAL_VALUE));
    injectBaseline =
        requireNonNull(
            jdbc.queryForObject(
                "SELECT count(DISTINCT inject_id) FROM injects_secret_references", Long.class));
    attachedCredentialBaseline =
        requireNonNull(
            jdbc.queryForObject(
                "SELECT count(DISTINCT isr.secret_reference_id) FROM injects_secret_references isr"
                    + " JOIN secret_references sr"
                    + " ON sr.secret_reference_id = isr.secret_reference_id"
                    + " WHERE sr.secret_reference_type = ?",
                Long.class,
                SECRET_REFERENCE_TYPE.CREDENTIAL_VALUE));
    userBaseline =
        requireNonNull(
            jdbc.queryForObject(
                "SELECT count(DISTINCT i.inject_user) FROM injects i"
                    + " JOIN injects_secret_references isr ON isr.inject_id = i.inject_id"
                    + " JOIN secret_references sr"
                    + " ON sr.secret_reference_id = isr.secret_reference_id"
                    + " WHERE sr.secret_reference_type = ?",
                Long.class,
                SECRET_REFERENCE_TYPE.CREDENTIAL_VALUE));
    awsWithoutInjectorUsageBaseline =
        requireNonNull(
            jdbc.queryForObject(
                "SELECT count(*) FROM injects i"
                    + " JOIN injects_secret_references isr ON isr.inject_id = i.inject_id"
                    + " JOIN secret_references sr"
                    + " ON sr.secret_reference_id = isr.secret_reference_id"
                    + " WHERE sr.secret_reference_type = ? AND sr.secret_reference_credential_type = ?"
                    + " AND i.inject_injector IS NULL",
                Long.class,
                SECRET_REFERENCE_TYPE.CREDENTIAL_VALUE,
                CREDENTIAL_TYPE.CLOUD_AWS.name()));
    for (String name : List.of("credential-telemetry-a-", "credential-telemetry-b-")) {
      String tenantId = seedTenant(name + UUID.randomUUID());
      seedInjectUsing(tenantId, seedCredential(tenantId), seedUser(name));
    }
  }

  @AfterEach
  void cleanup() {
    for (String tenantId : seededTenants) {
      jdbc.update("DELETE FROM injects WHERE tenant_id = ?", tenantId);
      jdbc.update("DELETE FROM secret_references WHERE tenant_id = ?", tenantId);
      jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenantId);
    }
    seededTenants.clear();
    for (String userId : seededUsers) {
      jdbc.update("DELETE FROM users WHERE user_id = ?", userId);
    }
    seededUsers.clear();
  }

  @Nested
  @DisplayName("Credential breakdown gauge")
  class CredentialBreakdown {

    @Test
    @DisplayName("given credentials in two tenants, should count both")
    void given_credentialsInTwoTenants_should_countBoth() {
      // Act
      long counted =
          collector.collectCredentials().values().stream().mapToLong(Long::longValue).sum();

      // Assert
      assertThat(counted)
          .as("credentials_total must span every tenant, not only the ambient one")
          .isEqualTo(credentialBaseline + 2);
    }
  }

  @Nested
  @DisplayName("Injects with credentials gauge")
  class InjectsWithCredentials {

    @Test
    @DisplayName("given injects using credentials in two tenants, should count both")
    void given_injectsWithCredentialsInTwoTenants_should_countBoth() {
      // Act
      long counted = collector.countInjectsWithCredentials();

      // Assert
      assertThat(counted)
          .as("injects_with_credentials_total must span every tenant, not only the ambient one")
          .isEqualTo(injectBaseline + 2);
    }
  }

  @Nested
  @DisplayName("Credentials attached to injects gauge")
  class CredentialsAttachedToInjects {

    @Test
    @DisplayName("given credentials attached in two tenants, should count both")
    void given_credentialsAttachedInTwoTenants_should_countBoth() {
      // Act
      long counted = collector.countCredentialsAttachedToInjects();

      // Assert
      assertThat(counted)
          .as("credentials_attached_to_injects_total must span every tenant")
          .isEqualTo(attachedCredentialBaseline + 2);
    }
  }

  @Nested
  @DisplayName("Users with injects using credentials gauge")
  class UsersWithInjectsUsingCredentials {

    @Test
    @DisplayName("given an inject author in each of two tenants, should count both")
    void given_injectAuthorsInTwoTenants_should_countBoth() {
      // Act
      long counted = collector.countUsersWithInjectsUsingCredentials();

      // Assert
      assertThat(counted)
          .as("users_with_injects_using_credentials_total must span every tenant")
          .isEqualTo(userBaseline + 2);
    }
  }

  @Nested
  @DisplayName("Credential usage gauge")
  class CredentialUsages {

    @Test
    @DisplayName("given usages without injector in two tenants, should count both as unknown")
    void given_usagesWithoutInjectorInTwoTenants_should_countBothAsUnknown() {
      // Act
      Long counted =
          collector
              .collectCredentialUsages()
              .get(
                  Attributes.of(
                      stringKey("credential_type"),
                      CREDENTIAL_TYPE.CLOUD_AWS.name(),
                      stringKey("injector_type"),
                      "unknown"));

      // Assert
      assertThat(counted)
          .as("credential_usages_total must span every tenant, not only the ambient one")
          .isEqualTo(awsWithoutInjectorUsageBaseline + 2);
    }
  }

  private String seedUser(String emailPrefix) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO users (user_id, user_email, user_admin, user_status) VALUES (?, ?, false, 0)",
        id,
        emailPrefix + id + "@openaev.test");
    seededUsers.add(id);
    return id;
  }

  private String seedTenant(String name) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        id,
        name);
    seededTenants.add(id);
    return id;
  }

  private String seedCredential(String tenantId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO secret_references (secret_reference_id, secret_reference_type,"
            + " secret_reference_name, secret_reference_connector_instance_id,"
            + " secret_reference_status, secret_reference_credential_type,"
            + " secret_reference_credential_auth_method, tenant_id)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        id,
        SECRET_REFERENCE_TYPE.CREDENTIAL_VALUE,
        "credential-" + id,
        LOCAL_SECRETS_PROVIDER_ID,
        SECRET_STATUS.ACTIVE.name(),
        CREDENTIAL_TYPE.CLOUD_AWS.name(),
        CREDENTIAL_AUTH_METHOD.AWS_ACCESS_KEY.name(),
        tenantId);
    return id;
  }

  private void seedInjectUsing(String tenantId, String credentialId, String userId) {
    String injectId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO injects (inject_id, inject_title, inject_all_teams, inject_enabled,"
            + " inject_depends_duration, inject_user, tenant_id)"
            + " VALUES (?, 'credential-telemetry-inject', false, true, 0, ?, ?)",
        injectId,
        userId,
        tenantId);
    jdbc.update(
        "INSERT INTO injects_secret_references (inject_id, secret_reference_id) VALUES (?, ?)",
        injectId,
        credentialId);
  }
}
