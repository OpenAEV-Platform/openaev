package io.openaev.service.readiness;

import io.openaev.database.model.Asset;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectDocument;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Team;
import jakarta.validation.constraints.NotNull;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * What an inject runs and against whom: its content (arguments and expectations included), its
 * action, its targets (assets, asset groups, teams, all teams) and its documents. A change of any
 * of them puts a scheduled run back in a not-ready state (see {@link LaunchReadinessService}).
 * Name, description, tags, ordering and trigger timing are not part of it.
 */
public final class InjectSensitiveFields {

  private InjectSensitiveFields() {}

  public static String fingerprint(@NotNull final Inject inject) {
    return String.join(
        "|",
        String.valueOf(inject.getContent()),
        inject.getInjectorContract().map(InjectorContract::getId).orElse(""),
        ids(inject.getAssets(), Asset::getId),
        ids(inject.getAssetGroups(), AssetGroup::getId),
        ids(inject.getTeams(), Team::getId),
        String.valueOf(inject.isAllTeams()),
        ids(inject.getDocuments(), InjectSensitiveFields::documentId));
  }

  private static String documentId(InjectDocument document) {
    return document.getDocument().getId();
  }

  private static <T> String ids(Collection<T> items, Function<T, String> id) {
    if (items == null) {
      return "";
    }
    return items.stream()
        .filter(Objects::nonNull)
        .map(id)
        .sorted()
        .collect(Collectors.joining(","));
  }
}
