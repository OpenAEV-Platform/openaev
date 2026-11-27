package io.openaev.ocsf.schema.v1100;

public class JsonNodeDeserialiser
    extends com.fasterxml.jackson.databind.JsonDeserializer<
        io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeJsonT> {

  @java.lang.Override
  public io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeJsonT deserialize(
      com.fasterxml.jackson.core.JsonParser p,
      com.fasterxml.jackson.databind.DeserializationContext ctxt)
      throws java.io.IOException {
    return new io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeJsonT(
        p.readValueAs(com.fasterxml.jackson.databind.JsonNode.class));
  }
}
