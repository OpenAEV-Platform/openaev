package io.openaev.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.ContractOutputType;
import io.openaev.output_processor.AsreproastableAccountOutputProcessor;
import io.openaev.output_processor.CredentialsOutputProcessor;
import io.openaev.output_processor.FindingCapableOutputProcessor;
import io.openaev.output_processor.KerberoastableAccountOutputProcessor;
import io.openaev.rest.finding.FindingService;
import io.openaev.utils.SensitiveValueMaskingUtils.ValueComposition;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Guards {@code SensitiveValueMaskingUtils.VALUE_COMPOSITIONS} against drifting away from the
 * processors it describes.
 *
 * <p>The table has to be static: reaching the processors at runtime would make the finding mapper
 * depend on every {@code OutputProcessor} bean, one of which depends back on {@code FindingService}
 * - a Spring cycle. A test has no such constraint, so it does what production cannot: it builds
 * each processor by hand (constructor injection with a mocked service, no context), asks it for a
 * finding value, and checks the declared composition takes that value apart into exactly the
 * segments it was built from.
 *
 * <p>This is a round-trip property, not a count: changing {@code toFindingValue} to emit an extra
 * field, to reorder the segments or to pick another separator breaks it immediately. A drifting
 * table is what would hand a password out in the clear under the guise of a username.
 */
@DisplayName("Finding value composition consistency")
class SensitiveValueCompositionConsistencyTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String USERNAME = "jdoe";
  private static final String PASSWORD = "Sup3rS3cret";
  private static final String HASH = "aad3b435b51404eeaad3b435b51404ee";
  private static final String ASSET_ID = "asset-1";
  private static final String HOST = "10.0.0.1";

  private static FindingCapableOutputProcessor credentials() {
    return new CredentialsOutputProcessor(mock(FindingService.class));
  }

  private static FindingCapableOutputProcessor asreproastable() {
    return new AsreproastableAccountOutputProcessor(mock(FindingService.class));
  }

  private static FindingCapableOutputProcessor kerberoastable() {
    return new KerberoastableAccountOutputProcessor(mock(FindingService.class));
  }

  private static ObjectNode node(Map<String, String> fields) {
    ObjectNode json = MAPPER.createObjectNode();
    fields.forEach(json::put);
    return json;
  }

  /**
   * Splits the value the way {@code SensitiveValueMaskingUtils} does - same limited split - and
   * asserts the segments are exactly the ones the payload was built from.
   */
  private static void assertSplitsInto(
      ContractOutputType type, String value, List<String> expectedSegments) {
    ValueComposition composition = SensitiveValueMaskingUtils.valueCompositions().get(type);
    assertThat(composition)
        .as("no composition declared for %s, yet the test expects one", type)
        .isNotNull();
    assertThat(composition.segments())
        .as("%s declares a different number of segments than its value is built from", type)
        .hasSize(expectedSegments.size());

    List<String> parts =
        composition.separator() == null
            ? List.of(value)
            : List.of(
                value.split(Pattern.quote(composition.separator()), composition.segments().size()));

    assertThat(parts)
        .as("the declared composition of %s no longer takes its finding value apart", type)
        .containsExactlyElementsOf(expectedSegments);
  }

  @Nested
  @DisplayName("When a processor builds a finding value")
  class WhenAProcessorBuildsAFindingValue {

    @Test
    @DisplayName("given a credential with a password, should split into username then password")
    void given_aCredentialWithAPassword_should_splitIntoUsernameThenPassword() {
      // -- ARRANGE --
      ObjectNode json =
          node(
              Map.of(
                  "asset_id", ASSET_ID, "username", USERNAME, "password", PASSWORD, "host", HOST));

      // -- ACT --
      String value = credentials().toFindingValue(json);

      // -- ASSERT --
      assertSplitsInto(ContractOutputType.Credentials, value, List.of(USERNAME, PASSWORD));
    }

    @Test
    @DisplayName("given a credential with a hash, should split into username then hash")
    void given_aCredentialWithAHash_should_splitIntoUsernameThenHash() {
      // -- ARRANGE --
      // The processor falls back to the hash when no password was captured: the second segment is
      // declared as Password, but Hash is masked just the same, so both branches are covered.
      ObjectNode json =
          node(Map.of("asset_id", ASSET_ID, "username", USERNAME, "hash", HASH, "host", HOST));

      // -- ACT --
      String value = credentials().toFindingValue(json);

      // -- ASSERT --
      assertSplitsInto(ContractOutputType.Credentials, value, List.of(USERNAME, HASH));
      assertThat(SensitiveValueMaskingUtils.maskIfNeeded(ContractOutputType.Credentials, value))
          .isEqualTo(USERNAME + ":" + HASH.substring(0, 2) + SensitiveValueMaskingUtils.MASK);
    }

    @Test
    @DisplayName("given an AS-REP roastable account, should yield the username alone")
    void given_anAsreproastableAccount_should_yieldTheUsernameAlone() {
      // -- ARRANGE --
      // The recipe declares a hash field, but the processor does not put it in the value: this is
      // exactly what the composition records, and what stops the type from being masked.
      ObjectNode json =
          node(Map.of("asset_id", ASSET_ID, "username", USERNAME, "hash", HASH, "host", HOST));

      // -- ACT --
      String value = asreproastable().toFindingValue(json);

      // -- ASSERT --
      assertSplitsInto(ContractOutputType.AsreproastableAccount, value, List.of(USERNAME));
      assertThat(value).doesNotContain(HASH);
    }

    @Test
    @DisplayName("given a Kerberoastable account, should yield the username alone")
    void given_aKerberoastableAccount_should_yieldTheUsernameAlone() {
      // -- ARRANGE --
      ObjectNode json =
          node(Map.of("asset_id", ASSET_ID, "username", USERNAME, "hash", HASH, "host", HOST));

      // -- ACT --
      String value = kerberoastable().toFindingValue(json);

      // -- ASSERT --
      assertSplitsInto(ContractOutputType.KerberoastableAccount, value, List.of(USERNAME));
      assertThat(value).doesNotContain(HASH);
    }
  }

  @Nested
  @DisplayName("When the table gains an entry")
  class WhenTheTableGainsAnEntry {

    @Test
    @DisplayName("given a newly declared composition, should force a round-trip case to be written")
    void given_aNewlyDeclaredComposition_should_forceARoundTripCaseToBeWritten() {
      // -- ARRANGE --
      // Declaring a composition without proving it against its processor would defeat the guard:
      // this list is the inventory of what the cases above actually exercise.
      List<ContractOutputType> covered =
          List.of(
              ContractOutputType.Credentials,
              ContractOutputType.AsreproastableAccount,
              ContractOutputType.KerberoastableAccount);

      // -- ACT & ASSERT --
      assertThat(SensitiveValueMaskingUtils.valueCompositions().keySet())
          .as("a composition was declared without a round-trip case proving it")
          .containsExactlyInAnyOrderElementsOf(covered);
    }
  }
}
