package io.openaev.utils.fixtures;

import io.openaev.executors.crowdstrike.model.CrowdStrikeDevice;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class CrowdstrikeDeviceFixture {

  public static CrowdStrikeDevice createDefaultCrowdStrikeDevice() {
    CrowdStrikeDevice crowdstrikeAgent = new CrowdStrikeDevice();
    crowdstrikeAgent.setDevice_id("externalRefCS");
    crowdstrikeAgent.setHostname("hostnameCS");
    crowdstrikeAgent.setPlatform_name("Windows");
    crowdstrikeAgent.setExternal_ip("1.1.1.1");
    crowdstrikeAgent.setLocal_ip("1.1.1.2");
    crowdstrikeAgent.setConnection_ip("1.1.1.3");
    crowdstrikeAgent.setMac_address("AA:AA:AA:AA:AA:AA");
    Instant now = Instant.now();
    DateTimeFormatter formatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneId.systemDefault());
    crowdstrikeAgent.setLast_seen(formatter.format(now));
    return crowdstrikeAgent;
  }
}
