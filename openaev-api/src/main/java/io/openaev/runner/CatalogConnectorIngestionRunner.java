package io.openaev.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.service.catalog_connectors.CatalogConnectorIngestionService;
import java.io.IOException;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
@Profile("!test")
public class CatalogConnectorIngestionRunner implements CommandLineRunner {
  private static final ObjectMapper mapper = new ObjectMapper();
  private static final String resourcePath = "/catalog/catalog-integrators.json";
  private final CatalogConnectorIngestionService catalogConnectorIngestionService;

  @Override
  public void run(String... args) {

    try (InputStream is = CatalogConnectorIngestionRunner.class.getResourceAsStream(resourcePath)) {
      if (is == null) {
        throw new IOException("File not found : " + resourcePath);
      }

      JsonNode rootNode = mapper.readTree(is);

      catalogConnectorIngestionService.extractCatalog(rootNode);

    } catch (IOException e) {
      log.error("Error while reading file : {}", e.getMessage());
    }
  }
}
