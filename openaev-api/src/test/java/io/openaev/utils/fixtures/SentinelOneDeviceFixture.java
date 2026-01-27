package io.openaev.utils.fixtures;

import io.openaev.executors.sentinelone.model.SentinelOneAgent;
import io.openaev.executors.sentinelone.model.SentinelOneNetwork;
import java.time.Instant;
import java.util.List;

public class SentinelOneDeviceFixture {

  public static SentinelOneAgent createDefaultSentinelOneAgent() {
    SentinelOneAgent sentinelOneAgent = new SentinelOneAgent();
    sentinelOneAgent.setUuid("externalRefSentinelOne");
    sentinelOneAgent.setComputerName("hostnameSentinelOne");
    sentinelOneAgent.setOsType("Windows");
    sentinelOneAgent.setOsArch("64 bit");
    sentinelOneAgent.setExternalIp("1.1.1.1");
    SentinelOneNetwork network = new SentinelOneNetwork();
    network.setPhysical("AA:AA:AA:AA:AA:AA");
    network.setInet(List.of("1.1.1.2"));
    sentinelOneAgent.setNetworkInterfaces(List.of(network));
    Instant now = Instant.now();
    sentinelOneAgent.setLastActiveDate(now.toString());
    sentinelOneAgent.setAccountId("accountId");
    sentinelOneAgent.setAccountName("accountName");
    sentinelOneAgent.setSiteId("siteId");
    sentinelOneAgent.setSiteName("siteName");
    sentinelOneAgent.setGroupId("groupId");
    sentinelOneAgent.setGroupName("groupName");
    return sentinelOneAgent;
  }
}
