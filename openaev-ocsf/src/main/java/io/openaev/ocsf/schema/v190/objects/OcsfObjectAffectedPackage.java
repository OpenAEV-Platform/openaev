package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectAffectedPackage extends OcsfObject {
  /**
   * Architecture is a shorthand name describing the type of computer hardware the packaged software
   * is meant to run on.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "architecture")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT architectureField;

  /**
   * The Common Platform Enumeration (CPE) name. For example: <code>cpe:/a:apple:safari:16.2</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cpe_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT cpeNameField;

  /**
   * The software package epoch. Epoch is a way to define weighted dependencies based on version
   * numbers.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "epoch")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT epochField;

  /** The software package version in which a reported vulnerability was patched/fixed. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "fixed_in_version")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT fixedInVersionField;

  /**
   * Cryptographic hash to identify the binary instance of a software component. This can include
   * any component such file, package, or library.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "hash")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectFingerprint hashField;

  /** The software license applied to this package. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "license")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT licenseField;

  /**
   * The URL pointing to the license applied on package or software. This is typically a <code>
   * LICENSE.md</code> file within a repository.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "license_url")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeUrlT licenseUrlField;

  /** The software package name. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  /**
   * The software packager manager utilized to manage a package on a system, e.g. npm, yum, dpkg
   * etc.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "package_manager")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT packageManagerField;

  /**
   * The URL of the package or library at the package manager, or the specific URL or URI of an
   * internal package manager link such as <code>AWS CodeArtifact</code> or <code>Artifactory</code>
   * .
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "package_manager_url")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeUrlT packageManagerUrlField;

  /** The installation path of the affected package. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "path")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT pathField;

  /**
   * A purl is a URL string used to identify and locate a software package in a mostly universal and
   * uniform way across programming languages, package managers, packaging conventions, tools, APIs
   * and databases.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "purl")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT purlField;

  /** Release is the number of times a version of the software has been packaged. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "release")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT releaseField;

  /** Describes the recommended remediation steps to address identified issue(s). */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "remediation")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectRemediation remediationField;

  /**
   * The link to the specific library or package such as within <code>GitHub</code>, this is
   * different from the link to the package manager where the library or package is hosted.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "src_url")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeUrlT srcUrlField;

  /**
   * The type of software package, normalized to the caption of the <code>type_id</code> value. In
   * the case of 'Other', it is defined by the source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;

  /** The type of software package. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT typeIdField;

  /**
   * A unique identifier for the package or library reported by the source tool. E.g., the <code>
   * libId</code> within the <code>sbom</code> field of an OX Security Issue or the SPDX <code>
   * components.*.bom-ref</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;

  /** The name of the vendor who published the software package. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "vendor_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT vendorNameField;

  /** The software package version. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT versionField;
}
