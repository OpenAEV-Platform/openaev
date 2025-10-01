package io.openaev.service.targets;

import io.openaev.database.model.*;
import io.openaev.service.targets.search.AgentTargetSearchAdaptor;
import io.openaev.service.targets.search.AssetGroupTargetSearchAdaptor;
import io.openaev.service.targets.search.EndpointTargetSearchAdaptor;
import io.openaev.service.targets.search.PlayerTargetSearchAdaptor;
import io.openaev.service.targets.search.TeamTargetSearchAdaptor;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.TargetType;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TargetService {
  private final AssetGroupTargetSearchAdaptor assetGroupTargetSearchAdaptor;
  private final EndpointTargetSearchAdaptor endpointTargetSearchAdaptor;
  private final TeamTargetSearchAdaptor teamTargetSearchAdaptor;
  private final AgentTargetSearchAdaptor agentTargetSearchAdaptor;
  private final PlayerTargetSearchAdaptor playerTargetSearchAdaptor;

  public Page<InjectTarget> searchTargets(
      TargetType injectTargetType, Inject inject, SearchPaginationInput input) {

    // handle defaults if filter group is null
    if (input.getFilterGroup() == null) {
      Filters.FilterGroup filterGroup = new Filters.FilterGroup();
      filterGroup.setMode(Filters.FilterMode.and);
      filterGroup.setFilters(List.of());
      input.setFilterGroup(filterGroup);
    }
    return switch (injectTargetType) {
      case ASSETS_GROUPS -> assetGroupTargetSearchAdaptor.search(input, inject);
      case ASSETS -> endpointTargetSearchAdaptor.search(input, inject);
      case TEAMS -> teamTargetSearchAdaptor.search(input, inject);
      case PLAYERS -> playerTargetSearchAdaptor.search(input, inject);
      case AGENT -> agentTargetSearchAdaptor.search(input, inject);
      default -> throw new IllegalArgumentException("Unsupported target type: " + injectTargetType);
    };
  }

  public List<FilterUtilsJpa.Option> getTargetOptions(
      TargetType targetType, Inject inject, String textSearch) {
    return switch (targetType) {
      case ASSETS_GROUPS -> assetGroupTargetSearchAdaptor.getOptionsForInject(inject, textSearch);
      case ASSETS -> endpointTargetSearchAdaptor.getOptionsForInject(inject, textSearch);
      case TEAMS -> teamTargetSearchAdaptor.getOptionsForInject(inject, textSearch);
      case PLAYERS -> playerTargetSearchAdaptor.getOptionsForInject(inject, textSearch);
      case AGENT -> agentTargetSearchAdaptor.getOptionsForInject(inject, textSearch);
      default -> throw new IllegalArgumentException("Unsupported target type: " + targetType);
    };
  }

  public List<FilterUtilsJpa.Option> getTargetOptionsByIds(
      TargetType targetType, List<String> ids) {
    return switch (targetType) {
      case ASSETS_GROUPS -> assetGroupTargetSearchAdaptor.getOptionsByIds(ids);
      case ASSETS -> endpointTargetSearchAdaptor.getOptionsByIds(ids);
      case TEAMS -> teamTargetSearchAdaptor.getOptionsByIds(ids);
      case AGENT -> agentTargetSearchAdaptor.getOptionsByIds(ids);
      case PLAYERS -> playerTargetSearchAdaptor.getOptionsByIds(ids);
      default -> throw new IllegalArgumentException("Unsupported target type: " + targetType);
    };
  }
}
