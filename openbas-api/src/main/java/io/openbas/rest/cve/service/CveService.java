package io.openbas.rest.cve.service;

import static io.openbas.helper.StreamHelper.fromIterable;
import static io.openbas.utils.pagination.PaginationUtils.buildPaginationJPA;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openbas.config.cache.LicenseCacheManager;
import io.openbas.database.model.Collector;
import io.openbas.database.model.Cve;
import io.openbas.database.model.Cwe;
import io.openbas.database.repository.CveRepository;
import io.openbas.database.repository.CweRepository;
import io.openbas.ee.Ee;
import io.openbas.rest.collector.service.CollectorService;
import io.openbas.rest.cve.form.*;
import io.openbas.rest.exception.ElementNotFoundException;
import io.openbas.utils.pagination.SearchPaginationInput;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        cveRepository.findAllByExternalIdIn(externalIds.stream().toList()).stream()
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

  public List<Cve> findAllByIdsOrThrowIfMissing(final Set<String> vulnIds) {
    List<Cve> vulns = fromIterable(this.cveRepository.findAllById(vulnIds));
    List<String> missingIds =
        vulnIds.stream()
            .filter(id -> !vulns.stream().map(Cve::getId).toList().contains(id))
            .toList();
    if (!missingIds.isEmpty()) {
      throw new ElementNotFoundException(
          String.format("Missing vulnerabilities: %s", String.join(", ", missingIds)));
    }
    return vulns;
  }

  public Cve findByExternalId(String externalId) {
    return cveRepository
        .findByExternalId(externalId)
        .orElseThrow(() -> new ElementNotFoundException(CVE_NOT_FOUND_MSG + externalId));
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
}
