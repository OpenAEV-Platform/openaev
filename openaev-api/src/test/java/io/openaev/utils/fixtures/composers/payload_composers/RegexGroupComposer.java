package io.openaev.utils.fixtures.composers.payload_composers;

import io.openaev.database.model.RegexGroup;
import io.openaev.utils.fixtures.composers.ComposerBase;
import io.openaev.utils.fixtures.composers.InnerComposerBase;
import org.springframework.stereotype.Component;

@Component
public class RegexGroupComposer extends ComposerBase<RegexGroup> {

  public static class Composer extends InnerComposerBase<RegexGroup> {

    private final RegexGroup regexGroup;

    public Composer(RegexGroup regexGroup) {
      this.regexGroup = regexGroup;
    }

    @Override
    public RegexGroupComposer.Composer persist() {
      throw new RuntimeException("Use ContractOutputElementComposer to persist entities");
    }

    @Override
    public RegexGroupComposer.Composer delete() {
      throw new UnsupportedOperationException(
          "Use ContractOutputElementComposer to delete entities");
    }

    @Override
    public RegexGroup get() {
      return this.regexGroup;
    }
  }

  public RegexGroupComposer.Composer forRegexGroup(RegexGroup regexGroup) {
    generatedItems.add(regexGroup);
    return new Composer(regexGroup);
  }
}
