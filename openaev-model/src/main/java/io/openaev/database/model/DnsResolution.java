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
@DiscriminatorValue(DnsResolution.DNS_RESOLUTION_TYPE)
@EntityListeners(ModelBaseListener.class)
public class DnsResolution extends Payload {

  public static final String DNS_RESOLUTION_TYPE = "DnsResolution";

  @JsonProperty("payload_type")
  private String type = DNS_RESOLUTION_TYPE;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "dns_resolution_hostname")
  @JsonProperty("dns_resolution_hostname")
  @NotNull
  private String hostname;

  public DnsResolution() {}

  public DnsResolution(String id, String type, String name) {
    super(id, type, name);
  }
}
