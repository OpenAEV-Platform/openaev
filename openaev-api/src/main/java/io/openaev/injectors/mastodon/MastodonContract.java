package io.openaev.injectors.mastodon;

import static io.openaev.helper.SupportedLanguage.en;
import static io.openaev.injector_contract.Contract.executableContract;
import static io.openaev.injector_contract.ContractCardinality.Multiple;
import static io.openaev.injector_contract.ContractDef.contractBuilder;
import static io.openaev.injector_contract.fields.ContractAttachment.attachmentField;
import static io.openaev.injector_contract.fields.ContractText.textField;
import static io.openaev.injector_contract.fields.ContractTextArea.textareaField;

import io.openaev.database.model.Endpoint;
import io.openaev.injector_contract.Contract;
import io.openaev.injector_contract.ContractConfig;
import io.openaev.injector_contract.Contractor;
import io.openaev.injector_contract.ContractorIcon;
import io.openaev.injector_contract.fields.ContractElement;
import io.openaev.injectors.mastodon.config.MastodonConfig;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MastodonContract extends Contractor {

  public static final String TYPE = "openaev_mastodon";

  public static final String MASTODON_DEFAULT = "aeab9ed6-ae98-4b48-b8cc-2e91ac54f2f9";

  private MastodonConfig config;

  @Autowired
  public void setConfig(MastodonConfig config) {
    this.config = config;
  }

  @Override
  public boolean isExpose() {
    return Optional.ofNullable(config.getEnable()).orElse(false);
  }

  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  public ContractConfig getConfig() {
    return new ContractConfig(
        TYPE, Map.of(en, "Mastodon"), "#ad1457", "#ad1457", "/img/mastodon.png", isExpose());
  }

  @Override
  public List<Contract> contracts() {
    ContractConfig contractConfig = getConfig();
    List<ContractElement> instance =
        contractBuilder()
            .mandatory(textField("token", "Token"))
            .mandatory(textareaField("status", "Status"))
            .optional(attachmentField(Multiple))
            .build();
    return List.of(
        executableContract(
            contractConfig,
            MASTODON_DEFAULT,
            Map.of(en, "Mastodon"),
            instance,
            List.of(Endpoint.PLATFORM_TYPE.Service),
            false));
  }

  @Override
  public ContractorIcon getIcon() {
    InputStream iconStream = getClass().getResourceAsStream("/img/icon-mastodon.png");
    return new ContractorIcon(iconStream);
  }
}
