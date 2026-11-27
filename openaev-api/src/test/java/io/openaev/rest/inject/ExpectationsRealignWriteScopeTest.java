package io.openaev.rest.inject;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.InjectRepository;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The realignment of an atomic testing overwrites the stored expectations of its inject with the
 * predefined ones of its contract. Each chunk of the write commits in its own transaction, so that
 * transaction carries the request's tenant scope: with {@code injects} isolated, the content update
 * of a transaction without that scope matches no row and the realignment reports success while the
 * stored content is unchanged.
 */
@WithMockUser(isAdmin = true)
@DisplayName("Expectations realignment of an atomic testing, injects isolated")
class ExpectationsRealignWriteScopeTest extends ByIdRequestScopeTestBase {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String PREDEFINED_TYPE = "DETECTION";
  private static final String STORED_TYPE = "PREVENTION";

  @Autowired private InjectRepository realignInjectRepository;
  @Autowired private InjectorContractComposer realignContractComposer;
  @Autowired private DataSource realignDataSource;

  private final List<String> realignInjects = new ArrayList<>();

  @AfterEach
  void removeRealignRows() {
    JdbcTemplate jdbc = new JdbcTemplate(realignDataSource);
    realignInjects.forEach(id -> jdbc.update("DELETE FROM injects WHERE inject_id = ?", id));
    realignInjects.clear();
    realignContractComposer.reset();
  }

  @Nested
  @DisplayName("Tenant route")
  class TenantRoute {

    @Test
    @DisplayName(
        "given a drifted atomic testing of the path tenant, its stored expectations are replaced"
            + " by the predefined ones")
    void given_driftedAtomicTestingOfPathTenant_should_storeThePredefinedExpectations()
        throws Exception {
      // Arrange
      String injectId = seedDriftedAtomicTesting(memberTenant);

      // Act
      int realign = realignAtomicTesting(tenantRoute(memberTenant), injectId);

      // Assert
      assertThat(realign).isEqualTo(200);
      assertThat(storedExpectationType(injectId)).isEqualTo(PREDEFINED_TYPE);
    }
  }

  @Nested
  @DisplayName("Non-prefixed route with an X-Tenant-Ids selector")
  class HeaderRoute {

    @Test
    @DisplayName(
        "given a drifted atomic testing of the selected tenant, its stored expectations are"
            + " replaced by the predefined ones")
    void given_driftedAtomicTestingOfSelectedTenant_should_storeThePredefinedExpectations()
        throws Exception {
      // Arrange
      String injectId = seedDriftedAtomicTesting(memberTenant);

      // Act
      int realign = realignAtomicTesting(headerRoute(memberTenant), injectId);

      // Assert
      assertThat(realign).isEqualTo(200);
      assertThat(storedExpectationType(injectId)).isEqualTo(PREDEFINED_TYPE);
    }
  }

  /** An atomic testing storing an expectation its contract no longer predefines. */
  private String seedDriftedAtomicTesting(String tenantId) {
    String id =
        inTenantScope(
            tenantId,
            () -> {
              InjectorContract template = InjectorContractFixture.createDefaultInjectorContract();
              ObjectNode content = MAPPER.createObjectNode();
              content.set(
                  "fields",
                  MAPPER
                      .createArrayNode()
                      .add(
                          MAPPER
                              .createObjectNode()
                              .put("key", "expectations")
                              .put("type", "expectation")
                              .set(
                                  "availableExpectations",
                                  MAPPER
                                      .createArrayNode()
                                      .add(expectation(PREDEFINED_TYPE, true)))));
              template.setContent(content.toString());
              template.setConvertedContent(content);
              template.setTenant(new Tenant(tenantId));
              InjectorContract contract =
                  realignContractComposer.forInjectorContract(template).persist().get();

              Inject inject = InjectFixture.getDefaultInject();
              inject.setTenant(new Tenant(tenantId));
              inject.setInjectorContract(contract);
              ObjectNode injectContent = MAPPER.createObjectNode();
              ArrayNode stored = MAPPER.createArrayNode().add(expectation(STORED_TYPE, false));
              injectContent.set("expectations", stored);
              inject.setContent(injectContent);
              return realignInjectRepository.save(inject).getId();
            });
    realignInjects.add(id);
    return id;
  }

  private static ObjectNode expectation(String type, boolean predefined) {
    return MAPPER
        .createObjectNode()
        .put("expectation_type", type)
        .put("expectation_name", "Expect " + type)
        .put("expectation_score", 50)
        .put("expectation_expectation_group", false)
        .put("expectation_is_predefined", predefined);
  }

  /** The type of the first stored expectation, read with raw JDBC on its own connection. */
  private String storedExpectationType(String injectId) {
    return new JdbcTemplate(realignDataSource)
        .queryForObject(
            "SELECT inject_content::jsonb -> 'expectations' -> 0 ->> 'expectation_type'"
                + " FROM injects WHERE inject_id = ?",
            String.class,
            injectId);
  }
}
