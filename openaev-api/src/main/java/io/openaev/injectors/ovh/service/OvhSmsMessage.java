package io.openaev.injectors.ovh.service;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OvhSmsMessage {

  private final List<String> receivers;
  private final String message;
  private String charset = "UTF-8";
  private String coding = "8bit";

  private String priority = "high";

  private String sender;

  private boolean senderForResponse = false;

  private boolean noStopClause = true;

  public OvhSmsMessage(List<String> receivers, String message, String sender) {
    this.receivers = receivers;
    this.message = message;
    this.sender = sender;
  }
}
