package io.openaev.database.model;

import java.util.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * In-memory view of a workflow state (global or local), built from the {@code
 * workflow_state_entries} rows (ADR-011). It is never persisted as such: writes go row by row
 * through {@code WorkflowStateStore}, and a view may hold only the subset of entries a caller asked
 * for (see {@code WorkflowStateStore#load}).
 */
@AllArgsConstructor
@Getter
@Setter
public class WorkflowStateEntries {

  List<Input> inputs;
  List<Correlated> correlated;
  Set<String> hashExecution;

  @Builder
  @Getter
  @Setter
  @AllArgsConstructor
  public static class Input {
    String key;
    Set<String> values;
  }

  public record Pair(String key, String value) {}

  @Builder
  @Getter
  @Setter
  @AllArgsConstructor
  public static class Correlated {
    public Set<Pair> values;

    /** Business type name = ContractOutputType.name(), e.g. "PortsScan", "Credentials". */
    public String type;
  }

  /** Creates an empty view. */
  public static WorkflowStateEntries empty() {
    return new WorkflowStateEntries(new ArrayList<>(), new ArrayList<>(), new HashSet<>());
  }

  public Input getInputByKey(String key) {
    if (inputs.isEmpty()) {
      return getNewInput(key);
    } else {
      List<Input> inputsSameKey = inputs.stream().filter(input -> input.key.equals(key)).toList();
      if (inputsSameKey.isEmpty()) {
        return getNewInput(key);
      } else if (inputsSameKey.size() > 1) {
        throw new RuntimeException("More than one input with same key: " + key);
      } else {
        return inputsSameKey.getFirst();
      }
    }
  }

  private Input getNewInput(String key) {
    Input input = Input.builder().key(key).values(new HashSet<>()).build();
    this.inputs.add(input);
    return input;
  }

  /**
   * Computes the Cartesian product of a list of lists.
   *
   * <p>The Cartesian product is a set of all possible combinations where one element is taken from
   * each of the input lists. The order of elements in each combination corresponds to the order of
   * the input lists.
   *
   * <p>Example:
   *
   * <pre>
   * Input:  [[A, B], [1, 2]]
   * Output: [[A, 1], [A, 2], [B, 1], [B, 2]]
   * </pre>
   *
   * <p>If the input list of lists is empty, the method returns a list containing a single empty
   * list, representing the Cartesian product of zero sets.
   *
   * @param <T> the type of elements in the lists
   * @param lists a list of lists for which the Cartesian product will be correlated
   * @return a list of lists containing all possible combinations
   */
  public <T> List<List<T>> cartesianProduct(List<List<T>> lists) {
    List<List<T>> resultLists = new ArrayList<>();
    if (lists.isEmpty()) {
      resultLists.add(new ArrayList<>());
      return resultLists;
    } else {
      List<T> firstList = lists.getFirst();
      List<List<T>> remainingLists = cartesianProduct(lists.subList(1, lists.size()));
      for (T condition : firstList) {
        for (List<T> remaining : remainingLists) {
          List<T> resultList = new ArrayList<>();
          resultList.add(condition);
          resultList.addAll(remaining);
          resultLists.add(resultList);
        }
      }
    }
    return resultLists;
  }

  /**
   * Returns correlated tuples that share at least one required key.
   *
   * @param requiredKeys required dynamic mapper keys
   * @return tuples whose pair keys intersect with requiredKeys
   */
  public List<Correlated> findCandidateCorrelated(Set<String> requiredKeys) {
    return correlated.stream()
        .filter(tuple -> tuple.getValues().stream().anyMatch(p -> requiredKeys.contains(p.key())))
        .toList();
  }

  /**
   * Projects a correlated tuple to required keys only.
   *
   * @param tuple the correlated tuple to project
   * @param requiredKeys the keys to keep
   * @return key-value pairs present in both the tuple and requiredKeys
   */
  public Map<String, String> projectTuple(Correlated tuple, Set<String> requiredKeys) {
    Map<String, String> projection = new HashMap<>();
    tuple.getValues().stream()
        .filter(pair -> requiredKeys.contains(pair.key()))
        .forEach(pair -> projection.put(pair.key(), pair.value()));
    return projection;
  }
}
