package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.helper.MonoIdSerializer;
import io.openaev.helper.MultiIdListSerializer;
import io.openaev.stix.objects.DomainObject;
import io.openaev.stix.objects.constants.CommonProperties;
import io.openaev.stix.objects.constants.ObjectTypes;
import io.openaev.stix.parsing.StixDomainObjectConvertible;
import io.openaev.stix.types.Identifier;
import io.openaev.stix.types.StixString;
import io.openaev.stix.types.Timestamp;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@EqualsAndHashCode(callSuper = true)
@Data
@Entity
@DiscriminatorValue(AssetType.Values.SECURITY_PLATFORM_TYPE)
@EntityListeners(ModelBaseListener.class)
public class SecurityPlatform extends Asset implements StixDomainObjectConvertible {

  @Override
  public DomainObject toStixDomainObject() {
    return new DomainObject(
        new HashMap<>(
            Map.of(
                CommonProperties.ID.toString(),
                new Identifier(ObjectTypes.IDENTITY.toString(), this.getId()),
                CommonProperties.CREATED.toString(),
                new Timestamp(this.getCreatedAt()),
                CommonProperties.MODIFIED.toString(),
                new Timestamp(this.getUpdatedAt()),
                "name",
                new StixString(this.getName()),
                CommonProperties.TYPE.toString(),
                new StixString(ObjectTypes.IDENTITY.toString()),
                "identity_class",
                new StixString("securityplatform"))));
  }

  public enum SECURITY_PLATFORM_TYPE {
    @JsonProperty("EDR")
    EDR,
    @JsonProperty("XDR")
    XDR,
    @JsonProperty("SIEM")
    SIEM,
    @JsonProperty("SOAR")
    SOAR,
    @JsonProperty("NDR")
    NDR,
    @JsonProperty("ISPM")
    ISPM,
    // AI defense platforms (LLM firewalls / guardrails / AI gateways)
    @JsonProperty("LLM_FIREWALL")
    LLM_FIREWALL,
    @JsonProperty("AI_GATEWAY")
    AI_GATEWAY,
  }

  @Queryable(filterable = true, sortable = true)
  @Column(name = "security_platform_type")
  @JsonProperty("security_platform_type")
  @Enumerated(EnumType.STRING)
  @NotNull
  private SECURITY_PLATFORM_TYPE securityPlatformType;

  @OneToMany(
      mappedBy = "securityPlatform",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @JsonProperty("security_platform_traces")
  private List<InjectExpectationTrace> traces;

  /**
   * Collectors currently declaring this security platform as theirs (the FK is {@code ON DELETE SET
   * NULL}, so this list empties when the collector is removed). This is the authoritative "managed
   * by a collector" signal: {@code asset_external_reference} is set at creation and never cleared,
   * so it must not be used to decide whether the platform is still read-only.
   */
  @ArraySchema(
      schema =
          @Schema(
              description = "IDs of the collectors currently managing this security platform",
              implementation = String.class))
  @OneToMany(mappedBy = "securityPlatform", fetch = FetchType.LAZY)
  @JsonSerialize(using = MultiIdListSerializer.class)
  @JsonProperty("security_platform_collectors")
  @EqualsAndHashCode.Exclude
  @ToString.Exclude
  private List<Collector> collectors = new ArrayList<>();

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "security_platform_logo_light")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("security_platform_logo_light")
  @Schema(implementation = String.class)
  private Document logoLight;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "security_platform_logo_dark")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("security_platform_logo_dark")
  @Schema(implementation = String.class)
  private Document logoDark;

  @Getter(onMethod_ = @JsonIgnore)
  @Transient
  private final ResourceType resourceType = ResourceType.SECURITY_PLATFORM;

  /**
   * A Security Platform always belongs to the SECURITY_PLATFORM asset category; its subcategory
   * mirrors the platform type (EDR / SIEM / ...) so it shows up consistently in the inventory and
   * filters.
   */
  @PrePersist
  @PreUpdate
  public void applySecurityPlatformDefaults() {
    if (this.getCategory() == null) {
      this.setCategory(AssetCategory.SECURITY_PLATFORM);
    }
    if (this.getSubcategory() == null && this.securityPlatformType != null) {
      try {
        this.setSubcategory(AssetSubCategory.valueOf(this.securityPlatformType.name()));
      } catch (IllegalArgumentException ignored) {
        // No matching subcategory for this platform type; leave it unset.
      }
    }
  }

  public SecurityPlatform() {}

  public SecurityPlatform(
      String id, String type, String name, SECURITY_PLATFORM_TYPE securityPlatformType) {
    super(id, type, name);
    this.securityPlatformType = securityPlatformType;
  }
}
