package io.openaev.structured_output_parsers;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.Asset;
import io.openaev.database.model.AssetType;

/**
 * Interface for handlers that can create assets. Only handlers that support ProcessingContext.ASSET
 * should implement this.
 */
public interface AssetCapable {

  /** Convert JSON node to asset */
  Asset toAsset(JsonNode jsonNode);

  /** Get the asset type this handler creates */
  AssetType getAssetType();
}
