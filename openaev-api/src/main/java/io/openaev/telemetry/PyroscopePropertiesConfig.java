package io.openaev.telemetry;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Data
public class PyroscopePropertiesConfig {

  @Value("${pyroscope.agent.enabled:#{false}}")
  private boolean pyroscopeEnabled;

  @Value("${pyroscope.agent.instance:#{'OpenAEV'}}")
  private String pyroscopeInstanceName;

  @Value("${pyroscope.profiler.event:#{'itimer'}}")
  private String profilerEventType;

  @Value("${pyroscope.profiler.alloc:#{''}}")
  private String profilerAllocSize;

  @Value("${pyroscope.profiler.lock:#{''}}")
  private String profilerLockSize;

  @Value("${pyroscope.server.address:#{'http://localhost:4040'}}")
  private String profilerServerAddress;
}
