package io.openaev.service.detection_remediation;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(
    description =
        "Response containing detection rules and recommended remediation actions for CrowdStrike")
public class DetectionRemediationCrowdstrikeResponse {

  @Schema(description = "Whether the request succeeded", example = "true")
  Boolean success;

  @Schema(description = "List of rules matching the request")
  List<Rule> rules;

  @JsonProperty("total_rules")
  @Schema(description = "Total number of rules returned", example = "3")
  int totalRules;

  @Schema(description = "Informational message or error details", example = "3 rules matched")
  String message;

  @Schema(description = "Timestamp of the request", example = "2025-09-09T12:34:56Z")
  String timestamp;

  @Getter
  @Setter
  @Schema(description = "Detection rule with severity and action to take")
  public static class Rule {
    @JsonProperty("rule_type")
    @Schema(description = "Rule type", example = "Network Connection")
    String ruleType;

    @JsonProperty("action_to_take")
    @Schema(description = "Action to take for remediation", example = "Detect")
    String actionToTake;

    @Schema(description = "Severity level", example = "High")
    String severity;

    @JsonProperty("rule_name")
    @Schema(description = "Rule name", example = "Suspicious Outbound LDAP (389) Connection")
    String ruleName;

    @JsonProperty("rule_description")
    @Schema(description = "Detailed rule description")
    String ruleDescription;

    @JsonProperty("tactic_technique")
    @Schema(
        description = "Tactic and technique",
        example = "Custom Intelligence via Indicator of Attack")
    String tacticTechnique;

    @JsonProperty("detection_strategy")
    @Schema(description = "Detection strategy used to identify the behavior")
    String detectionStrategy;

    @JsonProperty("field_configuration")
    @Schema(description = "Field configuration")
    FieldConfiguration fieldConfiguration;

    @Getter
    @Setter
    @Schema(description = "Fields used for detection logic")
    static class FieldConfiguration {

      @JsonProperty("grandparent_image_filename")
      @Schema(description = "Grandparent process image filename", example = "explorer.exe")
      String grandparentImageFilename;

      @JsonProperty("grandparent_command_line")
      @Schema(description = "Grandparent process command line")
      String grandparentCommandLine;

      @JsonProperty("parent_image_filename")
      @Schema(description = "Parent process image filename", example = "powershell.exe")
      String parentImageFilename;

      @JsonProperty("parent_command_line")
      @Schema(description = "Parent process command line")
      String parentCommandLine;

      @JsonProperty("image_filename")
      @Schema(description = "Process image filename", example = "cmd.exe")
      String imageFilename;

      @JsonProperty("command_line")
      @Schema(description = "Process command line")
      String commandLine;

      @JsonProperty("file_path")
      @Schema(description = "File path involved", example = "C:\\Windows\\System32\\cmd.exe")
      String filePath;

      @JsonProperty("remote_ip_address")
      @Schema(description = "Remote IP address", example = "192.168.1.10")
      String remoteIpAddress;

      @JsonProperty("remote_port")
      @Schema(description = "Remote port", example = "443")
      String remotePort;

      @JsonProperty("connection_type")
      @Schema(description = "Connection type or protocol", example = "TCP")
      String connectionType;

      @JsonProperty("domain_name")
      @Schema(description = "Domain name", example = "example.com")
      String domainName;
    }
  }

  public String formateRules() {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < rules.size(); i++) {
      builder.append("<p>================================</p>\n");
      builder.append("<p>Rule ").append(i + 1).append("</p>\n");
      builder.append("<p>Rule Type: ").append(rules.get(i).ruleType).append("</p>\n");
      builder.append("<p>Action to take: ").append(rules.get(i).actionToTake).append("</p>\n");
      builder.append("<p>Severity: ").append(rules.get(i).severity).append("</p>\n");
      builder.append("<p>Rule name: ").append(rules.get(i).ruleName).append("</p>\n");
      builder.append("<p>Rule description: ").append(rules.get(i).ruleDescription).append("</p>\n");
      builder
          .append("<p>Tactic & Technique: ")
          .append(rules.get(i).tacticTechnique)
          .append("</p>\n");
      builder
          .append("<p>Detection Strategy: ")
          .append(rules.get(i).detectionStrategy)
          .append("</p>\n");

      builder.append("<p>Field Configuration: ").append("</p>\n");
      builder.append("<ul>");
      builder
          .append("<li>Grandparent Image Filename: ")
          .append(rules.get(i).fieldConfiguration.grandparentImageFilename)
          .append("</li>\n");
      builder
          .append("<li>Grandparent Command Line: ")
          .append(rules.get(i).fieldConfiguration.grandparentCommandLine)
          .append("</li>\n");
      builder
          .append("<li>Parent Image Filename: ")
          .append(rules.get(i).fieldConfiguration.parentImageFilename)
          .append("</li>\n");
      builder
          .append("<li>Parent Command Line: ")
          .append(rules.get(i).fieldConfiguration.parentCommandLine)
          .append("</li>\n");
      builder
          .append("<li>Image Filename: ")
          .append(rules.get(i).fieldConfiguration.imageFilename)
          .append("</li>\n");
      builder
          .append("<li>Command Line: ")
          .append(rules.get(i).fieldConfiguration.commandLine)
          .append("</li>\n");

      if (rules.get(i).fieldConfiguration.filePath != null
          && !rules.get(i).fieldConfiguration.filePath.isEmpty())
        builder
            .append("<li>File Path: ")
            .append(rules.get(i).fieldConfiguration.filePath)
            .append("</li>\n");

      if (rules.get(i).fieldConfiguration.remoteIpAddress != null
          && !rules.get(i).fieldConfiguration.remoteIpAddress.isEmpty())
        builder
            .append("<li>Remote IP Address: ")
            .append(rules.get(i).fieldConfiguration.remoteIpAddress)
            .append("</li>\n");

      if (rules.get(i).fieldConfiguration.remotePort != null
          && !rules.get(i).fieldConfiguration.remotePort.isEmpty())
        builder
            .append("<li>Remote TCP/UDP Port: ")
            .append(rules.get(i).fieldConfiguration.remotePort)
            .append("</li>\n");

      if (rules.get(i).fieldConfiguration.connectionType != null
          && !rules.get(i).fieldConfiguration.connectionType.isEmpty())
        builder
            .append("<li>Connection Type: ")
            .append(rules.get(i).fieldConfiguration.connectionType)
            .append("</li>\n");

      if (rules.get(i).fieldConfiguration.domainName != null
          && !rules.get(i).fieldConfiguration.domainName.isEmpty())
        builder
            .append("<li>Domain Name: ")
            .append(rules.get(i).fieldConfiguration.domainName)
            .append("</li>\n");

      builder.append("</ul>");
    }
    return builder.toString();
  }
}
