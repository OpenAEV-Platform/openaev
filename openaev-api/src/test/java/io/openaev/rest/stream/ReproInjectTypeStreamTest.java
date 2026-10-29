package io.openaev.rest.stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.audit.BaseEvent;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Injector;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.InjectRepository;
import io.openaev.execution.ExecutableInject;
import io.openaev.healthcheck.utils.HealthCheckUtils;
import io.openaev.helper.InjectHelper;
import io.openaev.scheduler.TenantScopedJobRunner;
import io.openaev.scheduler.jobs.InjectsExecutionJob;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.TeamFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.TeamComposer;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@WithMockUser(isAdmin = true)
@RecordApplicationEvents
class ReproInjectTypeStreamTest extends IntegrationTest {

  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private TenantScopedJobRunner runner;
  @Autowired private InjectRepository injectRepository;
  @Autowired private InjectComposer injectComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private TeamComposer teamComposer;
  @Autowired private UserComposer userComposer;
  @Autowired private InjectorContractFixture injectorContractFixture;
  @Autowired private InjectorFixture injectorFixture;
  @Autowired private InjectHelper injectHelper;
  @Autowired private InjectsExecutionJob job;
  @Autowired private ObjectMapper mapper;
  @Autowired private ApplicationEvents events;
  @MockitoSpyBean private HealthCheckUtils healthCheckUtils;

  @Test
  void repro() throws Exception {
    String tenant = Tenant.DEFAULT_TENANT_UUID;
    TenantContext.setCurrentTenant(tenant);
    try {
      String injectId =
          tenantTx.execute(
              TxCtx.forTenant(tenant),
              () -> {
                UserComposer.Composer u =
                    userComposer.forUser(UserFixture.getUserWithDefaultEmail());
                TeamComposer.Composer team =
                    teamComposer.forTeam(TeamFixture.getDefaultTeam()).withUser(u);
                Exercise exercise =
                    exerciseComposer
                        .forExercise(
                            ExerciseFixture.createRunningAttackExercise(
                                Instant.now().minus(1, ChronoUnit.MINUTES)))
                        .withTeam(team)
                        .withTeamUsers()
                        .persist()
                        .get();
                InjectorContract contract =
                    injectorContractFixture.getWellKnownSingleEmailContract();
                Injector emailInjector = injectorFixture.getWellKnownEmailInjector(true);
                contract.addInjector(emailInjector);
                Inject inject =
                    injectComposer
                        .forInject(InjectFixture.getInjectForEmailContract(contract))
                        .withTeam(team)
                        .persist()
                        .get();
                inject.setExercise(exercise);
                inject.setInjector(emailInjector);
                injectRepository.save(inject);
                return inject.getId();
              });
      doReturn(List.of()).when(healthCheckUtils).runContentChecks(any(Inject.class));

      ExecutableInject ei =
          injectHelper.getInjectsToRun().stream()
              .filter(e -> injectId.equals(e.getInjection().getInject().getId()))
              .findFirst()
              .orElseThrow();
      Method m =
          InjectsExecutionJob.class.getDeclaredMethod("executeInject", ExecutableInject.class);
      m.setAccessible(true);
      runner.runInTenant(
          tenant,
          () -> {
            try {
              m.invoke(job, ei);
            } catch (Exception e) {
              System.out.println("REPRO execute FAILED " + e.getCause());
            }
          });

      List<BaseEvent> injectEvents =
          events.stream(BaseEvent.class)
              .filter(e -> e.getInstance() instanceof Inject)
              .filter(e -> injectId.equals(e.getInstance().getId()))
              .toList();
      System.out.println("REPRO inject events = " + injectEvents.size());
      for (BaseEvent e : injectEvents) {
        JsonNode inTx = e.getInstanceData();
        String re;
        try {
          JsonNode after = mapper.valueToTree(e.getInstance());
          re =
              String.valueOf(after.get("inject_type"))
                  + " status="
                  + after.path("inject_status").path("status_name");
        } catch (Exception ex) {
          re = "FAILED " + ex;
        }
        System.out.println(
            "REPRO event "
                + e.getType()
                + " in-tx inject_type="
                + inTx.get("inject_type")
                + " status="
                + inTx.path("inject_status").path("status_name")
                + " | re-serialized inject_type="
                + re);
      }
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }
}
