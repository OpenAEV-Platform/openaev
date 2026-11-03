package io.openaev.finding.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.ContractOutputType;
import io.openaev.finding.identity.FindingLocation.AssetLocation;
import io.openaev.output_processor.AccountWithPasswordNotRequiredOutputProcessor;
import io.openaev.output_processor.FileOutputProcessor;
import io.openaev.output_processor.FindingCapableOutputProcessor;
import io.openaev.output_processor.GroupOutputProcessor;
import io.openaev.output_processor.PasswordPolicyOutputProcessor;
import io.openaev.output_processor.PortScanOutputProcessor;
import io.openaev.output_processor.ShareOutputProcessor;
import io.openaev.output_processor.VulnerabilityOutputProcessor;
import io.openaev.rest.finding.FindingService;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Guards {@link FindingValueNormalizer} against drifting away from the processors whose values it
 * parses: each case builds the processor by hand, asks it for two values that differ only by what
 * the normalizer must drop (a changing state, or the host on an asset), and checks both resolve to
 * the same identity hash. Changing a {@code toFindingValue} format breaks it immediately.
 */
@DisplayName("Finding identity consistency with the output processors")
class FindingIdentityConsistencyTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final FindingLocation ASSET_A = new AssetLocation("asset-a");
  private static final FindingLocation ASSET_B = new AssetLocation("asset-b");

  // Not findings: AssetOutputProcessor creates endpoints, SignatureOutputProcessor matches
  // expectations.
  private static final Set<ContractOutputType> NOT_FINDING_TYPES =
      EnumSet.of(ContractOutputType.Asset, ContractOutputType.ExpectationSignature);

  private static ObjectNode node(Map<String, String> fields) {
    ObjectNode json = MAPPER.createObjectNode();
    fields.forEach(json::put);
    return json;
  }

  private static String hash(
      FindingCapableOutputProcessor processor, ObjectNode json, FindingLocation location) {
    return FindingIdentity.of(processor.getType(), processor.toFindingValue(json), location).hash();
  }

  private static FindingService findingService() {
    return mock(FindingService.class);
  }

  @Nested
  @DisplayName("When a processor builds a value carrying a changing state")
  class WhenAProcessorBuildsAValueCarryingAChangingState {

    @Test
    @DisplayName("given a group whose member count changed, should keep the identity")
    void given_aGroupWhoseMemberCountChanged_should_keepTheIdentity() {
      // -- ARRANGE --
      GroupOutputProcessor processor = new GroupOutputProcessor(findingService());

      // -- ACT --
      String monday =
          hash(processor, node(Map.of("group_name", "Domain Admins", "member_count", "12")), ASSET_A);
      String tuesday =
          hash(processor, node(Map.of("group_name", "Domain Admins", "member_count", "13")), ASSET_A);

      // -- ASSERT --
      assertThat(monday).isEqualTo(tuesday);
    }

    @Test
    @DisplayName("given a vulnerability whose status changed, should keep the identity")
    void given_aVulnerabilityWhoseStatusChanged_should_keepTheIdentity() {
      // -- ARRANGE --
      VulnerabilityOutputProcessor processor = new VulnerabilityOutputProcessor(findingService());

      // -- ACT --
      String before =
          hash(processor, node(Map.of("name", "ZeroLogon", "status", "VULNERABLE")), ASSET_A);
      String after =
          hash(processor, node(Map.of("name", "ZeroLogon", "status", "PATCHED")), ASSET_A);

      // -- ASSERT --
      assertThat(before).isEqualTo(after);
    }

    @Test
    @DisplayName("given a password policy setting whose value changed, should keep the identity")
    void given_aPasswordPolicySettingWhoseValueChanged_should_keepTheIdentity() {
      // -- ARRANGE --
      PasswordPolicyOutputProcessor processor = new PasswordPolicyOutputProcessor(findingService());

      // -- ACT --
      String before =
          hash(processor, node(Map.of("key", "MinimumPasswordLength", "value", "7")), ASSET_A);
      String after =
          hash(processor, node(Map.of("key", "MinimumPasswordLength", "value", "8")), ASSET_A);

      // -- ASSERT --
      assertThat(before).isEqualTo(after);
    }

    @Test
    @DisplayName("given an account whose status changed, should keep the identity")
    void given_anAccountWhoseStatusChanged_should_keepTheIdentity() {
      // -- ARRANGE --
      AccountWithPasswordNotRequiredOutputProcessor processor =
          new AccountWithPasswordNotRequiredOutputProcessor(findingService());

      // -- ACT --
      String enabled = hash(processor, node(Map.of("account", "guest", "status", "enabled")), ASSET_A);
      String withoutStatus = hash(processor, node(Map.of("account", "guest")), ASSET_A);

      // -- ASSERT --
      assertThat(enabled).isEqualTo(withoutStatus);
    }
  }

  @Nested
  @DisplayName("When a processor puts the host in the value")
  class WhenAProcessorPutsTheHostInTheValue {

    @Test
    @DisplayName("given the same port on two assets with different services, should share the hash")
    void given_theSamePortOnTwoAssetsWithDifferentServices_should_shareTheHash() {
      // -- ARRANGE --
      PortScanOutputProcessor processor = new PortScanOutputProcessor(findingService());

      // -- ACT --
      String onA =
          hash(
              processor,
              node(Map.of("host", "10.0.0.5", "port", "445", "service", "microsoft-ds")),
              ASSET_A);
      String onB =
          hash(processor, node(Map.of("host", "10.0.0.6", "port", "445", "service", "")), ASSET_B);

      // -- ASSERT --
      assertThat(onA).isEqualTo(onB);
    }

    @Test
    @DisplayName("given the same share on two assets with different permissions, should share it")
    void given_theSameShareOnTwoAssetsWithDifferentPermissions_should_shareIt() {
      // -- ARRANGE --
      ShareOutputProcessor processor = new ShareOutputProcessor(findingService());

      // -- ACT --
      String onA =
          hash(
              processor,
              node(Map.of("host", "dc01", "share_name", "SYSVOL", "permissions", "READ")),
              ASSET_A);
      String onB =
          hash(
              processor,
              node(Map.of("host", "dc02", "share_name", "SYSVOL", "permissions", "READ,WRITE")),
              ASSET_B);
      String withoutHost =
          hash(processor, node(Map.of("share_name", "SYSVOL", "permissions", "READ")), ASSET_B);

      // -- ASSERT --
      assertThat(onA).isEqualTo(onB).isEqualTo(withoutHost);
    }

    @Test
    @DisplayName("given the same file on two assets, should share the hash with or without host")
    void given_theSameFileOnTwoAssets_should_shareTheHashWithOrWithoutHost() {
      // -- ARRANGE --
      FileOutputProcessor processor = new FileOutputProcessor(findingService());
      Map<String, String> smbFile =
          Map.of("file_name", "secret.ps1", "path", "north.local/scripts", "share", "SYSVOL");
      Map<String, String> localFile = Map.of("file_name", "shadow", "path", "/etc");

      // -- ACT --
      String smbOnA = hash(processor, node(with(smbFile, "host", "dc01")), ASSET_A);
      String smbOnB = hash(processor, node(with(smbFile, "host", "dc02")), ASSET_B);
      String smbWithoutHost = hash(processor, node(smbFile), ASSET_B);
      String localOnA = hash(processor, node(with(localFile, "host", "srv1")), ASSET_A);
      String localWithoutHost = hash(processor, node(localFile), ASSET_B);

      // -- ASSERT --
      assertThat(smbOnA).isEqualTo(smbOnB).isEqualTo(smbWithoutHost);
      assertThat(localOnA).isEqualTo(localWithoutHost);
    }

    private static Map<String, String> with(Map<String, String> fields, String key, String value) {
      Map<String, String> copy = new HashMap<>(fields);
      copy.put(key, value);
      return copy;
    }
  }

  @Nested
  @DisplayName("When a contract output type is added")
  class WhenAContractOutputTypeIsAdded {

    @Test
    @DisplayName("given a finding type, should declare its normalization rule")
    void given_aFindingType_should_declareItsNormalizationRule() {
      // -- ARRANGE --
      // A new type silently falling back to "trim only" could keep a changing state or a host in
      // its identity: declaring a rule, even AS_IS, is a deliberate choice.
      Set<ContractOutputType> findingTypes =
          Arrays.stream(ContractOutputType.values())
              .filter(type -> !NOT_FINDING_TYPES.contains(type))
              .collect(Collectors.toSet());

      // -- ACT & ASSERT --
      assertThat(FindingValueNormalizer.rules().keySet())
          .as("a contract output type has no normalization rule")
          .containsExactlyInAnyOrderElementsOf(findingTypes);
    }
  }
}
