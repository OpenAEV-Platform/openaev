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
}
