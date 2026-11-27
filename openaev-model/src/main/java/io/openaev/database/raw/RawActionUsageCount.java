package io.openaev.database.raw;

/** How many scenario injects, atomic testings and chained scenario steps run an action. */
public interface RawActionUsageCount {

  String getActionId();

  long getUsageCount();
}
