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

  // -- ERROR REPORTING HELPERS --

  /**
   * Builds a PowerShell snippet that reports an error back to the platform. Uses the existing
   * inject execution trace endpoint so the error appears in the inject status UI. The snippet is
   * designed to be embedded inside a catch block.
   *
   * @param step human-readable step name (e.g. "Download", "Firewall", "Start")
   * @return PowerShell code that POSTs an error trace to the platform
   */
  private static String psErrorCallback(String step) {
    // Report the error to the platform via the inject execution endpoint.
    // $_.Exception.Message contains the PowerShell exception message.
    // We use Invoke-WebRequest with -Method POST to send the trace.
    return "$errMsg='" + step + " failed: ' + $_.Exception.Message;"
        + "$body='{\"execution_message\":\"' + $errMsg + '\",\"execution_status\":\"ERROR\",\"execution_action\":\"complete\",\"execution_duration\":0}';"
        + "try { Invoke-WebRequest -Uri \"$server/api/agent/#{agent}/inject/#{inject}/execution\" "
        + "-Method POST -ContentType 'application/json' -Body $body "
        + "-Headers @{'Authorization'=\"Bearer $token\"} -UseBasicParsing | Out-Null } "
        + "catch { Write-Host \"[OpenAEV] Failed to report error: $($_.Exception.Message)\" }";
  }

  /**
   * Builds a Bash snippet that reports an error back to the platform.
   *
   * @param step human-readable step name (e.g. "Download", "Firewall", "Start")
   * @param errorVar the bash variable containing the error message
   * @return Bash code that POSTs an error trace to the platform
   */
  private static String bashErrorCallback(String step, String errorVar) {
    return "err_msg=\"" + step + " failed: $" + errorVar + "\";"
        + "err_body='{\"execution_message\":\"'\"$err_msg\"'\",\"execution_status\":\"ERROR\",\"execution_action\":\"complete\",\"execution_duration\":0}';"
        + "curl -s -X POST \"$server/api/agent/#{agent}/inject/#{inject}/execution\" "
        + "-H 'Content-Type: application/json' -H \"Authorization: Bearer $token\" "
        + "-d \"$err_body\" > /dev/null 2>&1 || true";
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
    // Palo Alto Windows commands use ScheduledTask with SYSTEM privileges.
    // Error reporting wraps the download step — ScheduledTask failures are harder to catch
    // because the task runs asynchronously under SYSTEM context.
    commands.put(
        PALOALTOCORTEX_EXECUTOR_NAME
            + "."
            + Endpoint.PLATFORM_TYPE.Windows.name()
            + "."
            + arch.name(),
        "[Net.ServicePointManager]::SecurityProtocol += [Net.SecurityProtocolType]::Tls12;"
            + "$x=\"#{location}\";$location=$x.Replace(\"\\oaev-agent-caldera.exe\", \"\");[Environment]::CurrentDirectory = $location;"
            + "$filename=\"oaev-implant-#{inject}-agent-#{agent}.exe\";"
            + "$" + vars.tokenVar() + ";"
            + "$" + vars.serverVar() + ";"
            + "$" + vars.unsecuredCertificateVar() + ";"
            + "$" + vars.withProxyVar() + ";"
            + "$" + vars.maxSizeVar() + ";"
            + dlVar(cfg, "windows", arch.name()) + ";"
            // -- DOWNLOAD with error reporting --
            + "try { $wc=New-Object System.Net.WebClient;$data=$wc.DownloadData($url);[io.file]::WriteAllBytes($filename,$data) | Out-Null; Write-Host '[OpenAEV] Implant binary downloaded successfully' }"
            + " catch { Write-Host \"[OpenAEV] Download failed: $($_.Exception.Message)\";"
            + psErrorCallback("Implant download") + "; exit 1 };"
            // -- FIREWALL with warning (non-blocking for Palo Alto — ScheduledTask runs as SYSTEM) --
            + "try { Remove-NetFirewallRule -DisplayName \"Allow OpenAEV Inbound\" -ErrorAction SilentlyContinue } catch {};"
            + "try { New-NetFirewallRule -DisplayName \"Allow OpenAEV Inbound\" -Direction Inbound -Program \"$location\\$filename\" -Action Allow | Out-Null } catch { Write-Host \"[OpenAEV] Warning: Inbound firewall rule failed (non-admin): $($_.Exception.Message)\" };"
            + "try { Remove-NetFirewallRule -DisplayName \"Allow OpenAEV Outbound\" -ErrorAction SilentlyContinue } catch {};"
            + "try { New-NetFirewallRule -DisplayName \"Allow OpenAEV Outbound\" -Direction Outbound -Program \"$location\\$filename\" -Action Allow | Out-Null } catch { Write-Host \"[OpenAEV] Warning: Outbound firewall rule failed (non-admin): $($_.Exception.Message)\" };"
            // -- SCHEDULED TASK (Palo Alto specific) --
            + "$taskName = 'OpenAEV-Inject-#{inject}-Agent-#{agent}';"
            + "$taskDescription = 'OpenAEV EDR validation task - inject #{inject} - agent #{agent} - safe to ignore - will self-delete after execution';"
            + "$implantArgs = '--uri ' + $server + ' --token ' + $token + ' --unsecured-certificate ' + $unsecured_certificate + ' --with-proxy ' + $with_proxy + ' --agent-id #{agent} --inject-id #{inject} --tenant-id #{tenant}';"
            + "try {"
            + "$action = New-ScheduledTaskAction -Execute \"$location\\$filename\" -Argument $implantArgs;"
            + "$principal = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest;"
            + "$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -ExecutionTimeLimit (New-TimeSpan -Hours 0);"
            + "Register-ScheduledTask -TaskName $taskName -Description $taskDescription -Action $action -Principal $principal -Settings $settings -Force | Out-Null;"
            + "Start-ScheduledTask -TaskName $taskName;"
            + "Write-Host '[OpenAEV] Scheduled task started successfully'"
            + "} catch { Write-Host \"[OpenAEV] ScheduledTask failed: $($_.Exception.Message)\";"
            + psErrorCallback("ScheduledTask creation/start") + "; exit 1 };"
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
    // Generic Windows: wrap download, firewall, and start in try/catch blocks
    // with error reporting back to the platform.
    commands.put(
        Endpoint.PLATFORM_TYPE.Windows.name() + "." + arch.name(),
        "[Net.ServicePointManager]::SecurityProtocol += [Net.SecurityProtocolType]::Tls12;"
            + "$x=\"#{location}\";$location=$x.Replace(\"\\oaev-agent-caldera.exe\", \"\");[Environment]::CurrentDirectory = $location;"
            + "$filename=\"oaev-implant-#{inject}-agent-#{agent}.exe\";"
            + "$" + vars.tokenVar() + ";"
            + "$" + vars.serverVar() + ";"
            + "$" + vars.unsecuredCertificateVar() + ";"
            + "$" + vars.withProxyVar() + ";"
            + "$" + vars.maxSizeVar() + ";"
            + dlVar(cfg, "windows", arch.name()) + ";"
            // -- DOWNLOAD with error reporting --
            + "try { $wc=New-Object System.Net.WebClient;$data=$wc.DownloadData($url);[io.file]::WriteAllBytes($filename,$data) | Out-Null; Write-Host '[OpenAEV] Implant binary downloaded successfully' }"
            + " catch { Write-Host \"[OpenAEV] Download failed: $($_.Exception.Message)\";"
            + psErrorCallback("Implant download") + "; exit 1 };"
            // -- FIREWALL with warning (non-blocking — implant may still work on localhost without rules) --
            + "try { Remove-NetFirewallRule -DisplayName \"Allow OpenAEV Inbound\" -ErrorAction SilentlyContinue } catch {};"
            + "try { New-NetFirewallRule -DisplayName \"Allow OpenAEV Inbound\" -Direction Inbound -Program \"$location\\$filename\" -Action Allow | Out-Null } catch { Write-Host \"[OpenAEV] Warning: Inbound firewall rule failed (non-admin): $($_.Exception.Message)\" };"
            + "try { Remove-NetFirewallRule -DisplayName \"Allow OpenAEV Outbound\" -ErrorAction SilentlyContinue } catch {};"
            + "try { New-NetFirewallRule -DisplayName \"Allow OpenAEV Outbound\" -Direction Outbound -Program \"$location\\$filename\" -Action Allow | Out-Null } catch { Write-Host \"[OpenAEV] Warning: Outbound firewall rule failed (non-admin): $($_.Exception.Message)\" };"
            // -- START with error reporting --
            + "try { Start-Process -FilePath \"$location\\$filename\" -ArgumentList \"--uri $server --token $token --unsecured-certificate $unsecured_certificate --with-proxy $with_proxy --agent-id #{agent} --inject-id #{inject} --tenant-id #{tenant}\" -WindowStyle hidden; Write-Host '[OpenAEV] Implant process started' }"
            + " catch { Write-Host \"[OpenAEV] Start failed: $($_.Exception.Message)\";"
            + psErrorCallback("Implant start") + "; exit 1 };");
  }

  private static void buildGenericLinuxCommand(
      Endpoint.PLATFORM_ARCH arch,
      OpenAEVConfig cfg,
      Map<String, String> commands,
      CommandVars vars) {
    // Linux: check curl exit code and file existence before executing.
    // Report errors back to the platform via curl POST.
    commands.put(
        Endpoint.PLATFORM_TYPE.Linux.name() + "." + arch.name(),
        "x=\"#{location}\";location=$(echo \"$x\" | sed \"s#/openaev-caldera-agent##\");"
            + "filename=oaev-implant-#{inject}-agent-#{agent};"
            + vars.serverVar() + ";"
            + vars.tokenVar() + ";"
            + vars.unsecuredCertificateVar() + ";"
            + vars.withProxyVar() + ";"
            + vars.maxSizeVar() + ";"
            // -- DOWNLOAD with error check --
            + "http_code=$(curl -s -o \"$location/$filename\" -w '%{http_code}' -X GET "
            + dlUri(cfg, "linux", arch.name()) + ");"
            + "if [ \"$http_code\" -ne 200 ] || [ ! -s \"$location/$filename\" ]; then "
            + "dl_err=\"HTTP $http_code or empty file\";"
            + bashErrorCallback("Implant download", "dl_err") + ";"
            + "echo \"[OpenAEV] Download failed: $dl_err\"; exit 1; fi;"
            + "echo '[OpenAEV] Implant binary downloaded successfully';"
            + "chmod +x $location/$filename;"
            // -- START with error check --
            + "$location/$filename --uri $server --token $token --unsecured-certificate $unsecured_certificate --with-proxy $with_proxy --agent-id #{agent} --inject-id #{inject} --tenant-id #{tenant} &"
            + " start_pid=$!; sleep 1;"
            + "if ! kill -0 $start_pid 2>/dev/null; then "
            + "start_err=\"Implant process exited immediately (PID $start_pid)\";"
            + bashErrorCallback("Implant start", "start_err") + ";"
            + "echo \"[OpenAEV] Start failed: $start_err\"; exit 1; fi;"
            + "echo '[OpenAEV] Implant process started (PID '$start_pid')'");
  }

  private static void buildGenericMacOSCommand(
      Endpoint.PLATFORM_ARCH arch,
      OpenAEVConfig cfg,
      Map<String, String> commands,
      CommandVars vars) {
    // macOS: same error reporting pattern as Linux.
    commands.put(
        Endpoint.PLATFORM_TYPE.MacOS.name() + "." + arch.name(),
        "x=\"#{location}\";location=$(echo \"$x\" | sed \"s#/openaev-caldera-agent##\");"
            + "filename=oaev-implant-#{inject}-agent-#{agent};"
            + vars.serverVar() + ";"
            + vars.tokenVar() + ";"
            + vars.unsecuredCertificateVar() + ";"
            + vars.withProxyVar() + ";"
            + (Endpoint.PLATFORM_ARCH.x86_64.equals(arch) ? "" : "$")
            + vars.maxSizeVar() + ";"
            // -- DOWNLOAD with error check --
            + "http_code=$(curl -s -o \"$location/$filename\" -w '%{http_code}' -X GET "
            + dlUri(cfg, "macos", arch.name()) + ");"
            + "if [ \"$http_code\" -ne 200 ] || [ ! -s \"$location/$filename\" ]; then "
            + "dl_err=\"HTTP $http_code or empty file\";"
            + bashErrorCallback("Implant download", "dl_err") + ";"
            + "echo \"[OpenAEV] Download failed: $dl_err\"; exit 1; fi;"
            + "echo '[OpenAEV] Implant binary downloaded successfully';"
            + "chmod +x $location/$filename;"
            // -- START with error check --
            + "$location/$filename --uri $server --token $token --unsecured-certificate $unsecured_certificate --with-proxy $with_proxy --agent-id #{agent} --inject-id #{inject} --tenant-id #{tenant} &"
            + " start_pid=$!; sleep 1;"
            + "if ! kill -0 $start_pid 2>/dev/null; then "
            + "start_err=\"Implant process exited immediately (PID $start_pid)\";"
            + bashErrorCallback("Implant start", "start_err") + ";"
            + "echo \"[OpenAEV] Start failed: $start_err\"; exit 1; fi;"
            + "echo '[OpenAEV] Implant process started (PID '$start_pid')'");
  }
}
