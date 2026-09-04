package io.openaev.integration.impl.injectors.openaev;

import static io.openaev.integration.impl.executors.mde.MdeExecutorIntegration.MDE_EXECUTOR_NAME;
import static io.openaev.integration.impl.executors.paloaltocortex.PaloAltoCortexExecutorIntegration.PALOALTOCORTEX_EXECUTOR_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.Endpoint;
import io.openaev.executors.ExecutorHelper;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Guards on the implant commands, focused on one question: how long does OpenAEV hold the vendor's
 * remote-execution session open?
 *
 * <p>Context. Endpoint Injects reach an endpoint through a remote-execution channel (CrowdStrike
 * RTR, Defender Live Response, Cortex Live Terminal, and so on). Those channels are built for an
 * analyst typing one command and reading the answer within seconds, and vendors cap the number of
 * concurrent sessions per tenant. Every second we keep a session open is a second of that budget
 * spent, multiplied by the number of targeted endpoints.
 *
 * <p>These tests exist because that duration is decided by a string, generated here, and a string
 * is exactly the kind of thing that silently regresses.
 */
class OpenaevImplantCommandBuilderTest {

  /**
   * Same value the injector factory passes: {@code inject.execution.threshold.minutes} times 60.
   */
  private static final int TEN_MINUTES_IN_SECONDS = 600;

  private static Map<String, String> commands() {
    return OpenaevImplantCommandBuilder.buildExecutorCommands(TEN_MINUTES_IN_SECONDS);
  }

  /**
   * The two executors whose Windows command launches the implant from a detached scheduled task.
   */
  private static Stream<String> detachedWindowsCommandKeys() {
    return Stream.of(
        MDE_EXECUTOR_NAME + ".Windows.x86_64",
        MDE_EXECUTOR_NAME + ".Windows.arm64",
        PALOALTOCORTEX_EXECUTOR_NAME + ".Windows.x86_64",
        PALOALTOCORTEX_EXECUTOR_NAME + ".Windows.arm64");
  }

  @DisplayName("A detached Windows command returns the session as soon as the implant has started")
  @ParameterizedTest(name = "{0}")
  @MethodSource("detachedWindowsCommandKeys")
  void detachedWindowsCommandWaitsForStartNotForCompletion(String commandKey) {
    String command = commands().get(commandKey);

    assertThat(command).as("command should exist for key %s", commandKey).isNotNull();

    // "The task has started" is what LastRunTime tells us. It is stamped as soon as the Task
    // Scheduler runs the action, so this normally succeeds on the first poll.
    //
    // Compared against a recent instant rather than against $null on purpose: a task that has
    // never run reports a sentinel LastRunTime (30/11/1999) on several Windows versions, so a
    // null check would never become true and every launch would wait out the whole budget.
    assertThat(command)
        .as("should wait for the task to have started")
        .contains("$info.LastRunTime -gt $startedAfter");
  }

  @DisplayName("A detached Windows command does not wait for the payload to finish")
  @ParameterizedTest(name = "{0}")
  @MethodSource("detachedWindowsCommandKeys")
  void detachedWindowsCommandDoesNotWaitForThePayload(String commandKey) {
    String command = commands().get(commandKey);

    // This is the regression this file mainly exists for.
    //
    // The command used to poll until the scheduled task returned to the Ready state, which means
    // "the payload has finished", for up to five minutes. Detaching the implant protected it from
    // session teardown, but waiting for it to finish handed the session back anyway, so the
    // detachment bought nothing in session terms.
    //
    // Reference implementation: openaev-ttr.ps1, the Tanium Threat Response wrapper shipped in
    // this repository, waits on "has it started" and returns in about a second.
    assertThat(command)
        .as("waiting for state Ready means waiting for the payload to finish")
        .doesNotContain("$state -eq 'Ready'");

    // LastTaskResult is only meaningful once the task completed, so reading it implies waiting.
    // It was also never used: the five executor clients return void and no command result is
    // ever polled back from the vendor. Execution results arrive through the implant callback.
    assertThat(command)
        .as("reading the payload result implies waiting for the payload")
        .doesNotContain("LastTaskResult");
  }

  @DisplayName("A detached Windows command still reports whether the implant was launched")
  @ParameterizedTest(name = "{0}")
  @MethodSource("detachedWindowsCommandKeys")
  void detachedWindowsCommandKeepsABootstrapSignal(String commandKey) {
    String command = commands().get(commandKey);

    // Returning the session early costs us the payload's console output, which is fine because
    // nothing reads it. What we must not lose is the bootstrap outcome: when the implant never
    // starts at all (blocked download, antivirus, wrong architecture) no callback is ever sent,
    // and this line is the only human-readable evidence left in the vendor console.
    assertThat(command).contains("OpenAEV implant launched");
    assertThat(command).contains("OpenAEV implant start not confirmed");
  }

  @DisplayName("The detached Unix command survives the executor's own transformations")
  @Test
  void detachedUnixCommandSurvivesTheServiceTransformations() {
    // The builder output is not what reaches the endpoint. The executor context service applies
    // three more steps before base64-encoding it, and each one is a chance to break the command.
    // This reproduces them in order, so a change to either side is caught here rather than on a
    // customer machine.
    String command = commands().get(MDE_EXECUTOR_NAME + ".Linux.x86_64");

    // 1. Architecture detection: the literal arch in the download URL becomes a shell variable.
    command = ExecutorHelper.UNIX_ARCH + command.replace("x86_64", ExecutorHelper.ARCH_VARIABLE);

    // 2. Placeholder substitution.
    command =
        ExecutorHelper.replaceArgs(
            Endpoint.PLATFORM_TYPE.Linux,
            command,
            "INJ",
            "AGT",
            "TEN",
            "TOK",
            "https://oaev.local",
            "1024",
            "false",
            "false");

    // 3. The implant folder rewrite, which replaces the whole head of the command.
    String implantLocation =
        "location="
            + ExecutorHelper.IMPLANT_LOCATION_UNIX
            + ExecutorHelper.IMPLANT_BASE_NAME
            + "UUID;mkdir -p $location;filename=";
    command =
        command.replaceFirst(
            "\\$?x=.+location=.+;filename=", Matcher.quoteReplacement(implantLocation));

    // Nothing left unresolved. A surviving placeholder would reach the endpoint verbatim.
    assertThat(command).as("every placeholder must be resolved").doesNotContain("#{");

    // The head rewrite landed: the builder's own location preamble is gone, replaced by the
    // per-inject implant folder.
    assertThat(command).contains("mkdir -p $location");
    assertThat(command).doesNotContain("openaev-caldera-agent");

    // The detach survived all three steps.
    assertThat(command).contains("nohup setsid /bin/sh -c");
    assertThat(command).contains(">/dev/null 2>&1 &");
    assertThat(command).endsWith("exit 0");

    // The deferred variables must NOT have been resolved here: they belong to the detached shell,
    // which is a second parsing pass. This is the assertion that catches an over-eager escape fix.
    assertThat(command).contains("ipid=\\$!");
    assertThat(command).contains("wait \\$ipid");

    // Values crossing into the detached shell are quoted, so a space or an ampersand in a URL or
    // token cannot be re-split by that second parsing pass.
    assertThat(command).contains("--uri '$server'");
    assertThat(command).contains("--token '$token'");
  }

  @DisplayName("Known gap: the generic commands still block for the whole payload duration")
  @Test
  void genericCommandsStillHoldTheSessionForTheWholePayload() {
    Map<String, String> commands = commands();

    // Deliberately asserting the CURRENT behaviour, not the desired one.
    //
    // The generic commands are read by CrowdStrike, SentinelOne and Tanium on every platform, and
    // by Defender and Cortex on Unix. They still wait for the implant to finish, so those paths
    // still hold the vendor session for up to the Inject threshold.
    //
    // They are not fixed in the same change for one specific reason: these keys are shared. The
    // native OpenAEV agent (OpenAEVExecutorContextService.computeCommand) and Caldera
    // (CalderaExecutorClient.createSubprocessorAbility) read the very same entries, and neither
    // has anything to do with vendor sessions. Editing them in place would silently make the
    // native agent fire-and-forget too.
    //
    // The way forward is the one already used by Defender and Cortex on Windows: add
    // executor-specific keys carrying the detached launch, and point only the executors that need
    // it at those keys. When that lands, this test is the one to update.
    assertThat(commands.get("Windows.x86_64"))
        .as("generic Windows still blocks on the implant process")
        .contains("$proc.WaitForExit(" + (TEN_MINUTES_IN_SECONDS * 1000L) + ")");

    assertThat(commands.get("Linux.x86_64"))
        .as("generic Linux still blocks on the implant process")
        .contains("wait $pid");

    assertThat(commands.get("MacOS.x86_64"))
        .as("generic macOS still blocks on the implant process")
        .contains("wait $pid");
  }

  /** The two executors whose Unix command now launches the implant fully detached. */
  private static Stream<String> detachedUnixCommandKeys() {
    return Stream.of(
        MDE_EXECUTOR_NAME + ".Linux.x86_64",
        MDE_EXECUTOR_NAME + ".MacOS.x86_64",
        PALOALTOCORTEX_EXECUTOR_NAME + ".Linux.x86_64",
        PALOALTOCORTEX_EXECUTOR_NAME + ".MacOS.x86_64");
  }

  @DisplayName("A detached Unix command escapes the process group and returns immediately")
  @ParameterizedTest(name = "{0}")
  @MethodSource("detachedUnixCommandKeys")
  void detachedUnixCommandDetachesAndReturns(String commandKey) {
    String command = commands().get(commandKey);

    assertThat(command).as("command should exist for key %s", commandKey).isNotNull();

    // Escaping the process group is what makes detaching real. Without it the payload dies when
    // the vendor tears the session down, and we would have given up supervision for nothing.
    // setsid on Linux, double fork through a subshell on macOS which does not ship setsid.
    assertThat(command).contains("command -v setsid");
    assertThat(command).contains("nohup setsid /bin/sh -c");
    assertThat(command).contains("( nohup /bin/sh -c");

    // Redirecting output matters as much as detaching: a backgrounded process holding the pipe
    // keeps the caller waiting even with no explicit wait, so the session would stay held anyway.
    assertThat(command).contains(">/dev/null 2>&1 &");

    // The session shell must not block. It exits as soon as the implant is on its way.
    assertThat(command).endsWith("exit 0");
  }

  @DisplayName("A detached Unix command keeps the watchdog, inside the detached process")
  @ParameterizedTest(name = "{0}")
  @MethodSource("detachedUnixCommandKeys")
  void detachedUnixCommandKeepsTheWatchdogInsideTheDetachedProcess(String commandKey) {
    String command = commands().get(commandKey);

    // The wait was there for a real reason: without it a stuck implant sits on the customer
    // machine forever. That safeguard is preserved, it moves.
    assertThat(command).contains("sleep " + TEN_MINUTES_IN_SECONDS);
    assertThat(command).contains("kill -TERM");
    assertThat(command).contains("kill -KILL");

    // And it must live inside the detached shell, not outside it. The escaped dollars are the
    // tell: they are resolved by the detached shell, not by the session shell. A watchdog left
    // outside would be killed with the session and leave the implant running unsupervised, which
    // is strictly worse than the behaviour we are replacing.
    assertThat(command).contains("& ipid=\\$!");
    assertThat(command).contains("wait \\$ipid");
  }

  @DisplayName("A detached Unix command reports whether the implant could be launched")
  @ParameterizedTest(name = "{0}")
  @MethodSource("detachedUnixCommandKeys")
  void detachedUnixCommandKeepsABootstrapSignal(String commandKey) {
    String command = commands().get(commandKey);

    // Once detached, a failure to even fetch the implant produces no callback and no payload
    // output. The download is therefore checked explicitly, and the command says what happened.
    assertThat(command).contains("curl -s -f");
    assertThat(command).contains("OpenAEV implant download failed");
    assertThat(command).contains("OpenAEV implant launched");
  }

  @DisplayName("The detached Windows scheduled task is bounded again")
  @ParameterizedTest(name = "{0}")
  @MethodSource("detachedWindowsCommandKeys")
  void detachedWindowsTaskHasAnExecutionTimeLimit(String commandKey) {
    String command = commands().get(commandKey);

    // (New-TimeSpan -Hours 0) disables the limit. Combined with an implant that has no maximum
    // runtime of its own, nothing bounded the payload on this path at all. Returning the session
    // early makes that worse, since we stop observing the task, so the bound is restored using
    // the same budget the shell watchdog applies elsewhere.
    assertThat(command)
        .as("an unlimited scheduled task leaves the payload unsupervised")
        .doesNotContain("New-TimeSpan -Hours 0");
    assertThat(command).contains("New-TimeSpan -Seconds " + TEN_MINUTES_IN_SECONDS);
  }

  @DisplayName("The detached Windows commands are separate entries, generic keys are untouched")
  @Test
  void detachedCommandsDoNotOverwriteTheSharedGenericKeys() {
    Map<String, String> commands = commands();

    // The prefixed keys and the generic ones must stay distinct. This is what keeps the native
    // agent and Caldera out of the blast radius of any executor-specific change.
    assertThat(commands.get(MDE_EXECUTOR_NAME + ".Windows.x86_64"))
        .isNotEqualTo(commands.get("Windows.x86_64"));
    assertThat(commands.get(PALOALTOCORTEX_EXECUTOR_NAME + ".Windows.x86_64"))
        .isNotEqualTo(commands.get("Windows.x86_64"));

    // Same on Unix, where the detached commands were just added.
    assertThat(commands.get(MDE_EXECUTOR_NAME + ".Linux.x86_64"))
        .isNotEqualTo(commands.get("Linux.x86_64"));
    assertThat(commands.get(PALOALTOCORTEX_EXECUTOR_NAME + ".MacOS.x86_64"))
        .isNotEqualTo(commands.get("MacOS.x86_64"));

    // And the generic entries must still be the blocking ones, byte for byte what the native
    // agent and Caldera were already getting. This is the cheap proof that adding executor
    // commands did not change anyone else's behaviour.
    assertThat(commands.get("Linux.x86_64"))
        .as("the native agent and Caldera read this entry")
        .doesNotContain("setsid")
        .doesNotContain("nohup");
    assertThat(commands.get("Windows.x86_64"))
        .as("the native agent and Caldera read this entry")
        .doesNotContain("Register-ScheduledTask");
  }
}
