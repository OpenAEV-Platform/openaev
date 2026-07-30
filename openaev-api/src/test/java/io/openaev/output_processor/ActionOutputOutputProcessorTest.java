package io.openaev.output_processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.rest.finding.FindingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ActionOutputOutputProcessorTest {

  private final FindingService findingService = mock(FindingService.class);
  private final ActionOutputOutputProcessor processor =
      new ActionOutputOutputProcessor(findingService);
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("Should keep multiline execution output value")
  void shouldKeepMultilineExecutionOutputValue() throws Exception {
    String executionOutput =
        "NetExec succeeded:\n"
            + "SMB                      74.234.220.121  445    WINTERFELL       [*] Windows 10 / Server 2019 Build 17763 (name:WINTERFELL) (domain:north.sevenkingdoms.local) (signing:True) (SMBv1:False) (Null Auth:True)\n"
            + "SMB                      74.234.220.121  445    WINTERFELL       [-] Failed to enumerate disks: SMB SessionError: code: 0xc0000203 - STATUS_USER_SESSION_DELETED - The remote user session has been deleted.";
    JsonNode node = objectMapper.readTree(objectMapper.writeValueAsString(executionOutput));

    String result = processor.toFindingValue(node);

    assertEquals(executionOutput, result);
  }
}
