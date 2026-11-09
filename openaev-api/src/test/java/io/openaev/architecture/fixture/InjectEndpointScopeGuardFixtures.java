package io.openaev.architecture.fixture;

import io.openaev.aop.AccessControl;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.database.repository.InjectRepository;
import io.openaev.rest.inject.service.InjectService;
import java.util.function.Supplier;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Endpoints naming an inject, in each shape the scope guard rule must tell apart. They are mapped
 * with {@code @RequestMapping}: the build's endpoint rules read the verb-specific mappings only.
 */
public class InjectEndpointScopeGuardFixtures {

  private final InjectService injectService;
  private final InjectRepository injectRepository;

  public InjectEndpointScopeGuardFixtures(
      InjectService injectService, InjectRepository injectRepository) {
    this.injectService = injectService;
    this.injectRepository = injectRepository;
  }

  @RequestMapping("/guarded-directly")
  @AccessControl(
      resourceId = "#injectId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.INJECT)
  public void guardedDirectly(TxCtx ctx, String injectId) {
    injectService.existsInScope(ctx, injectId);
  }

  @RequestMapping("/guarded-through-a-helper")
  @AccessControl(
      resourceId = "#injectId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.ATOMIC_TESTING)
  public void guardedThroughAHelper(TxCtx ctx, String injectId) {
    requireInScope(ctx, injectId);
  }

  @RequestMapping("/guarded-secret")
  @AccessControl(
      resourceId = "#injectId",
      actionPerformed = Action.RESOLVE,
      resourceType = ResourceType.INJECT_SECRET)
  public void guardedSecret(TxCtx ctx, String injectId) {
    injectService.existsInScope(ctx, injectId);
  }

  @RequestMapping("/guarded-in-a-lambda")
  @AccessControl(
      resourceId = "#injectId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.INJECT)
  public Supplier<Object> guardedInALambda(TxCtx ctx, String injectId) {
    return () -> injectService.inject(ctx, injectId);
  }

  @RequestMapping("/guarded-with-the-id-second")
  @AccessControl(
      resourceId = "#injectId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.INJECT)
  public void guardedWithTheIdSecond(String other, String injectId, TxCtx ctx) {
    requireInScope(ctx, injectId);
  }

  @RequestMapping("/guard-on-another-id")
  @AccessControl(
      resourceId = "#injectId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECT)
  public void guardOnAnotherId(TxCtx ctx, String injectId, String otherId) {
    injectService.existsInScope(ctx, otherId);
  }

  @RequestMapping("/guard-after-the-write")
  @AccessControl(
      resourceId = "#injectId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECT)
  public void guardAfterTheWrite(TxCtx ctx, String injectId) {
    injectRepository.deleteById(injectId);
    injectService.existsInScope(ctx, injectId);
  }

  @RequestMapping("/guard-after-the-write-in-the-helper")
  @AccessControl(
      resourceId = "#injectId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECT)
  public void guardAfterTheWriteInTheHelper(TxCtx ctx, String injectId) {
    deleteThenRequire(ctx, injectId);
  }

  @RequestMapping("/unguarded")
  @AccessControl(
      resourceId = "#injectId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECT)
  public void unguarded(String injectId) {
    injectService.inject(injectId);
  }

  @RequestMapping("/guard-too-far-away")
  @AccessControl(
      resourceId = "#injectId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.ATOMIC_TESTING)
  public void guardTooFarAway(TxCtx ctx, String injectId) {
    hopOne(ctx, injectId);
  }

  @RequestMapping("/no-resource-id")
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  public void noResourceId() {}

  @RequestMapping("/other-resource")
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public void otherResource(String scenarioId) {}

  public void notAnEndpoint(String injectId) {
    injectService.inject(injectId);
  }

  private void requireInScope(TxCtx ctx, String injectId) {
    injectService.inject(ctx, injectId);
  }

  private void deleteThenRequire(TxCtx ctx, String injectId) {
    injectRepository.deleteById(injectId);
    injectService.existsInScope(ctx, injectId);
  }

  private void hopOne(TxCtx ctx, String injectId) {
    hopTwo(ctx, injectId);
  }

  private void hopTwo(TxCtx ctx, String injectId) {
    hopThree(ctx, injectId);
  }

  private void hopThree(TxCtx ctx, String injectId) {
    hopFour(ctx, injectId);
  }

  private void hopFour(TxCtx ctx, String injectId) {
    injectService.existsInScope(ctx, injectId);
  }
}
