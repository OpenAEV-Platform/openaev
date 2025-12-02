package io.openaev.rest.domain.service;

import static org.junit.jupiter.api.Assertions.*;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Domain;
import io.openaev.rest.domain.DomainService;
import io.openaev.rest.domain.enums.DefaultDomain;
import jakarta.transaction.Transactional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Transactional
@SpringBootTest
public class DomainServiceTest extends IntegrationTest {

  @Autowired private DomainService domainService;

  @Test
  @DisplayName("Upsert with null parameter should not fail")
  void upsertWithNullShouldNotFail() {
    Set<Domain> domains = this.domainService.upserts(null);

    assertTrue(domains.isEmpty());
  }

  @Test
  @DisplayName("Set should be merged")
  void setShouldBeMerged() {
    Set<Domain> domainsA = Set.of(DefaultDomain.CLOUD.getDomain());
    Set<Domain> domainsB = Set.of(DefaultDomain.ENDPOINT.getDomain());

    Set<Domain> domains = this.domainService.mergeDomains(domainsA, domainsB);

    assertFalse(domains.isEmpty());
    assertEquals(2, domains.size());
    assertTrue(domains.contains(DefaultDomain.CLOUD.getDomain()));
    assertTrue(domains.contains(DefaultDomain.ENDPOINT.getDomain()));
  }

  @Test
  @DisplayName("Set should not be merged, because existing is null")
  void setShouldNotBeMergedBecauseExistingIsNull() {
    Set<Domain> domainsB = Set.of(DefaultDomain.ENDPOINT.getDomain());

    Set<Domain> domains = this.domainService.mergeDomains(null, domainsB);

    assertFalse(domains.isEmpty());
    assertEquals(1, domains.size());
    assertTrue(domains.contains(DefaultDomain.ENDPOINT.getDomain()));
  }

  @Test
  @DisplayName("Set should not be merged, because existing is empty")
  void setShouldNotBeMergedBecauseExistingIsEmpty() {
    Set<Domain> domainsB = Set.of(DefaultDomain.ENDPOINT.getDomain());

    Set<Domain> domains = this.domainService.mergeDomains(Set.of(), domainsB);

    assertFalse(domains.isEmpty());
    assertEquals(1, domains.size());
    assertTrue(domains.contains(DefaultDomain.ENDPOINT.getDomain()));
  }

  @Test
  @DisplayName("Set should not be merged, because existing is to classify")
  void setShouldNotBeMergedBecauseExistingIsToClassify() {
    Set<Domain> domainsA = Set.of(DefaultDomain.TOCLASSIFY.getDomain());
    Set<Domain> domainsB = Set.of(DefaultDomain.ENDPOINT.getDomain());

    Set<Domain> domains = this.domainService.mergeDomains(domainsA, domainsB);

    assertFalse(domains.isEmpty());
    assertEquals(1, domains.size());
    assertTrue(domains.contains(DefaultDomain.ENDPOINT.getDomain()));
  }

  @Test
  @DisplayName("Should find Endpoint because no any keyword match")
  void shouldFindEndpointBecauseNoAnyKeywordMatch() {
    Set<Domain> domains =
        this.domainService.findDomainByNameAndDescription("123456789", "123456789");

    assertFalse(domains.isEmpty());
    assertEquals(1, domains.size());
    assertTrue(domains.contains(DefaultDomain.ENDPOINT.getDomain()));
  }

  @Test
  @DisplayName("Should find all domains because no all keyword match")
  void shouldFindAllDomainsBecauseNoAllKeywordMatch() {
    Set<Domain> domains =
        this.domainService.findDomainByNameAndDescription(
            "lsass lateral movement sql injection spearphishing attachment",
            "exfiltrat domain fronting aws");

    assertFalse(domains.isEmpty());
    assertEquals(7, domains.size());
    assertTrue(domains.contains(DefaultDomain.ENDPOINT.getDomain()));
    assertTrue(domains.contains(DefaultDomain.NETWORK.getDomain()));
    assertTrue(domains.contains(DefaultDomain.WEB_APP.getDomain()));
    assertTrue(domains.contains(DefaultDomain.EMAIL_INFILTRATION.getDomain()));
    assertTrue(domains.contains(DefaultDomain.DATA_EXFILTRATION.getDomain()));
    assertTrue(domains.contains(DefaultDomain.URL_FILTERING.getDomain()));
    assertTrue(domains.contains(DefaultDomain.CLOUD.getDomain()));
  }
}
