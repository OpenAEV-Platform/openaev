package io.openaev.service.stix;

import static io.openaev.database.model.BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS;
import static io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE.DETECTION;
import static io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION;
import static io.openaev.utils.fixtures.InjectExpectationResultFixture.createCollectorResult;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

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
  }
}
