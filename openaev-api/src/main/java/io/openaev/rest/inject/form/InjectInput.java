package io.openaev.rest.inject.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class InjectInput {

  @NotBlank
  @JsonProperty("inject_title")
  private String title;

  @JsonProperty("inject_description")
  private String description;

  @JsonProperty("inject_injector_contract")
  private String injectorContract;

  @JsonProperty("inject_content")
  private ObjectNode content;

  @JsonProperty("inject_depends_on")
  private List<InjectDependencyInput> dependsOn = new ArrayList<>();

  @JsonProperty("inject_depends_duration")
  private Long dependsDuration;

  @JsonProperty("inject_teams")
  private List<String> teams = new ArrayList<>();

  @JsonProperty("inject_assets")
  private List<String> assets = new ArrayList<>();

  @JsonProperty("inject_asset_groups")
  private List<String> assetGroups = new ArrayList<>();

  @JsonProperty("inject_documents")
  private List<InjectDocumentInput> documents = new ArrayList<>();

  @JsonProperty("inject_all_teams")
  private boolean allTeams = false;

  @JsonProperty("inject_country")
  private String country;

  @JsonProperty("inject_city")
  private String city;

  @JsonProperty("inject_tags")
  private List<String> tagIds = new ArrayList<>();

  @JsonProperty("inject_enabled")
  private boolean enabled = true;

  public Inject toInject(@NotNull final InjectorContract injectorContract) {
    Inject inject = new Inject();
    inject.setTitle(getTitle());
    inject.setDescription(getDescription());
    inject.setContent(getContent());
    inject.setInjectorContract(injectorContract);
    inject.setDependsDuration(getDependsDuration());
    inject.setAllTeams(isAllTeams());
    inject.setCountry(getCountry());
    inject.setCity(getCity());
    inject.setEnabled(isEnabled());
    return inject;
  }
}
