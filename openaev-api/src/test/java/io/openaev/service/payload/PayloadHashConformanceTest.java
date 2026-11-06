package io.openaev.service.payload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.spy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.*;
import io.openaev.rest.document.DocumentService;
import io.openaev.service.FileService;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.erdtman.jcs.JsonCanonicalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** The scenarios repository's shared vectors (its conformance/payload-hash.json) on our hash. */
@ExtendWith(MockitoExtension.class)
class PayloadHashConformanceTest {

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .findAndRegisterModules()
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  private static final Map<String, Class<? extends Payload>> TYPES =
      Map.of(
          "Command", Command.class,
          "Executable", Executable.class,
          "FileDrop", FileDrop.class,
          "DnsResolution", DnsResolution.class,
          "NetworkTraffic", NetworkTraffic.class,
          "AiAttack", AiAttack.class);
  // Relations the export names by ID: set by hand, never read from the vector.
  private static final List<String> RELATIONS =
      List.of(
          "payload_author_user",
          "payload_author_team",
          "payload_author_organization",
          "payload_tags",
          "payload_domains",
          "payload_attack_patterns",
          "payload_detection_remediations",
          "executable_file",
          "file_drop_file");

  @Mock private FileService fileService;
  @Mock private DocumentService documentService;

  @TestFactory
  @DisplayName("Given a vector the repository hashes, our hash should be the repository's")
  Stream<DynamicTest> given_repositoryVector_should_giveTheSameHash() throws Exception {
    JsonNode vectors;
    try (InputStream in = getClass().getResourceAsStream("/catalog/payload-hash-vectors.json")) {
      vectors = MAPPER.readTree(in).get("vectors");
    }
    return StreamSupport.stream(vectors.spliterator(), false)
        .filter(vector -> !vector.hasNonNull("refused"))
        .map(
            vector ->
                DynamicTest.dynamicTest(
                    vector.get("name").asText(),
                    () -> {
                      // A platform export always holds these two; a vector without them is not one.
                      JsonNode exported = vector.get("payload");
                      assumeTrue(
                          exported.has("payload_elevation_required")
                              && exported.has("payload_execution_arch"));

                      // Arrange
                      PayloadContentHashService service = serviceFor(vector.path("files"));
                      Payload payload = payload(exported);
                      List<AttackPattern> attackPatterns = new ArrayList<>();
                      vector
                          .path("attack_patterns")
                          .forEach(id -> attackPatterns.add(attackPattern(id.asText())));

                      // Act
                      String canonical =
                          new JsonCanonicalizer(
                                  service.canonicalTree(payload, attackPatterns).toString())
                              .getEncodedString();
                      String hash = service.hash(payload, attackPatterns);

                      // Assert
                      assertThat(canonical).isEqualTo(vector.get("canonical").asText());
                      assertThat(hash).isEqualTo(vector.get("sha256").asText());
                    }));
  }

  private PayloadContentHashService serviceFor(JsonNode files) {
    PayloadContentHashService service =
        spy(new PayloadContentHashService(fileService, documentService));
    lenient()
        .when(documentService.document(anyString()))
        .thenAnswer(invocation -> document(invocation.getArgument(0)));
    lenient()
        .doAnswer(invocation -> files.path(((Document) invocation.getArgument(0)).getId()).asText())
        .when(service)
        .fileHash(any());
    return service;
  }

  private static Payload payload(JsonNode exported) {
    ObjectNode fields = exported.deepCopy();
    RELATIONS.forEach(fields::remove);
    Payload payload = MAPPER.convertValue(fields, TYPES.get(exported.get("payload_type").asText()));
    if (payload instanceof Executable executable && exported.hasNonNull("executable_file")) {
      executable.setExecutableFile(document(exported.get("executable_file").asText()));
    }
    if (payload instanceof FileDrop fileDrop && exported.hasNonNull("file_drop_file")) {
      fileDrop.setFileDropFile(document(exported.get("file_drop_file").asText()));
    }
    return payload;
  }

  private static Document document(String id) {
    Document document = new Document();
    document.setId(id);
    document.setTarget(id);
    return document;
  }

  private static AttackPattern attackPattern(String externalId) {
    AttackPattern attackPattern = new AttackPattern();
    attackPattern.setExternalId(externalId);
    return attackPattern;
  }
}
