package io.openaev.rest.document;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.injectors.challenge.ChallengeContract.CHALLENGE_PUBLISH;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.Article;
import io.openaev.database.model.Document;
import io.openaev.database.model.Inject;
import io.openaev.database.raw.RawDocument;
import io.openaev.database.repository.ChallengeRepository;
import io.openaev.database.repository.DocumentRepository;
import io.openaev.injectors.challenge.model.ChallengeContent;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.FileService;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class DocumentService {

  @Resource private ObjectMapper mapper;

  private final DocumentRepository documentRepository;
  private final ChallengeRepository challengeRepository;
  private final FileService fileService;

  // -- CRUD --

  public Document document(@NotBlank final String documentId) {
    return documentRepository
        .findById(documentId)
        .orElseThrow(() -> new ElementNotFoundException("Document not found"));
  }

  public List<Document> getPlayerDocuments(List<Article> articles, List<Inject> injects) {
    Stream<Document> channelsDocs =
        articles.stream().map(Article::getChannel).flatMap(channel -> channel.getLogos().stream());
    Stream<Document> articlesDocs =
        articles.stream().flatMap(article -> article.getDocuments().stream());
    List<String> challenges =
        injects.stream()
            .filter(
                inject ->
                    inject
                        .getInjectorContract()
                        .map(contract -> contract.getId().equals(CHALLENGE_PUBLISH))
                        .orElse(false))
            .filter(inject -> inject.getContent() != null)
            .flatMap(
                inject -> {
                  try {
                    ChallengeContent content =
                        mapper.treeToValue(inject.getContent(), ChallengeContent.class);
                    return content.getChallenges().stream();
                  } catch (JsonProcessingException e) {
                    return Stream.empty();
                  }
                })
            .toList();
    Stream<Document> challengesDocs =
        fromIterable(challengeRepository.findAllById(challenges)).stream()
            .flatMap(challenge -> challenge.getDocuments().stream());
    return Stream.of(channelsDocs, articlesDocs, challengesDocs)
        .flatMap(documentStream -> documentStream)
        .distinct()
        .toList();
  }

  public void deleteDocument(String documentId) {
    Document document = document(documentId); // fetch or throw if not found

    boolean isUsedInFileDrop =
        document.getPayloadsByFileDrop() != null && !document.getPayloadsByFileDrop().isEmpty();
    boolean isUsedInExecutable =
        document.getPayloadsByExecutableFile() != null
            && !document.getPayloadsByExecutableFile().isEmpty();

    if (isUsedInFileDrop || isUsedInExecutable) {
      throw new BadRequestException(
          "Document is still in use for some payloads and cannot be deleted.");
    }

    List<Document> documents = documentRepository.removeById(documentId);

    // Remove document from minio
    documents.forEach(
        documentToRemove -> {
          try {
            fileService.deleteFile(documentToRemove.getTarget());
          } catch (Exception e) {
            log.warn(
                "File already removed or not found in minio: {}", documentToRemove.getTarget(), e);
          }
        });
  }

  public static String encodeFileName(String name) {
    return URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
  }

  public List<Document> documentsForScenario(String scenarioId) {
    return this.documentRepository.findAllDistinctByScenarioId(scenarioId);
  }

  public List<Document> documentsForSimulation(String simulationId) {
    return this.documentRepository.findAllDistinctBySimulationId(simulationId);
  }

  public List<RawDocument> documentsForChannel(@NotBlank String channelId) {
    return this.documentRepository.rawAllDocumentsByChannelId(channelId);
  }

  public List<RawDocument> documentsForSecurityPlatform(@NotBlank String securityPlatformId) {
    return this.documentRepository.rawAllDocumentsBySecurityPlatformId(securityPlatformId);
  }

  public List<RawDocument> documentsForChallenge(@NotBlank String challengeId) {
    return this.documentRepository.rawAllDocumentsByChallengeId(challengeId);
  }

  public List<RawDocument> documentsForPayload(@NotBlank String payloadId) {
    return this.documentRepository.rawAllDocumentsByPayloadId(payloadId);
  }
}
