package io.openaev.scheduler.jobs;

import static io.openaev.database.model.CredentialSecretReference.CREDENTIAL_AUTH_METHOD.AZURE_MANAGED_IDENTITY;
import static io.openaev.database.model.SecretReference.SECRET_STATUS.UNSET;
import static io.openaev.integration.impl.secrets.local.LocalSecretsProviderIntegration.LOCAL_SECRETS_PROVIDER_ID;
import static org.junit.jupiter.api.Assertions.*;

import com.azure.core.credential.TokenCredential;
import com.azure.core.management.AzureEnvironment;
import com.azure.identity.CredentialUnavailableException;
import io.openaev.IntegrationTest;
import io.openaev.database.model.AzureEnvironments;
import io.openaev.database.model.CredentialSecretReference.CREDENTIAL_AUTH_METHOD;
import io.openaev.database.model.CredentialSecretReference.CREDENTIAL_TYPE;
import io.openaev.database.model.Secret.SECRET_TYPE;
import io.openaev.database.model.SecretReference.SECRET_REFERENCE_TYPE;
import io.openaev.secrets.provider.impl.validators.AzureCredentialConnectivityCheckFactory;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

/**
 * End-to-end proof of the credential status validation job, including its tenant isolation.
 *
 * <p>Deliberately NOT {@code @Transactional}: the job opens its own transactions through {@code
 * TenantScopedTransaction}, whose {@code execute} refuses to run inside an active one. Seeding and
 * cleanup therefore run in auto-committed JDBC, following {@code
 * TenantScopedTransactionIntegrationTest}.
 *
 * <p>Isolation is asserted the same way the tenant-isolation skill prescribes for HTTP endpoints,
 * adapted to a background job: two tenants are seeded with a native INSERT carrying an explicit
 * {@code tenant_id}, and every assertion reads GROUND TRUTH through raw JDBC on the test's own
 * connection — never through the entity manager, whose SQL the statement inspector rewrites, which
 * would make the assertion agree with the code instead of checking it.
 *
 * <p>{@code secret_references} and {@code secrets} are live in production's {@code active-tables},
 * but the test classpath keeps the allowlist empty, hence the explicit property here.
 *
 * <p>{@code CREDENTIAL_ASSET} is required too: the local secrets provider is spawned only when that
 * preview feature is on ({@code LocalSecretsProviderIntegrationFactory#findRelatedInstances}
 * returns nothing otherwise), so without it every probe short-circuits on {@code
 * PROVIDER_NOT_FOUND} and no credential is ever verified.
 *
 * <p>The Azure SDK is stubbed, deliberately (see {@link OutsideAzureConfiguration}): the real
 * factory dials IMDS, whose failure mode is NOT stable — a refused socket surfaces as {@code
 * CredentialUnavailableException} (inconclusive) while a hanging one surfaces as a bare {@code
 * ClientAuthenticationException} with no HTTP response, which {@code
 * AzureCredentialConnectivityCheck} reads as an outright rejection. Both are legitimate readings of
 * the SDK, so leaving the choice to the machine made this class pass alone and flip to {@code
 * INACTIVE} under the load of a full suite run. What is under test here is the job's plumbing, not
 * the SDK's networking, so the "outside Azure" condition is injected rather than provoked.
 */
@TestPropertySource(
    properties = {
      "openaev.tenant.active-tables=secret_references,secrets",
      "openaev.enabled-dev-features=CREDENTIAL_ASSET"
    })
@Import(CredentialConnectivityCheckJobIntegrationTest.OutsideAzureConfiguration.class)
@DisplayName("CredentialsStatusValidatorJob tests")
class CredentialConnectivityCheckJobIntegrationTest extends IntegrationTest {

  /**
   * Pins every Azure probe to the one outcome a deployment running outside Azure gets: the identity
   * endpoint cannot be reached, so the credential is inconclusive and its status must be preserved.
   *
   * <p>{@code @Primary} rather than a mock bean: the stub is pure behaviour with no verification on
   * it, and a plain bean keeps the context definition explicit and shareable across the class.
   */
  @TestConfiguration
  static class OutsideAzureConfiguration {

    @Bean
    @Primary
    AzureCredentialConnectivityCheckFactory unreachableAzureFactory() {
      TokenCredential unreachable =
          request ->
              Mono.error(
                  new CredentialUnavailableException(
                      "ManagedIdentityCredential is unavailable in tests"));
      return new AzureCredentialConnectivityCheckFactory() {
        @Override
        public TokenCredential forServicePrincipal(
            AzureEnvironment environment, String tenantId, String clientId, String clientSecret) {
          return unreachable;
        }

        @Override
        public TokenCredential forManagedIdentity(String clientId) {
          return unreachable;
        }
      };
    }
  }

  /**
   * The public Azure cloud, taken from {@link AzureEnvironments} rather than hardcoded: the handler
   * rejects any name the SDK does not know, so a cloud retired upstream would silently turn the
   * probe into an {@code INVALID_CONFIGURATION} outcome.
   */
  private static final String AZURE_CLOUD = AzureEnvironments.names().getFirst();

  @Autowired private CredentialConnectivityCheckJob job;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private String tenantA;
  private String tenantB;
  private String referenceA;
  private String referenceB;

  @BeforeEach
  void seedTwoTenantsWithOneCredentialEach() {
    jdbc = new JdbcTemplate(dataSource);
    tenantA = seedTenant("credential-validation-a-" + UUID.randomUUID());
    tenantB = seedTenant("credential-validation-b-" + UUID.randomUUID());
    // Never verified, so both are due on the very next run.
    referenceA = seedCredential(tenantA, AZURE_MANAGED_IDENTITY);
    referenceB = seedCredential(tenantB, AZURE_MANAGED_IDENTITY);
    setEnabled(true);
  }

  @AfterEach
  void cleanup() {
    jdbc.update("DELETE FROM secret_references WHERE tenant_id IN (?, ?)", tenantA, tenantB);
    jdbc.update("DELETE FROM secrets WHERE tenant_id IN (?, ?)", tenantA, tenantB);
    jdbc.update("DELETE FROM tenants WHERE tenant_id IN (?, ?)", tenantA, tenantB);
  }

  @Nested
  @DisplayName("Running the job")
  class Running {

    @Test
    @DisplayName("Every tenant's due credentials are verified in the same run")
    void given_credentialsInSeveralTenants_should_verifyThemAll() throws Exception {
      // Arrange — done in the fixture: one never-verified credential per tenant.

      // Act
      job.execute(null);

      // Assert — the identity endpoint is unreachable, so the managed identity probe is
      // inconclusive: the status stays UNSET while the attempt IS stamped. That combination is
      // precisely the "transient failure must not flip a credential" contract, observed end to end.
      assertNotNull(lastVerifiedAt(referenceA), "tenant A's credential was verified");
      assertNotNull(lastVerifiedAt(referenceB), "tenant B's credential was verified");
      assertEquals(UNSET.name(), status(referenceA), "an inconclusive probe keeps the status");
      assertEquals(UNSET.name(), status(referenceB), "an inconclusive probe keeps the status");
    }

    @Test
    @DisplayName("A credential verified recently is skipped")
    void given_freshlyVerifiedCredential_should_leaveItAlone() throws Exception {
      // Arrange
      Instant alreadyVerifiedAt = Instant.now().minus(1, ChronoUnit.HOURS);
      jdbc.update(
          "UPDATE secret_references SET secret_reference_last_verified_at = ?"
              + " WHERE secret_reference_id = ?",
          Timestamp.from(alreadyVerifiedAt),
          referenceA);

      // Act
      job.execute(null);

      // Assert — untouched, so the stamp is still the seeded one.
      assertEquals(
          alreadyVerifiedAt.truncatedTo(ChronoUnit.MILLIS),
          lastVerifiedAt(referenceA).truncatedTo(ChronoUnit.MILLIS),
          "a fresh credential must not be re-checked");
    }

    @Test
    @DisplayName("The job does nothing at all when it is disabled")
    void given_disabledJob_should_doNothing() throws Exception {
      // Arrange
      setEnabled(false);

      // Act
      job.execute(null);

      // Assert
      assertNull(lastVerifiedAt(referenceA), "a disabled job must not touch any credential");
      assertNull(lastVerifiedAt(referenceB), "a disabled job must not touch any credential");
    }

    @Test
    @DisplayName("A tenant whose run fails does not stop the other tenants")
    void given_oneFailingTenant_should_stillRunTheOthers() throws Exception {
      // Arrange — a dangling location is the per-credential failure the run must absorb; it also
      // proves the failure is contained inside the tenant that owns the broken row.
      jdbc.update(
          "UPDATE secret_references SET secret_reference_location = ?"
              + " WHERE secret_reference_id = ?",
          "missing-secret-" + UUID.randomUUID(),
          referenceA);

      // Act
      job.execute(null);

      // Assert — tenant B was processed regardless of tenant A's broken credential.
      assertNotNull(lastVerifiedAt(referenceB), "tenant B must still have been verified");
      // A dangling location is inconclusive: nothing is written for that one credential.
      assertNull(lastVerifiedAt(referenceA), "an unresolvable secret leaves its row untouched");
      assertEquals(UNSET.name(), status(referenceA), "and never flips its status");
    }
  }

  @Nested
  @DisplayName("Tenant isolation")
  class TenantIsolation {

    @Test
    @DisplayName("The job never writes a credential outside the tenant it is scoped to")
    void given_twoTenants_should_keepEachCredentialInItsOwnTenant() throws Exception {
      // Arrange — tenant B's credential is already fresh, so only tenant A has work to do.
      jdbc.update(
          "UPDATE secret_references SET secret_reference_last_verified_at = ?"
              + " WHERE secret_reference_id = ?",
          Timestamp.from(Instant.now()),
          referenceB);
      Instant tenantBStampBefore = lastVerifiedAt(referenceB);

      // Act
      job.execute(null);

      // Assert — ground truth through raw JDBC: tenant A moved, tenant B did not.
      assertNotNull(lastVerifiedAt(referenceA), "tenant A's credential was verified");
      assertEquals(
          tenantBStampBefore.truncatedTo(ChronoUnit.MILLIS),
          lastVerifiedAt(referenceB).truncatedTo(ChronoUnit.MILLIS),
          "tenant B's row must be byte-for-byte untouched by tenant A's run");
    }

    @Test
    @DisplayName("Each credential keeps the tenant it was seeded with")
    void given_completedRun_should_neverReattributeARow() throws Exception {
      // Arrange — done in the fixture.

      // Act
      job.execute(null);

      // Assert — a scoped UPDATE that leaked would show up as a moved tenant_id.
      assertEquals(tenantA, tenantIdOf(referenceA));
      assertEquals(tenantB, tenantIdOf(referenceB));
      assertEquals(
          1L,
          jdbc.queryForObject(
              "SELECT count(*) FROM secret_references WHERE tenant_id = ?", Long.class, tenantA),
          "tenant A owns exactly its seeded credential");
      assertEquals(
          1L,
          jdbc.queryForObject(
              "SELECT count(*) FROM secret_references WHERE tenant_id = ?", Long.class, tenantB),
          "tenant B owns exactly its seeded credential");
    }
  }

  // -- ground truth helpers: raw JDBC, never the entity manager --

  private Instant lastVerifiedAt(String referenceId) {
    Timestamp stamp =
        jdbc.queryForObject(
            "SELECT secret_reference_last_verified_at FROM secret_references"
                + " WHERE secret_reference_id = ?",
            Timestamp.class,
            referenceId);
    return stamp != null ? stamp.toInstant() : null;
  }

  private String status(String referenceId) {
    return jdbc.queryForObject(
        "SELECT secret_reference_status FROM secret_references WHERE secret_reference_id = ?",
        String.class,
        referenceId);
  }

  private String tenantIdOf(String referenceId) {
    return jdbc.queryForObject(
        "SELECT tenant_id FROM secret_references WHERE secret_reference_id = ?",
        String.class,
        referenceId);
  }

  // -- seeding --

  private void setEnabled(boolean enabled) {
    ReflectionTestUtils.setField(job, "enabled", enabled);
  }

  private String seedTenant(String name) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        id,
        name);
    return id;
  }

  private String seedCredential(String tenantId, CREDENTIAL_AUTH_METHOD authMethod) {
    String secretId = seedAzureManagedIdentitySecret(tenantId);
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO secret_references (secret_reference_id, secret_reference_type,"
            + " secret_reference_name, secret_reference_connector_instance_id,"
            + " secret_reference_location,"
            + " secret_reference_status, secret_reference_credential_type,"
            + " secret_reference_credential_auth_method, tenant_id)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        id,
        SECRET_REFERENCE_TYPE.CREDENTIAL_VALUE,
        "credential-" + id,
        LOCAL_SECRETS_PROVIDER_ID,
        secretId,
        UNSET.name(),
        CREDENTIAL_TYPE.CLOUD_AZURE.name(),
        authMethod.name(),
        tenantId);
    return id;
  }

  /**
   * A real stored secret is required, not just a reference: the local provider resolves the secret
   * at preparation time, and a reference with no location concludes on {@code SECRET_NOT_FOUND}
   * without a validator ever running — which leaves the row untouched and proves nothing about the
   * run. No subscription id, so the probe stops right after the token request: outside Azure IMDS
   * is unreachable, the outcome is inconclusive, and the run stamps the attempt without flipping
   * the status.
   */
  private String seedAzureManagedIdentitySecret(String tenantId) {
    String secretId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO secrets (secret_id, secret_type, secret_azure_environment,"
            + " secret_created_at, secret_updated_at, tenant_id)"
            + " VALUES (?, ?, ?, now(), now(), ?)",
        secretId,
        SECRET_TYPE.AZURE_MANAGED_IDENTITY.name(),
        AZURE_CLOUD,
        tenantId);
    return secretId;
  }
}
