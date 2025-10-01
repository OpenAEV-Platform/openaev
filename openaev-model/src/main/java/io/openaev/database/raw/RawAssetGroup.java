package io.openaev.database.raw;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.Filters;
import java.util.List;

public interface RawAssetGroup {

  default Filters.FilterGroup getAssetGroupDynamicFilter() {
    ObjectMapper objectMapper = new ObjectMapper();
    try {
      return objectMapper.readValue(getAsset_group_dynamic_filter(), Filters.FilterGroup.class);
    } catch (JsonProcessingException | IllegalArgumentException e) {
      // null value in filter column triggers IAE
      return null;
    }
  }

  String getAsset_group_id();

  String getAsset_group_name();

  List<String> getAsset_ids();

  String getAsset_group_dynamic_filter();
}
