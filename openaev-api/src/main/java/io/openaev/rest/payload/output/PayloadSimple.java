package io.openaev.rest.payload.output;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Payload;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class PayloadSimple {

  @JsonProperty("payload_id")
  private String id;

  @JsonProperty("payload_type")
  private String type;

  @JsonProperty("payload_collector_type")
  private String collectorType;

  @JsonProperty("payload_status")
  private Payload.PAYLOAD_STATUS status;

  @JsonProperty("payload_revoked")
  private boolean revoked;

  @JsonProperty("payload_version")
  private String version;

  @JsonProperty("payload_description")
  private String description;

  // From the catalogue, hence read-only; every payload has a version now.
  @JsonProperty("payload_managed")
  private boolean managed;

  @JsonProperty("payload_identity_id")
  private String identityId;

  // A customized catalogue payload: its base, a newer base to rebase on, a conflict to resolve.
  @JsonProperty("payload_variant")
  private boolean variant;

  @JsonProperty("payload_base_version")
  private String baseVersion;

  @JsonProperty("payload_newer_base_version")
  private String newerBaseVersion;

  @JsonProperty("payload_conflict")
  private boolean conflict;

  // Scenario and atomic testing injects still on another version of this payload (F3).
  @JsonProperty("payload_older_versions_in_use")
  private long olderVersionsInUse;

  @JsonProperty("payload_installed_versions")
  private long installedVersions;

  @Builder
  public PayloadSimple(
      String id, String type, String collectorType, Payload.PAYLOAD_STATUS status) {
    this.id = id;
    this.type = type;
    this.collectorType = collectorType;
    this.status = status;
  }
}
