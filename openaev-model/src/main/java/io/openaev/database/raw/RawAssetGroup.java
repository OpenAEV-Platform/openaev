package io.openaev.database.raw;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.Filters;
import java.util.List;

/**
 * Spring Data projection interface for asset group data.
 *
 * <p>This interface defines a projection for retrieving asset group information including member
 * assets and dynamic filtering criteria. Asset groups can be defined either statically (explicit
 * member list) or dynamically (filter-based membership).
 *
 * @see io.openaev.database.model.AssetGroup
 */
public interface RawAssetGroup {

  /**
   * Parses and returns the dynamic filter configuration for this asset group.
   *
   * <p>Dynamic asset groups use filter criteria to automatically determine membership based on
   * asset properties.
   *
   * @return the parsed {@link Filters.FilterGroup}, or {@code null} if no dynamic filter is defined
   *     or parsing fails
   */
  default Filters.FilterGroup getAssetGroupDynamicFilter() {
    ObjectMapper objectMapper = new ObjectMapper();
    try {
      return objectMapper.readValue(getAsset_group_dynamic_filter(), Filters.FilterGroup.class);
    } catch (JsonProcessingException | IllegalArgumentException e) {
      // null value in filter column triggers IAE
      return null;
    }
  }

  /**
   * Returns the unique identifier of the asset group.
   *
   * @return the asset group ID
   */
  String getAsset_group_id();

  /**
   * Returns the display name of the asset group.
   *
   * @return the asset group name
   */
  String getAsset_group_name();

  /**
   * Returns the list of asset IDs that are members of this group.
   *
   * @return list of asset IDs
   */
  List<String> getAsset_ids();

  /**
   * Returns the raw JSON string of the dynamic filter configuration.
   *
   * @return the dynamic filter JSON, or {@code null} if not a dynamic group
   */
  String getAsset_group_dynamic_filter();
}
