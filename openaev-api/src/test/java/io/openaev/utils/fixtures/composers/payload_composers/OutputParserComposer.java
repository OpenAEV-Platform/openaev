package io.openaev.utils.fixtures.composers.payload_composers;

import io.openaev.database.model.ContractOutputElement;
import io.openaev.database.model.OutputParser;
import io.openaev.utils.fixtures.composers.ComposerBase;
import io.openaev.utils.fixtures.composers.InnerComposerBase;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class OutputParserComposer extends ComposerBase<OutputParser> {

  public static class Composer extends InnerComposerBase<OutputParser> {

    private final OutputParser outputParser;
    private final List<ContractOutputElementComposer.Composer> contractOutputElementComposers =
        new ArrayList<>();

    public Composer(OutputParser outputParser) {
      this.outputParser = outputParser;
    }

    public OutputParserComposer.Composer withContractOutputElement(
        ContractOutputElementComposer.Composer contractOutputElementComposer) {
      contractOutputElementComposers.add(contractOutputElementComposer);
      Set<ContractOutputElement> contractOutputElements = outputParser.getContractOutputElements();
      contractOutputElements.add(contractOutputElementComposer.get());
      this.outputParser.setContractOutputElements(contractOutputElements);
      return this;
    }

    @Override
    public OutputParserComposer.Composer persist() {
      throw new RuntimeException("Use PayloadComposer to persist entities");
    }

    @Override
    public OutputParserComposer.Composer delete() {
      throw new UnsupportedOperationException("Use PayloadComposer to delete entities");
    }

    @Override
    public OutputParser get() {
      return this.outputParser;
    }
  }

  public OutputParserComposer.Composer forOutputParser(OutputParser outputParser) {
    generatedItems.add(outputParser);
    return new Composer(outputParser);
  }
}
