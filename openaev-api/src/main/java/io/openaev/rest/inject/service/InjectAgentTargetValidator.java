package io.openaev.rest.inject.service;

import static io.openaev.database.model.Filters.isEmptyFilterGroup;

import io.openaev.database.model.Agent;
import io.openaev.database.model.Inject;
import io.openaev.database.repository.AgentRepository;
import io.openaev.rest.exception.ForbiddenException;
import io.openaev.service.AssetGroupService;
import io.openaev.utils.AgentUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Object-level authorization gate shared by the implant endpoints (executable payload retrieval and
 * execution callback). The service-account bearer token carrying AGENT_RUNTIME_ACCESS is shared
 * across every agent, so the {@code @AccessControl} capability check alone does not prove the
 * caller is entitled to act on a specific inject for a specific agent.
 */
@RequiredArgsConstructor
@Service
@Slf4j
public class InjectAgentTargetValidator {

  static final String AGENT_ACCESS_DENIED = "Agent is not allowed to act on this inject";

  private final InjectService injectService;
  private final AgentRepository agentRepository;
  private final AssetGroupService assetGroupService;

  /**
   * Resolve the inject, rejecting the request (403) unless the given primary agent's asset is
   * targeted by the inject, either directly or through one of its asset groups (static or dynamic
   * membership).
   *
   * <p>Every failure (unknown inject, unknown or non-primary agent, agent without asset, agent not
   * targeted) maps to the same 403 and message, so the endpoints do not reveal whether an inject or
   * agent id exists. The precise reason is only logged server-side.
   *
   * @param injectId the inject the agent is acting on
   * @param agentId the agent reported by the implant
   * @return the resolved inject
   * @throws ForbiddenException if the agent is not a target of the inject
   */
  public Inject resolveInjectTargetingAgent(String injectId, String agentId) {
    Inject inject = injectService.findInjectOrNull(injectId);
    Agent agent =
        agentId == null
            ? null
            : agentRepository.findById(agentId).filter(AgentUtils::isPrimaryAgent).orElse(null);
    if (inject == null
        || agent == null
        || agent.getAsset() == null
        || !isInjectTarget(inject, agent)) {
      log.warn(
          "Agent access to inject denied: inject {} (found: {}), agent {} (primary agent with asset found: {})",
          injectId,
          inject != null,
          agentId,
          agent != null && agent.getAsset() != null);
      throw new ForbiddenException(AGENT_ACCESS_DENIED);
    }
    return inject;
  }

  private boolean isInjectTarget(Inject inject, Agent agent) {
    // Static membership is checked in a single query so the (potentially large) asset group member
    // lists are never materialized; dynamic membership costs one id-constrained query per group.
    return agentRepository.isAgentAssetStaticallyTargetedByInject(agent.getId(), inject.getId())
        || inject.getAssetGroups().stream()
            .filter(group -> !isEmptyFilterGroup(group.getDynamicFilter()))
            .anyMatch(group -> assetGroupService.isAssetInDynamicGroup(agent.getAsset(), group));
  }
}
