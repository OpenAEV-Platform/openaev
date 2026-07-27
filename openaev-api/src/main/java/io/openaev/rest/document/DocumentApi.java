package io.openaev.rest.document;

import static io.openaev.config.SessionHelper.currentUser;
import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.helper.StreamHelper.iterableToSet;
import static io.openaev.utils.mapper.DocumentMapper.toDocumentRelationsOutput;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.aop.UrlAccessControl;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.raw.RawDocument;
import io.openaev.database.raw.RawPaginationDocument;
import io.openaev.database.repository.*;
import io.openaev.rest.document.form.DocumentCreateInput;
import io.openaev.rest.document.form.DocumentRelationsOutput;
import io.openaev.rest.document.form.DocumentTagUpdateInput;
import io.openaev.rest.document.form.DocumentUpdateInput;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.security.error.AuthenticationError;
import io.openaev.service.ChannelService;
import io.openaev.service.FileService;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FilenameUtils;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class DocumentApi extends RestBehavior {

  public static final String DOCUMENT_API = "/api/documents";
  private static final String TENANT_DOCUMENT_API = TENANT_PREFIX + "/documents";
  private static final String IMAGES_API = "/api/images";
  private static final String TENANT_IMAGES_API = TENANT_PREFIX + "/images";
  private static final String SECURITY_PLATFORM_IMAGES_API = IMAGES_API + "/security_platforms";
  private static final String TENANT_SECURITY_PLATFORM_IMAGES_API =
      TENANT_IMAGES_API + "/security_platforms";
  private static final String CHANNEL_IMAGES_API = IMAGES_API + "/channels";
  private static final String TENANT_CHANNEL_IMAGES_API = TENANT_IMAGES_API + "/channels";
  private static final String EXECUTOR_IMAGES_API = IMAGES_API + "/executors";
  private static final String TENANT_EXECUTOR_IMAGES_API = TENANT_IMAGES_API + "/executors";
  private static final String PLAYER_DOCUMENTS_API = "/api/player/{exerciseOrScenarioId}/documents";
  private static final String TENANT_PLAYER_DOCUMENTS_API =
      TENANT_PREFIX + "/player/{exerciseOrScenarioId}/documents";

  private final TagRepository tagRepository;
  private final DocumentRepository documentRepository;
  private final ExerciseRepository exerciseRepository;
  private final ScenarioRepository scenarioRepository;
  private final UserRepository userRepository;
  private final SecurityPlatformRepository securityPlatformRepository;

  private final DocumentService documentService;
  private final FileService fileService;
  private final InjectService injectService;
  private final ChannelService channelService;

  @PostMapping({DOCUMENT_API, TENANT_DOCUMENT_API})
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.DOCUMENT)
  @Transactional(rollbackFor = Exception.class)
  public Document uploadDocument(
      @Valid @RequestPart("input") DocumentCreateInput input,
      @RequestPart("file") MultipartFile file)
      throws Exception {
    String extension = FilenameUtils.getExtension(file.getOriginalFilename());
    String fileTarget = DigestUtils.md5Hex(file.getInputStream()) + "." + extension;
    Optional<Document> targetDocument =
        documentRepository.findFirstByTargetOrderByIdAsc(fileTarget);
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
      return documentService.save(document);
    } else {
      fileService.uploadFile(fileTarget, file);
      Document document = new Document();
      document.setTarget(fileTarget);
      document.setName(file.getOriginalFilename());
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
      document.setType(file.getContentType());
      return documentService.save(document);
    }
  }

  @PostMapping({DOCUMENT_API + "/upsert", TENANT_DOCUMENT_API + "/upsert"})
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.DOCUMENT)
  @Transactional(rollbackFor = Exception.class)
  public Document upsertDocument(
      @Valid @RequestPart("input") DocumentCreateInput input,
      @RequestPart("file") MultipartFile file)
      throws Exception {
    return documentService.upsert(
        file.getOriginalFilename(),
        file.getInputStream(),
        file.getSize(),
        file.getContentType(),
        input);
  }

  @GetMapping({DOCUMENT_API, TENANT_DOCUMENT_API})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.DOCUMENT)
  public List<RawDocument> documents() {
    return documentRepository.rawAllDocuments();
  }

  @PostMapping({DOCUMENT_API + "/search", TENANT_DOCUMENT_API + "/search"})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.DOCUMENT)
  public Page<RawPaginationDocument> searchDocuments(
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    List<Document> securityPlatformLogos = securityPlatformRepository.securityPlatformLogo();
    return buildPaginationJPA(
            (Specification<Document> specification, Pageable pageable) ->
                this.documentRepository.findAll(specification, pageable),
            searchPaginationInput,
            Document.class)
        .map(
            (document) -> {
              var rawPaginationDocument = new RawPaginationDocument(document);
              rawPaginationDocument.setDocument_can_be_deleted(
                  !securityPlatformLogos.contains(document));
              return rawPaginationDocument;
            });
  }

  @GetMapping({DOCUMENT_API + "/{documentId}", TENANT_DOCUMENT_API + "/{documentId}"})
  @Transactional
  @AccessControl(
      resourceId = "#documentId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.DOCUMENT)
  public Document document(@PathVariable String documentId) {
    return documentRepository
        .findById(documentId)
        .orElseThrow(() -> new ElementNotFoundException("Document not found"));
  }

  @GetMapping({DOCUMENT_API + "/{documentId}/tags", TENANT_DOCUMENT_API + "/{documentId}/tags"})
  @Transactional
  @AccessControl(
      resourceId = "#documentId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.DOCUMENT)
  public Set<Tag> documentTags(@PathVariable String documentId) {
    Document document =
        documentRepository
            .findById(documentId)
            .orElseThrow(() -> new ElementNotFoundException("Document not found"));
    return document.getTags();
  }

  @PutMapping({DOCUMENT_API + "/{documentId}/tags", TENANT_DOCUMENT_API + "/{documentId}/tags"})
  @Transactional
  @AccessControl(
      resourceId = "#documentId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.DOCUMENT)
  public Document documentTags(
      @PathVariable String documentId, @RequestBody DocumentTagUpdateInput input) {
    Document document =
        documentRepository
            .findById(documentId)
            .orElseThrow(() -> new ElementNotFoundException("Document not found"));
    document.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));
    return documentService.save(document);
  }

  @Transactional(rollbackFor = Exception.class)
  @PutMapping({DOCUMENT_API + "/{documentId}", TENANT_DOCUMENT_API + "/{documentId}"})
  @AccessControl(
      resourceId = "#documentId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.DOCUMENT)
  public Document updateDocumentInformation(
      @PathVariable String documentId, @Valid @RequestBody DocumentUpdateInput input) {
    Document document =
        documentRepository
            .findById(documentId)
            .orElseThrow(() -> new ElementNotFoundException("Document not found"));
    document.setUpdateAttributes(input);
    document.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));

    // Get removed exercises
    Stream<String> askExerciseIdsStream =
        document.getExercises().stream()
            .filter(
                exercise ->
                    !exercise.isUserHasAccess(
                        userRepository
                            .findById(currentUser().getId())
                            .orElseThrow(
                                () -> new ElementNotFoundException("Current user not found"))))
            .map(Exercise::getId);
    List<String> askExerciseIds =
        Stream.concat(askExerciseIdsStream, input.getExerciseIds().stream()).distinct().toList();
    List<Exercise> removedExercises =
        document.getExercises().stream()
            .filter(exercise -> !askExerciseIds.contains(exercise.getId()))
            .toList();
    document.setExercises(iterableToSet(exerciseRepository.findAllById(askExerciseIds)));
    // In case of exercise removal, all inject doc attachment for exercise
    removedExercises.forEach(
        exercise -> injectService.cleanInjectsDocExercise(exercise.getId(), documentId));

    // Get removed scenarios
    Stream<String> askScenarioIdsStream =
        document.getScenarios().stream()
            .filter(
                scenario ->
                    !scenario.isUserHasAccess(
                        userRepository
                            .findById(currentUser().getId())
                            .orElseThrow(
                                () -> new ElementNotFoundException("Current user not found"))))
            .map(Scenario::getId);
    List<String> askScenarioIds =
        Stream.concat(askScenarioIdsStream, input.getScenarioIds().stream()).distinct().toList();
    List<Scenario> removedScenarios =
        document.getScenarios().stream()
            .filter(scenario -> !askScenarioIds.contains(scenario.getId()))
            .toList();
    document.setScenarios(iterableToSet(scenarioRepository.findAllById(askScenarioIds)));
    // In case of scenario removal, all inject doc attachment for scenario
    removedScenarios.forEach(
        scenario -> injectService.cleanInjectsDocScenario(scenario.getId(), documentId));

    // Save and return
    return documentService.save(document);
  }

  @GetMapping({DOCUMENT_API + "/{documentId}/file", TENANT_DOCUMENT_API + "/{documentId}/file"})
  @Transactional
  @AccessControl(
      resourceId = "#documentId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.DOCUMENT)
  public ResponseEntity<InputStreamResource> downloadDocument(@PathVariable String documentId) {
    Document document = documentService.document(documentId);

    String encodedFilename = DocumentService.encodeFileName(document.getName());
    InputStream in =
        fileService
            .getFile(document)
            .orElseThrow(() -> new ElementNotFoundException("File not found"));

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + encodedFilename)
        .header(HttpHeaders.CONTENT_TYPE, document.getType())
        .body(new InputStreamResource(in));
  }

  public ResponseEntity<InputStreamResource> downloadCollectorImage(
      @PathVariable String collectorType) {
    InputStream in =
        fileService
            .getCollectorImage(collectorType)
            .orElseThrow(() -> new ElementNotFoundException("File not found"));

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + collectorType + ".png")
        .contentType(MediaType.IMAGE_PNG)
        .body(new InputStreamResource(in));
  }

  @GetMapping(
      value = {
        SECURITY_PLATFORM_IMAGES_API + "/id/{assetId}/{theme}",
        TENANT_SECURITY_PLATFORM_IMAGES_API + "/id/{assetId}/{theme}"
      })
  @Transactional
  @AccessControl(skipRBAC = true)
  public ResponseEntity<InputStreamResource> getSecurityPlatformImageFromId(
      @PathVariable String assetId, @PathVariable String theme) {
    SecurityPlatform securityPlatform =
        this.securityPlatformRepository
            .findById(assetId)
            .orElseThrow(() -> new ElementNotFoundException("Security platform not found"));
    if (theme.equals("dark") && securityPlatform.getLogoDark() != null) {
      return downloadDocument(securityPlatform.getLogoDark().getId());
    } else if (securityPlatform.getLogoLight() != null) {
      return downloadDocument(securityPlatform.getLogoLight().getId());
    } else {
      return downloadCollectorImage("openaev_fake_detector");
    }
  }

  @GetMapping(
      value = {
        CHANNEL_IMAGES_API + "/id/{channelId}/{theme}",
        TENANT_CHANNEL_IMAGES_API + "/id/{channelId}/{theme}"
      })
  @AccessControl(
      resourceId = "#channelId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.CHANNEL)
  @Operation(summary = "Get the channel image")
  @Transactional
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Channel image"),
        @ApiResponse(responseCode = "404", description = "Channel not found")
      })
  public ResponseEntity<InputStreamResource> getChannelImageFromId(
      @PathVariable String channelId, @PathVariable String theme) {
    Channel channel = channelService.channel(channelId);

    if (theme.equals("dark") && channel.getLogoDark() != null) {
      return downloadDocument(channel.getLogoDark().getId());
    } else if (channel.getLogoLight() != null) {
      return downloadDocument(channel.getLogoLight().getId());
    } else {
      return downloadCollectorImage("openaev_fake_detector");
    }
  }

  @GetMapping(
      value = {
        EXECUTOR_IMAGES_API + "/icons/{executorId}",
        TENANT_EXECUTOR_IMAGES_API + "/icons/{executorId}"
      },
      produces = MediaType.IMAGE_PNG_VALUE)
  @Transactional
  @AccessControl(skipRBAC = true)
  public @ResponseBody ResponseEntity<InputStreamResource> getExecutorIconImage(
      @PathVariable String executorId) {
    return fileService
        .getExecutorIconImage(executorId)
        .map(
            inputStream ->
                ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES))
                    .body(new InputStreamResource(inputStream)))
        .orElse(null);
  }

  @GetMapping(
      value = {
        EXECUTOR_IMAGES_API + "/banners/{executorId}",
        TENANT_EXECUTOR_IMAGES_API + "/banners/{executorId}"
      },
      produces = MediaType.IMAGE_PNG_VALUE)
  @Transactional
  @AccessControl(skipRBAC = true)
  public @ResponseBody ResponseEntity<InputStreamResource> getExecutorBannerImage(
      @PathVariable String executorId) {
    return fileService
        .getExecutorBannerImage(executorId)
        .map(
            inputStream ->
                ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES))
                    .body(new InputStreamResource(inputStream)))
        .orElse(null);
  }

  private List<Document> getExercisePlayerDocuments(Exercise exercise) {
    List<Article> articles = exercise.getArticles();
    List<Inject> injects = exercise.getInjects();
    return documentService.getPlayerDocuments(articles, injects);
  }

  private List<Document> getScenarioPlayerDocuments(Scenario scenario) {
    List<Article> articles = scenario.getArticles();
    List<Inject> injects = scenario.getInjects();
    return documentService.getPlayerDocuments(articles, injects);
  }

  @LogExecutionTime
  @Operation(summary = "Fetch the entities related to this document id")
  @Transactional
  @GetMapping({
    DOCUMENT_API + "/{documentId}/relations",
    TENANT_DOCUMENT_API + "/{documentId}/relations"
  })
  @AccessControl(
      resourceId = "#documentId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.DOCUMENT)
  public DocumentRelationsOutput getDocumentRelations(@PathVariable String documentId) {
    return toDocumentRelationsOutput(documentService.document(documentId));
  }

  @Transactional(rollbackFor = Exception.class)
  @DeleteMapping({DOCUMENT_API + "/{documentId}", TENANT_DOCUMENT_API + "/{documentId}"})
  @AccessControl(
      resourceId = "#documentId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.DOCUMENT)
  public void deleteDocument(@PathVariable String documentId) {
    documentService.deleteDocument(documentId);
  }

  // -- EXERCISE & SENARIO--
  @GetMapping({PLAYER_DOCUMENTS_API, TENANT_PLAYER_DOCUMENTS_API})
  @Transactional
  @AccessControl(skipRBAC = true)
  @UrlAccessControl(userId = "#userId")
  public List<Document> playerDocuments(
      @PathVariable String exerciseOrScenarioId, @RequestParam Optional<String> userId)
      throws AuthenticationError {
    Optional<Exercise> exerciseOpt =
        this.exerciseRepository.findByIdAndTenantId(
            exerciseOrScenarioId, TenantContext.getCurrentTenant());
    Optional<Scenario> scenarioOpt =
        this.scenarioRepository.findByIdAndTenantId(
            exerciseOrScenarioId, TenantContext.getCurrentTenant());

    final User user = impersonateUser(userRepository, userId);

    if (exerciseOpt.isPresent()) {
      if (!exerciseOpt.get().isUserHasAccess(user)
          && !exerciseOpt.get().getUsers().contains(user)) {
        throw new AuthenticationError("The given player is not in this exercise");
      }
      return getExercisePlayerDocuments(exerciseOpt.get());
    } else if (scenarioOpt.isPresent()) {
      if (!scenarioOpt.get().isUserHasAccess(user)
          && !scenarioOpt.get().getUsers().contains(user)) {
        throw new AuthenticationError("The given player is not in this exercise");
      }
      return getScenarioPlayerDocuments(scenarioOpt.get());
    } else {
      throw new ElementNotFoundException("Exercise or scenario not found");
    }
  }

  @GetMapping({
    PLAYER_DOCUMENTS_API + "/{documentId}/file",
    TENANT_PLAYER_DOCUMENTS_API + "/{documentId}/file"
  })
  @Transactional
  @AccessControl(skipRBAC = true)
  @UrlAccessControl(userId = "#userId")
  public ResponseEntity<InputStreamResource> downloadPlayerDocument(
      @PathVariable String exerciseOrScenarioId,
      @PathVariable String documentId,
      @RequestParam Optional<String> userId)
      throws AuthenticationError {
    Optional<Exercise> exerciseOpt =
        this.exerciseRepository.findByIdAndTenantId(
            exerciseOrScenarioId, TenantContext.getCurrentTenant());
    Optional<Scenario> scenarioOpt =
        this.scenarioRepository.findByIdAndTenantId(
            exerciseOrScenarioId, TenantContext.getCurrentTenant());

    final User user = impersonateUser(userRepository, userId);

    Optional<Document> document = Optional.empty();
    if (exerciseOpt.isPresent()) {
      if (!exerciseOpt.get().isUserHasAccess(user)
          && !exerciseOpt.get().getUsers().contains(user)) {
        throw new AuthenticationError("The given player is not in this exercise");
      }
      document =
          getExercisePlayerDocuments(exerciseOpt.get()).stream()
              .filter(doc -> doc.getId().equals(documentId))
              .findFirst();
    } else if (scenarioOpt.isPresent()) {
      if (!scenarioOpt.get().isUserHasAccess(user)
          && !scenarioOpt.get().getUsers().contains(user)) {
        throw new AuthenticationError("The given player is not in this exercise");
      }
      document =
          getScenarioPlayerDocuments(scenarioOpt.get()).stream()
              .filter(doc -> doc.getId().equals(documentId))
              .findFirst();
    }

    Document doc = document.orElseThrow(() -> new ElementNotFoundException("File not found"));
    InputStream in =
        fileService.getFile(doc).orElseThrow(() -> new ElementNotFoundException("File not found"));

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + doc.getName())
        .header(HttpHeaders.CONTENT_TYPE, doc.getType())
        .body(new InputStreamResource(in));
  }
}
