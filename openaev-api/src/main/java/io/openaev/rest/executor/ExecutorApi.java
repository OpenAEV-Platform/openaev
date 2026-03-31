package io.openaev.rest.executor;

import static io.openaev.service.EndpointService.SERVICE;
import static io.openaev.utils.AgentUtils.AVAILABLE_ARCHITECTURES;
import static io.openaev.utils.AgentUtils.AVAILABLE_PLATFORMS;
import static io.openaev.utils.AgentUtils.normalizeArchitecture;
import static io.openaev.utils.AgentUtils.normalizePlatform;
import static io.openaev.utils.SecurityUtils.validateJFrogUri;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.aop.RBAC;
import io.openaev.database.model.*;
import io.openaev.database.repository.ExecutorRepository;
import io.openaev.database.repository.TokenRepository;
import io.openaev.executors.ExecutorService;
import io.openaev.rest.catalog_connector.dto.ConnectorIds;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.executor.form.ExecutorCreateInput;
import io.openaev.rest.executor.form.ExecutorOutput;
import io.openaev.rest.executor.form.ExecutorUpdateInput;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.EndpointService;
import io.openaev.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.annotation.Resource;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ExecutorApi extends RestBehavior {

  public static final String EXECUTOR_URI = "/api/executors";

  @Value("${info.app.version:unknown}")
  String version;

  @Value("${executor.openaev.binaries.origin:local}")
  private String executorOpenaevBinariesOrigin;

  @Value("${executor.openaev.binaries.version:${info.app.version:unknown}}")
  private String executorOpenaevBinariesVersion;

  private final ExecutorRepository executorRepository;
  private final EndpointService endpointService;
  private final FileService fileService;
  private final TokenRepository tokenRepository;
  private final ExecutorService executorService;

  @Resource protected ObjectMapper mapper;

  @GetMapping(EXECUTOR_URI)
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.ASSET)
  @Operation(
      summary = "Retrieve executors",
      description = "Retrieve all executors and pending executors if includeNext is true")
  @ApiResponse(
      responseCode = "200",
      content =
          @Content(
              mediaType = "application/json",
              array = @ArraySchema(schema = @Schema(implementation = ExecutorOutput.class))))
  public Iterable<ExecutorOutput> executors(
      @Parameter(
              name = "includeNext",
              description = "Include executors pending deployment",
              required = false)
          @RequestParam(value = "include_next", required = false, defaultValue = "false")
          boolean includeNext) {
    return executorService.executorsOutput(includeNext);
  }

  @GetMapping(EXECUTOR_URI + "/{executorId}")
  @RBAC(
      resourceId = "#collectorId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.ASSET)
  public Executor getExecutor(@PathVariable String executorId) {
    return executorService.executor(executorId);
  }

  @GetMapping(EXECUTOR_URI + "/{executorId}/related-ids")
  @RBAC(
      resourceId = "#executorId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.ASSET)
  @Operation(summary = "Retrieve executor related ids")
  public ConnectorIds getExecutorRelatedIds(@PathVariable String executorId) {
    return executorService.getExecutorRelationsId(executorId);
  }

  private Executor updateExecutor(Executor executor, String type, String name, String[] platforms) {
    executor.setUpdatedAt(Instant.now());
    executor.setType(type);
    executor.setName(name);
    executor.setPlatforms(platforms);
    return executorRepository.save(executor);
  }

  @PutMapping(EXECUTOR_URI + "/{executorId}")
  @RBAC(
      resourceId = "#executorId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.ASSET)
  public Executor updateExecutor(
      @PathVariable String executorId, @Valid @RequestBody ExecutorUpdateInput input) {
    Executor executor =
        executorRepository.findById(executorId).orElseThrow(ElementNotFoundException::new);
    return updateExecutor(
        executor, executor.getType(), executor.getName(), executor.getPlatforms());
  }

  @PostMapping(
      value = EXECUTOR_URI,
      produces = {MediaType.APPLICATION_JSON_VALUE},
      consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.ASSET)
  @Transactional(rollbackOn = Exception.class)
  public Executor registerExecutor(
      @Valid @RequestPart("input") ExecutorCreateInput input,
      @RequestPart("icon") Optional<MultipartFile> icon,
      @RequestPart("banner") Optional<MultipartFile> banner) {
    try {
      // Upload icon
      if (icon.isPresent() && "image/png".equals(icon.get().getContentType())) {
        fileService.uploadFile(
            FileService.EXECUTORS_IMAGES_ICONS_BASE_PATH + input.getType() + ".png", icon.get());
      }
      // Upload icon
      if (banner.isPresent() && "image/png".equals(banner.get().getContentType())) {
        fileService.uploadFile(
            FileService.EXECUTORS_IMAGES_BANNERS_BASE_PATH + input.getType() + ".png",
            banner.get());
      }
      // We need to support upsert for registration
      Executor executor = executorRepository.findById(input.getId()).orElse(null);
      if (executor == null) {
        Executor executorChecking = executorRepository.findByType(input.getType()).orElse(null);
        if (executorChecking != null) {
          throw new Exception(
              "The executor "
                  + input.getType()
                  + " already exists with a different ID, please delete it or contact your administrator.");
        }
      }
      if (executor != null) {
        return updateExecutor(executor, input.getType(), input.getName(), input.getPlatforms());
      } else {
        // save the injector
        Executor newExecutor = new Executor();
        newExecutor.setId(input.getId());
        newExecutor.setName(input.getName());
        newExecutor.setType(input.getType());
        newExecutor.setPlatforms(input.getPlatforms());
        return executorRepository.save(newExecutor);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  // Public API
  @Operation(
      summary = "Retrieve OpenAEV Agent Executable",
      description =
          "Downloads the OpenAEV agent executable for a specified platform and architecture.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved the executable."),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid platform or architecture specified."),
      })
  @GetMapping(
      value = "/api/agent/executable/openaev/{platform}/{architecture}",
      produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  @RBAC(skipRBAC = true)
  public @ResponseBody ResponseEntity<byte[]> getOpenAevAgentExecutable(
      @Parameter(
              description =
                  "Target platform for the agent installation (e.g., windows, linux, mac). Case insensitive.",
              required = true)
          @PathVariable
          String platform,
      @Parameter(
              description =
                  "Target architecture for the agent installation (e.g., x86_64, arm64, aarch64). Case insensitive.",
              required = true)
          @PathVariable
          String architecture)
      throws IOException {
    // Normalize platform and architecture aliases (e.g. aarch64 -> arm64, darwin -> macos)
    String normalizedPlatform = normalizePlatform(platform);
    String normalizedArch = normalizeArchitecture(architecture);

    log.info(
        "[agent-download] Executable request: platform={} (raw={}), arch={} (raw={}), version={}, origin={}",
        normalizedPlatform,
        platform,
        normalizedArch,
        architecture,
        executorOpenaevBinariesVersion,
        executorOpenaevBinariesOrigin);

    if (!AVAILABLE_PLATFORMS.contains(normalizedPlatform)) {
      log.warn(
          "[agent-download] Rejected: unsupported platform '{}' (raw='{}'). Available: {}",
          normalizedPlatform,
          platform,
          AVAILABLE_PLATFORMS);
      throw new IllegalArgumentException(
          "Platform invalid: '"
              + platform
              + "'. Supported platforms: "
              + AVAILABLE_PLATFORMS);
    }
    if (!AVAILABLE_ARCHITECTURES.contains(normalizedArch)) {
      log.warn(
          "[agent-download] Rejected: unsupported architecture '{}' (raw='{}'). Available: {}",
          normalizedArch,
          architecture,
          AVAILABLE_ARCHITECTURES);
      throw new IllegalArgumentException(
          "Architecture invalid: '"
              + architecture
              + "'. Supported architectures: "
              + AVAILABLE_ARCHITECTURES);
    }

    InputStream in = null;
    String resourcePath = "/openaev-agent/" + normalizedPlatform + "/" + normalizedArch + "/";
    String filename = "";

    if (executorOpenaevBinariesOrigin.equals("local")) { // if we want the local binaries
      filename =
          "openaev-agent-" + version + (normalizedPlatform.equals("windows") ? ".exe" : "");
      in = getClass().getResourceAsStream("/agents" + resourcePath + filename);
    } else if (executorOpenaevBinariesOrigin.equals(
        "repository")) { // if we want a specific version from artifactory
      filename =
          "openaev-agent-"
              + executorOpenaevBinariesVersion
              + (normalizedPlatform.equals("windows") ? ".exe" : "");
      in = new BufferedInputStream(validateJFrogUri(resourcePath, filename).toURL().openStream());
    }
    if (in != null) {
      log.info(
          "[agent-download] Serving executable: {} ({}/{})",
          filename,
          normalizedPlatform,
          normalizedArch);
      HttpHeaders headers = new HttpHeaders();
      headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename);
      return ResponseEntity.ok()
          .headers(headers)
          .contentType(MediaType.APPLICATION_OCTET_STREAM)
          .body(IOUtils.toByteArray(in));
    }
    log.error(
        "[agent-download] Binary not found: platform={}, arch={}, version={}, origin={}",
        normalizedPlatform,
        normalizedArch,
        executorOpenaevBinariesVersion,
        executorOpenaevBinariesOrigin);
    throw new UnsupportedOperationException(
        "Agent executable not found for "
            + normalizedPlatform
            + "/"
            + normalizedArch
            + " (version: "
            + executorOpenaevBinariesVersion
            + ", origin: "
            + executorOpenaevBinariesOrigin
            + ")");
  }

  // Public API
  @Operation(
      summary = "Retrieve OpenAEV Agent Package",
      description =
          "Downloads the OpenAEV agent package for the specified platform and architecture.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved the agent package."),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid platform or architecture specified."),
      })
  @GetMapping(
      value = "/api/agent/package/openaev/{platform}/{architecture}/{installationMode}",
      produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  @RBAC(skipRBAC = true)
  public @ResponseBody ResponseEntity<byte[]> getOpenAevAgentPackage(
      @Parameter(
              description =
                  "Target platform for the agent package (e.g., windows, linux, mac). Case insensitive.",
              required = true)
          @PathVariable
          String platform,
      @Parameter(
              description =
                  "Target architecture for the agent package (e.g., x86_64, arm64, aarch64). Case insensitive.",
              required = true)
          @PathVariable
          String architecture,
      @Parameter(
              description = "Installation Mode: session, user or system service",
              required = true)
          @PathVariable
          String installationMode)
      throws IOException {
    // Normalize platform and architecture aliases (e.g. aarch64 -> arm64, darwin -> macos)
    String normalizedPlatform = normalizePlatform(platform);
    String normalizedArch = normalizeArchitecture(architecture);

    log.info(
        "[agent-download] Package request: platform={} (raw={}), arch={} (raw={}), mode={}, version={}, origin={}",
        normalizedPlatform,
        platform,
        normalizedArch,
        architecture,
        installationMode,
        executorOpenaevBinariesVersion,
        executorOpenaevBinariesOrigin);

    if (!AVAILABLE_PLATFORMS.contains(normalizedPlatform)) {
      log.warn(
          "[agent-download] Rejected: unsupported platform '{}' (raw='{}'). Available: {}",
          normalizedPlatform,
          platform,
          AVAILABLE_PLATFORMS);
      throw new IllegalArgumentException(
          "Platform invalid: '"
              + platform
              + "'. Supported platforms: "
              + AVAILABLE_PLATFORMS);
    }
    if (!AVAILABLE_ARCHITECTURES.contains(normalizedArch)) {
      log.warn(
          "[agent-download] Rejected: unsupported architecture '{}' (raw='{}'). Available: {}",
          normalizedArch,
          architecture,
          AVAILABLE_ARCHITECTURES);
      throw new IllegalArgumentException(
          "Architecture invalid: '"
              + architecture
              + "'. Supported architectures: "
              + AVAILABLE_ARCHITECTURES);
    }

    byte[] file = null;
    String filename = null;

    if (normalizedPlatform.equals("windows")) {
      InputStream in = null;
      String resourcePath = "/openaev-agent/windows/" + normalizedArch + "/";

      filename = "openaev-agent-installer-";
      if (installationMode != null && !installationMode.equals(SERVICE)) {
        filename = filename.concat(installationMode).concat("-");
      }

      if (executorOpenaevBinariesOrigin.equals("local")) { // if we want the local binaries
        filename = filename.concat(version).concat(".exe");
        in = getClass().getResourceAsStream("/agents" + resourcePath + filename);
      } else if (executorOpenaevBinariesOrigin.equals(
          "repository")) { // if we want a specific version from artifactory
        filename = filename.concat(executorOpenaevBinariesVersion).concat(".exe");
        in = new BufferedInputStream(validateJFrogUri(resourcePath, filename).toURL().openStream());
      }
      if (in == null) {
        log.error(
            "[agent-download] Package not found: platform={}, arch={}, mode={}, version={}",
            normalizedPlatform,
            normalizedArch,
            installationMode,
            executorOpenaevBinariesVersion);
        throw new UnsupportedOperationException(
            "Agent version " + executorOpenaevBinariesVersion + " not found");
      }
      file = IOUtils.toByteArray(in);
    }
    // linux & macos - No package needed
    if (file != null) {
      log.info(
          "[agent-download] Serving package: {} ({}/{})",
          filename,
          normalizedPlatform,
          normalizedArch);
      HttpHeaders headers = new HttpHeaders();
      headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename);
      return ResponseEntity.ok()
          .headers(headers)
          .contentType(MediaType.APPLICATION_OCTET_STREAM)
          .body(file);
    }
    throw new UnsupportedOperationException(
        "Agent " + normalizedPlatform + " package not supported");
  }

  // Public API
  @Operation(
      summary = "Retrieve OpenAEV Agent Installer Command",
      description =
          "Generates the installation command for the OpenAEV agent for the specified platform, installation mode and token.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Successfully generated the install command."),
        @ApiResponse(responseCode = "400", description = "Invalid platform specified."),
        @ApiResponse(responseCode = "404", description = "Token not found."),
      })
  @GetMapping(value = "/api/agent/installer/openaev/{platform}/{installationMode}/{token}")
  @RBAC(skipRBAC = true)
  public @ResponseBody ResponseEntity<String> getOpenAevAgentInstaller(
      @Parameter(
              description =
                  "Target platform for the agent installation (e.g., windows, linux, mac). Case insensitive.",
              required = true)
          @PathVariable
          String platform,
      @Parameter(
              description = "Unique token associated with the agent installation.",
              required = true)
          @PathVariable
          String token,
      @Parameter(
              description = "Installation Mode: session, user or system service",
              required = true)
          @PathVariable
          String installationMode,
      @Parameter(description = "Installation directory") @RequestParam(required = false)
          String installationDir,
      @Parameter(description = "Service name") @RequestParam(required = false) String serviceName)
      throws IOException {
    // Normalize platform alias (e.g. darwin -> macos)
    String normalizedPlatform = normalizePlatform(platform);

    log.info(
        "[agent-install] Installer request: platform={} (raw={}), mode={}",
        normalizedPlatform,
        platform,
        installationMode);

    if (!AVAILABLE_PLATFORMS.contains(normalizedPlatform)) {
      log.warn(
          "[agent-install] Rejected: unsupported platform '{}' (raw='{}'). Available: {}",
          normalizedPlatform,
          platform,
          AVAILABLE_PLATFORMS);
      throw new IllegalArgumentException(
          "Platform invalid: '"
              + platform
              + "'. Supported platforms: "
              + AVAILABLE_PLATFORMS);
    }
    Optional<Token> resolvedToken = tokenRepository.findByValue(token);
    if (resolvedToken.isEmpty()) {
      throw new UnsupportedOperationException("Invalid token");
    }
    String installCommand =
        this.endpointService.generateInstallCommand(
            normalizedPlatform, token, installationMode, installationDir, serviceName);
    return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(installCommand);
  }
}
