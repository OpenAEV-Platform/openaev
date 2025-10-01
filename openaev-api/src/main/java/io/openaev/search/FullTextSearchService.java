package io.openaev.search;

import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;
import static io.openaev.utils.pagination.SortUtilsRuntime.toSortRuntime;
import static org.springframework.util.StringUtils.hasText;

import io.openaev.database.model.*;
import io.openaev.database.repository.*;
import io.openaev.database.specification.SpecificationUtils;
import io.openaev.service.UserService;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FullTextSearchService<T extends Base> {

  private final AssetRepository assetRepository;
  private final AssetGroupRepository assetGroupRepository;
  private final UserRepository userRepository;
  private final TeamRepository teamRepository;
  private final OrganizationRepository organizationRepository;
  private final ScenarioRepository scenarioRepository;
  private final ExerciseRepository exerciseRepository;
  private final UserService userService;

  private Map<Class<T>, JpaSpecificationExecutor<T>> repositoryMap;

  private Map<Class<T>, List<String>> searchListByClassMap;
  private Map<Class<T>, Optional<Capability>> capaByClassMap;

  @PostConstruct
  @SuppressWarnings("unchecked")
  public void init() {
    this.repositoryMap =
        Map.of(
            (Class<T>) Asset.class, (JpaSpecificationExecutor<T>) this.assetRepository,
            (Class<T>) AssetGroup.class, (JpaSpecificationExecutor<T>) this.assetGroupRepository,
            (Class<T>) User.class, (JpaSpecificationExecutor<T>) this.userRepository,
            (Class<T>) Team.class, (JpaSpecificationExecutor<T>) this.teamRepository,
            (Class<T>) Organization.class,
                (JpaSpecificationExecutor<T>) this.organizationRepository,
            (Class<T>) Scenario.class, (JpaSpecificationExecutor<T>) this.scenarioRepository,
            (Class<T>) Exercise.class, (JpaSpecificationExecutor<T>) this.exerciseRepository);

    this.searchListByClassMap =
        Map.of(
            (Class<T>) Asset.class,
            List.of("name", "id"),
            (Class<T>) AssetGroup.class,
            List.of("name", "id"),
            (Class<T>) User.class,
            List.of("email", "id"),
            (Class<T>) Team.class,
            List.of("name", "id"),
            (Class<T>) Organization.class,
            List.of("name", "id"),
            (Class<T>) Scenario.class,
            List.of("name", "id"),
            (Class<T>) Exercise.class,
            List.of("name", "id"));

    this.capaByClassMap =
        Map.of(
            (Class<T>) Asset.class,
            Capability.of(ResourceType.ASSET, Action.SEARCH),
            (Class<T>) AssetGroup.class,
            Capability.of(ResourceType.ASSET_GROUP, Action.SEARCH),
            (Class<T>) User.class,
            Capability.of(ResourceType.USER, Action.SEARCH),
            (Class<T>) Team.class,
            Capability.of(ResourceType.TEAM, Action.SEARCH),
            (Class<T>) Organization.class,
            Capability.of(ResourceType.ORGANIZATION, Action.SEARCH),
            (Class<T>) Scenario.class,
            Capability.of(ResourceType.SCENARIO, Action.SEARCH),
            (Class<T>) Exercise.class,
            Capability.of(ResourceType.SIMULATION, Action.SEARCH));

    validateMapKeys();
  }

  /** Ensure that the map have all the same classes, in case we forget when adding a new class. */
  private void validateMapKeys() {
    Set<Class<T>> keys1 = repositoryMap.keySet();
    Set<Class<T>> keys2 = searchListByClassMap.keySet();
    Set<Class<T>> keys3 = capaByClassMap.keySet();

    if (!keys1.equals(keys2) || !keys1.equals(keys3)) {
      throw new IllegalStateException("All maps must have the same keys");
    }
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

    Class<T> clazzT =
        this.repositoryMap.keySet().stream()
            .filter(k -> k.isAssignableFrom(clazz))
            .findFirst()
            .orElseThrow(
                () -> new IllegalArgumentException(clazz + " is not handle by full text search"));

    User currentUser = userService.currentUser();
    // Check if the principal has the right to search this class
    Capability capaForClass = capaByClassMap.get(clazzT).orElse(Capability.BYPASS);
    boolean isGrantable = clazzT.getAnnotation(Grantable.class) != null;

    if (!currentUser.isAdminOrBypass() && capaForClass != Capability.BYPASS && !isGrantable) {
      // We can't really use the PermissionService.hasPermission method here because it would
      // require a mapping between classes and resourceType
      if (!currentUser.getCapabilities().contains(Capability.BYPASS)
          && !currentUser.getCapabilities().contains(capaForClass)) {
        return generateEmptyResult(searchPaginationInput);
      }
    }

    JpaSpecificationExecutor<T> repository = repositoryMap.get(clazzT);

    String finalSearchTerm = getFinalSearchTerm(searchPaginationInput.getTextSearch());

    // Specification building
    Specification<T> specs =
        SpecificationUtils.<T>fullTextSearch(finalSearchTerm, searchListByClassMap.get(clazzT))
            .and(
                SpecificationUtils.hasGrantAccess(
                    currentUser.getId(),
                    currentUser.isAdminOrBypass(),
                    currentUser.getCapabilities().contains(capaForClass),
                    Grant.GRANT_TYPE.OBSERVER));

    return buildPaginationJPA(repository::findAll, searchPaginationInput, clazzT, specs)
        .map(this::transform);
  }

  private FullTextSearchResult transform(T element) {
    switch (element) {
      case Asset asset -> {
        FullTextSearchResult result = new FullTextSearchResult();
        result.setId(asset.getId());
        result.setName(asset.getName());
        result.setDescription(asset.getDescription());
        result.setTags(asset.getTags());
        result.setClazz(Asset.class.getSimpleName());
        return result;
      }
      case AssetGroup assetGroup -> {
        FullTextSearchResult result = new FullTextSearchResult();
        result.setId(assetGroup.getId());
        result.setName(assetGroup.getName());
        result.setDescription(assetGroup.getDescription());
        result.setTags(assetGroup.getTags());
        result.setClazz(AssetGroup.class.getSimpleName());
        return result;
      }
      case User user -> {
        FullTextSearchResult result = new FullTextSearchResult();
        result.setId(user.getId());
        result.setName(user.getEmail());
        result.setTags(user.getTags());
        result.setClazz(User.class.getSimpleName());
        return result;
      }
      case Team team -> {
        FullTextSearchResult result = new FullTextSearchResult();
        result.setId(team.getId());
        result.setName(team.getName());
        result.setDescription(team.getDescription());
        result.setTags(team.getTags());
        result.setClazz(Team.class.getSimpleName());
        return result;
      }
      case Organization organization -> {
        FullTextSearchResult result = new FullTextSearchResult();
        result.setId(organization.getId());
        result.setName(organization.getName());
        result.setDescription(organization.getDescription());
        result.setTags(organization.getTags());
        result.setClazz(Organization.class.getSimpleName());
        return result;
      }
      case Scenario scenario -> {
        FullTextSearchResult result = new FullTextSearchResult();
        result.setId(scenario.getId());
        result.setName(scenario.getName());
        result.setDescription(scenario.getDescription());
        result.setTags(scenario.getTags());
        result.setClazz(Scenario.class.getSimpleName());
        return result;
      }
      case Exercise exercise -> {
        FullTextSearchResult result = new FullTextSearchResult();
        result.setId(exercise.getId());
        result.setName(exercise.getName());
        result.setDescription(exercise.getDescription());
        result.setTags(exercise.getTags());
        result.setClazz(Exercise.class.getSimpleName());
        return result;
      }
      default -> {}
    }
    return null;
  }

  /**
   * Perform a full text search on all classes and only return the counts for each class. To get the
   * results, use the {@link #fullTextSearch(Class, SearchPaginationInput)}
   *
   * @param searchTerm the search term to use
   * @return a map of class type to the count of results for that class
   */
  @SuppressWarnings("unchecked")
  public Map<Class<T>, FullTextSearchCountResult> fullTextSearch(
      @Nullable final String searchTerm) {
    if (!hasText(searchTerm)) {
      return Map.of(
          (Class<T>) Asset.class, new FullTextSearchCountResult(Asset.class.getSimpleName(), 0L),
          (Class<T>) AssetGroup.class,
              new FullTextSearchCountResult(AssetGroup.class.getSimpleName(), 0L),
          (Class<T>) User.class, new FullTextSearchCountResult(User.class.getSimpleName(), 0L),
          (Class<T>) Team.class, new FullTextSearchCountResult(Team.class.getSimpleName(), 0L),
          (Class<T>) Organization.class,
              new FullTextSearchCountResult(Organization.class.getSimpleName(), 0L),
          (Class<T>) Scenario.class,
              new FullTextSearchCountResult(Scenario.class.getSimpleName(), 0L),
          (Class<T>) Exercise.class,
              new FullTextSearchCountResult(Exercise.class.getSimpleName(), 0L));
    }

    Map<Class<T>, FullTextSearchCountResult> results = new HashMap<>();
    String finalSearchTerm = getFinalSearchTerm(searchTerm);

    User currentUser = userService.currentUser();
    // Only search classes that the user has access to
    Set<Class<T>> classesToSearch;
    if (currentUser.isAdminOrBypass()) {
      classesToSearch = new HashSet<>(repositoryMap.keySet());
    } else {

      classesToSearch = new HashSet<>();
      for (Map.Entry<Class<T>, Optional<Capability>> entry : capaByClassMap.entrySet()) {
        Capability capaForClass = entry.getValue().orElse(Capability.BYPASS);
        if (currentUser.getCapabilities().contains(capaForClass)
            || Capability.BYPASS.equals(capaForClass)) {
          classesToSearch.add(entry.getKey());
        }
      }
    }

    classesToSearch.forEach(
        tClass -> {
          JpaSpecificationExecutor<T> repository = repositoryMap.get(tClass);
          // Specification building
          Specification<T> specs =
              SpecificationUtils.<T>fullTextSearch(
                      finalSearchTerm, searchListByClassMap.get(tClass))
                  .and(
                      SpecificationUtils.hasGrantAccess(
                          currentUser.getId(),
                          currentUser.isAdminOrBypass(),
                          currentUser
                              .getCapabilities()
                              .contains(capaByClassMap.get(tClass).orElse(Capability.BYPASS)),
                          Grant.GRANT_TYPE.OBSERVER));
          long count = repository.count(specs);
          results.put(tClass, new FullTextSearchCountResult(tClass.getSimpleName(), count));
        });

    return results;
  }

  private static String getFinalSearchTerm(String searchTerm) {
    return Arrays.stream(searchTerm.split(" "))
        .map(s -> "(" + s + ":*)")
        .collect(Collectors.joining(" & "));
  }

  public List<String> getAllowedClass() {
    return this.repositoryMap.keySet().stream().map(Class::getName).toList();
  }

  @AllArgsConstructor
  @Data
  public static class FullTextSearchCountResult {

    @NotBlank private String clazz;
    @NotBlank private long count;
  }

  @Data
  public static class FullTextSearchResult {

    @NotBlank private String id;
    @NotBlank private String name;
    private String description;
    private Set<Tag> tags;
    @NotBlank private String clazz;
  }
}
