package io.openaev.service.chaining;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.hash.Hashing;
import io.openaev.database.model.WorkflowStateEntries.Pair;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ChainingHashUtils")
class ChainingHashUtilsTest {

  @Nested
  @DisplayName("hashTuple")
  class HashTuple {

    @Test
    @DisplayName("values containing separators cannot make two different tuples share a hash")
    void given_valueForgingAFieldBoundary_should_produceDifferentHashes() {
      // Arrange — with a separator-based encoding both serialize to "IPv4=x|Port=y|Text=z|"
      List<Pair> forged = List.of(new Pair("IPv4", "x|Port=y"), new Pair("Text", "z"));
      List<Pair> genuine =
          List.of(new Pair("IPv4", "x"), new Pair("Port", "y"), new Pair("Text", "z"));

      // Act + Assert
      assertThat(ChainingHashUtils.hashTuple(forged))
          .isNotEqualTo(ChainingHashUtils.hashTuple(genuine));
    }

    @Test
    @DisplayName("values looking like length prefixes cannot make two tuples share a hash")
    void given_valueMimickingALengthPrefix_should_produceDifferentHashes() {
      // Arrange
      List<Pair> first = List.of(new Pair("A", "1:B"), new Pair("C", "D"));
      List<Pair> second = List.of(new Pair("A", "1"), new Pair("B", "1:C1:D"));

      // Act + Assert
      assertThat(ChainingHashUtils.hashTuple(first))
          .isNotEqualTo(ChainingHashUtils.hashTuple(second));
    }

    @Test
    @DisplayName("the hash does not depend on the order of the pairs")
    void given_samePairsInAnotherOrder_should_produceTheSameHash() {
      // Arrange
      List<Pair> ordered = List.of(new Pair("IPv4", "10.0.0.1"), new Pair("Port", "22"));
      List<Pair> reversed = List.of(new Pair("Port", "22"), new Pair("IPv4", "10.0.0.1"));

      // Act + Assert
      assertThat(ChainingHashUtils.hashTuple(ordered))
          .isEqualTo(ChainingHashUtils.hashTuple(reversed));
    }

    @Test
    @DisplayName("several pairs with the same key are all part of the hash")
    void given_twoPairsWithTheSameKey_should_differFromTheTupleWithOnlyOne() {
      // Arrange
      List<Pair> two = List.of(new Pair("IPv4", "10.0.0.1"), new Pair("IPv4", "10.0.0.2"));
      List<Pair> one = List.of(new Pair("IPv4", "10.0.0.1"));

      // Act + Assert
      assertThat(ChainingHashUtils.hashTuple(two)).isNotEqualTo(ChainingHashUtils.hashTuple(one));
    }

    @Test
    @DisplayName("a null value is distinct from the literal string \"null\"")
    void given_nullValue_should_differFromNullString() {
      // Arrange
      List<Pair> nullValue = List.of(new Pair("Text", null));
      List<Pair> nullString = List.of(new Pair("Text", "null"));

      // Act + Assert
      assertThat(ChainingHashUtils.hashTuple(nullValue))
          .isNotEqualTo(ChainingHashUtils.hashTuple(nullString));
    }
  }

  @Nested
  @DisplayName("hashCombo")
  class HashCombo {

    @Test
    @DisplayName("keeps the format of committed execution hashes")
    void given_combo_should_keepTheHistoricalFormat() {
      // Arrange — committed hashes are compared with freshly computed ones: the format is frozen
      Map<String, String> combo = Map.of("Port", "22", "IPv4", "10.0.0.1");

      // Act
      String hash = ChainingHashUtils.hashCombo(combo);

      // Assert
      assertThat(hash)
          .isEqualTo(
              Hashing.murmur3_128()
                  .hashString("IPv4=10.0.0.1|Port=22|", StandardCharsets.UTF_8)
                  .toString());
    }
  }
}
