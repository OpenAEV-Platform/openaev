package io.openaev.service.attackpath.dto;

/**
 * A finding's verdicts (issue 6647), derived from its producing executions: prevention, detection,
 * vulnerability and logging, each {@code success | failed | unknown} (the front's vocabulary). A
 * missing slot renders as {@code unknown} front-side.
 */
public record AttackPathFindingVerdictsDTO(
    String prevention, String detection, String vulnerability, String logging) {}
