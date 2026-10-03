package io.openaev.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static java.util.Map.entry;

import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.TryCatchBlock;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The build-time guard behind #8102: a handled error that still dies at commit.
 *
 * <p>The shape. A transaction is open (an entrypoint's {@code @Transactional}, or any transactional
 * method above the call). Inside it, a call crosses a Spring proxy into another bean's
 * {@code @Transactional} method, which throws. That inner interceptor does not own the transaction,
 * so instead of rolling back it marks the shared one rollback-only. The caller catches the
 * exception, treats it as handled and returns normally. The commit then fails with {@code
 * UnexpectedRollbackException}, and a caller that was told "handled" gets a 500 it cannot tell from
 * a real server error. A queue-backed caller retries for ever.
 *
 * <p>Note that Spring does not roll back on a checked exception by default, so a broad {@code
 * rollbackFor = Exception.class} on the inner boundary is half of what makes this fire. The other
 * half is the wrapping transaction. The fix belongs on the inner boundary ({@code noRollbackFor},
 * or a return value instead of an exception): {@code noRollbackFor} on the catching method does
 * nothing, because that interceptor never sees an exception caught inside the method it wraps.
 *
 * <p>What the rule detects, from bytecode: a try block containing a call to another class's
 * {@code @Transactional} method whose propagation lets it participate in the caller's transaction,
 * where the caught throwable types intersect that method's rollback rules, and where the catching
 * method is itself under a transaction. Every such site must be reviewed and listed in {@link
 * #REVIEWED_SITES} with a reason.
 *
 * <p>Known limits, all in the direction of missing sites rather than inventing them:
 *
 * <ul>
 *   <li>Whether the catch block actually swallows is not visible here: a catch that rethrows is
 *       harmless but still has to be listed. The reason column says which it is.
 *   <li>"Under a transaction" is proven only from the catching method's own {@code @Transactional}
 *       (or its class's). A method that is transactional only because of its callers, and a lambda
 *       body passed to {@link io.openaev.context.TenantScopedTransaction}, are not seen.
 *   <li>Calls through an interface whose implementation carries the annotation resolve to the
 *       interface, which has none.
 * </ul>
 */
final class HandledErrorRollbackRules {

  private HandledErrorRollbackRules() {}

  /** Propagations that open their own transaction, or none: the inner failure stays local. */
  private static final Set<String> NON_PARTICIPATING =
      Set.of("REQUIRES_NEW", "NOT_SUPPORTED", "NEVER");

  /** How far a call chain is followed from the try block before the walk gives up. */
  private static final int MAX_CALL_DEPTH = 5;

  private static final String TRANSACTIONAL =
      "org.springframework.transaction.annotation.Transactional";

  /**
   * Reviewed sites, keyed by {@code CatchingClass#method -> InnerClass.method}: the catching method
   * AND the transactional boundary it reaches. Keying on the pair rather than on the method alone
   * is deliberate. A method-only key would let a new call into a different transactional boundary
   * be added inside an already-listed method without the build noticing, which is exactly the
   * silent regression this guard exists to stop.
   *
   * <p>The value is why the site is accepted today. "rethrows" means the catch does not return a
   * success, so the rollback is honoured and the caller gets the error; those are safe. "known
   * defect" means the catch does return a success and the site is genuinely broken: it is recorded
   * here so the build stays honest about it, with the issue that tracks the fix.
   */
  static final Map<String, String> REVIEWED_SITES =
      Map.ofEntries(
          // --- Rethrows: the exception leaves the handler, so the rollback is honoured. ---
          entry(
              "AutonomousRunService#convertToManual -> WorkflowService.copyScenarioChainingWorkflowAsManual",
              "rethrows as ResponseStatusException(400)"),
          entry(
              "AutonomousRunService#deleteAttackPathStep -> WorkflowService.deleteChainedStep",
              "rethrows as ResponseStatusException(400)"),
          entry(
              "AutonomousRunService#evaluateAttackPath -> WorkflowService.evaluateWorkflowProgress",
              "rethrows as ResponseStatusException(400)"),
          entry(
              "AutonomousRunService#promoteToRealRun -> WorkflowService.startWorkflowByScenarioIdAndSimulation",
              "rethrows as ResponseStatusException(400)"),
          entry(
              "AutonomousRunService#restart -> WorkflowService.provisionSimulationTemplateWorkflow",
              "rethrows as ResponseStatusException(400)"),
          entry(
              "AutonomousRunService#restart -> WorkflowService.startWorkflowByScenarioIdAndSimulation",
              "rethrows as ResponseStatusException(400)"),
          entry(
              "AutonomousRunService#updateAttackPathStep -> WorkflowService.updateChainedStep",
              "rethrows as ResponseStatusException(400)"),
          entry(
              "CollectorApi#registerCollector -> CollectorService.register",
              "rethrows as RuntimeException"),
          entry(
              "ConnectorInstanceService#deleteById -> ManagerCreator.createManager",
              "rethrows as ConnectorStatusException"),
          entry(
              "InjectExecutionStep#run -> AssetGroupService.assetsFromAssetGroup",
              "rethrows as ChainingException"),
          entry(
              "InjectExecutionStep#run -> ConditionService.findAllConditionsByStepIds",
              "rethrows as ChainingException"),
          entry(
              "InjectExecutionStep#run -> ConnectorInstanceService.findByExecutorId",
              "rethrows as ChainingException"),
          entry(
              "InjectExecutionStep#run -> ConnectorInstanceService.hasStartedConnectorInstanceForInjector",
              "rethrows as ChainingException"),
          entry(
              "InjectExecutionStep#run -> InjectStatusService.initializeInjectStatus",
              "rethrows as ChainingException"),
          entry(
              "InjectExecutionStep#run -> InjectStatusService.saveAndStreamInject",
              "rethrows as ChainingException"),
          entry(
              "InjectExecutionStep#run -> ManagerCreator.createManager",
              "rethrows as ChainingException"),
          entry(
              "InjectExecutionStep#run -> ServiceAccountPrivilegeService.getTokenUserServiceAccountByTenant",
              "rethrows as ChainingException"),
          entry(
              "InjectExecutionStep#run -> UrlAccessTokenService.generateTokenUrl",
              "rethrows as ChainingException"),
          entry(
              "ManagerCreator#createManager -> IntegrationFactory.findRelatedInstances",
              "rethrows as RuntimeException"),
          entry(
              "ManagerCreator#createManager -> IntegrationFactory.sync",
              "rethrows as RuntimeException"),

          // --- Known defects: the catch returns a success and the commit then fails. ---
          entry(
              "StixApi#processBundle -> SecurityCoverageService.handleSecurityCoverageProcessing",
              "known defect #8102, fix in progress on the inner boundary"),
          entry(
              "SimulationInjectApi#executeInject -> AssetGroupService.assetsFromAssetGroup",
              "known defect, reported by the #8102 sweep"),
          entry(
              "SimulationInjectApi#executeInject -> ConnectorInstanceService.findByExecutorId",
              "known defect, reported by the #8102 sweep"),
          entry(
              "SimulationInjectApi#executeInject -> ConnectorInstanceService.hasStartedConnectorInstanceForInjector",
              "known defect, reported by the #8102 sweep"),
          entry(
              "SimulationInjectApi#executeInject -> InjectStatusService.initializeInjectStatus",
              "known defect, reported by the #8102 sweep"),
          entry(
              "SimulationInjectApi#executeInject -> InjectStatusService.saveAndStreamInject",
              "known defect, reported by the #8102 sweep"),
          entry(
              "SimulationInjectApi#executeInject -> ManagerCreator.createManager",
              "known defect, reported by the #8102 sweep"),
          entry(
              "SimulationInjectApi#executeInject -> ServiceAccountPrivilegeService.getTokenUserServiceAccountByTenant",
              "known defect, reported by the #8102 sweep"),
          entry(
              "BatchingInjectStatusService#processTenantCallbacks -> InjectRepository.updateUpdatedAt",
              "known defect, reported by the #8102 sweep; queue ack semantics are the inject team's call"),
          entry(
              "InjectExecutionService#handleInjectExecutionCallback -> AssetGroupService.assetsFromAssetGroup",
              "known defect, reported by the #8102 sweep"),
          entry(
              "InjectExecutionService#handleInjectExecutionCallback -> InjectRepository.updateUpdatedAt",
              "known defect, reported by the #8102 sweep"),
          entry(
              "ExecutorApi#getOpenAevAgentInstallerToken -> ServiceAccountPrivilegeService.getTokenUserServiceAccountByTenant",
              "known defect, reported by the #8102 sweep; the documented 404 becomes a 500"),
          entry(
              "WorkflowService#deleteAllScenarioSteps -> StepService.deleteStepTemplate",
              "known defect, reported by the #8102 sweep"),
          entry(
              "WorkflowService#deleteAllScenarioSteps -> ConditionService.deleteAllConditionsByWorkflowId",
              "known defect, reported by the #8102 sweep"),
          entry(
              "PhishingTrackingService#captureCredentials -> FindingService.createFindings",
              "known defect, reported by the #8102 sweep; the victim's tracking write is lost with it"),
          entry(
              "XtmOneService#autoRegister -> TenantSettingsService.resolveSettingValue",
              "known defect, reported by the #8102 sweep; contained by the caller's own catch"));

  static final ArchRule HANDLED_ERRORS_THAT_DIE_AT_COMMIT_ARE_REVIEWED =
      classes()
          .should(notCatchARollbackMarkingCallUnreviewed())
          .because(
              "catching an exception thrown out of another bean's @Transactional method does not"
                  + " un-mark the shared transaction: the handler returns a success and the commit"
                  + " throws UnexpectedRollbackException. Each site is reviewed and listed");

  private static ArchCondition<JavaClass> notCatchARollbackMarkingCallUnreviewed() {
    return new ArchCondition<>("not catch a rollback-marking call outside the reviewed list") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        for (JavaCodeUnit codeUnit : javaClass.getCodeUnits()) {
          if (!opensOrJoinsATransaction(codeUnit)) {
            continue;
          }
          for (TryCatchBlock block : codeUnit.getTryCatchBlocks()) {
            for (JavaMethod target : participatingTransactionalCalls(block)) {
              if (!rollbackRulesCover(target, block.getCaughtThrowables())) {
                continue;
              }
              String site = siteKey(codeUnit, target);
              if (REVIEWED_SITES.containsKey(site)) {
                continue;
              }
              events.add(
                  SimpleConditionEvent.violated(
                      javaClass,
                      ("%s catches %s, which marks the shared transaction rollback-only, so the"
                              + " commit then throws UnexpectedRollbackException. If the catch"
                              + " returns a success, the caller gets a 500 it cannot tell from a"
                              + " real server error. Review it and add \"%s\" to REVIEWED_SITES"
                              + " with a reason, at %s")
                          .formatted(
                              site, caughtNames(block), site, block.getSourceCodeLocation())));
            }
          }
        }
      }
    };
  }

  /** The catching method is provably inside a transaction: its own, or its class's. */
  private static boolean opensOrJoinsATransaction(JavaCodeUnit codeUnit) {
    Optional<String> propagation =
        transactionalPropagation(codeUnit).or(() -> transactionalPropagation(codeUnit.getOwner()));
    return propagation.isPresent()
        && !"NEVER".equals(propagation.get())
        && !"NOT_SUPPORTED".equals(propagation.get());
  }

  /**
   * The participating {@code @Transactional} boundaries a try block can reach, following calls
   * through non-transactional beans as well as direct ones.
   *
   * <p>Depth matters: on the instance this guard was written for (#8102), {@code
   * StixApi.processBundle} catches around {@code StixService.processBundle}, which carries no
   * {@code @Transactional} at all and simply forwards to {@code
   * SecurityCoverageService.handleSecurityCoverageProcessing}, which does. A rule that looked only
   * at the call in the try block would have missed the very site it exists for.
   *
   * <p>The walk stops at the first transactional boundary on each path (below it, the exception is
   * that boundary's business, not the caller's), stays inside {@code io.openaev}, and is bounded by
   * {@link #MAX_CALL_DEPTH} so an import of the whole application still terminates quickly.
   */
  private static Set<JavaMethod> participatingTransactionalCalls(TryCatchBlock block) {
    Set<JavaMethod> targets = new LinkedHashSet<>();
    for (JavaAccess<?> access : block.getAccessesContainedInTryBlock()) {
      if (access instanceof JavaMethodCall call) {
        call.getTarget()
            .resolveMember()
            .ifPresent(
                target ->
                    collectBoundaries(
                        target, block.getOwner().getOwner(), targets, new HashSet<>(), 0));
      }
    }
    return targets;
  }

  private static void collectBoundaries(
      JavaMethod method, JavaClass caller, Set<JavaMethod> found, Set<String> visited, int depth) {
    if (depth > MAX_CALL_DEPTH || !visited.add(method.getFullName())) {
      return;
    }
    if (!method.getOwner().getPackageName().startsWith("io.openaev")) {
      return;
    }
    if (!method.getOwner().equals(caller)) {
      // A call that crosses into another bean goes through the Spring proxy, so this method's
      // @Transactional (its own, or its class's) is a real interceptor that can mark the
      // transaction. An intra-class call bypasses the proxy and marks nothing.
      Optional<String> propagation =
          transactionalPropagation(method).or(() -> transactionalPropagation(method.getOwner()));
      if (propagation.isPresent()) {
        if (!NON_PARTICIPATING.contains(propagation.get())) {
          found.add(method);
        }
        // Either way this is where the exception is handled: stop descending this path.
        return;
      }
    }
    for (JavaMethodCall call : method.getMethodCallsFromSelf()) {
      call.getTarget()
          .resolveMember()
          .ifPresent(next -> collectBoundaries(next, method.getOwner(), found, visited, depth + 1));
    }
  }

  /** Spring's rollback rules for the inner boundary, against what the caller catches. */
  private static boolean rollbackRulesCover(JavaMethod target, Set<JavaClass> caught) {
    Set<String> rollbackFor = classArray(target, "rollbackFor");
    Set<String> noRollbackFor = classArray(target, "noRollbackFor");
    Set<String> rollbackNames =
        rollbackFor.isEmpty()
            ? Set.of(RuntimeException.class.getName(), Error.class.getName())
            : rollbackFor;
    for (JavaClass caughtType : caught) {
      if (noRollbackFor.stream().anyMatch(exempt -> isAssignableTo(caughtType, exempt))) {
        continue;
      }
      boolean intersects =
          rollbackNames.stream()
              .anyMatch(
                  rollback ->
                      isAssignableTo(caughtType, rollback)
                          || isAssignableFrom(caughtType, rollback));
      if (intersects) {
        return true;
      }
    }
    return false;
  }

  private static boolean isAssignableTo(JavaClass type, String name) {
    return type.getName().equals(name)
        || type.getAllRawSuperclasses().stream().anyMatch(s -> s.getName().equals(name));
  }

  private static boolean isAssignableFrom(JavaClass type, String name) {
    return type.getName().equals(name)
        || type.getAllSubclasses().stream().anyMatch(s -> s.getName().equals(name));
  }

  /**
   * The rollback rules that apply to this boundary. Read from the method's own
   * {@code @Transactional} when it has one, otherwise from the class's: a method-level annotation
   * replaces the class-level one wholesale, it does not merge with it. Missing the class-level
   * fallback made the rule report {@code UrlAccessTokenApi#access}, whose inner boundary declares
   * {@code noRollbackFor = AccessDeniedException.class} on the class.
   */
  private static Set<String> classArray(JavaMethod method, String attribute) {
    return method
        .tryGetAnnotationOfType(TRANSACTIONAL)
        .map(annotation -> classArray(annotation, attribute))
        .orElseGet(
            () ->
                method
                    .getOwner()
                    .tryGetAnnotationOfType(TRANSACTIONAL)
                    .map(annotation -> classArray(annotation, attribute))
                    .orElseGet(Set::of));
  }

  private static Set<String> classArray(
      com.tngtech.archunit.core.domain.JavaAnnotation<?> annotation, String attribute) {
    Set<String> names = new LinkedHashSet<>();
    annotation
        .tryGetExplicitlyDeclaredProperty(attribute)
        .ifPresent(
            value -> {
              for (Object entry : (Object[]) value) {
                names.add(((JavaClass) entry).getName());
              }
            });
    return names;
  }

  private static Optional<String> transactionalPropagation(JavaCodeUnit codeUnit) {
    return propagationOf(codeUnit.tryGetAnnotationOfType(TRANSACTIONAL).orElse(null));
  }

  private static Optional<String> transactionalPropagation(JavaMethod method) {
    return propagationOf(method.tryGetAnnotationOfType(TRANSACTIONAL).orElse(null));
  }

  private static Optional<String> transactionalPropagation(JavaClass javaClass) {
    return propagationOf(javaClass.tryGetAnnotationOfType(TRANSACTIONAL).orElse(null));
  }

  private static Optional<String> propagationOf(
      com.tngtech.archunit.core.domain.JavaAnnotation<?> annotation) {
    if (annotation == null) {
      return Optional.empty();
    }
    return Optional.of(
        annotation
            .tryGetExplicitlyDeclaredProperty("propagation")
            .map(value -> ((com.tngtech.archunit.core.domain.JavaEnumConstant) value).name())
            .orElse("REQUIRED"));
  }

  private static String siteKey(JavaCodeUnit codeUnit, JavaMethod boundary) {
    return "%s#%s -> %s.%s"
        .formatted(
            codeUnit.getOwner().getSimpleName(),
            codeUnit.getName(),
            boundary.getOwner().getSimpleName(),
            boundary.getName());
  }

  private static String caughtNames(TryCatchBlock block) {
    return block.getCaughtThrowables().stream()
        .map(JavaClass::getSimpleName)
        .reduce((a, b) -> a + "/" + b)
        .orElse("(finally)");
  }
}
