package io.openaev.utils.fixtures;

import io.openaev.executors.mde.model.MdeDevice;
import io.openaev.executors.mde.model.MdeDeviceGroup;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class MdeDeviceFixture {

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
    DateTimeFormatter formatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);
    device.setLastSeen(formatter.format(Instant.now()));
    return device;
  }

  public static MdeDeviceGroup createDefaultMdeDeviceGroup() {
    MdeDeviceGroup group = new MdeDeviceGroup();
    group.setId(42);
    group.setName("Test Device Group");
    group.setDescription("Test device group for OpenAEV");
    return group;
  }
}
