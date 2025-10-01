package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.ModelBaseListener;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@Entity
@DiscriminatorValue(NetworkTraffic.NETWORK_TRAFFIC_TYPE)
@EntityListeners(ModelBaseListener.class)
public class NetworkTraffic extends Payload {

  public static final String NETWORK_TRAFFIC_TYPE = "NetworkTraffic";

  @JsonProperty("payload_type")
  private String type = NETWORK_TRAFFIC_TYPE;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "network_traffic_ip_src")
  @JsonProperty("network_traffic_ip_src")
  @NotNull
  private String ipSrc;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "network_traffic_ip_dst")
  @JsonProperty("network_traffic_ip_dst")
  @NotNull
  private String ipDst;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "network_traffic_port_src")
  @JsonProperty("network_traffic_port_src")
  @NotNull
  private Integer portSrc;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "network_traffic_port_dst")
  @JsonProperty("network_traffic_port_dst")
  @NotNull
  private Integer portDst;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "network_traffic_protocol")
  @JsonProperty("network_traffic_protocol")
  @NotNull
  private String protocol;

  public NetworkTraffic() {}

  public NetworkTraffic(String id, String type, String name) {
    super(id, type, name);
  }
}
