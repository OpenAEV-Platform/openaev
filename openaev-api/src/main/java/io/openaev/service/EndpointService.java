package io.openaev.service;

import static io.openaev.database.model.Filters.FilterMode.and;
import static io.openaev.database.model.Filters.isEmptyFilterGroup;
import static io.openaev.database.specification.EndpointSpecification.*;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.helper.StreamHelper.iterableToSet;
import static io.openaev.integration.impl.executors.crowdstrike.CrowdStrikeExecutorIntegration.CROWDSTRIKE_EXECUTOR_TYPE;
import static io.openaev.integration.impl.executors.openaev.OpenAEVExecutorIntegration.OPENAEV_EXECUTOR_ID;
import static io.openaev.integration.impl.executors.sentinelone.SentinelOneExecutorIntegration.SENTINELONE_EXECUTOR_TYPE;
import static io.openaev.utils.ArchitectureFilterUtils.handleEndpointFilter;
import static io.openaev.utils.FilterUtilsJpa.computeFilterGroupJpa;
import static io.openaev.utils.SecurityUtils.validateJFrogUri;
import static io.openaev.utils.pagination.PaginationUtils.buildPageable;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;
import static java.time.Instant.now;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

import io.openaev.config.OpenAEVConfig;
import io.openaev.database.model.*;
import io.openaev.database.repository.AssetAgentJobRepository;
import io.openaev.database.repository.AssetGroupRepository;
import io.openaev.database.repository.EndpointRepository;
import io.openaev.database.repository.ExecutorRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.executors.model.AgentRegisterInput;
import io.openaev.rest.asset.endpoint.form.EndpointInput;
import io.openaev.rest.asset.endpoint.form.EndpointOutput;
import io.openaev.rest.asset.endpoint.form.EndpointRegisterInput;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.mapper.EndpointMapper;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.annotation.Resource;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class EndpointService {

  private static final String ASSET_GROUP_FILTER = "assetGroups";

  public static final int DELETE_TTL = 86400000; // 24 hours
  public static final String OPENAEV_AGENT_INSTALLER = "openaev-agent-installer";
  public static final String OPENAEV_AGENT_UPGRADE = "openaev-agent-upgrade";
  public static final String SERVICE = "service";
  public static final String SERVICE_USER = "service-user";
  public static final String SESSION_USER = "session-user";

  public static final String OPENAEV_INSTALL_DIR_WINDOWS_SERVICE =
      "C:\\Program Files (x86)\\Filigran\\OAEV Agent";
  public static final String OPENAEV_INSTALL_DIR_WINDOWS_SERVICE_USER = ".openaev";
  public static final String OPENAEV_INSTALL_DIR_WINDOWS_SESSION_USER = "$HOME\\.openaev";
  public static final String OPENAEV_INSTALL_DIR_UNIX_SERVICE = "/opt/openaev-agent";
  public static final String OPENAEV_INSTALL_DIR_UNIX_SERVICE_USER = ".local/openaev-agent-service";
  public static final String OPENAEV_INSTALL_DIR_UNIX_SESSION_USER = ".local/openaev-agent-session";

  public static final String OPENAEV_SERVICE_NAME_WINDOWS_SERVICE = "OAEVAgentService";
  public static final String OPENAEV_SERVICE_NAME_WINDOWS_SERVICE_USER = "OAEVAgent-Service";
  public static final String OPENAEV_SERVICE_NAME_WINDOWS_SESSION_USER = "OAEVAgent-Session";
  public static final String OPENAEV_SERVICE_NAME_UNIX_SERVICE = "openaev-agent";
  public static final String OPENAEV_SERVICE_NAME_UNIX_SERVICE_USER = "openaev-agent";
  public static final String OPENAEV_SERVICE_NAME_UNIX_SESSION_USER = "openaev-agent-session";

  @Resource private OpenAEVConfig openAEVConfig;

  private final EndpointMapper endpointMapper;

  @Value("${openbas.admin.token:${openaev.admin.token:#{null}}}")
  private String adminToken;

  @Value("${info.app.version:unknown}")
  String version;

  @Value("${executor.openaev.binaries.origin:local}")
  private String executorOpenaevBinariesOrigin;

  @Value("${executor.openaev.binaries.version:${info.app.version:unknown}}")
  private String executorOpenaevBinariesVersion;

  private final EndpointRepository endpointRepository;
  private final ExecutorRepository executorRepository;
  private final AssetGroupRepository assetGroupRepository;
  private final AssetAgentJobRepository assetAgentJobRepository;
  private final TagRepository tagRepository;
  private final AgentService agentService;
  private final AssetService assetService;

  // -- CRUD --
  public Endpoint createEndpoint(@NotNull final Endpoint endpoint) {
    return this.endpointRepository.save(endpoint);
  }

  public Endpoint createEndpoint(@NotNull final EndpointInput input) {
    Endpoint endpoint = new Endpoint();
    endpoint.setUpdateAttributes(input);
    endpoint.setIps(EndpointMapper.setIps(input.getIps()));
    endpoint.setMacAddresses(EndpointMapper.setMacAddresses(input.getMacAddresses()));
    endpoint.setTags(iterableToSet(this.tagRepository.findAllById(input.getTagIds())));
    endpoint.setEoL(input.isEol());
    return createEndpoint(endpoint);
  }

  public Endpoint endpoint(@NotBlank final String endpointId) {
    return this.endpointRepository
        .findById(endpointId)
        .orElseThrow(() -> new ElementNotFoundException("Endpoint not found"));
  }

  public List<Endpoint> findEndpointByHostnameAndAtLeastOneIp(
      @NotBlank final String hostname, @NotNull final String[] ips) {
    return this.endpointRepository.findByHostnameAndAtleastOneIp(hostname, ips);
  }

  public List<Endpoint> findEndpointByHostnameAndAtLeastOneMacAddress(
      @NotBlank final String hostname, @NotNull final String[] macAddresses) {
    return this.endpointRepository.findByHostnameAndAtleastOneMacAddress(hostname, macAddresses);
  }

  public Optional<Endpoint> findEndpointByExternalReference(
      @NotNull final String externalReference) {
    return this.endpointRepository.findByExternalReference(externalReference).stream().findFirst();
  }

  public Optional<Endpoint> findEndpointByAtLeastOneMacAddress(
      @NotNull final String[] macAddresses) {
    return this.endpointRepository.findByAtleastOneMacAddress(macAddresses).stream().findFirst();
  }

  public List<Endpoint> findEndpointsByMacAddresses(final String[] macAddresses) {
    return this.endpointRepository.findByAtleastOneMacAddress(macAddresses);
  }

  public List<Endpoint> endpoints() {
    return fromIterable(this.endpointRepository.findAll());
  }

  public List<Endpoint> endpoints(List<String> endpointIds) {
    return fromIterable(this.endpointRepository.findAll(fromIds(endpointIds)));
  }

  public List<Endpoint> endpoints(@NotNull final Specification<Endpoint> specification) {
    return fromIterable(this.endpointRepository.findAll(specification));
  }

  public Endpoint updateEndpoint(@NotNull final Endpoint endpoint) {
    endpoint.setUpdatedAt(now());
    return this.endpointRepository.save(endpoint);
  }

  public void deleteEndpoint(@NotBlank final String endpointId) {
    this.endpointRepository.deleteById(endpointId);
  }

  public Endpoint getEndpoint(@NotBlank final String endpointId) {
    return endpoint(endpointId);
  }

  public Page<Endpoint> searchEndpoints(SearchPaginationInput searchPaginationInput) {
    return buildPaginationJPA(
        (Specification<Endpoint> specification, Pageable pageable) ->
            this.endpointRepository.findAll(
                findEndpointsForInjectionOrAgentlessEndpoints().and(specification), pageable),
        handleEndpointFilter(searchPaginationInput),
        Endpoint.class);
  }

  private List<Specification<Endpoint>> getDynamicAssetGroupSpecifications(
      List<AssetGroup> assetGroups) {
    return assetGroups.stream()
        .filter(assetGroup -> !isEmptyFilterGroup(assetGroup.getDynamicFilter()))
        .map(
            assetGroup -> {
              Specification<Endpoint> specificationDynamic =
                  computeFilterGroupJpa(assetGroup.getDynamicFilter());
              return specificationDynamic;
            })
        .collect(toList());
  }

  private List<AssetGroup> getAssetGroupFromFilter(Filters.Filter assetGroupFilter) {
    return fromIterable(assetGroupRepository.findAllById(assetGroupFilter.getValues()));
  }

  private Specification<Endpoint> getStaticAssetGroupSpecification(
      SearchPaginationInput searchPaginationInput, Filters.Filter assetGroupFilter) {
    Filters.FilterGroup filterGroup = new Filters.FilterGroup();
    filterGroup.setMode(searchPaginationInput.getFilterGroup().getMode());
    filterGroup.setFilters(List.of(assetGroupFilter));
    return computeFilterGroupJpa(filterGroup);
  }

  private Specification<Endpoint> buildAdditionalEndpointSpecifications(
      SearchPaginationInput searchPaginationInput) {
    Optional<Filters.Filter> assetGroupFilter =
        ofNullable(searchPaginationInput.getFilterGroup())
            .flatMap(f -> f.findByKey(ASSET_GROUP_FILTER));

    if (assetGroupFilter.isEmpty()) {
      return findEndpointsForInjectionOrAgentlessEndpoints();
    }

    // Handle dynamic asset group filters
    List<AssetGroup> assetGroups = getAssetGroupFromFilter(assetGroupFilter.get());
    List<Specification<Endpoint>> assetGroupSpecifications =
        getDynamicAssetGroupSpecifications(assetGroups);

    // Handle static asset group filter
    assetGroupSpecifications.add(
        getStaticAssetGroupSpecification(searchPaginationInput, assetGroupFilter.get()));
    searchPaginationInput.getFilterGroup().removeByKey(ASSET_GROUP_FILTER);

    return Specification.anyOf(assetGroupSpecifications)
        .and(findEndpointsForInjectionOrAgentlessEndpoints());
  }

  public Page<Endpoint> searchManagedEndpoints(SearchPaginationInput searchPaginationInput) {
    Specification<Endpoint> finalSpec =
        buildAdditionalEndpointSpecifications(searchPaginationInput);
    Filters.FilterMode mode = searchPaginationInput.getFilterGroup().getMode();

    return buildPaginationJPA(
        (Specification<Endpoint> specification, Pageable pageable) ->
            this.endpointRepository.findAll(
                searchPaginationInput.getFilterGroup().getFilters().isEmpty()
                    ? finalSpec
                    : (and.equals(mode)
                        ? finalSpec.and(specification)
                        : finalSpec.or(specification)),
                pageable),
        handleEndpointFilter(searchPaginationInput),
        Endpoint.class);
  }

  public Page<Endpoint> searchManagedEndpointsByAssetGroup(
      String assetGroupId, SearchPaginationInput searchPaginationInput) {
    AssetGroup assetGroup =
        assetGroupRepository
            .findById(assetGroupId)
            .orElseThrow(() -> new IllegalArgumentException("Asset group not found"));

    Specification<Endpoint> specificationStatic =
        findEndpointsForAssetGroup(assetGroupId)
            .and(findEndpointsForInjectionOrAgentlessEndpoints());

    if (!isEmptyFilterGroup(assetGroup.getDynamicFilter())) {
      Specification<Endpoint> specificationDynamic =
          computeFilterGroupJpa(assetGroup.getDynamicFilter());
      Specification<Endpoint> specificationDynamicWithInjection =
          specificationDynamic.and(findEndpointsForInjectionOrAgentlessEndpoints());

      Page<Endpoint> dynamicResult =
          buildPaginationJPA(
              (Specification<Endpoint> specification, Pageable pageable) ->
                  this.endpointRepository.findAll(
                      specificationDynamicWithInjection.and(specification), pageable),
              handleEndpointFilter(searchPaginationInput),
              Endpoint.class);
      Page<Endpoint> staticResult =
          buildPaginationJPA(
              (Specification<Endpoint> specification, Pageable pageable) ->
                  this.endpointRepository.findAll(specificationStatic.and(specification), pageable),
              handleEndpointFilter(searchPaginationInput),
              Endpoint.class);
      List<Endpoint> mergedContent =
          Stream.concat(dynamicResult.getContent().stream(), staticResult.getContent().stream())
              .distinct()
              .limit(searchPaginationInput.getSize())
              .collect(toList());

      long total = dynamicResult.getTotalElements() + staticResult.getTotalElements();

      Pageable pageable = buildPageable(searchPaginationInput, Endpoint.class);
      return new PageImpl<>(mergedContent, pageable, total);
    } else {
      return buildPaginationJPA(
          (Specification<Endpoint> specification, Pageable pageable) ->
              this.endpointRepository.findAll(specificationStatic.and(specification), pageable),
          handleEndpointFilter(searchPaginationInput),
          Endpoint.class);
    }
  }

  public Endpoint updateEndpoint(
      @NotBlank final String endpointId, @NotNull final EndpointInput input) {
    Endpoint toUpdate = this.endpoint(endpointId);
    toUpdate.setUpdateAttributes(input);
    toUpdate.setEoL(input.isEol());
    toUpdate.setTags(iterableToSet(this.tagRepository.findAllById(input.getTagIds())));
    return updateEndpoint(toUpdate);
  }

  // -- INSTALLATION AGENT --

  /**
   * Get agents from SentinelOne, Crowdstrike API and register them into OpenAEV agents and
   * endpoints
   *
   * @param inputs from the API
   * @param existingAgents in the database
   * @return OpenAEV agents
   */
  public List<Agent> syncAgentsEndpoints(
      List<AgentRegisterInput> inputs, List<Agent> existingAgents) {
    List<Agent> agentsToSave = new ArrayList<>();
    List<Asset> endpointsToSave = new ArrayList<>();
    Endpoint endpointToSave;
    Agent agentToSave;
    // Update agents/endpoints with external reference
    Set<String> inputsExternalRefs =
        inputs.stream().map(AgentRegisterInput::getExternalReference).collect(Collectors.toSet());
    if (!inputsExternalRefs.isEmpty()) {
      Set<Agent> agentsToUpdate =
          existingAgents.stream()
              .filter(agent -> inputsExternalRefs.contains(agent.getExternalReference()))
              .collect(Collectors.toSet());
      Map<String, AgentRegisterInput> inputsByExternalReference =
          inputs.stream()
              .collect(
                  Collectors.toMap(AgentRegisterInput::getExternalReference, agent2 -> agent2));
      for (Agent agentToUpdate : agentsToUpdate) {
        final AgentRegisterInput inputToSave =
            inputsByExternalReference.get(agentToUpdate.getExternalReference());
        endpointToSave = (Endpoint) agentToUpdate.getAsset();
        setUpdatedEndpointAttributes(endpointToSave, inputToSave);
        agentToUpdate.setAsset(endpointToSave);
        agentToUpdate.setLastSeen(inputToSave.getLastSeen());
        // TODO: Making this function transactional is not helping to solve tags
        // addSourceTagToEndpoint(endpointToSave, inputToSave);
        endpointsToSave.add(endpointToSave);
        agentsToSave.add(agentToUpdate);
        inputs.removeIf(
            input -> input.getExternalReference().equals(inputToSave.getExternalReference()));
      }
    }
    // Update agents/endpoints with mac address
    String[] inputsMacAddresses =
        inputs.stream().map(AgentRegisterInput::getMacAddresses).toList().stream()
            .flatMap(Arrays::stream)
            .toArray(String[]::new);
    if (inputsMacAddresses.length > 0) {
      List<Endpoint> endpointsToUpdate = findEndpointsByMacAddresses(inputsMacAddresses);
      Optional<AgentRegisterInput> optionalInputToSave;
      for (Endpoint endpointToUpdate : endpointsToUpdate) {
        optionalInputToSave =
            inputs.stream()
                .filter(
                    input ->
                        Arrays.stream(endpointToUpdate.getMacAddresses())
                            .anyMatch(
                                macAddress ->
                                    Arrays.asList(input.getMacAddresses()).contains(macAddress)))
                .findFirst();
        if (optionalInputToSave.isPresent()) {
          // If no existing agent Crowdstrike/SentinelOne in this endpoint, add to it
          if (existingAgents.stream()
              .noneMatch(agent -> agent.getAsset().getId().equals(endpointToUpdate.getId()))) {
            final AgentRegisterInput inputToSave = optionalInputToSave.get();
            setUpdatedEndpointAttributes(endpointToUpdate, inputToSave);
            agentToSave = new Agent();
            setNewAgentAttributes(inputToSave, agentToSave);
            setUpdatedAgentAttributes(agentToSave, inputToSave, endpointToUpdate);
            // TODO: Making this function transactional is not helping to solve tags
            // addSourceTagToEndpoint(endpointToUpdate, inputToSave);
            endpointsToSave.add(endpointToUpdate);
            agentsToSave.add(agentToSave);
            inputs.removeIf(
                input -> Arrays.equals(input.getMacAddresses(), inputToSave.getMacAddresses()));
          }
        }
      }
    }
    // Create new agents/endpoints
    if (!inputs.isEmpty()) {
      for (AgentRegisterInput inputToUpdate : inputs) {
        endpointToSave = new Endpoint();
        endpointToSave.setUpdateAttributes(inputToUpdate);
        endpointToSave.setIps(inputToUpdate.getIps());
        endpointToSave.setSeenIp(inputToUpdate.getSeenIp());
        endpointToSave.setMacAddresses(inputToUpdate.getMacAddresses());
        // TODO: Making this function transactional is not helping to solve tags
        // addSourceTagToEndpoint(endpointToSave, inputToUpdate);
        endpointsToSave.add(endpointToSave);
        agentToSave = new Agent();
        setNewAgentAttributes(inputToUpdate, agentToSave);
        setUpdatedAgentAttributes(agentToSave, inputToUpdate, endpointToSave);
        agentsToSave.add(agentToSave);
      }
    }
    // Save all in database
    assetService.saveAllAssets(endpointsToSave);
    return agentService.saveAllAgents(agentsToSave);
  }

  @Transactional
  public Endpoint register(final EndpointRegisterInput input) throws IOException {
    AgentRegisterInput agentInput = toAgentEndpoint(input);
    Agent agent;
    // Check if agents exist (because we can find X openaev agent on an endpoint)
    List<Agent> existingAgents =
        agentService.findByExternalReference(agentInput.getExternalReference());
    if (!existingAgents.isEmpty()) {
      // Check if this specific agent exist
      Agent.DEPLOYMENT_MODE deploymentMode =
          agentInput.isService() ? Agent.DEPLOYMENT_MODE.service : Agent.DEPLOYMENT_MODE.session;
      Agent.PRIVILEGE privilege =
          agentInput.isElevated() ? Agent.PRIVILEGE.admin : Agent.PRIVILEGE.standard;
      Optional<Agent> existingAgent =
          existingAgents.stream()
              .filter(
                  ag ->
                      ag.getExecutedByUser().equals(agentInput.getExecutedByUser())
                          && ag.getDeploymentMode().equals(deploymentMode)
                          && ag.getPrivilege().equals(privilege))
              .findFirst();
      if (existingAgent.isPresent()) {
        agent = updateExistingAgent(existingAgent.get(), agentInput);
      } else {
        agent =
            updateExistingEndpointAndCreateAgent(
                (Endpoint) existingAgents.getFirst().getAsset(), agentInput);
      }
    } else {
      // Check if endpoint exists
      Optional<Endpoint> existingEndpoint =
          findEndpointByAtLeastOneMacAddress(agentInput.getMacAddresses());
      if (existingEndpoint.isPresent()) {
        agent = updateExistingEndpointAndManageAgent(existingEndpoint.get(), agentInput);
      } else {
        agent = createNewEndpointAndAgent(agentInput);
      }
    }
    // If agent is not temporary and not the same version as the platform => Create an upgrade task
    // for the agent
    Endpoint endpoint = (Endpoint) agent.getAsset();
    if (agent.getParent() == null && !agent.getVersion().equals(version)) {
      AssetAgentJob assetAgentJob = new AssetAgentJob();
      assetAgentJob.setCommand(
          generateUpgradeCommand(
              endpoint.getPlatform().name(),
              input.getInstallationMode(),
              input.getInstallationDirectory(),
              input.getServiceName()));
      assetAgentJob.setAgent(agent);
      assetAgentJobRepository.save(assetAgentJob);
    }
    return endpoint;
  }

  private void addSourceTagToEndpoint(Endpoint endpoint, AgentRegisterInput input) {
    Set<Tag> existingTags =
        endpoint.getTags() != null ? new HashSet<>(endpoint.getTags()) : new HashSet<>();
    existingTags.removeIf(t -> t.getName() != null && t.getName().startsWith("source:"));
    String tagName = "source:" + input.getExecutor().getName().toLowerCase();
    Optional<Tag> tag = tagRepository.findByName(tagName);
    if (tag.isEmpty()) {
      Tag newTag = new Tag();
      newTag.setColor(input.getExecutor().getBackgroundColor());
      newTag.setName(tagName);
      tagRepository.save(newTag);
      existingTags.add(newTag);
    } else {
      existingTags.add(tag.get());
    }
    endpoint.setTags(existingTags);
  }

  private Agent updateExistingEndpointAndManageAgent(Endpoint endpoint, AgentRegisterInput input) {
    setUpdatedEndpointAttributes(endpoint, input);
    addSourceTagToEndpoint(endpoint, input);
    updateEndpoint(endpoint);
    return createOrUpdateAgent(endpoint, input);
  }

  private Agent updateExistingAgent(Agent agent, AgentRegisterInput input) {
    Endpoint endpoint = (Endpoint) agent.getAsset();
    setUpdatedEndpointAttributes(endpoint, input);
    addSourceTagToEndpoint(endpoint, input);
    updateEndpoint(endpoint);
    setUpdatedAgentAttributes(agent, input, endpoint);
    return agentService.createOrUpdateAgent(agent);
  }

  private Agent updateExistingEndpointAndCreateAgent(Endpoint endpoint, AgentRegisterInput input) {
    setUpdatedEndpointAttributes(endpoint, input);
    addSourceTagToEndpoint(endpoint, input);
    updateEndpoint(endpoint);
    Agent agent = new Agent();
    setNewAgentAttributes(input, agent);
    setUpdatedAgentAttributes(agent, input, endpoint);
    return agentService.createOrUpdateAgent(agent);
  }

  private Agent createOrUpdateAgent(Endpoint endpoint, AgentRegisterInput input) {
    Agent.DEPLOYMENT_MODE deploymentMode =
        input.isService() ? Agent.DEPLOYMENT_MODE.service : Agent.DEPLOYMENT_MODE.session;
    Agent.PRIVILEGE privilege =
        input.isElevated() ? Agent.PRIVILEGE.admin : Agent.PRIVILEGE.standard;
    Optional<Agent> existingAgent =
        agentService.getAgentForAnAsset(
            endpoint.getId(),
            input.getExecutedByUser(),
            deploymentMode,
            privilege,
            input.getExecutor().getType());
    Agent agent;
    if (existingAgent.isPresent()) {
      agent = existingAgent.get();
    } else {
      agent = new Agent();
      setNewAgentAttributes(input, agent);
    }
    setUpdatedAgentAttributes(agent, input, endpoint);
    return agentService.createOrUpdateAgent(agent);
  }

  private void setUpdatedEndpointAttributes(Endpoint endpoint, AgentRegisterInput input) {
    // Hostname and arch not updated by Crowdstrike because Crowdstrike hostname is 15 length max
    // and arch is hard coded
    if (!CROWDSTRIKE_EXECUTOR_TYPE.equals(input.getExecutor().getType())) {
      endpoint.setHostname(input.getHostname());
      endpoint.setArch(input.getArch());
    }
    endpoint.setIps(EndpointMapper.mergeAddressArrays(endpoint.getIps(), input.getIps()));
    endpoint.setSeenIp(input.getSeenIp());
    endpoint.setMacAddresses(
        EndpointMapper.mergeAddressArrays(endpoint.getMacAddresses(), input.getMacAddresses()));
  }

  private void setUpdatedAgentAttributes(Agent agent, AgentRegisterInput input, Endpoint endpoint) {
    agent.setAsset(endpoint);
    agent.setLastSeen(input.getLastSeen());
    agent.setExternalReference(input.getExternalReference());
    // For OpenAEV agent
    agent.setVersion(input.getAgentVersion());
  }

  private Agent createNewEndpointAndAgent(AgentRegisterInput input) {
    Endpoint endpoint = new Endpoint();
    endpoint.setUpdateAttributes(input);
    endpoint.setIps(input.getIps());
    endpoint.setSeenIp(input.getSeenIp());
    endpoint.setMacAddresses(input.getMacAddresses());
    addSourceTagToEndpoint(endpoint, input);
    createEndpoint(endpoint);
    Agent agent = new Agent();
    setUpdatedAgentAttributes(agent, input, endpoint);
    setNewAgentAttributes(input, agent);
    return agentService.createOrUpdateAgent(agent);
  }

  private void setNewAgentAttributes(AgentRegisterInput input, Agent agent) {
    if (CROWDSTRIKE_EXECUTOR_TYPE.equals(input.getExecutor().getType())
        || SENTINELONE_EXECUTOR_TYPE.equals(input.getExecutor().getType())) {
      agent.setId(input.getExternalReference());
    }
    agent.setPrivilege(input.isElevated() ? Agent.PRIVILEGE.admin : Agent.PRIVILEGE.standard);
    agent.setDeploymentMode(
        input.isService() ? Agent.DEPLOYMENT_MODE.service : Agent.DEPLOYMENT_MODE.session);
    agent.setExecutedByUser(input.getExecutedByUser());
    agent.setExecutor(input.getExecutor());
  }

  private AgentRegisterInput toAgentEndpoint(EndpointRegisterInput input) {
    AgentRegisterInput agentInput = new AgentRegisterInput();
    agentInput.setExecutor(executorRepository.findById(OPENAEV_EXECUTOR_ID).orElse(null));
    agentInput.setLastSeen(Instant.now());
    agentInput.setExternalReference(input.getExternalReference());
    agentInput.setIps(input.getIps());
    agentInput.setSeenIp(input.getSeenIp());
    agentInput.setMacAddresses(input.getMacAddresses());
    agentInput.setHostname(input.getHostname());
    agentInput.setAgentVersion(input.getAgentVersion());
    agentInput.setName(input.getName());
    agentInput.setPlatform(input.getPlatform());
    agentInput.setArch(input.getArch());
    agentInput.setService(input.isService());
    agentInput.setElevated(input.isElevated());
    agentInput.setExecutedByUser(input.getExecutedByUser());
    agentInput.setInstallationMode(input.getInstallationMode());
    agentInput.setInstallationDirectory(input.getInstallationDirectory());
    agentInput.setServiceName(input.getServiceName());
    return agentInput;
  }

  public String getFileOrDownloadFromJfrog(
      String platform,
      String file,
      String adminToken,
      String installationDir,
      String serviceNameOrPrefix)
      throws IOException {
    String extension =
        switch (platform.toLowerCase()) {
          case "windows" -> "ps1";
          case "linux", "macos" -> "sh";
          default -> throw new UnsupportedOperationException("");
        };
    InputStream in = null;
    String filename;
    String resourcePath = "/openaev-agent/" + platform.toLowerCase() + "/";

    if (executorOpenaevBinariesOrigin.equals("local")) { // if we want the local binaries
      filename = file + "-" + version + "." + extension;
      in = getClass().getResourceAsStream("/agents" + resourcePath + filename);
    } else if (executorOpenaevBinariesOrigin.equals(
        "repository")) { // if we want a specific version from artifactory
      filename = file + "-" + executorOpenaevBinariesVersion + "." + extension;
      in = new BufferedInputStream(validateJFrogUri(resourcePath, filename).toURL().openStream());
    }
    if (in == null) {
      throw new UnsupportedOperationException(
          "Agent installer version " + executorOpenaevBinariesVersion + " not found");
    }

    if (installationDir == null) {
      installationDir = "";
    }

    return IOUtils.toString(in, StandardCharsets.UTF_8)
        .replace("${OPENAEV_URL}", openAEVConfig.getBaseUrlForAgent())
        .replace("${OPENAEV_TOKEN}", adminToken)
        .replace(
            "${OPENAEV_UNSECURED_CERTIFICATE}",
            String.valueOf(openAEVConfig.isUnsecuredCertificate()))
        .replace("${OPENAEV_WITH_PROXY}", String.valueOf(openAEVConfig.isWithProxy()))
        .replace("${OPENAEV_SERVICE_NAME}", serviceNameOrPrefix)
        .replace("${OPENAEV_INSTALL_DIR}", installationDir);
  }

  public String generateServiceNameOrPrefix(
      String platform, String installationMode, String serviceNameOrPrefix) {
    if (serviceNameOrPrefix != null && !serviceNameOrPrefix.equals("")) {
      return serviceNameOrPrefix;
    }
    if (platform.equalsIgnoreCase(Endpoint.PLATFORM_TYPE.Windows.name())) {
      if (installationMode != null && installationMode.equals(SERVICE)) {
        return OPENAEV_SERVICE_NAME_WINDOWS_SERVICE;
      }
      if (installationMode != null && installationMode.equals(SERVICE_USER)) {
        return OPENAEV_SERVICE_NAME_WINDOWS_SERVICE_USER;
      }
      if (installationMode != null && installationMode.equals(SESSION_USER)) {
        return OPENAEV_SERVICE_NAME_WINDOWS_SESSION_USER;
      }
      return OPENAEV_SERVICE_NAME_WINDOWS_SERVICE;
    } else {
      if (installationMode != null && installationMode.equals(SERVICE)) {
        return OPENAEV_SERVICE_NAME_UNIX_SERVICE;
      }
      if (installationMode != null && installationMode.equals(SERVICE_USER)) {
        return OPENAEV_SERVICE_NAME_UNIX_SERVICE_USER;
      }
      if (installationMode != null && installationMode.equals(SESSION_USER)) {
        return OPENAEV_SERVICE_NAME_UNIX_SESSION_USER;
      }
      return OPENAEV_SERVICE_NAME_UNIX_SERVICE;
    }
  }

  public String generateInstallationDir(
      String platform, String installationMode, String installationDir) {
    if (installationDir != null && !installationDir.equals("")) {
      return installationDir;
    }
    if (platform.equalsIgnoreCase(Endpoint.PLATFORM_TYPE.Windows.name())) {
      if (installationMode != null && installationMode.equals(SERVICE)) {
        return OPENAEV_INSTALL_DIR_WINDOWS_SERVICE;
      }
      if (installationMode != null && installationMode.equals(SERVICE_USER)) {
        return OPENAEV_INSTALL_DIR_WINDOWS_SERVICE_USER;
      }
      if (installationMode != null && installationMode.equals(SESSION_USER)) {
        return OPENAEV_INSTALL_DIR_WINDOWS_SESSION_USER;
      }
      return OPENAEV_INSTALL_DIR_WINDOWS_SERVICE;
    } else {
      if (installationMode != null && installationMode.equals(SERVICE)) {
        return OPENAEV_INSTALL_DIR_UNIX_SERVICE;
      }
      if (installationMode != null && installationMode.equals(SERVICE_USER)) {
        return OPENAEV_INSTALL_DIR_UNIX_SERVICE_USER;
      }
      if (installationMode != null && installationMode.equals(SESSION_USER)) {
        return OPENAEV_INSTALL_DIR_UNIX_SESSION_USER;
      }
      return OPENAEV_INSTALL_DIR_UNIX_SERVICE;
    }
  }

  public String generateInstallCommand(
      String platform,
      String token,
      String installationMode,
      String installationDir,
      String serviceNameOrPrefix)
      throws IOException {
    if (token == null || token.isEmpty()) {
      throw new IllegalArgumentException("Token must not be null or empty.");
    }
    String installerName = OPENAEV_AGENT_INSTALLER;
    if (installationMode != null && !installationMode.equals(SERVICE)) {
      installerName = installerName.concat("-").concat(installationMode);
    }
    installationDir = generateInstallationDir(platform, installationMode, installationDir);
    serviceNameOrPrefix =
        generateServiceNameOrPrefix(platform, installationMode, serviceNameOrPrefix);
    return getFileOrDownloadFromJfrog(
        platform, installerName, token, installationDir, serviceNameOrPrefix);
  }

  public String generateUpgradeCommand(
      String platform, String installationMode, String installationDir, String serviceNameOrPrefix)
      throws IOException {
    String upgradeName = OPENAEV_AGENT_UPGRADE;
    if (installationMode != null && !installationMode.equals(SERVICE)) {
      upgradeName = upgradeName.concat("-").concat(installationMode);
    }
    installationDir = generateInstallationDir(platform, installationMode, installationDir);
    serviceNameOrPrefix =
        generateServiceNameOrPrefix(platform, installationMode, serviceNameOrPrefix);
    return getFileOrDownloadFromJfrog(
        platform, upgradeName, adminToken, installationDir, serviceNameOrPrefix);
  }

  public List<Endpoint> endpointsForScenario(String scenarioId) {
    return this.endpointRepository.findDistinctByInjectsScenarioId(scenarioId);
  }

  public List<EndpointOutput> endpointsByIdsForScenario(
      String scenarioId, List<String> endpointIds) {
    return this.endpointRepository
        .findDistinctByInjectsScenarioIdAndIdIn(scenarioId, endpointIds)
        .stream()
        .map(endpointMapper::toEndpointOutput)
        .toList();
  }

  public List<Endpoint> endpointsForSimulation(String simulationId) {
    return this.endpointRepository.findDistinctByInjectsExerciseId(simulationId);
  }

  public List<EndpointOutput> endpointsByIdsForSimulation(
      String simulationId, List<String> endpointIds) {
    return this.endpointRepository
        .findDistinctByInjectsExerciseIdAndIdIn(simulationId, endpointIds)
        .stream()
        .map(endpointMapper::toEndpointOutput)
        .toList();
  }

  // -- OPTIONS --
  public List<FilterUtilsJpa.Option> getOptionsByNameLinkedToFindings(
      String searchText, String sourceId, Pageable pageable) {
    String trimmedSearchText = StringUtils.trimToNull(searchText);
    String trimmedSourceId = StringUtils.trimToNull(sourceId);

    List<Object[]> results;

    if (trimmedSourceId == null) {
      results = endpointRepository.findAllByNameLinkedToFindings(trimmedSearchText, pageable);
    } else {
      results =
          endpointRepository.findAllByNameLinkedToFindingsWithContext(
              trimmedSourceId, trimmedSearchText, pageable);
    }

    return results.stream()
        .map(i -> new FilterUtilsJpa.Option((String) i[0], (String) i[1]))
        .toList();
  }
}
