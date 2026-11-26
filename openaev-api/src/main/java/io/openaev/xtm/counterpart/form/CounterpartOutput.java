package io.openaev.xtm.counterpart.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.xtm.counterpart.Counterpart;
import io.openaev.xtm.counterpart.CounterpartService.Resolution;
import io.openaev.xtm.counterpart.form.LocalObjectOutput.FactOutput;
import java.util.List;

/**
 * The answer of one product for one key: match (with the deep link and the product's key facts), no
 * match, or why not.
 */
public record CounterpartOutput(
    @JsonProperty("counterpart_key") String key,
    @JsonProperty("counterpart_product") String product,
    @JsonProperty("counterpart_status") String status,
    @JsonProperty("counterpart_entity_id") String entityId,
    @JsonProperty("counterpart_entity_type") String entityType,
    @JsonProperty("counterpart_name") String name,
    @JsonProperty("counterpart_url") String url,
    @JsonProperty("counterpart_facts") List<FactOutput> facts) {

  public static CounterpartOutput from(Resolution r) {
    Counterpart c = r.counterpart();
    return new CounterpartOutput(
        r.key(),
        r.product(),
        r.status().name().toLowerCase(java.util.Locale.ROOT),
        c == null ? null : c.entityId(),
        c == null ? null : c.entityType(),
        c == null ? null : c.name(),
        c == null ? null : c.url(),
        c == null
            ? List.of()
            : c.facts().stream().map(f -> new FactOutput(f.key(), f.value())).toList());
  }
}
