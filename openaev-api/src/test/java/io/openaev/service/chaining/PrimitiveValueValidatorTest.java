package io.openaev.service.chaining;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.PrimitiveType;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("PrimitiveValueValidator Tests")
class PrimitiveValueValidatorTest {

  private static PrimitiveValidationContext emptyContext() {
    return new PrimitiveValidationContext(
        Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
        Set.of());
  }

  @Nested
  @DisplayName("Null and unrestricted types")
  class NullAndUnrestrictedTypes {

    @Test
    @DisplayName("should reject null values for any type")
    void given_nullValue_should_reject() {
      // Act / Assert
      assertThat(
              PrimitiveValueValidator.isAcceptedForPrimitiveType(
                  PrimitiveType.Text, null, emptyContext()))
          .isFalse();
    }

    @Test
    @DisplayName("should accept values for types without defined rules")
    void given_typeWithoutRules_should_accept() {
      // Act / Assert
      assertThat(
              PrimitiveValueValidator.isAcceptedForPrimitiveType(
                  PrimitiveType.Text, "anything", emptyContext()))
          .isTrue();
      assertThat(
              PrimitiveValueValidator.isAcceptedForPrimitiveType(
                  PrimitiveType.Username, "admin", emptyContext()))
          .isTrue();
    }
  }

  @Nested
  @DisplayName("Port and Number format rules")
  class PortAndNumberFormat {

    @Test
    @DisplayName("should reject non-numeric port values")
    void given_nonNumericPort_should_reject() {
      // Act / Assert
      assertThat(
              PrimitiveValueValidator.isAcceptedForPrimitiveType(
                  PrimitiveType.Port, "toto", emptyContext()))
          .isFalse();
    }

    @Test
    @DisplayName("should reject out-of-range port values")
    void given_outOfRangePort_should_reject() {
      // Act / Assert
      assertThat(
              PrimitiveValueValidator.isAcceptedForPrimitiveType(
                  PrimitiveType.Port, "65536", emptyContext()))
          .isFalse();
      assertThat(
              PrimitiveValueValidator.isAcceptedForPrimitiveType(
                  PrimitiveType.Port, "-1", emptyContext()))
          .isFalse();
    }

    @Test
    @DisplayName("should accept valid port values")
    void given_validPort_should_accept() {
      // Act / Assert
      assertThat(
              PrimitiveValueValidator.isAcceptedForPrimitiveType(
                  PrimitiveType.Port, "22", emptyContext()))
          .isTrue();
      assertThat(
              PrimitiveValueValidator.isAcceptedForPrimitiveType(
                  PrimitiveType.Port, "65535", emptyContext()))
          .isTrue();
    }

    @Test
    @DisplayName("should validate number format")
    void given_numberValues_should_validateFormat() {
      // Act / Assert
      assertThat(
              PrimitiveValueValidator.isAcceptedForPrimitiveType(
                  PrimitiveType.Number, "42.5", emptyContext()))
          .isTrue();
      assertThat(
              PrimitiveValueValidator.isAcceptedForPrimitiveType(
                  PrimitiveType.Number, "not-a-number", emptyContext()))
          .isFalse();
    }
  }

  @Nested
  @DisplayName("IP scope rules")
  class IpScopeRules {

    @Test
    @DisplayName("should reject malformed IP addresses")
    void given_malformedIp_should_reject() {
      // Act / Assert
      assertThat(
              PrimitiveValueValidator.isAcceptedForPrimitiveType(
                  PrimitiveType.IPv4, "999.1.1.1", emptyContext()))
          .isFalse();
      assertThat(
              PrimitiveValueValidator.isAcceptedForPrimitiveType(
                  PrimitiveType.IPv6, "not-an-ip", emptyContext()))
          .isFalse();
    }

    @Test
    @DisplayName("should accept any well-formed IP when no scope rule exists")
    void given_noScopeRules_should_acceptWellFormedIp() {
      // Act / Assert
      assertThat(
              PrimitiveValueValidator.isAcceptedForPrimitiveType(
                  PrimitiveType.IPv4, "192.168.1.10", emptyContext()))
          .isTrue();
    }

    @Test
    @DisplayName("should reject denylisted IP")
    void given_denylistedIp_should_reject() {
      // Arrange
      PrimitiveValidationContext context =
          new PrimitiveValidationContext(
              Set.of(),
              Set.of(),
              Set.of(),
              Set.of(),
              Set.of(),
              Set.of(),
              Set.of(),
              Set.of(),
              Set.of("10.0.0.2"),
              Set.of());

      // Act / Assert
      assertThat(
              PrimitiveValueValidator.isAcceptedForPrimitiveType(
                  PrimitiveType.IPv4, "10.0.0.2", context))
          .isFalse();
    }

    @Test
    @DisplayName("should reject IP inside denylisted subnet")
    void given_ipInDenylistedSubnet_should_reject() {
      // Arrange
      PrimitiveValidationContext context =
          new PrimitiveValidationContext(
              Set.of(),
              Set.of(),
              Set.of(),
              Set.of(),
              Set.of(),
              Set.of(),
              Set.of(),
              Set.of(),
              Set.of(),
              Set.of("10.0.0.0/24"));

      // Act / Assert
      assertThat(
              PrimitiveValueValidator.isAcceptedForPrimitiveType(
                  PrimitiveType.IPv4, "10.0.0.42", context))
          .isFalse();
    }

    @Test
    @DisplayName("should only accept allowlisted IPs when an allowlist exists")
    void given_allowlist_should_restrictToAllowlistedIps() {
      // Arrange
      PrimitiveValidationContext context =
          new PrimitiveValidationContext(
              Set.of(),
              Set.of(),
              Set.of(),
              Set.of("192.168.1.10"),
              Set.of("172.16.0.0/16"),
              Set.of(),
              Set.of(),
              Set.of(),
              Set.of(),
              Set.of());

      // Act / Assert
      assertThat(
              PrimitiveValueValidator.isAcceptedForPrimitiveType(
                  PrimitiveType.IPv4, "192.168.1.10", context))
          .isTrue();
      assertThat(
              PrimitiveValueValidator.isAcceptedForPrimitiveType(
                  PrimitiveType.IPv4, "172.16.5.5", context))
          .isTrue();
      assertThat(
              PrimitiveValueValidator.isAcceptedForPrimitiveType(
                  PrimitiveType.IPv4, "10.0.0.1", context))
          .isFalse();
    }
  }

  @Nested
  @DisplayName("Subnet scope rules")
  class SubnetScopeRules {

    @Test
    @DisplayName("should reject malformed subnets")
    void given_malformedSubnet_should_reject() {
      // Act / Assert
      assertThat(
              PrimitiveValueValidator.isAcceptedForPrimitiveType(
                  PrimitiveType.IpSubnet, "bad-subnet", emptyContext()))
          .isFalse();
    }

    @Test
    @DisplayName("should apply allow and deny lists to subnets")
    void given_scopeRules_should_applyToSubnets() {
      // Arrange
      PrimitiveValidationContext context =
          new PrimitiveValidationContext(
              Set.of(),
              Set.of(),
              Set.of(),
              Set.of(),
              Set.of("10.0.0.0/24"),
              Set.of(),
              Set.of(),
              Set.of(),
              Set.of(),
              Set.of("192.168.0.0/16"));

      // Act / Assert
      assertThat(
              PrimitiveValueValidator.isAcceptedForPrimitiveType(
                  PrimitiveType.IpSubnet, "10.0.0.0/24", context))
          .isTrue();
      assertThat(
              PrimitiveValueValidator.isAcceptedForPrimitiveType(
                  PrimitiveType.IpSubnet, "192.168.0.0/16", context))
          .isFalse();
      assertThat(
              PrimitiveValueValidator.isAcceptedForPrimitiveType(
                  PrimitiveType.IpSubnet, "172.16.0.0/16", context))
          .isFalse();
    }
  }

  @Nested
  @DisplayName("Domain scope rules")
  class DomainScopeRules {

    @Test
    @DisplayName("should reject malformed domains")
    void given_malformedDomain_should_reject() {
      // Act / Assert
      assertThat(
              PrimitiveValueValidator.isAcceptedForPrimitiveType(
                  PrimitiveType.Domain, "bad domain", emptyContext()))
          .isFalse();
    }

    @Test
    @DisplayName("should match domains case-insensitively against scope rules")
    void given_mixedCaseDomain_should_matchScopeRulesCaseInsensitively() {
      // Arrange
      PrimitiveValidationContext context =
          new PrimitiveValidationContext(
              Set.of(),
              Set.of(),
              Set.of("example.org"),
              Set.of(),
              Set.of(),
              Set.of(),
              Set.of(),
              Set.of("blocked.org"),
              Set.of(),
              Set.of());

      // Act / Assert
      assertThat(
              PrimitiveValueValidator.isAcceptedForPrimitiveType(
                  PrimitiveType.Domain, "EXAMPLE.ORG", context))
          .isTrue();
      assertThat(
              PrimitiveValueValidator.isAcceptedForPrimitiveType(
                  PrimitiveType.Domain, "Blocked.org", context))
          .isFalse();
      assertThat(
              PrimitiveValueValidator.isAcceptedForPrimitiveType(
                  PrimitiveType.Domain, "other.org", context))
          .isFalse();
    }
  }

  @Nested
  @DisplayName("Asset and asset-group scope rules")
  class AssetScopeRules {

    @Test
    @DisplayName("should accept any asset ID when no scope rule exists")
    void given_noScopeRules_should_acceptAnyAssetId() {
      // Act / Assert
      assertThat(
              PrimitiveValueValidator.isAcceptedForPrimitiveType(
                  PrimitiveType.AssetId, "asset-1", emptyContext()))
          .isTrue();
    }

    @Test
    @DisplayName("should apply allow and deny lists to asset IDs")
    void given_scopeRules_should_applyToAssetIds() {
      // Arrange
      PrimitiveValidationContext context =
          new PrimitiveValidationContext(
              Set.of(),
              Set.of("asset-allowed"),
              Set.of(),
              Set.of(),
              Set.of(),
              Set.of(),
              Set.of("asset-denied"),
              Set.of(),
              Set.of(),
              Set.of());

      // Act / Assert
      assertThat(
              PrimitiveValueValidator.isAcceptedForPrimitiveType(
                  PrimitiveType.AssetId, "asset-allowed", context))
          .isTrue();
      assertThat(
              PrimitiveValueValidator.isAcceptedForPrimitiveType(
                  PrimitiveType.AssetId, "asset-denied", context))
          .isFalse();
      assertThat(
              PrimitiveValueValidator.isAcceptedForPrimitiveType(
                  PrimitiveType.AssetId, "asset-other", context))
          .isFalse();
    }

    @Test
    @DisplayName("should apply allow and deny lists to asset group IDs")
    void given_scopeRules_should_applyToAssetGroupIds() {
      // Arrange
      PrimitiveValidationContext context =
          new PrimitiveValidationContext(
              Set.of("group-allowed"),
              Set.of(),
              Set.of(),
              Set.of(),
              Set.of(),
              Set.of("group-denied"),
              Set.of(),
              Set.of(),
              Set.of(),
              Set.of());

      // Act / Assert
      assertThat(
              PrimitiveValueValidator.isAcceptedForPrimitiveType(
                  PrimitiveType.AssetGroupId, "group-allowed", context))
          .isTrue();
      assertThat(
              PrimitiveValueValidator.isAcceptedForPrimitiveType(
                  PrimitiveType.AssetGroupId, "group-denied", context))
          .isFalse();
      assertThat(
              PrimitiveValueValidator.isAcceptedForPrimitiveType(
                  PrimitiveType.AssetGroupId, "group-other", context))
          .isFalse();
    }
  }
}
