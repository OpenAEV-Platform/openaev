package io.openaev.execution;

import io.openaev.database.model.User;
import io.openaev.injector_contract.variables.VariableHelper;
import io.openaev.injector_contract.variables.contract.UserContract;
import java.util.HashMap;
import java.util.List;

public class ExecutionContext extends HashMap<String, Object> {

  public ExecutionContext(User user, List<String> teams) {
    UserContract userContract = UserContract.fromUser(user);
    this.put(VariableHelper.USER, userContract);
    this.put(VariableHelper.TEAMS, teams);
  }

  public UserContract getUser() {
    return (UserContract) this.get(VariableHelper.USER);
  }

  public List<String> getTeams() {
    //noinspection unchecked
    return (List<String>) this.get(VariableHelper.TEAMS);
  }
}
