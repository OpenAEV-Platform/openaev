package io.openaev.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Tenant;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.Session;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.metamodel.CollectionClassification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Hibernate 7 refuses to write a change to a bag (a {@code List} without {@code @OrderColumn})
 * while an enabled filter affects it ("cannot recreate collection while filter is enabled"): a bag
 * can only be rewritten by deleting every row and re-inserting the loaded ones, which would drop
 * the rows the filter hides. Unlike Hibernate 6 it also counts a filter on the element entity, or
 * reached from it through an eager join-fetched association, so any owned bag of tenant-filtered
 * entities fails to update inside a tenant-scoped request. Such collections must be {@code Set}s,
 * which Hibernate updates row by row.
 */
@DisplayName("No owned bag collection is affected by the tenant filter")
class TenantFilteredBagCollectionsTest extends IntegrationTest {

  @Autowired private EntityManagerFactory entityManagerFactory;

  @Test
  void given_tenantFilterEnabled_should_haveNoOwnedBagAffectedByIt() {
    SessionFactoryImplementor sessionFactory =
        entityManagerFactory.unwrap(SessionFactoryImplementor.class);
    try (EntityManager em = entityManagerFactory.createEntityManager()) {
      Session session = em.unwrap(Session.class);
      session.enableFilter("tenantFilter").setParameter("tenantId", Tenant.DEFAULT_TENANT_UUID);
      SharedSessionContractImplementor sessionImpl =
          session.unwrap(SharedSessionContractImplementor.class);

      List<String> affectedBags = new ArrayList<>();
      sessionFactory
          .getMappingMetamodel()
          .forEachCollectionDescriptor(
              collection -> {
                if (!collection.isInverse()
                    && collection.getCollectionSemantics().getCollectionClassification()
                        == CollectionClassification.BAG
                    && collection.isAffectedByEnabledFilters(sessionImpl)) {
                  affectedBags.add(collection.getRole());
                }
              });

      assertThat(affectedBags.stream().sorted().toList())
          .as("owned bags affected by tenantFilter: map them as Set")
          .isEmpty();
    }
  }
}
