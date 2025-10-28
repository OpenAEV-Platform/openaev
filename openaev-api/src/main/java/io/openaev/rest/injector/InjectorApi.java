package io.openaev.rest.injector;

import static io.openaev.database.specification.InjectorSpecification.byName;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.service.EndpointService.JFROG_BASE;
import static io.openaev.utils.AgentUtils.AVAILABLE_ARCHITECTURES;
import static io.openaev.utils.AgentUtils.AVAILABLE_PLATFORMS;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.aop.RBAC;
import io.openaev.database.model.*;
import io.openaev.database.repository.*;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.inject.service.InjectStatusService;
import io.openaev.rest.injector.form.InjectorCreateInput;
import io.openaev.rest.injector.form.InjectorUpdateInput;
import io.openaev.rest.injector.response.InjectorRegistration;
import io.openaev.service.InjectorService;
import io.openaev.utils.FilterUtilsJpa;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequiredArgsConstructor
public class InjectorApi extends RestBehavior {

  public static final String INJECT0R_URI = "/api/injectors";

  @Value("${info.app.version:unknown}")
  String version;

  @Value("${executor.openaev.binaries.origin:local}")
  private String executorOpenaevBinariesOrigin;

  @Value("${executor.openaev.binaries.version:${info.app.version:unknown}}")
  private String executorOpenaevBinariesVersion;

  private final InjectorRepository injectorRepository;
  private final InjectorContractRepository injectorContractRepository;
  private final InjectStatusService injectStatusService;
  private final InjectorService injectorService;

  @GetMapping("/api/injectors")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.INJECTOR)
  public Iterable<Injector> injectors() {
    return injectorRepository.findAll();
  }

  @GetMapping("/api/injectors/{injectorId}/injector_contracts")
  @RBAC(
      resourceId = "#injectorId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.INJECTOR)
  public Collection<JsonNode> injectorInjectTypes(@PathVariable String injectorId) {
    Injector injector =
        injectorRepository.findById(injectorId).orElseThrow(ElementNotFoundException::new);
    return fromIterable(injectorContractRepository.findInjectorContractsByInjector(injector))
        .stream()
        .map(
            contract -> {
              try {
                return mapper.readTree(contract.getContent());
              } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
              }
            })
        .toList();
  }

  @PutMapping("/api/injectors/{injectorId}")
  @RBAC(
      resourceId = "#injectorId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECTOR)
  public Injector updateInjector(
      @PathVariable String injectorId, @Valid @RequestBody InjectorUpdateInput input) {
    Injector injector =
        injectorRepository.findById(injectorId).orElseThrow(ElementNotFoundException::new);
    return injectorService.updateInjector(
        injector,
        injector.getType(),
        input.getName(),
        input.getContracts(),
        input.getCustomContracts(),
        input.getCategory(),
        input.getExecutorCommands(),
        input.getExecutorClearCommands(),
        input.getPayloads());
  }

  @GetMapping("/api/injectors/{injectorId}")
  @RBAC(
      resourceId = "#injectorId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.INJECTOR)
  public Injector injector(@PathVariable String injectorId) {
    return injectorRepository.findById(injectorId).orElseThrow(ElementNotFoundException::new);
  }

  @PostMapping(
      value = "/api/injectors",
      produces = {MediaType.APPLICATION_JSON_VALUE},
      consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.INJECTOR)
  @Transactional(rollbackOn = Exception.class)
  public InjectorRegistration registerInjector(
      @Valid @RequestPart("input") InjectorCreateInput input,
      @RequestPart("icon") Optional<MultipartFile> file) {
    return injectorService.registerInjector(input, file);
  }

  @GetMapping(
      value = "/api/implant/caldera/{platform}/{arch}",
      produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  @RBAC(skipRBAC = true)
  public @ResponseBody byte[] getCalderaImplant(
      @PathVariable String platform, @PathVariable String arch) throws IOException {
    return getCalderaFile(platform, arch, null);
  }

  @GetMapping(
      value = "/api/implant/caldera/{platform}/{arch}/{extension}",
      produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  @RBAC(skipRBAC = true)
  public @ResponseBody byte[] getCalderaScript(
      @PathVariable String platform, @PathVariable String arch, @PathVariable String extension)
      throws IOException {
    return getCalderaFile(platform, arch, extension);
  }

  private byte[] getCalderaFile(String platform, String arch, String extension) throws IOException {
    if (!AVAILABLE_PLATFORMS.contains(platform)) {
      throw new IllegalArgumentException("Platform invalid : " + platform);
    }
    if (!AVAILABLE_ARCHITECTURES.contains(arch)) {
      throw new IllegalArgumentException("Architecture invalid : " + arch);
    }

    String resource =
        "/implants/caldera/" + platform + "/" + arch + "/oaev-implant-caldera-" + platform;
    if (extension != null) {
      resource += "." + extension;
    }
    InputStream in = getClass().getResourceAsStream(resource);
    if (in != null) {
      return IOUtils.toByteArray(in);
    }
    return null;
  }

  // Public API
  @GetMapping(
      value = "/api/implant/openaev/{platform}/{architecture}",
      produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  @RBAC(skipRBAC = true)
  public @ResponseBody ResponseEntity<byte[]> getOpenAevImplant(
      @PathVariable String platform,
      @PathVariable String architecture,
      @RequestParam(required = false) final String injectId,
      @RequestParam(required = false) final String agentId)
      throws IOException {
    if (!AVAILABLE_PLATFORMS.contains(platform)) {
      this.injectStatusService.setImplantErrorTrace(
          injectId, agentId, "Unable to download the implant. Platform invalid: " + platform);
    }
    if (!AVAILABLE_ARCHITECTURES.contains(architecture)) {
      this.injectStatusService.setImplantErrorTrace(
          injectId,
          agentId,
          "Unable to download the implant. Architecture invalid: " + architecture);
    }

    InputStream in = null;
    String filename = "";
    String resourcePath = "/openaev-implant/" + platform + "/" + architecture + "/";

    if (executorOpenaevBinariesOrigin.equals("local")) { // if we want the local binaries
      filename = "openaev-implant-" + version + (platform.equals("windows") ? ".exe" : "");
      in = getClass().getResourceAsStream("/implants" + resourcePath + filename);
    } else if (executorOpenaevBinariesOrigin.equals(
        "repository")) { // if we want a specific version from artifactory
      filename =
          "openaev-implant-"
              + executorOpenaevBinariesVersion
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
    throw new UnsupportedOperationException("Implant " + platform + " executable not supported");
  }

  // -- OPTION --

  @GetMapping(INJECT0R_URI + "/options")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.INJECTOR)
  public List<FilterUtilsJpa.Option> optionsByName(
      @RequestParam(required = false) final String searchText,
      @RequestParam(required = false) final String sourceId) {
    return fromIterable(
            this.injectorRepository.findAll(
                byName(searchText), Sort.by(Sort.Direction.ASC, "name")))
        .stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }

  @PostMapping(INJECT0R_URI + "/options")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.INJECTOR)
  public List<FilterUtilsJpa.Option> optionsById(
      @RequestBody final List<String> ids, @RequestParam(required = false) final String sourceId) {
    return fromIterable(this.injectorRepository.findAllById(ids)).stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }
}
