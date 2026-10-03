package io.openaev.service.stix;

import io.openaev.database.model.IocValidationTestKind;
import java.util.List;
import java.util.Map;

/**
 * The parsed {@code x-opencti-ioc-validation-request} bundle: the request metadata, the IOCs with
 * their deterministic test kind, and the (indicator, platform) pairs linked by a deployed-on
 * relationship. This is a pure value object; {@link IocValidationBundleParser} builds it and
 * validates the STIX shape, {@link IocValidationService} turns it into persisted state.
 */
public record IocValidationRequest(
    String requestId,
    String name,
    String description,
    String requestedBy,
    List<IocValidationTestKind> testKinds,
    List<Ioc> iocs,
    List<Pair> pairs,
    Map<String, String> platformNamesByRef) {

  /** One IOC to validate, already resolved to its observable value and deterministic test kind. */
  public record Ioc(
      String indicatorRef,
      String indicatorName,
      String observableType,
      String value,
      IocValidationTestKind testKind,
      String fileName,
      Map<String, String> hashes) {}

  /** One (indicator, security platform) pair, linked by a deployed-on relationship. */
  public record Pair(String indicatorRef, String platformRef, String deployedOnRef) {}
}
