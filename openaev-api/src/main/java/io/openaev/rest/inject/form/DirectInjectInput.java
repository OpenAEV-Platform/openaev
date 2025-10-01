package io.openaev.rest.inject.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DirectInjectInput {

  @JsonProperty("inject_title")
  private String title;

  @JsonProperty("inject_description")
  private String description;

  @JsonProperty("inject_injector_contract")
  private String injectorContract;

  @JsonProperty("inject_content")
  private ObjectNode content;

  @JsonProperty("inject_users")
  private List<String> userIds = new ArrayList<>();

  @JsonProperty("inject_documents")
  private List<InjectDocumentInput> documents = new ArrayList<>();

  public Inject toInject(@NotNull final InjectorContract injectorContract) {
    Inject inject = new Inject();
    inject.setTitle(getTitle());
    inject.setDescription(getDescription());
    inject.setContent(getContent());
    inject.setInjectorContract(injectorContract);
    return inject;
  }
}
