package io.openaev.config;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JVM-once guard around the write-attribution trigger install. The test database is shared by every
 * Spring context of one surefire fork, so the trigger is installed by the first context that
 * refreshes and reused by the others.
 *
 * <p>The guard flips to installed only after the install succeeded. A first context whose install
 * throws fails its own refresh, which is loud, and leaves the guard open, so the next context tries
 * again instead of running with no trigger at all. Marking the guard before the install would turn
 * one failed attempt into a silent, detector-less fork under {@code maven.test.failure.ignore}.
 */
final class WriteAttrTriggerInstallGuard {

  /** One install attempt; throws when the trigger could not be installed. */
  interface Installer {
    void install() throws Exception;
  }

  private final AtomicBoolean installed = new AtomicBoolean(false);

  /**
   * Runs {@code installer} unless an earlier call already succeeded. A failing install propagates
   * as an {@link IllegalStateException} and does not mark the guard.
   */
  synchronized void installOnce(Installer installer) {
    if (installed.get()) {
      return;
    }
    try {
      installer.install();
    } catch (Exception e) {
      // Not marked: the next context retries, and this one fails its refresh loudly.
      throw new IllegalStateException("cannot install the write-attribution trigger", e);
    }
    installed.set(true);
  }

  boolean isInstalled() {
    return installed.get();
  }
}
