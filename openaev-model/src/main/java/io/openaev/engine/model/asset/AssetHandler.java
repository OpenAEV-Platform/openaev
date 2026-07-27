package io.openaev.engine.model.asset;

import static io.openaev.engine.EsUtils.buildRestrictions;

import io.openaev.database.raw.RawIndexedAsset;
import io.openaev.database.repository.AssetRepository;
import io.openaev.engine.Handler;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AssetHandler implements Handler<EsAsset> {

  private AssetRepository assetRepository;

  @Autowired
  public void setAssetRepository(AssetRepository assetRepository) {
    this.assetRepository = assetRepository;
  }

  @Override
  public List<EsAsset> fetch(Instant from, int limit) {
    Instant queryFrom = from != null ? from : Instant.ofEpochMilli(0);
    List<RawIndexedAsset> forIndexing = assetRepository.findForIndexing(queryFrom, limit);
    return forIndexing.stream()
        .map(
            asset -> {
              EsAsset esAsset = new EsAsset();
              // Base
              esAsset.setBase_id(asset.getAsset_id());
              esAsset.setBase_representative(asset.getAsset_name());
              esAsset.setBase_created_at(asset.getAsset_created_at());
              esAsset.setBase_updated_at(asset.getAsset_indexed_at());
              // not sure what to put here, if anything
              esAsset.setBase_restrictions(buildRestrictions(asset.getAsset_id()));

              esAsset.setAsset_name(asset.getAsset_name());
              esAsset.setAsset_description(asset.getAsset_description());
              esAsset.setAsset_external_reference(asset.getAsset_external_reference());
              esAsset.setAsset_category(asset.getAsset_category());
              esAsset.setAsset_ips(asset.getAsset_ips());
              esAsset.setAsset_hostname(asset.getAsset_hostname());
              esAsset.setAsset_mac_addresses(asset.getAsset_mac_addresses());
              esAsset.setAsset_seen_ip(asset.getAsset_seen_ip());
              esAsset.setEndpoint_platform(asset.getEndpoint_platform());
              esAsset.setEndpoint_arch(asset.getEndpoint_arch());
              esAsset.setEndpoint_is_eol(asset.getEndpoint_is_eol());
              // Dependencies (see base_dependencies in EsBase)
              esAsset.setBase_tenant_side(asset.getTenant_id());
              if (asset.getAsset_findings() != null && !asset.getAsset_findings().isEmpty()) {
                esAsset.setBase_findings_side(asset.getAsset_findings());
              } else {
                esAsset.setBase_findings_side(Set.of());
              }
              if (asset.getAsset_tags() != null && !asset.getAsset_tags().isEmpty()) {
                esAsset.setBase_tags_side(asset.getAsset_tags());
              } else {
                esAsset.setBase_tags_side(Set.of());
              }
              if (asset.getAsset_exercises() != null && !asset.getAsset_exercises().isEmpty()) {
                esAsset.setBase_simulation_side(asset.getAsset_exercises());
              } else {
                esAsset.setBase_simulation_side(Set.of());
              }
              if (asset.getAsset_scenarios() != null && !asset.getAsset_scenarios().isEmpty()) {
                esAsset.setBase_scenario_side(asset.getAsset_scenarios());
              } else {
                esAsset.setBase_scenario_side(Set.of());
              }
              return esAsset;
            })
        .toList();
  }
}
