package io.openaev.execution;

import java.util.Set;

/** An inject due to run, as read by the cross-tenant poll: ids only, reloaded in its tenant. */
public record InjectToRun(
    String injectId, String tenantId, String exerciseId, Set<String> parentInjectIds) {}
