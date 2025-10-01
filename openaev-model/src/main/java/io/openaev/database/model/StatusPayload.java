package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.Singular;

@Getter
@Setter
public class StatusPayload {

  @JsonProperty("payload_name")
  private String name;

  @JsonProperty("payload_description")
  private String description;

  @JsonProperty("payload_type")
  private String type;

  @JsonProperty("payload_cleanup_executor")
  private String cleanupExecutor;

  @JsonProperty("payload_command_blocks")
  @Singular
  private List<PayloadCommandBlock> payloadCommandBlocks = new ArrayList<>();

  @JsonProperty("payload_arguments")
  private List<PayloadArgument> arguments = new ArrayList<>();

  @JsonProperty("payload_prerequisites")
  private List<PayloadPrerequisite> prerequisites = new ArrayList<>();

  @JsonProperty("payload_external_id")
  private String externalId;

  @JsonProperty("executable_file")
  private StatusPayloadDocument executableFile;

  @JsonProperty("file_drop_file")
  private StatusPayloadDocument fileDropFile;

  @JsonProperty("dns_resolution_hostname")
  private String hostname;

  @JsonProperty("network_traffic_ip_src")
  @NotNull
  private String ipSrc;

  @JsonProperty("network_traffic_ip_dst")
  @NotNull
  private String ipDst;

  @JsonProperty("network_traffic_port_src")
  @NotNull
  private Integer portSrc;

  @JsonProperty("network_traffic_port_dst")
  @NotNull
  private Integer portDst;

  @JsonProperty("network_traffic_protocol")
  @NotNull
  private String protocol;

  public StatusPayload() {}

  public StatusPayload(
      String name,
      String description,
      String type,
      String protocol,
      Integer portDst,
      Integer portSrc,
      String ipDst,
      String ipSrc,
      String hostname,
      Document fileDropFile,
      Document executableFile,
      String externalId,
      List<PayloadPrerequisite> prerequisites,
      List<PayloadArgument> arguments,
      List<PayloadCommandBlock> payloadCommandBlocks,
      String cleanupExecutor) {
    this.name = name;
    this.description = description;
    this.type = type;
    this.protocol = protocol;
    this.portDst = portDst;
    this.portSrc = portSrc;
    this.ipDst = ipDst;
    this.ipSrc = ipSrc;
    this.hostname = hostname;
    if (fileDropFile != null) {
      this.fileDropFile = new StatusPayloadDocument(fileDropFile);
    }
    if (executableFile != null) {
      this.executableFile = new StatusPayloadDocument(executableFile);
    }
    this.externalId = externalId;
    this.prerequisites = prerequisites;
    this.arguments = arguments;
    this.payloadCommandBlocks = payloadCommandBlocks;
    this.cleanupExecutor = cleanupExecutor;
  }
}
