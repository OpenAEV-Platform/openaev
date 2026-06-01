package io.openaev.search;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.database.model.Base;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class FullTextSearchApi extends RestBehavior {

  public static final String GLOBAL_SEARCH_URI = "/api/fulltextsearch";
  private static final String TENANT_GLOBAL_SEARCH_URI = TENANT_PREFIX + "/fulltextsearch";

  private final FullTextSearchService<? extends Base> fullTextSearchService;

  @PostMapping({GLOBAL_SEARCH_URI, TENANT_GLOBAL_SEARCH_URI})
  @AccessControl(skipRBAC = true)
  public Map<? extends Class<? extends Base>, FullTextSearchService.FullTextSearchCountResult>
      fullTextSearch(@Valid @RequestBody final SearchTerm searchTerm) {
    return this.fullTextSearchService.fullTextSearch(searchTerm.getSearchTerm());
  }

  @PostMapping({GLOBAL_SEARCH_URI + "/{clazz}", TENANT_GLOBAL_SEARCH_URI + "/{clazz}"})
  @AccessControl(skipRBAC = true)
  public Page<FullTextSearchService.FullTextSearchResult> fullTextSearch(
      @PathVariable @NotBlank final String clazz,
      @RequestBody @Valid SearchPaginationInput searchPaginationInput)
      throws ClassNotFoundException {
    if (!this.fullTextSearchService.getAllowedClass().contains(clazz)) {
      throw new IllegalArgumentException("Class not allowed : " + clazz);
    }

    return this.fullTextSearchService.fullTextSearch(Class.forName(clazz), searchPaginationInput);
  }

  @Data
  public static class SearchTerm {
    private String searchTerm;
  }
}
