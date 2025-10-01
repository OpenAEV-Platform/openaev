package io.openaev.injectors.caldera;

import io.openaev.config.OpenAEVConfig;
import io.openaev.database.model.Endpoint;
import io.openaev.injectors.caldera.config.CalderaInjectorConfig;
import io.openaev.integrations.InjectorService;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CalderaInjector {

  private static final String CALDERA_INJECTOR_NAME = "Caldera";

  @Autowired
  public CalderaInjector(
      InjectorService injectorService,
      CalderaContract contract,
      CalderaInjectorConfig calderaInjectorConfig,
      OpenAEVConfig openAEVConfig) {
    Map<String, String> executorCommands = new HashMap<>();
    executorCommands.put(
        Endpoint.PLATFORM_TYPE.Windows.name() + "." + Endpoint.PLATFORM_ARCH.x86_64,
        "$x=\"#{location}\";$location=$x.Replace(\"\\oaev-agent-caldera.exe\", \"\");[Environment]::CurrentDirectory = $location;$filename=\"oaev-implant-caldera-#{inject}-agent-#{agent}.exe\";$server=\""
            + calderaInjectorConfig.getPublicUrl()
            + "\";$url=\""
            + openAEVConfig.getBaseUrl()
            + "/api/implant/caldera/windows/x86_64\";$wc=New-Object System.Net.WebClient;$data=$wc.DownloadData($url);[io.file]::WriteAllBytes($filename,$data) | Out-Null;Remove-NetFirewallRule -DisplayName \"Allow OpenAEV Inbound\";New-NetFirewallRule -DisplayName \"Allow OpenAEV Inbound\" -Direction Inbound -Program \"$location\\$filename\" -Action Allow | Out-Null;Remove-NetFirewallRule -DisplayName \"Allow OpenAEV Outbound\";New-NetFirewallRule -DisplayName \"Allow OpenAEV Outbound\" -Direction Outbound -Program \"$location\\$filename\" -Action Allow | Out-Null;Start-Process -FilePath \"$location\\$filename\" -ArgumentList \"-server $server -group red\" -WindowStyle hidden;");
    executorCommands.put(
        Endpoint.PLATFORM_TYPE.Linux.name() + "." + Endpoint.PLATFORM_ARCH.x86_64,
        "x=\"#{location}\";location=$(echo \"$x\" | sed \"s#/openaev-caldera-agent##\");filename=oaev-implant-caldera-#{inject}-agent-#{agent};server=\""
            + calderaInjectorConfig.getPublicUrl()
            + "\";curl -s -X GET "
            + openAEVConfig.getBaseUrl()
            + "/api/implant/caldera/linux/x86_64 > $location/$filename;chmod +x $location/$filename;$location/$filename -server $server -group red &");
    executorCommands.put(
        Endpoint.PLATFORM_TYPE.MacOS.name() + "." + Endpoint.PLATFORM_ARCH.x86_64,
        "x=\"#{location}\";location=$(echo \"$x\" | sed \"s#/openaev-caldera-agent##\");filename=oaev-implant-caldera-#{inject}-agent-#{agent};server=\""
            + calderaInjectorConfig.getPublicUrl()
            + "\";curl -s -X GET "
            + openAEVConfig.getBaseUrl()
            + "/api/implant/caldera/macos/x86_64 > $location/$filename;chmod +x $location/$filename;$location/$filename -server $server -group red &");
    executorCommands.put(
        Endpoint.PLATFORM_TYPE.MacOS.name() + "." + Endpoint.PLATFORM_ARCH.arm64,
        "x=\"#{location}\";location=$(echo \"$x\" | sed \"s#/openaev-caldera-agent##\");filename=oaev-implant-caldera-#{inject}-agent-#{agent};server=\""
            + calderaInjectorConfig.getPublicUrl()
            + "\";curl -s -X GET "
            + openAEVConfig.getBaseUrl()
            + "/api/implant/caldera/macos/arm64 > $location/$filename;chmod +x $location/$filename;$location/$filename -server $server -group red &");
    Map<String, String> executorClearCommands = new HashMap<>();
    executorClearCommands.put(
        Endpoint.PLATFORM_TYPE.Windows.name() + "." + Endpoint.PLATFORM_ARCH.x86_64,
        "$x=\"#{location}\";$location=$x.Replace(\"\\oaev-agent-caldera.exe\", \"\");[Environment]::CurrentDirectory = $location;cd \"$location\";Get-ChildItem -Recurse -Filter *implant* | Remove-Item");
    executorClearCommands.put(
        Endpoint.PLATFORM_TYPE.Linux.name() + "." + Endpoint.PLATFORM_ARCH.x86_64,
        "x=\"#{location}\";location=$(echo \"$x\" | sed \"s#/openaev-caldera-agent##\");cd \"$location\"; rm *implant*");
    executorClearCommands.put(
        Endpoint.PLATFORM_TYPE.MacOS.name() + "." + Endpoint.PLATFORM_ARCH.x86_64,
        "x=\"#{location}\";location=$(echo \"$x\" | sed \"s#/openaev-caldera-agent##\");cd \"$location\"; rm *implant*");
    executorClearCommands.put(
        Endpoint.PLATFORM_TYPE.MacOS.name() + "." + Endpoint.PLATFORM_ARCH.arm64,
        "x=\"#{location}\";location=$(echo \"$x\" | sed \"s#/openaev-caldera-agent##\");cd \"$location\"; rm *implant*");
    try {
      injectorService.register(
          calderaInjectorConfig.getId(),
          CALDERA_INJECTOR_NAME,
          contract,
          false,
          "simulation-implant",
          executorCommands,
          executorClearCommands,
          false);
    } catch (Exception e) {
      log.error(String.format("Error creating Caldera injector (%s)", e.getMessage()), e);
    }
  }
}
