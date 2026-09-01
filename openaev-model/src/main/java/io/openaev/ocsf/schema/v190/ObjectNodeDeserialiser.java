package io.openaev.ocsf.schema.v190;

public class ObjectNodeDeserialiser
    extends com.fasterxml.jackson.databind.JsonDeserializer<
        io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeJsonT> {

  @java.lang.Override
  public io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeJsonT deserialize(
      com.fasterxml.jackson.core.JsonParser p,
      com.fasterxml.jackson.databind.DeserializationContext ctxt)
      throws java.io.IOException {
    return new io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeJsonT(
        p.readValueAs(com.fasterxml.jackson.databind.node.ObjectNode.class));
  }
}
