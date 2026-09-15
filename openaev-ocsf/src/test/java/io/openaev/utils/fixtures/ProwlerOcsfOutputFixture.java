package io.openaev.utils.fixtures;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.io.IOException;

public class ProwlerOcsfOutputFixture {
  public static ArrayNode getProwlerOcsfOutputTrace() throws IOException {
    return (ArrayNode)
        new ObjectMapper()
            .readTree(
                ProwlerOcsfOutputFixture.class.getResourceAsStream(
                    "/ocsf/prowler-security-finding.json"));
  }
}
