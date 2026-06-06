package io.openaev.rest.document;

import io.openaev.database.model.*;
import io.openaev.database.raw.RawDocument;
import io.openaev.rest.document.form.DocumentCreateInput;
import jakarta.validation.constraints.NotBlank;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

public interface DocumentServiceInternal {

  List<RawDocument> rawAllDocuments();

  Document document(@NotBlank String documentId);

  Document upsert(
      String fileName,
      InputStream fileIS,
      long fileSize,
      String fileContentType,
      DocumentCreateInput input)
      throws Exception;

  void deleteDocument(String documentId);

  Document save(Document document);

  List<Document> getPlayerDocuments(List<Article> articles, List<Inject> injects);

  List<Document> documentsForScenario(String scenarioId);

  List<Document> documentsForSimulation(String simulationId);

  List<RawDocument> documentsForChannel(@NotBlank String channelId);

  List<RawDocument> documentsForSecurityPlatform(@NotBlank String securityPlatformId);

  List<RawDocument> documentsForChallenge(@NotBlank String challengeId);

  List<RawDocument> documentsForPayload(@NotBlank String payloadId);

  List<Document> findAllDistinctOnInjectsByScenarioId(@NotBlank String scenarioId);

  boolean documentExists(String documentId);
}
