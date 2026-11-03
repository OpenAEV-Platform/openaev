package io.openaev.service.stix;

import static io.openaev.database.model.BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS;
import static io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE.DETECTION;
import static io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION;
import static io.openaev.utils.fixtures.InjectExpectationResultFixture.createCollectorResult;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectExpectationResult;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.stix.types.Complex;
import io.openaev.stix.types.Identifier;
import io.openaev.utils.fixtures.InjectExpectationFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.SecurityPlatformFixture;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Security Coverage per-platform attribution (coverage_platforms)")
class SecurityCoveragePlatformAttributionTest {

  private static SecurityPlatform createPlatform(String name, String type) {
    SecurityPlatform platform = SecurityPlatformFixture.createDefault(name, type);
    platform.setId(UUID.randomUUID().toString());
    return platform;
  }

  private static Inject createInject() {
    Inject inject = InjectFixture.getDefaultInject();
    inject.setId(UUID.randomUUID().toString());
    return inject;
  }

  private static BaseInjectExpectation createExpectation(
      BaseInjectExpectation.EXPECTATION_TYPE type, InjectExpectationResult... results) {
    BaseInjectExpectation expectation =
        InjectExpectationFixture.createExpectationWithTypeAndStatus(type, SUCCESS);
    expectation.setResults(new ArrayList<>(List.of(results)));
    return expectation;
  }

  private static Map<String, Identifier> stixIdsOf(SecurityPlatform... platforms) {
    Map<String, Identifier> stixIds = new LinkedHashMap<>();
    for (SecurityPlatform platform : platforms) {
      stixIds.put(platform.getId(), platform.toStixDomainObject().getId());
    }
    return stixIds;
  }

  @Nested
  @DisplayName("Method: computeCoveragePlatforms")
  class ComputeCoveragePlatforms {

    @Test
    @DisplayName("Each entry references the platform identity and is scored in percentage points")
    void given_platformResults_should_referenceTheIdentityAndRoundLikeTheCoverage() {
      // Arrange
      SecurityPlatform edr = createPlatform("EDR", "EDR");
      Inject inject = createInject();
      Map<String, List<BaseInjectExpectation>> expectationsByInject =
          Map.of(
              inject.getId(),
              List.of(
                  createExpectation(DETECTION, createCollectorResult(edr, 100.0)),
                  createExpectation(DETECTION, createCollectorResult(edr, 100.0)),
                  createExpectation(DETECTION, createCollectorResult(edr, 0.0)),
                  createExpectation(PREVENTION, createCollectorResult(edr, 100.0))));
      Map<String, Identifier> platformStixIds = stixIdsOf(edr);

      // Act
      List<PlatformCoverageResult> coveragePlatforms =
          SecurityCoverageService.computeCoveragePlatforms(
              List.of(inject), expectationsByInject, platformStixIds);

      // Assert
      String platformRef = SecurityPlatform.stixIdentityId(edr.getName());
      assertThat(platformStixIds.get(edr.getId()).getValue()).isEqualTo(platformRef);
      assertThat(coveragePlatforms)
          .containsExactly(
              new PlatformCoverageResult(platformRef, "PREVENTION", 100),
              new PlatformCoverageResult(platformRef, "DETECTION", 67));
    }

    @Test
    @DisplayName("Only the expectations of the matching injects are attributed")
    void given_resultsOnOtherInjects_should_attributeOnlyTheMatchingInjects() {
      // Arrange
      SecurityPlatform edr = createPlatform("EDR", "EDR");
      SecurityPlatform siem = createPlatform("SIEM", "SIEM");
      Inject matchingInject = createInject();
      Inject otherInject = createInject();
      Map<String, List<BaseInjectExpectation>> expectationsByInject =
          Map.of(
              matchingInject.getId(),
              List.of(createExpectation(DETECTION, createCollectorResult(edr, 100.0))),
              otherInject.getId(),
              List.of(createExpectation(DETECTION, createCollectorResult(siem, 100.0))));

      // Act
      List<PlatformCoverageResult> coveragePlatforms =
          SecurityCoverageService.computeCoveragePlatforms(
              List.of(matchingInject, matchingInject), expectationsByInject, stixIdsOf(edr, siem));

      // Assert
      assertThat(coveragePlatforms)
          .containsExactly(
              new PlatformCoverageResult(
                  SecurityPlatform.stixIdentityId(edr.getName()), "DETECTION", 100));
    }

    @Test
    @DisplayName(
        "Platforms sharing a name: the identity is built from the same platform whatever the order")
    void given_platformsSharingAName_should_buildTheIdentityFromTheSmallestPlatformId() {
      // Arrange
      SecurityPlatform falconEdr = createPlatform("Falcon", "EDR");
      SecurityPlatform falconXdr = createPlatform("falcon ", "XDR");
      SecurityPlatform first =
          falconEdr.getId().compareTo(falconXdr.getId()) < 0 ? falconEdr : falconXdr;
      String stixId = SecurityPlatform.stixIdentityId("Falcon");

      // Act
      SecurityCoverageService.PlatformIdentities forward =
          SecurityCoverageService.PlatformIdentities.of(List.of(falconEdr, falconXdr));
      SecurityCoverageService.PlatformIdentities backward =
          SecurityCoverageService.PlatformIdentities.of(List.of(falconXdr, falconEdr));

      // Assert
      for (SecurityCoverageService.PlatformIdentities identities : List.of(forward, backward)) {
        assertThat(identities.identityByStixId()).containsOnlyKeys(stixId);
        assertThat(identities.identityByStixId().get(stixId).getProperty("name"))
            .isEqualTo(first.toStixDomainObject().getProperty("name"));
        assertThat(identities.platformIdsByStixId().get(stixId))
            .containsExactlyInAnyOrder(falconEdr.getId(), falconXdr.getId());
        assertThat(identities.stixIdByPlatformId().values())
            .allSatisfy(identifier -> assertThat(identifier.getValue()).isEqualTo(stixId));
      }
    }

    @Test
    @DisplayName("Platforms sharing a name are scored together under their one identity")
    void given_platformsSharingAName_should_scoreThemTogetherUnderOneIdentity() {
      // Arrange
      SecurityPlatform falconEdr = createPlatform("Falcon", "EDR");
      SecurityPlatform falconXdr = createPlatform("falcon ", "XDR");
      Inject inject = createInject();
      Map<String, List<BaseInjectExpectation>> expectationsByInject =
          Map.of(
              inject.getId(),
              List.of(
                  createExpectation(DETECTION, createCollectorResult(falconEdr, 100.0)),
                  createExpectation(DETECTION, createCollectorResult(falconXdr, 0.0)),
                  createExpectation(
                      DETECTION,
                      createCollectorResult(falconEdr, 0.0),
                      createCollectorResult(falconXdr, 100.0))));

      // Act
      List<PlatformCoverageResult> coveragePlatforms =
          SecurityCoverageService.computeCoveragePlatforms(
              List.of(inject), expectationsByInject, stixIdsOf(falconEdr, falconXdr));

      // Assert
      assertThat(coveragePlatforms)
          .containsExactly(
              new PlatformCoverageResult(
                  SecurityPlatform.stixIdentityId("Falcon"), "DETECTION", 67));
    }

    @Test
    @DisplayName("Nothing is attributable without matching injects, expectations or platforms")
    void given_nothingAttributable_should_returnAnEmptyList() {
      // Arrange
      SecurityPlatform edr = createPlatform("EDR", "EDR");
      Inject inject = createInject();
      Map<String, List<BaseInjectExpectation>> expectationsByInject =
          Map.of(
              inject.getId(),
              List.of(createExpectation(DETECTION, createCollectorResult(edr, 100.0))));

      // Act + Assert
      assertThat(
              SecurityCoverageService.computeCoveragePlatforms(
                  List.of(), expectationsByInject, stixIdsOf(edr)))
          .isEmpty();
      assertThat(
              SecurityCoverageService.computeCoveragePlatforms(
                  List.of(createInject()), expectationsByInject, stixIdsOf(edr)))
          .isEmpty();
      assertThat(
              SecurityCoverageService.computeCoveragePlatforms(
                  List.of(inject), expectationsByInject, Map.of()))
          .isEmpty();
    }
  }

  @Nested
  @DisplayName("STIX shape")
  class StixShape {

    @Test
    @DisplayName("An entry serializes as platform_ref, name and score")
    void given_entry_should_serializeWithTheContractPropertyNames() {
      // Arrange
      PlatformCoverageResult entry =
          new PlatformCoverageResult(
              "identity--8c3f6f5e-7b2a-4f43-9b1a-2d6a8f0e5c11", "DETECTION", 75);

      // Act
      String json = new Complex<>(entry).toStix(new ObjectMapper()).toString();

      // Assert
      assertThatJson(json)
          .isEqualTo(
              "{\"platform_ref\":\"identity--8c3f6f5e-7b2a-4f43-9b1a-2d6a8f0e5c11\","
                  + "\"name\":\"DETECTION\",\"score\":75}");
    }

    @Test
    @DisplayName("The platform identity id is the OpenCTI standard id derived from its name")
    void given_platformName_should_deriveTheOpenCtiIdentityId() {
      // Arrange
      SecurityPlatform platform = createPlatform("CrowdStrike Falcon", "EDR");

      // Act
      String id = platform.toStixDomainObject().getId().getValue();

      // Assert
      assertThat(id).isEqualTo("identity--fd6bb94b-46b7-5e41-90d0-2a0fcf171ca2");
      assertThat(SecurityPlatform.stixIdentityId("  crowdstrike FALCON ")).isEqualTo(id);
    }

    @Test
    @DisplayName("Unicode spaces around a platform name are trimmed like OpenCTI does")
    void given_unicodeSpacesAroundTheName_should_deriveTheSameIdentityIdAsOpenCti() {
      // Arrange
      String expected = "identity--fd6bb94b-46b7-5e41-90d0-2a0fcf171ca2";

      // Act and assert: em space, no-break space, ideographic space, line separator, byte order
      // mark and tab, none of which String.trim() removes except the tab
      assertThat(SecurityPlatform.stixIdentityId("\u2003CrowdStrike Falcon\u00A0"))
          .isEqualTo(expected);
      assertThat(SecurityPlatform.stixIdentityId("\u3000\u2028crowdstrike falcon\uFEFF\t"))
          .isEqualTo(expected);
      // Spaces inside the name are kept
      assertThat(SecurityPlatform.stixIdentityId("CrowdStrike\u2003Falcon")).isNotEqualTo(expected);
    }

    @Test
    @DisplayName("Escaped and non-ASCII characters hash the canonical JSON OpenCTI hashes")
    void given_namesNeedingEscapes_should_deriveTheIdentityIdsOpenCtiDerives() {
      // Act and assert: ids computed by the OpenCTI standard id generation (canonicalize + UUIDv5)
      assertThat(SecurityPlatform.stixIdentityId("EDR \"Prod\" \\ lab"))
          .isEqualTo("identity--773e0647-3a70-5a17-aae3-ea4aa46df541");
      assertThat(SecurityPlatform.stixIdentityId("Splunk\u000eSIEM"))
          .isEqualTo("identity--3782b925-958e-5ba8-86b0-d93c4dd02693");
      assertThat(SecurityPlatform.stixIdentityId("a\tb\nc\bd\fe\rf\u001fg"))
          .isEqualTo("identity--aae89d3c-dabb-588b-9c8d-5015b0cd2138");
      assertThat(
              SecurityPlatform.stixIdentityId(
                  "D\u00e9fense \u00dcnicode \u9632\u5fa1 \ud83d\udee1"))
          .isEqualTo("identity--1511969b-fdf3-53f9-a4fe-349a0ad87bdd");
    }

    @Test
    @DisplayName("A name with a lone surrogate has no identity id, as in OpenCTI")
    void given_loneSurrogate_should_refuseTheName() {
      // Act and assert
      assertThatThrownBy(() -> SecurityPlatform.stixIdentityId("x\ud800y"))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
