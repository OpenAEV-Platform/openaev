package io.openaev.utils.fixtures;

import io.openaev.executors.mde.model.MdeDevice;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class MdeDeviceFixture {

  /** Same ISO-8601 UTC format the MDE machines inventory returns for {@code lastSeen}. */
  public static final DateTimeFormatter MDE_LAST_SEEN_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

  public static String formatLastSeen(Instant instant) {
    return MDE_LAST_SEEN_FORMATTER.format(instant);
  }

  public static MdeDevice createDefaultMdeDevice() {
    MdeDevice device = new MdeDevice();
    device.setId("a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2");
    device.setComputerDnsName("mde-test-host.example.com");
    device.setOsPlatform("Windows10");
    device.setOsArchitecture("64-bit");
    device.setLastIpAddress("192.168.1.10");
    device.setLastExternalIpAddress("203.0.113.10");
    device.setRbacGroupId("42");
    device.setRbacGroupName("Test Device Group");
    device.setHealthStatus("Active");
    device.setLastSeen(formatLastSeen(Instant.now()));
    return device;
  }

  /**
   * Default device with an explicit {@code healthStatus} and inventory {@code lastSeen}, used to
   * exercise the Advanced Hunting active-status resolution branches.
   */
  public static MdeDevice createMdeDevice(String healthStatus, Instant lastSeen) {
    MdeDevice device = createDefaultMdeDevice();
    device.setHealthStatus(healthStatus);
    device.setLastSeen(formatLastSeen(lastSeen));
    return device;
  }
}
