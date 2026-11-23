package io.openaev.service.catalog.scenario.update;

import io.openaev.database.repository.ScenarioRevisionRepository.RevisionRow;

/** A revision as the list shows it, numbered from 1 in the order the scenario kept them. */
public record NumberedRevision(int number, RevisionRow revision) {}
