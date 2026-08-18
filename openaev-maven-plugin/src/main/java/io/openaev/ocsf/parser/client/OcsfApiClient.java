package io.openaev.ocsf.parser.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.ocsf.parser.client.url.OcsfSchemaEndpoints;
import io.openaev.ocsf.parser.client.url.OcsfSchemaExtensions;
import io.openaev.ocsf.parser.client.url.UrlBuilder;
import io.openaev.ocsf.parser.schema.Version;
import java.io.IOException;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;

@Slf4j
public class OcsfApiClient {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Version version;

  private CloseableHttpClient getClient() {
    return HttpClients.createDefault();
  }

  public OcsfApiClient(Version version) {
    this.version = version;
  }

  public JsonNode fetch(OcsfSchemaEndpoints endpoint) throws IOException {
    return this.fetch(endpoint, null);
  }

  public JsonNode fetch(OcsfSchemaEndpoints endpoint, String endpointArgument) throws IOException {
    return this.get(
        UrlBuilder.builder()
            .withVersion(this.version)
            .withEndpoint(endpoint, endpointArgument)
            .withExtensions(
                Set.of(
                    OcsfSchemaExtensions.LINUX,
                    OcsfSchemaExtensions.MACOS,
                    OcsfSchemaExtensions.WIN))
            .build());
  }

  private JsonNode get(String url) throws IOException {
    try (CloseableHttpClient client = getClient()) {
      HttpGet get = new HttpGet(url);
      try (CloseableHttpResponse res = client.execute(get)) {
        int statusCode = res.getCode();
        if (statusCode != 200) {
          log.warn("Error downloading file from {} with status code {}", url, statusCode);
          return null;
        }

        HttpEntity entity = res.getEntity();
        byte[] content = entity.getContent().readAllBytes();
        return objectMapper.readTree(content);
      }
    }
  }
}
