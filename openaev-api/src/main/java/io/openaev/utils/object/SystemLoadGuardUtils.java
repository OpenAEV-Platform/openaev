package io.openaev.utils.object;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import org.springframework.stereotype.Component;

@Component
public class SystemLoadGuardUtils {

  private final MemoryMXBean memoryMxBean = ManagementFactory.getMemoryMXBean();

  public boolean isHeapUsageHigh(double maxHeapUsageRatio) {
    MemoryUsage heap = memoryMxBean.getHeapMemoryUsage();
    long max = heap.getMax();
    if (max <= 0) {
      return false;
    }
    double ratio = (double) heap.getUsed() / max;
    return ratio >= maxHeapUsageRatio;
  }

  public boolean isProcessCpuLoadHigh(double maxProcessCpuLoad) {
    java.lang.management.OperatingSystemMXBean osBean =
        ManagementFactory.getOperatingSystemMXBean();
    if (!(osBean instanceof OperatingSystemMXBean sunOsBean)) {
      return false;
    }
    double cpuLoad = sunOsBean.getProcessCpuLoad();
    return cpuLoad >= 0 && cpuLoad >= maxProcessCpuLoad;
  }
}
