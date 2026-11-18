package io.openaev.service.targets.search;

import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectTarget;
import io.openaev.database.model.ManualTarget;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.InjectContentUtils;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * Resolves the manual targets (IP addresses, subnets or hostnames typed in the inject content when
 * {@code target_selector = "manual"}) as {@link InjectTarget}s, so they appear in the inject
 * "Targets" panel. Manual targets are neither entities nor relations of the inject, only a content
 * value, hence a dedicated adaptor rather than a JPA-relation search.
 */
@Component
@RequiredArgsConstructor
public class ManualTargetSearchAdaptor extends SearchAdaptorBase {

  private final HelperTargetSearchAdaptor helperTargetSearchAdaptor;

  private List<String> matchingValues(Inject scopedInject, String textSearch) {
    String search = StringUtils.trimToEmpty(textSearch);
    return InjectContentUtils.contentManualTargets(scopedInject.getContent()).stream()
        .filter(value -> search.isEmpty() || StringUtils.containsIgnoreCase(value, search))
        .toList();
  }

  @Override
  public Page<InjectTarget> search(SearchPaginationInput input, Inject scopedInject) {
    List<String> values = matchingValues(scopedInject, input.getTextSearch());

    // A real (paged) Pageable is required: a PageImpl backed by Pageable.unpaged() throws
    // UnsupportedOperationException when Jackson serialises the pageable in the response.
    int size = Math.max(1, input.getSize());
    Pageable pageable = PageRequest.of(input.getPage(), size);
    int from = Math.min((int) pageable.getOffset(), values.size());
    int to = Math.min(from + size, values.size());
    List<InjectTarget> page =
        values.subList(from, to).stream()
            .map(value -> convertFromManualTarget(value, scopedInject))
            .toList();
    return new PageImpl<>(page, pageable, values.size());
  }

  @Override
  public List<FilterUtilsJpa.Option> getOptionsForInject(Inject scopedInject, String textSearch) {
    return matchingValues(scopedInject, textSearch).stream()
        .map(value -> new FilterUtilsJpa.Option(ManualTarget.idOf(value), value))
        .toList();
  }

  /**
   * Manual target ids are derived from values that only live in an inject content, so they cannot
   * be resolved back without that inject.
   */
  @Override
  public List<FilterUtilsJpa.Option> getOptionsByIds(List<String> ids) {
    return List.of();
  }

  private InjectTarget convertFromManualTarget(String value, Inject inject) {
    return helperTargetSearchAdaptor.buildTargetWithExpectations(
        inject, () -> new ManualTarget(value), true);
  }
}
