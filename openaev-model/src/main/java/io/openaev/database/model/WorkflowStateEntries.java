package io.openaev.database.model;

import com.google.common.hash.Hashing;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
@Getter
@Setter
public class WorkflowStateEntries {

  /*Correlated path are separate by "+" */
  private final String regexPathCorrelated = "^.+\\+.+$";

  /** Every outputs start by output.NUMBERS */
  private final String regexOutputs = "^(outputs\\.\\d+)";

  List<Input> inputs;
  List<Correlated> correlated;
  Set<String> hashExecution;

  /** List of all keys needs for the execution* */
  @NotNull @NotEmpty Set<String> executionKeys;

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

  public boolean isPathCorrelated(String path) {
    return path.matches(regexPathCorrelated);
  }

  public List<String> pathCorrelated(String path) {
    if (!path.contains("+")) return new ArrayList<>();

    String[] parts = path.split("\\+");

    return new ArrayList<>(Arrays.asList(parts));
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

  public Map<Set<Pair>, Correlated> getIndexCorrelatedInput() {
    Map<Set<Pair>, Correlated> index = new HashMap<>();

    for (Correlated c : correlated) {
      Set<Pair> keySet =
          c.values.stream().map(v -> new Pair(v.key, v.value)).collect(Collectors.toSet());
      index.put(keySet, c);
    }
    return index;
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

  public String hashCombo(Map<String, String> combo) {
    // Canonicalize key order so hash is stable regardless of map implementation.
    StringBuilder sb = new StringBuilder();
    new TreeMap<>(combo).forEach((k, v) -> sb.append(k).append("=").append(v).append("|"));

    return Hashing.murmur3_128().hashString(sb.toString(), StandardCharsets.UTF_8).toString();
  }

  /**
   * Returns correlated tuples that share at least one key with {@code requiredKeys}.
   *
   * <p>These are candidates for the correlated-first input resolution: they can anchor one or more
   * required keys, with remaining keys completed from the primitive pool.
   *
   * @param requiredKeys the set of key names the step needs (non-DEFAULT mappers)
   * @return tuples whose pair keys intersect with requiredKeys
   */
  public List<Correlated> findCandidateCorrelated(Set<String> requiredKeys) {
    return correlated.stream()
        .filter(tuple -> tuple.getValues().stream().anyMatch(p -> requiredKeys.contains(p.key())))
        .toList();
  }

  /**
   * Projects a correlated tuple to only the keys present in {@code requiredKeys}.
   *
   * <p>Use for both superset tuples (extra keys discarded) and subset tuples (covered keys
   * extracted; caller handles the rest via primitive pool).
   *
   * @param tuple the correlated tuple to project
   * @param requiredKeys the keys to keep
   * @return map of key to value for the intersection of tuple keys and requiredKeys
   */
  public Map<String, String> projectTuple(Correlated tuple, Set<String> requiredKeys) {
    Map<String, String> projection = new HashMap<>();
    tuple.getValues().stream()
        .filter(pair -> requiredKeys.contains(pair.key()))
        .forEach(pair -> projection.put(pair.key(), pair.value()));
    return projection;
  }
}
