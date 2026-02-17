package io.openaev.output_processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.Asset;

/**
 * Interface for handlers that can create assets. Only handlers that support ProcessingContext.ASSET
 * should implement this.
 */
public interface AssetCapable {

  /** Find or Create Asset from jsonNode */
  Asset toAsset(JsonNode jsonNode);
}
