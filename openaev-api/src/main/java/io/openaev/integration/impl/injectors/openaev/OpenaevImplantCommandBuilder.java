package io.openaev.integration.impl.injectors.openaev;

import static io.openaev.integration.impl.executors.paloaltocortex.PaloAltoCortexExecutorIntegration.PALOALTOCORTEX_EXECUTOR_NAME;

import io.openaev.config.OpenAEVConfig;
import io.openaev.database.model.Endpoint;
import java.util.HashMap;
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
    CommandVars(OpenAEVConfig cfg) {
      this(
          "token=\"" + cfg.getAdminToken() + "\"",
          "server=\"" + cfg.getBaseUrlForAgent() + "\"",
          "max_size=\"" + cfg.getLogsMaxSize() + "\"",
          "unsecured_certificate=\"" + cfg.isUnsecuredCertificate() + "\"",
          "with_proxy=\"" + cfg.isWithProxy() + "\"");
    }
  }

  // -- Trace reporting helpers --
  // These build inline script snippets that report errors back to the platform
  // via the inject execution API, so errors appear in the inject status UI.

  /**
   * Builds a PowerShell snippet that reports an error trace to the platform. Uses WebClient to POST
   * to the inject execution endpoint. Errors during reporting itself are silently ignored to avoid
   * masking the original error.
   */
  private static String psReportError(String message) {
    // The message is embedded in a JSON payload sent to the inject execution endpoint.
    // We use single quotes for the PS string to avoid escaping issues with double quotes in JSON.
    return "try { "
        + "$traceBody = '{\"execution_message\": \"" + message + "\", "
        + "\"execution_status\": \"ERROR\", "
        + "\"execution_action\": \"complete\", "
        + "\"execution_duration\": 0}'; "
        + "$traceWc = New-Object System.Net.WebClient; "
        + "$traceWc.Headers.Add('Content-Type', 'application/json'); "
        + "$traceWc.Headers.Add('Authorization', 'Bearer ' + $token); "
        + "$traceWc.UploadString("
        + "$server + '/api/tenants/#{tenant}/injects/#{inject}/traces/agents/#{agent}', "
        + "'POST', $traceBody) "
        + "} catch {}";
  }

  /**
   * Builds a PowerShell snippet that reports a warning trace to the platform. Used for non-fatal
   * issues like firewall rule failures that don't prevent execution.
   */
  private static String psReportWarning(String message) {
    return "try { "
        + "$traceBody = '{\"execution_message\": \"" + message + "\", "
        + "\"execution_status\": \"WARNING\", "
        + "\"execution_action\": \"execution\", "
        + "\"execution_duration\": 0}'; "
        + "$traceWc = New-Object System.Net.WebClient; "
        + "$traceWc.Headers.Add('Content-Type', 'application/json'); "
        + "$traceWc.Headers.Add('Authorization', 'Bearer ' + $token); "
        + "$traceWc.UploadString("
        + "$server + '/api/tenants/#{tenant}/injects/#{inject}/traces/agents/#{agent}', "
        + "'POST', $traceBody) "
        + "} catch {}";
  }

  /**
   * Builds a bash/sh snippet that reports an error trace to the platform via curl. Used in
   * Linux/macOS scripts.
   */
  private static String shReportError(String message) {
    return "curl -s -X POST "
        + "\"$server/api/tenants/#{tenant}/injects/#{inject}/traces/agents/#{agent}\" "
        + "-H 'Content-Type: application/json' "
        + "-H \"Authorization: Bearer $token\" "
        + "-d '{\"execution_message\": \"" + message + "\", "
        + "\"execution_status\": \"ERROR\", "
        + "\"execution_action\": \"complete\", "
        + "\"execution_duration\": 0}' "
        + ">/dev/null 2>&1";
  }

  /**
   * Builds a bash/sh snippet that reports a warning trace to the platform via curl. Used for
   * non-fatal issues in Linux/macOS scripts.
   */
  private static String shReportWarning(String message) {
    return "curl -s -X POST "
        + "\"$server/api/tenants/#{tenant}/injects/#{inject}/traces/agents/#{agent}\" "
        + "-H 'Content-Type: application/json' "
        + "-H \"Authorization: Bearer $token\" "
        + "-d '{\"execution_message\": \"" + message + "\", "
        + "\"execution_status\": \"WARNING\", "
        + "\"execution_action\": \"execution\", "
        + "\"execution_duration\": 0}' "
        + ">/dev/null 2>&1";
  }

  static Map<String, String> buildExecutorCommands(OpenAEVConfig cfg) {
    Map<String, String> commands = new HashMap<>();
    CommandVars vars = new CommandVars(cfg);
    // --- PALO ALTO WINDOWS SPECIFIC ---
    buildPaloAltoWindowsCommand(Endpoint.PLATFORM_ARCH.x86_64, cfg, commands, vars);
    buildPaloAltoWindowsCommand(Endpoint.PLATFORM_ARCH.arm64, cfg, commands, vars);
    // --- WINDOWS ---
    buildGenericWindowsCommand(Endpoint.PLATFORM_ARCH.x86_64, cfg, commands, vars);
    buildGenericWindowsCommand(Endpoint.PLATFORM_ARCH.arm64, cfg, commands, vars);
    // --- LINUX ---
    buildGenericLinuxCommand(Endpoint.PLATFORM_ARCH.x86_64, cfg, commands, vars);
    buildGenericLinuxCommand(Endpoint.PLATFORM_ARCH.arm64, cfg, commands, vars);
    // --- MACOS ---
    buildGenericMacOSCommand(Endpoint.PLATFORM_ARCH.x86_64, cfg, commands, vars);
    buildGenericMacOSCommand(Endpoint.PLATFORM_ARCH.arm64, cfg, commands, vars);
    return commands;
  }

  static Map<String, String> buildExecutorClearCommands() {
    Map<String, String> clear = new HashMap<>();
    clear.put(
        Endpoint.PLATFORM_TYPE.Windows.name() + "." + Endpoint.PLATFORM_ARCH.x86_64,
        "$x=\"#{location}\";$location=$x.Replace(\"\\oaev-agent-caldera.exe\", \"\");[Environment]::CurrentDirectory = $location;cd \"$location\";Get-ChildItem -Recurse -Filter *implant* | Remove-Item");
    clear.put(
        Endpoint.PLATFORM_TYPE.Windows.name() + "." + Endpoint.PLATFORM_ARCH.arm64,
        "$x=\"#{location}\";$location=$x.Replace(\"\\oaev-agent-caldera.exe\", \"\");[Environment]::CurrentDirectory = $location;cd \"$location\";Get-ChildItem -Recurse -Filter *implant* | Remove-Item");
    clear.put(
        Endpoint.PLATFORM_TYPE.Linux.name() + "." + Endpoint.PLATFORM_ARCH.x86_64,
        "x=\"#{location}\";location=$(echo \"$x\" | sed \"s#/openaev-caldera-agent##\");cd \"$location\"; rm *implant*");
    clear.put(
        Endpoint.PLATFORM_TYPE.Linux.name() + "." + Endpoint.PLATFORM_ARCH.arm64,
        "x=\"#{location}\";location=$(echo \"$x\" | sed \"s#/openaev-caldera-agent##\");cd \"$location\"; rm *implant*");
    clear.put(
        Endpoint.PLATFORM_TYPE.MacOS.name() + "." + Endpoint.PLATFORM_ARCH.x86_64,
        "x=\"#{location}\";location=$(echo \"$x\" | sed \"s#/openaev-caldera-agent##\");cd \"$location\"; rm *implant*");
    clear.put(
        Endpoint.PLATFORM_TYPE.MacOS.name() + "." + Endpoint.PLATFORM_ARCH.arm64,
        "x=\"#{location}\";location=$(echo \"$x\" | sed \"s#/openaev-caldera-agent##\");cd \"$location\"; rm *implant*");
    return clear;
  }

  // --- Private helpers ---

  private static String dlUri(OpenAEVConfig cfg, String platform, String arch) {
    return "\""
        + cfg.getBaseUrlForAgent()
        + "/api/tenants/#{tenant}/implant/openaev/"
        + platform
        + "/"
        + arch
        + "?injectId=#{inject}&agentId=#{agent}\"";
  }

  @SuppressWarnings("SameParameterValue")
  private static String dlVar(OpenAEVConfig cfg, String platform, String arch) {
    return "$url=\""
        + cfg.getBaseUrl()
        + "/api/tenants/#{tenant}/implant/openaev/"
        + platform
        + "/"
        + arch
        + "?injectId=#{inject}&agentId=#{agent}"
        + "\"";
  }

  private static void buildPaloAltoWindowsCommand(
      Endpoint.PLATFORM_ARCH arch,
      OpenAEVConfig cfg,
      Map<String, String> commands,
      CommandVars vars) {
    commands.put(
        PALOALTOCORTEX_EXECUTOR_NAME
            + "."
            + Endpoint.PLATFORM_TYPE.Windows.name()
            + "."
            + arch.name(),
        // -- INIT: TLS, location, variables --
        "[Net.ServicePointManager]::SecurityProtocol += [Net.SecurityProtocolType]::Tls12;"
            + "$x=\"#{location}\";"
            + "$location=$x.Replace(\"\\oaev-agent-caldera.exe\", \"\");"
            + "[Environment]::CurrentDirectory = $location;"
            + "$filename=\"oaev-implant-#{inject}-agent-#{agent}.exe\";"
            + "$" + vars.tokenVar() + ";"
            + "$" + vars.serverVar() + ";"
            + "$" + vars.unsecuredCertificateVar() + ";"
            + "$" + vars.withProxyVar() + ";"
            + "$" + vars.maxSizeVar() + ";"
            + dlVar(cfg, "windows", arch.name()) + ";"
            // -- DOWNLOAD: with error reporting --
            + "try { "
            + "$wc=New-Object System.Net.WebClient;"
            + "$data=$wc.DownloadData($url);"
            + "[io.file]::WriteAllBytes($filename,$data) | Out-Null "
            + "} catch { "
            + psReportError("Implant download failed: ' + $_.Exception.Message + '") + "; "
            + "exit 1 "
            + "};"
            // -- FIREWALL: non-blocking, report warning on failure --
            + "try { "
            + "Remove-NetFirewallRule -DisplayName \"Allow OpenAEV Inbound\" -ErrorAction SilentlyContinue;"
            + "New-NetFirewallRule -DisplayName \"Allow OpenAEV Inbound\" -Direction Inbound -Program \"$location\\$filename\" -Action Allow | Out-Null;"
            + "Remove-NetFirewallRule -DisplayName \"Allow OpenAEV Outbound\" -ErrorAction SilentlyContinue;"
            + "New-NetFirewallRule -DisplayName \"Allow OpenAEV Outbound\" -Direction Outbound -Program \"$location\\$filename\" -Action Allow | Out-Null "
            + "} catch { "
            + psReportWarning("Firewall rules failed (non-admin session): ' + $_.Exception.Message + '")
            + " };"
            // -- SCHEDULED TASK: PaloAlto-specific execution --
            + "$taskName = 'OpenAEV-Inject-#{inject}-Agent-#{agent}';"
            + "$taskDescription = 'OpenAEV EDR validation task - inject #{inject} - agent #{agent} - safe to ignore - will self-delete after execution';"
            + "$implantArgs = '--uri ' + $server + ' --token ' + $token + ' --unsecured-certificate ' + $unsecured_certificate + ' --with-proxy ' + $with_proxy + ' --agent-id #{agent} --inject-id #{inject} --tenant-id #{tenant}';"
            + "$action = New-ScheduledTaskAction -Execute \"$location\\$filename\" -Argument $implantArgs;"
            + "$principal = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest;"
            + "$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -ExecutionTimeLimit (New-TimeSpan -Hours 0);"
            + "Register-ScheduledTask -TaskName $taskName -Description $taskDescription -Action $action -Principal $principal -Settings $settings -Force | Out-Null;"
            + "Start-ScheduledTask -TaskName $taskName;"
            + "$timeout = 300; $elapsed = 0;"
            + "while($elapsed -lt $timeout) {"
            + "  $state = (Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue).State;"
            + "  if($state -eq 'Ready') { break }"
            + "  Start-Sleep -Seconds 1; $elapsed++;"
            + "}"
            + "$info = Get-ScheduledTaskInfo -TaskName $taskName -ErrorAction SilentlyContinue;"
            + "$exitCode = $info.LastTaskResult;"
            + "Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction SilentlyContinue;"
            + "exit $exitCode;");
  }

  private static void buildGenericWindowsCommand(
      Endpoint.PLATFORM_ARCH arch,
      OpenAEVConfig cfg,
      Map<String, String> commands,
      CommandVars vars) {
    commands.put(
        Endpoint.PLATFORM_TYPE.Windows.name() + "." + arch.name(),
        // -- INIT: TLS, location, variables --
        "[Net.ServicePointManager]::SecurityProtocol += [Net.SecurityProtocolType]::Tls12;"
            + "$x=\"#{location}\";"
            + "$location=$x.Replace(\"\\oaev-agent-caldera.exe\", \"\");"
            + "[Environment]::CurrentDirectory = $location;"
            + "$filename=\"oaev-implant-#{inject}-agent-#{agent}.exe\";"
            + "$" + vars.tokenVar() + ";"
            + "$" + vars.serverVar() + ";"
            + "$" + vars.unsecuredCertificateVar() + ";"
            + "$" + vars.withProxyVar() + ";"
            + "$" + vars.maxSizeVar() + ";"
            + dlVar(cfg, "windows", arch.name()) + ";"
            // -- DOWNLOAD: with error reporting --
            + "try { "
            + "$wc=New-Object System.Net.WebClient;"
            + "$data=$wc.DownloadData($url);"
            + "[io.file]::WriteAllBytes($filename,$data) | Out-Null "
            + "} catch { "
            + psReportError("Implant download failed: ' + $_.Exception.Message + '") + "; "
            + "exit 1 "
            + "};"
            // -- FIREWALL: non-blocking, report warning on failure --
            + "try { "
            + "Remove-NetFirewallRule -DisplayName \"Allow OpenAEV Inbound\" -ErrorAction SilentlyContinue;"
            + "New-NetFirewallRule -DisplayName \"Allow OpenAEV Inbound\" -Direction Inbound -Program \"$location\\$filename\" -Action Allow | Out-Null;"
            + "Remove-NetFirewallRule -DisplayName \"Allow OpenAEV Outbound\" -ErrorAction SilentlyContinue;"
            + "New-NetFirewallRule -DisplayName \"Allow OpenAEV Outbound\" -Direction Outbound -Program \"$location\\$filename\" -Action Allow | Out-Null "
            + "} catch { "
            + psReportWarning("Firewall rules failed (non-admin session): ' + $_.Exception.Message + '")
            + " };"
            // -- START: with error reporting --
            + "try { "
            + "Start-Process -FilePath \"$location\\$filename\" -ArgumentList \"--uri $server --token $token --unsecured-certificate $unsecured_certificate --with-proxy $with_proxy --agent-id #{agent} --inject-id #{inject} --tenant-id #{tenant}\" -WindowStyle hidden "
            + "} catch { "
            + psReportError("Implant process failed to start: ' + $_.Exception.Message + '") + "; "
            + "exit 1 "
            + "};");
  }

  private static void buildGenericLinuxCommand(
      Endpoint.PLATFORM_ARCH arch,
      OpenAEVConfig cfg,
      Map<String, String> commands,
      CommandVars vars) {
    commands.put(
        Endpoint.PLATFORM_TYPE.Linux.name() + "." + arch.name(),
        // -- INIT: location, variables --
        "x=\"#{location}\";"
            + "location=$(echo \"$x\" | sed \"s#/openaev-caldera-agent##\");"
            + "filename=oaev-implant-#{inject}-agent-#{agent};"
            + vars.serverVar() + ";"
            + vars.tokenVar() + ";"
            + vars.unsecuredCertificateVar() + ";"
            + vars.withProxyVar() + ";"
            + vars.maxSizeVar() + ";"
            // -- DOWNLOAD: with HTTP status check and error reporting --
            + "http_code=$(curl -s -o \"$location/$filename\" -w '%{http_code}' -X GET "
            + dlUri(cfg, "linux", arch.name()) + ");"
            + "if [ \"$http_code\" != \"200\" ]; then "
            + shReportError("Implant download failed with HTTP status $http_code") + "; "
            + "exit 1; "
            + "fi;"
            // -- FILE CHECK: verify binary was written --
            + "if [ ! -f \"$location/$filename\" ] || [ ! -s \"$location/$filename\" ]; then "
            + shReportError("Implant binary not found or empty after download") + "; "
            + "exit 1; "
            + "fi;"
            + "chmod +x $location/$filename;"
            // -- START: launch implant in background --
            + "$location/$filename --uri $server --token $token --unsecured-certificate $unsecured_certificate --with-proxy $with_proxy --agent-id #{agent} --inject-id #{inject} --tenant-id #{tenant} &");
  }

  private static void buildGenericMacOSCommand(
      Endpoint.PLATFORM_ARCH arch,
      OpenAEVConfig cfg,
      Map<String, String> commands,
      CommandVars vars) {
    commands.put(
        Endpoint.PLATFORM_TYPE.MacOS.name() + "." + arch.name(),
        // -- INIT: location, variables --
        "x=\"#{location}\";"
            + "location=$(echo \"$x\" | sed \"s#/openaev-caldera-agent##\");"
            + "filename=oaev-implant-#{inject}-agent-#{agent};"
            + vars.serverVar() + ";"
            + vars.tokenVar() + ";"
            + vars.unsecuredCertificateVar() + ";"
            + vars.withProxyVar() + ";"
            + (Endpoint.PLATFORM_ARCH.x86_64.equals(arch)
                ? ""
                : "$") // TODO: Should find a way to test on an x86 mac if the diff is necessary
            + vars.maxSizeVar() + ";"
            // -- DOWNLOAD: with HTTP status check and error reporting --
            + "http_code=$(curl -s -o \"$location/$filename\" -w '%{http_code}' -X GET "
            + dlUri(cfg, "macos", arch.name()) + ");"
            + "if [ \"$http_code\" != \"200\" ]; then "
            + shReportError("Implant download failed with HTTP status $http_code") + "; "
            + "exit 1; "
            + "fi;"
            // -- FILE CHECK: verify binary was written --
            + "if [ ! -f \"$location/$filename\" ] || [ ! -s \"$location/$filename\" ]; then "
            + shReportError("Implant binary not found or empty after download") + "; "
            + "exit 1; "
            + "fi;"
            + "chmod +x $location/$filename;"
            // -- START: launch implant in background --
            + "$location/$filename --uri $server --token $token --unsecured-certificate $unsecured_certificate --with-proxy $with_proxy --agent-id #{agent} --inject-id #{inject} --tenant-id #{tenant} &");
  }
}
