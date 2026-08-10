package io.openaev.importer;

/**
 * A single action (chaining workflow step / inject) that could not be created during import because
 * one of its dependencies (injector, injector contract or payload) was missing on the target
 * instance. Both fields are displayed as-is by the front (partial-import toast): product decision
 * is to surface the type AND the name of the concerned step/inject.
 *
 * @param type human-readable category of the missing dependency (e.g. {@code "Injector"} or {@code
 *     "InjectorContract/Payload"})
 * @param name human-readable name of the concerned step/inject
 */
public record MissingImportedAction(String type, String name) {}
