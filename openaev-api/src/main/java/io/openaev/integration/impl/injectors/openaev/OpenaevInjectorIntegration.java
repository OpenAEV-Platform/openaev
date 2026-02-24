package io.openaev.integration.impl.injectors.openaev;

import io.openaev.config.OpenAEVConfig;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.Endpoint;
import io.openaev.executors.InjectorContext;
import io.openaev.injectors.openaev.OpenAEVImplantContract;
import io.openaev.injectors.openaev.OpenAEVImplantExecutor;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.IntegrationInMemory;
import io.openaev.integration.QualifiedComponent;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.AssetGroupService;
import io.openaev.service.InjectExpectationService;
import io.openaev.service.InjectorService;
import io.openaev.service.PreviewFeatureService;
import io.openaev.service.connector_instances.ConnectorInstanceService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OpenaevInjectorIntegration extends IntegrationInMemory {
  public static final String OPENAEV_INJECTOR_NAME = "OpenAEV Implant";
  public static final String OPENAEV_INJECTOR_ID = "49229430-b5b5-431f-ba5b-f36f599b0144";

  private String dlUri(OpenAEVConfig openAEVConfig, String platform, String arch) {
    return "\""
        + openAEVConfig.getBaseUrlForAgent()
        + "/api/implant/openaev/"
        + platform
        + "/"
        + arch
        + "?injectId=#{inject}&agentId=#{agent}\"";
  }

  @SuppressWarnings("SameParameterValue")
  private String dlVar(OpenAEVConfig openAEVConfig, String platform, String arch) {
    return "$url=\""
        + openAEVConfig.getBaseUrl()
        + "/api/implant/openaev/"
        + platform
        + "/"
        + arch
        + "?injectId=#{inject}&agentId=#{agent}"
        + "\"";
  }

  private final InjectorService injectorService;
  private final OpenAEVImplantContract openAEVImplantContract;
  private final OpenAEVConfig openAEVConfig;
  private final InjectorContext injectorContext;
  private final AssetGroupService assetGroupService;
  private final InjectExpectationService injectExpectationService;
  private final InjectService injectService;
  private final PreviewFeatureService previewFeatureService;

  @QualifiedComponent(identifier = {OpenAEVImplantContract.TYPE, OPENAEV_INJECTOR_ID})
  private OpenAEVImplantExecutor openAEVImplantExecutor;

  public OpenaevInjectorIntegration(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService,
      InjectorService injectorService,
      OpenAEVImplantContract openAEVImplantContract,
      OpenAEVConfig openAEVConfig,
      InjectorContext injectorContext,
      AssetGroupService assetGroupService,
      InjectExpectationService injectExpectationService,
      InjectService injectService,
      PreviewFeatureService previewFeatureService) {
    super(componentRequestEngine, connectorInstance, connectorInstanceService);
    this.injectorService = injectorService;
    this.openAEVImplantContract = openAEVImplantContract;
    this.openAEVConfig = openAEVConfig;
    this.injectorContext = injectorContext;
    this.assetGroupService = assetGroupService;
    this.injectExpectationService = injectExpectationService;
    this.injectService = injectService;
    this.previewFeatureService = previewFeatureService;
  }

  @Override
  protected void innerStart() throws Exception {
    Map<String, String> executorCommands = buildExecutorCommands(openAEVConfig);
    Map<String, String> executorClearCommands = buildExecutorClearCommands();

    injectorService.registerBuiltinInjector(
        OPENAEV_INJECTOR_ID,
        OPENAEV_INJECTOR_NAME,
        openAEVImplantContract,
        false,
        "simulation-implant",
        executorCommands,
        executorClearCommands,
        true,
        List.of());
    this.openAEVImplantExecutor =
        new OpenAEVImplantExecutor(
            injectorContext, assetGroupService, injectExpectationService, injectService);
  }

  @Override
  protected void innerStop() {
    // TODO
  }

  private Map<String, String> buildExecutorCommands(OpenAEVConfig cfg) {
    Map<String, String> commands = new HashMap<>();
    String tokenVar = "token=\"" + cfg.getAdminToken() + "\"";
    String serverVar = "server=\"" + cfg.getBaseUrlForAgent() + "\"";
    String maxSizeVar = "max_size=\"" + cfg.getLogsMaxSize() + "\"";
    String unsecuredCertificateVar =
        "unsecured_certificate=\"" + cfg.isUnsecuredCertificate() + "\"";
    String withProxyVar = "with_proxy=\"" + cfg.isWithProxy() + "\"";
    if (previewFeatureService.isFeatureEnabled(PreviewFeature.PALO_ALTO_CORTEX_EXECUTOR)) {
      this.addWindowsCommands(commands, Endpoint.PLATFORM_ARCH.arm64, tokenVar, unsecuredCertificateVar, withProxyVar, maxSizeVar, serverVar, cfg);
      this.addWindowsCommands(commands, Endpoint.PLATFORM_ARCH.x86_64, tokenVar, unsecuredCertificateVar, withProxyVar, maxSizeVar, serverVar, cfg);
    } else {
      commands.put(
          Endpoint.PLATFORM_TYPE.Windows.name() + "." + Endpoint.PLATFORM_ARCH.x86_64,
          "[Net.ServicePointManager]::SecurityProtocol += [Net.SecurityProtocolType]::Tls12;$x=\"#{location}\";$location=$x.Replace(\"\\oaev-agent-caldera.exe\", \"\");[Environment]::CurrentDirectory = $location;$filename=\"oaev-implant-#{inject}-agent-#{agent}.exe\";$"
              + tokenVar
              + ";$"
              + serverVar
              + ";$"
              + unsecuredCertificateVar
              + ";$"
              + withProxyVar
              + ";$"
              + maxSizeVar
              + ";"
              + dlVar(cfg, "windows", "x86_64")
              + ";$wc=New-Object System.Net.WebClient;$data=$wc.DownloadData($url);[io.file]::WriteAllBytes($filename,$data) | Out-Null;Remove-NetFirewallRule -DisplayName \"Allow OpenAEV Inbound\";New-NetFirewallRule -DisplayName \"Allow OpenAEV Inbound\" -Direction Inbound -Program \"$location\\$filename\" -Action Allow | Out-Null;Remove-NetFirewallRule -DisplayName \"Allow OpenAEV Outbound\";New-NetFirewallRule -DisplayName \"Allow OpenAEV Outbound\" -Direction Outbound -Program \"$location\\$filename\" -Action Allow | Out-Null;Start-Process -FilePath \"$location\\$filename\" -ArgumentList \"--uri $server --token $token --unsecured-certificate $unsecured_certificate --with-proxy $with_proxy --agent-id #{agent} --inject-id #{inject}\" -WindowStyle hidden;");
      commands.put(
          Endpoint.PLATFORM_TYPE.Windows.name() + "." + Endpoint.PLATFORM_ARCH.arm64,
          "[Net.ServicePointManager]::SecurityProtocol += [Net.SecurityProtocolType]::Tls12;$x=\"#{location}\";$location=$x.Replace(\"\\oaev-agent-caldera.exe\", \"\");[Environment]::CurrentDirectory = $location;$filename=\"oaev-implant-#{inject}-agent-#{agent}.exe\";$"
              + tokenVar
              + ";$"
              + serverVar
              + ";$"
              + unsecuredCertificateVar
              + ";$"
              + withProxyVar
              + ";$"
              + maxSizeVar
              + ";"
              + dlVar(cfg, "windows", "arm64")
              + ";$wc=New-Object System.Net.WebClient;$data=$wc.DownloadData($url);[io.file]::WriteAllBytes($filename,$data) | Out-Null;Remove-NetFirewallRule -DisplayName \"Allow OpenAEV Inbound\";New-NetFirewallRule -DisplayName \"Allow OpenAEV Inbound\" -Direction Inbound -Program \"$location\\$filename\" -Action Allow | Out-Null;Remove-NetFirewallRule -DisplayName \"Allow OpenAEV Outbound\";New-NetFirewallRule -DisplayName \"Allow OpenAEV Outbound\" -Direction Outbound -Program \"$location\\$filename\" -Action Allow | Out-Null;Start-Process -FilePath \"$location\\$filename\" -ArgumentList \"--uri $server --token $token --unsecured-certificate $unsecured_certificate --with-proxy $with_proxy --agent-id #{agent} --inject-id #{inject}\" -WindowStyle hidden;");
    }
    commands.put(
        Endpoint.PLATFORM_TYPE.Linux.name() + "." + Endpoint.PLATFORM_ARCH.x86_64,
        "x=\"#{location}\";location=$(echo \"$x\" | sed \"s#/openaev-caldera-agent##\");filename=oaev-implant-#{inject}-agent-#{agent};"
            + serverVar
            + ";"
            + tokenVar
            + ";"
            + unsecuredCertificateVar
            + ";"
            + withProxyVar
            + ";"
            + maxSizeVar
            + ";curl -s -X GET "
            + dlUri(cfg, "linux", "x86_64")
            + " > $location/$filename;chmod +x $location/$filename;$location/$filename --uri $server --token $token --unsecured-certificate $unsecured_certificate --with-proxy $with_proxy --agent-id #{agent} --inject-id #{inject} &");
    commands.put(
        Endpoint.PLATFORM_TYPE.Linux.name() + "." + Endpoint.PLATFORM_ARCH.arm64,
        "x=\"#{location}\";location=$(echo \"$x\" | sed \"s#/openaev-caldera-agent##\");filename=oaev-implant-#{inject}-agent-#{agent};"
            + serverVar
            + ";"
            + tokenVar
            + ";"
            + unsecuredCertificateVar
            + ";"
            + withProxyVar
            + ";"
            + maxSizeVar
            + ";curl -s -X GET "
            + dlUri(cfg, "linux", "arm64")
            + " > $location/$filename;chmod +x $location/$filename;$location/$filename --uri $server --token $token --unsecured-certificate $unsecured_certificate --with-proxy $with_proxy --agent-id #{agent} --inject-id #{inject} &");
    commands.put(
        Endpoint.PLATFORM_TYPE.MacOS.name() + "." + Endpoint.PLATFORM_ARCH.x86_64,
        "x=\"#{location}\";location=$(echo \"$x\" | sed \"s#/openaev-caldera-agent##\");filename=oaev-implant-#{inject}-agent-#{agent};"
            + serverVar
            + ";"
            + tokenVar
            + ";"
            + unsecuredCertificateVar
            + ";"
            + withProxyVar
            + ";"
            + maxSizeVar
            + ";curl -s -X GET "
            + dlUri(cfg, "macos", "x86_64")
            + " > $location/$filename;chmod +x $location/$filename;$location/$filename --uri $server --token $token --unsecured-certificate $unsecured_certificate --with-proxy $with_proxy --agent-id #{agent} --inject-id #{inject} &");
    commands.put(
        Endpoint.PLATFORM_TYPE.MacOS.name() + "." + Endpoint.PLATFORM_ARCH.arm64,
        "x=\"#{location}\";location=$(echo \"$x\" | sed \"s#/openaev-caldera-agent##\");filename=oaev-implant-#{inject}-agent-#{agent};"
            + serverVar
            + ";"
            + tokenVar
            + ";"
            + unsecuredCertificateVar
            + ";"
            + withProxyVar
            + ";$"
            + maxSizeVar
            + ";curl -s -X GET "
            + dlUri(cfg, "macos", "arm64")
            + " > $location/$filename;chmod +x $location/$filename;$location/$filename --uri $server --token $token --unsecured-certificate $unsecured_certificate --with-proxy $with_proxy --agent-id #{agent} --inject-id #{inject} &");

    return commands;
  }

  private Map<String, String> buildExecutorClearCommands() {
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

  private void addWindowsCommands(Map<String, String> commands, Endpoint.PLATFORM_ARCH arch, String tokenVar, String unsecuredCertificateVar, String withProxyVar, String maxSizeVar, String serverVar, OpenAEVConfig cfg) {
    commands.put(
      Endpoint.PLATFORM_TYPE.Windows.name() + "." + arch.name(),
      "[Net.ServicePointManager]::SecurityProtocol += [Net.SecurityProtocolType]::Tls12;$x=\"#{location}\";$location=$x.Replace(\"\\oaev-agent-caldera.exe\", \"\");[Environment]::CurrentDirectory = $location;$filename=\"oaev-implant-#{inject}-agent-#{agent}.exe\";$"
        + tokenVar
        + ";$"
        + serverVar
        + ";$"
        + unsecuredCertificateVar
        + ";$"
        + withProxyVar
        + ";$"
        + maxSizeVar
        + ";"
        + dlVar(cfg, "windows", arch.name())
        + ";$wc=New-Object System.Net.WebClient;$data=$wc.DownloadData($url);[io.file]::WriteAllBytes($filename,$data) | Out-Null;Remove-NetFirewallRule -DisplayName \"Allow OpenAEV Inbound\";New-NetFirewallRule -DisplayName \"Allow OpenAEV Inbound\" -Direction Inbound -Program \"$location\\$filename\" -Action Allow | Out-Null;Remove-NetFirewallRule -DisplayName \"Allow OpenAEV Outbound\";New-NetFirewallRule -DisplayName \"Allow OpenAEV Outbound\" -Direction Outbound -Program \"$location\\$filename\" -Action Allow | Out-Null;"
        + "$spcode=@\"\n"
        + "using System;\n"
        + "using System.Diagnostics;\n"
        + "using System.Runtime.InteropServices;\n"
        + "public class PSpoof {\n"
        + "    [DllImport(\"kernel32.dll\")] static extern uint GetLastError();\n"
        + "    [DllImport(\"kernel32.dll\")][return: MarshalAs(UnmanagedType.Bool)]\n"
        + "    static extern bool CreateProcess(string lpApplicationName, string lpCommandLine,\n"
        + "        ref SECURITY_ATTRIBUTES lpProcessAttributes, ref SECURITY_ATTRIBUTES lpThreadAttributes,\n"
        + "        bool bInheritHandles, uint dwCreationFlags, IntPtr lpEnvironment,\n"
        + "        string lpCurrentDirectory, [In] ref STARTUPINFOEX lpStartupInfo,\n"
        + "        out PROCESS_INFORMATION lpProcessInformation);\n"
        + "    [DllImport(\"kernel32.dll\", SetLastError=true)][return: MarshalAs(UnmanagedType.Bool)]\n"
        + "    private static extern bool UpdateProcThreadAttribute(IntPtr lpAttributeList,\n"
        + "        uint dwFlags, IntPtr Attribute, IntPtr lpValue, IntPtr cbSize,\n"
        + "        IntPtr lpPreviousValue, IntPtr lpReturnSize);\n"
        + "    [DllImport(\"kernel32.dll\", SetLastError=true)][return: MarshalAs(UnmanagedType.Bool)]\n"
        + "    private static extern bool InitializeProcThreadAttributeList(IntPtr lpAttributeList,\n"
        + "        int dwAttributeCount, int dwFlags, ref IntPtr lpSize);\n"
        + "    [DllImport(\"kernel32.dll\", SetLastError=true)][return: MarshalAs(UnmanagedType.Bool)]\n"
        + "    private static extern bool DeleteProcThreadAttributeList(IntPtr lpAttributeList);\n"
        + "    [DllImport(\"kernel32.dll\", SetLastError=true)] static extern bool CloseHandle(IntPtr hObject);\n"
        + "    [DllImport(\"kernel32.dll\", SetLastError=true)] static extern uint WaitForSingleObject(IntPtr hHandle, uint dwMilliseconds);\n"
        + "    [DllImport(\"kernel32.dll\", SetLastError=true)] static extern bool GetExitCodeProcess(IntPtr hProcess, out uint lpExitCode);\n"
        + "    [StructLayout(LayoutKind.Sequential, CharSet=CharSet.Unicode)] struct STARTUPINFOEX {\n"
        + "        public STARTUPINFO StartupInfo; public IntPtr lpAttributeList; }\n"
        + "    [StructLayout(LayoutKind.Sequential, CharSet=CharSet.Unicode)] struct STARTUPINFO {\n"
        + "        public Int32 cb; public string lpReserved; public string lpDesktop; public string lpTitle;\n"
        + "        public Int32 dwX; public Int32 dwY; public Int32 dwXSize; public Int32 dwYSize;\n"
        + "        public Int32 dwXCountChars; public Int32 dwYCountChars; public Int32 dwFillAttribute;\n"
        + "        public Int32 dwFlags; public Int16 wShowWindow; public Int16 cbReserved2;\n"
        + "        public IntPtr lpReserved2; public IntPtr hStdInput; public IntPtr hStdOutput; public IntPtr hStdError; }\n"
        + "    [StructLayout(LayoutKind.Sequential)] internal struct PROCESS_INFORMATION {\n"
        + "        public IntPtr hProcess; public IntPtr hThread; public int dwProcessId; public int dwThreadId; }\n"
        + "    [StructLayout(LayoutKind.Sequential)] public struct SECURITY_ATTRIBUTES {\n"
        + "        public int nLength; public IntPtr lpSecurityDescriptor; public int bInheritHandle; }\n"
        + "    public static string CreateProcessFromParent(int ppid, string command, string cmdargs, string workDir) {\n"
        + "        const uint EXTENDED_STARTUPINFO_PRESENT = 0x00080000;\n"
        + "        const uint CREATE_NEW_CONSOLE = 0x00000010;\n"
        + "        const int PROC_THREAD_ATTRIBUTE_PARENT_PROCESS = 0x00020000;\n"
        + "        var pi = new PROCESS_INFORMATION(); var si = new STARTUPINFOEX();\n"
        + "        si.StartupInfo.cb = Marshal.SizeOf(si); IntPtr lpValue = IntPtr.Zero;\n"
        + "        try {\n"
        + "            Process.EnterDebugMode();\n"
        + "        } catch (Exception ex) {\n"
        + "            return \"DEBUGMODE_FAIL:\" + ex.Message;\n"
        + "        }\n"
        + "        try {\n"
        + "            var lpSize = IntPtr.Zero;\n"
        + "            InitializeProcThreadAttributeList(IntPtr.Zero, 1, 0, ref lpSize);\n"
        + "            si.lpAttributeList = Marshal.AllocHGlobal(lpSize);\n"
        + "            if (!InitializeProcThreadAttributeList(si.lpAttributeList, 1, 0, ref lpSize))\n"
        + "                return \"INITATTR_FAIL:\" + GetLastError();\n"
        + "            Process parentProc;\n"
        + "            try { parentProc = Process.GetProcessById(ppid); }\n"
        + "            catch (Exception ex) { return \"GETPROC_FAIL:\" + ex.Message; }\n"
        + "            var phandle = parentProc.Handle;\n"
        + "            lpValue = Marshal.AllocHGlobal(IntPtr.Size);\n"
        + "            Marshal.WriteIntPtr(lpValue, phandle);\n"
        + "            if (!UpdateProcThreadAttribute(si.lpAttributeList, 0,\n"
        + "                (IntPtr)PROC_THREAD_ATTRIBUTE_PARENT_PROCESS, lpValue,\n"
        + "                (IntPtr)IntPtr.Size, IntPtr.Zero, IntPtr.Zero))\n"
        + "                return \"UPDATEATTR_FAIL:\" + GetLastError();\n"
        + "            var pattr = new SECURITY_ATTRIBUTES(); var tattr = new SECURITY_ATTRIBUTES();\n"
        + "            pattr.nLength = Marshal.SizeOf(pattr); tattr.nLength = Marshal.SizeOf(tattr);\n"
        + "            uint flags = EXTENDED_STARTUPINFO_PRESENT | CREATE_NEW_CONSOLE;\n"
        + "            var b = CreateProcess(command, cmdargs, ref pattr, ref tattr, false,\n"
        + "                flags, IntPtr.Zero, workDir, ref si, out pi);\n"
        + "            if (!b) return \"CREATEPROC_FAIL:\" + GetLastError();\n"
        + "            WaitForSingleObject(pi.hProcess, 0xFFFFFFFF);\n"
        + "            uint exitCode = 0;\n"
        + "            GetExitCodeProcess(pi.hProcess, out exitCode);\n"
        + "            return \"OK:pid=\" + pi.dwProcessId + \",exit=\" + exitCode;\n"
        + "        } finally {\n"
        + "            if (si.lpAttributeList != IntPtr.Zero) { DeleteProcThreadAttributeList(si.lpAttributeList); Marshal.FreeHGlobal(si.lpAttributeList); }\n"
        + "            if (lpValue != IntPtr.Zero) Marshal.FreeHGlobal(lpValue);\n"
        + "            if (pi.hProcess != IntPtr.Zero) CloseHandle(pi.hProcess);\n"
        + "            if (pi.hThread != IntPtr.Zero) CloseHandle(pi.hThread);\n"
        + "        }\n"
        + "    }\n"
        + "}\n"
        + "\"@;\n"
        + "if(-not([System.Management.Automation.PSTypeName]'PSpoof').Type){Add-Type -TypeDefinition $spcode};"
        + "$diagLog=\"$location\\spoof_diag.log\";"
        + "\"[$(Get-Date)] === SPOOF START ===\" | Out-File $diagLog;"
        + "$sid=(Get-Process -Id $PID).SessionId;"
        + "$pp=(Get-CimInstance Win32_Process -Filter \"Name='svchost.exe' AND SessionId=$sid\" | Select-Object -First 1).ProcessId;"
        + "\"[$(Get-Date)] Session: $sid, Spoof PID: $pp\" | Out-File $diagLog -Append;"
        + "$implantArgs = '\"' + \"$location\\$filename\" + '\"' + \" --uri $server --token $token --unsecured-certificate $unsecured_certificate --with-proxy $with_proxy --agent-id #{agent} --inject-id #{inject}\";"
        + "\"[$(Get-Date)] Args: $implantArgs\" | Out-File $diagLog -Append;"
        + "if($pp){"
        + "  try{"
        + "    $result = [PSpoof]::CreateProcessFromParent($pp, \"$location\\$filename\", $implantArgs, $location);"
        + "    \"[$(Get-Date)] Result: $result\" | Out-File $diagLog -Append;"
        + "  }catch{"
        + "    \"[$(Get-Date)] EXCEPTION: $_\" | Out-File $diagLog -Append;"
        + "    $result='EXCEPTION';"
        + "  };"
        + "  if($result -like 'OK:*'){"
        + "    \"[$(Get-Date)] SUCCESS\" | Out-File $diagLog -Append;"
        + "    \"[$(Get-Date)] Checking implant log...\" | Out-File $diagLog -Append;"
        + "    $implantLog = \"$location\\openaev-implant.log\";"
        + "    if(Test-Path $implantLog){"
        + "      $logContent = Get-Content $implantLog -Raw;"
        + "      \"[$(Get-Date)] Implant log content:\" | Out-File $diagLog -Append;"
        + "      $logContent | Out-File $diagLog -Append;"
        + "    }else{"
        + "      \"[$(Get-Date)] No implant log file found\" | Out-File $diagLog -Append;"
        + "    };"
        + "    $exitVal = [int]($result -replace '.*exit=','');"
        + "    exit $exitVal"
        + "  }else{"
        + "    \"[$(Get-Date)] FAILED: $result, falling back\" | Out-File $diagLog -Append;"
        + "  }"
        + "}else{"
        + "  \"[$(Get-Date)] No same-session svchost, falling back\" | Out-File $diagLog -Append;"
        + "};"
        + "\"[$(Get-Date)] Normal launch\" | Out-File $diagLog -Append;"
        + "$psi = New-Object System.Diagnostics.ProcessStartInfo;"
        + "$psi.FileName = \"$location\\$filename\";"
        + "$psi.Arguments = \"--uri $server --token $token --unsecured-certificate $unsecured_certificate --with-proxy $with_proxy --agent-id #{agent} --inject-id #{inject}\";"
        + "$psi.UseShellExecute = $false;"
        + "$psi.RedirectStandardError = $true;"
        + "$psi.RedirectStandardOutput = $true;"
        + "$psi.RedirectStandardInput = $true;"
        + "$proc = [System.Diagnostics.Process]::Start($psi);"
        + "$stdout = $proc.StandardOutput.ReadToEndAsync();"
        + "$stderr = $proc.StandardError.ReadToEndAsync();"
        + "$proc.WaitForExit();"
        + "\"[$(Get-Date)] Normal exit: $($proc.ExitCode)\" | Out-File $diagLog -Append;"
        + "exit $proc.ExitCode;");
  }
}
