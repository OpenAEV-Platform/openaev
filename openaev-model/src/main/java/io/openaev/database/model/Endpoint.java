package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import io.openaev.annotation.Ipv4OrIpv6Constraint;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.helper.MultiModelDeserializer;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import lombok.*;
import org.hibernate.annotations.Type;

@EqualsAndHashCode(callSuper = true)
@Data
@Entity
@DiscriminatorValue(AssetType.Values.ENDPOINT_TYPE)
@EntityListeners(ModelBaseListener.class)
public class Endpoint extends Asset {

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
    Unknown,
  }

  public enum PLATFORM_TYPE {
    @JsonProperty("Linux")
    Linux,
    @JsonProperty("Windows")
    Windows,
    @JsonProperty("MacOS")
    MacOS,
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

  @Queryable(filterable = true, sortable = true)
  @Column(name = "endpoint_platform")
  @JsonProperty("endpoint_platform")
  @Enumerated(EnumType.STRING)
  @NotNull
  private PLATFORM_TYPE platform;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "endpoint_arch")
  @JsonProperty("endpoint_arch")
  @Enumerated(EnumType.STRING)
  @NotNull
  private PLATFORM_ARCH arch;

  @Type(StringArrayType.class)
  @Column(name = "endpoint_mac_addresses")
  @JsonProperty("endpoint_mac_addresses")
  private String[] macAddresses;

  @Column(name = "endpoint_is_eol")
  @JsonProperty("endpoint_is_eol")
  private boolean isEoL;

  @OneToMany(
      mappedBy = "asset",
      fetch = FetchType.EAGER,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  // method
  @JsonProperty("asset_agents")
  @JsonSerialize(using = MultiModelDeserializer.class)
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
    this.hostname = hostname.toLowerCase();
  }

  public Endpoint() {}

  public Endpoint(String id, String type, String name, PLATFORM_TYPE platform) {
    super(id, type, name);
    this.platform = platform;
  }
}
