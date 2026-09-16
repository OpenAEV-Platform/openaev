package io.openaev.architecture.background_fixtures;

/**
 * Near-miss for the detached hand-off family: a raw {@code new Thread(...)} started inline, with NO
 * executor field. Field-typed executor detection alone misses this shape (ShutdownService is the
 * production instance). Test-scope only; imported explicitly by the detection test.
 */
public class NewThreadFixture {

  public void detach() {
    Thread thread = new Thread(this::work, "fixture-thread");
    thread.setDaemon(true);
    thread.start();
  }

  private void work() {}
}
