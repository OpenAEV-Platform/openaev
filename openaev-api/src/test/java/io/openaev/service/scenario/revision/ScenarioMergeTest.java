package io.openaev.service.scenario.revision;

import static io.openaev.service.scenario.revision.ScenarioMerge.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.service.scenario.revision.ScenarioMerge.Choice;
import io.openaev.service.scenario.revision.ScenarioMerge.ElementChange;
import io.openaev.service.scenario.revision.ScenarioMerge.Result;
import io.openaev.service.scenario.revision.ScenarioMerge.Status;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Three-way merge of a catalogue scenario (ADR-018, section 5)")
class ScenarioMergeTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static ObjectNode content(ObjectNode... injects) {
    ObjectNode content = MAPPER.createObjectNode();
    content.putObject(SCENARIO).put("name", "Ransomware").put("description", "d");
    content.putObject(MATERIALS).put("articles", "h");
    content.putObject(VARIABLES);
    ObjectNode nodes = content.putObject(INJECTS);
    for (ObjectNode inject : injects) {
      nodes.set(inject.remove("key").asText(), inject);
    }
    content.putObject(STEPS);
    return content;
  }

  private static ObjectNode inject(String key, String contract, String title, String... args) {
    ObjectNode inject = MAPPER.createObjectNode();
    inject.put("key", key);
    inject.put(CONTRACT, contract);
    inject.putObject(CONTENT).put("obfuscator", "plain-text");
    inject.putObject(TIMING).put("depends_duration", 0).putArray(DEPENDS_ON);
    inject.putObject(TEXT).put("title", title).putNull("description");
    ObjectNode arguments = inject.putObject(ARGUMENTS);
    for (int i = 0; i < args.length; i += 2) {
      arguments.put(args[i], args[i + 1]);
    }
    return inject;
  }

  @Test
  @DisplayName("vN changes the contract, the client the title: both are kept, no conflict")
  void given_differentBlocks_should_mergeWithoutConflict() {
    ObjectNode base = content(inject("a", "payload:r|p|1.0.0", "Dump"));
    ObjectNode applied = content(inject("a", "payload:r|p|1.1.0", "Dump"));
    ObjectNode local = content(inject("a", "payload:r|p|1.0.0", "Dump LSASS"));

    Result result = merge(base, applied, local, Map.of());

    assertThat(result.resolved()).isTrue();
    assertThat(result.merged().at("/injects/a/contract").asText()).isEqualTo("payload:r|p|1.1.0");
    assertThat(result.merged().at("/injects/a/text/title").asText()).isEqualTo("Dump LSASS");
  }

  @Test
  @DisplayName("Both sides change a block differently: a conflict, settled by either choice")
  void given_conflict_should_needChoice() {
    ObjectNode base = content(inject("a", "c", "Dump"));
    ObjectNode applied = content(inject("a", "c", "Dump v2"));
    ObjectNode local = content(inject("a", "c", "Dump mine"));

    Result unresolved = merge(base, applied, local, Map.of());
    Result mine = merge(base, applied, local, Map.of("injects:a:text", Choice.KEEP_MINE));
    Result theirs = merge(base, applied, local, Map.of("injects:a:text", Choice.TAKE_APPLIED));

    assertThat(unresolved.unresolved()).containsExactly("injects:a:text");
    assertThat(mine.merged().at("/injects/a/text/title").asText()).isEqualTo("Dump mine");
    assertThat(theirs.merged().at("/injects/a/text/title").asText()).isEqualTo("Dump v2");
  }

  @Test
  @DisplayName("A typed value settles a conflict; the payload version cannot be typed")
  void given_edit_should_takeTypedValueExceptOnContract() {
    ObjectNode base = content(inject("a", "payload:r|p|1.0.0", "Dump"));
    ObjectNode applied = content(inject("a", "payload:r|p|2.0.0", "Dump v2"));
    ObjectNode local = content(inject("a", "payload:r|p|1.5.0", "Dump mine"));
    ObjectNode text = MAPPER.createObjectNode().put("title", "Dump merged");

    Result result =
        merge(
            base,
            applied,
            local,
            Map.of("injects:a:text", Choice.EDIT, "injects:a:contract", Choice.EDIT),
            Map.of(
                "injects:a:text",
                text,
                "injects:a:contract",
                MAPPER.getNodeFactory().textNode("payload:r|p|9.9.9")));

    assertThat(result.merged().at("/injects/a/text/title").asText()).isEqualTo("Dump merged");
    assertThat(result.unresolved()).containsExactly("injects:a:contract");
  }

  @Test
  @DisplayName("The same change on both sides is no conflict")
  void given_sameChange_should_notConflict() {
    ObjectNode base = content(inject("a", "c", "Dump"));
    ObjectNode both = content(inject("a", "c", "Dump v2"));

    Result result = merge(base, both, both.deepCopy(), Map.of());

    assertThat(result.resolved()).isTrue();
    assertThat(result.changes()).extracting(Change::status).containsExactly(Status.SAME);
  }

  @Test
  @DisplayName("Arguments key by key: domain kept, port taken, timeout in conflict")
  void given_arguments_should_mergeKeyByKey() {
    ObjectNode base =
        content(inject("a", "c", "Scan", "domain", "a.com", "port", "80", "timeout", "5"));
    ObjectNode applied =
        content(inject("a", "c", "Scan", "domain", "a.com", "port", "443", "timeout", "10"));
    ObjectNode local =
        content(inject("a", "c", "Scan", "domain", "b.com", "port", "80", "timeout", "30"));

    Result result = merge(base, applied, local, Map.of());

    assertThat(result.merged().at("/injects/a/arguments/domain").asText()).isEqualTo("b.com");
    assertThat(result.merged().at("/injects/a/arguments/port").asText()).isEqualTo("443");
    assertThat(result.unresolved()).containsExactly("injects:a:arguments:timeout");
  }

  @Test
  @DisplayName("A changed argument type on a value the client changed is a conflict")
  void given_typeChange_should_conflict() {
    ObjectNode base = content(inject("a", "c", "Scan", "port", "80"));
    ObjectNode applied = content(inject("a", "c", "Scan"));
    ((ObjectNode) applied.at("/injects/a/arguments")).put("port", 443);
    ObjectNode local = content(inject("a", "c", "Scan", "port", "8080"));

    Result result = merge(base, applied, local, Map.of());

    assertThat(result.unresolved()).containsExactly("injects:a:arguments:port");
  }

  @Test
  @DisplayName("Keeping an older contract keeps the client's arguments and says so")
  void given_olderContractKept_should_keepArguments() {
    ObjectNode base = content(inject("a", "payload:r|p|1.0.0", "Scan", "port", "80"));
    ObjectNode applied = content(inject("a", "payload:r|p|2.0.0", "Scan", "host", "x"));
    ObjectNode local = content(inject("a", "payload:r|p|1.5.0", "Scan", "port", "8080"));

    Result result = merge(base, applied, local, Map.of("injects:a:contract", Choice.KEEP_MINE));

    assertThat(result.merged().at("/injects/a/arguments/port").asText()).isEqualTo("8080");
    assertThat(result.warnings()).contains("OLDER_PAYLOAD_VERSION:injects:a");
  }

  @Test
  @DisplayName("Deleted by the client and changed by vN: it stays deleted")
  void given_deletedLocally_should_stayDeleted() {
    ObjectNode base = content(inject("a", "c", "Dump"), inject("b", "c", "Other"));
    ObjectNode applied = content(inject("a", "c", "Dump v2"), inject("b", "c", "Other"));
    ObjectNode local = content(inject("b", "c", "Other"));

    Result result = merge(base, applied, local, Map.of());

    assertThat(result.merged().path(INJECTS).has("a")).isFalse();
    assertThat(result.elements())
        .extracting(Element::change)
        .containsExactly(ElementChange.STAYS_DELETED);
  }

  @Test
  @DisplayName("Removed by vN: deleted if untouched, a conflict if the client changed it")
  void given_removedByRelease_should_deleteOrAsk() {
    ObjectNode base = content(inject("a", "c", "Dump"), inject("b", "c", "Other"));
    ObjectNode applied = content();
    ObjectNode local = content(inject("a", "c", "Dump"), inject("b", "c", "Other mine"));

    Result unresolved = merge(base, applied, local, Map.of());
    Result kept = merge(base, applied, local, Map.of("injects:b", Choice.KEEP_MINE));

    assertThat(unresolved.merged().path(INJECTS).has("a")).isFalse();
    assertThat(unresolved.unresolved()).containsExactly("injects:b");
    assertThat(kept.merged().path(INJECTS).has(LOCAL_PREFIX + "b")).isTrue();
  }

  @Test
  @DisplayName("Added by vN: created; added by the user: always kept")
  void given_addedInjects_should_keepBoth() {
    ObjectNode base = content(inject("a", "c", "Dump"));
    ObjectNode applied = content(inject("a", "c", "Dump"), inject("n", "c", "New"));
    ObjectNode local = content(inject("a", "c", "Dump"), inject(LOCAL_PREFIX + "u", "c", "Mine"));

    Result result = merge(base, applied, local, Map.of());

    assertThat(result.merged().path(INJECTS).has("n")).isTrue();
    assertThat(result.merged().path(INJECTS).has(LOCAL_PREFIX + "u")).isTrue();
    assertThat(result.elements()).extracting(Element::change).containsExactly(ElementChange.ADDED);
  }

  @Test
  @DisplayName("A kept inject whose parent is gone loses that dependency, with a warning")
  void given_lostParent_should_warn() {
    ObjectNode child = inject(LOCAL_PREFIX + "u", "c", "Mine");
    child.withArray("/" + TIMING + "/" + DEPENDS_ON).addObject().put(PARENT, "a");
    ObjectNode base = content(inject("a", "c", "Dump"));
    ObjectNode local = content(inject("a", "c", "Dump"), child);

    Result result = merge(base, content(), local, Map.of());

    assertThat(result.warnings()).contains("LOST_DEPENDENCY:injects:" + LOCAL_PREFIX + "u");
    assertThat(result.merged().at("/injects/local:u/timing/depends_on")).isEmpty();
  }

  @Test
  @DisplayName("Variables: new added, untouched removed, changed by the client kept")
  void given_variables_should_mergeKeyByKey() {
    ObjectNode base = content();
    ((ObjectNode) base.get(VARIABLES)).put("old", "1").put("mine", "1");
    ObjectNode applied = content();
    ((ObjectNode) applied.get(VARIABLES)).put("new", "2");
    ObjectNode local = content();
    ((ObjectNode) local.get(VARIABLES)).put("old", "1").put("mine", "changed");

    Result result = merge(base, applied, local, Map.of());

    assertThat(result.merged().path(VARIABLES).has("old")).isFalse();
    assertThat(result.merged().path(VARIABLES).path("mine").asText()).isEqualTo("changed");
    assertThat(result.merged().path(VARIABLES).path("new").asText()).isEqualTo("2");
  }
}
