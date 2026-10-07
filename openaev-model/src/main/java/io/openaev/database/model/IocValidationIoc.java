package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One IOC of an IOC validation request, stored in the {@code ioc_validation_iocs} JSON column. It
 * records what OpenCTI asked for, what OpenAEV actually runs once the tenant allow-list is applied,
 * and the injects built for it, so results can be traced back to the indicator.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class IocValidationIoc {

  @JsonProperty("ioc_indicator_ref")
  private String indicatorRef;

  @JsonProperty("ioc_indicator_name")
  private String indicatorName;

  @JsonProperty("ioc_observable_type")
  private String observableType;

  @JsonProperty("ioc_value")
  private String value;

  @JsonProperty("ioc_requested_test_kind")
  private IocValidationTestKind requestedTestKind;

  /** Test kind actually run; {@code null} when the IOC is skipped. */
  @JsonProperty("ioc_test_kind")
  private IocValidationTestKind testKind;

  @JsonProperty("ioc_file_name")
  private String fileName;

  @JsonProperty("ioc_hashes")
  private Map<String, String> hashes = new LinkedHashMap<>();

  @JsonProperty("ioc_inject_ids")
  private List<String> injectIds = new ArrayList<>();

  /**
   * For each inject of {@link #injectIds}, the endpoints with an active agent it runs the test on,
   * as approved: it targets them and no asset group, whose members can change after the approval.
   * An inject targeting other endpoints, more or fewer, is not executed.
   */
  @JsonProperty("ioc_inject_targets")
  private Map<String, List<String>> injectTargets = new LinkedHashMap<>();

  /**
   * For each inject of {@link #injectIds}, the id of the IOC validation payload it was approved
   * with: a test kind has a payload per executor (POSIX, PowerShell...), so an inject whose
   * contract now runs another payload, even one of the same kind, is not executed.
   */
  @JsonProperty("ioc_inject_payloads")
  private Map<String, String> injectPayloads = new LinkedHashMap<>();

  /** Why the IOC was skipped, or how it was adapted (sinkhole, DNS fallback). */
  @JsonProperty("ioc_message")
  private String message;

  /**
   * Whether the IOC value failed the checks of the planner (a URL, hash or file name outside the
   * accepted characters, an internal address...): it never reaches a payload, {@link #message} says
   * why.
   */
  @JsonProperty("ioc_refused")
  private boolean refused;

  /**
   * Digest of the arguments of the planned test (target, port, proxy...), {@code null} when the IOC
   * is skipped: an approval refuses to run a test whose arguments changed since it was shown. A
   * digest and not the arguments, which may hold the proxy credentials.
   */
  @JsonProperty("ioc_plan_fingerprint")
  private String planFingerprint;
}
