package io.openaev.architecture.background_fixtures;

import java.util.concurrent.CompletableFuture;

/**
 * Near-miss for the detached hand-off family: work detached inline through {@code
 * CompletableFuture.runAsync} on the common pool, with NO executor field. Field-typed executor
 * detection alone misses this shape. Test-scope only; imported explicitly by the detection test.
 */
public class InlineCompletableFutureFixture {

  public void detach() {
    CompletableFuture.runAsync(this::work);
  }

  private void work() {}
}
