package io.openaev.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.openaev.IntegrationTest;
import io.openaev.database.model.InjectorContract;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.hibernate.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies the preset domain duplicate merge: a variant of a preset name ("Table-Top") is merged
 * into the preset of its tenant, its contract links are repointed (or dropped when the contract is
 * already linked to the preset), its id is journaled for the search engine, other tenants and
 * domains are untouched, and re-running the migration is a no-op.
 *
 * <p>Rows are seeded through the raw JDBC connection, as Flyway runs the migration, and roll back
 * with the {@code @Transactional} test transaction.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Merge preset domain duplicates migration")
class MergePresetDomainDuplicatesMigrationTest extends IntegrationTest {

  private static final String TABLETOP = "Tabletop";
  private static final Instant LONG_AGO = Instant.parse("2020-01-01T00:00:00Z");

  @Autowired private V6_20261012120000000__Merge_preset_domain_duplicates migration;
  @Autowired private InjectorContractComposer injectorContractComposer;

  private void execute(String sql, Object... parameters) {
    entityManager
        .unwrap(Session.class)
        .doWork(
            connection -> {
              try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int index = 0; index < parameters.length; index++) {
                  statement.setObject(index + 1, parameters[index]);
                }
                statement.executeUpdate();
              }
            });
  }

  private List<String> selectStrings(String sql, Object... parameters) {
    List<String> values = new ArrayList<>();
    entityManager
        .unwrap(Session.class)
        .doWork(
            connection -> {
              try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int index = 0; index < parameters.length; index++) {
                  statement.setObject(index + 1, parameters[index]);
                }
                try (ResultSet resultSet = statement.executeQuery()) {
                  while (resultSet.next()) {
                    values.add(resultSet.getString(1));
                  }
                }
              }
            });
    return values;
  }

  private String createTenant() {
    String tenantId = UUID.randomUUID().toString();
    execute(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        tenantId,
        "preset-domain-merge-" + tenantId);
    return tenantId;
  }

  private String insertDomain(String name, String tenantId) {
    String domainId = UUID.randomUUID().toString();
    execute(
        "INSERT INTO domains (domain_id, domain_name, domain_color, tenant_id) VALUES (?, ?, ?, ?)",
        domainId,
        name,
        "#FFCC33",
        tenantId);
    return domainId;
  }

  private String presetOf(String tenantId) {
    execute(
        "INSERT INTO domains (domain_id, domain_name, domain_color, tenant_id) VALUES (?, ?, ?, ?)"
            + " ON CONFLICT (domain_name, tenant_id) DO NOTHING",
        UUID.randomUUID().toString(),
        TABLETOP,
        "#FFCC33",
        tenantId);
    return selectStrings(
            "SELECT domain_id FROM domains WHERE domain_name = ? AND tenant_id = ?",
            TABLETOP,
            tenantId)
        .getFirst();
  }

  private InjectorContract persistContract() {
    InjectorContract contract =
        injectorContractComposer
            .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
            .withInjector(InjectorFixture.createDefaultPayloadInjector())
            .persist()
            .get();
    entityManager.flush();
    execute(
        "UPDATE injectors_contracts SET injector_contract_updated_at = ?"
            + " WHERE injector_contract_id = ?",
        Timestamp.from(LONG_AGO),
        contract.getId());
    return contract;
  }

  private void link(InjectorContract contract, String domainId) {
    execute(
        "INSERT INTO injectors_contracts_domains (injector_contract_id, domain_id, tenant_id)"
            + " VALUES (?, ?, ?)",
        contract.getId(),
        domainId,
        contract.getTenant().getId());
  }

  private List<String> domainIdsLinkedTo(InjectorContract contract) {
    return selectStrings(
        "SELECT domain_id FROM injectors_contracts_domains WHERE injector_contract_id = ?",
        contract.getId());
  }

  private boolean domainExists(String domainId) {
    return !selectStrings("SELECT domain_id FROM domains WHERE domain_id = ?", domainId).isEmpty();
  }

  private boolean isJournaled(String domainId) {
    return !selectStrings(
            "SELECT deletion_id FROM engine_deletions WHERE deletion_id = ?", domainId)
        .isEmpty();
  }

  private Instant updatedAtOf(InjectorContract contract) {
    return Instant.ofEpochSecond(
        Long.parseLong(
            selectStrings(
                    "SELECT EXTRACT(EPOCH FROM injector_contract_updated_at)::bigint"
                        + " FROM injectors_contracts WHERE injector_contract_id = ?",
                    contract.getId())
                .getFirst()));
  }

  private void runMigration() {
    entityManager
        .unwrap(Session.class)
        .doWork(
            connection -> {
              try {
                migration.migrate(
                    new Context() {
                      @Override
                      public Configuration getConfiguration() {
                        return null;
                      }

                      @Override
                      public java.sql.Connection getConnection() {
                        return connection;
                      }
                    });
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
  }

  @Test
  @DisplayName("Repoints a duplicate's contract links to the preset, then deletes and journals it")
  void duplicate_is_merged_into_the_preset() {
    InjectorContract contract = persistContract();
    String tenantId = contract.getTenant().getId();
    String presetId = presetOf(tenantId);
    String duplicateId = insertDomain("TaBlE-tOp", tenantId);
    link(contract, duplicateId);

    runMigration();

    assertThat(domainExists(duplicateId)).isFalse();
    assertThat(domainExists(presetId)).isTrue();
    assertThat(domainIdsLinkedTo(contract)).containsExactly(presetId);
    assertThat(isJournaled(duplicateId)).isTrue();
    assertThat(updatedAtOf(contract)).isAfter(LONG_AGO);
  }

  @Test
  @DisplayName("Drops the duplicate link of a contract already linked to the preset")
  void duplicate_link_twin_is_dropped() {
    InjectorContract contract = persistContract();
    String tenantId = contract.getTenant().getId();
    String presetId = presetOf(tenantId);
    String duplicateId = insertDomain("tAbLe ToP", tenantId);
    link(contract, presetId);
    link(contract, duplicateId);

    runMigration();

    assertThat(domainExists(duplicateId)).isFalse();
    assertThat(domainIdsLinkedTo(contract)).containsExactly(presetId);
  }

  @Test
  @DisplayName("Merges per tenant and leaves tenants without the preset and other domains intact")
  void merge_is_tenant_scoped() {
    String tenantWithPreset = createTenant();
    String presetId = presetOf(tenantWithPreset);
    String duplicateId = insertDomain("TABLE_TOP", tenantWithPreset);
    String customId = insertDomain("Satellite Ops", tenantWithPreset);
    String tenantWithoutPreset = createTenant();
    String orphanVariantId = insertDomain("Table-Top", tenantWithoutPreset);

    runMigration();

    assertThat(domainExists(duplicateId)).isFalse();
    assertThat(domainExists(presetId)).isTrue();
    assertThat(domainExists(customId)).isTrue();
    assertThat(domainExists(orphanVariantId)).isTrue();
    assertThat(isJournaled(orphanVariantId)).isFalse();
  }

  @Test
  @DisplayName("Re-running the migration is a no-op (idempotent)")
  void migration_is_idempotent() {
    InjectorContract contract = persistContract();
    String tenantId = contract.getTenant().getId();
    String presetId = presetOf(tenantId);
    String duplicateName = "TABLE - TOP";
    link(contract, insertDomain(duplicateName, tenantId));

    runMigration();
    assertThatCode(this::runMigration).doesNotThrowAnyException();

    assertThat(domainIdsLinkedTo(contract)).containsExactly(presetId);
    assertThat(
            selectStrings(
                "SELECT domain_name FROM domains WHERE tenant_id = ? AND domain_name IN (?, ?)",
                tenantId,
                TABLETOP,
                duplicateName))
        .containsExactly(TABLETOP);
  }
}
