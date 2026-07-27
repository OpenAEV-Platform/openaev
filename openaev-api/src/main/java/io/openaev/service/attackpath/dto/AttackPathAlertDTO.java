package io.openaev.service.attackpath.dto;

/**
 * One alert raised by a security platform on an execution, from an {@code InjectExpectationTrace}
 * (A1). Shown in the platform's alert popover in the Result &amp; Terminal drawer.
 */
public record AttackPathAlertDTO(String id, String title, String date, String link) {}
