package io.openaev.architecture.background_fixtures;

import java.util.concurrent.Executors;

/**
 * Near-miss for the detached hand-off family: an executor obtained and used inline, with NO
 * executor field, no {@code CompletableFuture} and no {@code new Thread}. This is exactly the shape
 * that escapes a field-typed guard: {@code
 * Executors.newSingleThreadExecutor().execute(this::readActiveTable)} spawns a thread that inherits
 * neither the caller's transaction nor its tenant scope, yet the field-typed and CompletableFuture
 * detectors both miss it. Test-scope only; imported explicitly by the detection test.
 */
public class InlineExecutorFixture {

  public void detach() {
    Executors.newSingleThreadExecutor().execute(this::readActiveTable);
  }

  private void readActiveTable() {}
}
