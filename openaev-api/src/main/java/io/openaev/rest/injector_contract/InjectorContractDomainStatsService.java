package io.openaev.rest.injector_contract;

import io.openaev.database.model.Domain;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.specification.InjectorContractSpecification;
import io.openaev.rest.injector_contract.input.InjectorContractSearchPaginationInput;
import io.openaev.rest.injector_contract.output.InjectorContractDomainCountOutput;
import io.openaev.service.UserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InjectorContractDomainStatsService {

  @PersistenceContext private final EntityManager entityManager;

  private final UserService userService;

  public List<InjectorContractDomainCountOutput> countByDomain(
      InjectorContractSearchPaginationInput input) {

    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<InjectorContract> root = cq.from(InjectorContract.class);

    Specification<InjectorContract> spec =
        InjectorContractSpecification.hasAccessToInjectorContract(userService.currentUser());

    Predicate predicate = spec.toPredicate(root, cq, cb);
    if (predicate != null) {
      cq.where(predicate);
    }

    Join<InjectorContract, Domain> domainJoin = root.join("domains", JoinType.LEFT);

    cq.multiselect(
            domainJoin.get("id").alias("domainId"),
            cb.countDistinct(root.get("id")).alias("contractCount"))
        .groupBy(domainJoin.get("id"));

    return entityManager.createQuery(cq).getResultList().stream()
        .map(
            t ->
                new InjectorContractDomainCountOutput(
                    t.get("domainId", String.class), t.get("contractCount", Long.class)))
        .toList();
  }
}
