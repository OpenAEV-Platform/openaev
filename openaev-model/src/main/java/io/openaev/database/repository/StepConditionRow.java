package io.openaev.database.repository;

import io.openaev.database.model.Condition;

/**
 * A condition paired with the step it is linked to, so a batched read over several steps can be
 * grouped back by step (issue 5048). {@code stepTemplateId} is {@code conditionStep.step.id}.
 */
public record StepConditionRow(String stepTemplateId, Condition condition) {}
