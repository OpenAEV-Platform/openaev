package io.openaev.database.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class InjectorContractTest {

  private static Injector injector(String id, String type, String tenantId) {
    Injector injector = new Injector();
    injector.setId(id);
    injector.setName(id);
    injector.setType(type);
    injector.setTenantId(tenantId);
    return injector;
  }

  private static InjectorContract contractInTenant(String tenantId) {
    InjectorContract contract = new InjectorContract();
    contract.setTenant(new Tenant(tenantId));
    return contract;
  }

  @Test
  @DisplayName("addInjector rejects an injector from a different tenant than the contract")
  void addInjector_rejects_cross_tenant_injector() {
    // The join row's tenant is derived from the injector, so linking a foreign-tenant injector
    // would
    // silently write the link into the wrong tenant. The invariant must fail loud instead.
    InjectorContract contract = contractInTenant("tenant-A");

    assertThatThrownBy(() -> contract.addInjector(injector("email", "email", "tenant-B")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenant");
    assertThat(contract.getInjectors()).isEmpty();
  }

  @Test
  @DisplayName("addInjector accepts an injector from the same tenant as the contract")
  void addInjector_accepts_same_tenant_injector() {
    InjectorContract contract = contractInTenant("tenant-A");

    contract.addInjector(injector("email", "email", "tenant-A"));

    assertThat(contract.getInjectors()).extracting(Injector::getId).containsExactly("email");
  }

  @Nested
  @DisplayName(
      "updatedAt sync bumps only happen on real association changes, so no-op collector upserts"
          + " do not force an UPDATE and an SSE restream (#6778)")
  class UpdatedAtSyncBump {

    private static final Instant SENTINEL = Instant.EPOCH;

    private static Tag tag(String id) {
      Tag tag = new Tag();
      tag.setId(id);
      tag.setName(id);
      return tag;
    }

    private static AttackPattern attackPattern(String id) {
      AttackPattern attackPattern = new AttackPattern();
      attackPattern.setId(id);
      return attackPattern;
    }

    private static Vulnerability vulnerability(String id) {
      Vulnerability vulnerability = new Vulnerability();
      vulnerability.setId(id);
      return vulnerability;
    }

    private static Domain domainWithId(String id) {
      Domain domain = new Domain();
      domain.setId(id);
      domain.setName(id);
      return domain;
    }

    private static InjectorContract contractAtSentinel() {
      InjectorContract contract = new InjectorContract();
      contract.setTags(new HashSet<>(Set.of(tag("t1"), tag("t2"))));
      contract.setAttackPatterns(
          new ArrayList<>(List.of(attackPattern("a1"), attackPattern("a2"))));
      contract.setVulnerabilities(new HashSet<>(Set.of(vulnerability("v1"))));
      contract.setDomains(new HashSet<>(Set.of(domainWithId("d1"))));
      contract.setUpdatedAt(SENTINEL);
      return contract;
    }

    @Test
    @DisplayName("setTags with the same tag ids does not bump updatedAt")
    void setTags_same_ids_does_not_bump() {
      InjectorContract contract = contractAtSentinel();

      contract.setTags(new HashSet<>(Set.of(tag("t2"), tag("t1"))));

      assertThat(contract.getUpdatedAt()).isEqualTo(SENTINEL);
    }

    @Test
    @DisplayName("setTags with different tag ids bumps updatedAt")
    void setTags_different_ids_bumps() {
      InjectorContract contract = contractAtSentinel();

      contract.setTags(new HashSet<>(Set.of(tag("t1"), tag("t3"))));

      assertThat(contract.getUpdatedAt()).isAfter(SENTINEL);
    }

    @Test
    @DisplayName("setAttackPatterns with the same ids in a different order does not bump updatedAt")
    void setAttackPatterns_same_ids_does_not_bump() {
      InjectorContract contract = contractAtSentinel();

      contract.setAttackPatterns(
          new ArrayList<>(List.of(attackPattern("a2"), attackPattern("a1"))));

      assertThat(contract.getUpdatedAt()).isEqualTo(SENTINEL);
    }

    @Test
    @DisplayName("setAttackPatterns with an added attack pattern bumps updatedAt")
    void setAttackPatterns_different_ids_bumps() {
      InjectorContract contract = contractAtSentinel();

      contract.setAttackPatterns(
          new ArrayList<>(List.of(attackPattern("a1"), attackPattern("a2"), attackPattern("a3"))));

      assertThat(contract.getUpdatedAt()).isAfter(SENTINEL);
    }

    @Test
    @DisplayName("setAttackPatterns clearing existing attack patterns bumps updatedAt")
    void setAttackPatterns_cleared_bumps() {
      InjectorContract contract = contractAtSentinel();

      contract.setAttackPatterns(new ArrayList<>());

      assertThat(contract.getUpdatedAt()).isAfter(SENTINEL);
    }

    @Test
    @DisplayName("setVulnerabilities with the same ids does not bump updatedAt")
    void setVulnerabilities_same_ids_does_not_bump() {
      InjectorContract contract = contractAtSentinel();

      contract.setVulnerabilities(new HashSet<>(Set.of(vulnerability("v1"))));

      assertThat(contract.getUpdatedAt()).isEqualTo(SENTINEL);
    }

    @Test
    @DisplayName("setVulnerabilities with different ids bumps updatedAt")
    void setVulnerabilities_different_ids_bumps() {
      InjectorContract contract = contractAtSentinel();

      contract.setVulnerabilities(new HashSet<>(Set.of(vulnerability("v2"))));

      assertThat(contract.getUpdatedAt()).isAfter(SENTINEL);
    }

    @Test
    @DisplayName("setDomains with the same ids does not bump updatedAt")
    void setDomains_same_ids_does_not_bump() {
      InjectorContract contract = contractAtSentinel();

      contract.setDomains(new HashSet<>(Set.of(domainWithId("d1"))));

      assertThat(contract.getUpdatedAt()).isEqualTo(SENTINEL);
    }

    @Test
    @DisplayName("setDomains with different ids bumps updatedAt")
    void setDomains_different_ids_bumps() {
      // A real domain change must be visible to updatedAt-driven logic (SSE restream, engine
      // indexing cursor), exactly like the other association setters.
      InjectorContract contract = contractAtSentinel();

      contract.setDomains(new HashSet<>(Set.of(domainWithId("d2"))));

      assertThat(contract.getUpdatedAt()).isAfter(SENTINEL);
    }

    @Test
    @DisplayName("first association assignment on a new contract bumps updatedAt")
    void first_assignment_bumps() {
      InjectorContract contract = new InjectorContract();
      contract.setUpdatedAt(SENTINEL);

      contract.setTags(new HashSet<>(Set.of(tag("t1"))));

      assertThat(contract.getUpdatedAt()).isAfter(SENTINEL);
    }

    @Test
    @DisplayName(
        "replacing a transient tag (no id yet) with another transient tag bumps updatedAt:"
            + " {null} ids are never considered equal")
    void transient_entities_are_never_considered_equal() {
      InjectorContract contract = new InjectorContract();
      contract.setTags(new HashSet<>(Set.of(new Tag())));
      contract.setUpdatedAt(SENTINEL);

      contract.setTags(new HashSet<>(Set.of(new Tag())));

      assertThat(contract.getUpdatedAt()).isAfter(SENTINEL);
    }
  }

  @Nested
  @DisplayName(
      "an unchanged association keeps the STORED collection instance, so a no-op upsert never"
          + " dereferences the persistent collection and never rewrites its join rows")
  class AssociationInstanceIsPreservedWhenUnchanged {

    private static Domain domain(String id) {
      Domain domain = new Domain();
      domain.setId(id);
      domain.setName(id);
      return domain;
    }

    private static Tag tagWithId(String id) {
      Tag tag = new Tag();
      tag.setId(id);
      tag.setName(id);
      return tag;
    }

    @Test
    @DisplayName("setDomains with the same domain ids keeps the stored collection")
    void setDomains_same_ids_keeps_stored_collection() {
      // An injector re-registering every ~40s hands over a freshly built Set holding the very same
      // domains. Assigning it would dereference the persistent collection, and Hibernate deletes
      // then re-inserts every join row of a dereferenced collection - on every single cycle.
      InjectorContract contract = new InjectorContract();
      Set<Domain> stored = new HashSet<>(Set.of(domain("d1"), domain("d2")));
      contract.setDomains(stored);

      contract.setDomains(new HashSet<>(Set.of(domain("d2"), domain("d1"))));

      assertThat(contract.getDomains()).isSameAs(stored);
    }

    @Test
    @DisplayName("setDomains with different domain ids stores the incoming collection")
    void setDomains_different_ids_stores_incoming() {
      InjectorContract contract = new InjectorContract();
      contract.setDomains(new HashSet<>(Set.of(domain("d1"))));
      Set<Domain> incoming = new HashSet<>(Set.of(domain("d1"), domain("d2")));

      contract.setDomains(incoming);

      assertThat(contract.getDomains()).isSameAs(incoming);
    }

    @Test
    @DisplayName("setTags with the same tag ids keeps the stored collection")
    void setTags_same_ids_keeps_stored_collection() {
      InjectorContract contract = new InjectorContract();
      Set<Tag> stored = new HashSet<>(Set.of(tagWithId("t1")));
      contract.setTags(stored);

      contract.setTags(new HashSet<>(Set.of(tagWithId("t1"))));

      assertThat(contract.getTags()).isSameAs(stored);
    }

    private static AttackPattern attackPatternWithId(String id) {
      AttackPattern attackPattern = new AttackPattern();
      attackPattern.setId(id);
      return attackPattern;
    }

    private static Vulnerability vulnerabilityWithId(String id) {
      Vulnerability vulnerability = new Vulnerability();
      vulnerability.setId(id);
      return vulnerability;
    }

    @Test
    @DisplayName("setAttackPatterns with the same ids keeps the stored collection")
    void setAttackPatterns_same_ids_keeps_stored_collection() {
      InjectorContract contract = new InjectorContract();
      List<AttackPattern> stored = new ArrayList<>(List.of(attackPatternWithId("a1")));
      contract.setAttackPatterns(stored);

      contract.setAttackPatterns(new ArrayList<>(List.of(attackPatternWithId("a1"))));

      assertThat(contract.getAttackPatterns()).isSameAs(stored);
    }

    @Test
    @DisplayName("setVulnerabilities with the same ids keeps the stored collection")
    void setVulnerabilities_same_ids_keeps_stored_collection() {
      InjectorContract contract = new InjectorContract();
      Set<Vulnerability> stored = new HashSet<>(Set.of(vulnerabilityWithId("v1")));
      contract.setVulnerabilities(stored);

      contract.setVulnerabilities(new HashSet<>(Set.of(vulnerabilityWithId("v1"))));

      assertThat(contract.getVulnerabilities()).isSameAs(stored);
    }
  }
}
