package io.openbas.rest.executor;

import static io.openbas.service.EndpointService.JFROG_BASE;
import static io.openbas.service.EndpointService.SERVICE;
import static io.openbas.utils.AgentUtils.AVAILABLE_ARCHITECTURES;
import static io.openbas.utils.AgentUtils.AVAILABLE_PLATFORMS;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openbas.aop.RBAC;
import io.openbas.database.model.Action;
import io.openbas.database.model.Executor;
import io.openbas.database.model.ResourceType;
import io.openbas.database.model.Token;
import io.openbas.database.repository.ExecutorRepository;
import io.openbas.database.repository.TokenRepository;
import io.openbas.rest.exception.ElementNotFoundException;
import io.openbas.rest.executor.form.ExecutorCreateInput;
import io.openbas.rest.executor.form.ExecutorUpdateInput;
import io.openbas.rest.helper.RestBehavior;
import io.openbas.service.EndpointService;
import io.openbas.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.annotation.Resource;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Instant;
import java.util.Optional;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ExecutorApi extends RestBehavior {

  @Value("${info.app.version:unknown}")
  String version;

  @Value("${executor.openbas.binaries.origin:local}")
  private String executorOpenbasBinariesOrigin;

  @Value("${executor.openbas.binaries.version:${info.app.version:unknown}}")
  private String executorOpenbasBinariesVersion;

  private ExecutorRepository executorRepository;
  private EndpointService endpointService;
  private FileService fileService;
  private TokenRepository tokenRepository;

  @Resource protected ObjectMapper mapper;

  @Autowired
  public void setTokenRepository(TokenRepository tokenRepository) {
    this.tokenRepository = tokenRepository;
  }

  @Autowired
  public void setEndpointService(EndpointService endpointService) {
    this.endpointService = endpointService;
  }

  @Autowired
  public void setFileService(FileService fileService) {
    this.fileService = fileService;
  }

  @Autowired
  public void setExecutorRepository(ExecutorRepository executorRepository) {
    this.executorRepository = executorRepository;
  }

  @GetMapping("/api/executors")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.ASSET)
  public Iterable<Executor> executors() {
    return executorRepository.findAll();
  }

  private Executor updateExecutor(Executor executor, String type, String name, String[] platforms) {
    executor.setUpdatedAt(Instant.now());
    executor.setType(type);
    executor.setName(name);
    executor.setPlatforms(platforms);
    return executorRepository.save(executor);
  }

  @PutMapping("/api/executors/{executorId}")
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
      value = "/api/executors",
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
      summary = "Retrieve OpenBAS Agent Executable",
      description =
          "Downloads the OpenBAS agent executable for a specified platform and architecture.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved the executable."),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid platform or architecture specified."),
      })
  @GetMapping(
      value = "/api/agent/executable/openbas/{platform}/{architecture}",
      produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  @RBAC(skipRBAC = true)
  public @ResponseBody ResponseEntity<byte[]> getOpenBasAgentExecutable(
      @Parameter(
              description =
                  "Target platform for the agent installation (e.g., windows, linux, mac). Case insensitive.",
              required = true)
          @PathVariable
          String platform,
      @Parameter(
              description =
                  "Target architecture for the agent installation (e.g., x86_64, arm64). Case insensitive.",
              required = true)
          @PathVariable
          String architecture)
      throws IOException {
    platform = Optional.ofNullable(platform).map(String::toLowerCase).orElse("");
    architecture = Optional.ofNullable(architecture).map(String::toLowerCase).orElse("");

    if (!AVAILABLE_PLATFORMS.contains(platform)) {
      throw new IllegalArgumentException("Platform invalid : " + platform);
    }
    if (!AVAILABLE_ARCHITECTURES.contains(architecture)) {
      throw new IllegalArgumentException("Architecture invalid : " + architecture);
    }

    InputStream in = null;
    String resourcePath = "/openbas-agent/" + platform + "/" + architecture + "/";
    String filename = "";

    if (executorOpenbasBinariesOrigin.equals("local")) { // if we want the local binaries
      filename = "openbas-agent-" + version + (platform.equals("windows") ? ".exe" : "");
      in = getClass().getResourceAsStream("/agents" + resourcePath + filename);
    } else if (executorOpenbasBinariesOrigin.equals(
        "repository")) { // if we want a specific version from artifactory
      filename =
          "openbas-agent-"
              + executorOpenbasBinariesVersion
              + (platform.equals("windows") ? ".exe" : "");
      in = new BufferedInputStream(new URL(JFROG_BASE + resourcePath + filename).openStream());
    }
    if (in != null) {
      HttpHeaders headers = new HttpHeaders();
      headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename);
      return ResponseEntity.ok()
          .headers(headers)
          .contentType(MediaType.APPLICATION_OCTET_STREAM)
          .body(IOUtils.toByteArray(in));
    }
    throw new UnsupportedOperationException("Agent " + platform + " executable not supported");
  }

  // Public API
  @Operation(
      summary = "Retrieve OpenBAS Agent Package",
      description =
          "Downloads the OpenBAS agent package for the specified platform and architecture.")
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
      value = "/api/agent/package/openbas/{platform}/{architecture}/{installationMode}",
      produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  @RBAC(skipRBAC = true)
  public @ResponseBody ResponseEntity<byte[]> getOpenBasAgentPackage(
      @Parameter(
              description =
                  "Target platform for the agent package (e.g., windows, linux, mac). Case insensitive.",
              required = true)
          @PathVariable
          String platform,
      @Parameter(
              description =
                  "Target architecture for the agent package (e.g., x86, x64, arm). Case insensitive.",
              required = true)
          @PathVariable
          String architecture,
      @Parameter(
              description = "Installation Mode: session, user or system service",
              required = true)
          @PathVariable
          String installationMode)
      throws IOException {
    platform = Optional.ofNullable(platform).map(String::toLowerCase).orElse("");
    architecture = Optional.ofNullable(architecture).map(String::toLowerCase).orElse("");

    if (!AVAILABLE_PLATFORMS.contains(platform)) {
      throw new IllegalArgumentException("Platform invalid : " + platform);
    }
    if (!AVAILABLE_ARCHITECTURES.contains(architecture)) {
      throw new IllegalArgumentException("Architecture invalid : " + architecture);
    }

    byte[] file = null;
    String filename = null;

    if (platform.equals("windows")) {
      InputStream in = null;
      String resourcePath = "/openbas-agent/windows/" + architecture + "/";

      filename = "openbas-agent-installer-";
      if (installationMode != null && !installationMode.equals(SERVICE)) {
        filename = filename.concat(installationMode).concat("-");
      }

      if (executorOpenbasBinariesOrigin.equals("local")) { // if we want the local binaries
        filename = filename.concat(version).concat(".exe");
        in = getClass().getResourceAsStream("/agents" + resourcePath + filename);
      } else if (executorOpenbasBinariesOrigin.equals(
          "repository")) { // if we want a specific version from artifactory
        filename = filename.concat(executorOpenbasBinariesVersion).concat(".exe");
        in = new BufferedInputStream(new URL(JFROG_BASE + resourcePath + filename).openStream());
      }
      if (in == null) {
        throw new UnsupportedOperationException(
            "Agent version " + executorOpenbasBinariesVersion + " not found");
      }
      file = IOUtils.toByteArray(in);
    }
    // linux & macos - No package needed
    if (file != null) {
      HttpHeaders headers = new HttpHeaders();
      headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename);
      return ResponseEntity.ok()
          .headers(headers)
          .contentType(MediaType.APPLICATION_OCTET_STREAM)
          .body(file);
    }
    throw new UnsupportedOperationException("Agent " + platform + " package not supported");
  }

  // Public API
  @Operation(
      summary = "Retrieve OpenBAS Agent Installer Command",
      description =
          "Generates the installation command for the OpenBAS agent for the specified platform, installation mode and token.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Successfully generated the install command."),
        @ApiResponse(responseCode = "400", description = "Invalid platform specified."),
        @ApiResponse(responseCode = "404", description = "Token not found."),
      })
  @GetMapping(value = "/api/agent/installer/openbas/{platform}/{installationMode}/{token}")
  @RBAC(skipRBAC = true)
  public @ResponseBody ResponseEntity<String> getOpenBasAgentInstaller(
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
    platform = Optional.ofNullable(platform).map(String::toLowerCase).orElse("");

    if (!AVAILABLE_PLATFORMS.contains(platform)) {
      throw new IllegalArgumentException("Platform invalid : " + platform);
    }
    Optional<Token> resolvedToken = tokenRepository.findByValue(token);
    if (resolvedToken.isEmpty()) {
      throw new UnsupportedOperationException("Invalid token");
    }
    String installCommand =
        this.endpointService.generateInstallCommand(
            platform, token, installationMode, installationDir, serviceName);
    return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(installCommand);
  }
}
