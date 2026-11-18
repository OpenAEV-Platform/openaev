package io.openaev.xtm.counterpart.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.xtm.counterpart.LocalObjectService.LocalObject;
import java.util.List;

/**
 * One object of this OpenAEV another XTM product asked for: the key it matched, its id, type and
 * name, the deep link that opens it, and its key facts (findings, simulations) the caller shows
 * next to its pivot.
 */
public record LocalObjectOutput(
    @JsonProperty("object_key") String key,
    @JsonProperty("object_id") String id,
    @JsonProperty("object_type") String type,
    @JsonProperty("object_name") String name,
    @JsonProperty("object_url") String url,
    @JsonProperty("object_facts") List<FactOutput> facts) {

  /** One key fact: a stable key the caller translates and its value as text. */
  public record FactOutput(
      @JsonProperty("fact_key") String key, @JsonProperty("fact_value") String value) {}

  public static LocalObjectOutput from(LocalObject o) {
    List<FactOutput> facts =
        o.facts() == null
            ? List.of()
            : o.facts().stream().map(f -> new FactOutput(f.key(), f.value())).toList();
    return new LocalObjectOutput(o.key(), o.id(), o.type(), o.name(), o.url(), facts);
  }
}
