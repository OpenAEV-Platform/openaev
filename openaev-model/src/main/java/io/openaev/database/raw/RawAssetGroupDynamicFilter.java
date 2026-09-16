package io.openaev.database.raw;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.Filters;

public interface RawAssetGroupDynamicFilter {
  ObjectMapper objectMapper = new ObjectMapper();

  default Filters.FilterGroup getAssetGroupDynamicFilter() {
    try {
      return objectMapper.readValue(getAsset_group_dynamic_filter(), Filters.FilterGroup.class);
    } catch (JsonProcessingException e) {
      return null;
    }
  }

  String getAsset_group_id();

  String getAsset_group_dynamic_filter();
}
