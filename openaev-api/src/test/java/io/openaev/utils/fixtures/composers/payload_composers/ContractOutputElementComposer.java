package io.openaev.utils.fixtures.composers.payload_composers;

import io.openaev.database.model.ContractOutputElement;
import io.openaev.database.model.RegexGroup;
import io.openaev.utils.fixtures.composers.ComposerBase;
import io.openaev.utils.fixtures.composers.InnerComposerBase;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ContractOutputElementComposer extends ComposerBase<ContractOutputElement> {

  public static class Composer extends InnerComposerBase<ContractOutputElement> {

    private final ContractOutputElement contractOutputElement;
    private final List<RegexGroupComposer.Composer> regexGroupComposers = new ArrayList<>();

    public Composer(ContractOutputElement contractOutputElement) {
      this.contractOutputElement = contractOutputElement;
    }

    public ContractOutputElementComposer.Composer withRegexGroup(
        RegexGroupComposer.Composer regexGroupComposer) {
      regexGroupComposers.add(regexGroupComposer);
      Set<RegexGroup> regexGroups = contractOutputElement.getRegexGroups();
      regexGroups.add(regexGroupComposer.get());
      this.contractOutputElement.setRegexGroups(regexGroups);
      return this;
    }

    @Override
    public ContractOutputElementComposer.Composer persist() {
      throw new RuntimeException("Use OutputParserComposer to persist entities");
    }

    @Override
    public ContractOutputElementComposer.Composer delete() {
      throw new UnsupportedOperationException("Use OutputParserComposer to delete entities");
    }

    @Override
    public ContractOutputElement get() {
      return this.contractOutputElement;
    }
  }

  public ContractOutputElementComposer.Composer forContractOutputElement(
      ContractOutputElement contractOutputElement) {
    generatedItems.add(contractOutputElement);
    return new Composer(contractOutputElement);
  }
}
