package io.openaev.service.threat_arsenal;

import io.openaev.database.model.ChainingTypeRegistry;
import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.Filters;
import io.openaev.database.model.InjectorContract;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class ProvidingFilterSpecificationBuilder {

  public static final String PROVIDING_FILTER_KEY = "injector_contract_providing";

  public record ProvidingFilterContext(
      SearchPaginationInput searchInput,
      Specification<InjectorContract> specification,
      Filters.FilterMode mode,
      boolean hasProvidingFilters,
      boolean hasRemainingFilters) {}

  public ProvidingFilterContext extractProvidingFilter(SearchPaginationInput searchInput) {
    if (searchInput.getFilterGroup() == null || searchInput.getFilterGroup().getFilters() == null) {
      return new ProvidingFilterContext(
          searchInput, Specification.unrestricted(), Filters.FilterMode.and, false, false);
    }

    List<Filters.Filter> allFilters = searchInput.getFilterGroup().getFilters();
    List<Filters.Filter> providingFilters =
        allFilters.stream().filter(filter -> PROVIDING_FILTER_KEY.equals(filter.getKey())).toList();

    if (providingFilters.isEmpty()) {
      return new ProvidingFilterContext(
          searchInput,
          Specification.unrestricted(),
          Filters.FilterMode.and,
          false,
          !allFilters.isEmpty());
    }

    List<Filters.Filter> remainingFilters =
        allFilters.stream()
            .filter(filter -> !PROVIDING_FILTER_KEY.equals(filter.getKey()))
            .toList();
    searchInput.getFilterGroup().setFilters(remainingFilters);

    Filters.FilterMode mode =
        searchInput.getFilterGroup().getMode() == null
            ? Filters.FilterMode.and
            : searchInput.getFilterGroup().getMode();
    List<Specification<InjectorContract>> providingSpecs =
        providingFilters.stream().map(this::toProvidingSpecification).toList();

    Specification<InjectorContract> merged = providingSpecs.get(0);
    for (int i = 1; i < providingSpecs.size(); i++) {
      merged =
          mode == Filters.FilterMode.or
              ? merged.or(providingSpecs.get(i))
              : merged.and(providingSpecs.get(i));
    }

    return new ProvidingFilterContext(searchInput, merged, mode, true, !remainingFilters.isEmpty());
  }

  private Specification<InjectorContract> toProvidingSpecification(Filters.Filter filter) {
    Filters.FilterOperator operator =
        filter.getOperator() == null ? Filters.FilterOperator.eq : filter.getOperator();

    if (operator == Filters.FilterOperator.empty || operator == Filters.FilterOperator.not_empty) {
      Specification<InjectorContract> hasAnyProviding = buildHasProvidingSpecification(null);
      return operator == Filters.FilterOperator.empty
          ? Specification.not(hasAnyProviding)
          : hasAnyProviding;
    }

    Set<ContractOutputType> expectedOutputTypes = resolveContractOutputTypes(filter.getValues());
    if (expectedOutputTypes.isEmpty()) {
      return Specification.unrestricted();
    }

    Specification<InjectorContract> hasProviding =
        buildHasProvidingSpecification(expectedOutputTypes);

    return switch (operator) {
      case not_eq, not_contains -> Specification.not(hasProviding);
      default -> hasProviding;
    };
  }

  private Specification<InjectorContract> buildHasProvidingSpecification(
      Set<ContractOutputType> expectedOutputTypes) {
    return (root, query, cb) -> {
      // No DISTINCT here: this predicate only adds a correlated EXISTS subquery
      // and content-LIKE predicates, neither of which fans out the outer query,
      // and the threat arsenal projection GROUP BYs the selected scalar keys
      // (contract composite id, payload id, collector type id - not the
      // unselected injector join id), so it already yields exactly one row per
      // contract even when a contract is linked to several injectors. Forcing
      // DISTINCT produced "SELECT DISTINCT ... ORDER BY" SQL whose ORDER BY
      // expands the composite id to (injector_contract_id, tenant_id) while the
      // SELECT lists only injector_contract_id, which PostgreSQL rejects with
      // "for SELECT DISTINCT, ORDER BY expressions must appear in select list"
      // (HTTP 500 on every findings-scoped arsenal search).

      Subquery<Integer> payloadSubquery = query.subquery(Integer.class);
      var payloadRoot = payloadSubquery.correlate(root);
      Join<?, ?> payloadJoin = payloadRoot.join("payload", JoinType.LEFT);
      Join<?, ?> outputParserJoin = payloadJoin.join("outputParsers", JoinType.LEFT);
      Join<?, ?> outputElementsJoin =
          outputParserJoin.join("contractOutputElements", JoinType.LEFT);
      payloadSubquery.select(cb.literal(1));
      if (expectedOutputTypes == null || expectedOutputTypes.isEmpty()) {
        payloadSubquery.where(cb.isNotNull(outputElementsJoin.get("type")));
      } else {
        payloadSubquery.where(outputElementsJoin.get("type").in(expectedOutputTypes));
      }

      Predicate noPayload = cb.isNull(root.get("payload"));
      var lowerContent = cb.lower(root.get("content"));
      Predicate contentMatch;
      if (expectedOutputTypes == null || expectedOutputTypes.isEmpty()) {
        contentMatch = cb.and(noPayload, cb.like(lowerContent, "%\"outputs\"%[%{%\"type\"%"));
      } else {
        Set<String> expectedLabels =
            expectedOutputTypes.stream()
                .map(ContractOutputType::getLabel)
                .collect(Collectors.toSet());
        List<Predicate> contentPredicates = new ArrayList<>();
        for (String label : expectedLabels) {
          contentPredicates.add(
              cb.and(
                  noPayload,
                  cb.like(
                      lowerContent,
                      "%\"outputs\"%[%{%\"type\"%\"" + label.toLowerCase(Locale.ROOT) + "\"%")));
        }
        contentMatch =
            contentPredicates.isEmpty()
                ? cb.disjunction()
                : cb.or(contentPredicates.toArray(Predicate[]::new));
      }

      return cb.or(cb.exists(payloadSubquery), contentMatch);
    };
  }

  private Set<ContractOutputType> resolveContractOutputTypes(List<String> primitiveLabels) {
    if (primitiveLabels == null || primitiveLabels.isEmpty()) {
      return Set.of();
    }
    Set<String> normalizedLabels = new HashSet<>();
    primitiveLabels.stream()
        .filter(value -> value != null && !value.isBlank())
        .map(value -> value.toLowerCase(Locale.ROOT))
        .forEach(normalizedLabels::add);

    EnumSet<ContractOutputType> resolvedTypes = EnumSet.noneOf(ContractOutputType.class);
    String textLabel = ContractOutputType.Text.getLabel().toLowerCase(Locale.ROOT);
    if (normalizedLabels.contains(textLabel)) {
      resolvedTypes.add(ContractOutputType.Text);
      normalizedLabels.remove(textLabel);
    }

    if (normalizedLabels.isEmpty()) {
      return resolvedTypes;
    }

    for (ContractOutputType candidate : ContractOutputType.values()) {
      if (normalizedLabels.contains(candidate.getLabel().toLowerCase(Locale.ROOT))
          || normalizedLabels.contains(candidate.name().toLowerCase(Locale.ROOT))) {
        resolvedTypes.add(candidate);
        continue;
      }
      ChainingTypeRegistry.getPrimitiveTypesForContractOutputType(candidate).stream()
          .map(primitiveType -> primitiveType.label.toLowerCase(Locale.ROOT))
          .filter(normalizedLabels::contains)
          .findFirst()
          .ifPresent(_unused -> resolvedTypes.add(candidate));
    }
    return resolvedTypes;
  }
}
