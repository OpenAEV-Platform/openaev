package io.openaev.xtm.counterpart.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.xtm.counterpart.LocalObjectService.LocalObject;

/**
 * One object of this OpenAEV another XTM product asked for: the key it matched, its id, type and
 * name, and the deep link that opens it.
 */
public record LocalObjectOutput(
    @JsonProperty("object_key") String key,
    @JsonProperty("object_id") String id,
    @JsonProperty("object_type") String type,
    @JsonProperty("object_name") String name,
    @JsonProperty("object_url") String url) {

  public static LocalObjectOutput from(LocalObject o) {
    return new LocalObjectOutput(o.key(), o.id(), o.type(), o.name(), o.url());
  }
}
