package io.openbas.rest.detection_remediation.form;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DetectionRemediationWebserviceOutput {
  Boolean success;
  List<Rule> rules;
  int total_rules;
  String message;

  @Getter
  @Setter
  public static class Rule {
    String rule_type;
    String action_to_take;
    String severity;
    String rule_name;
    String rule_description;
    String tactic_technique;
    String detection_strategy;
    FieldConfiguration field_configuration;

    @Getter
    @Setter
    static class FieldConfiguration {
      String grandparent_image_filename;
      String grandparent_command_line;
      String parent_image_filename;
      String parent_command_line;
      String image_filename;
      String command_line;
      String file_path;
      String remote_ip_address;
      String remote_port;
      String connection_type;
      String domain_name;
    }
  }
}
