package io.openaev.database.raw;

import java.util.List;
import java.util.Set;

public interface RawInject {

  String getInject_id();

  String getAsset_group_id();

  List<String> getInject_teams();

  List<String> getInject_assets();

  List<String> getInject_asset_groups();

  List<String> getInject_expectations();

  Set<String> getInject_communications();

  Set<String> getInject_platforms();

  Set<String> getInject_kill_chain_phases();

  String getStatus_name();

  String getInject_scenario();
}
