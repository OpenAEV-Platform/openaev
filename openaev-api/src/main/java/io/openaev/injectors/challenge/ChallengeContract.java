package io.openaev.injectors.challenge;

import static io.openaev.helper.SupportedLanguage.en;
import static io.openaev.helper.SupportedLanguage.fr;
import static io.openaev.injector_contract.Contract.executableContract;
import static io.openaev.injector_contract.ContractCardinality.Multiple;
import static io.openaev.injector_contract.ContractDef.contractBuilder;
import static io.openaev.injector_contract.fields.ContractAttachment.attachmentField;
import static io.openaev.injector_contract.fields.ContractChallenge.challengeField;
import static io.openaev.injector_contract.fields.ContractCheckbox.checkboxField;
import static io.openaev.injector_contract.fields.ContractExpectations.expectationsField;
import static io.openaev.injector_contract.fields.ContractTeam.teamField;
import static io.openaev.injector_contract.fields.ContractText.textField;
import static io.openaev.injector_contract.fields.ContractTextArea.richTextareaField;

import io.openaev.database.model.Endpoint;
import io.openaev.expectation.ExpectationBuilderService;
import io.openaev.injector_contract.Contract;
import io.openaev.injector_contract.ContractConfig;
import io.openaev.injector_contract.Contractor;
import io.openaev.injector_contract.ContractorIcon;
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
public class ChallengeContract extends Contractor {

  private final ExpectationBuilderService expectationBuilderService;

  public static final String CHALLENGE_PUBLISH = "f8e70b27-a69c-4b9f-a2df-e217c36b3981";

  public static final String TYPE = "openaev_challenge";

  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  public ContractConfig getConfig() {
    return new ContractConfig(
        TYPE, Map.of(en, "Challenge", fr, "Challenge"), "#e91e63", "#e91e63", "/img/challenge.png");
  }

  @Override
  public List<Contract> contracts() {
    ContractConfig contractConfig = getConfig();
    // In this "internal" contract we can't express choices.
    // Choices are contextual to a specific exercise.
    String messageBody =
        """
            Dear player,<br /><br />
            News challenges have been published.<br /><br />
            <#list challenges as challenge>
                - <a href="${challenge.uri}">${challenge.name}</a><br />
            </#list>
            <br/><br/>
            Kind regards,<br />
            The animation team
        """;
    // We include the expectations for challenges
    ContractExpectations expectationsField =
        expectationsField(List.of(this.expectationBuilderService.buildChallengeExpectation()));
    List<ContractElement> publishInstance =
        contractBuilder()
            .mandatory(challengeField(Multiple))
            // Contract specific
            .optional(expectationsField)
            .mandatory(
                textField("subject", "Subject", "New challenges published for ${user.email}"))
            .mandatory(richTextareaField("body", "Body", messageBody))
            .optional(checkboxField("encrypted", "Encrypted", false))
            .mandatory(teamField(Multiple))
            .optional(attachmentField(Multiple))
            .build();
    Contract publishChallenge =
        executableContract(
            contractConfig,
            CHALLENGE_PUBLISH,
            Map.of(en, "Publish challenges", fr, "Publier des challenges"),
            publishInstance,
            List.of(Endpoint.PLATFORM_TYPE.Internal),
            false,
            Set.of(PresetDomain.EMAIL_INFILTRATION, PresetDomain.TABLETOP));
    publishChallenge.setAtomicTesting(false);
    return List.of(publishChallenge);
  }

  public ContractorIcon getIcon() {
    InputStream iconStream = getClass().getResourceAsStream("/img/icon-challenge.png");
    return new ContractorIcon(iconStream);
  }
}
