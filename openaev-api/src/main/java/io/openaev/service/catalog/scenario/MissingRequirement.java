package io.openaev.service.catalog.scenario;

/** What a catalogue scenario lacks to install, with the integration that brings it. */
public record MissingRequirement(String label, String integrationId) {}
