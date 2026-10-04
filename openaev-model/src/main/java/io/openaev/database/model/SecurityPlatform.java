package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.hibernate.annotations.BatchSize;

@EqualsAndHashCode(callSuper = true)
@Data
@Entity
@DiscriminatorValue(AssetType.Values.SECURITY_PLATFORM_TYPE)
@EntityListeners(ModelBaseListener.class)
public class SecurityPlatform extends Asset implements StixDomainObjectConvertible {

  private static final String STIX_IDENTITY_CLASS = "securityplatform";
  private static final UUID OASIS_NAMESPACE =
      UUID.fromString("00abedb4-aa42-466c-9c01-fed23315a9b7");
  private static final ObjectMapper CANONICAL_MAPPER = new ObjectMapper();

  /**
   * STIX id of the security platform identity, derived from its name exactly like the OpenCTI
   * standard id of an identity (UUIDv5 in the OASIS namespace over the canonical JSON of the
   * lower-cased trimmed name and the identity class), so the same named platform resolves to the
   * same identity across OpenAEV instances and in OpenCTI.
   */
  public static String stixIdentityId(String name) {
    Map<String, String> contributions = new TreeMap<>();
    contributions.put("identity_class", STIX_IDENTITY_CLASS);
    contributions.put("name", normalizeIdentityName(name));
    try {
      String canonical = CANONICAL_MAPPER.writeValueAsString(contributions);
      return "%s--%s"
          .formatted(ObjectTypes.IDENTITY.toString(), uuidV5(OASIS_NAMESPACE, canonical));
    } catch (JsonProcessingException | NoSuchAlgorithmException e) {
      throw new IllegalStateException("Cannot generate the security platform STIX id", e);
    }
  }

  /**
   * The name as OpenCTI normalizes it for an identity standard id: lower-cased, then trimmed of the
   * characters the JavaScript {@code String.prototype.trim} removes (every Unicode space separator,
   * tab, line terminators and the byte order mark), which {@link String#trim()} does not all
   * remove.
   */
  static String normalizeIdentityName(String name) {
    String lower = Objects.requireNonNullElse(name, "").toLowerCase(Locale.ROOT);
    int start = 0;
    int end = lower.length();
    while (start < end && isTrimmedByOpenCti(lower.charAt(start))) {
      start++;
    }
    while (end > start && isTrimmedByOpenCti(lower.charAt(end - 1))) {
      end--;
    }
    return lower.substring(start, end);
  }

  private static boolean isTrimmedByOpenCti(char c) {
    return c == '\t'
        || c == '\n'
        || c == '\u000B'
        || c == '\f'
        || c == '\r'
        || c == '\u2028'
        || c == '\u2029'
        || c == '\uFEFF'
        || Character.getType(c) == Character.SPACE_SEPARATOR;
  }

  private static UUID uuidV5(UUID namespace, String name) throws NoSuchAlgorithmException {
    MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
    sha1.update(
        ByteBuffer.allocate(16)
            .putLong(namespace.getMostSignificantBits())
            .putLong(namespace.getLeastSignificantBits())
            .array());
    byte[] hash = sha1.digest(name.getBytes(StandardCharsets.UTF_8));
    hash[6] = (byte) ((hash[6] & 0x0f) | 0x50);
    hash[8] = (byte) ((hash[8] & 0x3f) | 0x80);
    ByteBuffer buffer = ByteBuffer.wrap(hash, 0, 16);
    return new UUID(buffer.getLong(), buffer.getLong());
  }

  @Override
  public DomainObject toStixDomainObject() {
    return new DomainObject(
        new HashMap<>(
            Map.of(
                CommonProperties.ID.toString(),
                new Identifier(stixIdentityId(this.getName())),
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
    // Email security (secure email gateways / anti-phishing) - fulfills the PREVENTION and
    // DETECTION expectations carried by phishing injector contracts.
    @JsonProperty("EMAIL_SECURITY")
    EMAIL_SECURITY,
    // AI defense platforms (LLM firewalls / guardrails / AI gateways)
    @JsonProperty("LLM_FIREWALL")
    LLM_FIREWALL,
    @JsonProperty("AI_GATEWAY")
    AI_GATEWAY,
    // Vulnerability assessment tools (e.g. the Nuclei injector) that fulfill
    // VULNERABILITY expectations themselves and appear as the verdict source.
    @JsonProperty("VULNERABILITY_SCANNER")
    VULNERABILITY_SCANNER,
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
  @BatchSize(size = 1000)
  @JsonSerialize(using = MultiIdListSerializer.class)
  @JsonProperty("security_platform_collectors")
  @EqualsAndHashCode.Exclude
  @ToString.Exclude
  private List<Collector> collectors = new ArrayList<>();

  /**
   * Injectors currently declaring this security platform as theirs (e.g. Nuclei registering itself
   * as a VULNERABILITY_SCANNER at startup). Same lifecycle contract as {@link #collectors}: the FK
   * is {@code ON DELETE SET NULL}, so this list empties when the injector is deleted from the
   * catalog, and the UI combines both lists into its "managed, keep read-only" signal (#7063).
   */
  @ArraySchema(
      schema =
          @Schema(
              description = "IDs of the injectors currently managing this security platform",
              implementation = String.class))
  @OneToMany(mappedBy = "securityPlatform", fetch = FetchType.LAZY)
  @BatchSize(size = 1000)
  @JsonSerialize(using = MultiIdListSerializer.class)
  @JsonProperty("security_platform_injectors")
  @EqualsAndHashCode.Exclude
  @ToString.Exclude
  private List<Injector> injectors = new ArrayList<>();

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
