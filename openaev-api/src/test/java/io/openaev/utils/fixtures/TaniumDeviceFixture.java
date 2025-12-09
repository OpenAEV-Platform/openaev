package io.openaev.utils.fixtures;

import io.openaev.executors.tanium.model.NodeEndpoint;
import io.openaev.executors.tanium.model.Os;
import io.openaev.executors.tanium.model.Processor;
import io.openaev.executors.tanium.model.TaniumEndpoint;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class TaniumDeviceFixture {

  public static NodeEndpoint createDefaultTaniumEndpoint() {
    NodeEndpoint nodeEndpoint = new NodeEndpoint();
    TaniumEndpoint taniumEndpoint = new TaniumEndpoint();
    taniumEndpoint.setId("externalRefTanium");
    taniumEndpoint.setName("TANIUM DEVICE");
    Os os = new Os();
    os.setPlatform("Windows");
    taniumEndpoint.setOs(os);
    Processor processor = new Processor();
    processor.setArchitecture("x86_64");
    taniumEndpoint.setProcessor(processor);
    taniumEndpoint.setIpAddresses(new String[] {"1.1.1.1"});
    taniumEndpoint.setMacAddresses(new String[] {"AA:AA:AA:AA:AA:AA"});
    Instant now = Instant.now();
    DateTimeFormatter formatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneId.systemDefault());
    taniumEndpoint.setEidLastSeen(formatter.format(now));
    nodeEndpoint.setNode(taniumEndpoint);
    return nodeEndpoint;
  }
}
