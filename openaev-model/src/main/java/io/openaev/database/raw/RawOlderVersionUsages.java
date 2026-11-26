package io.openaev.database.raw;

/** How many injects and chained steps still run another version of an action's payload. */
public interface RawOlderVersionUsages {

  String getActionId();

  long getUsages();
}
