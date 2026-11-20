package io.openaev.service.marking;

import static io.openaev.service.marking.MarkingEscalationValidator.assertCanAssignMarkings;
import static io.openaev.service.marking.MarkingEscalationValidator.assertCanDefineMarking;
import static io.openaev.service.marking.MarkingEscalationValidator.highestHeldOrder;
import static io.openaev.service.marking.MarkingEscalationValidator.isWithinClearance;
import static io.openaev.utils.fixtures.MarkingDefinitionFixture.createMarkingDefinition;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.context.MarkingCtx;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.rest.exception.ForbiddenException;
import io.openaev.rest.exception.MarkingAboveClearanceException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The clearance-escalation guard (design Q7).
 *
 * <p>Unit rather than integration on purpose: the rule is a pure set comparison, and the
 * interesting cases are the ones where the caller holds <i>something</i> but not <i>enough</i> —
 * which through the API would need a non-admin user with hand-built grants for each case.
 */
@DisplayName("MarkingEscalationValidator: you may not grant what you do not hold")
class MarkingEscalationValidatorTest {

  private static MarkingDefinition marking(String id, String name) {
    MarkingDefinition definition =
        createMarkingDefinition(MarkingDefinition.TYPE_TLP, name, 10, "#000000");
    definition.setId(id);
    return definition;
  }

  @Nested
  @DisplayName("refuses")
  class Refuses {

    @Test
    @DisplayName("given no clearance, should refuse assigning any marking")
    void given_noClearance_should_refuse() {
      // -- ARRANGE --
      List<MarkingDefinition> requested = List.of(marking("m-red", "TLP:RED"));

      // -- ACT --
      ForbiddenException thrown =
          assertThrows(
              ForbiddenException.class,
              () -> assertCanAssignMarkings(MarkingCtx.none(), requested));

      // -- ASSERT --
      // This is the default state of every user, and it is the case that makes the guard worth
      // having: managing a group must not be a route to granting oneself TLP:RED.
      assertTrue(thrown.getMessage().contains("TLP:RED"), thrown.getMessage());
    }

    @Test
    @DisplayName("given a partial clearance, should refuse and name only the unheld markings")
    void given_partialClearance_should_nameOnlyTheUnheldOnes() {
      // -- ARRANGE --
      MarkingCtx clearance = MarkingCtx.forMarkings(List.of("m-green"));

      // -- ACT --
      ForbiddenException thrown =
          assertThrows(
              ForbiddenException.class,
              () ->
                  assertCanAssignMarkings(
                      clearance,
                      List.of(marking("m-green", "TLP:GREEN"), marking("m-red", "TLP:RED"))));

      // -- ASSERT --
      // Naming the held one too would send the caller to fix something that is not wrong.
      assertTrue(thrown.getMessage().contains("TLP:RED"), thrown.getMessage());
      assertTrue(!thrown.getMessage().contains("TLP:GREEN"), thrown.getMessage());
    }

    @Test
    @DisplayName("given an unresolved all() intention, should refuse rather than treat it as total")
    void given_allIntention_should_refuse() {
      // -- ARRANGE --
      // all() is a background intention that must never reach an HTTP write. Treating it as "holds
      // everything" would make a plumbing mistake into an escalation, so it is read as holding
      // nothing.
      MarkingCtx unresolved = MarkingCtx.all();

      // -- ACT / ASSERT --
      assertThrows(
          ForbiddenException.class,
          () -> assertCanAssignMarkings(unresolved, List.of(marking("m-red", "TLP:RED"))));
    }
  }

  @Nested
  @DisplayName("allows")
  class Allows {

    @Test
    @DisplayName("given the caller holds every requested marking, should allow")
    void given_callerHoldsThem_should_allow() {
      // -- ARRANGE --
      MarkingCtx clearance = MarkingCtx.forMarkings(List.of("m-green", "m-red"));

      // -- ACT / ASSERT --
      assertDoesNotThrow(
          () ->
              assertCanAssignMarkings(
                  clearance,
                  List.of(marking("m-green", "TLP:GREEN"), marking("m-red", "TLP:RED"))));
    }

    @Test
    @DisplayName("given a lower marking implied by a higher one, should allow")
    void given_impliedMarking_should_allow() {
      // -- ARRANGE --
      // MarkingCtx is already ordinality-expanded by MarkingScopeResolver, so holding TLP:RED means
      // holding TLP:GREEN. Granting it discloses nothing the caller could not already read.
      MarkingCtx expandedFromRed = MarkingCtx.forMarkings(List.of("m-green", "m-red"));

      // -- ACT / ASSERT --
      assertDoesNotThrow(
          () -> assertCanAssignMarkings(expandedFromRed, List.of(marking("m-green", "TLP:GREEN"))));
    }

    @Test
    @DisplayName("given an empty request, should allow — revoking everything is always permitted")
    void given_emptyRequest_should_allow() {
      // -- ACT / ASSERT --
      // Revocation only ever narrows what the group's members can see.
      assertDoesNotThrow(() -> assertCanAssignMarkings(MarkingCtx.none(), List.of()));
    }
  }

  @Nested
  @DisplayName("defining a marking: you may not place one above your own clearance")
  class Define {

    private final List<MarkingDefinition> heldUpToAmber =
        List.of(
            createMarkingDefinition(MarkingDefinition.TYPE_TLP, "TLP:GREEN", 2, "#000000"),
            createMarkingDefinition(MarkingDefinition.TYPE_TLP, "TLP:AMBER", 3, "#000000"));

    @Test
    @DisplayName("given an order above the highest held on the type, should refuse")
    void given_orderAboveHighestHeld_should_refuse() {
      // -- ACT --
      ForbiddenException thrown =
          assertThrows(
              ForbiddenException.class,
              () -> assertCanDefineMarking(heldUpToAmber, MarkingDefinition.TYPE_TLP, 4));

      // -- ASSERT --
      assertEquals(MarkingAboveClearanceException.CODE, thrown.getMessage());
    }

    @Test
    @DisplayName("given a type the caller holds nothing on, should refuse even at order 0")
    void given_typeNotHeld_should_refuse() {
      // -- ACT / ASSERT --
      // Types are separate scales: holding TLP:AMBER says nothing about PAP, nor about a type that
      // does not exist yet.
      assertThrows(ForbiddenException.class, () -> assertCanDefineMarking(heldUpToAmber, "PAP", 0));
    }

    @Test
    @DisplayName("given no clearance, should refuse")
    void given_noClearance_should_refuse() {
      // -- ACT / ASSERT --
      assertThrows(
          ForbiddenException.class,
          () -> assertCanDefineMarking(List.of(), MarkingDefinition.TYPE_TLP, 0));
    }

    @Test
    @DisplayName("given an order equal to the highest held, should allow")
    void given_orderEqualToHighestHeld_should_allow() {
      // -- ACT / ASSERT --
      // Two definitions at the same order are both covered by holding either: no escalation.
      assertDoesNotThrow(
          () -> assertCanDefineMarking(heldUpToAmber, MarkingDefinition.TYPE_TLP, 3));
    }

    @Test
    @DisplayName("given an order below the highest held, should allow")
    void given_orderBelowHighestHeld_should_allow() {
      // -- ACT / ASSERT --
      assertDoesNotThrow(
          () -> assertCanDefineMarking(heldUpToAmber, MarkingDefinition.TYPE_TLP, 0));
    }
  }

  @Nested
  @DisplayName("isWithinClearance: the same rule, answered instead of thrown")
  class WithinClearance {

    private final List<MarkingDefinition> heldUpToAmber =
        List.of(
            createMarkingDefinition(MarkingDefinition.TYPE_TLP, "TLP:GREEN", 2, "#000000"),
            createMarkingDefinition(MarkingDefinition.TYPE_TLP, "TLP:AMBER", 3, "#000000"));

    @ParameterizedTest(name = "{0} at order {1} is within the clearance: {2}")
    @CsvSource({"TLP,0,true", "TLP,3,true", "TLP,4,false", "PAP,0,false"})
    @DisplayName("given a clearance up to TLP:AMBER, should answer as assertCanDefineMarking does")
    void given_clearanceUpToAmber_should_answerLikeTheAssertion(
        String type, int order, boolean expected) {
      // -- ACT --
      boolean within = isWithinClearance(heldUpToAmber, type, order);

      // -- ASSERT --
      assertEquals(expected, within);
      assertEquals(
          expected,
          assertDoesNotThrowOrFalse(() -> assertCanDefineMarking(heldUpToAmber, type, order)));
    }

    @Test
    @DisplayName("given no clearance, should be above whatever the order")
    void given_noClearance_should_beAbove() {
      // -- ACT / ASSERT --
      assertFalse(isWithinClearance(List.of(), MarkingDefinition.TYPE_TLP, 0));
    }

    @Test
    @DisplayName("given a type, should give the highest order held on it, and nothing for another")
    void given_type_should_giveHighestHeldOrder() {
      // -- ACT / ASSERT --
      assertEquals(3, highestHeldOrder(heldUpToAmber, MarkingDefinition.TYPE_TLP).getAsInt());
      assertTrue(highestHeldOrder(heldUpToAmber, "PAP").isEmpty());
    }

    private boolean assertDoesNotThrowOrFalse(Runnable assertion) {
      try {
        assertion.run();
        return true;
      } catch (ForbiddenException e) {
        return false;
      }
    }
  }
}
