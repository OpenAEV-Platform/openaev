package io.openaev.search;

import static io.openaev.search.SearchableClass.DESCRIPTION;
import static io.openaev.search.SearchableClass.EMAIL;
import static io.openaev.search.SearchableClass.NAME;
import static io.openaev.utils.FilterUtilsJpa.computeFilterGroupJpa;
import static io.openaev.utils.pagination.PaginationUtils.buildPageable;
import static io.openaev.utils.pagination.SortUtilsRuntime.toSortRuntime;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.springframework.util.StringUtils.hasText;

import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.repository.*;
import io.openaev.database.specification.SpecificationUtils;
import io.openaev.database.specification.TenantSpecification;
import io.openaev.service.UserService;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FullTextSearchService {

  private final AssetRepository assetRepository;
  private final AssetGroupRepository assetGroupRepository;
  private final UserRepository userRepository;
  private final TeamRepository teamRepository;
  private final OrganizationRepository organizationRepository;
  private final ScenarioRepository scenarioRepository;
  private final ExerciseRepository exerciseRepository;
  private final UserService userService;
  private final FullTextSearchQuery fullTextSearchQuery;

  private Map<Class<? extends Base>, SearchableClass<? extends Base>> searchableClasses;

  @PostConstruct
  public void init() {
    this.searchableClasses =
        Stream.of(
                new SearchableClass<>(
                    Asset.class,
                    assetRepository,
                    searchCapability(ResourceType.ASSET),
                    NAME,
                    DESCRIPTION,
                    // Security platforms share the assets table but have their own page
                    Set.of(SecurityPlatform.class)),
                new SearchableClass<>(
                    AssetGroup.class,
                    assetGroupRepository,
                    searchCapability(ResourceType.ASSET_GROUP),
                    NAME,
                    DESCRIPTION),
                new SearchableClass<>(
                    User.class, userRepository, searchCapability(ResourceType.USER), EMAIL, null),
                new SearchableClass<>(
                    Team.class,
                    teamRepository,
                    searchCapability(ResourceType.TEAM),
                    NAME,
                    DESCRIPTION),
                new SearchableClass<>(
                    Organization.class,
                    organizationRepository,
                    searchCapability(ResourceType.ORGANIZATION),
                    NAME,
                    DESCRIPTION),
                new SearchableClass<>(
                    Scenario.class,
                    scenarioRepository,
                    searchCapability(ResourceType.SCENARIO),
                    NAME,
                    DESCRIPTION),
                new SearchableClass<>(
                    Exercise.class,
                    exerciseRepository,
                    searchCapability(ResourceType.SIMULATION),
                    NAME,
                    DESCRIPTION))
            .collect(toMap(SearchableClass::clazz, identity()));

    searchableClasses.values().forEach(fullTextSearchQuery::validateAttributes);
  }

  private static Capability searchCapability(ResourceType resourceType) {
    return Capability.of(resourceType, Action.SEARCH).orElse(Capability.BYPASS);
  }

  private PageImpl<FullTextSearchResult> generateEmptyResult(
      final SearchPaginationInput searchPaginationInput) {
    Pageable pageable =
        PageRequest.of(
            searchPaginationInput.getPage(),
            searchPaginationInput.getSize(),
            toSortRuntime(searchPaginationInput.getSorts()));
    return new PageImpl<>(Collections.emptyList(), pageable, 0);
  }

  public Page<FullTextSearchResult> fullTextSearch(
      @NotBlank final Class<?> clazz, @NotNull final SearchPaginationInput searchPaginationInput) {
    if (!hasText(searchPaginationInput.getTextSearch())) {
      return generateEmptyResult(searchPaginationInput);
    }

    SearchableClass<? extends Base> searchable =
        this.searchableClasses.values().stream()
            .filter(s -> s.clazz().isAssignableFrom(clazz))
            .findFirst()
            .orElseThrow(
                () -> new IllegalArgumentException(clazz + " is not handle by full text search"));

    return search(searchable, searchPaginationInput);
  }

  private <T extends Base> Page<FullTextSearchResult> search(
      SearchableClass<T> searchable, SearchPaginationInput searchPaginationInput) {
    User currentUser = userService.currentUser();
    // Check if the principal has the right to search this class
    Capability capaForClass = searchable.capability();
    boolean isGrantable = searchable.clazz().getAnnotation(Grantable.class) != null;

    if (!currentUser.isAdminOrBypass() && capaForClass != Capability.BYPASS && !isGrantable) {
      // We can't really use the PermissionService.hasPermission method here because it would
      // require a mapping between classes and resourceType
      if (!currentUser.getCapabilities().contains(Capability.BYPASS)
          && !currentUser.getCapabilities().contains(capaForClass)) {
        return generateEmptyResult(searchPaginationInput);
      }
    }

    String finalSearchTerm = getFinalSearchTerm(searchPaginationInput.getTextSearch());
    Specification<T> specs =
        searchSpecification(searchable, finalSearchTerm, currentUser)
            .and(computeFilterGroupJpa(searchPaginationInput.getFilterGroup()));

    return fullTextSearchQuery.findResults(
        searchable, specs, buildPageable(searchPaginationInput, searchable.clazz()));
  }

  /**
   * Perform a full text search on all classes and only return the counts for each class. To get the
   * results, use the {@link #fullTextSearch(Class, SearchPaginationInput)}
   *
   * @param searchTerm the search term to use
   * @return a map of class type to the count of results for that class
   */
  public Map<Class<? extends Base>, FullTextSearchCountResult> fullTextSearch(
      @Nullable final String searchTerm) {
    if (!hasText(searchTerm)) {
      return searchableClasses.keySet().stream()
          .collect(toMap(identity(), c -> new FullTextSearchCountResult(c.getSimpleName(), 0L)));
    }

    String finalSearchTerm = getFinalSearchTerm(searchTerm);
    User currentUser = userService.currentUser();

    // Only search classes that the user has access to
    return searchableClasses.values().stream()
        .filter(
            s ->
                currentUser.isAdminOrBypass()
                    || currentUser.getCapabilities().contains(s.capability())
                    || Capability.BYPASS.equals(s.capability()))
        .collect(toMap(SearchableClass::clazz, s -> count(s, finalSearchTerm, currentUser)));
  }

  private <T extends Base> FullTextSearchCountResult count(
      SearchableClass<T> searchable, String finalSearchTerm, User currentUser) {
    long count =
        searchable
            .repository()
            .count(searchSpecification(searchable, finalSearchTerm, currentUser));
    return new FullTextSearchCountResult(searchable.clazz().getSimpleName(), count);
  }

  private static <T extends Base> Specification<T> searchSpecification(
      SearchableClass<T> searchable, String finalSearchTerm, User currentUser) {
    return SpecificationUtils.<T>fullTextSearch(finalSearchTerm, searchable.searchAttributes())
        .and(
            SpecificationUtils.hasGrantAccess(
                currentUser.getId(),
                currentUser.isAdminOrBypass(),
                currentUser.getCapabilities().contains(searchable.capability()),
                Grant.GRANT_TYPE.OBSERVER))
        .and(TenantSpecification.fromTenant(TenantContext.getCurrentTenant()));
  }

  private static String getFinalSearchTerm(String searchTerm) {
    return Arrays.stream(searchTerm.split(" "))
        .map(s -> "(" + s + ":*)")
        .collect(Collectors.joining(" & "));
  }

  public List<String> getAllowedClass() {
    return this.searchableClasses.keySet().stream().map(Class::getName).toList();
  }
}
