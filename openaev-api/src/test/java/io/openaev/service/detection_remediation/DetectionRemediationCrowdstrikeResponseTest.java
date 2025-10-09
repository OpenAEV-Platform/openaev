package io.openaev.service.detection_remediation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;

public class DetectionRemediationCrowdstrikeResponseTest extends IntegrationTest {
  @Resource protected ObjectMapper mapper;

  @Test
  public void getCrowdstrikeRules() throws JsonProcessingException {

    DetectionRemediationAIResponse detectionRemediationCrowdstrikeResponse =
        mapper.readValue(
            """
                {
                  "success": true,
                  "rules": [
                    {
                      "rule_type": "Process Creation",
                      "action_to_take": "Detect",
                      "severity": "High",
                      "rule_name": "Suspicious AnyDesk Download via PowerShell",
                      "rule_description": "Detects AnyDesk download using PowerShell Invoke-WebRequest",
                      "tactic_technique": "Custom Intelligence via Indicator of Attack",
                      "field_configuration": {
                        "grandparent_image_filename": ".*",
                        "grandparent_command_line": ".*",
                        "parent_image_filename": ".*\\\\\\\\powershell\\\\.exe",
                        "parent_command_line": ".*invoke-webrequest.*anydesk.*",
                        "image_filename": ".*",
                        "command_line": ".*"
                      },
                      "detection_strategy": "Detects remote access tool download using PowerShell"
                    }
                  ],
                  "total_rules": 1,
                  "message": "Rules generated successfully"
                }
                """,
            DetectionRemediationCrowdstrikeResponse.class);
    assertThat(detectionRemediationCrowdstrikeResponse.formateRules())
        .isEqualTo(
            """
                        <p>================================</p>
                        <p>Rule 1</p>
                        <p>Rule Type: Process Creation</p>
                        <p>Action to take: Detect</p>
                        <p>Severity: High</p>
                        <p>Rule name: Suspicious AnyDesk Download via PowerShell</p>
                        <p>Rule description: Detects AnyDesk download using PowerShell Invoke-WebRequest</p>
                        <p>Tactic & Technique: Custom Intelligence via Indicator of Attack</p>
                        <p>Detection Strategy: Detects remote access tool download using PowerShell</p>
                        <p>Field Configuration: </p>
                        <ul><li>Grandparent Image Filename: .*</li>
                        <li>Grandparent Command Line: .*</li>
                        <li>Parent Image Filename: .*\\\\powershell\\.exe</li>
                        <li>Parent Command Line: .*invoke-webrequest.*anydesk.*</li>
                        <li>Image Filename: .*</li>
                        <li>Command Line: .*</li>
                        </ul>""");
  }
}
