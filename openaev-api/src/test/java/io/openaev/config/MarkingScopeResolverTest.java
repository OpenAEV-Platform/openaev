package io.openaev.config;

import static org.junit.jupiter.api.Assertions.*;

import io.openaev.config.MarkingScopeResolver.MarkingRef;
import io.openaev.context.MarkingCtx;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pins the ordinality-in-Java trick (§2.2): a granted level is expanded into every level at or
 * below it, per type, so what reaches SQL is a flat set and the predicate stays a containment test.
 */
@DisplayName("marking clearance resolution collapses ordinality into a flat set")
class MarkingScopeResolverTest {

  private static final String TLP = "TLP";
  private static final String PAP = "PAP";

  // A two-scale tenant, deliberately not in order: resolution must not depend on row order.
  private static final MarkingRef TLP_AMBER = new MarkingRef("tlp-amber", TLP, 30);
  private static final MarkingRef TLP_CLEAR = new MarkingRef("tlp-clear", TLP, 10);
  private static final MarkingRef TLP_RED = new MarkingRef("tlp-red", TLP, 50);
  private static final MarkingRef TLP_GREEN = new MarkingRef("tlp-green", TLP, 20);
  private static final MarkingRef PAP_RED = new MarkingRef("pap-red", PAP, 50);
  private static final MarkingRef PAP_GREEN = new MarkingRef("pap-green", PAP, 20);

  private static final List<MarkingRef> TENANT_SCALES =
      List.of(TLP_AMBER, TLP_CLEAR, TLP_RED, TLP_GREEN, PAP_RED, PAP_GREEN);

  private final MarkingScopeResolver resolver = new MarkingScopeResolver();

  private Set<String> clearanceOf(Set<String> granted) {
    return Set.of(resolver.resolve(granted, TENANT_SCALES, false).toGuc().split(","));
  }

  @Nested
  @DisplayName("ordinality")
  class Ordinality {

    @Test
    @DisplayName("given a mid-scale grant, should include every lower level and no higher one")
    void given_midScaleGrant_should_expandDownwardsOnly() {
      // -- ACT --
      Set<String> clearance = clearanceOf(Set.of(TLP_AMBER.id()));

      // -- ASSERT --
      // AMBER(30) implies GREEN(20) and CLEAR(10); it must not imply RED(50).
      assertTrue(clearance.containsAll(Set.of("tlp-amber", "tlp-green", "tlp-clear")));
      assertFalse(clearance.contains("tlp-red"), "a grant must never imply a higher level");
    }

    @Test
    @DisplayName("given grants from several groups, should keep the highest order per type")
    void given_grantsInSeveralGroups_should_keepTheHighest() {
      // -- ARRANGE --
      // One group grants GREEN, another RED: the union of the user's group grants.
      Set<String> granted = Set.of(TLP_GREEN.id(), TLP_RED.id());

      // -- ACT --
      Set<String> clearance = clearanceOf(granted);

      // -- ASSERT --
      assertEquals(Set.of("tlp-red", "tlp-amber", "tlp-green", "tlp-clear"), clearance);
    }

    @Test
    @DisplayName("given only the lowest level, should not leak the rest of the scale")
    void given_lowestLevel_should_stayAtThatLevel() {
      assertEquals(Set.of("tlp-clear"), clearanceOf(Set.of(TLP_CLEAR.id())));
    }
  }

  @Nested
  @DisplayName("type independence")
  class TypeIndependence {

    @Test
    @DisplayName("given a grant on one type, should grant nothing on another")
    void given_grantOnOneType_should_notTouchAnotherType() {
      // -- ACT --
      Set<String> clearance = clearanceOf(Set.of(TLP_RED.id()));

      // -- ASSERT --
      // Holding the top of TLP says nothing about PAP — not even its lowest level. A type the
      // caller was granted nothing on contributes nothing at all.
      assertFalse(clearance.contains("pap-green"));
      assertFalse(clearance.contains("pap-red"));
    }

    @Test
    @DisplayName("given grants on two types, should resolve each scale on its own")
    void given_grantsOnTwoTypes_should_resolveEachIndependently() {
      // -- ACT --
      Set<String> clearance = clearanceOf(Set.of(TLP_GREEN.id(), PAP_RED.id()));

      // -- ASSERT --
      assertEquals(
          Set.of("tlp-green", "tlp-clear", "pap-red", "pap-green"),
          clearance,
          "TLP capped at GREEN, PAP at RED; the orders must not cross scales");
    }
  }

  @Nested
  @DisplayName("empty and degenerate clearances")
  class EmptyClearance {

    @Test
    @DisplayName("given no grant, should resolve to none() rather than an empty Restricted")
    void given_noGrant_should_resolveToNone() {
      // -- ACT --
      MarkingCtx clearance = resolver.resolve(Set.of(), TENANT_SCALES, false);

      // -- ASSERT --
      // none() is a normal state, not an error: the user still sees every unmarked row, because
      // the empty set is contained in the empty set.
      assertEquals(MarkingCtx.none(), clearance);
      assertEquals("", clearance.toGuc());
    }

    @Test
    @DisplayName("given a grant on a marking the tenant no longer defines, should ignore it")
    void given_staleGrant_should_ignoreIt() {
      // -- ACT --
      // A grant row surviving a deleted definition must not widen anything, and must not throw.
      MarkingCtx clearance = resolver.resolve(Set.of("deleted-marking"), TENANT_SCALES, false);

      // -- ASSERT --
      assertEquals(MarkingCtx.none(), clearance);
    }

    @Test
    @DisplayName("given a tenant with no markings at all, should resolve to none()")
    void given_tenantWithoutScales_should_resolveToNone() {
      assertEquals(MarkingCtx.none(), resolver.resolve(Set.of("anything"), List.of(), false));
    }
  }

  @Nested
  @DisplayName("bypass")
  class Bypass {

    @Test
    @DisplayName("given a bypassing caller, should hold every marking of the tenant")
    void given_bypass_should_holdTheWholeScale() {
      // -- ACT --
      MarkingCtx clearance = resolver.resolve(Set.of(), TENANT_SCALES, true);

      // -- ASSERT --
      assertEquals(
          Set.of("pap-green", "pap-red", "tlp-amber", "tlp-clear", "tlp-green", "tlp-red"),
          Set.of(clearance.toGuc().split(",")));
    }

    @Test
    @DisplayName("given a bypassing caller, should resolve to an explicit list, never the wildcard")
    void given_bypass_should_notLeakTheUnresolvedIntention() {
      // -- ACT & ASSERT --
      // MarkingCtx.all() is a background-only intention; if it reached the HTTP path, toGuc()
      // would throw at scope-set time. Bypass must be expanded here instead.
      assertInstanceOf(
          MarkingCtx.Restricted.class, resolver.resolve(Set.of(), TENANT_SCALES, true));
    }

    @Test
    @DisplayName("given a bypassing caller with no markings defined, should resolve to none()")
    void given_bypassOnEmptyTenant_should_resolveToNone() {
      assertEquals(MarkingCtx.none(), resolver.resolve(Set.of(), List.of(), true));
    }
  }

  @Nested
  @DisplayName("determinism")
  class Determinism {

    @Test
    @DisplayName("should sort the clearance so the GUC value is stable")
    void should_produceAStableGucValue() {
      // -- ACT --
      String guc = resolver.resolve(Set.of(TLP_RED.id()), TENANT_SCALES, false).toGuc();

      // -- ASSERT --
      // Sorted, like TenantScopeResolver: a stable GUC is a stable cache key and a readable log.
      assertEquals("tlp-amber,tlp-clear,tlp-green,tlp-red", guc);
    }

    @Test
    @DisplayName("should not depend on the order the definitions arrive in")
    void should_beIndependentOfRowOrder() {
      // -- ARRANGE --
      List<MarkingRef> reversed = new java.util.ArrayList<>(TENANT_SCALES);
      java.util.Collections.reverse(reversed);

      // -- ACT & ASSERT --
      assertEquals(
          resolver.resolve(Set.of(TLP_AMBER.id()), TENANT_SCALES, false).toGuc(),
          resolver.resolve(Set.of(TLP_AMBER.id()), reversed, false).toGuc());
    }
  }
}
