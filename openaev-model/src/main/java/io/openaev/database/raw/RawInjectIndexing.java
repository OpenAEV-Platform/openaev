package io.openaev.database.raw;

import java.time.Instant;
import java.util.Set;

public interface RawInjectIndexing {

  String getInject_id();

  String getInject_title();

  Instant getInject_created_at();

  Instant getInject_updated_at();

  String getInject_injector_contract();

  Instant getInjector_contract_updated_at();

  Instant getTracking_sent_date();

  Set<String> getInject_platforms();

  Set<String> getInject_attack_patterns();

  Set<String> getInject_children();

  Set<String> getAttack_patterns_children();

  Set<String> getInject_kill_chain_phases();

  Set<String> getInject_tags();

  Set<String> getInject_assets();

  Set<String> getInject_asset_groups();

  // Set used here to avoid duplication because a concatenation of 3 tables is done in the request
  // InjectRepository.findForIndexing()
  Set<String> getInject_teams();

  String getInject_status_name();

  String getInject_scenario();

  String getInject_Exercise();
}
