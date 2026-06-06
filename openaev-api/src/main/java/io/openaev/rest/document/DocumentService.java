package io.openaev.rest.document;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.helper.StreamHelper.iterableToSet;
import static io.openaev.injectors.challenge.ChallengeContract.CHALLENGE_PUBLISH;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.context.ExecState;
import io.openaev.context.StateExecutionContext;
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

  /**
   * Lists all documents for the tenant carried by {@code state}. The {@link TenantContextAspect}
   * sets the tenant context automatically.
   */
  public List<RawDocument> rawAllDocuments(ExecState state) {
    return documentRepository.forOp(state).rawAllDocuments();
  }

  /**
   * Fetch a document by ID, scoped to the provided tenant.
   *
   * <p>The {@link TenantContextAspect} automatically sets {@link StateExecutionContext} from
   * {@code state} before this method runs, so the {@link TenantStatementInspector} will filter all
   * repository queries to the correct tenant. No manual wrapping needed.
   *
   * @param state tenant scope for this operation
   * @param documentId the document to fetch
   */
  public Document document(ExecState state, @NotBlank final String documentId) {
    return documentRepository
        .forOp(state)
        .findById(documentId)
        .orElseThrow(() -> new ElementNotFoundException("Document not found"));
  }

  /**
   * @deprecated Legacy bridge for internal callers (jobs, connectors) that do not yet carry an
   *     {@link ExecState}. Uses the {@link ExecState} already set in the {@link
   *     StateExecutionContext} by the interceptor or caller. Migrate callers to {@link
   *     #document(ExecState, String)} to make the contract explicit.
   */
  @Deprecated(since = "migration", forRemoval = true)
  public Document document(@NotBlank final String documentId) {
    ExecState state = StateExecutionContext.get();
    if (state == null) {
      throw new IllegalStateException(
          "No TenantExecutionContext active — use document(ExecState, String) instead");
    }
    return document(state, documentId);
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
      ExecState state,
      String fileName,
      InputStream fileIS,
      long fileSize,
      String fileContentType,
      DocumentCreateInput input)
      throws Exception {
    byte[] content = fileIS.readAllBytes();
    String extension = FilenameUtils.getExtension(fileName);
    String fileTarget = DigestUtils.md5Hex(new ByteArrayInputStream(content)) + "." + extension;
    Optional<Document> targetDocument = documentRepository.forOp(state).findByTarget(fileTarget);
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
      Optional<Document> existingDocument = documentRepository.forOp(state).findByName(fileName);
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
        document.setTenant(state.currentTenant());
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

  public void deleteDocument(ExecState state, String documentId) {
    Document document = document(state, documentId);

    boolean isUsedInFileDrop =
        document.getPayloadsByFileDrop() != null && !document.getPayloadsByFileDrop().isEmpty();
    boolean isUsedInExecutable =
        document.getPayloadsByExecutableFile() != null
            && !document.getPayloadsByExecutableFile().isEmpty();

    if (isUsedInFileDrop || isUsedInExecutable) {
      throw new BadRequestException(
          "Document is still in use for some payloads and cannot be deleted.");
    }

    List<Document> documents = documentRepository.forOp(state).removeById(documentId);

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
    return this.documentRepository.forCurrentTenant().findAllDistinctByScenarioId(scenarioId);
  }

  public List<Document> documentsForSimulation(String simulationId) {
    return this.documentRepository.forCurrentTenant().findAllDistinctBySimulationId(simulationId);
  }

  public List<RawDocument> documentsForChannel(@NotBlank String channelId) {
    return this.documentRepository.forCurrentTenant().rawAllDocumentsByChannelId(channelId);
  }

  public List<RawDocument> documentsForSecurityPlatform(@NotBlank String securityPlatformId) {
    return this.documentRepository
        .forCurrentTenant()
        .rawAllDocumentsBySecurityPlatformId(securityPlatformId);
  }

  public List<RawDocument> documentsForChallenge(@NotBlank String challengeId) {
    return this.documentRepository.forCurrentTenant().rawAllDocumentsByChallengeId(challengeId);
  }

  public List<RawDocument> documentsForPayload(@NotBlank String payloadId) {
    return this.documentRepository.forCurrentTenant().rawAllDocumentsByPayloadId(payloadId);
  }

  public List<Document> findAllDistinctOnInjectsByScenarioId(@NotBlank String scenarioId) {
    return this.documentRepository
        .forCurrentTenant()
        .findAllDistinctOnInjectsByScenarioId(scenarioId);
  }

  public boolean documentExists(String documentId) {
    return this.documentRepository.forCurrentTenant().existsById(documentId);
  }

  public Document save(Document document) {
    return documentRepository.forCurrentTenant().save(document);
  }
}
