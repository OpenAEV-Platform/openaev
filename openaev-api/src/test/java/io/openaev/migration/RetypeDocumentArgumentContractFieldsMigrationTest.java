package io.openaev.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Tenant;
import java.sql.Connection;
import java.util.UUID;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.hibernate.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies the retyping of stored contract fields: a text field whose argument type is document
 * becomes a document field, other fields and contracts are left untouched, and a rerun changes
 * nothing. Rows are seeded and read over the session connection, like the migration runs; the test
 * transaction ({@code @Transactional}) rolls them back.
 */
@Transactional
@DisplayName("Migration V6_20261112080000000: retype document argument contract fields")
class RetypeDocumentArgumentContractFieldsMigrationTest extends IntegrationTest {

  private static final String DOCUMENT_ARGUMENT_CONTENT =
      "{\"fields\":[{\"key\":\"doc\",\"type\":\"text\",\"argumentType\":\"document\"},"
          + "{\"key\":\"cmd\",\"type\":\"text\",\"argumentType\":\"text\"}]}";
  private static final String TEXT_ARGUMENTS_CONTENT =
      "{\"fields\":[{\"key\":\"cmd\",\"type\":\"text\",\"argumentType\":\"text\"}]}";

  private final ObjectMapper mapper = new ObjectMapper();

  @Autowired private V6_20261112080000000__Retype_document_argument_contract_fields migration;

  @Test
  @DisplayName("Given a document argument stored as text should retype it, a rerun is a no-op")
  void given_aDocumentArgumentStoredAsText_should_retypeIt() throws Exception {
    // Arrange
    String contractId = seedPayloadContract(DOCUMENT_ARGUMENT_CONTENT);

    // Act
    runMigration();
    runMigration();

    // Assert
    JsonNode fields = mapper.readTree(contentOf(contractId)).get("fields");
    assertThat(fields.get(0).path("type").asText()).isEqualTo("document");
    assertThat(fields.get(0).path("argumentType").asText()).isEqualTo("document");
    assertThat(fields.get(1).path("type").asText()).isEqualTo("text");
  }

  @Test
  @DisplayName("Given a contract with no document argument should leave it unchanged")
  void given_aContractWithNoDocumentArgument_should_leaveItUnchanged() throws Exception {
    // Arrange
    String contractId = seedPayloadContract(TEXT_ARGUMENTS_CONTENT);

    // Act
    runMigration();

    // Assert
    assertThat(mapper.readTree(contentOf(contractId)))
        .isEqualTo(mapper.readTree(TEXT_ARGUMENTS_CONTENT));
  }

  @Test
  @DisplayName("Given content with no fields array should report nothing to change")
  void given_contentWithNoFieldsArray_should_reportNothingToChange() throws Exception {
    // Act & Assert
    assertThat(retype(null)).isNull();
    assertThat(retype("[]")).isNull();
    assertThat(retype("{\"fields\":{}}")).isNull();
  }

  private String retype(String content) throws Exception {
    return V6_20261112080000000__Retype_document_argument_contract_fields.withDocumentFieldType(
        mapper, content);
  }

  private String seedPayloadContract(String content) {
    String payloadId = UUID.randomUUID().toString();
    String contractId = UUID.randomUUID().toString();
    execute(
        "INSERT INTO payload_identities (payload_identity_id, tenant_id, payload_source)"
            + " VALUES ('%s', '%s', 'MANUAL')",
        payloadId, Tenant.DEFAULT_TENANT_UUID);
    execute(
        "INSERT INTO payloads (payload_id, payload_type, payload_name, tenant_id,"
            + " payload_identity, payload_version) VALUES ('%s', 'Command', 'p', '%s', '%s',"
            + " '1.0.0')",
        payloadId, Tenant.DEFAULT_TENANT_UUID, payloadId);
    execute(
        "INSERT INTO injectors_contracts (injector_contract_id, injector_contract_content,"
            + " injector_contract_payload, tenant_id) VALUES ('%s', '%s', '%s', '%s')",
        contractId, content, payloadId, Tenant.DEFAULT_TENANT_UUID);
    return contractId;
  }

  private String contentOf(String contractId) {
    String[] content = new String[1];
    withConnection(
        connection -> {
          try (var statement =
              connection.prepareStatement(
                  "SELECT CAST(injector_contract_content AS text) FROM injectors_contracts"
                      + " WHERE injector_contract_id = ?")) {
            statement.setString(1, contractId);
            try (var resultSet = statement.executeQuery()) {
              resultSet.next();
              content[0] = resultSet.getString(1);
            }
          }
        });
    return content[0];
  }

  private void runMigration() {
    withConnection(connection -> migration.migrate(contextOn(connection)));
  }

  @FunctionalInterface
  private interface ConnectionWork {
    void run(Connection connection) throws Exception;
  }

  private void withConnection(ConnectionWork work) {
    entityManager
        .unwrap(Session.class)
        .doWork(
            connection -> {
              try {
                work.run(connection);
              } catch (RuntimeException e) {
                throw e;
              } catch (Exception e) {
                throw new IllegalStateException(e);
              }
            });
  }

  private void execute(String sql, Object... args) {
    withConnection(
        connection -> {
          try (var statement = connection.createStatement()) {
            statement.execute(sql.formatted(args));
          }
        });
  }

  private static Context contextOn(Connection connection) {
    return new Context() {
      @Override
      public Configuration getConfiguration() {
        return null;
      }

      @Override
      public Connection getConnection() {
        return connection;
      }
    };
  }
}
