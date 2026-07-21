package io.openaev.injectors;

import static io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE.ARTICLE;
import static io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE.CHALLENGE;
import static io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE.MANUAL;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE;
import io.openaev.expectation.ExpectationBuilderService;
import io.openaev.expectation.ExpectationPropertiesConfig;
import io.openaev.injector_contract.Contract;
import io.openaev.injector_contract.fields.ContractExpectations;
import io.openaev.injectors.challenge.ChallengeContract;
import io.openaev.injectors.channel.ChannelContract;
import io.openaev.injectors.email.EmailContract;
import io.openaev.model.inject.form.Expectation;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Contract defaults for available expectations")
class ContractExpectationsDefaultsTest {

  private final ExpectationBuilderService expectationBuilderService =
      new ExpectationBuilderService(new ExpectationPropertiesConfig());

  @Nested
  @DisplayName("Media pressure contract")
  class MediaPressureContract {

    @Test
    void given_mediaPressureInject_should_exposeArticleAndManualAsAvailableExpectations() {
      // Arrange
      ChannelContract channelContract = new ChannelContract(expectationBuilderService);

      // Act
      Contract contract = channelContract.contracts().getFirst();
      ContractExpectations expectationsField = findExpectationsField(contract);

      // Assert
      assertThat(expectationsField.getAvailableExpectations())
          .filteredOn(Expectation::isPredefined)
          .extracting(Expectation::getType)
          .containsExactly(ARTICLE);
      assertThat(types(expectationsField.getAvailableExpectations()))
          .containsExactlyInAnyOrder(ARTICLE, MANUAL);
    }
  }

  @Nested
  @DisplayName("Challenge contract")
  class ChallengeInjectContract {

    @Test
    void given_challengeInject_should_exposeChallengeAndManualAsAvailableExpectations() {
      // Arrange
      ChallengeContract challengeContract = new ChallengeContract(expectationBuilderService);

      // Act
      Contract contract = challengeContract.contracts().getFirst();
      ContractExpectations expectationsField = findExpectationsField(contract);

      // Assert
      assertThat(expectationsField.getAvailableExpectations())
          .filteredOn(Expectation::isPredefined)
          .extracting(Expectation::getType)
          .containsExactly(CHALLENGE);
      assertThat(types(expectationsField.getAvailableExpectations()))
          .containsExactlyInAnyOrder(CHALLENGE, MANUAL);
    }
  }

  @Nested
  @DisplayName("Email contract")
  class EmailInjectContract {

    @Test
    void given_emailInject_should_exposeOnlyManualAsAvailableExpectations() {
      // Arrange
      EmailContract emailContract = new EmailContract(expectationBuilderService);

      // Act
      List<Contract> contracts = emailContract.contracts();

      // Assert
      assertThat(contracts).hasSize(2);
      contracts.forEach(
          contract -> {
            ContractExpectations expectationsField = findExpectationsField(contract);
            assertThat(expectationsField.getAvailableExpectations())
                .filteredOn(Expectation::isPredefined)
                .extracting(Expectation::getType)
                .isEmpty();
            assertThat(types(expectationsField.getAvailableExpectations()))
                .containsExactlyInAnyOrder(MANUAL);
          });
    }
  }

  private static ContractExpectations findExpectationsField(Contract contract) {
    return contract.getFields().stream()
        .filter(ContractExpectations.class::isInstance)
        .map(ContractExpectations.class::cast)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Missing expectations field in contract"));
  }

  private static List<EXPECTATION_TYPE> types(List<Expectation> expectations) {
    return expectations.stream().map(Expectation::getType).toList();
  }
}
