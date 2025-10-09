package io.openaev.service.detection_remediation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DetectionRemediationSplunkResponseTest extends IntegrationTest {
  @Resource protected ObjectMapper mapper;

  @Test
  public void getSplunkSplQuery() throws JsonProcessingException {

    DetectionRemediationAIResponse detectionRemediationSplunkResponse =
        mapper.readValue(
            """
                {
                  "success": true,
                  "spl_query": "index=windows EventCode=4688 CommandLine=\\"*Invoke-WebRequest*\\" CommandLine=\\"*AnyDesk*\\" | stats count by Computer, User, CommandLine | sort -count",
                  "message": "SPL query generated successfully"
                }
                """,
            DetectionRemediationSplunkResponse.class);
    assertThat(detectionRemediationSplunkResponse.formateRules())
        .isEqualTo(
            "index=windows EventCode=4688 CommandLine=\"*Invoke-WebRequest*\" CommandLine=\"*AnyDesk*\" | stats count by Computer, User, CommandLine | sort -count");
  }
}
