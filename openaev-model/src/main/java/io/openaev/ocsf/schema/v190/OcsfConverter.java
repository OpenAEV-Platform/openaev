package io.openaev.ocsf.schema.v190;

import com.fasterxml.jackson.databind.ObjectMapper;

public class OcsfConverter {

  private com.fasterxml.jackson.databind.ObjectMapper mapper = new ObjectMapper();

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectProgrammaticCredential
      toOcsfObjectProgrammaticCredential(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectProgrammaticCredential.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectAccount toOcsfObjectAccount(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectAccount.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectCheck toOcsfObjectCheck(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectCheck.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectWinResource toOcsfObjectWinResource(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectWinResource.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectDnsAnswer toOcsfObjectDnsAnswer(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectDnsAnswer.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectDnsSection toOcsfObjectDnsSection(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectDnsSection.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectClipboard toOcsfObjectClipboard(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectClipboard.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectEvidences toOcsfObjectEvidences(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectEvidences.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectAttestation toOcsfObjectAttestation(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectAttestation.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectFeature toOcsfObjectFeature(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectFeature.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectSoftwareComponent
      toOcsfObjectSoftwareComponent(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectSoftwareComponent.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectScript toOcsfObjectScript(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectScript.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectSensorInfo toOcsfObjectSensorInfo(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectSensorInfo.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassKernelActivity toOcsfClassKernelActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassKernelActivity.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassSecurityFinding toOcsfClassSecurityFinding(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassSecurityFinding.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectAgent toOcsfObjectAgent(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectAgent.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectExtension toOcsfObjectExtension(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectExtension.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectRelatedEvent toOcsfObjectRelatedEvent(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectRelatedEvent.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectLoadBalancer toOcsfObjectLoadBalancer(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectLoadBalancer.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassScriptActivity toOcsfClassScriptActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassScriptActivity.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectEncryptionDetails
      toOcsfObjectEncryptionDetails(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectEncryptionDetails.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectCpuInfo toOcsfObjectCpuInfo(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectCpuInfo.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectObject toOcsfObjectObject(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectObject.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectCompliance toOcsfObjectCompliance(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectCompliance.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassCloudResourcesInventoryInfo
      toOcsfClassCloudResourcesInventoryInfo(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassCloudResourcesInventoryInfo.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectContainer toOcsfObjectContainer(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectContainer.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassWindowsResourceActivity
      toOcsfClassWindowsResourceActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassWindowsResourceActivity.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassEvidenceInfo toOcsfClassEvidenceInfo(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassEvidenceInfo.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectJa4Fingerprint toOcsfObjectJa4Fingerprint(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectJa4Fingerprint.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectEnvironmentVariable
      toOcsfObjectEnvironmentVariable(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectEnvironmentVariable.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassDataSecurityFinding
      toOcsfClassDataSecurityFinding(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassDataSecurityFinding.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassTunnelActivity toOcsfClassTunnelActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassTunnelActivity.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassOsintInventoryInfo
      toOcsfClassOsintInventoryInfo(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassOsintInventoryInfo.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectWinService toOcsfObjectWinService(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectWinService.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectHttpResponse toOcsfObjectHttpResponse(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectHttpResponse.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectFirewallRule toOcsfObjectFirewallRule(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectFirewallRule.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectAffectedCode toOcsfObjectAffectedCode(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectAffectedCode.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectManagedEntity toOcsfObjectManagedEntity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectManagedEntity.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectDiscoveryDetails
      toOcsfObjectDiscoveryDetails(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectDiscoveryDetails.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassNetworkConnectionQuery
      toOcsfClassNetworkConnectionQuery(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassNetworkConnectionQuery.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassPeripheralActivity
      toOcsfClassPeripheralActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassPeripheralActivity.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectRequest toOcsfObjectRequest(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectRequest.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectQueryInfo toOcsfObjectQueryInfo(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectQueryInfo.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassNetworkFileActivity
      toOcsfClassNetworkFileActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassNetworkFileActivity.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectMessageContext toOcsfObjectMessageContext(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectMessageContext.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectReputation toOcsfObjectReputation(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectReputation.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassEmailUrlActivity toOcsfClassEmailUrlActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassEmailUrlActivity.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectCisBenchmark toOcsfObjectCisBenchmark(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectCisBenchmark.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectTlsExtension toOcsfObjectTlsExtension(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectTlsExtension.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectTransformationInfo
      toOcsfObjectTransformationInfo(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectTransformationInfo.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectToken toOcsfObjectToken(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectToken.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectPortInfo toOcsfObjectPortInfo(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectPortInfo.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassUserManagement toOcsfClassUserManagement(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassUserManagement.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectDataClassification
      toOcsfObjectDataClassification(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectDataClassification.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectJobTrigger toOcsfObjectJobTrigger(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectJobTrigger.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassUserQuery toOcsfClassUserQuery(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.classes.OcsfClassUserQuery.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectNetworkProxy toOcsfObjectNetworkProxy(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectNetworkProxy.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectFindingInfo toOcsfObjectFindingInfo(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectFindingInfo.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectLogger toOcsfObjectLogger(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectLogger.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassProcessQuery toOcsfClassProcessQuery(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassProcessQuery.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectEdge toOcsfObjectEdge(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectEdge.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectFinding toOcsfObjectFinding(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectFinding.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassRegistryValueQuery
      toOcsfClassRegistryValueQuery(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassRegistryValueQuery.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectPackage toOcsfObjectPackage(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectPackage.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectUnmannedAerialSystem
      toOcsfObjectUnmannedAerialSystem(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectUnmannedAerialSystem.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassAuthentication toOcsfClassAuthentication(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassAuthentication.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectReporter toOcsfObjectReporter(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectReporter.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassVulnerabilityFinding
      toOcsfClassVulnerabilityFinding(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassVulnerabilityFinding.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectAuthFactor toOcsfObjectAuthFactor(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectAuthFactor.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectUnmannedSystemOperatingArea
      toOcsfObjectUnmannedSystemOperatingArea(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectUnmannedSystemOperatingArea.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectVendorAttributes
      toOcsfObjectVendorAttributes(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectVendorAttributes.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassApplicationError toOcsfClassApplicationError(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassApplicationError.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectPrivilegeAttackInfo
      toOcsfObjectPrivilegeAttackInfo(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectPrivilegeAttackInfo.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassEntityManagement toOcsfClassEntityManagement(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassEntityManagement.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassDroneFlightsActivity
      toOcsfClassDroneFlightsActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassDroneFlightsActivity.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectOs toOcsfObjectOs(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectOs.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectEndpointConnection
      toOcsfObjectEndpointConnection(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectEndpointConnection.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassDatastoreActivity
      toOcsfClassDatastoreActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassDatastoreActivity.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectTrait toOcsfObjectTrait(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectTrait.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectAdvisory toOcsfObjectAdvisory(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectAdvisory.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectDomainContact toOcsfObjectDomainContact(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectDomainContact.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectOrganization toOcsfObjectOrganization(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectOrganization.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectD3fTechnique toOcsfObjectD3fTechnique(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectD3fTechnique.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectLocation toOcsfObjectLocation(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectLocation.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassNtpActivity toOcsfClassNtpActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.classes.OcsfClassNtpActivity.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectImage toOcsfObjectImage(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectImage.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectCisBenchmarkResult
      toOcsfObjectCisBenchmarkResult(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectCisBenchmarkResult.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectCisCsc toOcsfObjectCisCsc(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectCisCsc.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectResourceDetails toOcsfObjectResourceDetails(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectResourceDetails.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectAuthorization toOcsfObjectAuthorization(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectAuthorization.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectCve toOcsfObjectCve(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectCve.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassModuleQuery toOcsfClassModuleQuery(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.classes.OcsfClassModuleQuery.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectDigitalSignature
      toOcsfObjectDigitalSignature(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectDigitalSignature.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectScan toOcsfObjectScan(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectScan.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectAiModel toOcsfObjectAiModel(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectAiModel.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectCvss toOcsfObjectCvss(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectCvss.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassClipboardActivity
      toOcsfClassClipboardActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassClipboardActivity.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectMalware toOcsfObjectMalware(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectMalware.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectScim toOcsfObjectScim(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectScim.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectSpan toOcsfObjectSpan(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectSpan.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectIdentityActivityMetrics
      toOcsfObjectIdentityActivityMetrics(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectIdentityActivityMetrics.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectFile toOcsfObjectFile(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectFile.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectQueryEvidence toOcsfObjectQueryEvidence(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectQueryEvidence.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassPatchState toOcsfClassPatchState(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.classes.OcsfClassPatchState.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassInventoryInfo toOcsfClassInventoryInfo(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassInventoryInfo.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectOccurrenceDetails
      toOcsfObjectOccurrenceDetails(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectOccurrenceDetails.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectPermissionAnalysisResult
      toOcsfObjectPermissionAnalysisResult(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectPermissionAnalysisResult.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassEmailFileActivity
      toOcsfClassEmailFileActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassEmailFileActivity.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectKernel toOcsfObjectKernel(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectKernel.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectProduct toOcsfObjectProduct(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectProduct.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectObservation toOcsfObjectObservation(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectObservation.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectGroup toOcsfObjectGroup(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectGroup.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectDataSecurity toOcsfObjectDataSecurity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectDataSecurity.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectMitigation toOcsfObjectMitigation(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectMitigation.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectAnomalyAnalysis toOcsfObjectAnomalyAnalysis(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectAnomalyAnalysis.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassRegistryKeyQuery toOcsfClassRegistryKeyQuery(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassRegistryKeyQuery.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectTrace toOcsfObjectTrace(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectTrace.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassJobQuery toOcsfClassJobQuery(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.classes.OcsfClassJobQuery.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectTsig toOcsfObjectTsig(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectTsig.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectWebResource toOcsfObjectWebResource(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectWebResource.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectResponse toOcsfObjectResponse(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectResponse.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectD3fend toOcsfObjectD3fend(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectD3fend.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassDhcpActivity toOcsfClassDhcpActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassDhcpActivity.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectRpcInterface toOcsfObjectRpcInterface(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectRpcInterface.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectMetric toOcsfObjectMetric(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectMetric.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectNetworkConnectionInfo
      toOcsfObjectNetworkConnectionInfo(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectNetworkConnectionInfo.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectUser toOcsfObjectUser(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectUser.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectModule toOcsfObjectModule(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectModule.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectServicePrivilegeAnalysis
      toOcsfObjectServicePrivilegeAnalysis(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectServicePrivilegeAnalysis.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectProcessEntity toOcsfObjectProcessEntity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectProcessEntity.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectCisControl toOcsfObjectCisControl(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectCisControl.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectAnalytic toOcsfObjectAnalytic(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectAnalytic.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectDelegation toOcsfObjectDelegation(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectDelegation.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectEmailAuth toOcsfObjectEmailAuth(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectEmailAuth.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectGraph toOcsfObjectGraph(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectGraph.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassConfigState toOcsfClassConfigState(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.classes.OcsfClassConfigState.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectTechnique toOcsfObjectTechnique(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectTechnique.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectNetworkTraffic toOcsfObjectNetworkTraffic(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectNetworkTraffic.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassNetworkActivity toOcsfClassNetworkActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassNetworkActivity.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectPacket toOcsfObjectPacket(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectPacket.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassHttpActivity toOcsfClassHttpActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassHttpActivity.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectDevice toOcsfObjectDevice(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectDevice.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassIamAnalysisFinding
      toOcsfClassIamAnalysisFinding(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassIamAnalysisFinding.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassKernelObjectQuery
      toOcsfClassKernelObjectQuery(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassKernelObjectQuery.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassDeviceConfigStateChange
      toOcsfClassDeviceConfigStateChange(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassDeviceConfigStateChange.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassWindowsServiceActivity
      toOcsfClassWindowsServiceActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassWindowsServiceActivity.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectDatabase toOcsfObjectDatabase(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectDatabase.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassWebResourcesActivity
      toOcsfClassWebResourcesActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassWebResourcesActivity.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectWhois toOcsfObjectWhois(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectWhois.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectIamRole toOcsfObjectIamRole(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectIamRole.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectAffectedPackage toOcsfObjectAffectedPackage(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectAffectedPackage.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassStartupItemQuery toOcsfClassStartupItemQuery(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassStartupItemQuery.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassFileHosting toOcsfClassFileHosting(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.classes.OcsfClassFileHosting.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectCloud toOcsfObjectCloud(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectCloud.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectLdapPerson toOcsfObjectLdapPerson(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectLdapPerson.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassAirborneBroadcastActivity
      toOcsfClassAirborneBroadcastActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassAirborneBroadcastActivity.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectHttpRequest toOcsfObjectHttpRequest(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectHttpRequest.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectMetadata toOcsfObjectMetadata(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectMetadata.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectDatabucket toOcsfObjectDatabucket(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectDatabucket.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassModuleActivity toOcsfClassModuleActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassModuleActivity.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectNetworkEndpoint toOcsfObjectNetworkEndpoint(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectNetworkEndpoint.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectSbom toOcsfObjectSbom(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectSbom.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectDnsQuery toOcsfObjectDnsQuery(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectDnsQuery.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectJob toOcsfObjectJob(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectJob.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectLongString toOcsfObjectLongString(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectLongString.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectIdp toOcsfObjectIdp(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectIdp.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectJobAction toOcsfObjectJobAction(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectJobAction.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectTls toOcsfObjectTls(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectTls.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectPrevEvent toOcsfObjectPrevEvent(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectPrevEvent.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectNode toOcsfObjectNode(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectNode.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectBaseline toOcsfObjectBaseline(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectBaseline.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassEventLogActvity toOcsfClassEventLogActvity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassEventLogActvity.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassFtpActivity toOcsfClassFtpActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.classes.OcsfClassFtpActivity.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassEmailActivity toOcsfClassEmailActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassEmailActivity.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectTimespan toOcsfObjectTimespan(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectTimespan.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassServiceQuery toOcsfClassServiceQuery(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassServiceQuery.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassNetworksQuery toOcsfClassNetworksQuery(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassNetworksQuery.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectRegKey toOcsfObjectRegKey(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectRegKey.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectApi toOcsfObjectApi(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectApi.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassPrefetchQuery toOcsfClassPrefetchQuery(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassPrefetchQuery.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectSubTechnique toOcsfObjectSubTechnique(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectSubTechnique.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassFolderQuery toOcsfClassFolderQuery(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.classes.OcsfClassFolderQuery.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectHttpHeader toOcsfObjectHttpHeader(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectHttpHeader.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectPolicy toOcsfObjectPolicy(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectPolicy.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectNote toOcsfObjectNote(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectNote.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassPeripheralDeviceQuery
      toOcsfClassPeripheralDeviceQuery(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassPeripheralDeviceQuery.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectRule toOcsfObjectRule(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectRule.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassWebResourceAccessActivity
      toOcsfClassWebResourceAccessActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassWebResourceAccessActivity.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectActor toOcsfObjectActor(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectActor.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectNetworkInterface
      toOcsfObjectNetworkInterface(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectNetworkInterface.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassAccountChange toOcsfClassAccountChange(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassAccountChange.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassMemoryActivity toOcsfClassMemoryActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassMemoryActivity.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectParameter toOcsfObjectParameter(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectParameter.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectTicket toOcsfObjectTicket(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectTicket.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassScanActivity toOcsfClassScanActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassScanActivity.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectDeviceHwInfo toOcsfObjectDeviceHwInfo(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectDeviceHwInfo.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectFunctionInvocation
      toOcsfObjectFunctionInvocation(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectFunctionInvocation.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectKernelDriver toOcsfObjectKernelDriver(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectKernelDriver.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectKbArticle toOcsfObjectKbArticle(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectKbArticle.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectService toOcsfObjectService(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectService.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassKernelExtensionActivity
      toOcsfClassKernelExtensionActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassKernelExtensionActivity.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectPrivilegeInfo toOcsfObjectPrivilegeInfo(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectPrivilegeInfo.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectAircraft toOcsfObjectAircraft(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectAircraft.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectAutonomousSystem
      toOcsfObjectAutonomousSystem(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectAutonomousSystem.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectSecurityState toOcsfObjectSecurityState(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectSecurityState.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectKeyValueObject toOcsfObjectKeyValueObject(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectKeyValueObject.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectDceRpc toOcsfObjectDceRpc(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectDceRpc.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectHassh toOcsfObjectHassh(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectHassh.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectDownloadInfo toOcsfObjectDownloadInfo(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectDownloadInfo.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectDnsResourceRecord
      toOcsfObjectDnsResourceRecord(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectDnsResourceRecord.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassAuthorizeSession toOcsfClassAuthorizeSession(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassAuthorizeSession.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectDisplay toOcsfObjectDisplay(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectDisplay.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectEndpoint toOcsfObjectEndpoint(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectEndpoint.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectHttpCookie toOcsfObjectHttpCookie(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectHttpCookie.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassNetworkRemediationActivity
      toOcsfClassNetworkRemediationActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassNetworkRemediationActivity.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectClassifierDetails
      toOcsfObjectClassifierDetails(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectClassifierDetails.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectCwe toOcsfObjectCwe(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectCwe.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassProcessRemediationActivity
      toOcsfClassProcessRemediationActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassProcessRemediationActivity.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassRegistryKeyActivity
      toOcsfClassRegistryKeyActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassRegistryKeyActivity.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassDnsActivity toOcsfClassDnsActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.classes.OcsfClassDnsActivity.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassFileActivity toOcsfClassFileActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassFileActivity.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectOsint toOcsfObjectOsint(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectOsint.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectAdditionalRestriction
      toOcsfObjectAdditionalRestriction(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectAdditionalRestriction.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectFingerprint toOcsfObjectFingerprint(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectFingerprint.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectAnalysisTarget toOcsfObjectAnalysisTarget(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectAnalysisTarget.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectAssessment toOcsfObjectAssessment(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectAssessment.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassApplicationLifecycle
      toOcsfClassApplicationLifecycle(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassApplicationLifecycle.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassDetectionFinding toOcsfClassDetectionFinding(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassDetectionFinding.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassDevicePowerStateActivity
      toOcsfClassDevicePowerStateActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassDevicePowerStateActivity.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassFileRemediationActivity
      toOcsfClassFileRemediationActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassFileRemediationActivity.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectApplication toOcsfObjectApplication(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectApplication.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassSessionQuery toOcsfClassSessionQuery(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassSessionQuery.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectThreatActor toOcsfObjectThreatActor(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectThreatActor.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassRdpActivity toOcsfClassRdpActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.classes.OcsfClassRdpActivity.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectSan toOcsfObjectSan(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectSan.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassProcessActivity toOcsfClassProcessActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassProcessActivity.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassComplianceFinding
      toOcsfClassComplianceFinding(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassComplianceFinding.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassUserAccess toOcsfClassUserAccess(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.classes.OcsfClassUserAccess.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassSmbActivity toOcsfClassSmbActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.classes.OcsfClassSmbActivity.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassFileQuery toOcsfClassFileQuery(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.classes.OcsfClassFileQuery.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectGpuInfo toOcsfObjectGpuInfo(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectGpuInfo.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectPeripheralDevice
      toOcsfObjectPeripheralDevice(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectPeripheralDevice.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectProcess toOcsfObjectProcess(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectProcess.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectEmail toOcsfObjectEmail(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectEmail.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectClipboardItem toOcsfObjectClipboardItem(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectClipboardItem.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectMalwareScanInfo toOcsfObjectMalwareScanInfo(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectMalwareScanInfo.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectRemediation toOcsfObjectRemediation(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectRemediation.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassUserInventory toOcsfClassUserInventory(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassUserInventory.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectObservable toOcsfObjectObservable(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectObservable.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassRemediationActivity
      toOcsfClassRemediationActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassRemediationActivity.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassGroupManagement toOcsfClassGroupManagement(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassGroupManagement.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectAuthenticationToken
      toOcsfObjectAuthenticationToken(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectAuthenticationToken.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectCertificate toOcsfObjectCertificate(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectCertificate.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectKeyboardInfo toOcsfObjectKeyboardInfo(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectKeyboardInfo.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassIncidentFinding toOcsfClassIncidentFinding(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassIncidentFinding.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectEpss toOcsfObjectEpss(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectEpss.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectSession toOcsfObjectSession(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectSession.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassSshActivity toOcsfClassSshActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.classes.OcsfClassSshActivity.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectTable toOcsfObjectTable(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectTable.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectSso toOcsfObjectSso(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectSso.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectRegValue toOcsfObjectRegValue(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectRegValue.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectAccessAnalysisResult
      toOcsfObjectAccessAnalysisResult(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectAccessAnalysisResult.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassScheduledJobActivity
      toOcsfClassScheduledJobActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassScheduledJobActivity.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassBaseEvent toOcsfClassBaseEvent(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.classes.OcsfClassBaseEvent.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassApplicationSecurityPostureFinding
      toOcsfClassApplicationSecurityPostureFinding(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassApplicationSecurityPostureFinding.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassAdminGroupQuery toOcsfClassAdminGroupQuery(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassAdminGroupQuery.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectUrl toOcsfObjectUrl(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectUrl.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassSoftwareInfo toOcsfClassSoftwareInfo(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassSoftwareInfo.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassApiActivity toOcsfClassApiActivity(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.classes.OcsfClassApiActivity.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassRegistryValueActivity
      toOcsfClassRegistryValueActivity(com.fasterxml.jackson.databind.JsonNode node)
          throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassRegistryValueActivity.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectAnomaly toOcsfObjectAnomaly(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectAnomaly.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectKillChainPhase toOcsfObjectKillChainPhase(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectKillChainPhase.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectAttack toOcsfObjectAttack(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectAttack.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectAiAgent toOcsfObjectAiAgent(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectAiAgent.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectStartupItem toOcsfObjectStartupItem(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectStartupItem.class);
  }

  public io.openaev.ocsf.schema.v190.classes.OcsfClassRoleManagement toOcsfClassRoleManagement(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.classes.OcsfClassRoleManagement.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectTactic toOcsfObjectTactic(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectTactic.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectD3fTactic toOcsfObjectD3fTactic(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectD3fTactic.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectVulnerability toOcsfObjectVulnerability(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(
        node, io.openaev.ocsf.schema.v190.objects.OcsfObjectVulnerability.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectCampaign toOcsfObjectCampaign(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectCampaign.class);
  }

  public io.openaev.ocsf.schema.v190.objects.OcsfObjectEnrichment toOcsfObjectEnrichment(
      com.fasterxml.jackson.databind.JsonNode node)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return mapper.treeToValue(node, io.openaev.ocsf.schema.v190.objects.OcsfObjectEnrichment.class);
  }
}
