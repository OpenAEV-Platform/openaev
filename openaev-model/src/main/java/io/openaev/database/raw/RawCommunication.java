package io.openaev.database.raw;

import java.time.Instant;
import java.util.Set;

public interface RawCommunication {

  public String getCommunication_id();

  public String getCommunication_message_id();

  public Instant getCommunication_received_at();

  public Instant getCommunication_sent_at();

  public String getCommunication_subject();

  public String getCommunication_inject();

  public Set<String> getCommunication_users();

  public boolean getCommunication_ack();

  public boolean getCommunication_animation();

  public String getCommunication_from();

  public String getCommunication_to();

  public String getCommunication_exercise();
}
