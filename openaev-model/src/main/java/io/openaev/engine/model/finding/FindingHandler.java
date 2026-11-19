package io.openaev.engine.model.finding;

import static io.openaev.engine.EsUtils.buildRestrictions;

import io.openaev.database.raw.RawFindingIndexing;
import io.openaev.database.repository.FindingRepository;
import io.openaev.engine.Handler;
import io.openaev.utils.SensitiveValueMaskingUtils;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FindingHandler implements Handler<EsFinding> {

  private FindingRepository findingRepository;

  @Autowired
  public void setFindingRepository(FindingRepository findingRepository) {
    this.findingRepository = findingRepository;
  }

  @Override
  public List<EsFinding> fetch(Instant from, int limit) {
    return fetch(from, null, limit);
  }

  /**
   * Keyset-paged: findings sharing one {@code finding_updated_at} are common (an upsert stamps a
   * whole transaction with {@code now()}), and a timestamp-only cursor skips the part of such a
   * group beyond a full batch - for good, since the incremental indexing never revisits an
   * unchanged finding, and again on every re-feed.
   */
  @Override
  public List<EsFinding> fetch(Instant from, String fromId, int limit) {
    Instant queryFrom = from != null ? from : Instant.ofEpochMilli(0);
    List<RawFindingIndexing> forIndexing =
        findingRepository.findForIndexing(queryFrom, fromId, limit);
    return forIndexing.stream().map(FindingHandler::toEsFinding).toList();
  }

  @Override
  public boolean isKeysetPaged() {
    return true;
  }

  /**
   * Maps an indexing row to its search document.
   *
   * <p>The value is indexed in its <b>displayed</b> form, the stored {@code finding_value_display}:
   * dashboards, widgets and reports read the documents as they are, and their filters and search
   * run on them, so indexing the cleartext would both show the secret and let a {@code contains}
   * filter guess it character by character.
   */
  private static EsFinding toEsFinding(RawFindingIndexing finding) {
    String displayedValue = displayedValueOf(finding);
    EsFinding esFinding = new EsFinding();
    // Base
    esFinding.setBase_id(finding.getFinding_id());
    esFinding.setBase_representative(displayedValue);
    esFinding.setBase_created_at(finding.getFinding_created_at());
    esFinding.setBase_updated_at(finding.getFinding_updated_at());
    esFinding.setBase_restrictions(buildRestrictions(toArray(finding.getScenario_ids())));
    esFinding.setBase_tenant_side(finding.getTenant_id());
    // Specific
    esFinding.setFinding_type(finding.getFinding_type());
    esFinding.setFinding_field(orEmpty(finding.getFinding_fields()));
    esFinding.setFinding_value(displayedValue);
    esFinding.setFinding_masking_version(SensitiveValueMaskingUtils.MASKING_VERSION);
    // Dependencies (see base_dependencies in EsBase): only what deletes the finding with it, the
    // asset or the user of its location. Its injects, simulations and scenarios are sides:
    // deleting one leaves it in place.
    esFinding.setBase_dependencies(
        Stream.of(finding.getFinding_location_asset_id(), finding.getFinding_location_user_id())
            .filter(Objects::nonNull)
            .toList());
    esFinding.setBase_inject_side(orEmpty(finding.getInject_ids()));
    esFinding.setBase_simulation_side(orEmpty(finding.getSimulation_ids()));
    esFinding.setBase_scenario_side(orEmpty(finding.getScenario_ids()));
    String locationAssetId = finding.getFinding_location_asset_id();
    esFinding.setBase_endpoint_side(locationAssetId != null ? Set.of(locationAssetId) : Set.of());
    return esFinding;
  }

  private static Set<String> orEmpty(Set<String> values) {
    return values != null ? values : Set.of();
  }

  private static String[] toArray(Set<String> values) {
    return orEmpty(values).toArray(String[]::new);
  }

  /**
   * The value as the platform displays it, read from {@code finding_value_display}: the index holds
   * exactly what the Findings page shows and what its search and filters match (ADR-017).
   *
   * <p>A row whose column is still empty - written by a pod of the previous version during a
   * rolling deploy - is masked here from the cleartext, with the same fail-closed rule as every
   * writer of the column: a cleartext secret must never reach the index, and a single such row must
   * never fail the whole indexing batch.
   */
  private static String displayedValueOf(RawFindingIndexing finding) {
    String stored = finding.getFinding_value_display();
    return stored != null
        ? stored
        : SensitiveValueMaskingUtils.maskIfNeededFailClosed(
            finding.getFinding_type(), finding.getFinding_value());
  }
}
