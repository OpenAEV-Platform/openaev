package io.openaev.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Tag;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.TagRepository;
import io.openaev.service.PermissionService.TenantDecision;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.TagFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

/**
 * The per-tenant decisions of {@link PermissionService} take a decision in the tenant of each row,
 * on rows of two tenants that the read returns together.
 *
 * <p>The caller holds the rights {@code @WithMockUser} grants in the default tenant, and is a
 * member of tenant B, where it holds only what a test grants there. The decision is the one the
 * simulation and scenario lists take, the access right read from the caller's capabilities, through
 * the real {@link PermissionService#holdsInTenant}. The scope names both tenants while the ambient
 * tenant is the default one, as on the {@code X-Tenant-Ids} route.
 *
 * <p>The filter is applied to tags because {@code tags} carries no v1 tenant filter and this class
 * arms no active table, so the query returns the rows of both tenants and only the filter under
 * test decides which of them are read. A decision taken in the ambient tenant only reads B's rows
 * as if the caller held its default-tenant right there.
 */
@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Permission decisions taken in the tenant of each row")
class PermissionServiceTenantDecisionTest extends IntegrationTest {

  @Autowired private PermissionService permissionService;
  @Autowired private UserService userService;
  @Autowired private TagRepository tagRepository;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantB;

  @BeforeEach
  void setUp() throws Exception {
    tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    tenantB = tenantHelper.createTenantWithCurrentUser("tenant-decision-b").getId();
    TenantContext.setCurrentTenant(Tenant.DEFAULT_TENANT_UUID);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("With the access right in the default tenant")
  @WithMockUser(withCapabilities = {Capability.ACCESS_ASSESSMENT})
  class RightInDefaultTenant {

    @Test
    @DisplayName("given the right only outside B should hold in the default tenant only")
    void given_rightOnlyOutsideB_should_holdInDefaultTenantOnly() {
      // -- ARRANGE --
      BooleanSupplier accessRight = accessRight();

      // -- ACT --
      TenantDecision decision = permissionService.decideInEachTenant(bothTenants(), accessRight);

      // -- ASSERT --
      assertThat(decision.holding()).containsExactly(Tenant.DEFAULT_TENANT_UUID);
      assertThat(decision.notHolding()).containsExactly(tenantB);
      assertThat(decision.holdsIn(Tenant.DEFAULT_TENANT_UUID)).isTrue();
      assertThat(decision.holdsIn(tenantB)).isFalse();
    }

    @Test
    @DisplayName(
        "given the right only outside B should list every default row and B's granted rows")
    void given_rightOnlyOutsideB_should_listEveryDefaultRowAndGrantedBRows() {
      // -- ARRANGE --
      Row ofDefault = new Row("row-of-default", Tenant.DEFAULT_TENANT_UUID);
      Row ofB = new Row("row-of-b", tenantB);
      Row grantedOfB = new Row("granted-row-of-b", tenantB);

      // -- ACT --
      List<Row> listed =
          listInEachTenant(bothTenants(), List.of(ofDefault, ofB, grantedOfB), List.of(grantedOfB));

      // -- ASSERT --
      assertThat(listed).containsExactlyInAnyOrder(ofDefault, grantedOfB);
    }

    @Test
    @DisplayName("given the right in both tenants should list every row")
    void given_rightInBothTenants_should_listEveryRow() {
      // -- ARRANGE --
      Row ofDefault = new Row("row-of-default", Tenant.DEFAULT_TENANT_UUID);
      Row ofB = new Row("row-of-b", tenantB);
      grantInB();

      // -- ACT --
      List<Row> listed = listInEachTenant(bothTenants(), List.of(ofDefault, ofB), List.of());

      // -- ASSERT --
      assertThat(listed).containsExactlyInAnyOrder(ofDefault, ofB);
    }

    @Test
    @DisplayName("given a granted row the caller also reads should list it once")
    void given_grantedRowAlsoReadable_should_listItOnce() {
      // -- ARRANGE --
      Row ofDefault = new Row("row-of-default", Tenant.DEFAULT_TENANT_UUID);
      Row grantedOfB = new Row("granted-row-of-b", tenantB);

      // -- ACT --
      List<Row> listed =
          listInEachTenant(
              bothTenants(), List.of(ofDefault, grantedOfB), List.of(ofDefault, grantedOfB));

      // -- ASSERT --
      assertThat(listed).containsExactlyInAnyOrder(ofDefault, grantedOfB);
    }

    @Test
    @DisplayName(
        "given a scope naming only B should decide the rows of the ambient tenant in the ambient tenant")
    void given_scopeNamingOnlyB_should_decideAmbientRowsInAmbientTenant() {
      // -- ARRANGE --
      Row ofDefault = new Row("row-of-default", Tenant.DEFAULT_TENANT_UUID);
      Row ofB = new Row("row-of-b", tenantB);

      // -- ACT --
      List<Row> listed =
          listInEachTenant(TxCtx.forTenant(tenantB), List.of(ofDefault, ofB), List.of());

      // -- ASSERT --
      assertThat(listed).containsExactly(ofDefault);
    }

    @Test
    @DisplayName(
        "given the right only outside B should filter every default tag and B's granted tags")
    void given_rightOnlyOutsideB_should_filterEveryDefaultTagAndGrantedBTags() {
      // -- ARRANGE --
      Tags tags = seedTags();

      // -- ACT --
      List<String> read = filterInEachTenant(bothTenants(), tags);

      // -- ASSERT --
      assertThat(read)
          .containsExactlyInAnyOrder(tags.ofDefault(), tags.grantedOfDefault(), tags.grantedOfB());
    }

    @Test
    @DisplayName("given the right in both tenants should filter every tag")
    void given_rightInBothTenants_should_filterEveryTag() {
      // -- ARRANGE --
      Tags tags = seedTags();
      grantInB();

      // -- ACT --
      List<String> read = filterInEachTenant(bothTenants(), tags);

      // -- ASSERT --
      assertThat(read).containsExactlyInAnyOrderElementsOf(tags.all());
    }

    @Test
    @DisplayName(
        "given a scope naming only B should filter the tags of the ambient tenant in the ambient tenant")
    void given_scopeNamingOnlyB_should_filterAmbientTagsInAmbientTenant() {
      // -- ARRANGE --
      Tags tags = seedTags();

      // -- ACT --
      List<String> read = filterInEachTenant(TxCtx.forTenant(tenantB), tags);

      // -- ASSERT --
      assertThat(read)
          .containsExactlyInAnyOrder(tags.ofDefault(), tags.grantedOfDefault(), tags.grantedOfB());
    }
  }

  @Nested
  @DisplayName("Without the access right in the default tenant")
  @WithMockUser
  class NoRightInDefaultTenant {

    @Test
    @DisplayName("given the right only in B should list every row of B and the granted rows")
    void given_rightOnlyInB_should_listEveryBRowAndGrantedRows() {
      // -- ARRANGE --
      Row ofDefault = new Row("row-of-default", Tenant.DEFAULT_TENANT_UUID);
      Row grantedOfDefault = new Row("granted-row-of-default", Tenant.DEFAULT_TENANT_UUID);
      Row ofB = new Row("row-of-b", tenantB);
      grantInB();

      // -- ACT --
      List<Row> listed =
          listInEachTenant(
              bothTenants(), List.of(ofDefault, grantedOfDefault, ofB), List.of(grantedOfDefault));

      // -- ASSERT --
      assertThat(listed).containsExactlyInAnyOrder(ofB, grantedOfDefault);
    }

    @Test
    @DisplayName("given the right in no tenant should list the granted rows only")
    void given_rightInNoTenant_should_listGrantedRowsOnly() {
      // -- ARRANGE --
      Row ofDefault = new Row("row-of-default", Tenant.DEFAULT_TENANT_UUID);
      Row grantedOfB = new Row("granted-row-of-b", tenantB);

      // -- ACT --
      List<Row> listed =
          listInEachTenant(bothTenants(), List.of(ofDefault, grantedOfB), List.of(grantedOfB));

      // -- ASSERT --
      assertThat(listed).containsExactly(grantedOfB);
    }

    @Test
    @DisplayName("given the right only in B should filter every tag of B and the granted tags")
    void given_rightOnlyInB_should_filterEveryBTagAndGrantedTags() {
      // -- ARRANGE --
      Tags tags = seedTags();
      grantInB();

      // -- ACT --
      List<String> read = filterInEachTenant(bothTenants(), tags);

      // -- ASSERT --
      assertThat(read)
          .containsExactlyInAnyOrder(tags.ofB(), tags.grantedOfB(), tags.grantedOfDefault());
    }

    @Test
    @DisplayName("given the right in no tenant should filter the granted tags only")
    void given_rightInNoTenant_should_filterGrantedTagsOnly() {
      // -- ARRANGE --
      Tags tags = seedTags();

      // -- ACT --
      List<String> read = filterInEachTenant(bothTenants(), tags);

      // -- ASSERT --
      assertThat(read).containsExactlyInAnyOrder(tags.grantedOfDefault(), tags.grantedOfB());
    }
  }

  /** A row of a list, with the tenant it belongs to. */
  private record Row(String id, String tenantId) {}

  /** Tags of both tenants, two of which the caller holds a grant on. */
  private record Tags(String ofDefault, String grantedOfDefault, String ofB, String grantedOfB) {

    List<String> all() {
      return List.of(ofDefault, grantedOfDefault, ofB, grantedOfB);
    }

    List<String> granted() {
      return List.of(grantedOfDefault, grantedOfB);
    }
  }

  private List<Row> listInEachTenant(TxCtx ctx, List<Row> all, List<Row> granted) {
    return permissionService.listInEachTenant(
        ctx, accessRight(), () -> all, () -> granted, Row::tenantId, Row::id);
  }

  private List<String> filterInEachTenant(TxCtx ctx, Tags tags) {
    Function<Boolean, Specification<Tag>> rows =
        readsEveryRow ->
            readsEveryRow ? (root, query, cb) -> cb.conjunction() : idIn(tags.granted());
    Specification<Tag> readable = permissionService.filterInEachTenant(ctx, accessRight(), rows);
    List<String> read = new ArrayList<>();
    StreamSupport.stream(tagRepository.findAll(readable.and(idIn(tags.all()))).spliterator(), false)
        .map(Tag::getId)
        .forEach(read::add);
    return read;
  }

  private BooleanSupplier accessRight() {
    User caller = userService.currentUser();
    return () -> caller.getCapabilities().contains(Capability.ACCESS_ASSESSMENT);
  }

  private Tags seedTags() {
    return new Tags(
        tagIn(Tenant.DEFAULT_TENANT_UUID, "tenant-decision-default"),
        tagIn(Tenant.DEFAULT_TENANT_UUID, "tenant-decision-granted-default"),
        tagIn(tenantB, "tenant-decision-b"),
        tagIn(tenantB, "tenant-decision-granted-b"));
  }

  private String tagIn(String tenantId, String name) {
    Tag tag = TagFixture.getTagWithText(name);
    tag.setTenant(new Tenant(tenantId));
    return tagRepository.save(tag).getId();
  }

  private static Specification<Tag> idIn(Collection<String> ids) {
    return (root, query, cb) -> root.get("id").in(ids);
  }

  private TxCtx bothTenants() {
    return TxCtx.forTenants(Set.of(Tenant.DEFAULT_TENANT_UUID, tenantB));
  }

  private void grantInB() {
    tenantHelper.grantCapabilitiesInTenant(tenantB, Set.of(Capability.ACCESS_ASSESSMENT));
  }
}
