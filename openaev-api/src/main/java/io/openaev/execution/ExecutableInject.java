package io.openaev.execution;

import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.openaev.database.model.*;
import io.openaev.rest.inject.service.AssetToExecute;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.hibernate.Hibernate;
import org.springframework.web.multipart.MultipartFile;

@Getter
public class ExecutableInject {

  private final Injection injection;
  private final int teamSize;
  private final boolean direct;
  private final boolean runtime;
  private final int documentSize;
  private final List<Team> teams;
  private final String exerciseId;
  private final List<Asset> assets;
  private final List<AssetGroup> assetGroups;
  private final List<ExecutionContext> users;
  private final boolean chainingExecution;
  private final String stepId;

  @JsonIgnore private final List<MultipartFile> directAttachments = new ArrayList<>();
  @JsonIgnore private List<AssetToExecute> assetsToExecute;

  /**
   * Domain entities backing this inject's expectations (e.g. challenges or articles), pre-resolved
   * by the executor so expectation behaviors don't reload them. {@code null} until cached.
   */
  @JsonIgnore private List<?> expectationContext;

  public ExecutableInject(
      boolean runtime,
      boolean direct,
      Injection injection,
      List<Team> teams,
      List<Asset> assets,
      List<AssetGroup> assetGroups,
      List<ExecutionContext> users) {
    this(runtime, direct, injection, teams, assets, assetGroups, users, false, null);
  }

  public ExecutableInject(
      boolean runtime,
      boolean direct,
      Injection injection,
      List<Team> teams,
      List<Asset> assets,
      List<AssetGroup> assetGroups,
      List<ExecutionContext> users,
      boolean chainingExecution) {
    this(runtime, direct, injection, teams, assets, assetGroups, users, chainingExecution, null);
  }

  /**
   * @param stepId the chaining step this execution was created for, or {@code null} outside
   *     chaining (e.g. the time-based scheduler). Lets executors that need a stable, already
   *     persisted reference (unlike the inject, still uncommitted at this point) attribute tracking
   *     rows to the step instead - see {@code PhishingExecutor}.
   */
  public ExecutableInject(
      boolean runtime,
      boolean direct,
      Injection injection,
      List<Team> teams,
      List<Asset> assets,
      List<AssetGroup> assetGroups,
      List<ExecutionContext> users,
      boolean chainingExecution,
      String stepId) {
    this.injection = injection;
    this.exerciseId = ofNullable(injection.getExercise()).map(Exercise::getId).orElse(null);
    this.runtime = runtime;
    this.direct = direct;
    this.chainingExecution = chainingExecution;
    this.stepId = stepId;
    this.users = users;
    this.teams = teams;
    this.teams.forEach(team -> Hibernate.initialize(team.getTags()));
    this.assets = assets;
    this.assets.forEach(asset -> Hibernate.initialize(asset.getTags()));
    this.assetGroups = assetGroups;
    this.assetGroups.forEach(assetGroup -> Hibernate.initialize(assetGroup.getTags()));
    this.teamSize = teams.size();
    this.documentSize = injection.getInject().getDocuments().size();
  }

  public ExecutableInject(
      boolean runtime, boolean direct, Injection injection, List<ExecutionContext> users) {
    this(runtime, direct, injection, List.of(), List.of(), List.of(), users, false, null);
  }

  public void addDirectAttachment(MultipartFile file) {
    this.directAttachments.add(file);
  }

  public void cacheAssetsToExecute(List<AssetToExecute> resolvedAssetsToExecute) {
    this.assetsToExecute =
        resolvedAssetsToExecute != null ? List.copyOf(resolvedAssetsToExecute) : null;
  }

  /**
   * Caches the domain entities backing this inject's expectations so behaviors can reuse them
   * instead of reloading from the database.
   *
   * @param entities the pre-resolved entities (e.g. challenges or articles)
   */
  public void cacheExpectationContext(List<?> entities) {
    this.expectationContext = entities != null ? List.copyOf(entities) : null;
  }

  /**
   * Returns the cached expectation-context entities of the given type, or an empty list if none
   * were cached.
   *
   * @param type the expected entity type
   * @param <T> the entity type
   */
  public <T> List<T> getExpectationContext(Class<T> type) {
    if (this.expectationContext == null) {
      return List.of();
    }
    return this.expectationContext.stream().filter(type::isInstance).map(type::cast).toList();
  }
}
