package io.openaev.runner;

import static io.openaev.runner.CatalogConnectorIngestionRunner.RESOURCE_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.service.catalog_connectors.CatalogConnectorIngestionService;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.CommandLineRunner;

@ExtendWith(MockitoExtension.class)
@DisplayName("CatalogConnectorIngestionRunner")
class CatalogConnectorIngestionRunnerTest {

  @Mock private CatalogConnectorIngestionService catalogConnectorIngestionService;

  @InjectMocks private CatalogConnectorIngestionRunner runner;

  @Nested
  @DisplayName("Run")
  class Run {

    @Test
    @DisplayName("Given the bundled catalog, should pass its contracts to the ingestion service")
    void given_bundledCatalog_should_passContractsToIngestionService() {
      // Arrange
      ArgumentCaptor<JsonNode> rootNode = ArgumentCaptor.forClass(JsonNode.class);

      // Act
      runner.run();

      // Assert
      verify(catalogConnectorIngestionService).extractCatalog(rootNode.capture());
      List<String> slugs =
          StreamSupport.stream(rootNode.getValue().get("contracts").spliterator(), false)
              .map(contract -> contract.get("slug").asText())
              .toList();
      assertThat(slugs).contains("openaev_nmap");
    }

    @Test
    @DisplayName("Given a missing catalog file, should not call the ingestion service")
    void given_missingCatalogFile_should_notCallIngestionService() throws Exception {
      // Arrange
      CommandLineRunner runnerWithoutCatalog =
          (CommandLineRunner)
              new CatalogHidingClassLoader()
                  .loadClass(CatalogConnectorIngestionRunner.class.getName())
                  .getConstructor(CatalogConnectorIngestionService.class)
                  .newInstance(catalogConnectorIngestionService);

      // Act
      runnerWithoutCatalog.run();

      // Assert
      verifyNoInteractions(catalogConnectorIngestionService);
    }
  }

  /**
   * Defines its own copy of the runner and hides the bundled catalog from it, so that the runner
   * takes its "file not found" path. Every other class comes from the test class loader.
   */
  private static class CatalogHidingClassLoader extends ClassLoader {
    private static final String RUNNER_CLASS = CatalogConnectorIngestionRunner.class.getName();

    CatalogHidingClassLoader() {
      super(CatalogConnectorIngestionRunner.class.getClassLoader());
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      if (!RUNNER_CLASS.equals(name)) {
        return super.loadClass(name, resolve);
      }
      synchronized (getClassLoadingLock(name)) {
        Class<?> runnerClass = findLoadedClass(name);
        if (runnerClass != null) {
          return runnerClass;
        }
        try (InputStream bytecode =
            getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
          byte[] bytes = bytecode.readAllBytes();
          // Same protection domain as the original class, so that coverage tools record it
          return defineClass(
              name,
              bytes,
              0,
              bytes.length,
              CatalogConnectorIngestionRunner.class.getProtectionDomain());
        } catch (IOException e) {
          throw new ClassNotFoundException(name, e);
        }
      }
    }

    @Override
    public URL getResource(String name) {
      return RESOURCE_PATH.substring(1).equals(name) ? null : super.getResource(name);
    }
  }
}
