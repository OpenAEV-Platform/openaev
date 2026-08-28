package io.openaev.debug;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runtime on/off switch for the verbose work (SQL/ORM/JFR), flipped off by the auto-disable timer.
 * The proxy stays in the chain but goes inert; fully removing it needs a restart.
 */
public class DebugRuntimeState {

  private final AtomicBoolean active = new AtomicBoolean(true);

  public boolean isActive() {
    return active.get();
  }

  public void deactivate() {
    active.set(false);
  }
}
