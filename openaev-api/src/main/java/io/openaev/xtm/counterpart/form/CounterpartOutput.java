package io.openaev.xtm.counterpart.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.xtm.counterpart.Counterpart;
import io.openaev.xtm.counterpart.CounterpartService.Resolution;

/** The answer of one product for one key: match (with the deep link), no match, or why not. */
public record CounterpartOutput(
    @JsonProperty("counterpart_key") String key,
    @JsonProperty("counterpart_product") String product,
    @JsonProperty("counterpart_status") String status,
    @JsonProperty("counterpart_entity_id") String entityId,
    @JsonProperty("counterpart_entity_type") String entityType,
    @JsonProperty("counterpart_name") String name,
    @JsonProperty("counterpart_url") String url) {

  public static CounterpartOutput from(Resolution r) {
    Counterpart c = r.counterpart();
    return new CounterpartOutput(
        r.key(),
        r.product(),
        r.status().name().toLowerCase(java.util.Locale.ROOT),
        c == null ? null : c.entityId(),
        c == null ? null : c.entityType(),
        c == null ? null : c.name(),
        c == null ? null : c.url());
  }
}
