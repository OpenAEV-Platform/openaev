package io.openaev.jsonapi;

import java.util.Map;

/** Attributes an entity exports and imports besides its own fields, e.g. ones it delegates. */
public interface JsonApiExtraAttributes {

  Map<String, Object> extraAttributes();

  void applyExtraAttributes(Map<String, Object> attributes);
}
