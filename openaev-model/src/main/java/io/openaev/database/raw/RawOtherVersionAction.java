package io.openaev.database.raw;

/** An action, and the action of another version of its payload. */
public interface RawOtherVersionAction {

  String getActionId();

  String getOtherVersionActionId();
}
