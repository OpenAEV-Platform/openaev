package io.openaev.database.model.catalog;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/** A payload version of the catalogue. */
@Getter
@Setter
@Entity
@DiscriminatorValue(ContentType.PAYLOAD_VALUE)
public class CatalogPayload extends CatalogItem {

  /** SHA-256 of the payload content, as the manifest gives it. */
  @Column(name = "catalog_item_content_hash")
  @JsonProperty("catalog_item_content_hash")
  private String contentHash;

  /** First domain of the payload, as the Threat Arsenal sections show it. */
  @Column(name = "catalog_item_domain")
  @JsonProperty("catalog_item_domain")
  private String domain;
}
