package io.openaev.helper;

import com.fasterxml.jackson.databind.DeserializationContext;

public interface CompositeIdResolvableI {
  Object resolveCompositeId(String rawId, DeserializationContext ctxt);
}
