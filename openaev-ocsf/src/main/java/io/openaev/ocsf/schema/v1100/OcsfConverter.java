package io.openaev.ocsf.schema.v1100;

import com.fasterxml.jackson.databind.ObjectMapper;

public class OcsfConverter {

  private com.fasterxml.jackson.databind.ObjectMapper mapper =
      new ObjectMapper()
          .configure(
              com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
              false);

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassAccountChange toOcsfClassAccountChange(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassAccountChange.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassAdminGroupQuery toOcsfClassAdminGroupQuery(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassAdminGroupQuery.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassAirborneBroadcastActivity
      toOcsfClassAirborneBroadcastActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassAirborneBroadcastActivity.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassApiActivity toOcsfClassApiActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassApiActivity.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassApplicationError toOcsfClassApplicationError(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassApplicationError.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassApplicationLifecycle
      toOcsfClassApplicationLifecycle(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassApplicationLifecycle.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassApplicationSecurityPostureFinding
      toOcsfClassApplicationSecurityPostureFinding(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node,
        io.openaev.ocsf.schema.v1100.classes.OcsfClassApplicationSecurityPostureFinding.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassAuthentication toOcsfClassAuthentication(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassAuthentication.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassAuthorizeSession toOcsfClassAuthorizeSession(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassAuthorizeSession.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassBaseEvent toOcsfClassBaseEvent(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.classes.OcsfClassBaseEvent.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassClipboardActivity
      toOcsfClassClipboardActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassClipboardActivity.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassCloudResourcesInventoryInfo
      toOcsfClassCloudResourcesInventoryInfo(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassCloudResourcesInventoryInfo.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassComplianceFinding
      toOcsfClassComplianceFinding(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassComplianceFinding.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassConfigState toOcsfClassConfigState(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassConfigState.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassDataSecurityFinding
      toOcsfClassDataSecurityFinding(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassDataSecurityFinding.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassDatastoreActivity
      toOcsfClassDatastoreActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassDatastoreActivity.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassDetectionFinding toOcsfClassDetectionFinding(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassDetectionFinding.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassDeviceConfigStateChange
      toOcsfClassDeviceConfigStateChange(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassDeviceConfigStateChange.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassDevicePowerStateActivity
      toOcsfClassDevicePowerStateActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassDevicePowerStateActivity.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassDhcpActivity toOcsfClassDhcpActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassDhcpActivity.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassDnsActivity toOcsfClassDnsActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassDnsActivity.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassDnsZoneTransferActivity
      toOcsfClassDnsZoneTransferActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassDnsZoneTransferActivity.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassDroneFlightsActivity
      toOcsfClassDroneFlightsActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassDroneFlightsActivity.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassEmailActivity toOcsfClassEmailActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassEmailActivity.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassEmailFileActivity
      toOcsfClassEmailFileActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassEmailFileActivity.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassEmailUrlActivity toOcsfClassEmailUrlActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassEmailUrlActivity.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassEntityManagement toOcsfClassEntityManagement(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassEntityManagement.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassEventLogActvity toOcsfClassEventLogActvity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassEventLogActvity.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassEvidenceInfo toOcsfClassEvidenceInfo(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassEvidenceInfo.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassFileActivity toOcsfClassFileActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassFileActivity.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassFileHosting toOcsfClassFileHosting(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassFileHosting.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassFileQuery toOcsfClassFileQuery(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.classes.OcsfClassFileQuery.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassFileRemediationActivity
      toOcsfClassFileRemediationActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassFileRemediationActivity.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassFolderQuery toOcsfClassFolderQuery(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassFolderQuery.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassFtpActivity toOcsfClassFtpActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassFtpActivity.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassGroupManagement toOcsfClassGroupManagement(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassGroupManagement.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassHttpActivity toOcsfClassHttpActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassHttpActivity.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassIamAnalysisFinding
      toOcsfClassIamAnalysisFinding(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassIamAnalysisFinding.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassIncidentFinding toOcsfClassIncidentFinding(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassIncidentFinding.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassInventoryInfo toOcsfClassInventoryInfo(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassInventoryInfo.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassJobQuery toOcsfClassJobQuery(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.classes.OcsfClassJobQuery.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassKernelActivity toOcsfClassKernelActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassKernelActivity.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassKernelExtensionActivity
      toOcsfClassKernelExtensionActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassKernelExtensionActivity.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassKernelObjectQuery
      toOcsfClassKernelObjectQuery(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassKernelObjectQuery.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassMemoryActivity toOcsfClassMemoryActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassMemoryActivity.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassModuleActivity toOcsfClassModuleActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassModuleActivity.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassModuleQuery toOcsfClassModuleQuery(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassModuleQuery.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassNetworkActivity toOcsfClassNetworkActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassNetworkActivity.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassNetworkConnectionQuery
      toOcsfClassNetworkConnectionQuery(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassNetworkConnectionQuery.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassNetworkFileActivity
      toOcsfClassNetworkFileActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassNetworkFileActivity.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassNetworkRemediationActivity
      toOcsfClassNetworkRemediationActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassNetworkRemediationActivity.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassNetworksQuery toOcsfClassNetworksQuery(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassNetworksQuery.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassNtpActivity toOcsfClassNtpActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassNtpActivity.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassOsintInventoryInfo
      toOcsfClassOsintInventoryInfo(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassOsintInventoryInfo.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassPatchState toOcsfClassPatchState(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.classes.OcsfClassPatchState.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassPeripheralActivity
      toOcsfClassPeripheralActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassPeripheralActivity.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassPeripheralDeviceQuery
      toOcsfClassPeripheralDeviceQuery(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassPeripheralDeviceQuery.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassPrefetchQuery toOcsfClassPrefetchQuery(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassPrefetchQuery.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassProcessActivity toOcsfClassProcessActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassProcessActivity.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassProcessQuery toOcsfClassProcessQuery(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassProcessQuery.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassProcessRemediationActivity
      toOcsfClassProcessRemediationActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassProcessRemediationActivity.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassRdpActivity toOcsfClassRdpActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassRdpActivity.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassRegistryKeyActivity
      toOcsfClassRegistryKeyActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassRegistryKeyActivity.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassRegistryKeyQuery toOcsfClassRegistryKeyQuery(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassRegistryKeyQuery.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassRegistryValueActivity
      toOcsfClassRegistryValueActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassRegistryValueActivity.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassRegistryValueQuery
      toOcsfClassRegistryValueQuery(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassRegistryValueQuery.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassRemediationActivity
      toOcsfClassRemediationActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassRemediationActivity.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassRoleManagement toOcsfClassRoleManagement(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassRoleManagement.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassScanActivity toOcsfClassScanActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassScanActivity.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassScheduledJobActivity
      toOcsfClassScheduledJobActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassScheduledJobActivity.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassScriptActivity toOcsfClassScriptActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassScriptActivity.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassSecurityFinding toOcsfClassSecurityFinding(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassSecurityFinding.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassServiceQuery toOcsfClassServiceQuery(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassServiceQuery.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassSessionQuery toOcsfClassSessionQuery(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassSessionQuery.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassSmbActivity toOcsfClassSmbActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassSmbActivity.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassSoftwareInfo toOcsfClassSoftwareInfo(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassSoftwareInfo.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassSshActivity toOcsfClassSshActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassSshActivity.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassStartupItemQuery toOcsfClassStartupItemQuery(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassStartupItemQuery.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassTunnelActivity toOcsfClassTunnelActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassTunnelActivity.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassUserAccess toOcsfClassUserAccess(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.classes.OcsfClassUserAccess.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassUserInventory toOcsfClassUserInventory(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassUserInventory.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassUserManagement toOcsfClassUserManagement(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassUserManagement.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassUserQuery toOcsfClassUserQuery(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.classes.OcsfClassUserQuery.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassVulnerabilityFinding
      toOcsfClassVulnerabilityFinding(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassVulnerabilityFinding.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassWebResourceAccessActivity
      toOcsfClassWebResourceAccessActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassWebResourceAccessActivity.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassWebResourcesActivity
      toOcsfClassWebResourcesActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassWebResourcesActivity.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassWindowsResourceActivity
      toOcsfClassWindowsResourceActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassWindowsResourceActivity.class);
  }

  public io.openaev.ocsf.schema.v1100.classes.OcsfClassWindowsServiceActivity
      toOcsfClassWindowsServiceActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.classes.OcsfClassWindowsServiceActivity.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectAccessAnalysisResult
      toOcsfObjectAccessAnalysisResult(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectAccessAnalysisResult.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectAccount toOcsfObjectAccount(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectAccount.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectActor toOcsfObjectActor(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectActor.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectAdditionalRestriction
      toOcsfObjectAdditionalRestriction(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectAdditionalRestriction.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectAdvisory toOcsfObjectAdvisory(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectAdvisory.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectAffectedCode toOcsfObjectAffectedCode(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectAffectedCode.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectAffectedPackage toOcsfObjectAffectedPackage(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectAffectedPackage.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectAgent toOcsfObjectAgent(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectAgent.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectAiAgent toOcsfObjectAiAgent(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectAiAgent.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectAiCapability toOcsfObjectAiCapability(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectAiCapability.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectAiModel toOcsfObjectAiModel(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectAiModel.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectAiStatus toOcsfObjectAiStatus(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectAiStatus.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectAircraft toOcsfObjectAircraft(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectAircraft.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectAnalysisTarget toOcsfObjectAnalysisTarget(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectAnalysisTarget.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectAnalytic toOcsfObjectAnalytic(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectAnalytic.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectAnomaly toOcsfObjectAnomaly(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectAnomaly.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectAnomalyAnalysis toOcsfObjectAnomalyAnalysis(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectAnomalyAnalysis.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectApi toOcsfObjectApi(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectApi.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectApplication toOcsfObjectApplication(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectApplication.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectAssessment toOcsfObjectAssessment(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectAssessment.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectAttack toOcsfObjectAttack(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectAttack.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectAttestation toOcsfObjectAttestation(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectAttestation.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectAuthFactor toOcsfObjectAuthFactor(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectAuthFactor.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectAuthenticationToken
      toOcsfObjectAuthenticationToken(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectAuthenticationToken.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectAuthorization toOcsfObjectAuthorization(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectAuthorization.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectAutonomousSystem
      toOcsfObjectAutonomousSystem(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectAutonomousSystem.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectBaseline toOcsfObjectBaseline(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectBaseline.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectCampaign toOcsfObjectCampaign(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectCampaign.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectCertificate toOcsfObjectCertificate(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectCertificate.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectCheck toOcsfObjectCheck(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectCheck.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectCisBenchmark toOcsfObjectCisBenchmark(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectCisBenchmark.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectCisBenchmarkResult
      toOcsfObjectCisBenchmarkResult(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectCisBenchmarkResult.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectCisControl toOcsfObjectCisControl(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectCisControl.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectCisCsc toOcsfObjectCisCsc(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectCisCsc.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectClassifierDetails
      toOcsfObjectClassifierDetails(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectClassifierDetails.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectClipboard toOcsfObjectClipboard(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectClipboard.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectClipboardItem toOcsfObjectClipboardItem(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectClipboardItem.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectCloud toOcsfObjectCloud(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectCloud.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectCompliance toOcsfObjectCompliance(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectCompliance.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectContainer toOcsfObjectContainer(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectContainer.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectCpuInfo toOcsfObjectCpuInfo(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectCpuInfo.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectCve toOcsfObjectCve(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectCve.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectCvss toOcsfObjectCvss(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectCvss.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectCwe toOcsfObjectCwe(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectCwe.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectD3fTactic toOcsfObjectD3fTactic(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectD3fTactic.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectD3fTechnique toOcsfObjectD3fTechnique(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectD3fTechnique.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectD3fend toOcsfObjectD3fend(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectD3fend.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectDataClassification
      toOcsfObjectDataClassification(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectDataClassification.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectDataSecurity toOcsfObjectDataSecurity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectDataSecurity.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectDatabase toOcsfObjectDatabase(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectDatabase.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectDatabucket toOcsfObjectDatabucket(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectDatabucket.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectDceRpc toOcsfObjectDceRpc(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectDceRpc.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectDelegation toOcsfObjectDelegation(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectDelegation.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectDevice toOcsfObjectDevice(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectDevice.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectDeviceHwInfo toOcsfObjectDeviceHwInfo(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectDeviceHwInfo.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectDigitalSignature
      toOcsfObjectDigitalSignature(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectDigitalSignature.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectDiscoveryDetails
      toOcsfObjectDiscoveryDetails(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectDiscoveryDetails.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectDisplay toOcsfObjectDisplay(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectDisplay.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectDnsAnswer toOcsfObjectDnsAnswer(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectDnsAnswer.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectDnsQuery toOcsfObjectDnsQuery(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectDnsQuery.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectDnsResourceRecord
      toOcsfObjectDnsResourceRecord(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectDnsResourceRecord.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectDnsSection toOcsfObjectDnsSection(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectDnsSection.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectDnsSoa toOcsfObjectDnsSoa(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectDnsSoa.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectDomainContact toOcsfObjectDomainContact(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectDomainContact.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectDownloadInfo toOcsfObjectDownloadInfo(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectDownloadInfo.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectEdge toOcsfObjectEdge(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectEdge.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectEmail toOcsfObjectEmail(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectEmail.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectEmailAuth toOcsfObjectEmailAuth(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectEmailAuth.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectEncryptionDetails
      toOcsfObjectEncryptionDetails(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectEncryptionDetails.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectEndpoint toOcsfObjectEndpoint(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectEndpoint.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectEndpointConnection
      toOcsfObjectEndpointConnection(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectEndpointConnection.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectEnrichment toOcsfObjectEnrichment(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectEnrichment.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectEnvironmentVariable
      toOcsfObjectEnvironmentVariable(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectEnvironmentVariable.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectEpss toOcsfObjectEpss(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectEpss.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectEvidences toOcsfObjectEvidences(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectEvidences.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectExtension toOcsfObjectExtension(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectExtension.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectFeature toOcsfObjectFeature(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectFeature.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectFile toOcsfObjectFile(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectFile.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectFinding toOcsfObjectFinding(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectFinding.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectFindingInfo toOcsfObjectFindingInfo(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectFindingInfo.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectFingerprint toOcsfObjectFingerprint(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectFingerprint.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectFirewallRule toOcsfObjectFirewallRule(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectFirewallRule.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectFirewallRuleEvaluation
      toOcsfObjectFirewallRuleEvaluation(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectFirewallRuleEvaluation.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectFunctionInvocation
      toOcsfObjectFunctionInvocation(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectFunctionInvocation.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectGpuInfo toOcsfObjectGpuInfo(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectGpuInfo.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectGraph toOcsfObjectGraph(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectGraph.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectGroup toOcsfObjectGroup(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectGroup.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectHassh toOcsfObjectHassh(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectHassh.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectHttpCookie toOcsfObjectHttpCookie(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectHttpCookie.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectHttpHeader toOcsfObjectHttpHeader(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectHttpHeader.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectHttpRequest toOcsfObjectHttpRequest(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectHttpRequest.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectHttpResponse toOcsfObjectHttpResponse(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectHttpResponse.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectIamRole toOcsfObjectIamRole(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectIamRole.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectIdentityActivityMetrics
      toOcsfObjectIdentityActivityMetrics(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectIdentityActivityMetrics.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectIdp toOcsfObjectIdp(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectIdp.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectImage toOcsfObjectImage(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectImage.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectJa4Fingerprint toOcsfObjectJa4Fingerprint(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectJa4Fingerprint.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectJob toOcsfObjectJob(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectJob.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectJobAction toOcsfObjectJobAction(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectJobAction.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectJobTrigger toOcsfObjectJobTrigger(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectJobTrigger.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectKbArticle toOcsfObjectKbArticle(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectKbArticle.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectKernel toOcsfObjectKernel(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectKernel.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectKernelDriver toOcsfObjectKernelDriver(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectKernelDriver.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectKeyValueObject toOcsfObjectKeyValueObject(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectKeyValueObject.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectKeyboardInfo toOcsfObjectKeyboardInfo(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectKeyboardInfo.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectKillChainPhase toOcsfObjectKillChainPhase(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectKillChainPhase.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectLdapPerson toOcsfObjectLdapPerson(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectLdapPerson.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectLoadBalancer toOcsfObjectLoadBalancer(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectLoadBalancer.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectLocation toOcsfObjectLocation(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectLocation.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectLogger toOcsfObjectLogger(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectLogger.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectLongString toOcsfObjectLongString(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectLongString.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectMalware toOcsfObjectMalware(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectMalware.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectMalwareScanInfo toOcsfObjectMalwareScanInfo(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectMalwareScanInfo.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectManagedEntity toOcsfObjectManagedEntity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectManagedEntity.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectMessageContext toOcsfObjectMessageContext(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectMessageContext.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectMetadata toOcsfObjectMetadata(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectMetadata.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectMetric toOcsfObjectMetric(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectMetric.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectMitigation toOcsfObjectMitigation(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectMitigation.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectModule toOcsfObjectModule(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectModule.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectNetworkConnectionInfo
      toOcsfObjectNetworkConnectionInfo(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectNetworkConnectionInfo.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectNetworkEndpoint toOcsfObjectNetworkEndpoint(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectNetworkEndpoint.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectNetworkInterface
      toOcsfObjectNetworkInterface(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectNetworkInterface.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectNetworkProxy toOcsfObjectNetworkProxy(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectNetworkProxy.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectNetworkTraffic toOcsfObjectNetworkTraffic(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectNetworkTraffic.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectNode toOcsfObjectNode(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectNode.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectNote toOcsfObjectNote(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectNote.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectObject toOcsfObjectObject(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectObject.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectObservable toOcsfObjectObservable(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectObservable.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectObservation toOcsfObjectObservation(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectObservation.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectOccurrenceDetails
      toOcsfObjectOccurrenceDetails(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectOccurrenceDetails.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectOrganization toOcsfObjectOrganization(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectOrganization.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectOs toOcsfObjectOs(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectOs.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectOsint toOcsfObjectOsint(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectOsint.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectPackage toOcsfObjectPackage(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectPackage.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectPacket toOcsfObjectPacket(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectPacket.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectParameter toOcsfObjectParameter(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectParameter.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectPeripheralDevice
      toOcsfObjectPeripheralDevice(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectPeripheralDevice.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectPermissionAnalysisResult
      toOcsfObjectPermissionAnalysisResult(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectPermissionAnalysisResult.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectPhysicalSite toOcsfObjectPhysicalSite(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectPhysicalSite.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectPolicy toOcsfObjectPolicy(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectPolicy.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectPortInfo toOcsfObjectPortInfo(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectPortInfo.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectPrevEvent toOcsfObjectPrevEvent(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectPrevEvent.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectPrivilegeAttackInfo
      toOcsfObjectPrivilegeAttackInfo(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectPrivilegeAttackInfo.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectPrivilegeInfo toOcsfObjectPrivilegeInfo(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectPrivilegeInfo.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectProcess toOcsfObjectProcess(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectProcess.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectProcessEntity toOcsfObjectProcessEntity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectProcessEntity.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectProduct toOcsfObjectProduct(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectProduct.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectProgrammaticCredential
      toOcsfObjectProgrammaticCredential(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectProgrammaticCredential.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectQueryEvidence toOcsfObjectQueryEvidence(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectQueryEvidence.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectQueryInfo toOcsfObjectQueryInfo(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectQueryInfo.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectRegKey toOcsfObjectRegKey(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectRegKey.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectRegValue toOcsfObjectRegValue(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectRegValue.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectRelatedEvent toOcsfObjectRelatedEvent(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectRelatedEvent.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectRemediation toOcsfObjectRemediation(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectRemediation.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectReporter toOcsfObjectReporter(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectReporter.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectReputation toOcsfObjectReputation(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectReputation.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectRequest toOcsfObjectRequest(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectRequest.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectResourceDetails toOcsfObjectResourceDetails(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectResourceDetails.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectResponse toOcsfObjectResponse(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectResponse.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectRpcInterface toOcsfObjectRpcInterface(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectRpcInterface.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectRule toOcsfObjectRule(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectRule.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectSan toOcsfObjectSan(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectSan.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectSbom toOcsfObjectSbom(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectSbom.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectScan toOcsfObjectScan(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectScan.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectScim toOcsfObjectScim(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectScim.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectScript toOcsfObjectScript(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectScript.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectSecurityState toOcsfObjectSecurityState(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectSecurityState.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectSensorInfo toOcsfObjectSensorInfo(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectSensorInfo.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectService toOcsfObjectService(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectService.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectServicePrivilegeAnalysis
      toOcsfObjectServicePrivilegeAnalysis(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectServicePrivilegeAnalysis.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectSession toOcsfObjectSession(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectSession.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectSiteZone toOcsfObjectSiteZone(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectSiteZone.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectSoftwareComponent
      toOcsfObjectSoftwareComponent(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectSoftwareComponent.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectSpan toOcsfObjectSpan(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectSpan.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectSso toOcsfObjectSso(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectSso.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectStartupItem toOcsfObjectStartupItem(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectStartupItem.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectSubTechnique toOcsfObjectSubTechnique(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectSubTechnique.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectTable toOcsfObjectTable(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectTable.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectTactic toOcsfObjectTactic(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectTactic.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectTechnique toOcsfObjectTechnique(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectTechnique.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectThreatActor toOcsfObjectThreatActor(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectThreatActor.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectTicket toOcsfObjectTicket(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectTicket.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectTimespan toOcsfObjectTimespan(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectTimespan.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectTls toOcsfObjectTls(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectTls.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectTlsExtension toOcsfObjectTlsExtension(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectTlsExtension.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectToken toOcsfObjectToken(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectToken.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectTrace toOcsfObjectTrace(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectTrace.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectTrait toOcsfObjectTrait(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectTrait.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectTransformationInfo
      toOcsfObjectTransformationInfo(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectTransformationInfo.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectTsig toOcsfObjectTsig(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectTsig.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectUnmannedAerialSystem
      toOcsfObjectUnmannedAerialSystem(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectUnmannedAerialSystem.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectUnmannedSystemOperatingArea
      toOcsfObjectUnmannedSystemOperatingArea(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectUnmannedSystemOperatingArea.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectUrl toOcsfObjectUrl(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectUrl.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectUser toOcsfObjectUser(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectUser.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectVendorAttributes
      toOcsfObjectVendorAttributes(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectVendorAttributes.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectVulnerability toOcsfObjectVulnerability(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectVulnerability.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectWebResource toOcsfObjectWebResource(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectWebResource.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectWhois toOcsfObjectWhois(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectWhois.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectWifi toOcsfObjectWifi(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectWifi.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectWinResource toOcsfObjectWinResource(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectWinResource.class);
  }

  public io.openaev.ocsf.schema.v1100.objects.OcsfObjectWinService toOcsfObjectWinService(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v1100.objects.OcsfObjectWinService.class);
  }
}
