package io.openaev.service.stix;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.IocValidation;
import io.openaev.database.model.IocValidationIoc;
import io.openaev.database.model.IocValidationPair;
import io.openaev.database.model.IocValidationTestKind;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("IOC validation approval fingerprint")
class IocValidationApprovalFingerprintTest {

  private static IocValidationIoc ioc(String indicator, IocValidationTestKind kind, String plan) {
    IocValidationIoc ioc = new IocValidationIoc();
    ioc.setIndicatorRef(indicator);
    ioc.setTestKind(kind);
    ioc.setPlanFingerprint(plan);
    return ioc;
  }

  private static IocValidationPair pair(String indicator, String platform, String matched) {
    IocValidationPair pair = new IocValidationPair();
    pair.setIndicatorRef(indicator);
    pair.setPlatformRef(platform);
    pair.setSecurityPlatformId(matched);
    return pair;
  }

  /** One IOC that runs (on two platforms) and one skipped IOC (on a third platform). */
  private static IocValidation validation() {
    IocValidation validation = new IocValidation();
    validation.setIocs(
        new ArrayList<>(
            List.of(
                ioc("indicator--a", IocValidationTestKind.DNS_RESOLUTION, "a".repeat(64)),
                ioc("indicator--b", null, null))));
    validation.setPairs(
        new ArrayList<>(
            List.of(
                pair("indicator--a", "identity--edr", "platform-edr"),
                pair("indicator--a", "identity--siem", null),
                pair("indicator--b", "identity--ndr", "platform-ndr"))));
    return validation;
  }

  private static String fingerprintAfter(Consumer<IocValidation> change) {
    IocValidation validation = validation();
    change.accept(validation);
    return IocValidationService.approvalFingerprint(validation);
  }

  @Test
  @DisplayName("is the same for the same plan, whatever the order of the pairs")
  void given_samePlan_should_giveTheSameFingerprint() {
    String fingerprint = IocValidationService.approvalFingerprint(validation());

    assertThat(fingerprint).matches("[0-9a-f]{64}");
    assertThat(fingerprintAfter(v -> v.setPairs(new ArrayList<>(v.getPairs().reversed()))))
        .isEqualTo(fingerprint);
    // A reason shown for a skipped IOC is not what the approval starts
    assertThat(fingerprintAfter(v -> v.getIocs().get(1).setMessage("Not run: other reason")))
        .isEqualTo(fingerprint);
    // Nor is the security platform of an indicator whose test does not run
    assertThat(fingerprintAfter(v -> v.getPairs().get(2).setSecurityPlatformId(null)))
        .isEqualTo(fingerprint);
  }

  @Test
  @DisplayName("changes with the test of an IOC, its arguments, or a platform of a test that runs")
  void given_otherPlan_should_giveAnotherFingerprint() {
    String fingerprint = IocValidationService.approvalFingerprint(validation());

    assertThat(fingerprintAfter(v -> v.getIocs().get(0).setTestKind(null)))
        .isNotEqualTo(fingerprint);
    assertThat(fingerprintAfter(v -> v.getIocs().get(0).setPlanFingerprint("b".repeat(64))))
        .isNotEqualTo(fingerprint);
    assertThat(
            fingerprintAfter(
                v -> v.getIocs().get(1).setTestKind(IocValidationTestKind.NETWORK_TRAFFIC)))
        .isNotEqualTo(fingerprint);
    assertThat(fingerprintAfter(v -> v.getPairs().get(1).setSecurityPlatformId("platform-siem")))
        .isNotEqualTo(fingerprint);
    assertThat(fingerprintAfter(v -> v.getPairs().remove(0))).isNotEqualTo(fingerprint);
  }
}
