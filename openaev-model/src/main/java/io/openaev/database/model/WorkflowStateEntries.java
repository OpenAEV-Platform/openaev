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

  /** B2-lite: single global partition in V1. TODO(B2): make asset-aware. */
  public static final String GLOBAL_PARTITION = "__GLOBAL__";

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
   * Materializes all Correlated combinations for a newly received primitive value, bounded to the
   * given recipe. Recipe is supplied by caller — POJO has no registry access.
   *
   * <p>If two different recipes yield the SAME pairSet, dedup keeps the first one's type (known,
   * accepted V1 limitation — real recipes have distinct key sets).
   *
   * @param receivedKey the primitive type name of the received value (e.g. "Username")
   * @param newValue the newly received value
   * @param recipeKeys ordered list of primitive type names composing the recipe (e.g. ["Username",
   *     "Password"])
   * @param type business type name (ContractOutputType.name()), stamped on each new Correlated
   */
  public void generateCorrelatedForRecipe(
      String receivedKey, String newValue, List<String> recipeKeys, String type) {
    if (!recipeKeys.contains(receivedKey)) {
      return;
    }

    List<String> otherKeys = recipeKeys.stream().filter(k -> !k.equals(receivedKey)).toList();

    List<List<Pair>> otherPairsPerType = new ArrayList<>();
    for (String k : otherKeys) {
      Input in = getInputByKey(k);
      if (in.getValues().isEmpty()) {
        return; // recipe not satisfiable yet
      }
      otherPairsPerType.add(in.getValues().stream().map(v -> new Pair(k, v)).toList());
    }

    List<List<Pair>> otherCombinations = cartesianProduct(otherPairsPerType);
    Map<Set<Pair>, Correlated> index = getIndexCorrelatedInput();

    for (List<Pair> combo : otherCombinations) {
      Set<Pair> pairSet = new HashSet<>(combo);
      pairSet.add(new Pair(receivedKey, newValue));
      if (!index.containsKey(pairSet)) {
        Correlated newCorrelated = new Correlated(pairSet, type);
        correlated.add(newCorrelated);
        index.put(pairSet, newCorrelated);
      }
    }
    // TODO(B2): partition pairSet/index by asset_id — Input.values is asset-blind and
    //   currently crosses values from all assets/executions of the run (fantom credentials).
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

  public boolean comboContainAllExecutionKeys(
      Set<String> executionKeys, Map<String, String> combo) {
    return combo.keySet().containsAll(executionKeys);
  }
}
