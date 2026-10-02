package io.openaev.ocsf.schema.v190;

public class OcsfFilter {

  private OcsfConverter converter = new OcsfConverter();

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassAccountChange>
      filterOcsfClassAccountChanges(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassAccountChange> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "3001".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassAccountChange(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassAdminGroupQuery>
      filterOcsfClassAdminGroupQueries(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassAdminGroupQuery> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "5009".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassAdminGroupQuery(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassAirborneBroadcastActivity>
      filterOcsfClassAirborneBroadcastActivities(
          com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassAirborneBroadcastActivity>
        selected = new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "8002".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassAirborneBroadcastActivity(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassApiActivity>
      filterOcsfClassApiActivities(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassApiActivity> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "6003".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassApiActivity(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassApplicationError>
      filterOcsfClassApplicationErrors(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassApplicationError> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "6008".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassApplicationError(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassApplicationLifecycle>
      filterOcsfClassApplicationLifecycles(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassApplicationLifecycle> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "6002".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassApplicationLifecycle(node));
      }
    }
    return selected;
  }

  public java.util.List<
          io.openaev.ocsf.schema.v190.classes.OcsfClassApplicationSecurityPostureFinding>
      filterOcsfClassApplicationSecurityPostureFindings(
          com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassApplicationSecurityPostureFinding>
        selected = new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "2007".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassApplicationSecurityPostureFinding(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassAuthentication>
      filterOcsfClassAuthentications(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassAuthentication> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "3002".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassAuthentication(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassAuthorizeSession>
      filterOcsfClassAuthorizeSessions(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassAuthorizeSession> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "3003".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassAuthorizeSession(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassBaseEvent>
      filterOcsfClassBaseEvents(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassBaseEvent> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "0".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassBaseEvent(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassClipboardActivity>
      filterOcsfClassClipboardActivities(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassClipboardActivity> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "1012".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassClipboardActivity(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassCloudResourcesInventoryInfo>
      filterOcsfClassCloudResourcesInventoryInfos(
          com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassCloudResourcesInventoryInfo>
        selected = new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "5023".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassCloudResourcesInventoryInfo(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassComplianceFinding>
      filterOcsfClassComplianceFindings(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassComplianceFinding> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "2003".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassComplianceFinding(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassConfigState>
      filterOcsfClassConfigStates(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassConfigState> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "5002".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassConfigState(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassDataSecurityFinding>
      filterOcsfClassDataSecurityFindings(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassDataSecurityFinding> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "2006".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassDataSecurityFinding(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassDatastoreActivity>
      filterOcsfClassDatastoreActivities(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassDatastoreActivity> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "6005".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassDatastoreActivity(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassDetectionFinding>
      filterOcsfClassDetectionFindings(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassDetectionFinding> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "2004".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassDetectionFinding(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassDeviceConfigStateChange>
      filterOcsfClassDeviceConfigStateChanges(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassDeviceConfigStateChange> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "5019".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassDeviceConfigStateChange(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassDevicePowerStateActivity>
      filterOcsfClassDevicePowerStateActivities(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassDevicePowerStateActivity> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "1011".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassDevicePowerStateActivity(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassDhcpActivity>
      filterOcsfClassDhcpActivities(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassDhcpActivity> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "4004".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassDhcpActivity(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassDnsActivity>
      filterOcsfClassDnsActivities(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassDnsActivity> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "4003".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassDnsActivity(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassDroneFlightsActivity>
      filterOcsfClassDroneFlightsActivities(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassDroneFlightsActivity> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "8001".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassDroneFlightsActivity(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassEmailActivity>
      filterOcsfClassEmailActivities(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassEmailActivity> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "4009".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassEmailActivity(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassEmailFileActivity>
      filterOcsfClassEmailFileActivities(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassEmailFileActivity> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "4011".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassEmailFileActivity(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassEmailUrlActivity>
      filterOcsfClassEmailUrlActivities(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassEmailUrlActivity> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "4012".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassEmailUrlActivity(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassEntityManagement>
      filterOcsfClassEntityManagements(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassEntityManagement> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "3004".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassEntityManagement(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassEventLogActvity>
      filterOcsfClassEventLogActvities(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassEventLogActvity> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "1008".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassEventLogActvity(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassEvidenceInfo>
      filterOcsfClassEvidenceInfos(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassEvidenceInfo> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "5040".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassEvidenceInfo(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassFileActivity>
      filterOcsfClassFileActivities(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassFileActivity> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "1001".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassFileActivity(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassFileHosting>
      filterOcsfClassFileHostings(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassFileHosting> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "6006".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassFileHosting(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassFileQuery>
      filterOcsfClassFileQueries(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassFileQuery> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "5007".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassFileQuery(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassFileRemediationActivity>
      filterOcsfClassFileRemediationActivities(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassFileRemediationActivity> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "7002".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassFileRemediationActivity(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassFolderQuery>
      filterOcsfClassFolderQueries(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassFolderQuery> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "5008".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassFolderQuery(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassFtpActivity>
      filterOcsfClassFtpActivities(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassFtpActivity> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "4008".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassFtpActivity(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassGroupManagement>
      filterOcsfClassGroupManagements(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassGroupManagement> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "3006".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassGroupManagement(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassHttpActivity>
      filterOcsfClassHttpActivities(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassHttpActivity> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "4002".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassHttpActivity(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassIamAnalysisFinding>
      filterOcsfClassIamAnalysisFindings(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassIamAnalysisFinding> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "2008".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassIamAnalysisFinding(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassIncidentFinding>
      filterOcsfClassIncidentFindings(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassIncidentFinding> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "2005".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassIncidentFinding(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassInventoryInfo>
      filterOcsfClassInventoryInfos(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassInventoryInfo> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "5001".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassInventoryInfo(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassJobQuery>
      filterOcsfClassJobQueries(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassJobQuery> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "5010".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassJobQuery(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassKernelActivity>
      filterOcsfClassKernelActivities(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassKernelActivity> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "1003".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassKernelActivity(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassKernelExtensionActivity>
      filterOcsfClassKernelExtensionActivities(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassKernelExtensionActivity> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "1002".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassKernelExtensionActivity(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassKernelObjectQuery>
      filterOcsfClassKernelObjectQueries(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassKernelObjectQuery> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "5006".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassKernelObjectQuery(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassMemoryActivity>
      filterOcsfClassMemoryActivities(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassMemoryActivity> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "1004".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassMemoryActivity(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassModuleActivity>
      filterOcsfClassModuleActivities(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassModuleActivity> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "1005".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassModuleActivity(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassModuleQuery>
      filterOcsfClassModuleQueries(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassModuleQuery> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "5011".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassModuleQuery(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassNetworkActivity>
      filterOcsfClassNetworkActivities(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassNetworkActivity> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "4001".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassNetworkActivity(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassNetworkConnectionQuery>
      filterOcsfClassNetworkConnectionQueries(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassNetworkConnectionQuery> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "5012".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassNetworkConnectionQuery(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassNetworkFileActivity>
      filterOcsfClassNetworkFileActivities(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassNetworkFileActivity> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "4010".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassNetworkFileActivity(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassNetworkRemediationActivity>
      filterOcsfClassNetworkRemediationActivities(
          com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassNetworkRemediationActivity>
        selected = new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "7004".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassNetworkRemediationActivity(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassNetworksQuery>
      filterOcsfClassNetworksQueries(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassNetworksQuery> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "5013".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassNetworksQuery(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassNtpActivity>
      filterOcsfClassNtpActivities(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassNtpActivity> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "4013".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassNtpActivity(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassOsintInventoryInfo>
      filterOcsfClassOsintInventoryInfos(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassOsintInventoryInfo> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "5021".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassOsintInventoryInfo(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassPatchState>
      filterOcsfClassPatchStates(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassPatchState> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "5004".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassPatchState(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassPeripheralActivity>
      filterOcsfClassPeripheralActivities(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassPeripheralActivity> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "1010".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassPeripheralActivity(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassPeripheralDeviceQuery>
      filterOcsfClassPeripheralDeviceQueries(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassPeripheralDeviceQuery> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "5014".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassPeripheralDeviceQuery(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassPrefetchQuery>
      filterOcsfClassPrefetchQueries(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassPrefetchQuery> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "205019".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassPrefetchQuery(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassProcessActivity>
      filterOcsfClassProcessActivities(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassProcessActivity> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "1007".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassProcessActivity(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassProcessQuery>
      filterOcsfClassProcessQueries(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassProcessQuery> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "5015".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassProcessQuery(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassProcessRemediationActivity>
      filterOcsfClassProcessRemediationActivities(
          com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassProcessRemediationActivity>
        selected = new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "7003".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassProcessRemediationActivity(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassRdpActivity>
      filterOcsfClassRdpActivities(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassRdpActivity> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "4005".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassRdpActivity(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassRegistryKeyActivity>
      filterOcsfClassRegistryKeyActivities(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassRegistryKeyActivity> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "201001".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassRegistryKeyActivity(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassRegistryKeyQuery>
      filterOcsfClassRegistryKeyQueries(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassRegistryKeyQuery> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "205004".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassRegistryKeyQuery(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassRegistryValueActivity>
      filterOcsfClassRegistryValueActivities(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassRegistryValueActivity> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "201002".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassRegistryValueActivity(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassRegistryValueQuery>
      filterOcsfClassRegistryValueQueries(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassRegistryValueQuery> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "205005".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassRegistryValueQuery(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassRemediationActivity>
      filterOcsfClassRemediationActivities(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassRemediationActivity> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "7001".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassRemediationActivity(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassRoleManagement>
      filterOcsfClassRoleManagements(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassRoleManagement> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "3008".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassRoleManagement(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassScanActivity>
      filterOcsfClassScanActivities(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassScanActivity> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "6007".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassScanActivity(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassScheduledJobActivity>
      filterOcsfClassScheduledJobActivities(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassScheduledJobActivity> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "1006".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassScheduledJobActivity(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassScriptActivity>
      filterOcsfClassScriptActivities(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassScriptActivity> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "1009".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassScriptActivity(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassSecurityFinding>
      filterOcsfClassSecurityFindings(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassSecurityFinding> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "2001".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassSecurityFinding(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassServiceQuery>
      filterOcsfClassServiceQueries(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassServiceQuery> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "5016".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassServiceQuery(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassSessionQuery>
      filterOcsfClassSessionQueries(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassSessionQuery> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "5017".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassSessionQuery(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassSmbActivity>
      filterOcsfClassSmbActivities(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassSmbActivity> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "4006".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassSmbActivity(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassSoftwareInfo>
      filterOcsfClassSoftwareInfos(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassSoftwareInfo> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "5020".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassSoftwareInfo(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassSshActivity>
      filterOcsfClassSshActivities(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassSshActivity> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "4007".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassSshActivity(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassStartupItemQuery>
      filterOcsfClassStartupItemQueries(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassStartupItemQuery> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "5022".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassStartupItemQuery(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassTunnelActivity>
      filterOcsfClassTunnelActivities(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassTunnelActivity> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "4014".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassTunnelActivity(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassUserAccess>
      filterOcsfClassUserAccesss(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassUserAccess> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "3005".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassUserAccess(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassUserInventory>
      filterOcsfClassUserInventories(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassUserInventory> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "5003".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassUserInventory(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassUserManagement>
      filterOcsfClassUserManagements(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassUserManagement> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "3007".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassUserManagement(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassUserQuery>
      filterOcsfClassUserQueries(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassUserQuery> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "5018".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassUserQuery(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassVulnerabilityFinding>
      filterOcsfClassVulnerabilityFindings(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassVulnerabilityFinding> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "2002".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassVulnerabilityFinding(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassWebResourceAccessActivity>
      filterOcsfClassWebResourceAccessActivities(
          com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassWebResourceAccessActivity>
        selected = new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "6004".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassWebResourceAccessActivity(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassWebResourcesActivity>
      filterOcsfClassWebResourcesActivities(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassWebResourcesActivity> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "6001".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassWebResourcesActivity(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassWindowsResourceActivity>
      filterOcsfClassWindowsResourceActivities(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassWindowsResourceActivity> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "201003".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassWindowsResourceActivity(node));
      }
    }
    return selected;
  }

  public java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassWindowsServiceActivity>
      filterOcsfClassWindowsServiceActivities(com.fasterxml.jackson.databind.node.ArrayNode nodes)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    java.util.List<io.openaev.ocsf.schema.v190.classes.OcsfClassWindowsServiceActivity> selected =
        new java.util.ArrayList<>();
    for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = nodes.elements();
        it.hasNext(); ) {
      com.fasterxml.jackson.databind.JsonNode node = it.next();
      if (node.has("class_uid") && "201004".equals(node.get("class_uid").asText())) {
        selected.add(converter.toOcsfClassWindowsServiceActivity(node));
      }
    }
    return selected;
  }
}
