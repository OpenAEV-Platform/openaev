package io.openaev.rest.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Domain;
import io.openaev.rest.domain.DomainService;
import io.openaev.rest.domain.enums.PresetDomain;
import io.openaev.utils.fixtures.ColourFixture;
import io.openaev.utils.fixtures.DomainFixture;
import io.openaev.utils.fixtures.composers.DomainComposer;
import jakarta.transaction.Transactional;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Transactional
@SpringBootTest
public class DomainServiceTest extends IntegrationTest {

  @Autowired private DomainService domainService;
  @Autowired private DomainComposer domainComposer;

  @Test
  @DisplayName("Upsert DTOs with null parameter should not fail")
  void upsertWithNullShouldNotFail() {
    Set<Domain> domains = this.domainService.upserts(null);
    assertTrue(domains.isEmpty());
  }

  @Test
  @DisplayName("Upsert entities with null parameter should not fail")
  void upsertEntitiesWithNullShouldNotFail() {
    Set<Domain> domains = this.domainService.upsertDomainEntities(null);
    assertTrue(domains.isEmpty());
  }

  @Test
  @DisplayName("Upsert entities with set partially existing")
  void upsertEntitiesWithSetPartiallyExisting() {
    Set<Domain> domains = new HashSet<>();
    for (int i = 0; i < 3; i++) {
      domains.add(domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get());
    }
    for (int i = 0; i < 3; i++) {
      // don't persist those
      domains.add(DomainFixture.getRandomDomain());
    }

    Set<Domain> upserted = this.domainService.upsertDomainEntities(domains);

    assertThat(upserted).hasSameElementsAs(domains);
  }

  @Test
  @DisplayName("Upsert existing entities prevents changing colour")
  void upsertExistingEntitiesPreventsCHangingColour() {
    Map<String, Domain> domains = new HashMap<>();
    for (int i = 0; i < 3; i++) {
      Domain d = DomainFixture.getRandomDomain();
      domains.put(d.getName(), domainComposer.forDomain(d).persist().get());
    }

    Set<Domain> modified =
        domains.values().stream()
            .map(
                domain ->
                    DomainFixture.getDomainWithNameAndColour(
                        domain.getName(), ColourFixture.getRandomRgbString()))
            .collect(Collectors.toSet());

    Set<Domain> upserted = this.domainService.upsertDomainEntities(modified);

    assertThat(upserted)
        .hasSameElementsAs(domains.values())
        .satisfies(
            set ->
                set.forEach(
                    domain ->
                        assertThat(domain.getColor())
                            .isEqualTo(domains.get(domain.getName()).getColor())));
  }

  @Test
  @DisplayName("Set should be merged")
  void setShouldBeMerged() {
    Set<Domain> domainsA = Set.of(PresetDomain.CLOUD);
    Set<Domain> domainsB = Set.of(PresetDomain.ENDPOINT);

    Set<Domain> domains = this.domainService.mergeDomains(domainsA, domainsB);

    assertThat(domains).containsExactlyInAnyOrder(PresetDomain.ENDPOINT, PresetDomain.CLOUD);
  }

  @Test
  @DisplayName("Set should not be merged, because existing is null")
  void setShouldNotBeMergedBecauseExistingIsNull() {
    Set<Domain> domainsB = Set.of(PresetDomain.ENDPOINT);

    Set<Domain> domains = this.domainService.mergeDomains(null, domainsB);

    assertThat(domains).containsExactly(PresetDomain.ENDPOINT);
  }

  @Test
  @DisplayName("Set should not be merged, because existing is empty")
  void setShouldNotBeMergedBecauseExistingIsEmpty() {
    Set<Domain> domainsB = Set.of(PresetDomain.ENDPOINT);

    Set<Domain> domains = this.domainService.mergeDomains(Set.of(), domainsB);

    assertThat(domains).containsExactly(PresetDomain.ENDPOINT);
  }

  @Test
  @DisplayName("Set should not be merged, because existing is to classify")
  void setShouldNotBeMergedBecauseExistingIsToClassify() {
    Set<Domain> domainsA = Set.of(PresetDomain.TOCLASSIFY);
    Set<Domain> domainsB = Set.of(PresetDomain.ENDPOINT);

    Set<Domain> domains = this.domainService.mergeDomains(domainsA, domainsB);

    assertThat(domains).containsExactly(PresetDomain.ENDPOINT);
  }

  @Test
  @DisplayName("Should find Endpoint because no any keyword match")
  void shouldFindEndpointBecauseNoAnyKeywordMatch() {
    Set<Domain> domains = this.domainService.findDomainByNameAndDescription("123456789");

    assertThat(domains).containsExactly(PresetDomain.ENDPOINT);
  }

  @Test
  @DisplayName("Should find all domains because no all keyword match")
  void shouldFindAllDomainsBecauseNoAllKeywordMatch() {
    Set<Domain> domains =
        this.domainService.findDomainByNameAndDescription(
            "network web email exfiltrat bitsadmin aws");

    assertThat(domains)
        .containsExactlyInAnyOrder(
            PresetDomain.EMAIL_INFILTRATION,
            PresetDomain.DATA_EXFILTRATION,
            PresetDomain.CLOUD,
            PresetDomain.ENDPOINT,
            PresetDomain.URL_FILTERING,
            PresetDomain.NETWORK,
            PresetDomain.WEB_APP);
  }
}
