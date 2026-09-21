package io.openaev.architecture.background_fixtures;

import java.util.concurrent.CompletableFuture;

/**
 * Near-miss for the detached hand-off family via a {@code CompletableFuture} continuation whose
 * name ends in {@code Async} but is neither {@code supplyAsync} nor {@code runAsync}: {@code
 * thenApplyAsync} hands the mapping to the default {@code ForkJoinPool}, off the caller's thread
 * and so off its transaction and tenant scope. Matching only {@code supplyAsync}/{@code runAsync}
 * would miss it. The future is created with {@code completedFuture} (no {@code Async} suffix), so
 * only the {@code thenApplyAsync} call exercises the suffix match. Test-scope only; imported
 * explicitly by the detection test.
 */
public class ChainedCompletableFutureFixture {

  public void detach() {
    CompletableFuture.completedFuture("seed").thenApplyAsync(this::work);
  }

  private String work(String input) {
    return input;
  }
}
