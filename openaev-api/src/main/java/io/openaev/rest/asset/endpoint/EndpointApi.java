package io.openaev.rest.asset.endpoint;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.helper.StreamHelper.iterableToSet;

import io.openaev.aop.LogExecutionTime;
import io.openaev.aop.RBAC;
import io.openaev.database.model.Action;
import io.openaev.database.model.Agent;
import io.openaev.database.model.AssetAgentJob;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.Tag;
import io.openaev.database.repository.AssetAgentJobRepository;
import io.openaev.database.repository.EndpointRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.database.specification.AssetAgentJobSpecification;
import io.openaev.database.specification.EndpointSpecification;
import io.openaev.rest.asset.endpoint.form.*;
import io.openaev.rest.asset.endpoint.output.EndpointTargetOutput;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.EndpointService;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.HttpReqRespUtils;
import io.openaev.utils.InputFilterOptions;
import io.openaev.utils.mapper.EndpointMapper;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@Slf4j
public class EndpointApi extends RestBehavior {

  public static final String ENDPOINT_URI = "/api/endpoints";

  private final EndpointService endpointService;
  private final EndpointRepository endpointRepository;
  private final AssetAgentJobRepository assetAgentJobRepository;
  private final TagRepository tagRepository;

  private final EndpointMapper endpointMapper;

  @PostMapping(ENDPOINT_URI + "/agentless")
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.ASSET)
  @Transactional(rollbackFor = Exception.class)
  public Endpoint createEndpoint(@Valid @RequestBody final EndpointInput input) {
    return this.endpointService.createEndpoint(input);
  }

  @PostMapping(ENDPOINT_URI + "/agentless/upsert")
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.ASSET)
  @Transactional(rollbackFor = Exception.class)
  public Endpoint upsertAgentLessEndpoint(@Valid @RequestBody final EndpointInput input) {
    Optional<Endpoint> endpoint = Optional.empty();
    if (input.getExternalReference() != null) {
      endpoint = this.endpointService.findEndpointByExternalReference(input.getExternalReference());
    }
    if (endpoint.isEmpty() && input.getIps() != null) {
      List<Endpoint> endpoints =
          this.endpointService.findEndpointByHostnameAndAtLeastOneIp(
              input.getHostname(), input.getIps());
      if (!endpoints.isEmpty()) {
        endpoint = Optional.of(endpoints.getFirst());
      }
    }
    if (endpoint.isEmpty() && input.getMacAddresses() != null) {
      List<Endpoint> endpoints =
          this.endpointService.findEndpointByHostnameAndAtLeastOneMacAddress(
              input.getHostname(), input.getMacAddresses());
      if (!endpoints.isEmpty()) {
        endpoint = Optional.of(endpoints.getFirst());
      }
    }
    if (endpoint.isPresent()) {
      Endpoint endpointToUpdate = endpoint.get();
      // Mandatory fields
      endpointToUpdate.setName(input.getName());
      Iterable<String> tags =
          Stream.concat(
                  endpointToUpdate.getTags().stream().map(Tag::getId).toList().stream(),
                  input.getTagIds().stream())
              .distinct()
              .toList();
      endpointToUpdate.setTags(iterableToSet(tagRepository.findAllById(tags)));
      endpointToUpdate.setArch(input.getArch());
      endpointToUpdate.setPlatform(input.getPlatform());
      // Optional fields
      if (input.getIps() != null) {
        endpointToUpdate.setIps(EndpointMapper.setIps(input.getIps()));
      }
      if (input.getHostname() != null) {
        endpointToUpdate.setHostname(input.getHostname());
      }
      if (input.getMacAddresses() != null) {
        endpointToUpdate.setMacAddresses(input.getMacAddresses());
      }
      return this.endpointService.updateEndpoint(endpointToUpdate);
    }
    return this.endpointService.createEndpoint(input);
  }

  @PostMapping(ENDPOINT_URI + "/register")
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.ASSET)
  @Transactional(rollbackFor = Exception.class)
  public Endpoint upsertEndpoint(@Valid @RequestBody final EndpointRegisterInput input)
      throws IOException {
    input.setSeenIp(HttpReqRespUtils.getClientIpAddressIfServletRequestExist());
    return this.endpointService.register(input);
  }

  @LogExecutionTime
  @PostMapping(ENDPOINT_URI + "/jobs")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.ASSET)
  @Transactional(rollbackFor = Exception.class)
  public List<AssetAgentJob> getEndpointJobs(@RequestBody final EndpointRegisterInput input) {
    return this.assetAgentJobRepository.findAll(
        AssetAgentJobSpecification.forEndpoint(
            input.getExternalReference(),
            input.isService()
                ? Agent.DEPLOYMENT_MODE.service.name()
                : Agent.DEPLOYMENT_MODE.session.name(),
            input.isElevated() ? Agent.PRIVILEGE.admin.name() : Agent.PRIVILEGE.standard.name(),
            input.getExecutedByUser()));
  }

  @Deprecated(since = "1.11.0")
  @LogExecutionTime
  @GetMapping(ENDPOINT_URI + "/jobs/{endpointExternalReference}")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.ASSET)
  @Transactional(rollbackFor = Exception.class)
  public List<AssetAgentJob> getEndpointJobs(
      @PathVariable @NotBlank final String endpointExternalReference) {
    return this.assetAgentJobRepository.findAll(
        AssetAgentJobSpecification.forEndpoint(endpointExternalReference));
  }

  @DeleteMapping(ENDPOINT_URI + "/jobs/{assetAgentJobId}")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.JOB)
  @Transactional(rollbackFor = Exception.class)
  public void cleanupAssetAgentJob(@PathVariable @NotBlank final String assetAgentJobId) {
    this.assetAgentJobRepository.deleteById(assetAgentJobId);
  }

  @Deprecated(since = "1.11.0")
  @PostMapping(ENDPOINT_URI + "/jobs/{assetAgentJobId}")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.JOB)
  @Transactional(rollbackFor = Exception.class)
  public void cleanupAssetAgentJobDepreacted(@PathVariable @NotBlank final String assetAgentJobId) {
    this.assetAgentJobRepository.deleteById(assetAgentJobId);
  }

  @LogExecutionTime
  @GetMapping(ENDPOINT_URI)
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.ASSET)
  public List<Endpoint> endpoints() {
    return this.endpointService.endpoints(
        EndpointSpecification.findEndpointsForInjectionOrAgentlessEndpoints());
  }

  @LogExecutionTime
  @GetMapping(ENDPOINT_URI + "/{endpointId}")
  @RBAC(
      resourceId = "#endpointId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.ASSET)
  public EndpointOverviewOutput endpoint(@PathVariable @NotBlank final String endpointId) {
    return endpointMapper.toEndpointOverviewOutput(this.endpointService.getEndpoint(endpointId));
  }

  @LogExecutionTime
  @PostMapping(ENDPOINT_URI + "/search")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.ASSET)
  public Page<EndpointOutput> endpoints(
      @RequestBody @Valid SearchPaginationInput searchPaginationInput) {
    Page<Endpoint> endpointPage = endpointService.searchEndpoints(searchPaginationInput);
    // Convert the Page of Endpoint to a Page of EndpointOutput
    List<EndpointOutput> endpointOutputs =
        endpointPage.getContent().stream().map(endpointMapper::toEndpointOutput).toList();
    return new PageImpl<>(
        endpointOutputs, endpointPage.getPageable(), endpointPage.getTotalElements());
  }

  @LogExecutionTime
  @PostMapping(ENDPOINT_URI + "/targets")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.ASSET)
  public Page<EndpointTargetOutput> targetEndpoints(
      @RequestBody @Valid SearchPaginationInput searchPaginationInput) {

    Page<Endpoint> endpointPage = endpointService.searchManagedEndpoints(searchPaginationInput);
    List<EndpointTargetOutput> endpointTargetOutputs =
        endpointPage.getContent().stream().map(endpointMapper::toEndpointTargetOutput).toList();
    return new PageImpl<>(
        endpointTargetOutputs, endpointPage.getPageable(), endpointPage.getTotalElements());
  }

  @LogExecutionTime
  @PostMapping(ENDPOINT_URI + "/find")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.ASSET)
  @Transactional(readOnly = true)
  public List<Endpoint> findEndpoints(@RequestBody @Valid @NotNull final List<String> endpointIds) {
    return this.endpointService.endpoints(endpointIds);
  }

  @PutMapping(ENDPOINT_URI + "/{endpointId}")
  @RBAC(
      resourceId = "#endpointId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.ASSET)
  @Transactional(rollbackFor = Exception.class)
  public EndpointOverviewOutput updateEndpoint(
      @PathVariable @NotBlank final String endpointId,
      @Valid @RequestBody final EndpointInput input) {
    return endpointMapper.toEndpointOverviewOutput(
        this.endpointService.updateEndpoint(endpointId, input));
  }

  @DeleteMapping(ENDPOINT_URI + "/{endpointId}")
  @RBAC(
      resourceId = "#endpointId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.ASSET)
  @Transactional(rollbackFor = Exception.class)
  public void deleteEndpoint(@PathVariable @NotBlank final String endpointId) {
    this.endpointService.deleteEndpoint(endpointId);
  }

  // -- OPTION --

  @GetMapping(ENDPOINT_URI + "/options")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.ASSET)
  public List<FilterUtilsJpa.Option> optionsByName(
      @RequestParam(required = false) final String searchText,
      @RequestParam(required = false) final String sourceId,
      @RequestParam(required = false) final String inputFilterOption) {
    List<FilterUtilsJpa.Option> options = List.of();
    InputFilterOptions injectFilterOptionEnum;
    try {
      injectFilterOptionEnum = InputFilterOptions.valueOf(inputFilterOption);
    } catch (Exception e) {
      if (StringUtils.isEmpty(inputFilterOption)) {
        log.warn("InputFilterOption is null, fall back to backwards compatible case");
        if (StringUtils.isNotEmpty(sourceId)) {
          injectFilterOptionEnum = InputFilterOptions.SIMULATION_OR_SCENARIO;
        } else {
          injectFilterOptionEnum = InputFilterOptions.ATOMIC_TESTING;
        }
      } else {
        throw new BadRequestException(
            String.format("Invalid input filter option %s", inputFilterOption));
      }
    }

    switch (injectFilterOptionEnum) {
      case ALL_INJECTS:
        {
          options =
              endpointRepository.findAllEndpointsForAtomicTestingsSimulationsAndScenarios().stream()
                  .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
                  .toList();
          break;
        }
      case SIMULATION_OR_SCENARIO:
        {
          if (StringUtils.isEmpty(sourceId)) {
            throw new BadRequestException("Missing simulation or scenario id");
          }
          // fall through intentional
        }
      case ATOMIC_TESTING:
        {
          options =
              endpointRepository
                  .findAllBySimulationOrScenarioIdAndName(
                      StringUtils.trimToNull(sourceId), StringUtils.trimToNull(searchText))
                  .stream()
                  .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
                  .toList();
          break;
        }
    }
    return options;
  }

  @LogExecutionTime
  @GetMapping(ENDPOINT_URI + "/findings/options")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.ASSET)
  public List<FilterUtilsJpa.Option> optionsByNameLinkedToFindings(
      @RequestParam(required = false) final String searchText,
      @RequestParam(required = false) final String sourceId) {
    return endpointService.getOptionsByNameLinkedToFindings(
        searchText, sourceId, PageRequest.of(0, 50));
  }

  @PostMapping(ENDPOINT_URI + "/options")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.ASSET)
  public List<FilterUtilsJpa.Option> optionsById(@RequestBody final List<String> ids) {
    return fromIterable(this.endpointRepository.findAllById(ids)).stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }
}
