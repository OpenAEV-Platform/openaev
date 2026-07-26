package io.openaev.service.attackpath.dto;

/**
 * One finding produced by an execution, for the Result tab of the execution detail drawer (issue
 * 5048): its type and value. The value is masked server-side for the credentials category.
 */
public record AttackPathExecutionFindingItemDTO(
    String type, String value, AttackPathFindingVerdictsDTO verdicts) {}
