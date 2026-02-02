package io.openaev.injectors.opencti;

import static io.openaev.helper.SupportedLanguage.en;
import static io.openaev.helper.SupportedLanguage.fr;
import static io.openaev.injector_contract.Contract.executableContract;
import static io.openaev.injector_contract.ContractCardinality.Multiple;
import static io.openaev.injector_contract.ContractCardinality.One;
import static io.openaev.injector_contract.ContractDef.contractBuilder;
import static io.openaev.injector_contract.ContractVariable.variable;
import static io.openaev.injector_contract.fields.ContractAttachment.attachmentField;
import static io.openaev.injector_contract.fields.ContractExpectations.expectationsField;
import static io.openaev.injector_contract.fields.ContractText.textField;
import static io.openaev.injector_contract.fields.ContractTextArea.richTextareaField;

import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Variable.VariableType;
import io.openaev.injector_contract.*;
import io.openaev.injector_contract.fields.ContractElement;
import io.openaev.injector_contract.fields.ContractExpectations;
import io.openaev.rest.domain.enums.PresetDomain;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class OpenCTIContract extends Contractor {

  public static final String TYPE = "openaev_opencti";
  public static final String OPENCTI_CREATE_CASE = "88db2075-ae49-4fe9-a64c-08da2ed07637";
  public static final String OPENCTI_CREATE_REPORT = "b535f011-3a03-46e7-800a-74f01cd8865e";

  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  public ContractConfig getConfig() {
    return new ContractConfig(
        TYPE, Map.of(en, "OpenCTI", fr, "OpenCTI"), "#0fbcff", "#001bda", "/img/icon-opencti.png");
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
    List<ContractElement> createCaseInstance =
        contractBuilder()
            .mandatory(textField("name", "Name"))
            .mandatory(richTextareaField("description", "Description"))
            .optional(attachmentField(Multiple))
            .optional(expectationsField)
            .build();
    Contract createCase =
        executableContract(
            contractConfig,
            OPENCTI_CREATE_CASE,
            Map.of(en, "Create a new case", fr, "Créer un nouveau case"),
            createCaseInstance,
            List.of(Endpoint.PLATFORM_TYPE.Service),
            false,
            new HashSet<>(Collections.singletonList(PresetDomain.TOCLASSIFY)));
    createCase.addVariable(documentUriVariable);
    List<ContractElement> createReportInstance =
        contractBuilder()
            .mandatory(textField("name", "Name"))
            .mandatory(richTextareaField("description", "Description"))
            .optional(attachmentField(Multiple))
            .optional(expectationsField)
            .build();
    Contract createReport =
        executableContract(
            contractConfig,
            OPENCTI_CREATE_REPORT,
            Map.of(en, "Create a new report", fr, "Créer un nouveau rapport"),
            createReportInstance,
            List.of(Endpoint.PLATFORM_TYPE.Service),
            false,
            new HashSet<>(Collections.singletonList(PresetDomain.TOCLASSIFY)));
    createReport.addVariable(documentUriVariable);
    return List.of(createCase, createReport);
  }

  public ContractorIcon getIcon() {
    InputStream iconStream = getClass().getResourceAsStream("/img/icon-opencti.png");
    return new ContractorIcon(iconStream);
  }
}
