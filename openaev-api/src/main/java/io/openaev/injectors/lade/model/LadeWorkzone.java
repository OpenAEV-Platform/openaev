package io.openaev.injectors.lade.model;

import java.util.HashMap;
import java.util.Map;

public class LadeWorkzone {
  private String id;

  private String name;

  private Map<String, String> hostsByName = new HashMap<>();

  private Map<String, String> hostsByIp = new HashMap<>();

  public LadeWorkzone(String id, String name) {
    this.id = id;
    this.name = name;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Map<String, String> getHostsByName() {
    return hostsByName;
  }

  public void setHostsByName(Map<String, String> hostsByName) {
    this.hostsByName = hostsByName;
  }

  public Map<String, String> getHostsByIp() {
    return hostsByIp;
  }

  public void setHostsByIp(Map<String, String> hostsByIp) {
    this.hostsByIp = hostsByIp;
  }
}
