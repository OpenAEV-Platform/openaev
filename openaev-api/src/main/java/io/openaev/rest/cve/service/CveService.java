package io.openaev.rest.cve.service;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.*;
import io.openaev.database.repository.CveRepository;
import io.openaev.database.repository.CweRepository;
import io.openaev.ee.Ee;
import io.openaev.rest.collector.service.CollectorService;
import io.openaev.rest.cve.form.*;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CveService {

  private static final String CVE_NOT_FOUND_MSG = "CVE not found with id: ";

  private final CollectorService collectorService;
  private final Ee eeService;

  private final CveRepository cveRepository;
  private final CweRepository cweRepository;
  private final LicenseCacheManager licenseCacheManager;

  @Resource protected ObjectMapper mapper;

  public Cve createCve(final @Valid CveCreateInput input) {
    final Cve cve = new Cve();
    if (eeService.isEnterpriseLicenseInactive(licenseCacheManager.getEnterpriseEditionInfo())) {
      input.setRemediation(null);
    }
    cve.setUpdateAttributes(input);
    updateCweAssociations(cve, input.getCwes());
    return cveRepository.save(cve);
  }

  private List<Cve> batchUpsertCves(List<CveCreateInput> cveInputs) {
    // Extract external IDs
    Set<String> externalIds =
        cveInputs.stream().map(CveCreateInput::getExternalId).collect(Collectors.toSet());

    // Batch fetch existing CVEs
    Map<String, Cve> existingCvesMap =
        getVulnerabilitiesByExternalIds(externalIds).stream()
            .collect(Collectors.toMap(Cve::getExternalId, Function.identity()));

    // Process with pre-fetched data
    List<Cve> cves =
        cveInputs.stream()
            .map(
                cveInput -> {
                  Cve cve = existingCvesMap.getOrDefault(cveInput.getExternalId(), new Cve());
                  cve.setUpdateAttributes(cveInput);
                  updateCweAssociations(cve, cveInput.getCwes());
                  return cve;
                })
            .toList();

    return fromIterable(cveRepository.saveAll(cves));
  }

  private void updateCollectorStateFromCVEBulkInsertInput(
      Collector collector, @NotNull CVEBulkInsertInput inputs) {
    ObjectNode collectorNewState = mapper.createObjectNode();
    collectorNewState.put(
        "last_modified_date_fetched", inputs.getLastModifiedDateFetched().toString());
    collectorNewState.put("last_index", inputs.getLastIndex().toString());
    collectorNewState.put("initial_dataset_completed", inputs.getInitialDatasetCompleted());
    this.collectorService.updateCollectorState(collector, collectorNewState);
  }

  @Transactional(rollbackFor = Exception.class)
  public void bulkUpsertCVEs(@NotNull CVEBulkInsertInput inputs) {
    Collector collector = this.collectorService.collector(inputs.getSourceIdentifier());

    List<Cve> cves = this.batchUpsertCves(inputs.getCves());
    this.updateCollectorStateFromCVEBulkInsertInput(collector, inputs);

    log.info(
        "Bulk upsert {} CVEs with last modified date fetched: {}",
        cves.size(),
        inputs.getLastModifiedDateFetched());
  }

  public Page<Cve> searchCves(final @Valid SearchPaginationInput input) {
    return buildPaginationJPA(
        (Specification<Cve> spec, Pageable pageable) -> cveRepository.findAll(spec, pageable),
        input,
        Cve.class);
  }

  public Cve updateCve(final String cveId, final @Valid CveUpdateInput input) {
    final Cve existingCve = findById(cveId);
    if (eeService.isEnterpriseLicenseInactive(licenseCacheManager.getEnterpriseEditionInfo())) {
      input.setRemediation(null);
      BeanUtils.copyProperties(input, existingCve, "remediation");
    } else {
      existingCve.setUpdateAttributes(input);
    }
    updateCweAssociations(existingCve, input.getCwes());
    return cveRepository.save(existingCve);
  }

  public Cve findById(final String cveId) {
    return cveRepository
        .findById(cveId)
        .orElseThrow(() -> new ElementNotFoundException(CVE_NOT_FOUND_MSG + cveId));
  }

  public Set<Cve> findAllByIdsOrThrowIfMissing(final Set<String> vulnIds) {
    Set<Cve> vulns = this.cveRepository.getAllByIdInIgnoreCase(vulnIds);
    throwIfMissing(vulnIds, vulns, Cve::getId);
    return vulns;
  }

  public Cve findByExternalId(String externalId) {
    return cveRepository
        .findByExternalId(externalId)
        .orElseThrow(() -> new ElementNotFoundException(CVE_NOT_FOUND_MSG + externalId));
  }

  public Set<Cve> findAllByExternalIdsOrThrowIfMissing(final Set<String> vulnIds) {
    Set<Cve> vulns = getVulnerabilitiesByExternalIds(vulnIds);
    throwIfMissing(vulnIds, vulns, Cve::getExternalId);
    return vulns;
  }

  private void throwIfMissing(
      Set<String> requiredIds,
      Set<Cve> fetchedVulnerabilities,
      Function<? super Cve, String> getId) {

    List<String> fetchedIdLower =
        fetchedVulnerabilities.stream().map(vuln -> getId.apply(vuln).toLowerCase()).toList();

    List<String> missingIds =
        requiredIds.stream().filter(id -> !fetchedIdLower.contains(id.toLowerCase())).toList();

    if (!missingIds.isEmpty()) {
      throw new ElementNotFoundException(
          String.format("Missing vulnerabilities: %s", String.join(", ", missingIds)));
    }
  }

  public void deleteById(final String cveId) {
    cveRepository.deleteById(cveId);
  }

  private void updateCweAssociations(Cve cve, List<CweInput> cweInputs) {
    if (cweInputs == null || cweInputs.isEmpty()) {
      cve.setCwes(Collections.emptyList());
      return;
    }

    List<Cwe> cweEntities =
        cweInputs.stream()
            .map(
                input ->
                    cweRepository
                        .findByExternalId(input.getExternalId())
                        .orElseGet(
                            () -> {
                              Cwe newCwe = new Cwe();
                              newCwe.setExternalId(input.getExternalId());
                              newCwe.setSource(input.getSource());
                              return cweRepository.save(newCwe);
                            }))
            .collect(Collectors.toList());

    cve.setCwes(cweEntities);
  }

  /**
   * Resolves external Vulnerability Refs from a set of vulnerability {@link Cve} entities.
   *
   * @param externalIds set vulnerability Refs
   * @return set of resolved vulnerability entities
   */
  public Set<Cve> getVulnerabilitiesByExternalIds(Set<String> externalIds) {
    if (externalIds.isEmpty()) {
      return Collections.emptySet();
    }
    return this.cveRepository.getAllByExternalIdInIgnoreCase(externalIds);
  }
}
