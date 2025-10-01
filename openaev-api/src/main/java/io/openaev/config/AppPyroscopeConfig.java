package io.openaev.config;

import io.openaev.telemetry.PyroscopePropertiesConfig;
import io.pyroscope.http.Format;
import io.pyroscope.javaagent.EventType;
import io.pyroscope.javaagent.PyroscopeAgent;
import io.pyroscope.javaagent.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "pyroscope.agent", name = "enabled")
public class AppPyroscopeConfig {

  public AppPyroscopeConfig(@Autowired PyroscopePropertiesConfig pyroscopePropertiesConfig) {
    PyroscopeAgent.start(
        new Config.Builder()
            .setApplicationName(pyroscopePropertiesConfig.getPyroscopeInstanceName())
            .setFormat(Format.JFR)
            .setServerAddress(pyroscopePropertiesConfig.getProfilerServerAddress())
            .setProfilingEvent(
                EventType.valueOf(pyroscopePropertiesConfig.getProfilerEventType().toUpperCase()))
            .setProfilingAlloc(pyroscopePropertiesConfig.getProfilerAllocSize())
            .setProfilingLock(pyroscopePropertiesConfig.getProfilerLockSize())
            .build());
  }
}
