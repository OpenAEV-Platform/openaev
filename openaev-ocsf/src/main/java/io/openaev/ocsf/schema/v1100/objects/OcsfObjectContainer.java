package io.openaev.ocsf.schema.v1100.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectContainer extends OcsfObject {
  /**
   * Commit hash of image created for docker or the SHA256 hash of the container. For example:
   * <code>13550340a8681c84c861aac2e5b440161c2b33a3e4f302ac680ca5b686de48de</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "hash")
  private io.openaev.ocsf.schema.v1100.objects.OcsfObjectFingerprint hashField;

  /** The container image used as a template to run the container. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "image")
  private io.openaev.ocsf.schema.v1100.objects.OcsfObjectImage imageField;

  /**
   * Indicates that the container runs in privileged mode. A privileged container gets all kernel
   * capabilities and access to the host devices, and the runtime does not apply its default system
   * call filter, AppArmor or SELinux confinement, so it is not isolated from the host in a
   * meaningful way.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_privileged")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeBooleanT isPrivilegedField;

  /** The list of labels associated to the container. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "labels")
  private java.util.List<io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT> labelsField;

  /** The container name. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT nameField;

  /** The network driver used by the container. For example, bridge, overlay, host, none, etc. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "network_driver")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT networkDriverField;

  /** The orchestrator managing the container, such as ECS, EKS, K8s, or OpenShift. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "orchestrator")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT orchestratorField;

  /** The unique identifier of the pod (or equivalent) that the container is executing on. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "pod_uuid")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeUuidT podUuidField;

  /** The backend running the container, such as containerd or cri-o. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "runtime")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT runtimeField;

  /** The size of the container image. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "size")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeLongT sizeField;

  /** The tag used by the container. It can indicate version, format, OS. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "tag")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT tagField;

  /** The list of tags; <code>{key:value}</code> pairs associated to the container. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "tags")
  private java.util.List<io.openaev.ocsf.schema.v1100.objects.OcsfObjectKeyValueObject> tagsField;

  /**
   * The full container unique identifier for this instantiation of the container. For example:
   * <code>ac2ea168264a08f9aaca0dfc82ff3551418dfd22d02b713142a6843caa2f61bf</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT uidField;
}
