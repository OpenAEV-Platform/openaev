package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectModule extends OcsfObject {
  /** The memory address where the module was loaded. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "base_address")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT baseAddressField;

  /** The module file object. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "file")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectFile fileField;

  /** Details about the invocation of the function given in <code>function_name</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "function_invocation")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectFunctionInvocation functionInvocationField;

  /**
   * The invoked function in the module. For load and unload events, this is the entry-point
   * function of the module. The system calls the entry-point function whenever a process or thread
   * loads or unloads the module.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "function_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT functionNameField;

  /**
   * The load type, normalized to the caption of the load_type_id value. In the case of 'Other', it
   * is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "load_type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT loadTypeField;

  /** The normalized identifier for how the module was loaded in memory. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "load_type_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT loadTypeIdField;

  /** The start address of the execution. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "start_address")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT startAddressField;

  /** The module type. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT typeField;
}
