package io.openaev.ocsf.schema.v180;

import com.fasterxml.jackson.databind.ObjectMapper;

public class OcsfConverter {

  private com.fasterxml.jackson.databind.ObjectMapper mapper = new ObjectMapper();

  public io.openaev.ocsf.schema.v180.classes.OcsfClassAccountChange toOcsfClassAccountChange(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassAccountChange.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassAdminGroupQuery toOcsfClassAdminGroupQuery(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassAdminGroupQuery.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassAirborneBroadcastActivity
      toOcsfClassAirborneBroadcastActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassAirborneBroadcastActivity.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassApiActivity toOcsfClassApiActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.classes.OcsfClassApiActivity.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassApplicationError toOcsfClassApplicationError(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassApplicationError.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassApplicationLifecycle
      toOcsfClassApplicationLifecycle(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassApplicationLifecycle.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassApplicationSecurityPostureFinding
      toOcsfClassApplicationSecurityPostureFinding(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassApplicationSecurityPostureFinding.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassAuthentication toOcsfClassAuthentication(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassAuthentication.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassAuthorizeSession toOcsfClassAuthorizeSession(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassAuthorizeSession.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassBaseEvent toOcsfClassBaseEvent(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.classes.OcsfClassBaseEvent.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassCloudResourcesInventoryInfo
      toOcsfClassCloudResourcesInventoryInfo(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassCloudResourcesInventoryInfo.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassComplianceFinding
      toOcsfClassComplianceFinding(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassComplianceFinding.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassConfigState toOcsfClassConfigState(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.classes.OcsfClassConfigState.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassDataSecurityFinding
      toOcsfClassDataSecurityFinding(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassDataSecurityFinding.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassDatastoreActivity
      toOcsfClassDatastoreActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassDatastoreActivity.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassDetectionFinding toOcsfClassDetectionFinding(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassDetectionFinding.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassDeviceConfigStateChange
      toOcsfClassDeviceConfigStateChange(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassDeviceConfigStateChange.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassDhcpActivity toOcsfClassDhcpActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassDhcpActivity.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassDnsActivity toOcsfClassDnsActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.classes.OcsfClassDnsActivity.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassDroneFlightsActivity
      toOcsfClassDroneFlightsActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassDroneFlightsActivity.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassEmailActivity toOcsfClassEmailActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassEmailActivity.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassEmailFileActivity
      toOcsfClassEmailFileActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassEmailFileActivity.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassEmailUrlActivity toOcsfClassEmailUrlActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassEmailUrlActivity.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassEntityManagement toOcsfClassEntityManagement(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassEntityManagement.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassEventLogActvity toOcsfClassEventLogActvity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassEventLogActvity.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassEvidenceInfo toOcsfClassEvidenceInfo(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassEvidenceInfo.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassFileActivity toOcsfClassFileActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassFileActivity.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassFileHosting toOcsfClassFileHosting(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.classes.OcsfClassFileHosting.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassFileQuery toOcsfClassFileQuery(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.classes.OcsfClassFileQuery.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassFileRemediationActivity
      toOcsfClassFileRemediationActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassFileRemediationActivity.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassFolderQuery toOcsfClassFolderQuery(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.classes.OcsfClassFolderQuery.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassFtpActivity toOcsfClassFtpActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.classes.OcsfClassFtpActivity.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassGroupManagement toOcsfClassGroupManagement(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassGroupManagement.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassHttpActivity toOcsfClassHttpActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassHttpActivity.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassIamAnalysisFinding
      toOcsfClassIamAnalysisFinding(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassIamAnalysisFinding.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassIncidentFinding toOcsfClassIncidentFinding(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassIncidentFinding.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassInventoryInfo toOcsfClassInventoryInfo(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassInventoryInfo.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassJobQuery toOcsfClassJobQuery(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.classes.OcsfClassJobQuery.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassKernelActivity toOcsfClassKernelActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassKernelActivity.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassKernelExtensionActivity
      toOcsfClassKernelExtensionActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassKernelExtensionActivity.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassKernelObjectQuery
      toOcsfClassKernelObjectQuery(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassKernelObjectQuery.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassMemoryActivity toOcsfClassMemoryActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassMemoryActivity.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassModuleActivity toOcsfClassModuleActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassModuleActivity.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassModuleQuery toOcsfClassModuleQuery(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.classes.OcsfClassModuleQuery.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassNetworkActivity toOcsfClassNetworkActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassNetworkActivity.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassNetworkConnectionQuery
      toOcsfClassNetworkConnectionQuery(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassNetworkConnectionQuery.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassNetworkFileActivity
      toOcsfClassNetworkFileActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassNetworkFileActivity.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassNetworkRemediationActivity
      toOcsfClassNetworkRemediationActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassNetworkRemediationActivity.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassNetworksQuery toOcsfClassNetworksQuery(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassNetworksQuery.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassNtpActivity toOcsfClassNtpActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.classes.OcsfClassNtpActivity.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassOsintInventoryInfo
      toOcsfClassOsintInventoryInfo(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassOsintInventoryInfo.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassPatchState toOcsfClassPatchState(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.classes.OcsfClassPatchState.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassPeripheralActivity
      toOcsfClassPeripheralActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassPeripheralActivity.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassPeripheralDeviceQuery
      toOcsfClassPeripheralDeviceQuery(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassPeripheralDeviceQuery.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassPrefetchQuery toOcsfClassPrefetchQuery(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassPrefetchQuery.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassProcessActivity toOcsfClassProcessActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassProcessActivity.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassProcessQuery toOcsfClassProcessQuery(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassProcessQuery.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassProcessRemediationActivity
      toOcsfClassProcessRemediationActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassProcessRemediationActivity.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassRdpActivity toOcsfClassRdpActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.classes.OcsfClassRdpActivity.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassRegistryKeyActivity
      toOcsfClassRegistryKeyActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassRegistryKeyActivity.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassRegistryKeyQuery toOcsfClassRegistryKeyQuery(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassRegistryKeyQuery.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassRegistryValueActivity
      toOcsfClassRegistryValueActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassRegistryValueActivity.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassRegistryValueQuery
      toOcsfClassRegistryValueQuery(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassRegistryValueQuery.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassRemediationActivity
      toOcsfClassRemediationActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassRemediationActivity.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassScanActivity toOcsfClassScanActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassScanActivity.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassScheduledJobActivity
      toOcsfClassScheduledJobActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassScheduledJobActivity.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassScriptActivity toOcsfClassScriptActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassScriptActivity.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassSecurityFinding toOcsfClassSecurityFinding(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassSecurityFinding.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassServiceQuery toOcsfClassServiceQuery(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassServiceQuery.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassSessionQuery toOcsfClassSessionQuery(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassSessionQuery.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassSmbActivity toOcsfClassSmbActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.classes.OcsfClassSmbActivity.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassSoftwareInfo toOcsfClassSoftwareInfo(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassSoftwareInfo.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassSshActivity toOcsfClassSshActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.classes.OcsfClassSshActivity.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassStartupItemQuery toOcsfClassStartupItemQuery(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassStartupItemQuery.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassTunnelActivity toOcsfClassTunnelActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassTunnelActivity.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassUserAccess toOcsfClassUserAccess(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.classes.OcsfClassUserAccess.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassUserInventory toOcsfClassUserInventory(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassUserInventory.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassUserQuery toOcsfClassUserQuery(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.classes.OcsfClassUserQuery.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassVulnerabilityFinding
      toOcsfClassVulnerabilityFinding(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassVulnerabilityFinding.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassWebResourceAccessActivity
      toOcsfClassWebResourceAccessActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassWebResourceAccessActivity.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassWebResourcesActivity
      toOcsfClassWebResourcesActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassWebResourcesActivity.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassWindowsResourceActivity
      toOcsfClassWindowsResourceActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassWindowsResourceActivity.class);
  }

  public io.openaev.ocsf.schema.v180.classes.OcsfClassWindowsServiceActivity
      toOcsfClassWindowsServiceActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.classes.OcsfClassWindowsServiceActivity.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectAccessAnalysisResult
      toOcsfObjectAccessAnalysisResult(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectAccessAnalysisResult.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectAccount toOcsfObjectAccount(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectAccount.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectActor toOcsfObjectActor(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectActor.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectAdditionalRestriction
      toOcsfObjectAdditionalRestriction(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectAdditionalRestriction.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectAdvisory toOcsfObjectAdvisory(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectAdvisory.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectAffectedCode toOcsfObjectAffectedCode(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectAffectedCode.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectAffectedPackage toOcsfObjectAffectedPackage(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectAffectedPackage.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectAgent toOcsfObjectAgent(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectAgent.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectAiModel toOcsfObjectAiModel(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectAiModel.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectAircraft toOcsfObjectAircraft(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectAircraft.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectAnalysisTarget toOcsfObjectAnalysisTarget(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectAnalysisTarget.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectAnalytic toOcsfObjectAnalytic(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectAnalytic.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectAnomaly toOcsfObjectAnomaly(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectAnomaly.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectAnomalyAnalysis toOcsfObjectAnomalyAnalysis(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectAnomalyAnalysis.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectApi toOcsfObjectApi(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectApi.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectApplication toOcsfObjectApplication(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectApplication.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectAssessment toOcsfObjectAssessment(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectAssessment.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectAttack toOcsfObjectAttack(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectAttack.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectAuthFactor toOcsfObjectAuthFactor(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectAuthFactor.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectAuthenticationToken
      toOcsfObjectAuthenticationToken(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectAuthenticationToken.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectAuthorization toOcsfObjectAuthorization(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectAuthorization.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectAutonomousSystem
      toOcsfObjectAutonomousSystem(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectAutonomousSystem.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectBaseline toOcsfObjectBaseline(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectBaseline.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectCampaign toOcsfObjectCampaign(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectCampaign.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectCertificate toOcsfObjectCertificate(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectCertificate.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectCheck toOcsfObjectCheck(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectCheck.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectCisBenchmark toOcsfObjectCisBenchmark(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectCisBenchmark.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectCisBenchmarkResult
      toOcsfObjectCisBenchmarkResult(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectCisBenchmarkResult.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectCisControl toOcsfObjectCisControl(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectCisControl.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectCisCsc toOcsfObjectCisCsc(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectCisCsc.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectClassifierDetails
      toOcsfObjectClassifierDetails(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectClassifierDetails.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectCloud toOcsfObjectCloud(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectCloud.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectCompliance toOcsfObjectCompliance(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectCompliance.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectContainer toOcsfObjectContainer(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectContainer.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectCve toOcsfObjectCve(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectCve.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectCvss toOcsfObjectCvss(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectCvss.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectCwe toOcsfObjectCwe(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectCwe.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectD3fTactic toOcsfObjectD3fTactic(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectD3fTactic.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectD3fTechnique toOcsfObjectD3fTechnique(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectD3fTechnique.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectD3fend toOcsfObjectD3fend(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectD3fend.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectDataClassification
      toOcsfObjectDataClassification(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectDataClassification.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectDataSecurity toOcsfObjectDataSecurity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectDataSecurity.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectDatabase toOcsfObjectDatabase(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectDatabase.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectDatabucket toOcsfObjectDatabucket(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectDatabucket.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectDceRpc toOcsfObjectDceRpc(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectDceRpc.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectDevice toOcsfObjectDevice(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectDevice.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectDeviceHwInfo toOcsfObjectDeviceHwInfo(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectDeviceHwInfo.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectDigitalSignature
      toOcsfObjectDigitalSignature(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectDigitalSignature.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectDiscoveryDetails
      toOcsfObjectDiscoveryDetails(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectDiscoveryDetails.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectDisplay toOcsfObjectDisplay(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectDisplay.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectDnsAnswer toOcsfObjectDnsAnswer(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectDnsAnswer.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectDnsQuery toOcsfObjectDnsQuery(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectDnsQuery.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectDomainContact toOcsfObjectDomainContact(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectDomainContact.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectEdge toOcsfObjectEdge(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectEdge.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectEmail toOcsfObjectEmail(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectEmail.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectEmailAuth toOcsfObjectEmailAuth(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectEmailAuth.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectEncryptionDetails
      toOcsfObjectEncryptionDetails(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectEncryptionDetails.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectEndpoint toOcsfObjectEndpoint(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectEndpoint.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectEndpointConnection
      toOcsfObjectEndpointConnection(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectEndpointConnection.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectEnrichment toOcsfObjectEnrichment(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectEnrichment.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectEnvironmentVariable
      toOcsfObjectEnvironmentVariable(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectEnvironmentVariable.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectEpss toOcsfObjectEpss(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectEpss.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectEvidences toOcsfObjectEvidences(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectEvidences.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectExtension toOcsfObjectExtension(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectExtension.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectFeature toOcsfObjectFeature(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectFeature.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectFile toOcsfObjectFile(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectFile.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectFinding toOcsfObjectFinding(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectFinding.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectFindingInfo toOcsfObjectFindingInfo(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectFindingInfo.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectFingerprint toOcsfObjectFingerprint(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectFingerprint.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectFirewallRule toOcsfObjectFirewallRule(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectFirewallRule.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectFunctionInvocation
      toOcsfObjectFunctionInvocation(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectFunctionInvocation.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectGpuInfo toOcsfObjectGpuInfo(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectGpuInfo.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectGraph toOcsfObjectGraph(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectGraph.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectGroup toOcsfObjectGroup(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectGroup.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectHassh toOcsfObjectHassh(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectHassh.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectHttpCookie toOcsfObjectHttpCookie(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectHttpCookie.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectHttpHeader toOcsfObjectHttpHeader(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectHttpHeader.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectHttpRequest toOcsfObjectHttpRequest(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectHttpRequest.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectHttpResponse toOcsfObjectHttpResponse(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectHttpResponse.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectIdentityActivityMetrics
      toOcsfObjectIdentityActivityMetrics(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectIdentityActivityMetrics.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectIdp toOcsfObjectIdp(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectIdp.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectImage toOcsfObjectImage(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectImage.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectJa4Fingerprint toOcsfObjectJa4Fingerprint(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectJa4Fingerprint.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectJob toOcsfObjectJob(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectJob.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectKbArticle toOcsfObjectKbArticle(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectKbArticle.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectKernel toOcsfObjectKernel(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectKernel.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectKernelDriver toOcsfObjectKernelDriver(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectKernelDriver.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectKeyValueObject toOcsfObjectKeyValueObject(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectKeyValueObject.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectKeyboardInfo toOcsfObjectKeyboardInfo(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectKeyboardInfo.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectKillChainPhase toOcsfObjectKillChainPhase(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectKillChainPhase.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectLdapPerson toOcsfObjectLdapPerson(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectLdapPerson.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectLoadBalancer toOcsfObjectLoadBalancer(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectLoadBalancer.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectLocation toOcsfObjectLocation(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectLocation.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectLogger toOcsfObjectLogger(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectLogger.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectLongString toOcsfObjectLongString(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectLongString.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectMalware toOcsfObjectMalware(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectMalware.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectMalwareScanInfo toOcsfObjectMalwareScanInfo(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectMalwareScanInfo.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectManagedEntity toOcsfObjectManagedEntity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectManagedEntity.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectMessageContext toOcsfObjectMessageContext(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectMessageContext.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectMetadata toOcsfObjectMetadata(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectMetadata.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectMetric toOcsfObjectMetric(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectMetric.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectMitigation toOcsfObjectMitigation(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectMitigation.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectModule toOcsfObjectModule(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectModule.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectNetworkConnectionInfo
      toOcsfObjectNetworkConnectionInfo(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectNetworkConnectionInfo.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectNetworkEndpoint toOcsfObjectNetworkEndpoint(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectNetworkEndpoint.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectNetworkInterface
      toOcsfObjectNetworkInterface(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectNetworkInterface.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectNetworkProxy toOcsfObjectNetworkProxy(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectNetworkProxy.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectNetworkTraffic toOcsfObjectNetworkTraffic(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectNetworkTraffic.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectNode toOcsfObjectNode(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectNode.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectObject toOcsfObjectObject(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectObject.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectObservable toOcsfObjectObservable(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectObservable.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectObservation toOcsfObjectObservation(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectObservation.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectOccurrenceDetails
      toOcsfObjectOccurrenceDetails(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectOccurrenceDetails.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectOrganization toOcsfObjectOrganization(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectOrganization.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectOs toOcsfObjectOs(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectOs.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectOsint toOcsfObjectOsint(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectOsint.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectPackage toOcsfObjectPackage(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectPackage.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectPacket toOcsfObjectPacket(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectPacket.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectParameter toOcsfObjectParameter(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectParameter.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectPeripheralDevice
      toOcsfObjectPeripheralDevice(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectPeripheralDevice.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectPermissionAnalysisResult
      toOcsfObjectPermissionAnalysisResult(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectPermissionAnalysisResult.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectPolicy toOcsfObjectPolicy(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectPolicy.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectPortInfo toOcsfObjectPortInfo(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectPortInfo.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectPrivilegeAttackInfo
      toOcsfObjectPrivilegeAttackInfo(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectPrivilegeAttackInfo.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectPrivilegeInfo toOcsfObjectPrivilegeInfo(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectPrivilegeInfo.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectProcess toOcsfObjectProcess(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectProcess.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectProcessEntity toOcsfObjectProcessEntity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectProcessEntity.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectProduct toOcsfObjectProduct(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectProduct.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectProgrammaticCredential
      toOcsfObjectProgrammaticCredential(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectProgrammaticCredential.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectQueryEvidence toOcsfObjectQueryEvidence(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectQueryEvidence.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectQueryInfo toOcsfObjectQueryInfo(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectQueryInfo.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectRegKey toOcsfObjectRegKey(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectRegKey.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectRegValue toOcsfObjectRegValue(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectRegValue.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectRelatedEvent toOcsfObjectRelatedEvent(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectRelatedEvent.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectRemediation toOcsfObjectRemediation(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectRemediation.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectReporter toOcsfObjectReporter(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectReporter.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectReputation toOcsfObjectReputation(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectReputation.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectRequest toOcsfObjectRequest(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectRequest.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectResourceDetails toOcsfObjectResourceDetails(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectResourceDetails.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectResponse toOcsfObjectResponse(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectResponse.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectRpcInterface toOcsfObjectRpcInterface(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectRpcInterface.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectRule toOcsfObjectRule(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectRule.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectSan toOcsfObjectSan(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectSan.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectSbom toOcsfObjectSbom(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectSbom.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectScan toOcsfObjectScan(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectScan.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectScim toOcsfObjectScim(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectScim.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectScript toOcsfObjectScript(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectScript.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectSecurityState toOcsfObjectSecurityState(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectSecurityState.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectService toOcsfObjectService(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectService.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectServicePrivilegeAnalysis
      toOcsfObjectServicePrivilegeAnalysis(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectServicePrivilegeAnalysis.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectSession toOcsfObjectSession(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectSession.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectSoftwareComponent
      toOcsfObjectSoftwareComponent(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectSoftwareComponent.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectSpan toOcsfObjectSpan(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectSpan.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectSso toOcsfObjectSso(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectSso.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectStartupItem toOcsfObjectStartupItem(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectStartupItem.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectSubTechnique toOcsfObjectSubTechnique(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectSubTechnique.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectTable toOcsfObjectTable(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectTable.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectTactic toOcsfObjectTactic(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectTactic.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectTechnique toOcsfObjectTechnique(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectTechnique.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectThreatActor toOcsfObjectThreatActor(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectThreatActor.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectTicket toOcsfObjectTicket(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectTicket.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectTimespan toOcsfObjectTimespan(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectTimespan.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectTls toOcsfObjectTls(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectTls.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectTlsExtension toOcsfObjectTlsExtension(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectTlsExtension.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectToken toOcsfObjectToken(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectToken.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectTrace toOcsfObjectTrace(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectTrace.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectTrait toOcsfObjectTrait(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectTrait.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectTransformationInfo
      toOcsfObjectTransformationInfo(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectTransformationInfo.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectUnmannedAerialSystem
      toOcsfObjectUnmannedAerialSystem(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectUnmannedAerialSystem.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectUnmannedSystemOperatingArea
      toOcsfObjectUnmannedSystemOperatingArea(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectUnmannedSystemOperatingArea.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectUrl toOcsfObjectUrl(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectUrl.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectUser toOcsfObjectUser(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectUser.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectVendorAttributes
      toOcsfObjectVendorAttributes(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectVendorAttributes.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectVulnerability toOcsfObjectVulnerability(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectVulnerability.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectWebResource toOcsfObjectWebResource(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectWebResource.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectWhois toOcsfObjectWhois(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectWhois.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectWinResource toOcsfObjectWinResource(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v180.objects.OcsfObjectWinResource.class);
  }

  public io.openaev.ocsf.schema.v180.objects.OcsfObjectWinService toOcsfObjectWinService(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v180.objects.OcsfObjectWinService.class);
  }
}
