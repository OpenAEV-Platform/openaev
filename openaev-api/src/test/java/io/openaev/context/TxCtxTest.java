package io.openaev.context;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TxCtx")
class TxCtxTest {

  @Nested
  @DisplayName("missing()")
  class MissingTests {

    @Test
    @DisplayName("serializes to the empty string")
    void missingSerializesToEmptyString() {
      assertEquals("", TxCtx.missing().toGuc());
    }

    @Test
    @DisplayName("is a Missing instance")
    void missingType() {
      assertInstanceOf(TxCtx.Missing.class, TxCtx.missing());
    }

    @Test
    @DisplayName("two instances are equal")
    void missingEquality() {
      assertEquals(TxCtx.missing(), TxCtx.missing());
    }

    @Test
    @DisplayName("is not equal to a restricted scope")
    void missingNotEqualToRestricted() {
      assertNotEquals(TxCtx.missing(), TxCtx.forTenant("t1"));
    }
  }

  @Nested
  @DisplayName("forTenant() / forTenants()")
  class RestrictedTests {

    @Test
    @DisplayName("forTenant serializes to the tenant id")
    void singleTenant() {
      assertEquals("t1", TxCtx.forTenant("t1").toGuc());
    }

    @Test
    @DisplayName("forTenants serializes to a comma-separated list in order")
    void multiTenant() {
      assertEquals("t1,t2,t3", TxCtx.forTenants(List.of("t1", "t2", "t3")).toGuc());
    }

    @Test
    @DisplayName("exposes its tenant ids")
    void restrictedType() {
      TxCtx ctx = TxCtx.forTenants(List.of("t1", "t2"));
      assertInstanceOf(TxCtx.Restricted.class, ctx);
      assertEquals(List.of("t1", "t2"), ((TxCtx.Restricted) ctx).tenantIds());
    }

    @Test
    @DisplayName("equal when same tenant ids")
    void restrictedEquality() {
      assertEquals(TxCtx.forTenant("t1"), TxCtx.forTenant("t1"));
    }

    @Test
    @DisplayName("not equal when different tenant ids")
    void restrictedInequality() {
      assertNotEquals(TxCtx.forTenant("t1"), TxCtx.forTenant("t2"));
    }
  }

  @Nested
  @DisplayName("validation")
  class ValidationTests {

    @Test
    @DisplayName("empty list is rejected")
    void emptyListRejected() {
      assertThrows(IllegalArgumentException.class, () -> TxCtx.forTenants(List.of()));
    }

    @Test
    @DisplayName("null collection is rejected")
    void nullCollectionRejected() {
      assertThrows(NullPointerException.class, () -> TxCtx.forTenants(null));
    }

    @Test
    @DisplayName("blank tenant id is rejected")
    void blankRejected() {
      assertThrows(IllegalArgumentException.class, () -> TxCtx.forTenant("  "));
    }

    @Test
    @DisplayName("empty tenant id is rejected")
    void emptyStringRejected() {
      assertThrows(IllegalArgumentException.class, () -> TxCtx.forTenant(""));
    }

    @Test
    @DisplayName("null tenant id is rejected")
    void nullRejected() {
      assertThrows(NullPointerException.class, () -> TxCtx.forTenant(null));
    }

    @Test
    @DisplayName("null element in the list is rejected")
    void nullElementRejected() {
      List<String> withNull = new ArrayList<>();
      withNull.add("t1");
      withNull.add(null);
      assertThrows(NullPointerException.class, () -> TxCtx.forTenants(withNull));
    }

    @Test
    @DisplayName("tenant id containing the list separator is rejected")
    void commaRejected() {
      assertThrows(IllegalArgumentException.class, () -> TxCtx.forTenant("t1,t2"));
    }

    @Test
    @DisplayName("a list element containing the separator is rejected")
    void commaInListRejected() {
      assertThrows(IllegalArgumentException.class, () -> TxCtx.forTenants(List.of("t1", "a,b")));
    }
  }

  @Nested
  @DisplayName("immutability")
  class ImmutabilityTests {

    @Test
    @DisplayName("mutating the source collection does not change the scope")
    void defensiveCopy() {
      List<String> source = new ArrayList<>(List.of("t1", "t2"));
      TxCtx ctx = TxCtx.forTenants(source);
      source.add("intruder");
      assertEquals("t1,t2", ctx.toGuc());
    }

    @Test
    @DisplayName("the exposed tenant id list is unmodifiable")
    void unmodifiableList() {
      TxCtx.Restricted ctx = (TxCtx.Restricted) TxCtx.forTenants(List.of("t1", "t2"));
      assertThrows(UnsupportedOperationException.class, () -> ctx.tenantIds().add("intruder"));
    }
  }
}
