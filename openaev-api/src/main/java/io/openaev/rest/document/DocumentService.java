package io.openaev.rest.document;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.helper.StreamHelper.iterableToSet;
import static io.openaev.injectors.challenge.ChallengeContract.CHALLENGE_PUBLISH;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.*;
import io.openaev.database.raw.RawDocument;
import io.openaev.database.repository.*;
import io.openaev.injectors.challenge.model.ChallengeContent;
import io.openaev.rest.document.form.DocumentCreateInput;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.FileService;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class DocumentService {

  @Resource private ObjectMapper mapper;

  private final DocumentRepository documentRepository;
  private final ChallengeRepository challengeRepository;
  private final ExerciseRepository exerciseRepository;
  private final ScenarioRepository scenarioRepository;
  private final TagRepository tagRepository;
  private final FileService fileService;

  // -- CRUD --

  public Document document(@NotBlank final String documentId) {
    return documentRepository
        .findById(documentId)
        .orElseThrow(() -> new ElementNotFoundException("Document not found"));
  }

  /**
   * Upsert a document
   *
   * @param fileName of the document to upsert
   * @param fileIS Input Stream of the document to upsert
   * @param fileSize Size of the document to upsert
   * @param fileContentType Content Type of the document to upsert
   * @param input documents informations for his creation
   * @return the upserted Document
   * @throws Exception when an upload issue occur
   */
  public Document upsert(
      String fileName,
      InputStream fileIS,
      long fileSize,
      String fileContentType,
      DocumentCreateInput input)
      throws Exception {
    byte[] content = fileIS.readAllBytes();
    String extension = FilenameUtils.getExtension(fileName);
    String fileTarget = DigestUtils.md5Hex(new ByteArrayInputStream(content)) + "." + extension;
    Optional<Document> targetDocument = documentRepository.findByTarget(fileTarget);
    // Document already exists by hash
    if (targetDocument.isPresent()) {
      Document document = targetDocument.get();
      // Compute exercises
      if (!document.getExercises().isEmpty()) {
        Set<Exercise> exercises = new HashSet<>(document.getExercises());
        List<Exercise> inputExercises =
            fromIterable(exerciseRepository.findAllById(input.getExerciseIds()));
        exercises.addAll(inputExercises);
        document.setExercises(exercises);
      }
      // Compute scenarios
      if (!document.getScenarios().isEmpty()) {
        Set<Scenario> scenarios = new HashSet<>(document.getScenarios());
        List<Scenario> inputScenarios =
            fromIterable(scenarioRepository.findAllById(input.getScenarioIds()));
        scenarios.addAll(inputScenarios);
        document.setScenarios(scenarios);
      }
      // Compute tags
      Set<Tag> tags = new HashSet<>(document.getTags());
      List<Tag> inputTags = fromIterable(tagRepository.findAllById(input.getTagIds()));
      tags.addAll(inputTags);
      document.setTags(tags);
      return save(document);
    } else {
      Optional<Document> existingDocument = documentRepository.findByName(fileName);
      if (existingDocument.isPresent()) {
        Document document = existingDocument.get();
        // Update doc
        fileService.uploadFile(
            fileTarget, new ByteArrayInputStream(content), fileSize, fileContentType);
        document.setDescription(input.getDescription());

        // Compute exercises
        if (!document.getExercises().isEmpty()) {
          Set<Exercise> exercises = new HashSet<>(document.getExercises());
          List<Exercise> inputExercises =
              fromIterable(exerciseRepository.findAllById(input.getExerciseIds()));
          exercises.addAll(inputExercises);
          document.setExercises(exercises);
        }
        // Compute scenarios
        if (!document.getScenarios().isEmpty()) {
          Set<Scenario> scenarios = new HashSet<>(document.getScenarios());
          List<Scenario> inputScenarios =
              fromIterable(scenarioRepository.findAllById(input.getScenarioIds()));
          scenarios.addAll(inputScenarios);
          document.setScenarios(scenarios);
        }
        // Compute tags
        Set<Tag> tags = new HashSet<>(document.getTags());
        List<Tag> inputTags = fromIterable(tagRepository.findAllById(input.getTagIds()));
        tags.addAll(inputTags);
        document.setTags(tags);
        return save(document);
      } else {
        fileService.uploadFile(
            fileTarget, new ByteArrayInputStream(content), fileSize, fileContentType);
        Document document = new Document();
        document.setTarget(fileTarget);
        document.setName(fileName);
        document.setDescription(input.getDescription());
        if (!input.getExerciseIds().isEmpty()) {
          document.setExercises(
              iterableToSet(exerciseRepository.findAllById(input.getExerciseIds())));
        }
        if (!input.getScenarioIds().isEmpty()) {
          document.setScenarios(
              iterableToSet(scenarioRepository.findAllById(input.getScenarioIds())));
        }
        document.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));
        document.setType(fileContentType);
        return save(document);
      }
    }
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

  public List<Document> findAllDistinctOnInjectsByScenarioId(@NotBlank String scenarioId) {
    return this.documentRepository.findAllDistinctOnInjectsByScenarioId(scenarioId);
  }

  public boolean documentExists(String documentId) {
    return this.documentRepository.existsById(documentId);
  }

  public Document save(Document document) {
    return documentRepository.save(document);
  }
}
