package io.openbas.rest.detection_remediation.form;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DetectionRemediationHealthWebService {
  String status;
  String timestamp;
  String service;
  String version;
  String up_time;
}
