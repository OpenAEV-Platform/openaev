package io.openaev.database.model;

import java.util.function.Supplier;
import lombok.Getter;

public enum PayloadType {
  COMMAND(Command.COMMAND_TYPE, Command::new),
  EXECUTABLE(Executable.EXECUTABLE_TYPE, Executable::new),
  FILE_DROP(FileDrop.FILE_DROP_TYPE, FileDrop::new),
  DNS_RESOLUTION(DnsResolution.DNS_RESOLUTION_TYPE, DnsResolution::new),
  NETWORK_TRAFFIC(NetworkTraffic.NETWORK_TRAFFIC_TYPE, NetworkTraffic::new);

  public final String key;
  @Getter public final Supplier<Payload> payloadSupplier;

  PayloadType(String key, Supplier<Payload> payloadSupplier) {
    this.key = key;
    this.payloadSupplier = payloadSupplier;
  }

  public static PayloadType fromString(String key) {
    for (PayloadType type : PayloadType.values()) {
      if (type.key.equalsIgnoreCase(key)) {
        return type;
      }
    }
    throw new IllegalArgumentException("No PayloadType found for key: " + key);
  }
}
