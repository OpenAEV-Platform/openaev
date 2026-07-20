package io.openaev.rest.executor;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.service.EndpointService.SERVICE;
import static io.openaev.utils.SecurityUtils.validateJFrogUri;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.aop.AccessControl;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.repository.ExecutorRepository;
import io.openaev.executors.ExecutorService;
import io.openaev.rest.catalog_connector.dto.ConnectorIds;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.executor.form.ExecutorCreateInput;
import io.openaev.rest.executor.form.ExecutorOutput;
import io.openaev.rest.executor.form.ExecutorUpdateInput;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.EndpointService;
import io.openaev.service.FileService;
import io.openaev.service.account.ServiceAccountPrivilegeService;
import io.openaev.utils.AgentUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ExecutorApi extends RestBehavior {

  public static final String EXECUTOR_URI = "/api/executors";
  public static final String AGENT_URI = "/api/agent";
  private static final String TENANT_EXECUTOR_URI = TENANT_PREFIX + "/executors";
  private static final String TENANT_AGENT_URI = TENANT_PREFIX + "/agent";

  @Value("${info.app.version:unknown}")
  String version;

  @Value("${executor.openaev-agent.binaries.origin:${executor.openaev.binaries.origin:local}}")
  private String agentBinaryOrigin;

  @Value(
      "${executor.openaev-agent.binaries.version:${executor.openaev.binaries.version:${info.app.version:unknown}}}")
  private String agentBinaryVersion;

  private final ExecutorRepository executorRepository;
  private final EndpointService endpointService;
  private final FileService fileService;
  private final ExecutorService executorService;
  private final ServiceAccountPrivilegeService privilegeService;

  @Resource protected ObjectMapper mapper;

  @GetMapping({EXECUTOR_URI, TENANT_EXECUTOR_URI})
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.ASSET)
  @Operation(
      summary = "Retrieve executors",
      description = "Retrieve all executors and pending executors if includeNext is true")
  @Transactional(readOnly = true)
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

  @GetMapping({EXECUTOR_URI + "/{executorId}", TENANT_EXECUTOR_URI + "/{executorId}"})
  @Transactional
  @AccessControl(
      resourceId = "#executorId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.ASSET)
  public Executor getExecutor(@PathVariable String executorId) {
    try {
      return executorService.executor(executorId);
    } catch (ElementNotFoundException e) {
      log.warn(
          "Executor with id {} not found - This may be because the executor has never been started yet",
          executorId);
      throw new ResponseStatusException(
          org.springframework.http.HttpStatus.NOT_FOUND, "Executor not found");
    }
  }

  @GetMapping({
    EXECUTOR_URI + "/{executorId}/related-ids",
    TENANT_EXECUTOR_URI + "/{executorId}/related-ids"
  })
  @AccessControl(
      resourceId = "#executorId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.ASSET)
  @Operation(summary = "Retrieve executor related ids")
  @Transactional
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

  @PutMapping({EXECUTOR_URI + "/{executorId}", TENANT_EXECUTOR_URI + "/{executorId}"})
  @Transactional
  @AccessControl(
      resourceId = "#executorId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.ASSET)
  public Executor updateExecutor(
      @PathVariable String executorId, @Valid @RequestBody ExecutorUpdateInput input) {
    Executor executor =
        executorRepository
            .findByIdAndTenantId(executorId, TenantContext.getCurrentTenant())
            .orElseThrow(ElementNotFoundException::new);
    return updateExecutor(
        executor, executor.getType(), executor.getName(), executor.getPlatforms());
  }

  @DeleteMapping({EXECUTOR_URI + "/{executorId}", TENANT_EXECUTOR_URI + "/{executorId}"})
  @AccessControl(
      resourceId = "#executorId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.ASSET)
  @Operation(
      summary = "Delete an executor",
      description =
          "Removes a registered executor. Intended for stopped executors that no longer ping;"
              + " an active executor re-registers on its next heartbeat.")
  @Transactional(rollbackFor = Exception.class)
  public void deleteExecutor(@PathVariable String executorId) {
    executorService.remove(executorId);
  }

  @PostMapping(
      value = {EXECUTOR_URI, TENANT_EXECUTOR_URI},
      produces = {MediaType.APPLICATION_JSON_VALUE},
      consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.ASSET)
  @Transactional(rollbackFor = Exception.class)
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
      Executor executor =
          executorRepository
              .findByIdAndTenantId(input.getId(), TenantContext.getCurrentTenant())
              .orElse(null);
      if (executor == null) {
        Executor executorChecking =
            executorRepository
                .findByTypeAndTenantId(input.getType(), TenantContext.getCurrentTenant())
                .orElse(null);
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
  @Transactional
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved the executable."),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid platform or architecture specified."),
      })
  @GetMapping(
      value = {
        AGENT_URI + "/executable/openaev/{platform}/{architecture}",
        TENANT_AGENT_URI + "/executable/openaev/{platform}/{architecture}"
      },
      produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  @AccessControl(skipRBAC = true)
  public @ResponseBody ResponseEntity<InputStreamResource> getOpenAevAgentExecutable(
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
    String resolvedPlatform =
        AgentUtils.normaliseSupportedAgentPlatform(platform).name().toLowerCase();
    String resolvedArch = AgentUtils.normaliseSupportedAgentArch(architecture).name().toLowerCase();

    InputStream in = null;
    String resourcePath = "/openaev-agent/" + resolvedPlatform + "/" + resolvedArch + "/";
    String filename = "";

    if (agentBinaryOrigin.equals("local")) { // if we want the local binaries
      filename = "openaev-agent-" + version + (resolvedPlatform.equals("windows") ? ".exe" : "");
      in = getClass().getResourceAsStream("/agents" + resourcePath + filename);
    } else if (agentBinaryOrigin.equals(
        "repository")) { // if we want a specific version from artifactory
      filename =
          "openaev-agent-"
              + agentBinaryVersion
              + (resolvedPlatform.equals("windows") ? ".exe" : "");
      in = new BufferedInputStream(validateJFrogUri(resourcePath, filename).toURL().openStream());
    }
    if (in != null) {
      HttpHeaders headers = new HttpHeaders();
      headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename);
      // Stream the binary instead of buffering it fully in heap: thousands of concurrent agent
      // downloads with byte[] buffering caused GC churn / OOM risk
      return ResponseEntity.ok()
          .headers(headers)
          .contentType(MediaType.APPLICATION_OCTET_STREAM)
          .body(new InputStreamResource(in));
    }
    throw new UnsupportedOperationException(
        "Agent " + resolvedPlatform + " executable not supported");
  }

  // Public API
  @Operation(
      summary = "Retrieve OpenAEV Agent Package",
      description =
          "Downloads the OpenAEV agent package for the specified platform and architecture.")
  @Transactional
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
      value = {
        AGENT_URI + "/package/openaev/{platform}/{architecture}/{installationMode}",
        TENANT_AGENT_URI + "/package/openaev/{platform}/{architecture}/{installationMode}"
      },
      produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  @AccessControl(skipRBAC = true)
  public @ResponseBody ResponseEntity<InputStreamResource> getOpenAevAgentPackage(
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
    String resolvedInstallationMode = AgentUtils.getSupportedInstallationMode(installationMode);
    String resolvedPlatform =
        AgentUtils.normaliseSupportedAgentPlatform(platform).name().toLowerCase();
    String resolvedArch = AgentUtils.normaliseSupportedAgentArch(architecture).name().toLowerCase();

    if (resolvedPlatform.equals("windows")) {
      InputStream in = null;
      String resourcePath = "/openaev-agent/windows/" + resolvedArch + "/";

      String filename = "openaev-agent-installer-";
      if (!resolvedInstallationMode.equals(SERVICE)) {
        filename = filename.concat(installationMode).concat("-");
      }

      if (agentBinaryOrigin.equals("local")) { // if we want the local binaries
        filename = filename.concat(version).concat(".exe");
        in = getClass().getResourceAsStream("/agents" + resourcePath + filename);
      } else if (agentBinaryOrigin.equals(
          "repository")) { // if we want a specific version from artifactory
        filename = filename.concat(agentBinaryVersion).concat(".exe");
        in = new BufferedInputStream(validateJFrogUri(resourcePath, filename).toURL().openStream());
      }
      if (in == null) {
        throw new UnsupportedOperationException(
            "Agent version " + agentBinaryVersion + " not found");
      }
      HttpHeaders headers = new HttpHeaders();
      headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename);
      // Stream the package instead of buffering it fully in heap
      return ResponseEntity.ok()
          .headers(headers)
          .contentType(MediaType.APPLICATION_OCTET_STREAM)
          .body(new InputStreamResource(in));
    }
    // linux & macos - No package needed
    throw new UnsupportedOperationException("Agent " + resolvedPlatform + " package not supported");
  }

  // Public API
  @Operation(
      summary = "Retrieve OpenAEV Agent Installer Command",
      description =
          "Generates the installation command for the OpenAEV agent for the specified platform, installation mode and token.")
  @Transactional
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Successfully generated the install command."),
        @ApiResponse(responseCode = "400", description = "Invalid platform specified."),
        @ApiResponse(responseCode = "404", description = "Token not found."),
      })
  @GetMapping(
      value = {
        AGENT_URI + "/installer/openaev/{platform}/{installationMode}",
        TENANT_AGENT_URI + "/installer/openaev/{platform}/{installationMode}"
      })
  @AccessControl(skipRBAC = true)
  public @ResponseBody ResponseEntity<String> getOpenAevAgentInstaller(
      @Parameter(
              description =
                  "Target platform for the agent installation (e.g., windows, linux, mac). Case insensitive.",
              required = true)
          @PathVariable
          String platform,
      @Parameter(
              description = "Installation Mode: session, user or system service",
              required = true)
          @PathVariable
          String installationMode,
      @Parameter(description = "Installation directory") @RequestParam(required = false)
          String installationDir,
      @Parameter(description = "Service name") @RequestParam(required = false) String serviceName)
      throws IOException {
    String resolvedPlatform =
        AgentUtils.normaliseSupportedAgentPlatform(platform).name().toLowerCase();
    String resolvedInstallationMode = AgentUtils.getSupportedInstallationMode(installationMode);

    // FIND TOKEN BY TENANT
    String token =
        privilegeService.getTokenUserServiceAccountByTenant(TenantContext.getCurrentTenant());

    String installCommand =
        this.endpointService.generateInstallCommand(
            resolvedPlatform,
            token,
            resolvedInstallationMode,
            installationDir,
            serviceName,
            TenantContext.getCurrentTenant());
    return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(installCommand);
  }
}
