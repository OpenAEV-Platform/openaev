package io.openaev.service.scenario;

import io.openaev.database.model.Inject;
import io.openaev.service.catalog.scenario.MissingRequirement;
import io.openaev.service.catalog.scenario.targeting.InjectTargets;
import java.util.List;

/**
 * What a scenario's launch meets: its injects that need a target, those running a revoked payload
 * version, and what this platform lacks to run a catalogue scenario.
 */
public record LaunchCheck(
    List<InjectTargets> injects,
    List<Inject> revokedInjects,
    List<MissingRequirement> missingRequirements) {}
