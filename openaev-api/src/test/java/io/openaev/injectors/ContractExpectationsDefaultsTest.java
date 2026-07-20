package io.openaev.injectors;

import static io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE.ARTICLE;
import static io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE.CHALLENGE;
import static io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE.MANUAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE;
import io.openaev.expectation.ExpectationBuilderService;
import io.openaev.injector_contract.Contract;
import io.openaev.injector_contract.fields.ContractExpectations;
import io.openaev.injectors.challenge.ChallengeContract;
import io.openaev.injectors.channel.ChannelContract;
import io.openaev.injectors.email.EmailContract;
import io.openaev.model.inject.form.Expectation;
import io.openaev.utils.fixtures.ExpectationFixture;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Contract defaults for available expectations")
class ContractExpectationsDefaultsTest {

  @Mock private ExpectationBuilderService expectationBuilderService;

  @Nested
  @DisplayName("Media pressure contract")
  class MediaPressureContract {

    @Test
    void given_mediaPressureInject_should_exposeArticleAndManualAsAvailableExpectations() {
      // Arrange
      stubMediaPressureBuilderDefaults();
      ChannelContract channelContract = new ChannelContract(expectationBuilderService);

      // Act
      Contract contract = channelContract.contracts().getFirst();
      ContractExpectations expectationsField = findExpectationsField(contract);

      // Assert
      assertThat(types(expectationsField.getPredefinedExpectations())).containsExactly(ARTICLE);
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
      stubChallengeBuilderDefaults();
      ChallengeContract challengeContract = new ChallengeContract(expectationBuilderService);

      // Act
      Contract contract = challengeContract.contracts().getFirst();
      ContractExpectations expectationsField = findExpectationsField(contract);

      // Assert
      assertThat(types(expectationsField.getPredefinedExpectations())).containsExactly(CHALLENGE);
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
      stubEmailBuilderDefaults();
      EmailContract emailContract = new EmailContract(expectationBuilderService);

      // Act
      List<Contract> contracts = emailContract.contracts();

      // Assert
      assertThat(contracts).hasSize(2);
      contracts.forEach(
          contract -> {
            ContractExpectations expectationsField = findExpectationsField(contract);
            assertThat(types(expectationsField.getPredefinedExpectations())).isEmpty();
            assertThat(types(expectationsField.getAvailableExpectations())).containsExactly(MANUAL);
          });
    }
  }

  private void stubMediaPressureBuilderDefaults() {
    given(expectationBuilderService.buildPredefinedArticleExpectation())
        .willReturn(createExpectation(ARTICLE, "article"));
    given(expectationBuilderService.buildManualExpectation())
        .willReturn(createExpectation(MANUAL, "manual"));
  }

  private void stubChallengeBuilderDefaults() {
    given(expectationBuilderService.buildPredefinedChallengeExpectation())
        .willReturn(createExpectation(CHALLENGE, "challenge"));
    given(expectationBuilderService.buildManualExpectation())
        .willReturn(createExpectation(MANUAL, "manual"));
  }

  private void stubEmailBuilderDefaults() {
    given(expectationBuilderService.buildManualExpectation())
        .willReturn(createExpectation(MANUAL, "manual"));
  }

  private static Expectation createExpectation(EXPECTATION_TYPE type, String name) {
    return ExpectationFixture.createExpectation(type, name);
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
