package io.openaev.database.model;

import java.util.Arrays;
import java.util.List;

/**
 * Secondary classification of an {@link Asset} (level 2 of the asset taxonomy). Every subcategory
 * belongs to exactly one {@link AssetCategory}; the {@code category -> subcategories} mapping is
 * the single source of truth used by the backend validation and mirrored by the frontend manifest.
 *
 * <p>For {@link AssetCategory#CLOUD_RESOURCE} the subcategory captures the cloud service group
 * (compute, storage, database, ...); the concrete provider native type is stored separately on
 * {@code asset_cloud_native_type} to model the full breadth of cloud resources.
 */
public enum AssetSubCategory {
  // HOST
  SERVER(AssetCategory.HOST),
  WORKSTATION(AssetCategory.HOST),
  LAPTOP(AssetCategory.HOST),
  VIRTUAL_MACHINE(AssetCategory.HOST),
  HYPERVISOR(AssetCategory.HOST),
  MAINFRAME(AssetCategory.HOST),
  THIN_CLIENT(AssetCategory.HOST),

  // CONTAINER_WORKLOAD
  CONTAINER(AssetCategory.CONTAINER_WORKLOAD),
  CONTAINER_IMAGE(AssetCategory.CONTAINER_WORKLOAD),
  KUBERNETES_POD(AssetCategory.CONTAINER_WORKLOAD),
  KUBERNETES_CLUSTER(AssetCategory.CONTAINER_WORKLOAD),
  KUBERNETES_NODE(AssetCategory.CONTAINER_WORKLOAD),
  SERVERLESS_FUNCTION(AssetCategory.CONTAINER_WORKLOAD),

  // CLOUD_RESOURCE (service groups)
  COMPUTE(AssetCategory.CLOUD_RESOURCE),
  STORAGE(AssetCategory.CLOUD_RESOURCE),
  DATABASE(AssetCategory.CLOUD_RESOURCE),
  NETWORKING(AssetCategory.CLOUD_RESOURCE),
  SERVERLESS(AssetCategory.CLOUD_RESOURCE),
  CONTAINER_REGISTRY(AssetCategory.CLOUD_RESOURCE),
  KUBERNETES(AssetCategory.CLOUD_RESOURCE),
  IAM_PRINCIPAL(AssetCategory.CLOUD_RESOURCE),
  SECRETS_KEY_MGMT(AssetCategory.CLOUD_RESOURCE),
  MESSAGING_QUEUE(AssetCategory.CLOUD_RESOURCE),
  ANALYTICS_DATA(AssetCategory.CLOUD_RESOURCE),
  AI_ML_SERVICE(AssetCategory.CLOUD_RESOURCE),
  IAC_TEMPLATE(AssetCategory.CLOUD_RESOURCE),
  CLOUD_OTHER(AssetCategory.CLOUD_RESOURCE),

  // WEB_APPLICATION
  WEBSITE(AssetCategory.WEB_APPLICATION),
  WEB_API(AssetCategory.WEB_APPLICATION),
  SINGLE_PAGE_APP(AssetCategory.WEB_APPLICATION),
  GRAPHQL_API(AssetCategory.WEB_APPLICATION),
  WEB_SERVICE(AssetCategory.WEB_APPLICATION),
  MICROSERVICE(AssetCategory.WEB_APPLICATION),

  // NETWORK_DEVICE
  ROUTER(AssetCategory.NETWORK_DEVICE),
  SWITCH(AssetCategory.NETWORK_DEVICE),
  FIREWALL(AssetCategory.NETWORK_DEVICE),
  LOAD_BALANCER(AssetCategory.NETWORK_DEVICE),
  VPN_GATEWAY(AssetCategory.NETWORK_DEVICE),
  WIRELESS_AP(AssetCategory.NETWORK_DEVICE),
  PROXY(AssetCategory.NETWORK_DEVICE),
  DNS_SERVER(AssetCategory.NETWORK_DEVICE),
  DHCP_SERVER(AssetCategory.NETWORK_DEVICE),
  SAN_NAS(AssetCategory.NETWORK_DEVICE),
  NETWORK_OTHER(AssetCategory.NETWORK_DEVICE),

  // MOBILE_DEVICE
  SMARTPHONE(AssetCategory.MOBILE_DEVICE),
  TABLET(AssetCategory.MOBILE_DEVICE),

  // IOT_OT_DEVICE
  IOT_SENSOR(AssetCategory.IOT_OT_DEVICE),
  IP_CAMERA(AssetCategory.IOT_OT_DEVICE),
  GATEWAY(AssetCategory.IOT_OT_DEVICE),
  POINT_OF_SALE(AssetCategory.IOT_OT_DEVICE),
  MEDIA_DEVICE(AssetCategory.IOT_OT_DEVICE),
  PLC(AssetCategory.IOT_OT_DEVICE),
  RTU(AssetCategory.IOT_OT_DEVICE),
  HMI(AssetCategory.IOT_OT_DEVICE),
  SCADA_HISTORIAN(AssetCategory.IOT_OT_DEVICE),
  MEDICAL_DEVICE(AssetCategory.IOT_OT_DEVICE),
  PRINTER_PERIPHERAL(AssetCategory.IOT_OT_DEVICE),
  BUILDING_MGMT(AssetCategory.IOT_OT_DEVICE),

  // IDENTITY
  USER_ACCOUNT(AssetCategory.IDENTITY),
  SERVICE_ACCOUNT(AssetCategory.IDENTITY),
  GROUP(AssetCategory.IDENTITY),
  ROLE(AssetCategory.IDENTITY),
  SHARED_MAILBOX(AssetCategory.IDENTITY),
  NON_HUMAN_IDENTITY(AssetCategory.IDENTITY),

  // SAAS_APPLICATION
  SAAS_APP(AssetCategory.SAAS_APPLICATION),
  SAAS_TENANT(AssetCategory.SAAS_APPLICATION),

  // AI_TARGET
  LLM_MODEL(AssetCategory.AI_TARGET),
  AI_AGENT(AssetCategory.AI_TARGET),
  MCP_SERVER(AssetCategory.AI_TARGET),
  RAG_PIPELINE(AssetCategory.AI_TARGET),

  // SECURITY_PLATFORM (mirrors SecurityPlatform.SECURITY_PLATFORM_TYPE)
  EDR(AssetCategory.SECURITY_PLATFORM),
  XDR(AssetCategory.SECURITY_PLATFORM),
  SIEM(AssetCategory.SECURITY_PLATFORM),
  SOAR(AssetCategory.SECURITY_PLATFORM),
  NDR(AssetCategory.SECURITY_PLATFORM),
  ISPM(AssetCategory.SECURITY_PLATFORM),
  EMAIL_SECURITY(AssetCategory.SECURITY_PLATFORM),
  LLM_FIREWALL(AssetCategory.SECURITY_PLATFORM),
  AI_GATEWAY(AssetCategory.SECURITY_PLATFORM),
  VULNERABILITY_SCANNER(AssetCategory.SECURITY_PLATFORM);

  private final AssetCategory category;

  AssetSubCategory(AssetCategory category) {
    this.category = category;
  }

  public AssetCategory getCategory() {
    return this.category;
  }

  /** Returns the subcategories that belong to the given category. */
  public static List<AssetSubCategory> forCategory(AssetCategory category) {
    return Arrays.stream(values()).filter(sub -> sub.category == category).toList();
  }

  /** True if the subcategory is a valid child of the given category. */
  public static boolean belongsTo(AssetSubCategory subCategory, AssetCategory category) {
    return subCategory != null && category != null && subCategory.category == category;
  }
}
