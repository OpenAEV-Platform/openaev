package io.openaev.service.chaining;

import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
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
public class StepInputBuffer {
  /*Computed path are separate by "+" */
  private final String regexPathComputed = "^.+\\+.+$";

  /** Every outputs start by output.NUMBERS */
  private final String regexOutputs = "^(outputs\\.\\d+)";

  private final Gson gson = new Gson();

  List<Input> inputs;
  List<Computed> computed;
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
  public static class Computed {
    public Set<Pair> values;
  }

  /**
   * path - key are use during the config of mapped value (see Condition Mapper) path is different
   * depending on step parent but key is the same for the next step
   *
   * @param output
   * @param path "outputs.message.stdout" or "outputs.message.port+outputs.message.ip"
   * @param key "stout" or "ip+port"
   */
  public void newOutput(String output, String path, String key) {
    if (isPathComputed(path)) {
      // Get Mapped condition liked to this step(child) and with idStepFrom(parent)
      // todo It give you the key <-> path
      Map<String, String> pathKey = new HashMap<>();
      // todo remove
      pathKey.put("outputs.message.ip", "ip");
      pathKey.put("outputs.message.port", "port");
      List<String> paths = pathComputed(path);

      Set<Pair> values = new HashSet<>();
      for (String pathUnit : paths) {
        // TODO check if we can have other than Primitive
        String value = getValues(output, pathUnit).stream().findFirst().orElse("");
        values.add(new Pair(pathKey.get(pathUnit), value));
      }

      Map<Set<Pair>, Computed> index = getIndexComputedInput();
      if (!index.containsKey(values)) {
        Computed newComputed = new Computed(values);
        computed.add(newComputed);
        // todo test all combination  and launch the ones not executed
        // Todo save this StepInputBuffer
        testAndSaveCombinationsForComputed(newComputed);
      }
    } else {
      Set<String> values = getValues(output, path);

      List<String> newValues = new ArrayList<>();

      Input input = getInputByKey(key);
      for (String value : values) {
        if (!input.values.contains(value)) {
          newValues.add(value);
          input.values.add(value);
          // todo test all combination and launch the ones not executed
          // Todo save this StepInputBuffer

          if (!newValues.isEmpty()) {
            testAndSaveCombinationsForInput(input, newValues);
          }
        }
      }
    }
  }

  public boolean isPathComputed(String path) {
    return path.matches(regexPathComputed);
  }

  public List<String> pathComputed(String path) {
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

  private Set<String> getValues(String output, String path) {
    Map<String, Object> fields = StepService.getFields(output, path);

    return fields.values().stream()
        .map(
            value -> {
              if (value instanceof JsonNull) {
                return null;
              } else if (value instanceof JsonPrimitive) {
                return ((JsonPrimitive) value).getAsString();
              } else {
                return value.toString();
              }
            })
        .collect(Collectors.toSet());
  }

  private Map<Set<Pair>, Computed> getIndexComputedInput() {
    Map<Set<Pair>, Computed> index = new HashMap<>();

    for (Computed c : computed) {
      Set<Pair> keySet =
          c.values.stream().map(v -> new Pair(v.key, v.value)).collect(Collectors.toSet());
      index.put(keySet, c);
    }
    return index;
  }

  private void testAndSaveCombinationsForComputed(Computed newComputed) {
    List<Map<String, String>> combinations = generateCombinations(this.inputs, newComputed);

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

  private void testAndSaveCombinationsForInput(Input targetInput, List<String> newValues) {
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

      // Case without Computed
      if (computed.isEmpty()) {
        for (List<Pair> comboPairs : otherCombinations) {
          Map<String, String> combo = new TreeMap<>();
          for (Pair p : comboPairs) combo.put(p.key(), p.value());
          combo.put(newPair.key(), newPair.value());
          testAndSaveCombo(combo);
        }
      } else {
        // Case with Computed: for each existing Computed
        for (Computed comp : computed) {
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

  private List<Map<String, String>> generateCombinations(List<Input> inputs, Computed comp) {
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
   * @param lists a list of lists for which the Cartesian product will be computed
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
