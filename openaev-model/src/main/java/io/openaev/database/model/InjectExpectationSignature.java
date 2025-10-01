package io.openaev.database.model;

import io.openaev.validator.Ipv4OrIpv6Validator;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InjectExpectationSignature {
  public static final String EXPECTATION_SIGNATURE_TYPE_PARENT_PROCESS_NAME = "parent_process_name";
  public static final String EXPECTATION_SIGNATURE_TYPE_SOURCE_IPV4_ADDRESS = "source_ipv4_address";
  public static final String EXPECTATION_SIGNATURE_TYPE_SOURCE_IPV6_ADDRESS = "source_ipv6_address";
  public static final String EXPECTATION_SIGNATURE_TYPE_TARGET_IPV4_ADDRESS = "target_ipv4_address";
  public static final String EXPECTATION_SIGNATURE_TYPE_TARGET_IPV6_ADDRESS = "target_ipv6_address";
  public static final String EXPECTATION_SIGNATURE_TYPE_TARGET_HOSTNAME_ADDRESS =
      "target_hostname_address";
  public static final String EXPECTATION_SIGNATURE_TYPE_START_DATE = "start_date";
  public static final String EXPECTATION_SIGNATURE_TYPE_END_DATE = "end_date";

  private String type;
  private String value;

  public InjectExpectationSignature(String signatureType, String signatureValue) {
    this.type = signatureType;
    this.value = signatureValue;
  }

  public static InjectExpectationSignature createIpSignature(String ip, boolean isTarget) {
    if (ip == null || ip.isEmpty()) {
      return null;
    }
    if (Ipv4OrIpv6Validator.isIpv4(ip)) {
      return new InjectExpectationSignature(
          isTarget
              ? EXPECTATION_SIGNATURE_TYPE_TARGET_IPV4_ADDRESS
              : EXPECTATION_SIGNATURE_TYPE_SOURCE_IPV4_ADDRESS,
          ip);
    } else if (Ipv4OrIpv6Validator.isIpv6(ip)) {
      return new InjectExpectationSignature(
          isTarget
              ? EXPECTATION_SIGNATURE_TYPE_TARGET_IPV6_ADDRESS
              : EXPECTATION_SIGNATURE_TYPE_SOURCE_IPV6_ADDRESS,
          ip);
    } else {
      return null;
    }
  }

  public static InjectExpectationSignature createHostnameSignature(String signatureValue) {
    return new InjectExpectationSignature(
        EXPECTATION_SIGNATURE_TYPE_TARGET_HOSTNAME_ADDRESS, signatureValue);
  }
}
