package io.openaev.integration.impl.injectors.openaev;

import static io.openaev.integration.impl.executors.mde.MdeExecutorIntegration.MDE_EXECUTOR_NAME;
import static io.openaev.integration.impl.executors.paloaltocortex.PaloAltoCortexExecutorIntegration.PALOALTOCORTEX_EXECUTOR_NAME;

import io.openaev.config.OpenAEVConfig;
import io.openaev.database.model.Endpoint;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Builds executor commands for the OpenAEV implant injector. These commands are tenant-independent
 * (they only depend on {@link OpenAEVConfig}) and are used both at integration startup and during
 * tenant provisioning.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class OpenaevImplantCommandBuilder {

  /**
   * How long the Windows scheduled-task command waits for the task to start before giving up and
   * reporting a launch failure. This bounds how long the vendor remote session is held, so it is
   * kept short on purpose: a task that has not started within a minute is not going to.
   */
  private static final int SCHEDULED_TASK_START_TIMEOUT_SECONDS = 60;

  /**
   * Record to group all command variables.
   *
   * @param tokenVar the token variable
   * @param serverVar the server variable
   * @param maxSizeVar the max size variable
   * @param unsecuredCertificateVar unsecured certificate variable
   * @param withProxyVar with proxy variable
   */
  record CommandVars(
      String tokenVar,
      String serverVar,
      String maxSizeVar,
      String unsecuredCertificateVar,
      String withProxyVar) {
    CommandVars() {
      this(
          "token=\"#{token}\"",
          "server=\"#{baseUrl}\"",
          "max_size=\"#{maxSize}\"",
          "unsecured_certificate=\"#{unsecuredCertificate}\"",
          "with_proxy=\"#{withProxy}\"");
    }
  }

  static Map<String, String> buildExecutorCommands(int timeoutSeconds) {
    Map<String, String> commands = new HashMap<>();
    CommandVars vars = new CommandVars();
    // --- PALO ALTO WINDOWS SPECIFIC ---
    buildPaloAltoWindowsCommand(Endpoint.PLATFORM_ARCH.x86_64, commands, vars, timeoutSeconds);
    buildPaloAltoWindowsCommand(Endpoint.PLATFORM_ARCH.arm64, commands, vars, timeoutSeconds);
    // --- MDE WINDOWS SPECIFIC ---
    // MDE Live Response (like Cortex XDR) terminates child processes when the remote session ends,
    // so the implant must be launched from a detached scheduled task to survive and phone home.
    buildMdeWindowsCommand(Endpoint.PLATFORM_ARCH.x86_64, commands, vars, timeoutSeconds);
    buildMdeWindowsCommand(Endpoint.PLATFORM_ARCH.arm64, commands, vars, timeoutSeconds);
    // --- DETACHED UNIX, per executor ---
    // Same reasoning as the Windows commands above, applied to Linux and macOS. Kept under
    // executor-prefixed keys so the generic Unix commands, which the native OpenAEV agent and
    // Caldera also read, keep their current blocking behaviour.
    for (String executorName : List.of(MDE_EXECUTOR_NAME, PALOALTOCORTEX_EXECUTOR_NAME)) {
      buildDetachedUnixCommand(
          executorName, Endpoint.PLATFORM_TYPE.Linux, "linux", commands, vars, timeoutSeconds);
      buildDetachedUnixCommand(
          executorName, Endpoint.PLATFORM_TYPE.MacOS, "macos", commands, vars, timeoutSeconds);
    }
    // --- WINDOWS ---
    buildGenericWindowsCommand(Endpoint.PLATFORM_ARCH.x86_64, commands, vars, timeoutSeconds);
    buildGenericWindowsCommand(Endpoint.PLATFORM_ARCH.arm64, commands, vars, timeoutSeconds);
    // --- LINUX ---
    buildGenericLinuxCommand(Endpoint.PLATFORM_ARCH.x86_64, commands, vars, timeoutSeconds);
    buildGenericLinuxCommand(Endpoint.PLATFORM_ARCH.arm64, commands, vars, timeoutSeconds);
    // --- MACOS ---
    buildGenericMacOSCommand(Endpoint.PLATFORM_ARCH.x86_64, commands, vars, timeoutSeconds);
    buildGenericMacOSCommand(Endpoint.PLATFORM_ARCH.arm64, commands, vars, timeoutSeconds);
    return commands;
  }

  static Map<String, String> buildExecutorClearCommands() {
    Map<String, String> clear = new HashMap<>();
    clear.put(
        Endpoint.PLATFORM_TYPE.Windows.name() + "." + Endpoint.PLATFORM_ARCH.x86_64,
        "$x=\"#{location}\";$location=$x.Replace(\"\\oaev-agent-caldera.exe\","
            + " \"\");[Environment]::CurrentDirectory = $location;cd \"$location\";Get-ChildItem"
            + " -Recurse -Filter *implant* | Remove-Item");
    clear.put(
        Endpoint.PLATFORM_TYPE.Windows.name() + "." + Endpoint.PLATFORM_ARCH.arm64,
        "$x=\"#{location}\";$location=$x.Replace(\"\\oaev-agent-caldera.exe\","
            + " \"\");[Environment]::CurrentDirectory = $location;cd \"$location\";Get-ChildItem"
            + " -Recurse -Filter *implant* | Remove-Item");
    clear.put(
        Endpoint.PLATFORM_TYPE.Linux.name() + "." + Endpoint.PLATFORM_ARCH.x86_64,
        "x=\"#{location}\";location=$(echo \"$x\" | sed \"s#/openaev-caldera-agent##\");cd"
            + " \"$location\"; rm *implant*");
    clear.put(
        Endpoint.PLATFORM_TYPE.Linux.name() + "." + Endpoint.PLATFORM_ARCH.arm64,
        "x=\"#{location}\";location=$(echo \"$x\" | sed \"s#/openaev-caldera-agent##\");cd"
            + " \"$location\"; rm *implant*");
    clear.put(
        Endpoint.PLATFORM_TYPE.MacOS.name() + "." + Endpoint.PLATFORM_ARCH.x86_64,
        "x=\"#{location}\";location=$(echo \"$x\" | sed \"s#/openaev-caldera-agent##\");cd"
            + " \"$location\"; rm *implant*");
    clear.put(
        Endpoint.PLATFORM_TYPE.MacOS.name() + "." + Endpoint.PLATFORM_ARCH.arm64,
        "x=\"#{location}\";location=$(echo \"$x\" | sed \"s#/openaev-caldera-agent##\");cd"
            + " \"$location\"; rm *implant*");
    return clear;
  }

  // --- Private helpers ---

  private static String dlUri(String platform, String arch) {
    return "\""
        + "#{baseUrl}/api/tenants/#{tenant}/implant/openaev/"
        + platform
        + "/"
        + arch
        + "?injectId=#{inject}&agentId=#{agent}\"";
  }

  @SuppressWarnings("SameParameterValue")
  private static String dlVar(String platform, String arch) {
    return "$url=\""
        + "#{baseUrl}/api/tenants/#{tenant}/implant/openaev/"
        + platform
        + "/"
        + arch
        + "?injectId=#{inject}&agentId=#{agent}"
        + "\"";
  }

  private static void buildPaloAltoWindowsCommand(
      Endpoint.PLATFORM_ARCH arch,
      Map<String, String> commands,
      CommandVars vars,
      int timeoutSeconds) {
    buildScheduledTaskWindowsCommand(
        PALOALTOCORTEX_EXECUTOR_NAME, arch, commands, vars, timeoutSeconds);
  }

  private static void buildMdeWindowsCommand(
      Endpoint.PLATFORM_ARCH arch,
      Map<String, String> commands,
      CommandVars vars,
      int timeoutSeconds) {
    buildScheduledTaskWindowsCommand(MDE_EXECUTOR_NAME, arch, commands, vars, timeoutSeconds);
  }

  /**
   * Builds a Windows implant command that launches the implant from a detached SYSTEM scheduled
   * task instead of a direct child process, then returns the remote session immediately.
   *
   * <h4>Why a scheduled task</h4>
   *
   * EDR remote-execution channels (Palo Alto Cortex XDR Live Terminal, Microsoft Defender for
   * Endpoint Live Response) terminate the process tree of the remote session once it ends. There is
   * no Unix-style detach on Windows: a process started with {@code Start-Process} stays a child of
   * the session and dies with it. The way out is re-parenting, handing the work to a service that
   * outlives the session. The Task Scheduler service is that owner here.
   *
   * <h4>Why we no longer wait for the task to finish</h4>
   *
   * Detaching the implant protects it from session teardown, but it does not release the session.
   * This command used to poll until the task returned to {@code Ready}, which means "the payload
   * has finished", for up to five minutes. During that whole window the vendor session stayed open.
   *
   * <p>That matters because these APIs are built for an analyst typing one command and reading the
   * answer within seconds, and vendors cap the number of concurrent sessions per tenant. An Inject
   * targeting a hundred endpoints asked for a hundred simultaneous sessions held for minutes, so
   * OpenAEV was consuming a large share of that budget itself.
   *
   * <p>We now poll only until the task has actually <em>started</em>, which takes about a second,
   * then return. Execution results do not need the session: the implant posts them back over HTTP
   * through the execution callback, and nothing in OpenAEV ever reads the session output.
   *
   * <h4>Where this pattern comes from</h4>
   *
   * This mirrors {@code openaev-ttr.ps1}, the Tanium Threat Response wrapper already shipped in
   * this repository and already running at customers. It is the reference implementation for the
   * whole "return the session immediately" work: same scheduled task, same "has it started"
   * condition.
   *
   * <p>Two deliberate differences from that reference, both worth knowing:
   *
   * <ul>
   *   <li>The startup budget is 60 seconds here, against 180 in the Tanium wrapper. A task that has
   *       not started within a minute is not going to, and the point of this change is to stop
   *       holding sessions.
   *   <li>The exit code now reports whether the <em>launch</em> succeeded, not what the payload
   *       returned. The payload result was read from {@code LastTaskResult}, which is only
   *       meaningful once the task completes, and no OpenAEV code ever read it back: the five
   *       executor clients return {@code void} and no command result is ever polled.
   * </ul>
   *
   * <h4>The bootstrap signal is deliberately kept</h4>
   *
   * When the implant never starts at all (blocked download, antivirus, wrong architecture) no
   * callback is ever sent, and the session output is the only human-readable evidence left. So the
   * command still prints a one-line launch outcome before returning, exactly as the Tanium wrapper
   * does. Only the payload's own output goes away, and that was never the source of execution logs.
   *
   * <h4>Known open point</h4>
   *
   * Unregistering a task while its instance is still running is a behaviour we have only ever
   * exercised through the Tanium TTR package. The previous code never hit it, since it unregistered
   * after completion. It works in production there, but it is worth confirming per executor rather
   * than assuming.
   *
   * @param executorNameKey executor name used as the command map key prefix
   */
  private static void buildScheduledTaskWindowsCommand(
      String executorNameKey,
      Endpoint.PLATFORM_ARCH arch,
      Map<String, String> commands,
      CommandVars vars,
      int timeoutSeconds) {
    commands.put(
        executorNameKey + "." + Endpoint.PLATFORM_TYPE.Windows.name() + "." + arch.name(),
        "[Net.ServicePointManager]::SecurityProtocol +="
            + " [Net.SecurityProtocolType]::Tls12;$x=\"#{location}\";$location=$x.Replace(\"\\oaev-agent-caldera.exe\","
            + " \"\");[Environment]::CurrentDirectory ="
            + " $location;$filename=\"oaev-implant-#{inject}-agent-#{agent}.exe\";$"
            + vars.tokenVar()
            + ";$"
            + vars.serverVar()
            + ";$"
            + vars.unsecuredCertificateVar()
            + ";$"
            + vars.withProxyVar()
            + ";$"
            + vars.maxSizeVar()
            + ";"
            + dlVar("windows", arch.name())
            + ";$wc=New-Object"
            + " System.Net.WebClient;$data=$wc.DownloadData($url);[io.file]::WriteAllBytes($filename,$data)"
            + " | Out-Null;Remove-NetFirewallRule -DisplayName \"Allow OpenAEV"
            + " Inbound\";New-NetFirewallRule -DisplayName \"Allow OpenAEV Inbound\" -Direction"
            + " Inbound -Program \"$location\\$filename\" -Action Allow |"
            + " Out-Null;Remove-NetFirewallRule -DisplayName \"Allow OpenAEV"
            + " Outbound\";New-NetFirewallRule -DisplayName \"Allow OpenAEV Outbound\" -Direction"
            + " Outbound -Program \"$location\\$filename\" -Action Allow | Out-Null;$taskName ="
            + " 'OpenAEV-Inject-#{inject}-Agent-#{agent}';$taskDescription = 'OpenAEV EDR"
            + " validation task - inject #{inject} - agent #{agent} - safe to ignore -"
            + " removed as soon as it starts';$implantArgs = '--uri ' + $server + ' --token ' +"
            + " $token + ' --unsecured-certificate ' + $unsecured_certificate + ' --with-proxy ' +"
            + " $with_proxy + ' --agent-id #{agent} --inject-id #{inject} --tenant-id"
            + " #{tenant}';$action = New-ScheduledTaskAction -Execute \"$location\\$filename\""
            + " -Argument $implantArgs;$principal = New-ScheduledTaskPrincipal -UserId 'SYSTEM'"
            + " -LogonType ServiceAccount -RunLevel Highest;$settings ="
            + " New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries"
            // The safety net. It used to be (New-TimeSpan -Hours 0), which disables the limit
            // entirely: the implant has no maximum runtime of its own, so nothing bounded the
            // payload on this path at all. Returning the session early makes that gap worse, since
            // we no longer observe the task, so the bound is restored here using the same budget
            // the shell watchdog applies on the generic commands.
            + " -ExecutionTimeLimit (New-TimeSpan -Seconds "
            + timeoutSeconds
            + ");Register-ScheduledTask -TaskName"
            + " $taskName -Description $taskDescription -Action $action -Principal $principal"
            + " -Settings $settings -Force | Out-Null;Start-ScheduledTask -TaskName"
            + " $taskName;"
            // Poll for "has the task started", not "has it finished". LastRunTime is stamped as
            // soon as the Task Scheduler runs the action, so this normally exits on the first
            // iteration and the vendor session is returned in about a second.
            //
            // Compared against a recent instant rather than against $null: a task that has never
            // run reports a sentinel LastRunTime (30/11/1999) on several Windows versions, not
            // $null, so a null check would never become true and we would wait out the budget on
            // every launch. openaev-ttr.ps1 uses the null form and gets away with it because it
            // reports success regardless; we want the signal to mean something.
            + "$startedAfter = (Get-Date).AddMinutes(-1);$timeout = "
            + SCHEDULED_TASK_START_TIMEOUT_SECONDS
            + "; $elapsed = 0; $started = $false;"
            + "while($elapsed -lt $timeout -and -not $started) {"
            + "  $info = Get-ScheduledTaskInfo -TaskName $taskName -ErrorAction SilentlyContinue;"
            + "  if($info -and $info.LastRunTime -gt $startedAfter) { $started = $true }"
            + "  else { Start-Sleep -Seconds 1; $elapsed++ }"
            + "};"
            // The task is removed right after it started, while the payload is still running. The
            // running instance is owned by the Task Scheduler service and is not killed by this.
            + "Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction"
            + " SilentlyContinue;"
            // Bootstrap signal: the only evidence left in the vendor console when the implant never
            // starts and therefore never calls back. Not the payload result, which arrives through
            // the execution callback.
            + "if($started) { Write-Host 'OpenAEV implant launched'; exit 0 };"
            + "Write-Host 'OpenAEV implant start not confirmed within' $timeout 'seconds';"
            + " exit 1;");
  }

  /**
   * Builds a Unix implant command that launches the implant fully detached from the vendor's
   * remote-execution session, then returns that session immediately.
   *
   * <h4>The problem this solves</h4>
   *
   * The generic Unix command backgrounds the implant, starts a watchdog subshell, and then blocks
   * on {@code wait $pid} for the whole Inject threshold. The vendor session stays open for that
   * entire duration. On an Inject targeting a hundred endpoints that is a hundred concurrent
   * sessions held for minutes, against APIs built for an analyst reading one answer in seconds, and
   * vendors cap concurrent sessions per tenant.
   *
   * <h4>The watchdog is kept, it moves</h4>
   *
   * The reason the wait exists is legitimate: without it an implant that hangs would sit on the
   * customer's machine forever. That safeguard is preserved here, it simply moves inside the
   * detached process instead of living in the session-bound shell. If it stayed outside, tearing
   * the session down would kill the watchdog and leave the implant running unsupervised, which is
   * strictly worse than today.
   *
   * <h4>Two details that are not decoration</h4>
   *
   * <ul>
   *   <li><b>Escape the process group.</b> {@code setsid} on Linux, a double fork through a
   *       subshell on macOS which has no {@code setsid}. Without it the payload dies when the
   *       vendor tears the session down, and detaching buys nothing.
   *   <li><b>Redirect stdout and stderr.</b> A backgrounded process holding the pipe keeps the
   *       caller waiting even with no explicit {@code wait}. The CrowdStrike unix subprocessor is a
   *       {@code base64 -d | sh} pipeline inheriting the session's stdout, so without redirection
   *       the session stays held anyway.
   * </ul>
   *
   * <h4>Where this comes from</h4>
   *
   * This is the mechanism of {@code openaev-ttr.sh}, the Tanium Threat Response wrapper already in
   * this repository and already running at customers, moved into the generated command. Tanium
   * ships it as a script the customer installs; for the other executors the customer-side scripts
   * are pure pass-throughs ({@code base64 -d | sh}), so the behaviour has to live in what we
   * generate. No customer action is required.
   *
   * <h4>The bootstrap signal</h4>
   *
   * Once detached, a failure to even start the implant produces no callback and no session output,
   * so the download is checked explicitly and the command prints a one-line outcome before
   * returning. That line is the only human-readable evidence left when nothing calls back.
   *
   * @param executorNameKey executor name used as the command map key prefix
   * @param platform platform used in the command map key
   * @param downloadPlatform platform segment of the implant download URL
   */
  private static void buildDetachedUnixCommand(
      String executorNameKey,
      Endpoint.PLATFORM_TYPE platform,
      String downloadPlatform,
      Map<String, String> commands,
      CommandVars vars,
      int timeoutSeconds) {
    // Runs in the detached shell, not in the session shell. Escaped dollars are resolved there;
    // unescaped ones ($location, $server, ...) are expanded here, before detaching.
    // Single quotes around the outer expansions on purpose. This string is expanded once by the
    // session shell, then parsed a second time by the detached shell, so an unquoted value
    // containing a space or an "&" would be re-split there. Quoting closes that whole class of
    // problem; the generic command never had to care because it is only parsed once.
    String supervisedRun =
        "'$location/$filename' --uri '$server' --token '$token' --unsecured-certificate"
            + " '$unsecured_certificate' --with-proxy '$with_proxy' --agent-id #{agent}"
            + " --inject-id #{inject} --tenant-id #{tenant} & ipid=\\$!;"
            + "(sleep "
            + timeoutSeconds
            + ";if kill -0 \\$ipid 2>/dev/null;then kill -TERM \\$ipid 2>/dev/null;sleep 5;"
            + "kill -KILL \\$ipid 2>/dev/null;fi) & wait \\$ipid";

    commands.put(
        executorNameKey + "." + platform.name() + "." + Endpoint.PLATFORM_ARCH.x86_64.name(),
        "x=\"#{location}\";location=$(echo \"$x\" | sed"
            + " \"s#/openaev-caldera-agent##\");filename=oaev-implant-#{inject}-agent-#{agent};"
            + vars.serverVar()
            + ";"
            + vars.tokenVar()
            + ";"
            + vars.unsecuredCertificateVar()
            + ";"
            + vars.withProxyVar()
            + ";"
            + vars.maxSizeVar()
            + ";"
            // -f so an HTTP error is a non-zero exit instead of an HTML page written to disk.
            // Checked explicitly: after detaching, this is the last point where a failure can
            // still be reported to a human.
            + "curl -s -f -X GET "
            + dlUri(downloadPlatform, Endpoint.PLATFORM_ARCH.x86_64.name())
            + " -o $location/$filename || { echo 'OpenAEV implant download failed'; exit 1; };"
            + "chmod +x $location/$filename;"
            // setsid on Linux, double fork on macOS which does not ship it. Output redirected so
            // the caller is not held by an inherited pipe.
            + "if command -v setsid >/dev/null 2>&1; then"
            + " nohup setsid /bin/sh -c \""
            + supervisedRun
            + "\" >/dev/null 2>&1 &"
            + " else ( nohup /bin/sh -c \""
            + supervisedRun
            + "\" >/dev/null 2>&1 & ) & fi;"
            + "echo 'OpenAEV implant launched'; exit 0");
  }

  private static void buildGenericWindowsCommand(
      Endpoint.PLATFORM_ARCH arch,
      Map<String, String> commands,
      CommandVars vars,
      int timeoutSeconds) {
    commands.put(
        Endpoint.PLATFORM_TYPE.Windows.name() + "." + arch.name(),
        "[Net.ServicePointManager]::SecurityProtocol +="
            + " [Net.SecurityProtocolType]::Tls12;$x=\"#{location}\";$location=$x.Replace(\"\\oaev-agent-caldera.exe\","
            + " \"\");[Environment]::CurrentDirectory ="
            + " $location;$filename=\"oaev-implant-#{inject}-agent-#{agent}.exe\";$"
            + vars.tokenVar()
            + ";$"
            + vars.serverVar()
            + ";$"
            + vars.unsecuredCertificateVar()
            + ";$"
            + vars.withProxyVar()
            + ";$"
            + vars.maxSizeVar()
            + ";"
            + dlVar("windows", arch.name())
            + ";$wc=New-Object"
            + " System.Net.WebClient;$data=$wc.DownloadData($url);[io.file]::WriteAllBytes($filename,$data)"
            + " | Out-Null;Remove-NetFirewallRule -DisplayName \"Allow OpenAEV"
            + " Inbound\";New-NetFirewallRule -DisplayName \"Allow OpenAEV Inbound\" -Direction"
            + " Inbound -Program \"$location\\$filename\" -Action Allow |"
            + " Out-Null;Remove-NetFirewallRule -DisplayName \"Allow OpenAEV"
            + " Outbound\";New-NetFirewallRule -DisplayName \"Allow OpenAEV Outbound\" -Direction"
            + " Outbound -Program \"$location\\$filename\" -Action Allow | Out-Null;$proc=Start-Process"
            + " -FilePath \"$location\\$filename\" -ArgumentList \"--uri $server --token $token"
            + " --unsecured-certificate $unsecured_certificate --with-proxy $with_proxy --agent-id"
            + " #{agent} --inject-id #{inject} --tenant-id #{tenant}\" -WindowStyle hidden -PassThru;"
            + "if(-not $proc.WaitForExit("
            + (timeoutSeconds * 1000L)
            + ")){Stop-Process -Id $proc.Id -Force;exit 124};"
            + "exit $proc.ExitCode;");
  }

  private static void buildGenericLinuxCommand(
      Endpoint.PLATFORM_ARCH arch,
      Map<String, String> commands,
      CommandVars vars,
      int timeoutSeconds) {
    commands.put(
        Endpoint.PLATFORM_TYPE.Linux.name() + "." + arch.name(),
        "x=\"#{location}\";location=$(echo \"$x\" | sed"
            + " \"s#/openaev-caldera-agent##\");filename=oaev-implant-#{inject}-agent-#{agent};"
            + vars.serverVar()
            + ";"
            + vars.tokenVar()
            + ";"
            + vars.unsecuredCertificateVar()
            + ";"
            + vars.withProxyVar()
            + ";"
            + vars.maxSizeVar()
            + ";curl -s -X GET "
            + dlUri("linux", arch.name())
            + " > $location/$filename;chmod +x $location/$filename;$location/$filename --uri"
            + " $server --token $token --unsecured-certificate $unsecured_certificate --with-proxy"
            + " $with_proxy --agent-id #{agent} --inject-id #{inject} --tenant-id #{tenant} & pid=$!;"
            + "(sleep "
            + timeoutSeconds
            + ";if kill -0 $pid 2>/dev/null;then kill -TERM $pid 2>/dev/null;sleep 5;"
            + "kill -KILL $pid 2>/dev/null;fi) & watchdog=$!;wait $pid 2>/dev/null;exit_code=$?;"
            + "kill $watchdog 2>/dev/null;wait $watchdog 2>/dev/null;exit $exit_code");
  }

  private static void buildGenericMacOSCommand(
      Endpoint.PLATFORM_ARCH arch,
      Map<String, String> commands,
      CommandVars vars,
      int timeoutSeconds) {
    commands.put(
        Endpoint.PLATFORM_TYPE.MacOS.name() + "." + arch.name(),
        "x=\"#{location}\";location=$(echo \"$x\" | sed"
            + " \"s#/openaev-caldera-agent##\");filename=oaev-implant-#{inject}-agent-#{agent};"
            + vars.serverVar()
            + ";"
            + vars.tokenVar()
            + ";"
            + vars.unsecuredCertificateVar()
            + ";"
            + vars.withProxyVar()
            + (Endpoint.PLATFORM_ARCH.x86_64.equals(arch)
                ? ";"
                : ";$") // TODO: Should find a way to test on an x86 mac if the diff is necessary
            + vars.maxSizeVar()
            + ";curl -s -X GET "
            + dlUri("macos", arch.name())
            + " > $location/$filename;chmod +x $location/$filename;$location/$filename --uri"
            + " $server --token $token --unsecured-certificate $unsecured_certificate --with-proxy"
            + " $with_proxy --agent-id #{agent} --inject-id #{inject} --tenant-id #{tenant} & pid=$!;"
            + "(sleep "
            + timeoutSeconds
            + ";if kill -0 $pid 2>/dev/null;then kill -TERM $pid 2>/dev/null;sleep 5;"
            + "kill -KILL $pid 2>/dev/null;fi) & watchdog=$!;wait $pid 2>/dev/null;exit_code=$?;"
            + "kill $watchdog 2>/dev/null;wait $watchdog 2>/dev/null;exit $exit_code");
  }
}
