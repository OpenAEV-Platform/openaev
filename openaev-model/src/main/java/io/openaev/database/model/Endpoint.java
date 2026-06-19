package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import io.openaev.annotation.Ipv4OrIpv6Constraint;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.AuditSignificanceAware;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.helper.MultiModelSerializer;
import jakarta.persistence.*;
import java.util.*;
import java.util.stream.StreamSupport;
import lombok.*;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.Type;

@EqualsAndHashCode(callSuper = true)
@Data
@Entity
@DiscriminatorValue(AssetType.Values.ENDPOINT_TYPE)
@EntityListeners(ModelBaseListener.class)
public class Endpoint extends Asset implements AuditSignificanceAware {

  public static final Set<String> BAD_MAC_ADDRESS =
      new HashSet<>(Arrays.asList("ffffffffffff", "000000000000", "0180c2000000"));
  public static final Set<String> BAD_IP_ADDRESSES =
      new HashSet<>(Arrays.asList("127.0.0.1", "::1", "169.254.0.0"));
  public static final String REGEX_MAC_ADDRESS = "[^a-z0-9]";

  public enum PLATFORM_ARCH {
    @JsonProperty("x86_64")
    x86_64,
    @JsonProperty("arm64")
    arm64,
    @JsonProperty("Unknown")
    Unknown;

    /**
     * Returns the PLATFORM_ARCH enum constant corresponding to the given string value. If the value
     * is null or does not match any known architecture, returns Unknown.
     *
     * @param value the string representation of the platform architecture
     * @return the corresponding PLATFORM_ARCH, or Unknown if not recognized
     */
    public static PLATFORM_ARCH fromString(String value) {
      if (value == null) return Unknown;
      return switch (value.toLowerCase()) {
        case "x86_64" -> x86_64;
        case "arm64", "aarch64" -> arm64;
        default -> Unknown;
      };
    }
  }

  public enum PLATFORM_TYPE {
    @JsonProperty("Linux")
    Linux,
    @JsonProperty("Windows")
    Windows,
    @JsonProperty("MacOS")
    MacOS,
    @JsonProperty("Android")
    Android,
    @JsonProperty("iOS")
    iOS,
    @JsonProperty("Container")
    Container,
    @JsonProperty("Service")
    Service,
    @JsonProperty("Generic")
    Generic,
    @JsonProperty("Internal")
    Internal,
    @JsonProperty("Unknown")
    Unknown;

    /** Returns all enum constant names as strings. */
    public static List<String> getAllNamesAsStrings() {
      return Arrays.stream(values()).map(Enum::name).toList();
    }

    /**
     * Returns the PLATFORM_TYPE enum constant corresponding to the given string value. If the value
     * is null or does not match any known type, returns Unknown.
     *
     * @param value the string representation of the platform type
     * @return the corresponding PLATFORM_TYPE, or Unknown if not recognized
     */
    public static PLATFORM_TYPE fromString(String value) {
      if (value == null) return Unknown;
      for (PLATFORM_TYPE type : PLATFORM_TYPE.values()) {
        if (value.equalsIgnoreCase(type.name())) {
          return type;
        }
      }
      return Unknown;
    }

    /**
     * Convert and return all enum from a list of String
     *
     * @param node to convert
     * @return converted list
     */
    public static PLATFORM_TYPE[] fromJsonNode(JsonNode node) {
      if (node == null || !node.isArray()) {
        return new PLATFORM_TYPE[] {Unknown};
      }
      PLATFORM_TYPE[] result =
          StreamSupport.stream(node.spliterator(), false)
              .map(JsonNode::asText)
              .map(
                  value -> {
                    try {
                      return PLATFORM_TYPE.valueOf(value);
                    } catch (IllegalArgumentException e) {
                      return null;
                    }
                  })
              .filter(Objects::nonNull)
              .toArray(PLATFORM_TYPE[]::new);

      return result.length == 0 ? new PLATFORM_TYPE[] {Unknown} : result;
    }
  }

  @Queryable(filterable = true)
  @Ipv4OrIpv6Constraint
  @Type(StringArrayType.class)
  @Column(name = "endpoint_ips", columnDefinition = "text[]")
  @JsonProperty("endpoint_ips")
  private String[] ips;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "endpoint_seen_ip")
  @JsonProperty("endpoint_seen_ip")
  private String seenIp;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "endpoint_hostname")
  @JsonProperty("endpoint_hostname")
  private String hostname;

  /**
   * URL of the target for URL-based asset categories (web applications, cloud endpoints, ...). Not
   * relevant for agent-managed hosts.
   */
  @Queryable(filterable = true, sortable = true)
  @Column(name = "endpoint_url")
  @JsonProperty("endpoint_url")
  private String url;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "endpoint_platform")
  @JsonProperty("endpoint_platform")
  @Enumerated(EnumType.STRING)
  private PLATFORM_TYPE platform;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "endpoint_arch")
  @JsonProperty("endpoint_arch")
  @Enumerated(EnumType.STRING)
  private PLATFORM_ARCH arch;

  @Type(StringArrayType.class)
  @Column(name = "endpoint_mac_addresses")
  @JsonProperty("endpoint_mac_addresses")
  private String[] macAddresses;

  // Fixes a bug due to a new version of jackson and lombok
  // cf: https://github.com/projectlombok/lombok/issues/3978
  @Getter(onMethod_ = @JsonProperty("endpoint_is_eol"))
  @Column(name = "endpoint_is_eol")
  @JsonProperty("endpoint_is_eol")
  private boolean isEoL;

  @OneToMany(
      mappedBy = "asset",
      fetch = FetchType.EAGER,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @Fetch(FetchMode.SUBSELECT)
  // method
  @JsonProperty("asset_agents")
  @JsonSerialize(using = MultiModelSerializer.class)
  private List<Agent> agents = new ArrayList<>();

  // -- INJECT --

  @Getter
  @Setter(AccessLevel.NONE)
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "injects_assets",
      joinColumns = @JoinColumn(name = "asset_id"),
      inverseJoinColumns = @JoinColumn(name = "inject_id"))
  @JsonIgnore
  private List<Inject> injects = new ArrayList<>();

  public void setHostname(String hostname) {
    // Locale.ROOT keeps hostname normalization stable regardless of the JVM default locale
    // (e.g. the Turkish dotless-i), since hostnames are not locale-specific text.
    this.hostname = (hostname == null) ? null : hostname.toLowerCase(Locale.ROOT);
  }

  /**
   * Keeps the legacy invariants while platform/arch are now optional at the API layer: agent and
   * collector registrations always provide them, but the new category-driven forms (web app, cloud,
   * ...) may omit them. Defaulting to {@code Unknown} satisfies the (still NOT NULL) {@code
   * endpoint_arch} column, and every Endpoint without an explicit category is a HOST.
   */
  @PrePersist
  @PreUpdate
  public void applyEndpointDefaults() {
    if (this.platform == null) {
      this.platform = PLATFORM_TYPE.Unknown;
    }
    if (this.arch == null) {
      this.arch = PLATFORM_ARCH.Unknown;
    }
    if (this.getCategory() == null) {
      this.setCategory(AssetCategory.HOST);
    }
  }

  public Endpoint() {}

  public Endpoint(String id, String type, String name, PLATFORM_TYPE platform) {
    super(id, type, name);
    this.platform = platform;
  }

  /**
   * Fields excluded from audit significance comparison (timestamps, child collections, metadata).
   */
  private static final Set<String> NON_SIGNIFICANT_FIELDS =
      Set.of("asset_id", "asset_type", "asset_created_at", "asset_updated_at", "asset_tags");

  /**
   * Returns the significant state of this endpoint for audit comparison.
   *
   * <p>Serializes the entire entity via Jackson, then strips non-significant fields and replaces
   * the raw agents list with each agent's own {@link Agent#significantState}. Any new field added
   * to the entity is automatically included without code changes.
   */
  @Override
  public Map<String, Object> significantState(ObjectMapper objectMapper) {
    Map<String, Object> state = objectMapper.convertValue(this, new TypeReference<>() {});
    NON_SIGNIFICANT_FIELDS.forEach(state::remove);
    // Replace raw agent serialization with each agent's significant state (sorted by ID for stable
    // comparison)
    state.put(
        "asset_agents",
        agents.stream()
            .sorted(Comparator.comparing(Agent::getId))
            .map(agent -> agent.significantState(objectMapper))
            .toList());
    return state;
  }
}
