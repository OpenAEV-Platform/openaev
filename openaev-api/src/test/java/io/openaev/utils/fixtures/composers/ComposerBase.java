package io.openaev.utils.fixtures.composers;

import java.util.ArrayList;
import java.util.List;

public class ComposerBase<T> {
  public List<T> generatedItems = new ArrayList<>();

  public void reset() {
    generatedItems.clear();
  }
}
