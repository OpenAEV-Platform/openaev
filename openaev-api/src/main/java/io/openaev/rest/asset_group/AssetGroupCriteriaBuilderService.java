package io.openaev.rest.asset_group;

import static io.openaev.database.criteria.GenericCriteria.countQuery;
import static io.openaev.rest.asset_group.AssetGroupQueryHelper.execution;
import static io.openaev.rest.asset_group.AssetGroupQueryHelper.select;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationCriteriaBuilder;
import static io.openaev.utils.pagination.SortUtilsCriteriaBuilder.toSortCriteriaBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.AssetGroup;
import io.openaev.rest.asset_group.form.AssetGroupOutput;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class AssetGroupCriteriaBuilderService {

  @Resource protected ObjectMapper mapper;

  @PersistenceContext private EntityManager entityManager;

  public Page<AssetGroupOutput> assetGroupPagination(
      @NotNull SearchPaginationInput searchPaginationInput) {
    return buildPaginationCriteriaBuilder(this::paginate, searchPaginationInput, AssetGroup.class);
  }

  public List<AssetGroupOutput> find(Specification<AssetGroup> specification) {
    CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();

    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<AssetGroup> root = cq.from(AssetGroup.class);
    select(cb, cq, root);

    if (specification != null) {
      Predicate predicate = specification.toPredicate(root, cq, cb);
      if (predicate != null) {
        cq.where(predicate);
      }
    }

    TypedQuery<Tuple> query = entityManager.createQuery(cq);
    return execution(query, this.mapper);
  }

  // -- PRIVATE --

  private Page<AssetGroupOutput> paginate(
      Specification<AssetGroup> specification,
      Specification<AssetGroup> specificationCount,
      Pageable pageable) {
    CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();

    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<AssetGroup> assetGroupRoot = cq.from(AssetGroup.class);
    select(cb, cq, assetGroupRoot);

    // -- Specification --
    if (specification != null) {
      Predicate predicate = specification.toPredicate(assetGroupRoot, cq, cb);
      if (predicate != null) {
        cq.where(predicate);
      }
    }

    // -- Sorting --
    List<Order> orders = toSortCriteriaBuilder(cb, assetGroupRoot, pageable.getSort());
    cq.orderBy(orders);

    // Type Query
    TypedQuery<Tuple> query = entityManager.createQuery(cq);

    // -- Pagination --
    query.setFirstResult((int) pageable.getOffset());
    query.setMaxResults(pageable.getPageSize());

    // -- EXECUTION --
    List<AssetGroupOutput> assetGroups = execution(query, this.mapper);

    // -- Count Query --
    Long total = countQuery(cb, this.entityManager, AssetGroup.class, specificationCount);

    return new PageImpl<>(assetGroups, pageable, total);
  }
}
