package io.openaev.utils;

import static java.time.Instant.now;

import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.InjectExpectationSignature;
import io.openaev.expectation.ExpectationSignature;
import io.openaev.validator.Ipv4OrIpv6Validator;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ExpectationSignatureUtils {

  public static final String EXPECTATION_SIGNATURE_TYPE_PARENT_PROCESS_NAME = "parent_process_name";
  public static final String EXPECTATION_SIGNATURE_TYPE_SOURCE_IPV4_ADDRESS = "source_ipv4_address";
  public static final String EXPECTATION_SIGNATURE_TYPE_SOURCE_IPV6_ADDRESS = "source_ipv6_address";
  public static final String EXPECTATION_SIGNATURE_TYPE_TARGET_IPV4_ADDRESS = "target_ipv4_address";
  public static final String EXPECTATION_SIGNATURE_TYPE_TARGET_IPV6_ADDRESS = "target_ipv6_address";
  public static final String EXPECTATION_SIGNATURE_TYPE_TARGET_HOSTNAME_ADDRESS =
      "target_hostname_address";
  public static final String EXPECTATION_SIGNATURE_TYPE_START_DATE = "start_date";
  public static final String EXPECTATION_SIGNATURE_TYPE_END_DATE = "end_date";
  // AI adversarial validation: correlate AI defense (LLM firewall / guardrail) events back to a
  // specific AI inject execution. The injector emits a unique per-inject marker and the target
  // endpoint it called; AI defense collectors match these against their logs.
  public static final String EXPECTATION_SIGNATURE_TYPE_AI_REQUEST_MARKER = "ai_request_marker";
  public static final String EXPECTATION_SIGNATURE_TYPE_AI_TARGET_ENDPOINT = "ai_target_endpoint";

  public static ExpectationSignature createIpSignature(String ip, boolean isTarget) {
    if (ip == null || ip.isEmpty()) {
      return null;
    }
    if (Ipv4OrIpv6Validator.isIpv4(ip)) {
      return new ExpectationSignature(
          isTarget
              ? EXPECTATION_SIGNATURE_TYPE_TARGET_IPV4_ADDRESS
              : EXPECTATION_SIGNATURE_TYPE_SOURCE_IPV4_ADDRESS,
          ip);
    } else if (Ipv4OrIpv6Validator.isIpv6(ip)) {
      return new ExpectationSignature(
          isTarget
              ? EXPECTATION_SIGNATURE_TYPE_TARGET_IPV6_ADDRESS
              : EXPECTATION_SIGNATURE_TYPE_SOURCE_IPV6_ADDRESS,
          ip);
    } else {
      return null;
    }
  }

  public static ExpectationSignature createHostnameSignature(String signatureValue) {
    return new ExpectationSignature(
        EXPECTATION_SIGNATURE_TYPE_TARGET_HOSTNAME_ADDRESS, signatureValue);
  }

  public static ExpectationSignature createAiRequestMarkerSignature(String marker) {
    if (marker == null || marker.isEmpty()) {
      return null;
    }
    return new ExpectationSignature(EXPECTATION_SIGNATURE_TYPE_AI_REQUEST_MARKER, marker);
  }

  public static ExpectationSignature createAiTargetEndpointSignature(String endpoint) {
    if (endpoint == null || endpoint.isEmpty()) {
      return null;
    }
    return new ExpectationSignature(EXPECTATION_SIGNATURE_TYPE_AI_TARGET_ENDPOINT, endpoint);
  }

  public static List<InjectExpectationSignature> convertToInjectExpectationSignatures(
      List<ExpectationSignature> expectationSignatures, BaseInjectExpectation injectExpectation) {
    if (expectationSignatures == null || expectationSignatures.isEmpty()) {
      return new ArrayList<>();
    }
    // Drop nulls (createIpSignature returns null for non-IP values) and de-duplicate by
    // (type, value). InjectExpectationSignature has a composite primary key of (injectExpectation,
    // type, value): two identical (type, value) pairs on the same expectation map to the same row,
    // so persisting both makes Hibernate enqueue two managed entities with the same identifier and
    // throw NonUniqueObjectException, rolling back the whole expectation save. This happens
    // whenever
    // computeSignatures yields the same value twice - e.g. an endpoint whose seen IP is also one of
    // its declared IPs produces two source_ipv4_address signatures. The entity's
    // "ON CONFLICT DO NOTHING" only guards duplicates across separate flushes, not within one
    // batch.
    return expectationSignatures.stream()
        .filter(Objects::nonNull)
        .distinct()
        .map(signature -> convertToInjectExpectationSignature(signature, injectExpectation))
        .collect(Collectors.toCollection(ArrayList::new));
  }

  public static InjectExpectationSignature convertToInjectExpectationSignature(
      ExpectationSignature expectationSignature, BaseInjectExpectation injectExpectation) {
    return new InjectExpectationSignature(
        injectExpectation, expectationSignature.getType(), expectationSignature.getValue(), now());
  }

  public static List<InjectExpectationSignature> mergeExpectationSignatures(
      List<InjectExpectationSignature> firstExpectationSignatures,
      List<InjectExpectationSignature> secondExpectationSignatures) {
    LinkedHashSet<InjectExpectationSignature> mergedSignatures = new LinkedHashSet<>();
    if (firstExpectationSignatures != null) {
      mergedSignatures.addAll(firstExpectationSignatures);
    }
    if (secondExpectationSignatures != null) {
      mergedSignatures.addAll(secondExpectationSignatures);
    }
    mergedSignatures.remove(null);
    return new ArrayList<>(mergedSignatures);
  }
}
