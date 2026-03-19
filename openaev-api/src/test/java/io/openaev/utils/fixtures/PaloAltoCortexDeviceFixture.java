package io.openaev.utils.fixtures;

import io.openaev.executors.paloaltocortex.model.PaloAltoCortexEndpoint;

public class PaloAltoCortexDeviceFixture {

  public static PaloAltoCortexEndpoint createDefaultPaloAltoCortexEndpoint() {
    PaloAltoCortexEndpoint endpoint = new PaloAltoCortexEndpoint();
    endpoint.setEndpoint_id("externalRefPaloAltoCortex");
    endpoint.setEndpoint_name("hostnamePaloAltoCortex");
    endpoint.setOs_type("agent_os_windows");
    endpoint.setIp(new String[] {"1.1.1.1"});
    endpoint.setMac_address(new String[] {"aa:aa:aa:aa:aa:aa"});
    endpoint.setPublic_ip("192.168.1.1");
    endpoint.setLast_seen(1769086767939L);
    return endpoint;
  }
}
