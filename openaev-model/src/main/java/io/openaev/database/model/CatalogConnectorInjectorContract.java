package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.hypersistence.utils.hibernate.type.array.ListArrayType;
import io.hypersistence.utils.hibernate.type.basic.PostgreSQLHStoreType;
import io.openaev.database.audit.ModelBaseListener;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Setter
@Entity
@Table(name = "catalog_connector_injector_contracts")
@EntityListeners(ModelBaseListener.class)
public class CatalogConnectorInjectorContract implements Base {

  // -- IDENTITY --

  @Id
  @Column(name = "catalog_connector_injector_contract_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("catalog_connector_injector_contract_id")
  private String id;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "catalog_connector_id", nullable = false)
  @JsonIgnore
  private CatalogConnector catalogConnector;

  @NotBlank
  @Column(name = "catalog_connector_injector_contract_external_id")
  @JsonProperty("catalog_connector_injector_contract_external_id")
  private String externalId;

  // -- DESCRIPTION --

  @Type(PostgreSQLHStoreType.class)
  @Column(name = "catalog_connector_injector_contract_labels")
  @JsonProperty("catalog_connector_injector_contract_labels")
  private Map<String, String> labels = new HashMap<>();

  @Type(ListArrayType.class)
  @Column(name = "catalog_connector_injector_contract_domains")
  @JsonProperty("catalog_connector_injector_contract_domains")
  private List<String> domains = new ArrayList<>();

  @Type(ListArrayType.class)
  @Column(name = "catalog_connector_injector_contract_platforms")
  @JsonProperty("catalog_connector_injector_contract_platforms")
  private List<String> platforms = new ArrayList<>();

  // MITRE technique external ids, such as T1046.
  @Type(ListArrayType.class)
  @Column(name = "catalog_connector_injector_contract_attack_patterns")
  @JsonProperty("catalog_connector_injector_contract_attack_patterns")
  private List<String> attackPatterns = new ArrayList<>();
}
