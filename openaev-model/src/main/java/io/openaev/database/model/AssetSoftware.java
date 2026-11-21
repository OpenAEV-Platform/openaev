package io.openaev.database.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

/**
 * One software component an asset runs: vendor, product, version, CPE and PURL, where it was
 * learned and when, and the component that bundles it (a library inside an application, the web
 * server inside a container image).
 *
 * <p>A row like {@link AssetLink}: it carries no tenant of its own. Its asset's reads are scoped by
 * the tenant and marking inspectors, and {@code AssetSoftwareService}, the only writer, reaches a
 * row through its asset only, so a component is never seen or changed without its asset. Deleting
 * the asset deletes its software (foreign key cascade).
 *
 * <p>An unknown version is explicit: {@link #versionState} is {@link SoftwareVersionState#UNKNOWN}
 * and {@link #version} is null, never an empty string.
 */
@Data
@Entity
@Table(name = "asset_software")
public class AssetSoftware {

  @Id
  @Column(name = "asset_software_id", updatable = false, nullable = false)
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  private String id;

  @Column(name = "asset_id", updatable = false, nullable = false)
  private String assetId;

  @Column(name = "asset_software_parent_id")
  private String parentId;

  /** Identity on the asset: the PURL, else the CPE, else vendor, product and version. */
  @Column(name = "asset_software_key", nullable = false)
  private String key;

  @Column(name = "asset_software_vendor")
  private String vendor;

  @Column(name = "asset_software_product", nullable = false)
  private String product;

  @Column(name = "asset_software_version")
  private String version;

  @Column(name = "asset_software_version_state", nullable = false)
  @Enumerated(EnumType.STRING)
  private SoftwareVersionState versionState = SoftwareVersionState.UNKNOWN;

  @Column(name = "asset_software_cpe")
  private String cpe;

  @Column(name = "asset_software_purl")
  private String purl;

  @Column(name = "asset_software_source", nullable = false)
  @Enumerated(EnumType.STRING)
  private SoftwareSource source;

  @Column(name = "asset_software_first_seen", updatable = false, nullable = false)
  private Instant firstSeen = Instant.now();

  @Column(name = "asset_software_last_seen", nullable = false)
  private Instant lastSeen = Instant.now();
}
