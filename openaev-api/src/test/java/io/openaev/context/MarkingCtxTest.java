package io.openaev.context;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MarkingCtx")
class MarkingCtxTest {

  @Nested
  @DisplayName("none()")
  class NoneTests {

    @Test
    @DisplayName("serializes to the empty string")
    void noneSerializesToEmptyString() {
      assertEquals("", MarkingCtx.none().toGuc());
    }

    @Test
    @DisplayName("two instances are equal")
    void noneEquality() {
      assertEquals(MarkingCtx.none(), MarkingCtx.none());
    }

    @Test
    @DisplayName("an empty collection resolves to none() rather than an empty Restricted")
    void emptyCollectionCollapsesToNone() {
      // -- ACT & ASSERT --
      // The empty set is a legitimate clearance (a user who holds nothing), unlike an empty tenant
      // scope which is an error. Restricted rejects empty, so forMarkings must fold it here.
      assertEquals(MarkingCtx.none(), MarkingCtx.forMarkings(Set.of()));
    }
  }

  @Nested
  @DisplayName("forMarkings()")
  class RestrictedTests {

    @Test
    @DisplayName("serializes as a comma-separated list, preserving order")
    void restrictedSerializes() {
      assertEquals("m1,m2,m3", MarkingCtx.forMarkings(List.of("m1", "m2", "m3")).toGuc());
    }

    @Test
    @DisplayName("a single marking serializes without a separator")
    void singleMarkingSerializes() {
      assertEquals("m1", MarkingCtx.forMarkings(List.of("m1")).toGuc());
    }

    @Test
    @DisplayName("rejects a blank marking id")
    void rejectsBlankId() {
      assertThrows(IllegalArgumentException.class, () -> MarkingCtx.forMarkings(List.of(" ")));
    }

    @Test
    @DisplayName("rejects an id containing the separator, which would forge two markings")
    void rejectsIdContainingSeparator() {
      // -- ACT & ASSERT --
      // "a,b" would deserialize on the SQL side as two ids, silently widening the clearance.
      assertThrows(IllegalArgumentException.class, () -> MarkingCtx.forMarkings(List.of("a,b")));
    }

    @Test
    @DisplayName("is immutable: mutating the source collection does not change the clearance")
    void copiesTheSourceCollection() {
      // -- ARRANGE --
      List<String> source = new java.util.ArrayList<>(List.of("m1"));
      MarkingCtx clearance = MarkingCtx.forMarkings(source);

      // -- ACT --
      source.add("m2");

      // -- ASSERT --
      assertEquals("m1", clearance.toGuc());
    }
  }

  @Nested
  @DisplayName("all()")
  class AllTests {

    @Test
    @DisplayName("refuses to serialize: an intention must be resolved before it reaches the GUC")
    void allCannotSerialize() {
      // -- ACT & ASSERT --
      // Mirrors TxCtx.AllTenants: no wildcard ever reaches the scope channel, so a bug in the
      // resolution path fails loudly here instead of quietly granting every marking.
      assertThrows(IllegalStateException.class, () -> MarkingCtx.all().toGuc());
    }

    @Test
    @DisplayName("two instances are equal")
    void allEquality() {
      assertEquals(MarkingCtx.all(), MarkingCtx.all());
    }
  }
}
