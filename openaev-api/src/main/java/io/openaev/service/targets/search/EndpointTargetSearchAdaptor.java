package io.openaev.service.targets.search;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.database.model.*;
import io.openaev.database.repository.EndpointRepository;
import io.openaev.service.targets.search.specifications.IncludeDirectEndpointTargetsSpecification;
import io.openaev.service.targets.search.specifications.IncludeMembersOfAssetGroupsSpecification;
import io.openaev.service.targets.search.specifications.SearchSpecificationUtils;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class EndpointTargetSearchAdaptor extends SearchAdaptorBase {

  private final EndpointRepository endpointRepository;
  private final SearchSpecificationUtils<Endpoint> searchSpecificationUtils;
  private final IncludeMembersOfAssetGroupsSpecification<Endpoint>
      includeMembersOfAssetGroupsSpecification;
  private final IncludeDirectEndpointTargetsSpecification<Endpoint>
      includeDirectEndpointTargetsSpecification;
  private final HelperTargetSearchAdaptor helperTargetSearchAdaptor;

  private final List<String> joinPath = List.of("assets");

  public EndpointTargetSearchAdaptor(
      EndpointRepository endpointRepository,
      SearchSpecificationUtils<Endpoint> searchSpecificationUtils,
      IncludeMembersOfAssetGroupsSpecification<Endpoint> includeMembersOfAssetGroupsSpecification,
      IncludeDirectEndpointTargetsSpecification<Endpoint> includeDirectEndpointTargetsSpecification,
      HelperTargetSearchAdaptor helperTargetSearchAdaptor) {
    this.endpointRepository = endpointRepository;
    this.helperTargetSearchAdaptor = helperTargetSearchAdaptor;
    this.searchSpecificationUtils = searchSpecificationUtils;
    this.includeMembersOfAssetGroupsSpecification = includeMembersOfAssetGroupsSpecification;
    this.includeDirectEndpointTargetsSpecification = includeDirectEndpointTargetsSpecification;
    // field name translations
    this.fieldTranslations.put("target_name", "asset_name");
    this.fieldTranslations.put("target_tags", "asset_tags");
  }

  @Override
  public Page<InjectTarget> search(SearchPaginationInput input, @NotNull Inject scopedInject) {
    Specification<Endpoint> overallSpec =
        searchSpecificationUtils.compileSpecificationForAssetGroupMembership(
            scopedInject, input, joinPath);

    Specification<Endpoint> memberOfAnyTargetGroupSpec =
        searchSpecificationUtils.compileSpecificationForAssetGroupMembership(
            scopedInject,
            SearchPaginationInput.builder().filterGroup(new Filters.FilterGroup()).build(),
            joinPath);

    SearchPaginationInput translatedInput = this.translate(input, scopedInject);

    Page<Endpoint> eps =
        buildPaginationJPA(
            (Specification<Endpoint> specification, Pageable pageable) -> {
              if (Filters.FilterMode.and.equals(input.getFilterGroup().getMode())) {
                return this.endpointRepository.findAll(overallSpec.and(specification), pageable);
              }
              return this.endpointRepository.findAll(
                  overallSpec.or(specification.and(memberOfAnyTargetGroupSpec)), pageable);
            },
            translatedInput,
            Endpoint.class);

    return new PageImpl<>(
        eps.getContent().stream()
            .map(endpoint -> convertFromEndpoint(endpoint, scopedInject))
            .toList(),
        eps.getPageable(),
        eps.getTotalElements());
  }

  @Override
  public List<FilterUtilsJpa.Option> getOptionsForInject(Inject scopedInject, String textSearch) {
    Specification<Endpoint> spec =
        includeMembersOfAssetGroupsSpecification
            .buildSpecification(
                scopedInject.getAssetGroups().stream().map(AssetGroup::getId).toList(), joinPath)
            .or(
                includeDirectEndpointTargetsSpecification.buildSpecification(
                    scopedInject, joinPath));

    Specification<Endpoint> nameSpec =
        (root, query, criteriaBuilder) ->
            criteriaBuilder.like(root.get("name"), "%" + textSearch + "%");

    return this.endpointRepository.findAll(spec.and(nameSpec)).stream()
        .map(ep -> new FilterUtilsJpa.Option(ep.getId(), ep.getName()))
        .toList();
  }

  @Override
  public List<FilterUtilsJpa.Option> getOptionsByIds(List<String> ids) {
    return fromIterable(this.endpointRepository.findAllById(ids)).stream()
        .map(ep -> new FilterUtilsJpa.Option(ep.getId(), ep.getName()))
        .toList();
  }

  private InjectTarget convertFromEndpoint(Endpoint endpoint, Inject inject) {
    return helperTargetSearchAdaptor.buildTargetWithExpectations(
        inject,
        () ->
            new EndpointTarget(
                endpoint.getId(),
                endpoint.getName(),
                endpoint.getTags().stream().map(Tag::getId).collect(Collectors.toSet()),
                endpoint.getPlatform().name()),
        true);
  }
}
