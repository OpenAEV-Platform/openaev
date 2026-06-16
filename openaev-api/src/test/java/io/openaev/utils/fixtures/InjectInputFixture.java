package io.openaev.utils.fixtures;

import io.openaev.rest.inject.form.InjectInput;

public final class InjectInputFixture {

  private InjectInputFixture() {}

  public static InjectInput createAuditInjectInput(String injectTitle, String injectorContractId) {
    InjectInput input = new InjectInput();
    input.setTitle(injectTitle);
    input.setInjectorContract(injectorContractId);
    input.setDependsDuration(0L);
    return input;
  }
}
