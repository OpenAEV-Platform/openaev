package io.openaev.injectors.email;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.expectation.ExpectationBuilderService;
import io.openaev.expectation.ExpectationPropertiesConfig;
import io.openaev.injector_contract.Contract;
import io.openaev.injector_contract.ContractVariable;
import io.openaev.injector_contract.variables.VariableHelper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Email contract available variables")
class EmailContractVariablesTest {

  private final EmailContract emailContract =
      new EmailContract(new ExpectationBuilderService(new ExpectationPropertiesConfig()));

  @Test
  void given_singleEmailContract_should_exposeUserVariable() {
    Contract standardEmail = findContract(EmailContract.EMAIL_DEFAULT);

    assertThat(standardEmail.getVariables())
        .extracting(ContractVariable::getKey)
        .contains(VariableHelper.USER);
  }

  @Test
  void given_multiRecipientsEmailContract_should_notExposeUserVariable() {
    Contract globalEmail = findContract(EmailContract.EMAIL_GLOBAL);

    assertThat(globalEmail.getVariables())
        .extracting(ContractVariable::getKey)
        .doesNotContain(VariableHelper.USER);
  }

  @Test
  void given_multiRecipientsEmailContract_should_keepOtherDefaultVariables() {
    Contract globalEmail = findContract(EmailContract.EMAIL_GLOBAL);

    assertThat(globalEmail.getVariables())
        .extracting(ContractVariable::getKey)
        .contains(VariableHelper.EXERCISE, VariableHelper.TEAMS, VariableHelper.PLAYER_URI);
  }

  private Contract findContract(String contractId) {
    List<Contract> contracts = emailContract.contracts();
    return contracts.stream()
        .filter(contract -> contractId.equals(contract.getId()))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Missing email contract " + contractId));
  }
}
