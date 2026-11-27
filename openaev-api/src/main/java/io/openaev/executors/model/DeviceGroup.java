package io.openaev.executors.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

/**
 * A group of devices in the EDR, synced as an asset group.
 *
 * @param description {@code null} keeps the description already on the asset group
 */
@Slf4j
public record DeviceGroup(String externalReference, String name, String description) {

  /** Reads each group in turn; a group whose read fails is logged and skipped. */
  public static List<RemoteDevice> readEach(
      Collection<String> groupIds, Function<String, List<RemoteDevice>> read) {
    List<RemoteDevice> devices = new ArrayList<>();
    for (String groupId : groupIds) {
      try {
        devices.addAll(read.apply(groupId));
      } catch (RuntimeException e) {
        log.error("Skipping device group {}: {}", groupId, e.getMessage(), e);
      }
    }
    return devices;
  }
}
