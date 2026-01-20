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

@AllArgsConstructor
@Getter
@Setter
public class StepStateEntries {
  /*Correlated path are separate by "+" */
  private final String regexPathCorrelated = "^.+\\+.+$";

  /** Every outputs start by output.NUMBERS */
  private final String regexOutputs = "^(outputs\\.\\d+)";

  List<Input> inputs;
  List<Correlated> correlated;
  Set<Long> hashExecution;

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
        return inputsSameKey.get(0);
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

  public void testAndSaveCombinationsForCorrelated(Correlated newCorrelated) {
    List<Map<String, String>> combinations = generateCombinations(this.inputs, newCorrelated);

    for (Map<String, String> combo : combinations) {
      testAndSaveCombo(combo);
    }
  }

  private void testAndSaveCombo(Map<String, String> combo) {
    if (!comboContainAllExecutionKeys(executionKeys, combo)) {
      System.out.println("No execution, missing input : " + combo);
      return;
    }

    long hash = hashCombo(combo);
    if (!hashExecution.contains(hash)) {
      hashExecution.add(hash);
      System.out.println("New execution : " + combo + " -> hash=" + hash);
      // TODO: lancer l'exécution + persister StepInputBuffer
    } else {
      System.out.println("Already executed : " + combo);
    }
  }

  public void testAndSaveCombinationsForInput(Input targetInput, List<String> newValues) {
    // Separate the target input from the other inputs
    List<Input> otherInputs =
        this.inputs.stream().filter(in -> !in.getKey().equals(targetInput.getKey())).toList();

    // Prepare the list of pairs for the other inputs
    List<List<Pair>> otherPairsList = new ArrayList<>();
    for (Input in : otherInputs) {
      List<Pair> pairs = in.getValues().stream().map(v -> new Pair(in.getKey(), v)).toList();
      otherPairsList.add(pairs);
    }

    // Cartesian product of the other inputs
    List<List<Pair>> otherCombinations = cartesianProduct(otherPairsList);

    // For each new value of the target input
    for (String newValue : newValues) {
      Pair newPair = new Pair(targetInput.getKey(), newValue);

      // Case without Correlated
      if (correlated.isEmpty()) {
        for (List<Pair> comboPairs : otherCombinations) {
          Map<String, String> combo = new TreeMap<>();
          for (Pair p : comboPairs) combo.put(p.key(), p.value());
          combo.put(newPair.key(), newPair.value());
          testAndSaveCombo(combo);
        }
      } else {
        // Case with Correlated: for each existing Computed
        for (Correlated comp : correlated) {
          for (List<Pair> comboPairs : otherCombinations) {
            Map<String, String> combo = new TreeMap<>();
            for (Pair p : comboPairs) combo.put(p.key(), p.value());
            combo.put(newPair.key(), newPair.value());
            for (Pair p : comp.getValues()) combo.put(p.key(), p.value());
            testAndSaveCombo(combo);
          }
        }
      }
    }
  }

  private List<Map<String, String>> generateCombinations(List<Input> inputs, Correlated comp) {
    List<Map<String, String>> results = new ArrayList<>();

    // Get all sets of simple values
    List<List<Pair>> simplePairsList = new ArrayList<>();
    for (Input in : inputs) {
      List<Pair> pairs = in.getValues().stream().map(v -> new Pair(in.getKey(), v)).toList();
      simplePairsList.add(pairs);
    }

    // Cartesian product of the simple inputs
    List<List<Pair>> simpleCombinations = cartesianProduct(simplePairsList);

    if (comp != null) {
      for (List<Pair> simpleCombo : simpleCombinations) {
        // TreeMap for order
        Map<String, String> map = new TreeMap<>();
        for (Pair p : simpleCombo) map.put(p.key(), p.value());
        for (Pair p : comp.getValues()) map.put(p.key(), p.value());
        results.add(map);
      }
    } else {
      for (List<Pair> simpleCombo : simpleCombinations) {
        Map<String, String> map = new TreeMap<>();
        for (Pair p : simpleCombo) map.put(p.key(), p.value());
        results.add(map);
      }
    }
    return results;
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
  private <T> List<List<T>> cartesianProduct(List<List<T>> lists) {
    List<List<T>> resultLists = new ArrayList<>();
    if (lists.isEmpty()) {
      resultLists.add(new ArrayList<>());
      return resultLists;
    } else {
      List<T> firstList = lists.get(0);
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

  private long hashCombo(Map<String, String> combo) {
    // Order key
    StringBuilder sb = new StringBuilder();
    combo.forEach((k, v) -> sb.append(k).append("=").append(v).append("|"));

    return Hashing.murmur3_128().hashString(sb.toString(), StandardCharsets.UTF_8).asLong();
  }

  boolean comboContainAllExecutionKeys(Set<String> executionKeys, Map<String, String> combo) {
    return combo.keySet().containsAll(executionKeys);
  }
}
