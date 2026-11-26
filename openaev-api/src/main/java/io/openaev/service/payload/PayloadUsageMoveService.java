package io.openaev.service.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.Step;
import io.openaev.database.model.User;
import io.openaev.healthcheck.dto.HealthCheck;
import io.openaev.healthcheck.utils.HealthCheckUtils;
import io.openaev.service.PermissionService;
import io.openaev.service.UserService;
import io.openaev.service.chaining.StepService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moves scenario injects, atomic testings and chained steps from one version of a payload to
 * another. Each one is checked on its own: the others move even when one stays.
 */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class PayloadUsageMoveService {

  /** Why a usage stays on its version. */
  public enum Refusal {
    NOT_ON_THIS_VERSION,
    MISSING_RIGHT,
    NOT_COMPATIBLE
  }

  public record Moves(List<String> moved, Map<String, Refusal> refused) {}

  private final PayloadVersionService payloadVersionService;
  private final StepService stepService;
  private final HealthCheckUtils healthCheckUtils;
  private final PermissionService permissionService;
  private final UserService userService;
  private final ObjectMapper mapper;

  // -- UPDATE --

  public Moves move(
      TxCtx ctx, String tenantId, String actionId, String targetPayloadId, List<String> usageIds) {
    InjectorContract source = payloadVersionService.payloadAction(tenantId, actionId);
    InjectorContract target =
        payloadVersionService.otherVersionAction(tenantId, source.getPayload(), targetPayloadId);
    Map<String, Usage> onSource = usagesOn(tenantId, source);
    User user = userService.currentUser();

    List<String> moved = new ArrayList<>();
    Map<String, Refusal> refused = new LinkedHashMap<>();
    for (String usageId : new LinkedHashSet<>(usageIds)) {
      Usage usage = onSource.get(usageId);
      Optional<Refusal> refusal = refusal(ctx, user, usage, source, target);
      if (refusal.isPresent()) {
        refused.put(usageId, refusal.get());
      } else {
        usage.moveTo(target);
        moved.add(usageId);
      }
    }
    return new Moves(moved, refused);
  }

  private Map<String, Usage> usagesOn(String tenantId, InjectorContract action) {
    Map<String, Usage> usages = new HashMap<>();
    payloadVersionService
        .injectsOn(tenantId, action)
        .forEach(inject -> usages.put(inject.getId(), new InjectUsage(inject)));
    payloadVersionService
        .stepsOn(tenantId, action)
        .forEach(step -> usages.put(step.getId(), new StepUsage(step)));
    return usages;
  }

  private Optional<Refusal> refusal(
      TxCtx ctx, User user, Usage usage, InjectorContract source, InjectorContract target) {
    if (usage == null) {
      return Optional.of(Refusal.NOT_ON_THIS_VERSION);
    }
    if (!permissionService.hasPermissionInTenant(
        ctx, user, usage.id(), usage.type(), Action.WRITE)) {
      return Optional.of(Refusal.MISSING_RIGHT);
    }
    // Only what the target version adds is refused: a usage already incomplete can still move.
    List<String> added = errors(usage.contentChecks(target));
    errors(usage.contentChecks(source)).forEach(added::remove);
    return added.isEmpty() ? Optional.empty() : Optional.of(Refusal.NOT_COMPATIBLE);
  }

  private static List<String> errors(List<HealthCheck> checks) {
    return checks.stream()
        .filter(check -> check.getStatus() == HealthCheck.Status.ERROR)
        .map(check -> check.getType() + " " + check.getDetail())
        .collect(Collectors.toCollection(ArrayList::new));
  }

  /** An inject or a chained step running the source version. */
  private interface Usage {

    String id();

    ResourceType type();

    List<HealthCheck> contentChecks(InjectorContract action);

    void moveTo(InjectorContract action);
  }

  private final class InjectUsage implements Usage {

    private final Inject inject;

    private InjectUsage(Inject inject) {
      this.inject = inject;
    }

    @Override
    public String id() {
      return inject.getId();
    }

    @Override
    public ResourceType type() {
      return ResourceType.INJECT;
    }

    @Override
    public List<HealthCheck> contentChecks(InjectorContract action) {
      return healthCheckUtils.runContentChecks(action, inject);
    }

    @Override
    public void moveTo(InjectorContract action) {
      inject.setInjectorContract(action);
    }
  }

  private final class StepUsage implements Usage {

    private final Step step;

    private StepUsage(Step step) {
      this.step = step;
    }

    @Override
    public String id() {
      return step.getId();
    }

    @Override
    public ResourceType type() {
      return ResourceType.STEP;
    }

    @Override
    public List<HealthCheck> contentChecks(InjectorContract action) {
      StepInject inject = stepInject();
      return healthCheckUtils.runContentChecks(
          action,
          inject.content(),
          inject.allTeams(),
          inject.teams(),
          inject.assets(),
          inject.assetGroups(),
          inject.secretReferences());
    }

    @Override
    public void moveTo(InjectorContract action) {
      stepService.updateStepTemplateInjectorContract(step, action);
    }

    private StepInject stepInject() {
      try {
        return mapper.readValue(step.getData(), StepInject.class);
      } catch (JsonProcessingException e) {
        throw new IllegalArgumentException("Unreadable data of step " + step.getId(), e);
      }
    }
  }

  /** What the content checks read in the inject a chained step creates. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record StepInject(
      @JsonProperty("inject_content") ObjectNode content,
      @JsonProperty("inject_all_teams") boolean allTeams,
      @JsonProperty("inject_teams") List<String> teams,
      @JsonProperty("inject_assets") List<String> assets,
      @JsonProperty("inject_asset_groups") List<String> assetGroups,
      @JsonProperty("inject_secret_references") List<String> secretReferences) {

    StepInject {
      teams = teams == null ? List.of() : teams;
      assets = assets == null ? List.of() : assets;
      assetGroups = assetGroups == null ? List.of() : assetGroups;
      secretReferences = secretReferences == null ? List.of() : secretReferences;
    }
  }
}
