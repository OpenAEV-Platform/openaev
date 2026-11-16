package io.openaev.service.catalog.scenario.targeting;

import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_ASSETS;
import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_ASSET_GROUPS;
import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_TEAMS;
import static io.openaev.utils.InjectContentUtils.MANUAL_TARGETS_CONTENT_KEY;
import static io.openaev.utils.InjectContentUtils.MANUAL_TARGET_SELECTOR;
import static io.openaev.utils.InjectContentUtils.TARGET_SELECTOR_CONTENT_KEY;
import static io.openaev.utils.InjectContentUtils.contentManualTargets;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.Base;
import io.openaev.database.model.Inject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** A kind of target an inject takes, through a contract field or its target selector. */
enum TargetKind {
  ASSETS(CONTRACT_ELEMENT_CONTENT_KEY_ASSETS, "assets") {
    @Override
    boolean isChosenIn(ChosenTargets chosen) {
      return !chosen.assets().isEmpty();
    }

    @Override
    void addTo(Inject inject, ChosenTargets chosen) {
      inject.setAssets(union(inject.getAssets(), chosen.assets()));
    }
  },
  ASSET_GROUPS(CONTRACT_ELEMENT_CONTENT_KEY_ASSET_GROUPS, "asset-groups") {
    @Override
    boolean isChosenIn(ChosenTargets chosen) {
      return !chosen.assetGroups().isEmpty();
    }

    @Override
    void addTo(Inject inject, ChosenTargets chosen) {
      inject.setAssetGroups(union(inject.getAssetGroups(), chosen.assetGroups()));
    }
  },
  TEAMS(CONTRACT_ELEMENT_CONTENT_KEY_TEAMS, null) {
    @Override
    boolean isChosenIn(ChosenTargets chosen) {
      return !chosen.teams().isEmpty();
    }

    // "All teams" of the package meant the teams of the platform it was exported from.
    @Override
    void addTo(Inject inject, ChosenTargets chosen) {
      inject.setAllTeams(false);
      inject.setTeams(union(inject.getTeams(), chosen.teams()));
    }
  },
  HOSTS(MANUAL_TARGETS_CONTENT_KEY, MANUAL_TARGET_SELECTOR) {
    @Override
    boolean isChosenIn(ChosenTargets chosen) {
      return !chosen.hosts().isEmpty();
    }

    @Override
    void addTo(Inject inject, ChosenTargets chosen) {
      ObjectNode content = contentOf(inject);
      Set<String> hosts = new LinkedHashSet<>(contentManualTargets(content));
      hosts.addAll(chosen.hosts());
      content.put(MANUAL_TARGETS_CONTENT_KEY, String.join(",", hosts));
      inject.setContent(content);
    }
  };

  /** The kinds a contract takes as fields of their own. */
  static final List<TargetKind> FIELDS = List.of(ASSETS, ASSET_GROUPS, TEAMS);

  /** The kinds a target selector chooses between, in the order an install prefers them. */
  static final List<TargetKind> SELECTABLE = List.of(ASSET_GROUPS, ASSETS, HOSTS);

  private final String field;
  private final String selectorValue;

  TargetKind(String field, String selectorValue) {
    this.field = field;
    this.selectorValue = selectorValue;
  }

  abstract boolean isChosenIn(ChosenTargets chosen);

  abstract void addTo(Inject inject, ChosenTargets chosen);

  String field() {
    return field;
  }

  void selectOn(Inject inject) {
    ObjectNode content = contentOf(inject);
    content.put(TARGET_SELECTOR_CONTENT_KEY, selectorValue);
    inject.setContent(content);
  }

  static Optional<TargetKind> selectedOn(Inject inject) {
    String selected =
        Optional.ofNullable(inject.getContent())
            .map(content -> content.path(TARGET_SELECTOR_CONTENT_KEY).asText())
            .orElse("");
    return SELECTABLE.stream().filter(kind -> kind.selectorValue.equals(selected)).findFirst();
  }

  private static ObjectNode contentOf(Inject inject) {
    return inject.getContent() == null
        ? JsonNodeFactory.instance.objectNode()
        : inject.getContent().deepCopy();
  }

  private static <T extends Base> List<T> union(List<T> current, List<T> added) {
    Set<String> ids = current.stream().map(Base::getId).collect(Collectors.toSet());
    List<T> merged = new ArrayList<>(current);
    added.stream().filter(item -> ids.add(item.getId())).forEach(merged::add);
    return merged;
  }
}
