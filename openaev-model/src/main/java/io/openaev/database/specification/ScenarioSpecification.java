package io.openaev.database.specification;

import io.openaev.database.model.Scenario;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowStatus;
import jakarta.annotation.Nullable;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import org.springframework.data.jpa.domain.Specification;

public class ScenarioSpecification {

  private ScenarioSpecification() {}

  public static Specification<Scenario> isRecurring() {
    return (root, query, cb) -> cb.isNotNull(root.get("recurrence"));
  }

  public static Specification<Scenario> noRecurring() {
    return (root, query, cb) -> cb.isNull(root.get("recurrence"));
  }

  /** Whether a chaining {@link Workflow} TEMPLATE exists for the scenario (i.e. it is chained). */
  private static Predicate hasChainingWorkflow(
      Root<Scenario> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
    Subquery<String> workflowSubquery = query.subquery(String.class);
    Root<Workflow> workflowRoot = workflowSubquery.from(Workflow.class);
    workflowSubquery
        .select(workflowRoot.get("id"))
        .where(
            cb.equal(workflowRoot.get("scenario").get("id"), root.get("id")),
            cb.equal(workflowRoot.get("status"), WorkflowStatus.TEMPLATE));
    return cb.exists(workflowSubquery);
  }

  /** Autonomous (AI-driven) scenarios: the {@code scenario_autonomous} flag is set. */
  public static Specification<Scenario> isAutonomous() {
    return (root, query, cb) -> cb.equal(root.get("autonomous"), true);
  }

  /** Chained scenarios: they own a chaining workflow template and are not autonomous. */
  public static Specification<Scenario> isChained() {
    return (root, query, cb) ->
        cb.and(hasChainingWorkflow(root, query, cb), cb.equal(root.get("autonomous"), false));
  }

  /** Time-based scenarios: neither chained (no workflow template) nor autonomous. */
  public static Specification<Scenario> isTimeBased() {
    return (root, query, cb) ->
        cb.and(
            cb.not(hasChainingWorkflow(root, query, cb)), cb.equal(root.get("autonomous"), false));
  }

  public static Specification<Scenario> recurrenceStartDateBefore(
      @NotNull final Instant startDate) {
    return (root, query, cb) ->
        cb.or(
            cb.isNull(root.get("recurrenceStart")),
            cb.lessThanOrEqualTo(root.get("recurrenceStart"), startDate));
  }

  public static Specification<Scenario> recurrenceSartDateAfter(@NotNull final Instant startDate) {
    return (root, query, cb) ->
        cb.or(
            cb.isNull(root.get("recurrenceStart")),
            cb.greaterThanOrEqualTo(root.get("recurrenceStart"), startDate));
  }

  public static Specification<Scenario> recurrenceStopDateAfter(@NotNull final Instant stopDate) {
    return (root, query, cb) ->
        cb.or(
            cb.isNull(root.get("recurrenceEnd")),
            cb.greaterThanOrEqualTo(root.get("recurrenceEnd"), stopDate));
  }

  public static Specification<Scenario> recurrenceStopDateBefore(@NotNull final Instant stopDate) {
    return (root, query, cb) ->
        cb.or(
            cb.isNull(root.get("recurrenceEnd")),
            cb.lessThanOrEqualTo(root.get("recurrenceEnd"), stopDate));
  }

  public static Specification<Scenario> findGrantedFor(String userId) {
    return (root, query, criteriaBuilder) -> {
      Path<Object> path = root.join("grants").join("group").join("users").get("id");
      return criteriaBuilder.equal(path, userId);
    };
  }

  public static Specification<Scenario> byName(@Nullable final String searchText) {
    return UtilsSpecification.byName(searchText, "name");
  }
}
