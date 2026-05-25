package io.openaev.rest.document;

import static io.openaev.config.OpenAEVAnonymous.ANONYMOUS;
import static io.openaev.config.SessionHelper.currentUser;
import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.helper.StreamHelper.iterableToSet;
import static io.openaev.utils.mapper.DocumentMapper.toDocumentRelationsOutput;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
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
import io.openaev.service.ChannelService;
import io.openaev.service.FileService;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import java.io.IOException;
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
import org.apache.commons.io.IOUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class DocumentApi extends RestBehavior {

  public static final String DOCUMENT_API = "/api/documents";
  private static final String TENANT_DOCUMENT_API = TENANT_PREFIX + "/documents";
  private static final String IMAGES_API = "/api/images";
  private static final String TENANT_IMAGES_API = TENANT_PREFIX + "/images";
  private static final String INJECTOR_IMAGES_API = IMAGES_API + "/injectors";
  private static final String TENANT_INJECTOR_IMAGES_API = TENANT_IMAGES_API + "/injectors";
  private static final String COLLECTOR_IMAGES_API = IMAGES_API + "/collectors";
  private static final String TENANT_COLLECTOR_IMAGES_API = TENANT_IMAGES_API + "/collectors";
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
  private final InjectorRepository injectorRepository;
  private final CollectorRepository collectorRepository;
  private final SecurityPlatformRepository securityPlatformRepository;

  private final DocumentService documentService;
  private final FileService fileService;
  private final InjectService injectService;
  private final ChannelService channelService;

  @PostMapping({DOCUMENT_API, TENANT_DOCUMENT_API})
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.DOCUMENT)
  @Transactional(rollbackOn = Exception.class)
  public Document uploadDocument(
      @Valid @RequestPart("input") DocumentCreateInput input,
      @RequestPart("file") MultipartFile file)
      throws Exception {
    String extension = FilenameUtils.getExtension(file.getOriginalFilename());
    String fileTarget = DigestUtils.md5Hex(file.getInputStream()) + "." + extension;
    Optional<Document> targetDocument = documentRepository.findByTarget(fileTarget);
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
  @Transactional(rollbackOn = Exception.class)
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
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.DOCUMENT)
  public List<RawDocument> documents() {
    return documentRepository.rawAllDocuments();
  }

  @PostMapping({DOCUMENT_API + "/search", TENANT_DOCUMENT_API + "/search"})
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

  @Transactional(rollbackOn = Exception.class)
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
  @AccessControl(
      resourceId = "#documentId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.DOCUMENT)
  public void downloadDocument(@PathVariable String documentId, HttpServletResponse response)
      throws IOException {
    Document document = documentService.document(documentId);

    String encodedFilename = DocumentService.encodeFileName(document.getName());

    response.addHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + encodedFilename);
    response.addHeader(HttpHeaders.CONTENT_TYPE, document.getType());
    response.setStatus(HttpServletResponse.SC_OK);
    try (InputStream fileStream =
        fileService
            .getFile(document)
            .orElseThrow(() -> new ElementNotFoundException("File not found"))) {
      fileStream.transferTo(response.getOutputStream());
    }
  }

  @GetMapping(
      value = {
        INJECTOR_IMAGES_API + "/{injectorType}",
        TENANT_INJECTOR_IMAGES_API + "/{injectorType}"
      },
      produces = MediaType.IMAGE_PNG_VALUE)
  @AccessControl(skipRBAC = true)
  public @ResponseBody ResponseEntity<byte[]> getInjectorImage(@PathVariable String injectorType)
      throws IOException {
    Optional<InputStream> fileStream = fileService.getInjectorImage(injectorType);
    if (fileStream.isPresent()) {
      return ResponseEntity.ok()
          .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES))
          .body(IOUtils.toByteArray(fileStream.get()));
    }
    return null;
  }

  @GetMapping(
      value = {
        INJECTOR_IMAGES_API + "/id/{injectorId}",
        TENANT_INJECTOR_IMAGES_API + "/id/{injectorId}"
      },
      produces = MediaType.IMAGE_PNG_VALUE)
  @AccessControl(skipRBAC = true)
  public @ResponseBody ResponseEntity<byte[]> getInjectorImageFromId(
      @PathVariable String injectorId) throws IOException {
    Injector injector =
        this.injectorRepository
            .findByIdAndTenantId(injectorId, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new ElementNotFoundException("Injector not found"));
    Optional<InputStream> fileStream = fileService.getInjectorImage(injector.getType());
    if (fileStream.isPresent()) {
      return ResponseEntity.ok()
          .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES))
          .body(IOUtils.toByteArray(fileStream.get()));
    }
    return null;
  }

  @GetMapping(
      value = {
        COLLECTOR_IMAGES_API + "/{collectorType}",
        TENANT_COLLECTOR_IMAGES_API + "/{collectorType}"
      },
      produces = MediaType.IMAGE_PNG_VALUE)
  @AccessControl(skipRBAC = true)
  public @ResponseBody ResponseEntity<byte[]> getCollectorImage(@PathVariable String collectorType)
      throws IOException {
    Optional<InputStream> fileStream = fileService.getCollectorImage(collectorType);
    if (fileStream.isPresent()) {
      return ResponseEntity.ok()
          .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES))
          .body(IOUtils.toByteArray(fileStream.get()));
    }
    return null;
  }

  public void downloadCollectorImage(
      @PathVariable String collectorType, HttpServletResponse response) throws IOException {
    response.addHeader(
        HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + collectorType + ".png");
    response.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE);
    response.setStatus(HttpServletResponse.SC_OK);
    try (InputStream fileStream =
        fileService
            .getCollectorImage(collectorType)
            .orElseThrow(() -> new ElementNotFoundException("File not found"))) {
      fileStream.transferTo(response.getOutputStream());
    }
  }

  @GetMapping(
      value = {
        COLLECTOR_IMAGES_API + "/id/{collectorId}",
        TENANT_COLLECTOR_IMAGES_API + "/id/{collectorId}"
      },
      produces = MediaType.IMAGE_PNG_VALUE)
  @AccessControl(skipRBAC = true)
  public @ResponseBody ResponseEntity<byte[]> getCollectorImageFromId(
      @PathVariable String collectorId) throws IOException {
    Collector collector =
        this.collectorRepository
            .findByIdAndTenantId(collectorId, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new ElementNotFoundException("Collector not found"));
    Optional<InputStream> fileStream = fileService.getCollectorImage(collector.getType());
    if (fileStream.isPresent()) {
      return ResponseEntity.ok()
          .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES))
          .body(IOUtils.toByteArray(fileStream.get()));
    }
    return null;
  }

  @GetMapping(
      value = {
        SECURITY_PLATFORM_IMAGES_API + "/id/{assetId}/{theme}",
        TENANT_SECURITY_PLATFORM_IMAGES_API + "/id/{assetId}/{theme}"
      })
  @AccessControl(skipRBAC = true)
  public void getSecurityPlatformImageFromId(
      @PathVariable String assetId, @PathVariable String theme, HttpServletResponse response)
      throws IOException {
    SecurityPlatform securityPlatform =
        this.securityPlatformRepository
            .findById(assetId)
            .orElseThrow(() -> new ElementNotFoundException("Security platform not found"));
    if (theme.equals("dark") && securityPlatform.getLogoDark() != null) {
      downloadDocument(securityPlatform.getLogoDark().getId(), response);
    } else if (securityPlatform.getLogoLight() != null) {
      downloadDocument(securityPlatform.getLogoLight().getId(), response);
    } else {
      downloadCollectorImage("openaev_fake_detector", response);
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
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Channel image"),
        @ApiResponse(responseCode = "404", description = "Channel not found")
      })
  public void getChannelImageFromId(
      @PathVariable String channelId, @PathVariable String theme, HttpServletResponse response)
      throws IOException {
    Channel channel = channelService.channel(channelId);

    if (theme.equals("dark") && channel.getLogoDark() != null) {
      downloadDocument(channel.getLogoDark().getId(), response);
    } else if (channel.getLogoLight() != null) {
      downloadDocument(channel.getLogoLight().getId(), response);
    } else {
      downloadCollectorImage("openaev_fake_detector", response);
    }
  }

  @GetMapping(
      value = {
        EXECUTOR_IMAGES_API + "/icons/{executorId}",
        TENANT_EXECUTOR_IMAGES_API + "/icons/{executorId}"
      },
      produces = MediaType.IMAGE_PNG_VALUE)
  @AccessControl(skipRBAC = true)
  public @ResponseBody ResponseEntity<byte[]> getExecutorIconImage(@PathVariable String executorId)
      throws IOException {
    Optional<InputStream> fileStream = fileService.getExecutorIconImage(executorId);
    if (fileStream.isPresent()) {
      return ResponseEntity.ok()
          .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES))
          .body(IOUtils.toByteArray(fileStream.get()));
    }
    return null;
  }

  @GetMapping(
      value = {
        EXECUTOR_IMAGES_API + "/banners/{executorId}",
        TENANT_EXECUTOR_IMAGES_API + "/banners/{executorId}"
      },
      produces = MediaType.IMAGE_PNG_VALUE)
  @AccessControl(skipRBAC = true)
  public @ResponseBody ResponseEntity<byte[]> getExecutorBannerImage(
      @PathVariable String executorId) throws IOException {
    Optional<InputStream> fileStream = fileService.getExecutorBannerImage(executorId);
    if (fileStream.isPresent()) {
      return ResponseEntity.ok()
          .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES))
          .body(IOUtils.toByteArray(fileStream.get()));
    }
    return null;
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

  @Transactional(rollbackOn = Exception.class)
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
  @AccessControl(skipRBAC = true)
  public List<Document> playerDocuments(
      @PathVariable String exerciseOrScenarioId, @RequestParam Optional<String> userId) {
    Optional<Exercise> exerciseOpt = this.exerciseRepository.findById(exerciseOrScenarioId);
    Optional<Scenario> scenarioOpt = this.scenarioRepository.findById(exerciseOrScenarioId);

    final User user = impersonateUser(userRepository, userId);
    if (user.getId().equals(ANONYMOUS)) {
      throw new UnsupportedOperationException("User must be logged or dynamic player is required");
    }

    if (exerciseOpt.isPresent()) {
      if (!exerciseOpt.get().isUserHasAccess(user)
          && !exerciseOpt.get().getUsers().contains(user)) {
        throw new UnsupportedOperationException("The given player is not in this exercise");
      }
      return getExercisePlayerDocuments(exerciseOpt.get());
    } else if (scenarioOpt.isPresent()) {
      if (!scenarioOpt.get().isUserHasAccess(user)
          && !scenarioOpt.get().getUsers().contains(user)) {
        throw new UnsupportedOperationException("The given player is not in this exercise");
      }
      return getScenarioPlayerDocuments(scenarioOpt.get());
    } else {
      throw new IllegalArgumentException("Exercise or scenario ID not found");
    }
  }

  @GetMapping({
    PLAYER_DOCUMENTS_API + "/{documentId}/file",
    TENANT_PLAYER_DOCUMENTS_API + "/{documentId}/file"
  })
  @AccessControl(skipRBAC = true)
  public void downloadPlayerDocument(
      @PathVariable String exerciseOrScenarioId,
      @PathVariable String documentId,
      @RequestParam Optional<String> userId,
      HttpServletResponse response)
      throws IOException {
    Optional<Exercise> exerciseOpt = this.exerciseRepository.findById(exerciseOrScenarioId);
    Optional<Scenario> scenarioOpt = this.scenarioRepository.findById(exerciseOrScenarioId);

    final User user = impersonateUser(userRepository, userId);
    if (user.getId().equals(ANONYMOUS)) {
      throw new UnsupportedOperationException("User must be logged or dynamic player is required");
    }

    Document document = null;
    if (exerciseOpt.isPresent()) {
      if (!exerciseOpt.get().isUserHasAccess(user)
          && !exerciseOpt.get().getUsers().contains(user)) {
        throw new UnsupportedOperationException("The given player is not in this exercise");
      }
      document =
          getExercisePlayerDocuments(exerciseOpt.get()).stream()
              .filter(doc -> doc.getId().equals(documentId))
              .findFirst()
              .orElseThrow(() -> new ElementNotFoundException("Document not found"));
    } else if (scenarioOpt.isPresent()) {
      if (!scenarioOpt.get().isUserHasAccess(user)
          && !scenarioOpt.get().getUsers().contains(user)) {
        throw new UnsupportedOperationException("The given player is not in this exercise");
      }
      document =
          getScenarioPlayerDocuments(scenarioOpt.get()).stream()
              .filter(doc -> doc.getId().equals(documentId))
              .findFirst()
              .orElseThrow(() -> new ElementNotFoundException("Document not found"));
    }

    if (document != null) {
      response.addHeader(
          HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + document.getName());
      response.addHeader(HttpHeaders.CONTENT_TYPE, document.getType());
      response.setStatus(HttpServletResponse.SC_OK);
      try (InputStream fileStream =
          fileService
              .getFile(document)
              .orElseThrow(() -> new ElementNotFoundException("File not found"))) {
        fileStream.transferTo(response.getOutputStream());
      }
    }
  }
}
