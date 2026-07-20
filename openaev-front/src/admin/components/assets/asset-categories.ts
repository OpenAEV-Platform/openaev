import { type EndpointInput } from '../../../utils/api-types';

// Types derived from the generated API types so the manifest stays in sync with the backend enums.
export type AssetCategory = NonNullable<EndpointInput['asset_category']>;
export type AssetSubCategory = NonNullable<EndpointInput['asset_subcategory']>;
export type CloudProvider = NonNullable<EndpointInput['asset_cloud_provider']>;
export type AssetCriticality = NonNullable<EndpointInput['asset_criticality']>;
export type Platform = EndpointInput['endpoint_platform'];

export type FieldVisibility = 'hidden' | 'optional' | 'required';

export interface AssetMetadataField {
  /** Key stored under asset_metadata. */
  key: string;
  label: string;
}

export interface AssetCategoryFields {
  platform: FieldVisibility;
  arch: FieldVisibility;
  hostname: FieldVisibility;
  url: FieldVisibility;
  ips: FieldVisibility;
  macAddresses: FieldVisibility;
  eol: boolean;
  internetFacing: boolean;
  /** Whether to render the dedicated cloud-resource section (provider / native type / region / ids). */
  cloud: boolean;
  /** Whether to render the linked-person picker (identity assets). */
  person: boolean;
  /** Extra free-form fields persisted into asset_metadata. */
  metadataFields: AssetMetadataField[];
}

export interface AssetCategoryDef {
  value: AssetCategory;
  label: string;
  description: string;
  /** Aligned security domain (matches PresetDomain). */
  domain: string;
  subcategories: AssetSubCategory[];
  subcategoryRequired: boolean;
  /** Restricts the platform dropdown for this category (defaults to all host platforms). */
  platformOptions?: Platform[];
  fields: AssetCategoryFields;
}

const HOST_PLATFORMS: Platform[] = [
  'Linux',
  'Windows',
  'MacOS',
  'Container',
  'Service',
  'Generic',
  'Internal',
  'Unknown',
];
const MOBILE_PLATFORMS: Platform[] = ['iOS', 'Android'];

export const ARCH_OPTIONS = ['x86_64', 'arm64', 'Unknown'] as const;
export const CRITICALITY_OPTIONS: AssetCriticality[] = [
  'VERY_HIGH',
  'HIGH',
  'MEDIUM',
  'LOW',
  'UNKNOWN',
];
export const CLOUD_PROVIDERS: CloudProvider[] = [
  'AWS',
  'AZURE',
  'GCP',
  'OCI',
  'ALIBABA',
  'KUBERNETES',
  'OTHER',
];

const noFields: AssetCategoryFields = {
  platform: 'hidden',
  arch: 'hidden',
  hostname: 'hidden',
  url: 'hidden',
  ips: 'hidden',
  macAddresses: 'hidden',
  eol: false,
  internetFacing: false,
  cloud: false,
  person: false,
  metadataFields: [],
};

export const ASSET_CATEGORY_DEFS: Record<string, AssetCategoryDef> = {
  HOST: {
    value: 'HOST',
    label: 'Host',
    description: 'Physical or virtual machine, server or workstation.',
    domain: 'Endpoint',
    subcategories: ['SERVER', 'WORKSTATION', 'LAPTOP', 'VIRTUAL_MACHINE', 'HYPERVISOR', 'MAINFRAME', 'THIN_CLIENT'],
    subcategoryRequired: false,
    platformOptions: HOST_PLATFORMS,
    fields: {
      ...noFields,
      platform: 'required',
      arch: 'required',
      hostname: 'optional',
      ips: 'optional',
      macAddresses: 'optional',
      eol: true,
      internetFacing: true,
    },
  },
  CONTAINER_WORKLOAD: {
    value: 'CONTAINER_WORKLOAD',
    label: 'Container / Workload',
    description: 'Container, image, Kubernetes object or serverless function.',
    domain: 'Endpoint',
    subcategories: ['CONTAINER', 'CONTAINER_IMAGE', 'KUBERNETES_POD', 'KUBERNETES_CLUSTER', 'KUBERNETES_NODE', 'SERVERLESS_FUNCTION'],
    subcategoryRequired: false,
    fields: {
      ...noFields,
      arch: 'optional',
      hostname: 'optional',
      ips: 'optional',
      internetFacing: true,
      metadataFields: [{
        key: 'image',
        label: 'Image',
      }],
    },
  },
  CLOUD_RESOURCE: {
    value: 'CLOUD_RESOURCE',
    label: 'Cloud resource',
    description: 'Compute, storage, database, network or serverless cloud resource.',
    domain: 'Cloud',
    subcategories: [
      'COMPUTE', 'STORAGE', 'DATABASE', 'NETWORKING', 'SERVERLESS', 'CONTAINER_REGISTRY',
      'KUBERNETES', 'IAM_PRINCIPAL', 'SECRETS_KEY_MGMT', 'MESSAGING_QUEUE', 'ANALYTICS_DATA',
      'AI_ML_SERVICE', 'IAC_TEMPLATE', 'CLOUD_OTHER',
    ],
    subcategoryRequired: true,
    fields: {
      ...noFields,
      url: 'optional',
      ips: 'optional',
      internetFacing: true,
      cloud: true,
    },
  },
  WEB_APPLICATION: {
    value: 'WEB_APPLICATION',
    label: 'Web application',
    description: 'Website, web API or web service reachable over HTTP(S).',
    domain: 'Web App',
    subcategories: ['WEBSITE', 'WEB_API', 'SINGLE_PAGE_APP', 'GRAPHQL_API', 'WEB_SERVICE', 'MICROSERVICE'],
    subcategoryRequired: false,
    fields: {
      ...noFields,
      url: 'required',
      hostname: 'optional',
      ips: 'optional',
      internetFacing: true,
    },
  },
  NETWORK_DEVICE: {
    value: 'NETWORK_DEVICE',
    label: 'Network device',
    description: 'Router, switch, firewall, load balancer or other network gear.',
    domain: 'Network',
    subcategories: [
      'ROUTER', 'SWITCH', 'FIREWALL', 'LOAD_BALANCER', 'VPN_GATEWAY', 'WIRELESS_AP',
      'PROXY', 'DNS_SERVER', 'DHCP_SERVER', 'SAN_NAS', 'NETWORK_OTHER',
    ],
    subcategoryRequired: false,
    fields: {
      ...noFields,
      hostname: 'optional',
      ips: 'required',
      macAddresses: 'optional',
      internetFacing: true,
      metadataFields: [{
        key: 'vendor',
        label: 'Vendor',
      }, {
        key: 'model',
        label: 'Model',
      }],
    },
  },
  MOBILE_DEVICE: {
    value: 'MOBILE_DEVICE',
    label: 'Mobile device',
    description: 'Smartphone or tablet (iOS / Android).',
    domain: 'Endpoint',
    subcategories: ['SMARTPHONE', 'TABLET'],
    subcategoryRequired: false,
    platformOptions: MOBILE_PLATFORMS,
    fields: {
      ...noFields,
      platform: 'required',
      ips: 'optional',
      macAddresses: 'optional',
      metadataFields: [{
        key: 'os_version',
        label: 'OS version',
      }],
    },
  },
  IOT_OT_DEVICE: {
    value: 'IOT_OT_DEVICE',
    label: 'IoT / OT device',
    description: 'IoT sensor, camera, PLC, SCADA or other operational technology.',
    domain: 'Network',
    subcategories: [
      'IOT_SENSOR', 'IP_CAMERA', 'GATEWAY', 'POINT_OF_SALE', 'MEDIA_DEVICE', 'PLC',
      'RTU', 'HMI', 'SCADA_HISTORIAN', 'MEDICAL_DEVICE', 'PRINTER_PERIPHERAL', 'BUILDING_MGMT',
    ],
    subcategoryRequired: false,
    fields: {
      ...noFields,
      ips: 'required',
      macAddresses: 'optional',
      metadataFields: [
        {
          key: 'vendor',
          label: 'Vendor',
        },
        {
          key: 'model',
          label: 'Model',
        },
        {
          key: 'protocol',
          label: 'Protocol',
        },
      ],
    },
  },
  IDENTITY: {
    value: 'IDENTITY',
    label: 'Identity',
    description: 'User, service account, group or role.',
    domain: 'Identity',
    subcategories: ['USER_ACCOUNT', 'SERVICE_ACCOUNT', 'GROUP', 'ROLE', 'SHARED_MAILBOX', 'NON_HUMAN_IDENTITY'],
    subcategoryRequired: false,
    fields: {
      ...noFields,
      person: true,
      metadataFields: [
        {
          key: 'identity_provider',
          label: 'Identity provider',
        },
        {
          key: 'principal',
          label: 'Principal / email',
        },
      ],
    },
  },
  SAAS_APPLICATION: {
    value: 'SAAS_APPLICATION',
    label: 'SaaS application',
    description: 'Software-as-a-Service application or tenant.',
    domain: 'Cloud',
    subcategories: ['SAAS_APP', 'SAAS_TENANT'],
    subcategoryRequired: false,
    fields: {
      ...noFields,
      url: 'optional',
      internetFacing: true,
      metadataFields: [{
        key: 'vendor',
        label: 'Vendor',
      }],
    },
  },
  AI_TARGET: {
    value: 'AI_TARGET',
    label: 'AI target',
    description: 'LLM endpoint, AI agent, MCP server or RAG pipeline to red-team.',
    domain: 'Artificial Intelligence',
    subcategories: ['LLM_MODEL', 'AI_AGENT', 'MCP_SERVER', 'RAG_PIPELINE'],
    subcategoryRequired: false,
    // AI targets are created through the dedicated AiTargetForm (provider / endpoint /
    // model / token), not the network-field carrier, so no generic fields are shown here.
    fields: { ...noFields },
  },
  GENERIC_ASSET: {
    value: 'GENERIC_ASSET',
    label: 'Generic asset',
    description: 'Anything that does not fit another category yet.',
    domain: 'To classify',
    subcategories: [],
    subcategoryRequired: false,
    fields: {
      ...noFields,
      hostname: 'optional',
      url: 'optional',
      ips: 'optional',
      internetFacing: true,
    },
  },
};

/** Categories that can be created/edited through the unified asset form (Endpoint carrier). */
export const ASSET_FORM_CATEGORIES: AssetCategory[] = [
  'HOST',
  'WEB_APPLICATION',
  'CLOUD_RESOURCE',
  'CONTAINER_WORKLOAD',
  'NETWORK_DEVICE',
  'MOBILE_DEVICE',
  'IOT_OT_DEVICE',
  'IDENTITY',
  'SAAS_APPLICATION',
  'AI_TARGET',
  'GENERIC_ASSET',
];

export const getCategoryDef = (category?: AssetCategory | null): AssetCategoryDef =>
  (category && ASSET_CATEGORY_DEFS[category]) || ASSET_CATEGORY_DEFS.GENERIC_ASSET;

const ACRONYMS = new Set([
  'IAM', 'DNS', 'DHCP', 'VPN', 'HMI', 'PLC', 'RTU', 'SCADA', 'IOT', 'OT', 'SAAS', 'API', 'URL',
  'EDR', 'XDR', 'SIEM', 'SOAR', 'NDR', 'ISPM', 'LLM', 'AI', 'MCP', 'RAG', 'SAN', 'NAS', 'AP',
  'POS', 'AWS', 'GCP', 'OCI', 'ID',
]);

const SPECIAL_LABELS: Record<string, string> = {
  GRAPHQL_API: 'GraphQL API',
  SINGLE_PAGE_APP: 'Single-page app',
  SAN_NAS: 'SAN / NAS',
  SECRETS_KEY_MGMT: 'Secrets / Key management',
  ANALYTICS_DATA: 'Analytics / Data',
  AI_ML_SERVICE: 'AI / ML service',
  IAC_TEMPLATE: 'IaC template',
  CLOUD_OTHER: 'Other',
  NETWORK_OTHER: 'Other',
  WIRELESS_AP: 'Wireless AP',
  POINT_OF_SALE: 'Point of sale',
  SCADA_HISTORIAN: 'SCADA / Historian',
  BUILDING_MGMT: 'Building management',
  NON_HUMAN_IDENTITY: 'Non-human identity',
  SAAS_APP: 'SaaS app',
  SAAS_TENANT: 'SaaS tenant',
};

/** Human-friendly label for an enum-style value (subcategory, criticality, provider). */
export const humanizeEnum = (value: string): string => {
  if (SPECIAL_LABELS[value]) {
    return SPECIAL_LABELS[value];
  }
  return value
    .split('_')
    .map(word => (ACRONYMS.has(word) ? word : word.charAt(0) + word.slice(1).toLowerCase()))
    .join(' ');
};

/** Common provider/service native types used to seed the free-text cloud native-type field. */
export const CLOUD_NATIVE_TYPE_SUGGESTIONS: Record<string, string[]> = {
  'AWS:COMPUTE': ['ec2_instance', 'ecs_task', 'eks_node', 'lightsail_instance'],
  'AWS:STORAGE': ['s3_bucket', 'ebs_volume', 'efs_filesystem'],
  'AWS:DATABASE': ['rds_instance', 'dynamodb_table', 'redshift_cluster', 'elasticache_cluster'],
  'AWS:NETWORKING': ['vpc', 'elb', 'alb', 'security_group', 'api_gateway'],
  'AWS:SERVERLESS': ['lambda_function', 'step_function'],
  'AWS:CONTAINER_REGISTRY': ['ecr_repository'],
  'AWS:KUBERNETES': ['eks_cluster'],
  'AWS:IAM_PRINCIPAL': ['iam_user', 'iam_role', 'iam_group'],
  'AWS:SECRETS_KEY_MGMT': ['secretsmanager_secret', 'kms_key', 'ssm_parameter'],
  'AWS:MESSAGING_QUEUE': ['sqs_queue', 'sns_topic', 'kinesis_stream'],
  'AZURE:COMPUTE': ['virtual_machine', 'vm_scale_set'],
  'AZURE:STORAGE': ['storage_account', 'blob_container', 'managed_disk'],
  'AZURE:DATABASE': ['sql_database', 'cosmos_db', 'postgresql_server'],
  'AZURE:NETWORKING': ['virtual_network', 'load_balancer', 'application_gateway', 'nsg'],
  'AZURE:SERVERLESS': ['function_app'],
  'AZURE:KUBERNETES': ['aks_cluster'],
  'AZURE:IAM_PRINCIPAL': ['service_principal', 'managed_identity'],
  'AZURE:SECRETS_KEY_MGMT': ['key_vault', 'key_vault_secret'],
  'GCP:COMPUTE': ['compute_instance', 'instance_group'],
  'GCP:STORAGE': ['gcs_bucket', 'persistent_disk'],
  'GCP:DATABASE': ['cloud_sql_instance', 'bigtable_instance', 'firestore'],
  'GCP:NETWORKING': ['vpc_network', 'load_balancer', 'firewall_rule'],
  'GCP:SERVERLESS': ['cloud_function', 'cloud_run_service'],
  'GCP:KUBERNETES': ['gke_cluster'],
  'GCP:IAM_PRINCIPAL': ['service_account', 'iam_role'],
  'GCP:SECRETS_KEY_MGMT': ['secret_manager_secret', 'kms_key'],
  'KUBERNETES:COMPUTE': ['node'],
  'KUBERNETES:KUBERNETES': ['cluster', 'namespace', 'deployment'],
};

export const getCloudNativeTypeSuggestions = (
  provider?: CloudProvider | null,
  subcategory?: AssetSubCategory | null,
): string[] => {
  if (!provider || !subcategory) {
    return [];
  }
  return CLOUD_NATIVE_TYPE_SUGGESTIONS[`${provider}:${subcategory}`] ?? [];
};
