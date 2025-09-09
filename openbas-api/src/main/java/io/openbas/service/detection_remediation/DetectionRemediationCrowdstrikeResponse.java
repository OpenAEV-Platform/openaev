package io.openbas.service.detection_remediation;

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

  @Schema(description = "Total number of rules returned", example = "3")
  int total_rules;

  @Schema(description = "Informational message or error details", example = "3 rules matched")
  String message;

  @Schema(description = "Timestamp of the request", example = "2025-09-09T12:34:56Z")
  String timestamp;

  @Getter
  @Setter
  @Schema(description = "Detection rule with severity and action to take")
  public static class Rule {

    @Schema(description = "Rule type", example = "Network Connection")
    String rule_type;

    @Schema(description = "Action to take for remediation", example = "Detect")
    String action_to_take;

    @Schema(description = "Severity level", example = "High")
    String severity;

    @Schema(description = "Rule name", example = "Suspicious Outbound LDAP (389) Connection")
    String rule_name;

    @Schema(description = "Detailed rule description")
    String rule_description;

    @Schema(
        description = "Tactic and technique",
        example = "Custom Intelligence via Indicator of Attack")
    String tactic_technique;

    @Schema(description = "Detection strategy used to identify the behavior")
    String detection_strategy;

    @Schema(description = "Field configuration")
    FieldConfiguration field_configuration;

    @Getter
    @Setter
    @Schema(description = "Fields used for detection logic")
    static class FieldConfiguration {

      @Schema(description = "Grandparent process image filename", example = "explorer.exe")
      String grandparent_image_filename;

      @Schema(description = "Grandparent process command line")
      String grandparent_command_line;

      @Schema(description = "Parent process image filename", example = "powershell.exe")
      String parent_image_filename;

      @Schema(description = "Parent process command line")
      String parent_command_line;

      @Schema(description = "Process image filename", example = "cmd.exe")
      String image_filename;

      @Schema(description = "Process command line")
      String command_line;

      @Schema(description = "File path involved", example = "C:\\Windows\\System32\\cmd.exe")
      String file_path;

      @Schema(description = "Remote IP address", example = "192.168.1.10")
      String remote_ip_address;

      @Schema(description = "Remote port", example = "443")
      String remote_port;

      @Schema(description = "Connection type or protocol", example = "TCP")
      String connection_type;

      @Schema(description = "Domain name", example = "example.com")
      String domain_name;
    }
  }

  public String formateRules() {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < rules.size(); i++) {
      builder.append("================================\n");
      builder.append("<p>Rule ").append(i + 1).append("</p>\n");
      builder.append("<p>Rule Type: ").append(rules.get(i).rule_type).append("</p>\n");
      builder.append("<p>Action to take: ").append(rules.get(i).action_to_take).append("</p>\n");
      builder.append("<p>Severity: ").append(rules.get(i).severity).append("</p>\n");
      builder.append("<p>Rule name: ").append(rules.get(i).rule_name).append("</p>\n");
      builder
          .append("<p>Rule description: ")
          .append(rules.get(i).rule_description)
          .append("</p>\n");
      builder
          .append("<p>Tactic & Technique: ")
          .append(rules.get(i).tactic_technique)
          .append("</p>\n");
      builder
          .append("<p>Detection Strategy: ")
          .append(rules.get(i).detection_strategy)
          .append("</p>\n");

      builder.append("<p>Field Configuration: ").append("</p>\n");
      builder.append("<ul>");
      builder
          .append("<li>Grandparent Image Filename: ")
          .append(rules.get(i).field_configuration.grandparent_image_filename)
          .append("</ul>\n");
      builder
          .append("<li>Grandparent Command Line: ")
          .append(rules.get(i).field_configuration.grandparent_command_line)
          .append("</li>\n");
      builder
          .append("<li>Parent Image Filename: ")
          .append(rules.get(i).field_configuration.parent_image_filename)
          .append("</li>\n");
      builder
          .append("<li>Parent Command Line: ")
          .append(rules.get(i).field_configuration.parent_command_line)
          .append("</li>\n");
      builder
          .append("<li>Image Filename: ")
          .append(rules.get(i).field_configuration.image_filename)
          .append("</li>\n");
      builder
          .append("<li>Command Line: ")
          .append(rules.get(i).field_configuration.command_line)
          .append("</li>\n");

      if (rules.get(i).field_configuration.file_path != null
          && !rules.get(i).field_configuration.file_path.isEmpty())
        builder
            .append("<li>File Path: ")
            .append(rules.get(i).field_configuration.file_path)
            .append("</li>\n");

      if (rules.get(i).field_configuration.remote_ip_address != null
          && !rules.get(i).field_configuration.remote_ip_address.isEmpty())
        builder
            .append("<li>Remote IP Address: ")
            .append(rules.get(i).field_configuration.remote_ip_address)
            .append("</li>\n");

      if (rules.get(i).field_configuration.remote_port != null
          && !rules.get(i).field_configuration.remote_port.isEmpty())
        builder
            .append("<li>Remote TCP/UDP Port: ")
            .append(rules.get(i).field_configuration.remote_port)
            .append("</li>\n");

      if (rules.get(i).field_configuration.connection_type != null
          && !rules.get(i).field_configuration.connection_type.isEmpty())
        builder
            .append("<li>Connection Type: ")
            .append(rules.get(i).field_configuration.connection_type)
            .append("</li>\n");

      if (rules.get(i).field_configuration.domain_name != null
          && !rules.get(i).field_configuration.domain_name.isEmpty())
        builder
            .append("<li>Domain Name: ")
            .append(rules.get(i).field_configuration.domain_name)
            .append("</li>\n");

      builder.append("</ul>");
    }
    return builder.toString();
  }
}
