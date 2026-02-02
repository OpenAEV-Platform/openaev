package io.openaev.injectors.channel;

import static io.openaev.helper.SupportedLanguage.en;
import static io.openaev.helper.SupportedLanguage.fr;
import static io.openaev.injector_contract.Contract.executableContract;
import static io.openaev.injector_contract.ContractCardinality.Multiple;
import static io.openaev.injector_contract.ContractCardinality.One;
import static io.openaev.injector_contract.ContractDef.contractBuilder;
import static io.openaev.injector_contract.ContractVariable.variable;
import static io.openaev.injector_contract.fields.ContractArticle.articleField;
import static io.openaev.injector_contract.fields.ContractAttachment.attachmentField;
import static io.openaev.injector_contract.fields.ContractCheckbox.checkboxField;
import static io.openaev.injector_contract.fields.ContractExpectations.expectationsField;
import static io.openaev.injector_contract.fields.ContractTeam.teamField;
import static io.openaev.injector_contract.fields.ContractText.textField;
import static io.openaev.injector_contract.fields.ContractTextArea.richTextareaField;
import static io.openaev.injectors.channel.ChannelExecutor.VARIABLE_ARTICLE;
import static io.openaev.injectors.channel.ChannelExecutor.VARIABLE_ARTICLES;

import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Variable.VariableType;
import io.openaev.expectation.ExpectationBuilderService;
import io.openaev.injector_contract.Contract;
import io.openaev.injector_contract.ContractConfig;
import io.openaev.injector_contract.Contractor;
import io.openaev.injector_contract.ContractorIcon;
import io.openaev.injector_contract.fields.ContractCheckbox;
import io.openaev.injector_contract.fields.ContractElement;
import io.openaev.injector_contract.fields.ContractExpectations;
import io.openaev.rest.domain.enums.PresetDomain;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChannelContract extends Contractor {

  private final ExpectationBuilderService expectationBuilderService;

  public static final String CHANNEL_PUBLISH = "fb5e49a2-6366-4492-b69a-f9b9f39a533e";

  public static final String TYPE = "openaev_channel";

  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  public ContractConfig getConfig() {
    return new ContractConfig(
        TYPE,
        Map.of(en, "Media pressure", fr, "Pression médiatique"),
        "#ff9800",
        "#ff9800",
        "/img/channel.png");
  }

  @Override
  public List<Contract> contracts() {
    ContractConfig contractConfig = getConfig();
    // In this "internal" contract we can't express choices.
    // Choices are contextual to a specific exercise.
    String messageBody =
        """
            Dear player,<br /><br />
            New media pressure entries have been published.<br /><br />
            <#list articles as article>
                - <a href="${article.uri}">${article.name}</a><br />
            </#list>
            <br/><br/>
            Kind regards,<br />
            The animation team
        """;
    ContractCheckbox emailingField = checkboxField("emailing", "Send email", true);
    ContractExpectations expectationsField =
        expectationsField(List.of(this.expectationBuilderService.buildArticleExpectation()));
    List<ContractElement> publishInstance =
        contractBuilder()
            // built in
            .optional(teamField(Multiple))
            .optional(attachmentField(Multiple))
            .mandatory(articleField(Multiple))
            // Contract specific
            .optional(expectationsField)
            // Emailing zone
            .optional(emailingField)
            .mandatoryOnConditionValue(
                textField(
                    "subject",
                    "Subject",
                    "New media pressure entries published for ${user.email}",
                    List.of(emailingField),
                    Map.of(emailingField.getKey(), String.valueOf(true))),
                emailingField,
                String.valueOf(true))
            .mandatoryOnConditionValue(
                richTextareaField(
                    "body",
                    "Body",
                    messageBody,
                    List.of(emailingField),
                    Map.of(emailingField.getKey(), String.valueOf(true))),
                emailingField,
                String.valueOf(true))
            .optional(
                checkboxField(
                    "encrypted",
                    "Encrypted",
                    false,
                    List.of(emailingField),
                    Map.of(emailingField.getKey(), String.valueOf(true))))
            .build();
    Contract publishArticle =
        executableContract(
            contractConfig,
            CHANNEL_PUBLISH,
            Map.of(en, "Publish a media pressure", fr, "Publier de la pression médiatique"),
            publishInstance,
            List.of(Endpoint.PLATFORM_TYPE.Internal),
            false,
            Set.of(PresetDomain.EMAIL_INFILTRATION, PresetDomain.TABLETOP));
    // Adding generated variables
    publishArticle.addVariable(
        variable(
            VARIABLE_ARTICLES,
            "List of articles published by the injection",
            VariableType.Object,
            Multiple,
            List.of(
                variable(
                    VARIABLE_ARTICLE + ".id",
                    "Id of the article in the platform",
                    VariableType.String,
                    One),
                variable(
                    VARIABLE_ARTICLE + ".name", "Name of the article", VariableType.String, One),
                variable(
                    VARIABLE_ARTICLE + ".uri",
                    "Http user link to access the article",
                    VariableType.String,
                    One))));
    publishArticle.setAtomicTesting(false);
    return List.of(publishArticle);
  }

  public ContractorIcon getIcon() {
    InputStream iconStream = getClass().getResourceAsStream("/img/icon-media-pressure.png");
    return new ContractorIcon(iconStream);
  }
}
