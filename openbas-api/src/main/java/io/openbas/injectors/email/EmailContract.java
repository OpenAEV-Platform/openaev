package io.openbas.injectors.email;

import static io.openbas.helper.SupportedLanguage.en;
import static io.openbas.helper.SupportedLanguage.fr;
import static io.openbas.injector_contract.Contract.executableContract;
import static io.openbas.injector_contract.ContractCardinality.Multiple;
import static io.openbas.injector_contract.ContractCardinality.One;
import static io.openbas.injector_contract.ContractDef.contractBuilder;
import static io.openbas.injector_contract.ContractVariable.variable;
import static io.openbas.injector_contract.fields.ContractAttachment.attachmentField;
import static io.openbas.injector_contract.fields.ContractCheckbox.checkboxField;
import static io.openbas.injector_contract.fields.ContractExpectations.expectationsField;
import static io.openbas.injector_contract.fields.ContractTeam.teamField;
import static io.openbas.injector_contract.fields.ContractText.textField;
import static io.openbas.injector_contract.fields.ContractTextArea.richTextareaField;

import io.openbas.database.model.Endpoint;
import io.openbas.database.model.Variable.VariableType;
import io.openbas.injector_contract.*;
import io.openbas.injector_contract.fields.ContractElement;
import io.openbas.injector_contract.fields.ContractExpectations;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class EmailContract extends Contractor {

  public static final String TYPE = "openbas_email";
  public static final String EMAIL_DEFAULT = "138ad8f8-32f8-4a22-8114-aaa12322bd09";
  public static final String EMAIL_GLOBAL = "2790bd39-37d4-4e39-be7e-53f3ca783f86";

  @Override
  public boolean isExpose() {
    return true;
  }

  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  public ContractConfig getConfig() {
    return new ContractConfig(
        TYPE, Map.of(en, "Email", fr, "Email"), "#cddc39", "#cddc39", "/img/email.png", isExpose());
  }

  @Override
  public List<Contract> contracts() {
    // variables
    ContractVariable documentUriVariable =
        variable(
            "document_uri",
            "Http user link to upload the document (only for document expectation)",
            VariableType.String,
            One);
    // Contracts
    ContractExpectations expectationsField = expectationsField();
    ContractConfig contractConfig = getConfig();
    // Standard contract
    List<ContractElement> standardInstance =
        contractBuilder()
            .mandatory(teamField(Multiple))
            .mandatory(textField("subject", "Subject"))
            .mandatory(richTextareaField("body", "Body"))
            .optional(checkboxField("encrypted", "Encrypted", false))
            .optional(attachmentField(Multiple))
            .optional(expectationsField)
            .build();
    Contract standardEmail =
        executableContract(
            contractConfig,
            EMAIL_DEFAULT,
            Map.of(en, "Send individual mails", fr, "Envoyer des mails individuels"),
            standardInstance,
            List.of(Endpoint.PLATFORM_TYPE.Service),
            false);
    standardEmail.addVariable(documentUriVariable);
    // Global contract
    List<ContractElement> globalInstance =
        contractBuilder()
            .mandatory(teamField(Multiple))
            .mandatory(textField("subject", "Subject"))
            .mandatory(richTextareaField("body", "Body"))
            .optional(attachmentField(Multiple))
            .optional(expectationsField)
            .build();
    Contract globalEmail =
        executableContract(
            contractConfig,
            EMAIL_GLOBAL,
            Map.of(en, "Send multi-recipients mail", fr, "Envoyer un mail multi-destinataires"),
            globalInstance,
            List.of(Endpoint.PLATFORM_TYPE.Service),
            false);
    globalEmail.addVariable(documentUriVariable);
    return List.of(standardEmail, globalEmail);
  }

  @Override
  public ContractorIcon getIcon() {
    InputStream iconStream = getClass().getResourceAsStream("/img/icon-email.png");
    return new ContractorIcon(iconStream);
  }
}
