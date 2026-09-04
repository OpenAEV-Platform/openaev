package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectWinService extends OcsfObject {
  /** The full command line used to launch the service. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cmd_line")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT cmdLineField;

  /** The process that is hosting this service. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "hosting_process")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectProcessEntity hostingProcessField;

  /** The list of labels associated with the service. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "labels")
  private java.util.List<io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT> labelsField;

  /** The name of the load ordering group of which this service is a member. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "load_order_group")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT loadOrderGroupField;

  /** The unique name of the service. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  /**
   * The service category, normalized to the caption of the service_category_id value. In the case
   * of 'Other', it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "service_category")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT serviceCategoryField;

  /** The normalized identifier of the service category. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "service_category_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT serviceCategoryIdField;

  /** The names of other services upon which this service has a dependency. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "service_dependencies")
  private java.util.List<io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT>
      serviceDependenciesField;

  /**
   * For a shared user mode service (<code>service_type_id</code> is 4) this is the DLL that gets
   * loaded by the generic service host process (e.g. <code>svchost.exe</code>) to implement the
   * service.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "service_dll_file")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectFile serviceDllFileField;

  /**
   * The service error control, normalized to the caption of the <code>service_error_control_id
   * </code> value. In the case of 'Other', it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "service_error_control")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT serviceErrorControlField;

  /** The normalized identifier of the service error control. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "service_error_control_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT serviceErrorControlIdField;

  /**
   * For a user mode service (<code>service_type_id</code> 3 or 4) this is the executable program
   * that the SCM launches as the service process.<br>
   * For a kernel mode driver (<code>service_type_id</code> 1 or 2) this is the driver file loaded
   * into the kernel at the request of the SCM.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "service_file")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectFile serviceFileField;

  /**
   * For a user mode service, this attribute represents the name of the account under which the
   * service is run. For a kernel mode driver, this attribute represents the object name used to
   * load the driver.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "service_start_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT serviceStartNameField;

  /**
   * The service start type, normalized to the caption of the <code>service_start_type_id</code>
   * value. In the case of 'Other', it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "service_start_type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT serviceStartTypeField;

  /** The normalized identifier of the service start type. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "service_start_type_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT serviceStartTypeIdField;

  /**
   * The service type, normalized to the caption of the service_type_id value. In the case of
   * 'Other', it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "service_type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT serviceTypeField;

  /** The normalized identifier of the service type. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "service_type_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT serviceTypeIdField;

  /** The list of tags; <code>{key:value}</code> pairs associated to the service. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "tags")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectKeyValueObject> tagsField;

  /** The unique identifier of the service. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;

  /**
   * The <code>uid</code> attribute in numeric form where applicable.<br>
   * <strong>Note:</strong> Producers may populate <code>uid_numeric</code> only in addition to
   * <code>uid</code> and not as an alternative to it.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_numeric")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT uidNumericField;

  /** The version of the service. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT versionField;
}
