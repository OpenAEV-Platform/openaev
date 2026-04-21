package io.openaev.api.xtmhub;

import io.openaev.database.model.TenantXtmHubRegistration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
public class XtmHubRegistrationMapper {

  public XtmHubRegistrationOutput toXtmHubRegistrationOutput(TenantXtmHubRegistration entity) {
    return XtmHubRegistrationOutput.builder()
        .id(entity.getId())
        .token(entity.getToken())
        .registrationDate(entity.getRegistrationDate())
        .registrationStatus(entity.getRegistrationStatus())
        .registrationUserId(entity.getRegistrationUserId())
        .registrationUserName(entity.getRegistrationUserName())
        .lastConnectivityCheck(entity.getLastConnectivityCheck())
        .build();
  }
}
