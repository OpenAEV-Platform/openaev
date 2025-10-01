package io.openaev.service.targets.search;

import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.database.model.*;
import io.openaev.database.repository.AgentRepository;
import io.openaev.service.targets.search.specifications.SearchSpecificationUtils;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AgentTargetSearchAdaptor extends SearchAdaptorBase {

  private final AgentRepository agentRepository;
  private final SearchSpecificationUtils<Agent> specificationUtils;
  private final HelperTargetSearchAdaptor helperTargetSearchAdaptor;

  private final List<String> joinPath = List.of("assets", "agents");

  public AgentTargetSearchAdaptor(
      AgentRepository agentRepository,
      SearchSpecificationUtils<Agent> specificationUtils,
      HelperTargetSearchAdaptor helperTargetSearchAdaptor) {
    this.agentRepository = agentRepository;
    this.helperTargetSearchAdaptor = helperTargetSearchAdaptor;
    this.specificationUtils = specificationUtils;

    // field name translations
    this.fieldTranslations.put("target_name", "agent_executed_by_user");
    this.fieldTranslations.put("target_endpoint", "agent_asset");
  }

  @Override
  public Page<InjectTarget> search(SearchPaginationInput input, Inject scopedInject) {

    Specification<Agent> memberOfAssetGroupSpec =
        specificationUtils.compileSpecificationForAssetGroupMembership(
            scopedInject, input, joinPath);

    Specification<Agent> memberOfAnyTargetGroupSpec =
        specificationUtils.compileSpecificationForAssetGroupMembership(
            scopedInject,
            SearchPaginationInput.builder().filterGroup(new Filters.FilterGroup()).build(),
            joinPath);

    Specification<Agent> tagsSpec = specificationUtils.compileSpecificationForTags(input, joinPath);

    SearchPaginationInput translatedInput = this.translate(input, scopedInject);

    Page<Agent> eps =
        buildPaginationJPA(
            (Specification<Agent> specification, Pageable pageable) -> {
              if (Filters.FilterMode.and.equals(input.getFilterGroup().getMode())) {
                Specification<Agent> finalSpec = memberOfAssetGroupSpec.and(specification);
                if (tagsSpec != null) {
                  finalSpec = tagsSpec.and(finalSpec);
                }
                return this.agentRepository.findAll(finalSpec, pageable);
              }
              Specification<Agent> finalSpec =
                  memberOfAssetGroupSpec.or(specification.and(memberOfAnyTargetGroupSpec));
              if (tagsSpec != null) {
                finalSpec = tagsSpec.or(finalSpec);
              }
              return this.agentRepository.findAll(finalSpec, pageable);
            },
            translatedInput,
            Agent.class);

    return new PageImpl<>(
        eps.getContent().stream()
            .map(endpoint -> convertFromAgent(endpoint, scopedInject))
            .toList(),
        eps.getPageable(),
        eps.getTotalElements());
  }

  @Override
  public List<FilterUtilsJpa.Option> getOptionsForInject(Inject scopedInject, String textSearch) {
    log.info(
        "AgentTargetSearchAdaptor.getOptionsForInject: this method is stubbed, as there are no current filters on agent options.");
    return List.of();
  }

  @Override
  public List<FilterUtilsJpa.Option> getOptionsByIds(List<String> ids) {
    log.info(
        "AgentTargetSearchAdaptor.getOptionsByIds: this method is stubbed, as there are no current filters on agent options.");
    return List.of();
  }

  private InjectTarget convertFromAgent(Agent agent, Inject inject) {
    return helperTargetSearchAdaptor.buildTargetWithExpectations(
        inject,
        () ->
            new AgentTarget(
                agent.getId(),
                agent.getExecutedByUser(),
                Set.of(),
                agent.getAsset().getId(),
                agent.getExecutor().getType()),
        true);
  }
}
