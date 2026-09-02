package io.openaev.api.markings;

import static io.openaev.api.markings.MarkingEscalationValidator.assertCanAssignMarkings;
import static io.openaev.utils.fixtures.MarkingDefinitionFixture.createMarkingDefinition;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.context.MarkingCtx;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.rest.exception.ForbiddenException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
}
