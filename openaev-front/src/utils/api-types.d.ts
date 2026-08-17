/* eslint-disable */
/* tslint:disable */
// @ts-nocheck
/*
 * ---------------------------------------------------------------
 * ## THIS FILE WAS GENERATED VIA SWAGGER-TYPESCRIPT-API        ##
 * ##                                                           ##
 * ## AUTHOR: acacode                                           ##
 * ## SOURCE: https://github.com/acacode/swagger-typescript-api ##
 * ---------------------------------------------------------------
 */

type UtilRequiredKeys<T, K extends keyof T> = Omit<T, K> & Required<Pick<T, K>>;

export interface AdHocWidgetInput {
  pagination?: Pagination;
  parameters?: Record<string, string>;
  widget_config:
    | AverageConfiguration
    | DateHistogramWidget
    | FlatConfiguration
    | ListConfiguration
    | StructuralHistogramWidget;
}

export interface AdHocWidgetToEntitiesInput {
  /** Key-value pairs for filtering entities, where the key is the field name and the value is the filter criterion */
  filter_values_map?: Record<string, string[]>;
  /** Pagination for the widget */
  pagination?: Pagination;
  /** Additional parameters for the widget */
  parameters?: Record<string, string>;
  /**
   * The index of the series to filter by, if applicable, otherwise 0
   * @format int32
   */
  series_index?: number;
  /** The indexes of every series that produced the clicked number, ORed together. Takes precedence over series_index; use it whenever a widget displays a total spanning several series, so the drilled list resolves to exactly the documents that were counted */
  series_indexes?: number[];
  widget_config:
    | AverageConfiguration
    | DateHistogramWidget
    | FlatConfiguration
    | ListConfiguration
    | StructuralHistogramWidget;
  widget_type:
    | "vertical-barchart"
    | "horizontal-barchart"
    | "security-coverage"
    | "line"
    | "donut"
    | "list"
    | "attack-path"
    | "number"
    | "average"
    | "exposure-score"
    | "posture-radar"
    | "command-center"
    | "resilience-gauge";
}

export interface Agent {
  agent_active?: boolean;
  agent_asset: string;
  /** @format date-time */
  agent_cleared_at?: string;
  /** @format date-time */
  agent_created_at: string;
  agent_deployment_mode: "service" | "session";
  /** @minLength 1 */
  agent_executed_by_user: string;
  agent_executor?: string;
  agent_external_reference?: string;
  /** @minLength 1 */
  agent_id: string;
  agent_inject?: string;
  /** @format date-time */
  agent_last_seen?: string;
  agent_parent?: string;
  agent_privilege: "admin" | "standard";
  agent_process_name?: string;
  /** @format date-time */
  agent_updated_at: string;
  agent_version?: string;
  listened?: boolean;
}

export interface AgentCallInput {
  /** @minLength 1 */
  agent_slug: string;
  /** @minLength 1 */
  content: string;
  intent?: string;
}

export interface AgentCallOutput {
  content?: string;
  error?: string;
  status?: string;
}

export interface AgentExecutorOutput {
  /** Agent executor id */
  executor_id?: string;
  /** Agent executor name */
  executor_name?: string;
  /** Agent executor type */
  executor_type?: string;
}

export interface AgentOutput {
  /** Indicates whether the endpoint is active. The endpoint is considered active if it was seen in the last 3 minutes. */
  agent_active?: boolean;
  /** Agent deployment mode */
  agent_deployment_mode?: "service" | "session";
  /** The user who executed the agent */
  agent_executed_by_user?: string;
  /** Agent executor */
  agent_executor?: AgentExecutorOutput;
  /**
   * Agent id
   * @minLength 1
   */
  agent_id: string;
  /**
   * Instant when agent was last seen
   * @format date-time
   */
  agent_last_seen?: string;
  /** Agent privilege */
  agent_privilege?: "admin" | "standard";
  /** The version of the agent */
  agent_version?: string;
}

export interface AgentTarget {
  target_category?: string;
  target_detection_status?:
    | "FAILED"
    | "PENDING"
    | "PARTIAL"
    | "UNKNOWN"
    | "SUCCESS";
  target_execution_status?:
    | "FAILED"
    | "PENDING"
    | "PARTIAL"
    | "UNKNOWN"
    | "SUCCESS";
  target_human_response_status?:
    | "FAILED"
    | "PENDING"
    | "PARTIAL"
    | "UNKNOWN"
    | "SUCCESS";
  /** @minLength 1 */
  target_id: string;
  target_name?: string;
  target_prevention_status?:
    | "FAILED"
    | "PENDING"
    | "PARTIAL"
    | "UNKNOWN"
    | "SUCCESS";
  target_subtype?: string;
  /** @uniqueItems true */
  target_tags?: string[];
  target_type?: string;
  target_vulnerability_status?:
    | "FAILED"
    | "PENDING"
    | "PARTIAL"
    | "UNKNOWN"
    | "SUCCESS";
}

export interface AggregatedFindingOutput {
  /**
   * Asset groups linked to assets
   * @uniqueItems true
   */
  finding_asset_groups?: AssetGroupSimple[];
  /**
   * Assets linked to the finding (any asset type, not only endpoints)
   * @uniqueItems true
   */
  finding_assets: EndpointSimple[];
  /**
   * First time the finding was seen
   * @format date-time
   */
  finding_created_at: string;
  /**
   * Finding Id
   * @minLength 1
   */
  finding_id: string;
  /**
   * Represents the data type being extracted.
   * @example "text, number, port, portscan, ipv4, ipv6, credentials, cve"
   */
  finding_type:
    | "text"
    | "action_output"
    | "number"
    | "port"
    | "portscan"
    | "ipv4"
    | "ipv6"
    | "credentials"
    | "cve"
    | "username"
    | "email"
    | "share"
    | "file"
    | "admin_username"
    | "group"
    | "computer"
    | "password_policy"
    | "delegation"
    | "sid"
    | "vulnerability"
    | "account_with_password_not_required"
    | "asreproastable_account"
    | "kerberoastable_account"
    | "expectation_signature";
  /**
   * Last time the finding was seen
   * @format date-time
   */
  finding_updated_at: string;
  /**
   * Finding Value
   * @minLength 1
   */
  finding_value: string;
}

export interface AiAttack {
  ai_attack_category?: string;
  ai_attack_content?: string;
  ai_attack_converters?: string[];
  ai_attack_engine: "native" | "garak" | "pyrit" | "promptfoo";
  ai_attack_multi_turn?: Record<string, any>;
  ai_attack_success_detector?: Record<string, any>;
  listened?: boolean;
  payload_arguments?: PayloadArgument[];
  /** Organization author of the payload */
  payload_author_organization?: string;
  /** Team author of the payload */
  payload_author_team?: string;
  /** User author of the payload */
  payload_author_user?: string;
  payload_cleanup_command?: string;
  payload_cleanup_executor?: string;
  payload_collector_type?: string;
  /** @format date-time */
  payload_created_at: string;
  payload_description?: string;
  payload_detection_remediations?: DetectionRemediation[];
  payload_elevation_required?: boolean;
  payload_execution_arch: "x86_64" | "arm64" | "ALL_ARCHITECTURES";
  payload_expectations?: (
    | "ARTICLE"
    | "CHALLENGE"
    | "MANUAL"
    | "PREVENTION"
    | "DETECTION"
    | "VULNERABILITY"
  )[];
  payload_expected_security_platforms?: Record<
    string,
    (
      | "EDR"
      | "XDR"
      | "SIEM"
      | "SOAR"
      | "NDR"
      | "ISPM"
      | "EMAIL_SECURITY"
      | "LLM_FIREWALL"
      | "AI_GATEWAY"
      | "VULNERABILITY_SCANNER"
    )[]
  >;
  payload_external_id?: string;
  /** @minLength 1 */
  payload_id: string;
  /** @minLength 1 */
  payload_name: string;
  /** @uniqueItems true */
  payload_output_parsers?: OutputParser[];
  /** @minItems 1 */
  payload_platforms: (
    | "Linux"
    | "Windows"
    | "MacOS"
    | "Android"
    | "iOS"
    | "Container"
    | "Service"
    | "Generic"
    | "Internal"
    | "Unknown"
  )[];
  payload_prerequisites?: PayloadPrerequisite[];
  payload_source: "COMMUNITY" | "FILIGRAN" | "MANUAL";
  payload_status: "UNVERIFIED" | "VERIFIED" | "DEPRECATED";
  payload_type?: string;
  /** @format date-time */
  payload_updated_at: string;
  typeEnum?:
    | "COMMAND"
    | "EXECUTABLE"
    | "FILE_DROP"
    | "DNS_RESOLUTION"
    | "NETWORK_TRAFFIC"
    | "AI_ATTACK";
}

export interface AiGenericTextInput {
  /** @minLength 1 */
  ai_content: string;
  ai_format?: string;
  ai_tone?: string;
}

export interface AiMediaInput {
  ai_author?: string;
  ai_context?: string;
  /** @minLength 1 */
  ai_format: string;
  /** @minLength 1 */
  ai_input: string;
  /** @format int32 */
  ai_paragraphs?: number;
  ai_tone?: string;
}

export interface AiMessageInput {
  ai_context?: string;
  /** @minLength 1 */
  ai_format: string;
  /** @minLength 1 */
  ai_input: string;
  /** @format int32 */
  ai_paragraphs?: number;
  ai_recipient?: string;
  ai_sender?: string;
  ai_tone?: string;
}

export interface AiResult {
  chunk_content?: string;
  chunk_id?: string;
}

export interface AiTargetInput {
  ai_target_configuration?: Record<string, any>;
  ai_target_endpoint?: string | null;
  ai_target_modality?: "TEXT" | "VISION" | "AUDIO" | "MULTIMODAL";
  ai_target_model?: string | null;
  ai_target_provider:
    | "OPENAI_COMPATIBLE"
    | "ANTHROPIC"
    | "AZURE_OPENAI"
    | "AWS_BEDROCK"
    | "GOOGLE_VERTEX"
    | "HUGGINGFACE"
    | "OLLAMA"
    | "CUSTOM_HTTP"
    | "MCP_SERVER"
    | "AGENT_HTTP"
    | "XTM_ONE";
  ai_target_system_prompt?: string | null;
  ai_target_token?: string | null;
  asset_criticality?: "VERY_HIGH" | "HIGH" | "MEDIUM" | "LOW" | "UNKNOWN";
  asset_description?: string;
  asset_external_reference?: string;
  /** @minLength 1 */
  asset_name: string;
  asset_tags?: string[];
}

export interface AiTargetTarget {
  target_category?: string;
  target_detection_status?:
    | "FAILED"
    | "PENDING"
    | "PARTIAL"
    | "UNKNOWN"
    | "SUCCESS";
  target_execution_status?:
    | "FAILED"
    | "PENDING"
    | "PARTIAL"
    | "UNKNOWN"
    | "SUCCESS";
  target_human_response_status?:
    | "FAILED"
    | "PENDING"
    | "PARTIAL"
    | "UNKNOWN"
    | "SUCCESS";
  /** @minLength 1 */
  target_id: string;
  target_name?: string;
  target_prevention_status?:
    | "FAILED"
    | "PENDING"
    | "PARTIAL"
    | "UNKNOWN"
    | "SUCCESS";
  target_subtype?: string;
  /** @uniqueItems true */
  target_tags?: string[];
  target_type?: string;
  target_vulnerability_status?:
    | "FAILED"
    | "PENDING"
    | "PARTIAL"
    | "UNKNOWN"
    | "SUCCESS";
}

/** Installed arsenal + delivery substrate for custom actions */
export interface ArsenalInventory {
  /**
   * Number of active agents/implants (last seen within the active window)
   * @format int32
   */
  active_agent_count?: number;
  /** Deterministic one-line recommendation on how to obtain a substrate */
  advice?: string;
  /** True when at least one agent/implant is active - i.e. a Command payload has a host to execute on */
  command_delivery_available?: boolean;
  /** True when an HTTP-request-capable injector is installed and active - i.e. a raw HTTP exploit payload (SQLi, SSRF, path traversal, auth bypass) has somewhere to run */
  http_delivery_available?: boolean;
  /** Injectors registered in this tenant's catalog */
  installed_injectors?: InstalledInjector[];
}

export interface Article {
  article_author?: string;
  article_channel: string;
  /** @format int32 */
  article_comments?: number;
  article_content?: string;
  /** @format date-time */
  article_created_at: string;
  article_documents?: string[];
  article_exercise?: string;
  /** @minLength 1 */
  article_id: string;
  article_is_scheduled?: boolean;
  /** @format int32 */
  article_likes?: number;
  article_name?: string;
  article_scenario?: string;
  /** @format int32 */
  article_shares?: number;
  /** @format date-time */
  article_updated_at: string;
  /** @format date-time */
  article_virtual_publication?: string;
  listened?: boolean;
}

export interface ArticleCreateInput {
  article_author?: string;
  /** @minLength 1 */
  article_channel: string;
  /** @format int32 */
  article_comments?: number;
  article_content?: string;
  article_documents?: string[];
  /** @format int32 */
  article_likes?: number;
  /** @minLength 1 */
  article_name: string;
  article_published?: boolean;
  /** @format int32 */
  article_shares?: number;
}

export interface ArticleUpdateInput {
  article_author?: string;
  /** @minLength 1 */
  article_channel: string;
  /** @format int32 */
  article_comments?: number;
  article_content?: string;
  article_documents?: string[];
  /** @format int32 */
  article_likes?: number;
  /** @minLength 1 */
  article_name: string;
  article_published?: boolean;
  /** @format int32 */
  article_shares?: number;
}

export interface Asset {
  ai_target_configuration?: Record<string, any>;
  ai_target_endpoint?: string;
  ai_target_modality?: "TEXT" | "VISION" | "AUDIO" | "MULTIMODAL";
  ai_target_model?: string;
  ai_target_provider?:
    | "OPENAI_COMPATIBLE"
    | "ANTHROPIC"
    | "AZURE_OPENAI"
    | "AWS_BEDROCK"
    | "GOOGLE_VERTEX"
    | "HUGGINGFACE"
    | "OLLAMA"
    | "CUSTOM_HTTP"
    | "MCP_SERVER"
    | "AGENT_HTTP"
    | "XTM_ONE";
  ai_target_system_prompt?: string;
  ai_target_token?: string;
  asset_category?:
    | "HOST"
    | "CONTAINER_WORKLOAD"
    | "CLOUD_RESOURCE"
    | "WEB_APPLICATION"
    | "NETWORK_DEVICE"
    | "MOBILE_DEVICE"
    | "IOT_OT_DEVICE"
    | "IDENTITY"
    | "SAAS_APPLICATION"
    | "AI_TARGET"
    | "SECURITY_PLATFORM"
    | "GENERIC_ASSET";
  asset_cloud_native_type?: string;
  asset_cloud_provider?:
    | "AWS"
    | "AZURE"
    | "GCP"
    | "OCI"
    | "ALIBABA"
    | "KUBERNETES"
    | "OTHER";
  asset_cloud_region?: string;
  /** @format date-time */
  asset_created_at: string;
  asset_criticality?: "VERY_HIGH" | "HIGH" | "MEDIUM" | "LOW" | "UNKNOWN";
  asset_description?: string;
  asset_external_reference?: string;
  asset_hostname?: string;
  /** @minLength 1 */
  asset_id: string;
  asset_internet_facing?: boolean;
  asset_ips?: string[];
  asset_linked_person?: string;
  asset_mac_addresses?: string[];
  asset_metadata?: Record<string, any>;
  /** @minLength 1 */
  asset_name: string;
  asset_seen_ip?: string;
  /** Activity status derived from agents (ACTIVE / INACTIVE / AGENTLESS) */
  asset_status?: "ACTIVE" | "INACTIVE" | "AGENTLESS";
  asset_subcategory?:
    | "SERVER"
    | "WORKSTATION"
    | "LAPTOP"
    | "VIRTUAL_MACHINE"
    | "HYPERVISOR"
    | "MAINFRAME"
    | "THIN_CLIENT"
    | "CONTAINER"
    | "CONTAINER_IMAGE"
    | "KUBERNETES_POD"
    | "KUBERNETES_CLUSTER"
    | "KUBERNETES_NODE"
    | "SERVERLESS_FUNCTION"
    | "COMPUTE"
    | "STORAGE"
    | "DATABASE"
    | "NETWORKING"
    | "SERVERLESS"
    | "CONTAINER_REGISTRY"
    | "KUBERNETES"
    | "IAM_PRINCIPAL"
    | "SECRETS_KEY_MGMT"
    | "MESSAGING_QUEUE"
    | "ANALYTICS_DATA"
    | "AI_ML_SERVICE"
    | "IAC_TEMPLATE"
    | "CLOUD_OTHER"
    | "WEBSITE"
    | "WEB_API"
    | "SINGLE_PAGE_APP"
    | "GRAPHQL_API"
    | "WEB_SERVICE"
    | "MICROSERVICE"
    | "ROUTER"
    | "SWITCH"
    | "FIREWALL"
    | "LOAD_BALANCER"
    | "VPN_GATEWAY"
    | "WIRELESS_AP"
    | "PROXY"
    | "DNS_SERVER"
    | "DHCP_SERVER"
    | "SAN_NAS"
    | "NETWORK_OTHER"
    | "SMARTPHONE"
    | "TABLET"
    | "IOT_SENSOR"
    | "IP_CAMERA"
    | "GATEWAY"
    | "POINT_OF_SALE"
    | "MEDIA_DEVICE"
    | "PLC"
    | "RTU"
    | "HMI"
    | "SCADA_HISTORIAN"
    | "MEDICAL_DEVICE"
    | "PRINTER_PERIPHERAL"
    | "BUILDING_MGMT"
    | "USER_ACCOUNT"
    | "SERVICE_ACCOUNT"
    | "GROUP"
    | "ROLE"
    | "SHARED_MAILBOX"
    | "NON_HUMAN_IDENTITY"
    | "SAAS_APP"
    | "SAAS_TENANT"
    | "LLM_MODEL"
    | "AI_AGENT"
    | "MCP_SERVER"
    | "RAG_PIPELINE"
    | "EDR"
    | "XDR"
    | "SIEM"
    | "SOAR"
    | "NDR"
    | "ISPM"
    | "EMAIL_SECURITY"
    | "LLM_FIREWALL"
    | "AI_GATEWAY"
    | "VULNERABILITY_SCANNER";
  asset_tags?: string[];
  asset_type?: string;
  /** @format date-time */
  asset_updated_at: string;
  asset_url?: string;
  listened?: boolean;
}

export interface AssetAgentJob {
  asset_agent_agent?: string;
  /** @deprecated */
  asset_agent_asset?: string;
  /** @minLength 1 */
  asset_agent_command: string;
  /** @minLength 1 */
  asset_agent_id: string;
  asset_agent_inject?: string;
  listened?: boolean;
}

export interface AssetBulkProcessingInput {
  asset_ids_to_ignore?: string[];
  asset_ids_to_process?: string[];
  search_pagination_input?: SearchPaginationInput;
}

export interface AssetGroup {
  asset_group_assets?: string[];
  /** @format date-time */
  asset_group_created_at: string;
  asset_group_description?: string;
  asset_group_dynamic_assets?: string[];
  asset_group_dynamic_filter: FilterGroup;
  asset_group_external_reference?: string;
  /** @minLength 1 */
  asset_group_id: string;
  /** @minLength 1 */
  asset_group_name: string;
  asset_group_tags?: string[];
  /** @format date-time */
  asset_group_updated_at: string;
  listened?: boolean;
}

export interface AssetGroupBulkProcessingInput {
  asset_group_ids_to_ignore?: string[];
  asset_group_ids_to_process?: string[];
  search_pagination_input?: SearchPaginationInput;
}

export interface AssetGroupInput {
  asset_group_description?: string;
  asset_group_dynamic_filter?: FilterGroup;
  /** @minLength 1 */
  asset_group_name: string;
  asset_group_tags?: string[];
}

export interface AssetGroupOutput {
  /** @uniqueItems true */
  asset_group_assets?: string[];
  asset_group_description?: string;
  asset_group_dynamic_filter?: FilterGroup;
  /** @minLength 1 */
  asset_group_id: string;
  /** @minLength 1 */
  asset_group_name: string;
  /** @uniqueItems true */
  asset_group_tags?: string[];
}

export interface AssetGroupSimple {
  /**
   * Asset group Id
   * @minLength 1
   */
  asset_group_id: string;
  /**
   * Asset group Name
   * @minLength 1
   */
  asset_group_name: string;
}

export interface AssetGroupTarget {
  target_category?: string;
  target_detection_status?:
    | "FAILED"
    | "PENDING"
    | "PARTIAL"
    | "UNKNOWN"
    | "SUCCESS";
  target_execution_status?:
    | "FAILED"
    | "PENDING"
    | "PARTIAL"
    | "UNKNOWN"
    | "SUCCESS";
  target_human_response_status?:
    | "FAILED"
    | "PENDING"
    | "PARTIAL"
    | "UNKNOWN"
    | "SUCCESS";
  /** @minLength 1 */
  target_id: string;
  target_name?: string;
  target_prevention_status?:
    | "FAILED"
    | "PENDING"
    | "PARTIAL"
    | "UNKNOWN"
    | "SUCCESS";
  target_subtype?: string;
  /** @uniqueItems true */
  target_tags?: string[];
  target_type?: string;
  target_vulnerability_status?:
    | "FAILED"
    | "PENDING"
    | "PARTIAL"
    | "UNKNOWN"
    | "SUCCESS";
}

export interface AssetOptionOutput {
  /** Product-facing asset category, used to group options in pickers */
  category?: string;
  /** Asset id */
  id?: string;
  /** Asset name */
  label?: string;
}

export interface AssetOutput {
  /** AI target provider (AI targets only) */
  ai_target_provider?:
    | "OPENAI_COMPATIBLE"
    | "ANTHROPIC"
    | "AZURE_OPENAI"
    | "AWS_BEDROCK"
    | "GOOGLE_VERTEX"
    | "HUGGINGFACE"
    | "OLLAMA"
    | "CUSTOM_HTTP"
    | "MCP_SERVER"
    | "AGENT_HTTP"
    | "XTM_ONE";
  /** Asset category */
  asset_category?:
    | "HOST"
    | "CONTAINER_WORKLOAD"
    | "CLOUD_RESOURCE"
    | "WEB_APPLICATION"
    | "NETWORK_DEVICE"
    | "MOBILE_DEVICE"
    | "IOT_OT_DEVICE"
    | "IDENTITY"
    | "SAAS_APPLICATION"
    | "AI_TARGET"
    | "SECURITY_PLATFORM"
    | "GENERIC_ASSET";
  /** Asset criticality */
  asset_criticality?: "VERY_HIGH" | "HIGH" | "MEDIUM" | "LOW" | "UNKNOWN";
  /** Hostname (network-reachable assets) */
  asset_hostname?: string;
  /**
   * Asset Id
   * @minLength 1
   */
  asset_id: string;
  /**
   * Asset name
   * @minLength 1
   */
  asset_name: string;
  /** Asset subcategory */
  asset_subcategory?:
    | "SERVER"
    | "WORKSTATION"
    | "LAPTOP"
    | "VIRTUAL_MACHINE"
    | "HYPERVISOR"
    | "MAINFRAME"
    | "THIN_CLIENT"
    | "CONTAINER"
    | "CONTAINER_IMAGE"
    | "KUBERNETES_POD"
    | "KUBERNETES_CLUSTER"
    | "KUBERNETES_NODE"
    | "SERVERLESS_FUNCTION"
    | "COMPUTE"
    | "STORAGE"
    | "DATABASE"
    | "NETWORKING"
    | "SERVERLESS"
    | "CONTAINER_REGISTRY"
    | "KUBERNETES"
    | "IAM_PRINCIPAL"
    | "SECRETS_KEY_MGMT"
    | "MESSAGING_QUEUE"
    | "ANALYTICS_DATA"
    | "AI_ML_SERVICE"
    | "IAC_TEMPLATE"
    | "CLOUD_OTHER"
    | "WEBSITE"
    | "WEB_API"
    | "SINGLE_PAGE_APP"
    | "GRAPHQL_API"
    | "WEB_SERVICE"
    | "MICROSERVICE"
    | "ROUTER"
    | "SWITCH"
    | "FIREWALL"
    | "LOAD_BALANCER"
    | "VPN_GATEWAY"
    | "WIRELESS_AP"
    | "PROXY"
    | "DNS_SERVER"
    | "DHCP_SERVER"
    | "SAN_NAS"
    | "NETWORK_OTHER"
    | "SMARTPHONE"
    | "TABLET"
    | "IOT_SENSOR"
    | "IP_CAMERA"
    | "GATEWAY"
    | "POINT_OF_SALE"
    | "MEDIA_DEVICE"
    | "PLC"
    | "RTU"
    | "HMI"
    | "SCADA_HISTORIAN"
    | "MEDICAL_DEVICE"
    | "PRINTER_PERIPHERAL"
    | "BUILDING_MGMT"
    | "USER_ACCOUNT"
    | "SERVICE_ACCOUNT"
    | "GROUP"
    | "ROLE"
    | "SHARED_MAILBOX"
    | "NON_HUMAN_IDENTITY"
    | "SAAS_APP"
    | "SAAS_TENANT"
    | "LLM_MODEL"
    | "AI_AGENT"
    | "MCP_SERVER"
    | "RAG_PIPELINE"
    | "EDR"
    | "XDR"
    | "SIEM"
    | "SOAR"
    | "NDR"
    | "ISPM"
    | "EMAIL_SECURITY"
    | "LLM_FIREWALL"
    | "AI_GATEWAY"
    | "VULNERABILITY_SCANNER";
  /**
   * Tags
   * @uniqueItems true
   */
  asset_tags?: string[];
  /** Asset type discriminator (Asset / Endpoint / SecurityPlatform) */
  asset_type?: string;
  /** Platform (endpoints only) */
  endpoint_platform?:
    | "Linux"
    | "Windows"
    | "MacOS"
    | "Android"
    | "iOS"
    | "Container"
    | "Service"
    | "Generic"
    | "Internal"
    | "Unknown";
  /** Whether the asset belongs to the asset group statically or dynamically */
  is_static?: boolean;
}

/** Frozen asset composing a launched simulation's scope rule (display only). */
export interface AssetSnapshotOutput {
  /**
   * Frozen number of agents on the asset.
   * @format int32
   */
  asset_snapshot_agents_count?: number;
  /** Frozen distinct executor types of the asset's agents. */
  asset_snapshot_executors?: string[];
  /** Frozen asset id. */
  asset_snapshot_id?: string;
  /** Frozen asset name. */
  asset_snapshot_name?: string;
}

export interface AtomicInjectorContractOutput {
  convertedContent?: object;
  /** @minLength 1 */
  injector_contract_content: string;
  injector_contract_domains?: string[];
  /** @minLength 1 */
  injector_contract_id: string;
  injector_contract_labels: Record<string, string>;
  injector_contract_payload?: PayloadSimple;
  injector_contract_platforms?: (
    | "Linux"
    | "Windows"
    | "MacOS"
    | "Android"
    | "iOS"
    | "Container"
    | "Service"
    | "Generic"
    | "Internal"
    | "Unknown"
  )[];
}

export interface AtomicTestingInput {
  inject_all_teams?: boolean;
  inject_asset_groups?: string[];
  inject_assets?: string[];
  inject_content?: object;
  inject_description?: string;
  inject_documents?: InjectDocumentInput[];
  inject_injector?: string;
  inject_injector_contract?: string;
  inject_tags?: string[];
  inject_teams?: string[];
  /** @minLength 1 */
  inject_title: string;
}

export interface AtomicTestingUpdateTagsInput {
  atomic_tags?: string[];
}

export interface AttackPathAlertDTO {
  date?: string;
  id?: string;
  link?: string;
  title?: string;
}

export interface AttackPathAttackPatternDTO {
  externalId?: string;
  name?: string;
}

export interface AttackPathCausalSeedResultDTO {
  simulationId?: string;
}

export interface AttackPathCounters {
  /** @format int64 */
  credentials?: number;
  /** @format int64 */
  cves?: number;
  /** @format int64 */
  endpoints?: number;
  /** @format int64 */
  files?: number;
  /** @format int64 */
  ports?: number;
  /** @format int64 */
  shares?: number;
  /** @format int64 */
  users?: number;
}

export interface AttackPathDTO {
  attackPathEdges?: AttackPathEdges[];
  attackPathExecutions?: AttackPathNodeDTO[];
  attackPathNodes?: AttackPathNodeDTO[];
  counters?: AttackPathCounters;
  /** @format int64 */
  graphVersion?: number;
  mode?: string;
  staticAttackPathFindings?: AttackPathNodeDTO[];
}

export interface AttackPathDeltaDTO {
  attackPathEdges?: AttackPathEdges[];
  attackPathExecutions?: AttackPathNodeDTO[];
  attackPathNodes?: AttackPathNodeDTO[];
  counters?: AttackPathCounters;
  /** @format int64 */
  newVersion?: number;
  resyncRequired?: boolean;
  /** @format int64 */
  sinceVersion?: number;
  staticAttackPathFindings?: AttackPathNodeDTO[];
}

export interface AttackPathEdges {
  /** @format int32 */
  count?: number;
  edgeId?: string;
  edgeSourceId?: string;
  edgeTargetId?: string;
  executionIds?: string[];
  label?: string;
  type?: string;
}

export interface AttackPathEndpointRelationsDTO {
  edges?: AttackPathEdges[];
  executions?: AttackPathNodeDTO[];
  /** @format int64 */
  totalExecutions?: number;
}

export interface AttackPathExecutionDetailDTO {
  agentName?: string;
  agentPrivilege?: string;
  attackPatterns?: AttackPathAttackPatternDTO[];
  command?: string;
  detectionRemediations?: DetectionRemediationOutput[];
  detectionStatus?: string;
  endpointKey?: string;
  executedAt?: string;
  executionStatus?: string;
  findings?: AttackPathExecutionFindingItemDTO[];
  injectId?: string;
  injectorCommandLine?: string;
  payloadId?: string;
  payloadName?: string;
  preventionStatus?: string;
  securityPlatforms?: AttackPathSecurityPlatformDTO[];
  stepId?: string;
  targetHostname?: string;
  targetIp?: string;
  targetPlatform?: string;
  terminalOutput?: string;
  vulnerabilityStatus?: string;
}

export interface AttackPathExecutionFindingItemDTO {
  type?: string;
  value?: string;
  verdicts?: AttackPathFindingVerdictsDTO;
}

export interface AttackPathExpandDTO {
  findingTypes?: AttackPathNodeDTO[];
  findings?: AttackPathNodeDTO[];
}

export interface AttackPathFindingItemDTO {
  endpointKey?: string;
  endpointNodeId?: string;
  executionIds?: string[];
  type?: string;
  value?: string;
  verdicts?: AttackPathFindingVerdictsDTO;
}

export interface AttackPathFindingPageDTO {
  items?: AttackPathFindingItemDTO[];
  /** @format int64 */
  total?: number;
}

export interface AttackPathFindingVerdictsDTO {
  detection?: string;
  prevention?: string;
  vulnerability?: string;
}

export interface AttackPathNodeDTO {
  agentName?: string;
  agents?: string[];
  arguments?: any[];
  assetNodeId?: string;
  attackPatterns?: AttackPathAttackPatternDTO[];
  command?: string;
  consumedFindingKeys?: ConsumedFindingKeyDTO[];
  contractName?: string;
  criticality?: string;
  dependsOn?: string[];
  entityKind?: string;
  executedAt?: string;
  executionStatus?: string;
  executionsTraces?: any[];
  expectations?: any[];
  findingCounts?: Record<string, number>;
  findingsNodeIds?: string[];
  findingsTypeNodeId?: string;
  hostname?: string;
  id?: string;
  injectId?: string;
  injectorType?: string;
  ip?: string;
  isFinding?: boolean;
  label?: string;
  payloadCollectorType?: string;
  payloadId?: string;
  payloadName?: string;
  payloadType?: string;
  platform?: string;
  privilege?: string;
  ref?: string;
  seenIp?: string;
  status?: string;
  stepTemplateId?: string;
  type?: string;
  typeFindings?: string;
  value?: string;
  verdicts?: AttackPathFindingVerdictsDTO;
}

export interface AttackPathReplayStepDTO {
  done?: boolean;
  label?: string;
  /** @format int32 */
  stage?: number;
  /** @format int32 */
  totalStages?: number;
}

export interface AttackPathSecurityPlatformDTO {
  alerts?: AttackPathAlertDTO[];
  /** @format int32 */
  alertsCount?: number;
  bucket?: string;
  detectedAt?: string;
  platformName?: string;
  platformType?: string;
  resultLabel?: string;
  /** @format double */
  score?: number;
  sourceAssetId?: string;
  sourceId?: string;
  status?: string;
}

export interface AttackPathSeedInput {
  preset?: string;
  /** @format int64 */
  seed?: number;
  tenantId?: string;
}

export interface AttackPathSeedResultDTO {
  /** @format int64 */
  elapsedMs?: number;
  /** @format int64 */
  executions?: number;
  /** @format int64 */
  findings?: number;
  /** @format int64 */
  simulations?: number;
}

export interface AttackPathSimSummaryRow {
  /** @format int64 */
  endpointCount?: number;
  /** @format int64 */
  executionCount?: number;
  simulationId?: string;
}

export interface AttackPattern {
  /** @format date-time */
  attack_pattern_created_at?: string;
  attack_pattern_description?: string;
  /** @minLength 1 */
  attack_pattern_external_id: string;
  /** @minLength 1 */
  attack_pattern_id: string;
  attack_pattern_kill_chain_phases?: string[];
  /** @minLength 1 */
  attack_pattern_name: string;
  attack_pattern_parent?: string;
  attack_pattern_permissions_required?: string[];
  attack_pattern_platforms?: string[];
  /** @minLength 1 */
  attack_pattern_stix_id: string;
  /** @format date-time */
  attack_pattern_updated_at?: string;
  listened?: boolean;
}

export interface AttackPatternCoverageOutput {
  attack_pattern_external_id?: string;
  attack_pattern_id?: string;
  attack_pattern_name?: string;
  /** @format int64 */
  detection_success?: number;
  /** @format int64 */
  detection_total?: number;
  kill_chain_phases?: KillChainPhaseCoverage[];
  /** @format int64 */
  prevention_success?: number;
  /** @format int64 */
  prevention_total?: number;
}

export interface AttackPatternCreateInput {
  attack_pattern_description?: string;
  /** @minLength 1 */
  attack_pattern_external_id: string;
  attack_pattern_kill_chain_phases?: string[];
  /** @minLength 1 */
  attack_pattern_name: string;
  attack_pattern_parent?: string;
  attack_pattern_permissions_required?: string[];
  attack_pattern_platforms?: string[];
  attack_pattern_stix_id?: string;
}

export interface AttackPatternSimple {
  /** @minLength 1 */
  attack_pattern_external_id: string;
  /** @minLength 1 */
  attack_pattern_id: string;
  /** @minLength 1 */
  attack_pattern_name: string;
}

export interface AttackPatternUpdateInput {
  attack_pattern_description?: string;
  /** @minLength 1 */
  attack_pattern_external_id: string;
  attack_pattern_kill_chain_phases?: string[];
  /** @minLength 1 */
  attack_pattern_name: string;
}

export interface AttackPatternUpsertInput {
  attack_patterns?: AttackPatternCreateInput[];
  ignore_dependencies?: boolean;
}

/** A chained inject step appended to a live autonomous attack path */
export interface AutonomousAttackPathStepInput {
  inject: InjectInput;
  /** Optional step template id this step depends on (DEPEND_ON), for pure ordering. Null / omitted for a seed or a finding-driven step. Prefer 'trigger' over this. */
  parent_step_template_id?: string;
  /** Optional finding-driven trigger: the finding(s) this step reacts to and the finding values it consumes as inputs. Preferred over parent_step_template_id - it lets the attack path draw itself. Omit for a seed step (recon that runs first). */
  trigger?: AutonomousStepTrigger;
}

/** Result of appending a chained attack-path step */
export interface AutonomousAttackPathStepResult {
  /** Id of the created step template */
  step_template_id?: string;
}

/** Live state of one authored attack-path step */
export interface AutonomousAttackPathStepState {
  /** Human-readable name of the finding EVENT this step reacts to (the trigger root's name, e.g. "SMB service exposed"), or null when the step has no finding trigger (a seed or a pure DEPEND_ON step). Mirror of the trigger's event_name on the write side. */
  event_name?: string;
  /** Id of the inject backing this step (empty until the step has executed) */
  inject_id?: string;
  /** Id of the injector contract the step runs, when resolvable */
  injector_contract_id?: string;
  /** Pure-ordering parent: the step template id this step runs AFTER (its DEPEND_ON parent), or null when the step is a seed or is wired finding-driven via its trigger. Prefer reading the trigger fields below to understand WHY a step fires; this is only the ordering fallback, not the primary wiring. */
  parent_step_template_id?: string;
  /** Execution status: PENDING when never started, otherwise the live ExecutionStatus (QUEUING, EXECUTING, SUCCESS, ERROR, ...) */
  status?: string;
  /** Stable authoring handle for this step (the chaining step template id). Pass it as parent_step_template_id to chain a follow-on step onto it, or to update/replace this exact step in place instead of re-authoring a duplicate. */
  step_template_id?: string;
  /** Resolved target of the step (teams / assets / asset groups, or 'inherits run scope' when it binds to the run's allow-list). */
  target?: string;
  /** Human-readable step title */
  title?: string;
  /** Execution traces (action/status: message) captured while the step ran */
  traces?: string[];
  /** The finding predicates that make this step fire, each rendered as "<key> <operator> <value>" (e.g. "port EQ 445", "service IS_NOT_NULL"). Empty when the step is a seed or a pure DEPEND_ON step. This is the read-back of the trigger's filters, so a reader can see - and correct - exactly what the step listens for instead of inferring a linear chain. */
  trigger_filters?: string[];
  /** The finding values this step binds into its inject inputs, each rendered as "<key> -> <input>" (e.g. "ipv4 -> target_host"). Empty when the step consumes no finding values. This is the read-back of the trigger's mappings. */
  trigger_mappings?: string[];
  /** Inject type (injector) of the step */
  type?: string;
}

/** How to convert an autonomous scenario into a manual chained scenario */
export interface AutonomousConvertToManualInput {
  /** DUPLICATE creates a new manual chained scenario from a copy and leaves the AI run untouched; IN_PLACE turns this scenario manual for good (irreversible). */
  mode: "DUPLICATE" | "IN_PLACE";
}

/** Tenant default additional agents for autonomous runs */
export interface AutonomousDefaultAgentsInput {
  /** XTM One agent ids to consult by default. Empty clears the default. */
  agent_ids?: string[];
  /** Default discovery mode per agent id (EXISTING_ONLY / SCOPED / EXPANSIVE): how much latitude the agent has to create new assets / findings / persons from recon on the fly. Agents omitted here default to SCOPED. */
  agent_modes?: Record<string, string>;
}

/** Tenant default additional agents + per-agent discovery modes */
export interface AutonomousDefaultAgentsOutput {
  /** XTM One agent ids consulted by default. */
  agent_ids?: string[];
  /** Default discovery mode per agent id (EXISTING_ONLY / SCOPED / EXPANSIVE). */
  agent_modes?: Record<string, string>;
}

export interface AutonomousDirective {
  /**
   * When the orchestrator consumed the directive
   * @format date-time
   */
  autonomous_directive_consumed_at?: string;
  /** Free-text steering instruction */
  autonomous_directive_content: string;
  /**
   * Creation date
   * @format date-time
   */
  autonomous_directive_created_at?: string;
  /** ID of the directive */
  autonomous_directive_id?: string;
  /** Autonomous run this directive steers */
  autonomous_directive_run_id?: string;
  /** Whether the orchestrator has consumed the directive */
  autonomous_directive_status: "PENDING" | "CONSUMED";
  listened?: boolean;
}

/** Operator steering directive for a live autonomous run */
export interface AutonomousDirectiveInput {
  /**
   * Free-text steering instruction (focus, avoid host, change tactic...)
   * @minLength 1
   */
  content: string;
}

export interface AutonomousEvent {
  /** Human-readable body / narration */
  autonomous_event_content?: string;
  /**
   * Creation date
   * @format date-time
   */
  autonomous_event_created_at?: string;
  /** Structured payload (tool i/o, gap suggestions, proof metadata) */
  autonomous_event_data?: string;
  /** ID of the event */
  autonomous_event_id?: string;
  /** Autonomous run this event belongs to */
  autonomous_event_run_id?: string;
  /**
   * Monotonic per-run ordering cursor
   * @format int64
   */
  autonomous_event_sequence?: number;
  /** Short human title */
  autonomous_event_title?: string;
  /** Kind of timeline entry */
  autonomous_event_type:
    | "NARRATION"
    | "DECISION"
    | "TOOL_ACTION"
    | "HANDOVER"
    | "AGENT_DELEGATION"
    | "GAP"
    | "STATUS"
    | "DIRECTIVE"
    | "QUESTION"
    | "PROOF";
  listened?: boolean;
}

/** Timeline event appended by the orchestrator */
export interface AutonomousEventInput {
  /** Human-readable narration / body */
  content?: string;
  /** Structured JSON payload (tool i/o, gap suggestions, proof metadata) */
  data?: string;
  /** Short human title */
  title?: string;
  /** Kind of timeline entry */
  type:
    | "NARRATION"
    | "DECISION"
    | "TOOL_ACTION"
    | "HANDOVER"
    | "AGENT_DELEGATION"
    | "GAP"
    | "STATUS"
    | "DIRECTIVE"
    | "QUESTION"
    | "PROOF";
}

/** Binds an upstream finding value into one of this step's inject inputs */
export interface AutonomousInputMapping {
  /** The inject content field to fill (e.g. host, port, username, password) - the key the injector contract reads its input from. */
  input_key?: string;
  /** The finding primitive to pull the value from, as its lowercase label (e.g. host, port, username, password, hash). Must match a primitive an upstream step emits. */
  key_type?:
    | "account_with_password_not_required"
    | "action_output"
    | "admin_username"
    | "asreproastable_account"
    | "asset_group_id"
    | "asset_id"
    | "computer_name"
    | "cve"
    | "delegation_account"
    | "document"
    | "domain"
    | "email"
    | "file_name"
    | "file_path"
    | "group_name"
    | "hash"
    | "host"
    | "ipv4"
    | "ipv6"
    | "ip_subnet"
    | "kerberoastable_account"
    | "key"
    | "number"
    | "password"
    | "permissions"
    | "port"
    | "service"
    | "severity"
    | "share_name"
    | "sid"
    | "targeted-asset"
    | "text"
    | "username"
    | "value"
    | "vulnerability_name"
    | "vulnerability_status";
  /** Where to read the value from: GLOBAL (workflow-wide finding pool, the default and usual choice), LOCAL (this step's own matched values), or DEFAULT (static). */
  mapping_type?: "DEFAULT" | "LOCAL" | "GLOBAL";
}

export interface AutonomousObjectiveTemplate {
  /** Whether this is a seeded built-in template */
  autonomous_objective_template_builtin?: boolean;
  /**
   * Creation date
   * @format date-time
   */
  autonomous_objective_template_created_at?: string;
  /** Short description of what the objective does */
  autonomous_objective_template_description?: string;
  /** Whether the template is offered in the gallery */
  autonomous_objective_template_enabled?: boolean;
  /** Icon key used by the frontend gallery card */
  autonomous_objective_template_icon?: string;
  /** ID of the objective template */
  autonomous_objective_template_id?: string;
  /** Stable key (unique per tenant) for built-in identification */
  autonomous_objective_template_key: string;
  /** Optional kill-chain phase focus hint */
  autonomous_objective_template_kill_chain_focus?: string;
  /** Human label shown in the gallery */
  autonomous_objective_template_label: string;
  /**
   * Display order in the gallery
   * @format int32
   */
  autonomous_objective_template_order?: number;
  /** Objective prompt handed to the orchestrator */
  autonomous_objective_template_prompt: string;
  /** Whether the objective is environment-wide (operates over the whole authorized scope, no target choice needed) or target-dependent (needs a specific target/asset the operator picks up front or the orchestrator asks for). One of: environment, target. */
  autonomous_objective_template_scope_mode: string;
  /**
   * Update date
   * @format date-time
   */
  autonomous_objective_template_updated_at?: string;
  listened?: boolean;
}

/** Result of promoting a finding to a targetable asset */
export interface AutonomousPromotedAssetResult {
  /** Id of the created (endpoint) asset - use it as an inject target */
  asset_id?: string;
  /** Name of the created asset */
  asset_name?: string;
  /** Id of the original finding (kept, now linked to the asset) */
  finding_id?: string;
}

export interface AutonomousRun {
  /** XTM One agent ids the orchestrator may consult as specialist handover targets during this run (in addition to the built-in payload creator). */
  autonomous_run_agent_ids?: string[];
  /** Per-agent discovery mode for this run: maps an XTM One agent id (or the orchestrator's own id) to how much latitude it has to bring newly discovered entities into the attack path (EXISTING_ONLY / SCOPED / EXPANSIVE). Enforced at OpenAEV's creation choke points against the acting agent. An agent absent from the map falls back to SCOPED. */
  autonomous_run_agent_modes?: Record<string, string>;
  /**
   * Creation date
   * @format date-time
   */
  autonomous_run_created_at?: string;
  /**
   * Absolute instant at which OpenAEV hard-stops the run. Computed from startedAt + timeoutSeconds when the run becomes live. Null when no timeout applies.
   * @format date-time
   */
  autonomous_run_deadline_at?: string;
  /** ID of the autonomous run */
  autonomous_run_id?: string;
  /** Last error message when the run failed */
  autonomous_run_last_error?: string;
  /** Free-text or template-derived objective for the run */
  autonomous_run_objective: string;
  /** Key of the objective template the run was seeded from, if any */
  autonomous_run_objective_template_key?: string;
  /** Plan summary captured while building the logic and handed to a subsequent live autonomous run as guidance, so the live run follows the plan while still adapting to what it finds. */
  autonomous_run_plan_guidance?: string;
  /** Build flag. When true the orchestrator only authors the scenario's logic (scope + steps + decisions) and nothing is executed; the built logic is shown in draft orange and can then be launched (in normal or autonomous mode). */
  autonomous_run_plan_mode: boolean;
  /** Scenario the simulation was created from, if any */
  autonomous_run_scenario_id?: string;
  /** Authoritative run scope: a mixed list of targetable entities (assets, asset groups, teams, persons). The orchestrator attacks within this perimeter. */
  autonomous_run_scope?: AutonomousScopeTarget[];
  /** Asset group defining the initial in-scope perimeter */
  autonomous_run_scope_asset_group_id?: string;
  /** First team of the scope, projected for convenience. Authoritative scope is the mixed list in autonomous_run_scope. An inject can only target a team, never a bare person. */
  autonomous_run_scope_team_id?: string;
  /** Chained simulation (Exercise) this run drives */
  autonomous_run_simulation_id?: string;
  /**
   * When the run was last moved to RUNNING; the timeout deadline is based on it
   * @format date-time
   */
  autonomous_run_started_at?: string;
  /** Lifecycle status of the run */
  autonomous_run_status:
    | "CREATED"
    | "PLANNING"
    | "PLANNED"
    | "RUNNING"
    | "PAUSED"
    | "WAITING_INPUT"
    | "COMPLETED"
    | "FAILED"
    | "CANCELED";
  /**
   * Maximum wall-clock lifetime of the run in seconds. OpenAEV owns this deadline: it steers the orchestrator with winddown signals shortly before it, then hard-stops the run (exactly like an operator Stop) when it is reached. Null means no OpenAEV-enforced timeout (e.g. build mode).
   * @format int64
   */
  autonomous_run_timeout_seconds?: number;
  /**
   * Update date
   * @format date-time
   */
  autonomous_run_updated_at?: string;
  /** XTM One orchestrator agent slug */
  autonomous_run_xtm_agent_slug?: string;
  /** XTM One orchestrator session id for streaming reconnection */
  autonomous_run_xtm_session_id?: string;
  listened?: boolean;
}

/** Input to create an autonomous attack-path run */
export interface AutonomousRunCreateInput {
  /** Optional XTM One agent ids the orchestrator may consult as specialist handover targets during the run (in addition to the built-in payload creator). When omitted, the tenant's configured default additional agents are used. */
  agent_ids?: string[];
  /** Optional per-agent discovery mode for this run: maps an agent id to EXISTING_ONLY / SCOPED / EXPANSIVE, controlling how much latitude that agent has to create new assets / findings / persons from recon on the fly. When omitted, the tenant's configured default per-agent modes are used; an agent absent from the map falls back to SCOPED. */
  agent_modes?: Record<string, string>;
  /** Optional orchestrator agent slug override */
  agent_slug?: string;
  /** Optional description for the auto-provisioned run. */
  description?: string;
  /** Optional label for the auto-provisioned run. Defaults to a generated name. */
  name?: string;
  /** Free-text objective. Optional when an objective template key is provided. */
  objective?: string;
  /** Key of an objective template to seed the objective from */
  objective_template_key?: string;
  /** Build mode: when true the orchestrator only authors the scenario's logic (scope, steps, decisions) and executes nothing. The operator can review the built logic and later launch the scenario (in normal or autonomous mode). Defaults to false (immediate live autonomous run). */
  plan_mode?: boolean;
  /** Refine mode (plan / build only): when true the orchestrator refines the scenario's EXISTING authored logic instead of rebuilding it from scratch. The authored steps and event/trigger conditions are kept, and a prior AI-built (plan) run is reused so its decision timeline (full history) is preserved and reopened. When false (default) a build is a rebuild: the logic map is wiped and any prior run superseded so the orchestrator designs the path fresh. Ignored outside build/plan mode. */
  refine?: boolean;
  /** Advanced/optional: seed from an existing chaining scenario instead of auto-provisioning. Leave empty for a fully autonomous run. */
  scenario_id?: string;
  /** Optional mixed scope: a list of targetable entities (assets, asset groups, teams, persons) the run is restricted to. Leave empty to let the AI resolve the scope. */
  scope?: AutonomousScopeTarget[];
  /** Optional asset group defining the initial in-scope perimeter */
  scope_asset_group_id?: string;
  /** Optional full scope definition seeded onto the run's scenario and simulation workflows: allow-list and deny-list rules across every source (asset, asset group, team, person, and manual IP / CIDR / hostname / CSV), matching the manual chained-scope editor. Superset of 'scope' (which only carries allow-listed entities). Leave empty to skip scoping at launch and let the AI resolve and record the scope. */
  scope_rules?: WorkflowScopeRuleInput[];
  /** Optional team defining the in-scope audience for identity-targeted objectives (phishing, human credential harvesting). Legacy single-team shortcut; prefer the mixed 'scope' list. */
  scope_team_id?: string;
  /**
   * Maximum wall-clock lifetime of the run in seconds. OpenAEV owns this deadline: it steers the orchestrator with winddown signals shortly before it, then hard-stops the run (exactly like an operator Stop) when it is reached. Defaults to 24h when omitted; ignored in build mode (authoring the logic is untimed).
   * @format int64
   */
  timeout_seconds?: number;
}

/** One resolved scope entry of an autonomous run */
export interface AutonomousScopeEntry {
  /** The rule value: an entity id, or a raw IP / CIDR / hostname for MANUAL/CSV */
  id?: string;
  /** Resolved display name for entities; falls back to the id / raw value */
  name?: string;
  /** Workflow scope-rule source: ASSET, ASSET_GROUP, TEAM, PLAYER, MANUAL, CSV */
  source?: string;
  /** Orchestrator target kind: ASSETS, ASSETS_GROUPS, TEAMS, PLAYERS (or MANUAL for a raw IP / CIDR / hostname). Use this kind's ids with the authoring / scope tools. */
  type?: string;
}

/** One targetable entity in an autonomous run's scope */
export interface AutonomousScopeTarget {
  /** Entity id of that kind (asset / asset-group / team / user id) */
  id?: string;
  /** Target kind: ASSETS, ASSETS_GROUPS, TEAMS or PLAYERS */
  type?: string;
}

/** Input for the orchestrator to set an autonomous run's resolved scope */
export interface AutonomousScopeUpdateInput {
  /** The resolved scope: a list of targets (assets, asset groups, teams, persons) that become the run's allow-list. Replaces any previous allow-list. Empty clears it. */
  scope?: AutonomousScopeTarget[];
}

/** An autonomous run's live, resolved scope (allow-list + deny-list) */
export interface AutonomousScopeView {
  /** The authorized perimeter the orchestrator may attack */
  allowlist?: AutonomousScopeEntry[];
  /** Explicit carve-outs the orchestrator must never touch (deny wins) */
  denylist?: AutonomousScopeEntry[];
  /** The autonomous run id this scope belongs to */
  run_id?: string;
}

/** Run status update pushed by the orchestrator */
export interface AutonomousStatusUpdateInput {
  /** Optional narration for the status timeline entry */
  content?: string;
  /** Error message when the run failed */
  last_error?: string;
  /** New lifecycle status */
  status:
    | "CREATED"
    | "PLANNING"
    | "PLANNED"
    | "RUNNING"
    | "PAUSED"
    | "WAITING_INPUT"
    | "COMPLETED"
    | "FAILED"
    | "CANCELED";
  /** Optional short title for the status timeline entry */
  title?: string;
}

/** A finding-driven trigger: react to findings and consume their values */
export interface AutonomousStepTrigger {
  /** Short, human-readable name for the EVENT this trigger represents - the discovery it fires on, phrased as an operator would read it (e.g. "SMB service exposed", "Valid credentials found", "Open web port discovered"). It becomes the event node's title in the Logic graph. When omitted, a readable name is derived from the filters so the event is never shown as "Untitled event". */
  event_name?: string;
  /** The predicates that make this step fire. Empty means: fire as soon as any of the mapped key_types is present in the finding pool. */
  filters?: AutonomousTriggerFilter[];
  /** Which finding values to bind into this step's inject inputs (GLOBAL mappers). This is how the step attacks what upstream steps discovered. */
  mappings?: AutonomousInputMapping[];
  /** How to combine multiple filters: AND (all must hold) or OR (any). Defaults to AND. */
  match?: string;
}

/** Ensure a targetable team wrapping the given persons */
export interface AutonomousTargetTeamInput {
  /** Optional id of the agent on whose behalf this human target is being brought in (the orchestrator itself, or a specialist it consulted). Used to resolve the discovery mode enforced on the recipients: EXISTING_ONLY / SCOPED require them to be inside the run's identity allow-scope; EXPANSIVE may reach beyond it. Omitted -> SCOPED. */
  acting_agent_id?: string;
  /** Optional team name; a readable default is derived when omitted */
  name?: string;
  /**
   * Ids of the persons (players) that must be reachable through the team
   * @minItems 1
   */
  player_ids: string[];
  /** Optional existing team id to augment instead of creating a new one (idempotent reuse) */
  team_id?: string;
}

/** A team that is ready to be targeted by a human-in-the-loop inject */
export interface AutonomousTargetTeamResult {
  /** Ids of the players now enabled on the simulation through this team */
  player_ids?: string[];
  /** Id of the contextual team - pass it as an inject target (team_ids) */
  team_id?: string;
  /** Name of the team */
  team_name?: string;
}

/** A single predicate of a finding-driven trigger */
export interface AutonomousTriggerFilter {
  /** Whether the comparison is case-sensitive (default true). */
  case_sensitive?: boolean;
  /** The finding primitive to test, as its lowercase label (e.g. port, host, ipv4, cve, username, password, hash, share_name, service, severity, kerberoastable_account). This is the field an upstream inject's output parser emitted. */
  key_type?:
    | "account_with_password_not_required"
    | "action_output"
    | "admin_username"
    | "asreproastable_account"
    | "asset_group_id"
    | "asset_id"
    | "computer_name"
    | "cve"
    | "delegation_account"
    | "document"
    | "domain"
    | "email"
    | "file_name"
    | "file_path"
    | "group_name"
    | "hash"
    | "host"
    | "ipv4"
    | "ipv6"
    | "ip_subnet"
    | "kerberoastable_account"
    | "key"
    | "number"
    | "password"
    | "permissions"
    | "port"
    | "service"
    | "severity"
    | "share_name"
    | "sid"
    | "targeted-asset"
    | "text"
    | "username"
    | "value"
    | "vulnerability_name"
    | "vulnerability_status";
  /** Comparison: EQ, NEQ, IS_NULL, IS_NOT_NULL, GT, GTE, LT, LTE, IN, NIN. Defaults to IS_NOT_NULL (fire as soon as the finding carries this key_type at all). */
  operator?:
    | "AND"
    | "OR"
    | "EQ"
    | "NEQ"
    | "IS_NULL"
    | "IS_NOT_NULL"
    | "GT"
    | "GTE"
    | "LT"
    | "LTE"
    | "IN"
    | "NIN"
    | "MAPPER"
    | "DEPEND_ON";
  /** The value to compare against (omit for IS_NULL / IS_NOT_NULL). */
  value?: string;
}

export type AverageConfiguration = UtilRequiredKeys<
  WidgetConfiguration,
  "widget_configuration_type" | "time_range" | "date_attribute"
> & {
  series: Series[];
};

interface BaseEsBase {
  /** @format date-time */
  base_created_at?: string;
  base_dependencies?: string[];
  base_entity?: string;
  base_id?: string;
  base_representative?: string;
  base_restrictions?: string[];
  /** @format date-time */
  base_updated_at?: string;
}

type BaseEsBaseBaseEntityMapping<Key, Type> = {
  base_entity: Key;
} & Type;

interface BaseInjectTarget {
  target_category?: string;
  target_detection_status?:
    | "FAILED"
    | "PENDING"
    | "PARTIAL"
    | "UNKNOWN"
    | "SUCCESS";
  target_execution_status?:
    | "FAILED"
    | "PENDING"
    | "PARTIAL"
    | "UNKNOWN"
    | "SUCCESS";
  target_human_response_status?:
    | "FAILED"
    | "PENDING"
    | "PARTIAL"
    | "UNKNOWN"
    | "SUCCESS";
  /** @minLength 1 */
  target_id: string;
  target_prevention_status?:
    | "FAILED"
    | "PENDING"
    | "PARTIAL"
    | "UNKNOWN"
    | "SUCCESS";
  target_subtype?: string;
  /** @uniqueItems true */
  target_tags?: string[];
  target_type?: string;
  target_vulnerability_status?:
    | "FAILED"
    | "PENDING"
    | "PARTIAL"
    | "UNKNOWN"
    | "SUCCESS";
}

type BaseInjectTargetTargetTypeMapping<Key, Type> = {
  target_type: Key;
} & Type;

interface BaseInjectorContractBaseOutput {
  /** Injector contract external Id */
  injector_contract_external_id?: string;
  injector_contract_has_full_details?: boolean;
  /**
   * Injector contract Id
   * @minLength 1
   */
  injector_contract_id: string;
  /**
   * Timestamp when the injector contract was last updated
   * @format date-time
   */
  injector_contract_updated_at: string;
}

type BaseInjectorContractBaseOutputInjectorContractHasFullDetailsMapping<
  Key,
  Type,
> = {
  injector_contract_has_full_details: Key;
} & Type;

interface BasePayload {
  listened?: boolean;
  payload_arguments?: PayloadArgument[];
  /** Organization author of the payload */
  payload_author_organization?: string;
  /** Team author of the payload */
  payload_author_team?: string;
  /** User author of the payload */
  payload_author_user?: string;
  payload_cleanup_command?: string;
  payload_cleanup_executor?: string;
  payload_collector_type?: string;
  /** @format date-time */
  payload_created_at: string;
  payload_description?: string;
  payload_detection_remediations?: DetectionRemediation[];
  payload_elevation_required?: boolean;
  payload_execution_arch: "x86_64" | "arm64" | "ALL_ARCHITECTURES";
  payload_expectations?: (
    | "ARTICLE"
    | "CHALLENGE"
    | "MANUAL"
    | "PREVENTION"
    | "DETECTION"
    | "VULNERABILITY"
  )[];
  payload_expected_security_platforms?: Record<
    string,
    (
      | "EDR"
      | "XDR"
      | "SIEM"
      | "SOAR"
      | "NDR"
      | "ISPM"
      | "EMAIL_SECURITY"
      | "LLM_FIREWALL"
      | "AI_GATEWAY"
      | "VULNERABILITY_SCANNER"
    )[]
  >;
  payload_external_id?: string;
  /** @minLength 1 */
  payload_id: string;
  /** @minLength 1 */
  payload_name: string;
  /** @uniqueItems true */
  payload_output_parsers?: OutputParser[];
  /** @minItems 1 */
  payload_platforms: (
    | "Linux"
    | "Windows"
    | "MacOS"
    | "Android"
    | "iOS"
    | "Container"
    | "Service"
    | "Generic"
    | "Internal"
    | "Unknown"
  )[];
  payload_prerequisites?: PayloadPrerequisite[];
  payload_source: "COMMUNITY" | "FILIGRAN" | "MANUAL";
  payload_status: "UNVERIFIED" | "VERIFIED" | "DEPRECATED";
  payload_type?: string;
  /** @format date-time */
  payload_updated_at: string;
  typeEnum?:
    | "COMMAND"
    | "EXECUTABLE"
    | "FILE_DROP"
    | "DNS_RESOLUTION"
    | "NETWORK_TRAFFIC"
    | "AI_ATTACK";
}

interface BasePayloadCreateInput {
  command_content?: string | null;
  command_executor?: string | null;
  dns_resolution_hostname?: string;
  executable_file?: string;
  file_drop_file?: string;
  payload_arguments?: PayloadArgument[];
  payload_attack_patterns?: string[];
  payload_cleanup_command?: string | null;
  payload_cleanup_executor?: string | null;
  payload_description?: string;
  /** List of detection remediation gaps for collectors */
  payload_detection_remediations?: DetectionRemediationInput[];
  /** Set list of domains */
  payload_domains: string[];
  payload_elevation_required?: boolean;
  payload_execution_arch: "x86_64" | "arm64" | "ALL_ARCHITECTURES";
  payload_expectations: (
    | "ARTICLE"
    | "CHALLENGE"
    | "MANUAL"
    | "PREVENTION"
    | "DETECTION"
    | "VULNERABILITY"
  )[];
  payload_expected_security_platforms?: Record<
    string,
    (
      | "EDR"
      | "XDR"
      | "SIEM"
      | "SOAR"
      | "NDR"
      | "ISPM"
      | "EMAIL_SECURITY"
      | "LLM_FIREWALL"
      | "AI_GATEWAY"
      | "VULNERABILITY_SCANNER"
    )[]
  >;
  /** @minLength 1 */
  payload_name: string;
  /**
   * Set of output parsers
   * @uniqueItems true
   */
  payload_output_parsers?: OutputParserInput[];
  /** @minItems 1 */
  payload_platforms: (
    | "Linux"
    | "Windows"
    | "MacOS"
    | "Android"
    | "iOS"
    | "Container"
    | "Service"
    | "Generic"
    | "Internal"
    | "Unknown"
  )[];
  payload_prerequisites?: PayloadPrerequisite[];
  payload_source: "COMMUNITY" | "FILIGRAN" | "MANUAL";
  payload_status: "UNVERIFIED" | "VERIFIED" | "DEPRECATED";
  payload_tags?: string[];
  /** @minLength 1 */
  payload_type: string;
}

type BasePayloadCreateInputPayloadTypeMapping<Key, Type> = {
  payload_type: Key;
} & Type;

type BasePayloadPayloadTypeMapping<Key, Type> = {
  payload_type: Key;
} & Type;

export interface BrokerConnectionInfo {
  host?: string;
  pass?: string;
  /** @format int32 */
  port?: number;
  use_ssl?: boolean;
  user?: string;
  vhost?: string;
}

export interface BulkOperation {
  bulk_operation_action?: string;
  bulk_operation_entity?: string;
  /** @format date-time */
  bulk_operation_finished_at?: string;
  bulk_operation_id?: string;
  /** @format int32 */
  bulk_operation_processed?: number;
  /** @format date-time */
  bulk_operation_started_at?: string;
  bulk_operation_status?: "RUNNING" | "COMPLETED" | "FAILED";
  /** @format int32 */
  bulk_operation_total?: number;
}

export interface CTIEvent {
  event: Event;
  internal: Internal;
}

export interface CVEBulkInsertInput {
  cves: CveCreateInput[];
  initial_dataset_completed?: boolean;
  /** @format int32 */
  last_index?: number;
  /** @format date-time */
  last_modified_date_fetched?: string;
  source_identifier: string;
}

export interface CalderaSettings {
  /** True if the Caldera Executor is enabled */
  executor_caldera_enable?: boolean;
  /** Id of the instance linked to the configuration */
  executor_caldera_instance_id?: string;
  /** Url of the Caldera Executor */
  executor_caldera_public_url?: string;
}

/** A capability node in the capability tree */
export interface CapabilityOutput {
  /** Whether this capability can be assigned to a role */
  capability_checkable: boolean;
  /** Child capabilities */
  capability_children: CapabilityOutput[];
  /**
   * Scopes where this capability applies (PLATFORM, TENANT)
   * @uniqueItems true
   */
  capability_scopes: string[];
  /**
   * Enum key of the capability or group
   * @minLength 1
   */
  capability_value: string;
}

/** Capability resolution query (techniques / desired outputs / platforms) */
export interface CapabilityQueryInput {
  /** Optional objective template key; its kill-chain focus seeds the resolution when no explicit techniques/output types are given */
  objective_template_key?: string;
  /** Desired output/primitive types the AI needs produced (ContractOutputType labels: credentials, cve, port, share, kerberoastable_account...) */
  output_types?: string[];
  /** Optional platform filter (Windows, Linux, MacOS) applied to matches */
  platforms?: string[];
  /** MITRE ATT&CK technique external ids to resolve (e.g. T1110, T1566) */
  techniques?: string[];
}

/** Capability resolution report */
export interface CapabilityReport {
  /** Installed injectors and the delivery substrate available for custom actions */
  arsenal?: ArsenalInventory;
  /** True when every queried token has at least one installed contract */
  fully_satisfied?: boolean;
  /** Convenience subset: only the unsatisfied resolutions */
  gaps?: CapabilityResolution[];
  /** One resolution per queried capability token */
  resolutions?: CapabilityResolution[];
}

/** Resolution of one capability token against installed contracts */
export interface CapabilityResolution {
  /** Installed contracts that satisfy the token (empty when unsatisfied) */
  contracts?: ResolvedContract[];
  /** Whether this token is a technique, an output/primitive type, or a phase */
  kind?: "TECHNIQUE" | "OUTPUT_TYPE" | "KILL_CHAIN_PHASE";
  /** Human-readable label for the token */
  label?: string;
  /** True when at least one installed contract satisfies the token */
  satisfied?: boolean;
  /** Marketplace connectors to install to close the gap (empty when satisfied) */
  suggested_connectors?: SuggestedConnector[];
  /** The resolved token (technique external id, output type label, or phase) */
  token?: string;
}

export interface CatalogConnector {
  /** Connector class name */
  catalog_connector_class_name?: string;
  /** @uniqueItems true */
  catalog_connector_configuration: CatalogConnectorConfiguration[];
  /** Connector container image */
  catalog_connector_container_image?: string;
  /** Connector container version */
  catalog_connector_container_version?: string;
  /**
   * Connector deleted at
   * @format date-time
   */
  catalog_connector_deleted_at?: string;
  /** Connector description */
  catalog_connector_description?: string;
  /** @uniqueItems true */
  catalog_connector_instances: ConnectorInstancePersisted[];
  /**
   * Connector last verified date
   * @format date-time
   */
  catalog_connector_last_verified_date?: string;
  /** Connector logo */
  catalog_connector_logo_url?: string;
  /** Connector manager supported */
  catalog_connector_manager_supported?: boolean;
  /**
   * Connector max confidence level
   * @format int32
   */
  catalog_connector_max_confidence_level?: number;
  /** Connector playbook supported */
  catalog_connector_playbook_supported?: boolean;
  /** Whether the legacy properties configuration has already been migrated */
  catalog_connector_properties_migrated?: boolean;
  /** Connector description */
  catalog_connector_short_description?: string;
  /** Connector slug */
  catalog_connector_slug?: string;
  /** Connector source code */
  catalog_connector_source_code?: string;
  /** Connector subscription link */
  catalog_connector_subscription_link?: string;
  /** Connector support version */
  catalog_connector_support_version?: string;
  /** Connector type */
  catalog_connector_type?:
    | "COLLECTOR"
    | "INJECTOR"
    | "EXECUTOR"
    | "SECRETS_PROVIDER";
  /**
   * Connector use cases
   * @uniqueItems true
   */
  catalog_connector_use_cases?: string[];
  /** Connector verified */
  catalog_connector_verified?: boolean;
  /** Connector ID */
  connector_id: string;
  /**
   * Connector title
   * @minLength 1
   */
  connector_title: string;
  listened?: boolean;
}

export interface CatalogConnectorConfiguration {
  /** Connector configuration default */
  connector_configuration_default?: JsonNode;
  /** Connector configuration description */
  connector_configuration_description?: string;
  /**
   * Connector configuration enum
   * @uniqueItems true
   */
  connector_configuration_enum?: string[];
  /** Connector configuration format */
  connector_configuration_format?:
    | "DEFAULT"
    | "DATE"
    | "DATETIME"
    | "DURATION"
    | "EMAIL"
    | "PASSWORD"
    | "URI"
    | "UUID";
  /** Connector ID */
  connector_configuration_id?: string;
  /** Connector configuration key */
  connector_configuration_key: string;
  /** Connector configuration required */
  connector_configuration_required?: boolean;
  /** Connector configuration type */
  connector_configuration_type:
    | "ARRAY"
    | "BOOLEAN"
    | "INTEGER"
    | "OBJECT"
    | "STRING";
  /** Connector configuration write only */
  connector_configuration_writeonly?: boolean;
  listened?: boolean;
}

export interface CatalogConnectorOutput {
  /** Connector container version referenced in the catalog */
  catalog_connector_container_version?: string;
  catalog_connector_description?: string;
  /** @minLength 1 */
  catalog_connector_id: string;
  /** @format date-time */
  catalog_connector_last_verified_date?: string;
  catalog_connector_logo_url?: string;
  catalog_connector_manager_supported?: boolean;
  catalog_connector_short_description?: string;
  /** @minLength 1 */
  catalog_connector_slug: string;
  catalog_connector_source_code?: string;
  catalog_connector_subscription_link?: string;
  /** @minLength 1 */
  catalog_connector_title: string;
  catalog_connector_type:
    | "COLLECTOR"
    | "INJECTOR"
    | "EXECUTOR"
    | "SECRETS_PROVIDER";
  /** @uniqueItems true */
  catalog_connector_use_cases?: string[];
  catalog_connector_verified?: boolean;
  /** @format int32 */
  instance_deployed_count?: number;
}

/** Catalog simple output */
export interface CatalogConnectorSimpleOutput {
  catalog_connector_id?: string;
  catalog_connector_logo_url?: string;
  catalog_connector_short_description?: string;
}

export interface ChainingOutput {
  conditions?: EventOutput[];
  steps?: StepOutput[];
}

export interface Challenge {
  challenge_category?: string;
  challenge_content?: string;
  /** @format date-time */
  challenge_created_at: string;
  challenge_documents?: string[];
  challenge_exercises?: string[];
  /** @minItems 1 */
  challenge_flags: ChallengeFlag[];
  /** @minLength 1 */
  challenge_id: string;
  /** @format int32 */
  challenge_max_attempts?: number;
  /** @minLength 1 */
  challenge_name: string;
  challenge_scenarios?: string[];
  /** @format double */
  challenge_score?: number;
  challenge_tags?: string[];
  /** @format date-time */
  challenge_updated_at: string;
  /** @format date-time */
  challenge_virtual_publication?: string;
  listened?: boolean;
}

export interface ChallengeFlag {
  flag_challenge?: string;
  /** @format date-time */
  flag_created_at?: string;
  flag_id?: string;
  flag_type?: "VALUE" | "VALUE_CASE" | "REGEXP";
  /** @format date-time */
  flag_updated_at?: string;
  flag_value?: string;
  listened?: boolean;
}

export interface ChallengeInformation {
  /** @format int32 */
  challenge_attempt?: number;
  challenge_detail?: PublicChallenge;
  challenge_expectation?: InjectExpectationOutput;
}

export interface ChallengeInput {
  challenge_category?: string;
  challenge_content?: string;
  challenge_documents?: string[];
  /** @minItems 1 */
  challenge_flags: FlagInput[];
  /** @format int32 */
  challenge_max_attempts?: number;
  /** @minLength 1 */
  challenge_name: string;
  /** @format double */
  challenge_score?: number;
  challenge_tags?: string[];
}

export interface ChallengeResult {
  result?: boolean;
}

export interface ChallengeTryInput {
  challenge_value: string;
}

export interface ChangePasswordInput {
  /**
   * The new password
   * @minLength 1
   */
  password: string;
  /**
   * The new password again to validate it's been typed well
   * @minLength 1
   */
  password_validation: string;
}

export interface Channel {
  /** @format date-time */
  channel_created_at: string;
  channel_description?: string;
  /** @minLength 1 */
  channel_id: string;
  channel_logo_dark?: string;
  channel_logo_light?: string;
  channel_mode?: string;
  channel_name?: string;
  channel_primary_color_dark?: string;
  channel_primary_color_light?: string;
  channel_secondary_color_dark?: string;
  channel_secondary_color_light?: string;
  channel_type?: string;
  /** @format date-time */
  channel_updated_at: string;
  listened?: boolean;
  logos?: Document[];
}

export interface ChannelCreateInput {
  /** @minLength 1 */
  channel_description: string;
  /** @minLength 1 */
  channel_name: string;
  /** @minLength 1 */
  channel_type: string;
}

export interface ChannelReader {
  channel_articles?: Article[];
  channel_exercise?: Exercise;
  channel_id?: string;
  channel_information?: Channel;
  channel_scenario?: Scenario;
}

export interface ChannelUpdateInput {
  /** @minLength 1 */
  channel_description: string;
  channel_mode?: string;
  /** @minLength 1 */
  channel_name: string;
  channel_primary_color_dark?: string;
  channel_primary_color_light?: string;
  channel_secondary_color_dark?: string;
  channel_secondary_color_light?: string;
  /** @minLength 1 */
  channel_type: string;
}

export interface ChannelUpdateLogoInput {
  channel_logo_dark?: string;
  channel_logo_light?: string;
}

export interface ChatbotAgentOutput {
  description?: string;
  id?: string;
  name?: string;
  slug?: string;
}

export interface CheckExerciseRulesInput {
  /** List of tag that will be applied to the simulation */
  new_tags?: string[];
}

export interface CheckExerciseRulesOutput {
  /** Are there rules that can be applied? */
  rules_found: boolean;
}

export interface CheckScenarioRulesInput {
  /** List of tag that will be applied to the scenario */
  new_tags?: string[];
}

export interface CheckScenarioRulesOutput {
  /** Are there rules that can be applied? */
  rules_found: boolean;
}

export interface Collector {
  collector_author?: string;
  /** @format date-time */
  collector_created_at: string;
  collector_external?: boolean;
  /** @minLength 1 */
  collector_id: string;
  /** @format date-time */
  collector_last_execution?: string;
  /** @minLength 1 */
  collector_name: string;
  /** @format int32 */
  collector_period?: number;
  collector_security_platform?: SecurityPlatform;
  collector_state?: object;
  /** @minLength 1 */
  collector_type: string;
  /** @format date-time */
  collector_updated_at: string;
  listened?: boolean;
}

export interface CollectorCreateInput {
  collector_author?: string;
  /** @minLength 1 */
  collector_id: string;
  /** @minLength 1 */
  collector_name: string;
  /** @format int32 */
  collector_period?: number;
  collector_security_platform?: string;
  /** @minLength 1 */
  collector_type: string;
}

/** Collector output */
export interface CollectorOutput {
  /** Catalog simple output */
  catalog?: CatalogConnectorSimpleOutput;
  collector_external?: boolean;
  /**
   * Collector id
   * @minLength 1
   */
  collector_id: string;
  /** @format date-time */
  collector_last_execution?: string;
  /** @minLength 1 */
  collector_name: string;
  /** @minLength 1 */
  collector_type: string;
  connector_instance?: ConnectorInstanceOutput;
  existing_collector?: boolean;
  is_verified?: boolean;
}

export interface CollectorUpdateInput {
  /** @format date-time */
  collector_last_execution?: string;
}

export interface Comcheck {
  /** @format date-time */
  comcheck_end_date: string;
  comcheck_exercise?: string;
  /** @minLength 1 */
  comcheck_id: string;
  comcheck_message?: string;
  comcheck_name?: string;
  /** @format date-time */
  comcheck_start_date: string;
  comcheck_state?: "RUNNING" | "EXPIRED" | "FINISHED";
  comcheck_statuses?: string[];
  comcheck_subject?: string;
  /** @format int64 */
  comcheck_users_number?: number;
  listened?: boolean;
}

export interface ComcheckInput {
  /** @format date-time */
  comcheck_end_date?: string;
  comcheck_message?: string;
  /** @minLength 1 */
  comcheck_name: string;
  comcheck_subject?: string;
  comcheck_teams?: string[];
}

export interface ComcheckStatus {
  comcheckstatus_comcheck?: string;
  comcheckstatus_id?: string;
  /** @format date-time */
  comcheckstatus_receive_date?: string;
  /** @format date-time */
  comcheckstatus_sent_date?: string;
  /** @format int32 */
  comcheckstatus_sent_retry?: number;
  comcheckstatus_state?: "RUNNING" | "SUCCESS" | "FAILURE";
  comcheckstatus_user?: string;
  listened?: boolean;
}

export interface Command {
  command_content: string;
  command_executor: string;
  listened?: boolean;
  payload_arguments?: PayloadArgument[];
  /** Organization author of the payload */
  payload_author_organization?: string;
  /** Team author of the payload */
  payload_author_team?: string;
  /** User author of the payload */
  payload_author_user?: string;
  payload_cleanup_command?: string;
  payload_cleanup_executor?: string;
  payload_collector_type?: string;
  /** @format date-time */
  payload_created_at: string;
  payload_description?: string;
  payload_detection_remediations?: DetectionRemediation[];
  payload_elevation_required?: boolean;
  payload_execution_arch: "x86_64" | "arm64" | "ALL_ARCHITECTURES";
  payload_expectations?: (
    | "ARTICLE"
    | "CHALLENGE"
    | "MANUAL"
    | "PREVENTION"
    | "DETECTION"
    | "VULNERABILITY"
  )[];
  payload_expected_security_platforms?: Record<
    string,
    (
      | "EDR"
      | "XDR"
      | "SIEM"
      | "SOAR"
      | "NDR"
      | "ISPM"
      | "EMAIL_SECURITY"
      | "LLM_FIREWALL"
      | "AI_GATEWAY"
      | "VULNERABILITY_SCANNER"
    )[]
  >;
  payload_external_id?: string;
  /** @minLength 1 */
  payload_id: string;
  /** @minLength 1 */
  payload_name: string;
  /** @uniqueItems true */
  payload_output_parsers?: OutputParser[];
  /** @minItems 1 */
  payload_platforms: (
    | "Linux"
    | "Windows"
    | "MacOS"
    | "Android"
    | "iOS"
    | "Container"
    | "Service"
    | "Generic"
    | "Internal"
    | "Unknown"
  )[];
  payload_prerequisites?: PayloadPrerequisite[];
  payload_source: "COMMUNITY" | "FILIGRAN" | "MANUAL";
  payload_status: "UNVERIFIED" | "VERIFIED" | "DEPRECATED";
  payload_type?: string;
  /** @format date-time */
  payload_updated_at: string;
  typeEnum?:
    | "COMMAND"
    | "EXECUTABLE"
    | "FILE_DROP"
    | "DNS_RESOLUTION"
    | "NETWORK_TRAFFIC"
    | "AI_ATTACK";
}

export interface Communication {
  communication_ack?: boolean;
  communication_animation?: boolean;
  communication_attachments?: string[];
  communication_content?: string;
  communication_content_html?: string;
  communication_exercise?: string;
  /** @minLength 1 */
  communication_from: string;
  /** @minLength 1 */
  communication_id: string;
  communication_inject?: string;
  /** @minLength 1 */
  communication_message_id: string;
  /** @format date-time */
  communication_received_at: string;
  /** @format date-time */
  communication_sent_at: string;
  communication_subject?: string;
  /** @minLength 1 */
  communication_to: string;
  communication_users?: string[];
  listened?: boolean;
}

export interface Condition {
  key: string;
  operator: "eq";
  value?: boolean;
}

/** Condition used to execute a step. Can be a Template or an Execution depending on the status of stepFrom. */
export interface ConditionCreateInput {
  /** Whether the comparison is case-sensitive */
  condition_case_sensitive?: boolean;
  /** Property to be mapped */
  condition_key?: string;
  /** Paths to values in the output of the step from */
  condition_key_types?: (
    | "account_with_password_not_required"
    | "action_output"
    | "admin_username"
    | "asreproastable_account"
    | "asset_group_id"
    | "asset_id"
    | "computer_name"
    | "cve"
    | "delegation_account"
    | "document"
    | "domain"
    | "email"
    | "file_name"
    | "file_path"
    | "group_name"
    | "hash"
    | "host"
    | "ipv4"
    | "ipv6"
    | "ip_subnet"
    | "kerberoastable_account"
    | "key"
    | "number"
    | "password"
    | "permissions"
    | "port"
    | "service"
    | "severity"
    | "share_name"
    | "sid"
    | "targeted-asset"
    | "text"
    | "username"
    | "value"
    | "vulnerability_name"
    | "vulnerability_status"
  )[];
  /** Mapping type: DEFAULT, LOCAL, or GLOBAL. Required when condition type is MAPPER, must be null otherwise. */
  condition_mapping_type?: "DEFAULT" | "LOCAL" | "GLOBAL";
  /** Optional display name. On a trigger's root condition this is the event name shown in the Logic graph; leave null on child/mapper conditions. */
  condition_name?: string;
  /** ID of the step linked to the key */
  condition_step_from?: string;
  /** Temporary ID of the condition */
  condition_temporary_id?: string;
  /** Temporary ID of the parent condition */
  condition_temporary_id_condition_parent?: string;
  /** Condition type: AND, OR, EQ, NEQ, IS_NULL, IS_NOT_NULL, GT, GTE, LT, LTE, IN, NIN, AFTER, BEFORE, MAPPER, or DEPEND_ON */
  condition_type?:
    | "AND"
    | "OR"
    | "EQ"
    | "NEQ"
    | "IS_NULL"
    | "IS_NOT_NULL"
    | "GT"
    | "GTE"
    | "LT"
    | "LTE"
    | "IN"
    | "NIN"
    | "MAPPER"
    | "DEPEND_ON";
  /** Value to be compared */
  condition_value?: string;
}

export interface ConditionOutput {
  condition_case_sensitive?: boolean;
  condition_id?: string;
  condition_key?: string;
  condition_key_types?: (
    | "account_with_password_not_required"
    | "action_output"
    | "admin_username"
    | "asreproastable_account"
    | "asset_group_id"
    | "asset_id"
    | "computer_name"
    | "cve"
    | "delegation_account"
    | "document"
    | "domain"
    | "email"
    | "file_name"
    | "file_path"
    | "group_name"
    | "hash"
    | "host"
    | "ipv4"
    | "ipv6"
    | "ip_subnet"
    | "kerberoastable_account"
    | "key"
    | "number"
    | "password"
    | "permissions"
    | "port"
    | "service"
    | "severity"
    | "share_name"
    | "sid"
    | "targeted-asset"
    | "text"
    | "username"
    | "value"
    | "vulnerability_name"
    | "vulnerability_status"
  )[];
  condition_mapping_type?: "DEFAULT" | "LOCAL" | "GLOBAL";
  condition_parent_id?: string;
  condition_type?: string;
  condition_value?: string;
}

export interface Configuration {
  /** Configuration is encrypted */
  configuration_is_encrypted?: boolean;
  /**
   * Configuration key
   * @minLength 1
   */
  configuration_key: string;
  /** Configuration value */
  configuration_value?: string;
}

export interface ConfigurationInput {
  /**
   * Configuration key
   * @minLength 1
   */
  configuration_key: string;
  /** Configuration value */
  configuration_value?: JsonNode;
}

/** Define the ids linked to a collector */
export interface ConnectorIds {
  catalog_connector_id?: string;
  connector_instance_id?: string;
  /** Whether the connector entity is registered in the database. False when a connector instance has been deployed but the connector has not yet started. */
  connector_registered?: boolean;
}

export interface ConnectorInstanceConfiguration {
  /** @minLength 1 */
  connector_instance_configuration_id: string;
  connector_instance_configuration_is_encrypted?: boolean;
  /** @minLength 1 */
  connector_instance_configuration_key: string;
  connector_instance_configuration_value: JsonNode;
  listened?: boolean;
}

export interface ConnectorInstanceHealthInput {
  /** The connector instance id */
  connector_instance_is_in_reboot_loop?: boolean;
  /**
   * Connector instance restart count
   * @format int32
   */
  connector_instance_restart_count?: number;
  /**
   * The connector instance id
   * @format date-time
   */
  connector_instance_started_at?: string;
}

export interface ConnectorInstanceLog {
  /** Connector instance log */
  connector_instance_log?: string;
  /**
   * Connector instance log created at
   * @format date-time
   */
  connector_instance_log_created_at?: string;
  /** @minLength 1 */
  connector_instance_log_id: string;
  listened?: boolean;
}

export interface ConnectorInstanceLogsInput {
  /**
   * The connector instance logs
   * @uniqueItems true
   */
  connector_instance_logs?: string[];
}

export interface ConnectorInstanceOutput {
  connector_instance_current_status: "started" | "stopped";
  /** @minLength 1 */
  connector_instance_id: string;
  connector_instance_requested_status?: "starting" | "stopping";
}

export interface ConnectorInstancePersisted {
  className?: string;
  connector_instance_catalog: CatalogConnector;
  /** @uniqueItems true */
  connector_instance_configurations: ConnectorInstanceConfiguration[];
  connector_instance_current_status: "started" | "stopped";
  /** @minLength 1 */
  connector_instance_id: string;
  connector_instance_is_in_reboot_loop?: boolean;
  /** @uniqueItems true */
  connector_instance_logs: ConnectorInstanceLog[];
  connector_instance_requested_status?: "starting" | "stopping";
  /** @format int32 */
  connector_instance_restart_count?: number;
  connector_instance_source:
    | "PROPERTIES_MIGRATION"
    | "CATALOG_DEPLOYMENT"
    | "OTHER";
  /** @format date-time */
  connector_instance_started_at?: string;
  hashIdentity?: string;
  listened?: boolean;
}

export interface ConsumedFindingKeyDTO {
  eventName?: string;
  keyType?: string;
  matchedFindingIds?: string[];
  operator?: string;
  value?: string;
}

export interface ContractOutputElement {
  /** @format date-time */
  contract_output_element_created_at: string;
  /** @minLength 1 */
  contract_output_element_id: string;
  contract_output_element_is_finding: boolean;
  /** @minLength 1 */
  contract_output_element_key: string;
  /** @minLength 1 */
  contract_output_element_name: string;
  /** @uniqueItems true */
  contract_output_element_regex_groups: RegexGroup[];
  /** @minLength 1 */
  contract_output_element_rule: string;
  contract_output_element_tags?: string[];
  contract_output_element_type:
    | "text"
    | "action_output"
    | "number"
    | "port"
    | "portscan"
    | "ipv4"
    | "ipv6"
    | "credentials"
    | "cve"
    | "username"
    | "email"
    | "share"
    | "file"
    | "admin_username"
    | "group"
    | "computer"
    | "password_policy"
    | "delegation"
    | "sid"
    | "vulnerability"
    | "account_with_password_not_required"
    | "asreproastable_account"
    | "kerberoastable_account"
    | "expectation_signature";
  /** @format date-time */
  contract_output_element_updated_at: string;
  listened?: boolean;
}

export interface ContractOutputElementInput {
  contract_output_element_id?: string;
  /** Indicates whether this contract output element can be used to generate a finding */
  contract_output_element_is_finding: boolean;
  /**
   * Key
   * @minLength 1
   */
  contract_output_element_key: string;
  /**
   * Name
   * @minLength 1
   */
  contract_output_element_name: string;
  /**
   * Set of regex groups
   * @uniqueItems true
   */
  contract_output_element_regex_groups: RegexGroupInput[];
  /**
   * Parser Rule
   * @minLength 1
   */
  contract_output_element_rule: string;
  /** List of tags */
  contract_output_element_tags?: string[];
  /** Contract Output element type, can be: text, action_output, number, port, IPV6, IPV4, portscan, credentials */
  contract_output_element_type:
    | "text"
    | "action_output"
    | "number"
    | "port"
    | "portscan"
    | "ipv4"
    | "ipv6"
    | "credentials"
    | "cve"
    | "username"
    | "email"
    | "share"
    | "file"
    | "admin_username"
    | "group"
    | "computer"
    | "password_policy"
    | "delegation"
    | "sid"
    | "vulnerability"
    | "account_with_password_not_required"
    | "asreproastable_account"
    | "kerberoastable_account"
    | "expectation_signature";
}

/** Represents the rules for parsing the output of an execution. */
export interface ContractOutputElementSimple {
  /** @minLength 1 */
  contract_output_element_id: string;
  /**
   * Represents a unique key identifier.
   * @minLength 1
   */
  contract_output_element_key: string;
  /**
   * Represents the name of the rule.
   * @minLength 1
   */
  contract_output_element_name: string;
  /** @uniqueItems true */
  contract_output_element_regex_groups: RegexGroupSimple[];
  /**
   * The rule to apply for parsing the output, for example, can be a regex.
   * @minLength 1
   */
  contract_output_element_rule: string;
  contract_output_element_tags?: string[];
  /**
   * Represents the data type being extracted.
   * @example "text, action_output, number, port, portscan, ipv4, ipv6, credentials"
   */
  contract_output_element_type:
    | "text"
    | "action_output"
    | "number"
    | "port"
    | "portscan"
    | "ipv4"
    | "ipv6"
    | "credentials"
    | "cve"
    | "username"
    | "email"
    | "share"
    | "file"
    | "admin_username"
    | "group"
    | "computer"
    | "password_policy"
    | "delegation"
    | "sid"
    | "vulnerability"
    | "account_with_password_not_required"
    | "asreproastable_account"
    | "kerberoastable_account"
    | "expectation_signature";
}

export interface CreateConnectorInstanceInput {
  /** @minLength 1 */
  catalog_connector_id: string;
  connector_instance_configurations?: ConfigurationInput[];
}

export interface CreateExerciseInput {
  exercise_category?: string;
  exercise_custom_dashboard?: string;
  exercise_default_kill_chain?: string;
  exercise_description?: string;
  exercise_is_chaining?: boolean;
  /**
   * @minLength 0
   * @maxLength 100
   * @pattern ^[^\r\n\x00]*$
   */
  exercise_mail_from_name?: string;
  exercise_mails_reply_to?: string[];
  exercise_main_focus?: string;
  exercise_message_footer?: string;
  exercise_message_header?: string;
  /**
   * @minLength 0
   * @maxLength 255
   */
  exercise_name: string;
  exercise_severity?: string;
  /** @format date-time */
  exercise_start_date?: string | null;
  exercise_subtitle?: string;
  exercise_tags?: string[];
}

export interface CredentialBulkProcessingInput {
  credential_ids_to_ignore?: string[];
  credential_ids_to_process?: string[];
  search_pagination_input?: SearchPaginationInput;
}

export interface CredentialContractField {
  choices?: string[];
  field_name?: string;
  field_type?: "text" | "password" | "select" | "number" | "checkbox";
  required?: boolean;
}

export interface CredentialContractOutput {
  credential_auth_method: "USERNAME_PASSWORD" | "HASH";
  credential_type: "IDENTITY";
  fields?: CredentialContractField[];
}

export interface CredentialCreatedByOutput {
  /** Creator user ID */
  user_id?: string;
  /** Creator display name */
  user_name?: string;
}

export interface CredentialFullOutput {
  /** Credential authentication method */
  credential_auth_method: "USERNAME_PASSWORD" | "HASH";
  /**
   * Credential creation timestamp
   * @format date-time
   */
  credential_created_at: string;
  /** User who created the credential */
  credential_created_by: CredentialCreatedByOutput;
  /** Credential description */
  credential_description?: string;
  /** Credential description */
  credential_hash_algorithm?: "SHA" | "NTLM";
  /** Credential ID */
  credential_id: string;
  /**
   * Last credential verification timestamp
   * @format date-time
   */
  credential_last_verified_at?: string;
  /** Credential name */
  credential_name: string;
  /** Credential status */
  credential_status?: "ACTIVE" | "INACTIVE" | "UNSET";
  /**
   * Tag IDs linked to the credential
   * @uniqueItems true
   */
  credential_tags_ids?: string[];
  /** Credential type */
  credential_type: "IDENTITY";
  /** Secret username */
  credential_username?: string;
}

export interface CredentialInput {
  credential_auth_method: "USERNAME_PASSWORD" | "HASH";
  credential_description?: string;
  credential_hash?: string;
  credential_hash_algorithm?: "SHA" | "NTLM";
  /** @minLength 1 */
  credential_name: string;
  credential_password?: string;
  credential_tags?: string[];
  credential_type: "IDENTITY";
  credential_username?: string;
}

export interface CredentialOutput {
  /** Credential authentication method */
  credential_auth_method?: "USERNAME_PASSWORD" | "HASH";
  /**
   * Credential creation timestamp
   * @format date-time
   */
  credential_created_at?: string;
  /** User who created the credential */
  credential_created_by?: CredentialCreatedByOutput;
  /** Credential ID */
  credential_id?: string;
  /**
   * Last credential verification timestamp
   * @format date-time
   */
  credential_last_verified_at?: string;
  /** Credential name */
  credential_name?: string;
  /** Credential status */
  credential_status?: "ACTIVE" | "INACTIVE" | "UNSET";
  /**
   * Tag IDs linked to the credential
   * @uniqueItems true
   */
  credential_tags_ids?: string[];
  /** Credential type */
  credential_type?: "IDENTITY";
}

export interface CustomDashboard {
  /** @format date-time */
  custom_dashboard_created_at: string;
  custom_dashboard_description?: string;
  /** @minLength 1 */
  custom_dashboard_id: string;
  /** @minLength 1 */
  custom_dashboard_name: string;
  custom_dashboard_parameters?: CustomDashboardParameters[];
  /** @format date-time */
  custom_dashboard_updated_at: string;
  custom_dashboard_widgets?: Widget[];
  listened?: boolean;
}

export interface CustomDashboardInput {
  custom_dashboard_description?: string;
  /** @minLength 1 */
  custom_dashboard_name: string;
  custom_dashboard_parameters?: CustomDashboardParametersInput[];
}

export interface CustomDashboardOutput {
  custom_dashboard_id?: string;
  custom_dashboard_name?: string;
}

export interface CustomDashboardParameters {
  /** @minLength 1 */
  custom_dashboards_parameter_id: string;
  custom_dashboards_parameter_name: string;
  custom_dashboards_parameter_type:
    | "simulation"
    | "timeRange"
    | "startDate"
    | "endDate"
    | "scenario";
  listened?: boolean;
}

export interface CustomDashboardParametersInput {
  custom_dashboards_parameter_id?: string;
  custom_dashboards_parameter_name: string;
  custom_dashboards_parameter_type:
    | "simulation"
    | "timeRange"
    | "startDate"
    | "endDate"
    | "scenario";
}

export interface CustomDomain {
  /** @format date-time */
  custom_domain_created_at: string;
  /** @minLength 1 */
  custom_domain_hostname: string;
  /** @minLength 1 */
  custom_domain_id: string;
  /** @format date-time */
  custom_domain_last_checked_at?: string;
  custom_domain_last_error?: string;
  custom_domain_status: "PENDING" | "VERIFIED" | "FAILED";
  /** @format date-time */
  custom_domain_updated_at: string;
  /** @minLength 1 */
  custom_domain_verification_token: string;
  /** @format date-time */
  custom_domain_verified_at?: string;
  listened?: boolean;
}

export interface CustomDomainInput {
  /**
   * Fully-qualified hostname to serve landing pages on, e.g. security.acme.com
   * @minLength 1
   */
  custom_domain_hostname: string;
}

export interface CustomDomainInstructions {
  /** Name of the CNAME record to create (the custom hostname itself) */
  cname_record_name?: string;
  /** Target the CNAME should point to (the platform host) */
  cname_record_value?: string;
  /** The custom hostname these instructions apply to */
  hostname?: string;
  /** Name of the TXT ownership challenge record */
  txt_record_name?: string;
  /** Value the TXT ownership challenge record must carry */
  txt_record_value?: string;
}

/** Payload to create a CVE */
export interface CveCreateInput {
  /**
   * CVSS score
   * @min 0
   * @max 10
   * @example "7.5"
   */
  cve_cvss_v31: number;
  /**
   * Date when action is due by CISA
   * @format date-time
   */
  cve_cisa_action_due?: string;
  /**
   * Date when CISA added the CVE to the exploited list
   * @format date-time
   */
  cve_cisa_exploit_add?: string;
  /** Action required by CISA */
  cve_cisa_required_action?: string;
  /** Vulnerability name used by CISA */
  cve_cisa_vulnerability_name?: string;
  /** List of linked CWEs */
  cve_cwes?: CweInput[];
  /** Description of the CVE */
  cve_description?: string;
  /**
   * External Unique CVE identifier
   * @minLength 1
   * @example "CVE-2024-0001"
   */
  cve_external_id: string;
  /**
   * Publication date of the CVE
   * @format date-time
   */
  cve_published?: string;
  /** List of reference URLs */
  cve_reference_urls?: string[];
  /** Suggested remediation */
  cve_remediation?: string;
  /**
   * Identifier of the CVE source
   * @example "MITRE"
   */
  cve_source_identifier?: string;
  /**
   * Vulnerability status
   * @example "ANALYZED"
   */
  cve_vuln_status?: "ANALYZED" | "DEFERRED" | "MODIFIED";
}

/** CWE input used in vulnerability creation/update */
export interface CweInput {
  /**
   * External CWE identifier
   * @minLength 1
   * @example "CWE-79"
   */
  cwe_external_id: string;
  /**
   * Source of the CWE
   * @example "NIST"
   */
  cwe_source?: string;
}

/** CWE output data */
export interface CweOutput {
  /**
   * CWE identifier
   * @minLength 1
   * @example "CWE-79"
   */
  cwe_external_id: string;
  /** Source of the CWE */
  cwe_source?: string;
}

export type DateHistogramWidget = UtilRequiredKeys<
  WidgetConfiguration,
  "widget_configuration_type" | "time_range" | "date_attribute"
> & {
  display_legend?: boolean;
  interval: "year" | "month" | "week" | "day" | "hour" | "quarter";
  mode: string;
  series: Series[];
  stacked?: boolean;
};

export interface DetectionRemediation {
  author_rule: "HUMAN" | "AI" | "AI_OUTDATED";
  /** @format date-time */
  detection_remediation_created_at?: string;
  /** @minLength 1 */
  detection_remediation_id: string;
  detection_remediation_payload_id: string;
  detection_remediation_security_platform: string;
  /** @format date-time */
  detection_remediation_updated_at?: string;
  detection_remediation_values: string;
  listened?: boolean;
}

export interface DetectionRemediationAIOutput {
  rules?: string;
}

/** Health check response of the detection/remediation service. */
export interface DetectionRemediationHealthResponse {
  /**
   * Name of the service
   * @example "remediation-detection-webservice"
   */
  service?: string;
  /**
   * Status of the web service. Only one possible value: "healthy"
   * @example "healthy"
   */
  status?: string;
  /**
   * Timestamp of the request
   * @example "2025-09-09T12:08:07.489773Z"
   */
  timestamp?: string;
  /**
   * Elapsed time between request initiation and service start. (format HH:MM:SS.ffffff,)
   * @example "2:07:39.269613"
   */
  up_time?: string;
  /**
   * Version of the service
   * @example "0.1.0"
   */
  version?: string;
}

export interface DetectionRemediationInput {
  author_rule: "HUMAN" | "AI" | "AI_OUTDATED";
  detection_remediation_id?: string;
  /**
   * Security platform id
   * @minLength 1
   */
  detection_remediation_security_platform: string;
  /** Value of detection remediation, for exemple: query for sentinel */
  detection_remediation_values: string;
}

export interface DetectionRemediationOutput {
  /** Author of rules: Human, AI or AI out of date (for rules generated before payload updated) */
  detection_remediation_author_rule: "HUMAN" | "AI" | "AI_OUTDATED";
  detection_remediation_id?: string;
  /** Payload id */
  detection_remediation_payload: string;
  /** Security platform id */
  detection_remediation_security_platform: string;
  /** Security platform name */
  detection_remediation_security_platform_name?: string;
  /** Value of detection remediation, for exemple: query for sentinel */
  detection_remediation_values: string;
}

export interface DirectInjectInput {
  inject_content?: object;
  inject_description?: string;
  inject_documents?: InjectDocumentInput[];
  inject_injector?: string;
  inject_injector_contract?: string;
  inject_title?: string;
  inject_users?: string[];
}

export interface DnsResolution {
  dns_resolution_hostname: string;
  listened?: boolean;
  payload_arguments?: PayloadArgument[];
  /** Organization author of the payload */
  payload_author_organization?: string;
  /** Team author of the payload */
  payload_author_team?: string;
  /** User author of the payload */
  payload_author_user?: string;
  payload_cleanup_command?: string;
  payload_cleanup_executor?: string;
  payload_collector_type?: string;
  /** @format date-time */
  payload_created_at: string;
  payload_description?: string;
  payload_detection_remediations?: DetectionRemediation[];
  payload_elevation_required?: boolean;
  payload_execution_arch: "x86_64" | "arm64" | "ALL_ARCHITECTURES";
  payload_expectations?: (
    | "ARTICLE"
    | "CHALLENGE"
    | "MANUAL"
    | "PREVENTION"
    | "DETECTION"
    | "VULNERABILITY"
  )[];
  payload_expected_security_platforms?: Record<
    string,
    (
      | "EDR"
      | "XDR"
      | "SIEM"
      | "SOAR"
      | "NDR"
      | "ISPM"
      | "EMAIL_SECURITY"
      | "LLM_FIREWALL"
      | "AI_GATEWAY"
      | "VULNERABILITY_SCANNER"
    )[]
  >;
  payload_external_id?: string;
  /** @minLength 1 */
  payload_id: string;
  /** @minLength 1 */
  payload_name: string;
  /** @uniqueItems true */
  payload_output_parsers?: OutputParser[];
  /** @minItems 1 */
  payload_platforms: (
    | "Linux"
    | "Windows"
    | "MacOS"
    | "Android"
    | "iOS"
    | "Container"
    | "Service"
    | "Generic"
    | "Internal"
    | "Unknown"
  )[];
  payload_prerequisites?: PayloadPrerequisite[];
  payload_source: "COMMUNITY" | "FILIGRAN" | "MANUAL";
  payload_status: "UNVERIFIED" | "VERIFIED" | "DEPRECATED";
  payload_type?: string;
  /** @format date-time */
  payload_updated_at: string;
  typeEnum?:
    | "COMMAND"
    | "EXECUTABLE"
    | "FILE_DROP"
    | "DNS_RESOLUTION"
    | "NETWORK_TRAFFIC"
    | "AI_ATTACK";
}

export interface Document {
  document_description?: string;
  document_exercises?: string[];
  /** @minLength 1 */
  document_id: string;
  /** @minLength 1 */
  document_name: string;
  document_scenarios?: string[];
  document_tags?: string[];
  document_target?: string;
  /** @minLength 1 */
  document_type: string;
  listened?: boolean;
}

export interface DocumentCreateInput {
  document_description?: string;
  document_exercises?: string[];
  document_scenarios?: string[];
  document_tags?: string[];
}

export interface DocumentRelationsOutput {
  /** @uniqueItems true */
  atomicTestings?: RelatedEntityOutput[];
  /** @uniqueItems true */
  challenges?: RelatedEntityOutput[];
  /** @uniqueItems true */
  channels?: RelatedEntityOutput[];
  /** @uniqueItems true */
  payloads?: RelatedEntityOutput[];
  /** @uniqueItems true */
  scenarioArticles?: RelatedEntityOutput[];
  /** @uniqueItems true */
  scenarioInjects?: RelatedEntityOutput[];
  /** @uniqueItems true */
  securityPlatforms?: RelatedEntityOutput[];
  /** @uniqueItems true */
  simulationArticles?: RelatedEntityOutput[];
  /** @uniqueItems true */
  simulationInjects?: RelatedEntityOutput[];
  /** @uniqueItems true */
  simulations?: RelatedEntityOutput[];
}

export interface DocumentTagUpdateInput {
  tags?: string[];
}

export interface DocumentUpdateInput {
  document_description?: string;
  document_exercises?: string[];
  document_scenarios?: string[];
  document_tags?: string[];
}

export interface Domain {
  /** @minLength 1 */
  domain_color: string;
  /** @format date-time */
  domain_created_at?: string;
  /** @minLength 1 */
  domain_id: string;
  /** @minLength 1 */
  domain_name: string;
  /** @format date-time */
  domain_updated_at?: string;
  listened?: boolean;
}

export interface DomainBaseInput {
  /**
   * Color of the domain
   * @minLength 1
   */
  domain_color: string;
  /**
   * Name of the domain
   * @minLength 1
   */
  domain_name: string;
}

export interface Endpoint {
  ai_target_configuration?: Record<string, any>;
  ai_target_endpoint?: string;
  ai_target_modality?: "TEXT" | "VISION" | "AUDIO" | "MULTIMODAL";
  ai_target_model?: string;
  ai_target_provider?:
    | "OPENAI_COMPATIBLE"
    | "ANTHROPIC"
    | "AZURE_OPENAI"
    | "AWS_BEDROCK"
    | "GOOGLE_VERTEX"
    | "HUGGINGFACE"
    | "OLLAMA"
    | "CUSTOM_HTTP"
    | "MCP_SERVER"
    | "AGENT_HTTP"
    | "XTM_ONE";
  ai_target_system_prompt?: string;
  ai_target_token?: string;
  asset_agents?: Agent[];
  asset_category?:
    | "HOST"
    | "CONTAINER_WORKLOAD"
    | "CLOUD_RESOURCE"
    | "WEB_APPLICATION"
    | "NETWORK_DEVICE"
    | "MOBILE_DEVICE"
    | "IOT_OT_DEVICE"
    | "IDENTITY"
    | "SAAS_APPLICATION"
    | "AI_TARGET"
    | "SECURITY_PLATFORM"
    | "GENERIC_ASSET";
  asset_cloud_native_type?: string;
  asset_cloud_provider?:
    | "AWS"
    | "AZURE"
    | "GCP"
    | "OCI"
    | "ALIBABA"
    | "KUBERNETES"
    | "OTHER";
  asset_cloud_region?: string;
  /** @format date-time */
  asset_created_at: string;
  asset_criticality?: "VERY_HIGH" | "HIGH" | "MEDIUM" | "LOW" | "UNKNOWN";
  asset_description?: string;
  asset_external_reference?: string;
  asset_hostname?: string;
  /** @minLength 1 */
  asset_id: string;
  asset_internet_facing?: boolean;
  asset_ips?: string[];
  asset_linked_person?: string;
  asset_mac_addresses?: string[];
  asset_metadata?: Record<string, any>;
  /** @minLength 1 */
  asset_name: string;
  asset_seen_ip?: string;
  /** Activity status derived from agents (ACTIVE / INACTIVE / AGENTLESS) */
  asset_status?: "ACTIVE" | "INACTIVE" | "AGENTLESS";
  asset_subcategory?:
    | "SERVER"
    | "WORKSTATION"
    | "LAPTOP"
    | "VIRTUAL_MACHINE"
    | "HYPERVISOR"
    | "MAINFRAME"
    | "THIN_CLIENT"
    | "CONTAINER"
    | "CONTAINER_IMAGE"
    | "KUBERNETES_POD"
    | "KUBERNETES_CLUSTER"
    | "KUBERNETES_NODE"
    | "SERVERLESS_FUNCTION"
    | "COMPUTE"
    | "STORAGE"
    | "DATABASE"
    | "NETWORKING"
    | "SERVERLESS"
    | "CONTAINER_REGISTRY"
    | "KUBERNETES"
    | "IAM_PRINCIPAL"
    | "SECRETS_KEY_MGMT"
    | "MESSAGING_QUEUE"
    | "ANALYTICS_DATA"
    | "AI_ML_SERVICE"
    | "IAC_TEMPLATE"
    | "CLOUD_OTHER"
    | "WEBSITE"
    | "WEB_API"
    | "SINGLE_PAGE_APP"
    | "GRAPHQL_API"
    | "WEB_SERVICE"
    | "MICROSERVICE"
    | "ROUTER"
    | "SWITCH"
    | "FIREWALL"
    | "LOAD_BALANCER"
    | "VPN_GATEWAY"
    | "WIRELESS_AP"
    | "PROXY"
    | "DNS_SERVER"
    | "DHCP_SERVER"
    | "SAN_NAS"
    | "NETWORK_OTHER"
    | "SMARTPHONE"
    | "TABLET"
    | "IOT_SENSOR"
    | "IP_CAMERA"
    | "GATEWAY"
    | "POINT_OF_SALE"
    | "MEDIA_DEVICE"
    | "PLC"
    | "RTU"
    | "HMI"
    | "SCADA_HISTORIAN"
    | "MEDICAL_DEVICE"
    | "PRINTER_PERIPHERAL"
    | "BUILDING_MGMT"
    | "USER_ACCOUNT"
    | "SERVICE_ACCOUNT"
    | "GROUP"
    | "ROLE"
    | "SHARED_MAILBOX"
    | "NON_HUMAN_IDENTITY"
    | "SAAS_APP"
    | "SAAS_TENANT"
    | "LLM_MODEL"
    | "AI_AGENT"
    | "MCP_SERVER"
    | "RAG_PIPELINE"
    | "EDR"
    | "XDR"
    | "SIEM"
    | "SOAR"
    | "NDR"
    | "ISPM"
    | "EMAIL_SECURITY"
    | "LLM_FIREWALL"
    | "AI_GATEWAY"
    | "VULNERABILITY_SCANNER";
  asset_tags?: string[];
  asset_type?: string;
  /** @format date-time */
  asset_updated_at: string;
  asset_url?: string;
  endpoint_arch?: "x86_64" | "arm64" | "Unknown";
  endpoint_is_eol?: boolean;
  endpoint_platform?:
    | "Linux"
    | "Windows"
    | "MacOS"
    | "Android"
    | "iOS"
    | "Container"
    | "Service"
    | "Generic"
    | "Internal"
    | "Unknown";
  listened?: boolean;
}

export interface EndpointInput {
  asset_category?:
    | "HOST"
    | "CONTAINER_WORKLOAD"
    | "CLOUD_RESOURCE"
    | "WEB_APPLICATION"
    | "NETWORK_DEVICE"
    | "MOBILE_DEVICE"
    | "IOT_OT_DEVICE"
    | "IDENTITY"
    | "SAAS_APPLICATION"
    | "AI_TARGET"
    | "SECURITY_PLATFORM"
    | "GENERIC_ASSET";
  asset_cloud_native_type?: string | null;
  asset_cloud_provider?:
    | "AWS"
    | "AZURE"
    | "GCP"
    | "OCI"
    | "ALIBABA"
    | "KUBERNETES"
    | "OTHER";
  asset_cloud_region?: string | null;
  asset_criticality?: "VERY_HIGH" | "HIGH" | "MEDIUM" | "LOW" | "UNKNOWN";
  asset_description?: string;
  asset_external_reference?: string;
  asset_hostname?: string;
  asset_internet_facing?: boolean | null;
  asset_ips?: string[];
  asset_linked_person?: string | null;
  asset_mac_addresses?: string[];
  asset_metadata?: Record<string, any>;
  /** @minLength 1 */
  asset_name: string;
  asset_subcategory?:
    | "SERVER"
    | "WORKSTATION"
    | "LAPTOP"
    | "VIRTUAL_MACHINE"
    | "HYPERVISOR"
    | "MAINFRAME"
    | "THIN_CLIENT"
    | "CONTAINER"
    | "CONTAINER_IMAGE"
    | "KUBERNETES_POD"
    | "KUBERNETES_CLUSTER"
    | "KUBERNETES_NODE"
    | "SERVERLESS_FUNCTION"
    | "COMPUTE"
    | "STORAGE"
    | "DATABASE"
    | "NETWORKING"
    | "SERVERLESS"
    | "CONTAINER_REGISTRY"
    | "KUBERNETES"
    | "IAM_PRINCIPAL"
    | "SECRETS_KEY_MGMT"
    | "MESSAGING_QUEUE"
    | "ANALYTICS_DATA"
    | "AI_ML_SERVICE"
    | "IAC_TEMPLATE"
    | "CLOUD_OTHER"
    | "WEBSITE"
    | "WEB_API"
    | "SINGLE_PAGE_APP"
    | "GRAPHQL_API"
    | "WEB_SERVICE"
    | "MICROSERVICE"
    | "ROUTER"
    | "SWITCH"
    | "FIREWALL"
    | "LOAD_BALANCER"
    | "VPN_GATEWAY"
    | "WIRELESS_AP"
    | "PROXY"
    | "DNS_SERVER"
    | "DHCP_SERVER"
    | "SAN_NAS"
    | "NETWORK_OTHER"
    | "SMARTPHONE"
    | "TABLET"
    | "IOT_SENSOR"
    | "IP_CAMERA"
    | "GATEWAY"
    | "POINT_OF_SALE"
    | "MEDIA_DEVICE"
    | "PLC"
    | "RTU"
    | "HMI"
    | "SCADA_HISTORIAN"
    | "MEDICAL_DEVICE"
    | "PRINTER_PERIPHERAL"
    | "BUILDING_MGMT"
    | "USER_ACCOUNT"
    | "SERVICE_ACCOUNT"
    | "GROUP"
    | "ROLE"
    | "SHARED_MAILBOX"
    | "NON_HUMAN_IDENTITY"
    | "SAAS_APP"
    | "SAAS_TENANT"
    | "LLM_MODEL"
    | "AI_AGENT"
    | "MCP_SERVER"
    | "RAG_PIPELINE"
    | "EDR"
    | "XDR"
    | "SIEM"
    | "SOAR"
    | "NDR"
    | "ISPM"
    | "EMAIL_SECURITY"
    | "LLM_FIREWALL"
    | "AI_GATEWAY"
    | "VULNERABILITY_SCANNER";
  asset_tags?: string[];
  asset_url?: string | null;
  endpoint_agent_version?: string;
  endpoint_arch?: "x86_64" | "arm64" | "Unknown";
  /** True if the endpoint is in an End of Life state */
  endpoint_is_eol?: boolean;
  endpoint_platform?:
    | "Linux"
    | "Windows"
    | "MacOS"
    | "Android"
    | "iOS"
    | "Container"
    | "Service"
    | "Generic"
    | "Internal"
    | "Unknown";
}

export interface EndpointOutput {
  /**
   * List of agents
   * @uniqueItems true
   */
  asset_agents: AgentOutput[];
  /** Asset category */
  asset_category?:
    | "HOST"
    | "CONTAINER_WORKLOAD"
    | "CLOUD_RESOURCE"
    | "WEB_APPLICATION"
    | "NETWORK_DEVICE"
    | "MOBILE_DEVICE"
    | "IOT_OT_DEVICE"
    | "IDENTITY"
    | "SAAS_APPLICATION"
    | "AI_TARGET"
    | "SECURITY_PLATFORM"
    | "GENERIC_ASSET";
  /** Cloud native type */
  asset_cloud_native_type?: string;
  /** Cloud provider */
  asset_cloud_provider?:
    | "AWS"
    | "AZURE"
    | "GCP"
    | "OCI"
    | "ALIBABA"
    | "KUBERNETES"
    | "OTHER";
  /** Cloud region */
  asset_cloud_region?: string;
  /** Asset criticality */
  asset_criticality?: "VERY_HIGH" | "HIGH" | "MEDIUM" | "LOW" | "UNKNOWN";
  /** Asset external reference */
  asset_external_reference?: string;
  /**
   * Asset Id
   * @minLength 1
   */
  asset_id: string;
  /** Whether the asset is internet-facing */
  asset_internet_facing?: boolean;
  /** Linked person (user id) for identity assets */
  asset_linked_person?: string;
  /**
   * Asset name
   * @minLength 1
   */
  asset_name: string;
  /** Asset subcategory */
  asset_subcategory?:
    | "SERVER"
    | "WORKSTATION"
    | "LAPTOP"
    | "VIRTUAL_MACHINE"
    | "HYPERVISOR"
    | "MAINFRAME"
    | "THIN_CLIENT"
    | "CONTAINER"
    | "CONTAINER_IMAGE"
    | "KUBERNETES_POD"
    | "KUBERNETES_CLUSTER"
    | "KUBERNETES_NODE"
    | "SERVERLESS_FUNCTION"
    | "COMPUTE"
    | "STORAGE"
    | "DATABASE"
    | "NETWORKING"
    | "SERVERLESS"
    | "CONTAINER_REGISTRY"
    | "KUBERNETES"
    | "IAM_PRINCIPAL"
    | "SECRETS_KEY_MGMT"
    | "MESSAGING_QUEUE"
    | "ANALYTICS_DATA"
    | "AI_ML_SERVICE"
    | "IAC_TEMPLATE"
    | "CLOUD_OTHER"
    | "WEBSITE"
    | "WEB_API"
    | "SINGLE_PAGE_APP"
    | "GRAPHQL_API"
    | "WEB_SERVICE"
    | "MICROSERVICE"
    | "ROUTER"
    | "SWITCH"
    | "FIREWALL"
    | "LOAD_BALANCER"
    | "VPN_GATEWAY"
    | "WIRELESS_AP"
    | "PROXY"
    | "DNS_SERVER"
    | "DHCP_SERVER"
    | "SAN_NAS"
    | "NETWORK_OTHER"
    | "SMARTPHONE"
    | "TABLET"
    | "IOT_SENSOR"
    | "IP_CAMERA"
    | "GATEWAY"
    | "POINT_OF_SALE"
    | "MEDIA_DEVICE"
    | "PLC"
    | "RTU"
    | "HMI"
    | "SCADA_HISTORIAN"
    | "MEDICAL_DEVICE"
    | "PRINTER_PERIPHERAL"
    | "BUILDING_MGMT"
    | "USER_ACCOUNT"
    | "SERVICE_ACCOUNT"
    | "GROUP"
    | "ROLE"
    | "SHARED_MAILBOX"
    | "NON_HUMAN_IDENTITY"
    | "SAAS_APP"
    | "SAAS_TENANT"
    | "LLM_MODEL"
    | "AI_AGENT"
    | "MCP_SERVER"
    | "RAG_PIPELINE"
    | "EDR"
    | "XDR"
    | "SIEM"
    | "SOAR"
    | "NDR"
    | "ISPM"
    | "EMAIL_SECURITY"
    | "LLM_FIREWALL"
    | "AI_GATEWAY"
    | "VULNERABILITY_SCANNER";
  /**
   * Tags
   * @uniqueItems true
   */
  asset_tags?: string[];
  /** Asset type */
  asset_type?: string;
  /**
   * Architecture
   * @minLength 1
   */
  endpoint_arch: "x86_64" | "arm64" | "Unknown";
  /**
   * Platform
   * @minLength 1
   */
  endpoint_platform:
    | "Linux"
    | "Windows"
    | "MacOS"
    | "Android"
    | "iOS"
    | "Container"
    | "Service"
    | "Generic"
    | "Internal"
    | "Unknown";
  /** The endpoint is associated with an asset group, either statically or dynamically. */
  is_static?: boolean;
}

export interface EndpointOverviewOutput {
  /** AI target endpoint URL (AI targets only) */
  ai_target_endpoint?: string;
  /** AI target modality (AI targets only) */
  ai_target_modality?: "TEXT" | "VISION" | "AUDIO" | "MULTIMODAL";
  /** AI target model (AI targets only) */
  ai_target_model?: string;
  /** AI target provider (AI targets only) */
  ai_target_provider?:
    | "OPENAI_COMPATIBLE"
    | "ANTHROPIC"
    | "AZURE_OPENAI"
    | "AWS_BEDROCK"
    | "GOOGLE_VERTEX"
    | "HUGGINGFACE"
    | "OLLAMA"
    | "CUSTOM_HTTP"
    | "MCP_SERVER"
    | "AGENT_HTTP"
    | "XTM_ONE";
  /** AI target system prompt (AI targets only) */
  ai_target_system_prompt?: string;
  /**
   * List of primary agents
   * @uniqueItems true
   */
  asset_agents: AgentOutput[];
  /** Asset groups the asset belongs to (static or dynamic membership) */
  asset_asset_groups?: AssetGroupSimple[];
  /** Asset category */
  asset_category?:
    | "HOST"
    | "CONTAINER_WORKLOAD"
    | "CLOUD_RESOURCE"
    | "WEB_APPLICATION"
    | "NETWORK_DEVICE"
    | "MOBILE_DEVICE"
    | "IOT_OT_DEVICE"
    | "IDENTITY"
    | "SAAS_APPLICATION"
    | "AI_TARGET"
    | "SECURITY_PLATFORM"
    | "GENERIC_ASSET";
  /** Cloud native type */
  asset_cloud_native_type?: string;
  /** Cloud provider */
  asset_cloud_provider?:
    | "AWS"
    | "AZURE"
    | "GCP"
    | "OCI"
    | "ALIBABA"
    | "KUBERNETES"
    | "OTHER";
  /** Cloud region */
  asset_cloud_region?: string;
  /** Asset criticality */
  asset_criticality?: "VERY_HIGH" | "HIGH" | "MEDIUM" | "LOW" | "UNKNOWN";
  /** Asset description */
  asset_description?: string;
  /** Hostname */
  asset_hostname?: string;
  /**
   * Asset Id
   * @minLength 1
   */
  asset_id: string;
  /** Whether the asset is internet-facing */
  asset_internet_facing?: boolean;
  /**
   * List IPs
   * @uniqueItems true
   */
  asset_ips?: string[];
  /** Linked person (user id) for identity assets */
  asset_linked_person?: string;
  /**
   * List of MAC addresses
   * @uniqueItems true
   */
  asset_mac_addresses?: string[];
  /** Free-form category-specific attributes */
  asset_metadata?: Record<string, any>;
  /**
   * Asset name
   * @minLength 1
   */
  asset_name: string;
  /** Seen IP */
  asset_seen_ip?: string;
  /** Asset subcategory */
  asset_subcategory?:
    | "SERVER"
    | "WORKSTATION"
    | "LAPTOP"
    | "VIRTUAL_MACHINE"
    | "HYPERVISOR"
    | "MAINFRAME"
    | "THIN_CLIENT"
    | "CONTAINER"
    | "CONTAINER_IMAGE"
    | "KUBERNETES_POD"
    | "KUBERNETES_CLUSTER"
    | "KUBERNETES_NODE"
    | "SERVERLESS_FUNCTION"
    | "COMPUTE"
    | "STORAGE"
    | "DATABASE"
    | "NETWORKING"
    | "SERVERLESS"
    | "CONTAINER_REGISTRY"
    | "KUBERNETES"
    | "IAM_PRINCIPAL"
    | "SECRETS_KEY_MGMT"
    | "MESSAGING_QUEUE"
    | "ANALYTICS_DATA"
    | "AI_ML_SERVICE"
    | "IAC_TEMPLATE"
    | "CLOUD_OTHER"
    | "WEBSITE"
    | "WEB_API"
    | "SINGLE_PAGE_APP"
    | "GRAPHQL_API"
    | "WEB_SERVICE"
    | "MICROSERVICE"
    | "ROUTER"
    | "SWITCH"
    | "FIREWALL"
    | "LOAD_BALANCER"
    | "VPN_GATEWAY"
    | "WIRELESS_AP"
    | "PROXY"
    | "DNS_SERVER"
    | "DHCP_SERVER"
    | "SAN_NAS"
    | "NETWORK_OTHER"
    | "SMARTPHONE"
    | "TABLET"
    | "IOT_SENSOR"
    | "IP_CAMERA"
    | "GATEWAY"
    | "POINT_OF_SALE"
    | "MEDIA_DEVICE"
    | "PLC"
    | "RTU"
    | "HMI"
    | "SCADA_HISTORIAN"
    | "MEDICAL_DEVICE"
    | "PRINTER_PERIPHERAL"
    | "BUILDING_MGMT"
    | "USER_ACCOUNT"
    | "SERVICE_ACCOUNT"
    | "GROUP"
    | "ROLE"
    | "SHARED_MAILBOX"
    | "NON_HUMAN_IDENTITY"
    | "SAAS_APP"
    | "SAAS_TENANT"
    | "LLM_MODEL"
    | "AI_AGENT"
    | "MCP_SERVER"
    | "RAG_PIPELINE"
    | "EDR"
    | "XDR"
    | "SIEM"
    | "SOAR"
    | "NDR"
    | "ISPM"
    | "EMAIL_SECURITY"
    | "LLM_FIREWALL"
    | "AI_GATEWAY"
    | "VULNERABILITY_SCANNER";
  /**
   * Tags
   * @uniqueItems true
   */
  asset_tags?: string[];
  /** URL */
  asset_url?: string;
  /** Architecture */
  endpoint_arch?: "x86_64" | "arm64" | "Unknown";
  /** True if the endpoint is in an End of Life state */
  endpoint_is_eol?: boolean;
  /** Platform */
  endpoint_platform?:
    | "Linux"
    | "Windows"
    | "MacOS"
    | "Android"
    | "iOS"
    | "Container"
    | "Service"
    | "Generic"
    | "Internal"
    | "Unknown";
}

export interface EndpointRegisterInput {
  agent_executed_by_user?: string;
  agent_installation_directory?: string;
  agent_installation_mode?: string;
  agent_is_elevated?: boolean;
  agent_is_service?: boolean;
  agent_service_name?: string;
  asset_category?:
    | "HOST"
    | "CONTAINER_WORKLOAD"
    | "CLOUD_RESOURCE"
    | "WEB_APPLICATION"
    | "NETWORK_DEVICE"
    | "MOBILE_DEVICE"
    | "IOT_OT_DEVICE"
    | "IDENTITY"
    | "SAAS_APPLICATION"
    | "AI_TARGET"
    | "SECURITY_PLATFORM"
    | "GENERIC_ASSET";
  asset_cloud_native_type?: string | null;
  asset_cloud_provider?:
    | "AWS"
    | "AZURE"
    | "GCP"
    | "OCI"
    | "ALIBABA"
    | "KUBERNETES"
    | "OTHER";
  asset_cloud_region?: string | null;
  asset_criticality?: "VERY_HIGH" | "HIGH" | "MEDIUM" | "LOW" | "UNKNOWN";
  asset_description?: string;
  asset_external_reference: string;
  asset_hostname?: string;
  asset_internet_facing?: boolean | null;
  asset_ips?: string[];
  asset_linked_person?: string | null;
  asset_mac_addresses?: string[];
  asset_metadata?: Record<string, any>;
  /** @minLength 1 */
  asset_name: string;
  asset_subcategory?:
    | "SERVER"
    | "WORKSTATION"
    | "LAPTOP"
    | "VIRTUAL_MACHINE"
    | "HYPERVISOR"
    | "MAINFRAME"
    | "THIN_CLIENT"
    | "CONTAINER"
    | "CONTAINER_IMAGE"
    | "KUBERNETES_POD"
    | "KUBERNETES_CLUSTER"
    | "KUBERNETES_NODE"
    | "SERVERLESS_FUNCTION"
    | "COMPUTE"
    | "STORAGE"
    | "DATABASE"
    | "NETWORKING"
    | "SERVERLESS"
    | "CONTAINER_REGISTRY"
    | "KUBERNETES"
    | "IAM_PRINCIPAL"
    | "SECRETS_KEY_MGMT"
    | "MESSAGING_QUEUE"
    | "ANALYTICS_DATA"
    | "AI_ML_SERVICE"
    | "IAC_TEMPLATE"
    | "CLOUD_OTHER"
    | "WEBSITE"
    | "WEB_API"
    | "SINGLE_PAGE_APP"
    | "GRAPHQL_API"
    | "WEB_SERVICE"
    | "MICROSERVICE"
    | "ROUTER"
    | "SWITCH"
    | "FIREWALL"
    | "LOAD_BALANCER"
    | "VPN_GATEWAY"
    | "WIRELESS_AP"
    | "PROXY"
    | "DNS_SERVER"
    | "DHCP_SERVER"
    | "SAN_NAS"
    | "NETWORK_OTHER"
    | "SMARTPHONE"
    | "TABLET"
    | "IOT_SENSOR"
    | "IP_CAMERA"
    | "GATEWAY"
    | "POINT_OF_SALE"
    | "MEDIA_DEVICE"
    | "PLC"
    | "RTU"
    | "HMI"
    | "SCADA_HISTORIAN"
    | "MEDICAL_DEVICE"
    | "PRINTER_PERIPHERAL"
    | "BUILDING_MGMT"
    | "USER_ACCOUNT"
    | "SERVICE_ACCOUNT"
    | "GROUP"
    | "ROLE"
    | "SHARED_MAILBOX"
    | "NON_HUMAN_IDENTITY"
    | "SAAS_APP"
    | "SAAS_TENANT"
    | "LLM_MODEL"
    | "AI_AGENT"
    | "MCP_SERVER"
    | "RAG_PIPELINE"
    | "EDR"
    | "XDR"
    | "SIEM"
    | "SOAR"
    | "NDR"
    | "ISPM"
    | "EMAIL_SECURITY"
    | "LLM_FIREWALL"
    | "AI_GATEWAY"
    | "VULNERABILITY_SCANNER";
  asset_tags?: string[];
  asset_url?: string | null;
  elevated?: boolean;
  endpoint_agent_version?: string;
  endpoint_arch?: "x86_64" | "arm64" | "Unknown";
  /** True if the endpoint is in an End of Life state */
  endpoint_is_eol?: boolean;
  endpoint_platform?:
    | "Linux"
    | "Windows"
    | "MacOS"
    | "Android"
    | "iOS"
    | "Container"
    | "Service"
    | "Generic"
    | "Internal"
    | "Unknown";
  seenIp?: string;
  service?: boolean;
}

export interface EndpointSimple {
  /** Asset category (taxonomy: HOST, WEB_APPLICATION, CLOUD_RESOURCE, ...) */
  asset_category?: string;
  /**
   * Asset Id
   * @minLength 1
   */
  asset_id: string;
  /**
   * Asset name
   * @minLength 1
   */
  asset_name: string;
  /** Asset type discriminator (e.g. Endpoint, SecurityPlatform) */
  asset_type?: string;
  /** OS platform when the asset is an endpoint */
  endpoint_platform?: string;
}

export interface EndpointTarget {
  target_category?: string;
  target_detection_status?:
    | "FAILED"
    | "PENDING"
    | "PARTIAL"
    | "UNKNOWN"
    | "SUCCESS";
  target_execution_status?:
    | "FAILED"
    | "PENDING"
    | "PARTIAL"
    | "UNKNOWN"
    | "SUCCESS";
  target_human_response_status?:
    | "FAILED"
    | "PENDING"
    | "PARTIAL"
    | "UNKNOWN"
    | "SUCCESS";
  /** @minLength 1 */
  target_id: string;
  target_name?: string;
  target_prevention_status?:
    | "FAILED"
    | "PENDING"
    | "PARTIAL"
    | "UNKNOWN"
    | "SUCCESS";
  target_subtype?: string;
  /** @uniqueItems true */
  target_tags?: string[];
  target_type?: string;
  target_vulnerability_status?:
    | "FAILED"
    | "PENDING"
    | "PARTIAL"
    | "UNKNOWN"
    | "SUCCESS";
}

export interface EndpointTargetOutput {
  /**
   * List agents installed
   * @uniqueItems true
   */
  asset_agents?: AgentOutput[];
  /** Hostname */
  asset_hostname?: string;
  /**
   * Asset Id
   * @minLength 1
   */
  asset_id: string;
  /**
   * List IPs
   * @uniqueItems true
   */
  asset_ips?: string[];
  /** Seen IP */
  asset_seen_ip?: string;
}

export interface EngineSortField {
  direction: "ASC" | "DESC";
  fieldName: string;
}

export interface EntitiesPaginationInput {
  /** Pagination to set (optional) */
  pagination?: Pagination;
  /** Parameters to set */
  parameters?: Record<string, string>;
}

export interface EsAsset {
  asset_category?: string;
  asset_description?: string;
  asset_external_reference?: string;
  asset_hostname?: string;
  /** @uniqueItems true */
  asset_ips?: string[];
  /** @uniqueItems true */
  asset_mac_addresses?: string[];
  asset_name?: string;
  asset_seen_ip?: string;
  /** @format date-time */
  base_created_at?: string;
  base_dependencies?: string[];
  base_entity?: string;
  /** @uniqueItems true */
  base_findings_side?: string[];
  base_id?: string;
  base_representative?: string;
  base_restrictions?: string[];
  /** @uniqueItems true */
  base_scenario_side?: string[];
  /** @uniqueItems true */
  base_simulation_side?: string[];
  /** @uniqueItems true */
  base_tags_side?: string[];
  base_tenant_side?: string;
  /** @format date-time */
  base_updated_at?: string;
  endpoint_arch?: string;
  endpoint_is_eol?: boolean;
  endpoint_platform?: string;
}

export interface EsAssetGroup {
  /** @format date-time */
  base_created_at?: string;
  base_dependencies?: string[];
  base_entity?: string;
  base_id?: string;
  base_representative?: string;
  base_restrictions?: string[];
  base_tenant_side?: string;
  /** @format date-time */
  base_updated_at?: string;
  name?: string;
}

export interface EsAttackPath {
  /** @uniqueItems true */
  attackPatternChildrenIds?: string[];
  /** @minLength 1 */
  attackPatternExternalId: string;
  /** @minLength 1 */
  attackPatternId: string;
  /** @minLength 1 */
  attackPatternName: string;
  /** @uniqueItems true */
  injectIds?: string[];
  killChainPhases?: KillChainPhaseObject[];
  /** @format int64 */
  value?: number;
}

export interface EsAttackPattern {
  base_attack_pattern_side?: string;
  /** @format date-time */
  base_created_at?: string;
  base_dependencies?: string[];
  base_entity?: string;
  base_id?: string;
  /** @uniqueItems true */
  base_kill_chain_phases_side?: string[];
  base_representative?: string;
  base_restrictions?: string[];
  base_tenant_side?: string;
  /** @format date-time */
  base_updated_at?: string;
  description?: string;
  externalId?: string;
  name?: string;
  platforms?: string[];
  stixId?: string;
}

export interface EsAvgs {
  security_domain_average: EsDomainsAvgData[];
}

export type EsBase = BaseEsBase &
  (
    | BaseEsBaseBaseEntityMapping<"attack-pattern", EsAttackPattern>
    | BaseEsBaseBaseEntityMapping<"asset", EsAsset>
    | BaseEsBaseBaseEntityMapping<"finding", EsFinding>
    | BaseEsBaseBaseEntityMapping<"inject", EsInject>
    | BaseEsBaseBaseEntityMapping<"expectation-inject", EsInjectExpectation>
    | BaseEsBaseBaseEntityMapping<"simulation", EsSimulation>
    | BaseEsBaseBaseEntityMapping<"scenario", EsScenario>
    | BaseEsBaseBaseEntityMapping<"tag", EsTag>
    | BaseEsBaseBaseEntityMapping<"vulnerable-endpoint", EsVulnerableEndpoint>
    | BaseEsBaseBaseEntityMapping<"team", EsTeam>
    | BaseEsBaseBaseEntityMapping<"security-platform", EsSecurityPlatform>
    | BaseEsBaseBaseEntityMapping<"security-domain", EsSecurityDomain>
    | BaseEsBaseBaseEntityMapping<"asset-group", EsAssetGroup>
  );

export interface EsCountInterval {
  /** @format int64 */
  difference_count: number;
  /** @format int64 */
  interval_count: number;
  /** @format int64 */
  previous_interval_count: number;
}

export interface EsDomainsAvgData {
  data: EsSeries[];
  /** @minLength 1 */
  label: string;
}

export interface EsEntities {
  /** List of data from elasticSearch */
  es_datas: EsBase[];
  /**
   * Current page number
   * @format int64
   */
  page_number: number;
  /**
   * Total datas per pages
   * @format int64
   */
  page_size: number;
  /**
   * Total datas
   * @format int64
   */
  total: number;
  /**
   * Current page number
   * @format int64
   */
  total_pages: number;
}

export interface EsFinding {
  /** @format date-time */
  base_created_at?: string;
  base_dependencies?: string[];
  /** @uniqueItems true */
  base_endpoint_side?: string[];
  base_entity?: string;
  base_id?: string;
  base_inject_side?: string;
  base_representative?: string;
  base_restrictions?: string[];
  base_scenario_side?: string;
  base_simulation_side?: string;
  base_tenant_side?: string;
  /** @format date-time */
  base_updated_at?: string;
  finding_field?: string;
  finding_type?: string;
  finding_value?: string;
}

export interface EsInject {
  /** @uniqueItems true */
  base_asset_groups_side?: string[];
  /** @uniqueItems true */
  base_assets_side?: string[];
  /** @uniqueItems true */
  base_attack_patterns_children_side?: string[];
  /** @uniqueItems true */
  base_attack_patterns_side?: string[];
  /** @format date-time */
  base_created_at?: string;
  base_dependencies?: string[];
  base_entity?: string;
  base_id?: string;
  /** @uniqueItems true */
  base_inject_children_side?: string[];
  base_inject_contract_side?: string;
  /** @uniqueItems true */
  base_kill_chain_phases_side?: string[];
  /** @uniqueItems true */
  base_platforms_side_denormalized?: string[];
  base_representative?: string;
  base_restrictions?: string[];
  base_scenario_side?: string;
  base_simulation_side?: string;
  /** @uniqueItems true */
  base_tags_side?: string[];
  /** @uniqueItems true */
  base_teams_side?: string[];
  base_tenant_side?: string;
  /** @format date-time */
  base_updated_at?: string;
  /** @format date-time */
  execution_date?: string;
  inject_status?: string;
  inject_title?: string;
}

export interface EsInjectExpectation {
  base_asset_group_side?: string;
  base_asset_side?: string;
  /** @uniqueItems true */
  base_attack_patterns_side?: string[];
  /** @format date-time */
  base_created_at?: string;
  base_dependencies?: string[];
  base_entity?: string;
  base_id?: string;
  base_inject_side?: string;
  base_representative?: string;
  base_restrictions?: string[];
  base_scenario_side?: string;
  /** @uniqueItems true */
  base_security_domains_side?: string[];
  /** @uniqueItems true */
  base_security_platforms_side?: string[];
  base_simulation_side?: string;
  base_team_side?: string;
  base_tenant_side?: string;
  /** @format date-time */
  base_updated_at?: string;
  base_user_side?: string;
  /** @format date-time */
  execution_date?: string;
  inject_expectation_description?: string;
  /** @format double */
  inject_expectation_expected_score?: number;
  /** @format int64 */
  inject_expectation_expiration_time?: number;
  inject_expectation_group?: boolean;
  inject_expectation_name?: string;
  inject_expectation_results?: string;
  /** @format double */
  inject_expectation_score?: number;
  inject_expectation_status?: string;
  inject_expectation_type?: string;
  inject_title?: string;
}

export interface EsScenario {
  /** @uniqueItems true */
  base_asset_groups_side?: string[];
  /** @uniqueItems true */
  base_assets_side?: string[];
  /** @format date-time */
  base_created_at?: string;
  base_dependencies?: string[];
  base_entity?: string;
  base_id?: string;
  /** @uniqueItems true */
  base_platforms_side_denormalized?: string[];
  base_representative?: string;
  base_restrictions?: string[];
  /** @uniqueItems true */
  base_tags_side?: string[];
  /** @uniqueItems true */
  base_teams_side?: string[];
  base_tenant_side?: string;
  /** @format date-time */
  base_updated_at?: string;
  name?: string;
  status?: string;
}

export interface EsSearch {
  base_created_at?: string;
  base_entity?: string;
  /** @minLength 1 */
  base_id: string;
  base_representative?: string;
  /** @format double */
  base_score?: number;
  base_updated_at?: string;
}

export interface EsSecurityDomain {
  /** @format date-time */
  base_created_at?: string;
  base_dependencies?: string[];
  base_entity?: string;
  base_id?: string;
  base_representative?: string;
  base_restrictions?: string[];
  base_tenant_side?: string;
  /** @format date-time */
  base_updated_at?: string;
  domain_color?: string;
}

export interface EsSecurityPlatform {
  /** @format date-time */
  base_created_at?: string;
  base_dependencies?: string[];
  base_entity?: string;
  base_id?: string;
  base_representative?: string;
  base_restrictions?: string[];
  base_tenant_side?: string;
  /** @format date-time */
  base_updated_at?: string;
  name?: string;
}

export interface EsSeries {
  color?: string;
  data?: EsSeriesData[];
  label?: string;
  /** @format int64 */
  value?: number;
}

export interface EsSeriesData {
  key?: string;
  label?: string;
  /** @format int64 */
  value?: number;
}

export interface EsSimulation {
  /** @uniqueItems true */
  base_asset_groups_side?: string[];
  /** @uniqueItems true */
  base_assets_side?: string[];
  /** @format date-time */
  base_created_at?: string;
  base_dependencies?: string[];
  base_entity?: string;
  base_id?: string;
  /** @uniqueItems true */
  base_platforms_side_denormalized?: string[];
  base_representative?: string;
  base_restrictions?: string[];
  base_scenario_side?: string;
  /** @uniqueItems true */
  base_tags_side?: string[];
  /** @uniqueItems true */
  base_teams_side?: string[];
  base_tenant_side?: string;
  /** @format date-time */
  base_updated_at?: string;
  /** @format date-time */
  execution_date?: string;
  name?: string;
  status?: string;
}

export interface EsTag {
  /** @format date-time */
  base_created_at?: string;
  base_dependencies?: string[];
  base_entity?: string;
  base_id?: string;
  base_representative?: string;
  base_restrictions?: string[];
  base_tenant_side?: string;
  /** @format date-time */
  base_updated_at?: string;
  tag_color?: string;
}

export interface EsTeam {
  /** @format date-time */
  base_created_at?: string;
  base_dependencies?: string[];
  base_entity?: string;
  base_id?: string;
  base_representative?: string;
  base_restrictions?: string[];
  base_tenant_side?: string;
  /** @format date-time */
  base_updated_at?: string;
  name?: string;
}

export interface EsVulnerableEndpoint {
  /** @uniqueItems true */
  base_agents_side?: string[];
  /** @format date-time */
  base_created_at?: string;
  base_dependencies?: string[];
  base_entity?: string;
  /** @uniqueItems true */
  base_findings_side?: string[];
  base_id?: string;
  base_representative?: string;
  base_restrictions?: string[];
  base_scenario_side?: string;
  base_simulation_side?: string;
  /** @uniqueItems true */
  base_tags_side?: string[];
  base_tenant_side?: string;
  /** @format date-time */
  base_updated_at?: string;
  vulnerable_endpoint_action?: string;
  vulnerable_endpoint_agents_active_status?: boolean[];
  vulnerable_endpoint_agents_privileges?: string[];
  vulnerable_endpoint_architecture?: string;
  vulnerable_endpoint_findings_summary?: string;
  vulnerable_endpoint_hostname?: string;
  vulnerable_endpoint_id?: string;
  vulnerable_endpoint_platform?: string;
}

export interface Evaluation {
  /** @format date-time */
  evaluation_created_at: string;
  /** @minLength 1 */
  evaluation_id: string;
  evaluation_objective: string;
  /** @format int64 */
  evaluation_score?: number;
  /** @format date-time */
  evaluation_updated_at: string;
  evaluation_user: string;
  listened?: boolean;
}

export interface EvaluationInput {
  /** @format int64 */
  evaluation_score?: number;
}

export interface Event {
  /** @minLength 1 */
  stix_objects: string;
}

export interface EventInput {
  /** @minItems 1 */
  event_conditions: ConditionCreateInput[];
  event_description?: string;
  /** @minLength 1 */
  event_name: string;
  event_step_ids?: string[];
  /** @minLength 1 */
  event_workflow_id: string;
}

export interface EventOutput {
  event_conditions?: ConditionOutput[];
  /** @format date-time */
  event_created_at?: string;
  event_description?: string;
  /** @minLength 1 */
  event_id: string;
  /** @minLength 1 */
  event_name: string;
  /** @format date-time */
  event_updated_at?: string;
  /** @minLength 1 */
  event_workflow_id: string;
}

export interface Executable {
  executable_file: string;
  listened?: boolean;
  payload_arguments?: PayloadArgument[];
  /** Organization author of the payload */
  payload_author_organization?: string;
  /** Team author of the payload */
  payload_author_team?: string;
  /** User author of the payload */
  payload_author_user?: string;
  payload_cleanup_command?: string;
  payload_cleanup_executor?: string;
  payload_collector_type?: string;
  /** @format date-time */
  payload_created_at: string;
  payload_description?: string;
  payload_detection_remediations?: DetectionRemediation[];
  payload_elevation_required?: boolean;
  payload_execution_arch: "x86_64" | "arm64" | "ALL_ARCHITECTURES";
  payload_expectations?: (
    | "ARTICLE"
    | "CHALLENGE"
    | "MANUAL"
    | "PREVENTION"
    | "DETECTION"
    | "VULNERABILITY"
  )[];
  payload_expected_security_platforms?: Record<
    string,
    (
      | "EDR"
      | "XDR"
      | "SIEM"
      | "SOAR"
      | "NDR"
      | "ISPM"
      | "EMAIL_SECURITY"
      | "LLM_FIREWALL"
      | "AI_GATEWAY"
      | "VULNERABILITY_SCANNER"
    )[]
  >;
  payload_external_id?: string;
  /** @minLength 1 */
  payload_id: string;
  /** @minLength 1 */
  payload_name: string;
  /** @uniqueItems true */
  payload_output_parsers?: OutputParser[];
  /** @minItems 1 */
  payload_platforms: (
    | "Linux"
    | "Windows"
    | "MacOS"
    | "Android"
    | "iOS"
    | "Container"
    | "Service"
    | "Generic"
    | "Internal"
    | "Unknown"
  )[];
  payload_prerequisites?: PayloadPrerequisite[];
  payload_source: "COMMUNITY" | "FILIGRAN" | "MANUAL";
  payload_status: "UNVERIFIED" | "VERIFIED" | "DEPRECATED";
  payload_type?: string;
  /** @format date-time */
  payload_updated_at: string;
  typeEnum?:
    | "COMMAND"
    | "EXECUTABLE"
    | "FILE_DROP"
    | "DNS_RESOLUTION"
    | "NETWORK_TRAFFIC"
    | "AI_ATTACK";
}

export interface ExecutionTrace {
  agent?: string;
  execution_action?:
    | "START"
    | "PREREQUISITE_CHECK"
    | "PREREQUISITE_EXECUTION"
    | "EXECUTION"
    | "CLEANUP_EXECUTION"
    | "COMPLETE";
  execution_context_identifiers?: string[];
  /** @format date-time */
  execution_created_at: string;
  execution_message: string;
  execution_status?:
    | "EXECUTED"
    | "EXECUTED_WITH_CLEANUP_FAILURE"
    | "WARNING"
    | "ACCESS_DENIED"
    | "ERROR"
    | "COMMAND_NOT_FOUND"
    | "COMMAND_CANNOT_BE_EXECUTED"
    | "PREREQUISITE_FAILED"
    | "INVALID_USAGE"
    | "TIMEOUT"
    | "INTERRUPTED"
    | "ASSET_AGENTLESS"
    | "AGENT_INACTIVE"
    | "AGENT_OVERLOADED"
    | "INFO"
    | "PARTIAL"
    | "MAYBE_PREVENTED"
    | "MAYBE_PARTIAL_PREVENTED";
  /** @format date-time */
  execution_time?: string;
  execution_trace_id: string;
  /** @format date-time */
  execution_updated_at: string;
  injectStatus?: string;
  injectTestStatus?: string;
  listened?: boolean;
}

/** Represents a single execution trace detail */
export interface ExecutionTraceOutput {
  /**
   * The action that created this execution trace
   * @example "START, PREREQUISITE_CHECK, PREREQUISITE_EXECUTION, EXECUTION, CLEANUP_EXECUTION or COMPLETE"
   */
  execution_action:
    | "START"
    | "PREREQUISITE_CHECK"
    | "PREREQUISITE_EXECUTION"
    | "EXECUTION"
    | "CLEANUP_EXECUTION"
    | "COMPLETE";
  execution_agent?: AgentOutput;
  /** A detailed message describing the execution */
  execution_message: string;
  /**
   * The status of the execution trace
   * @example "EXECUTED, ERROR, COMMAND_NOT_FOUND, WARNING, COMMAND_CANNOT_BE_EXECUTED.."
   */
  execution_status:
    | "EXECUTED"
    | "EXECUTED_WITH_CLEANUP_FAILURE"
    | "WARNING"
    | "ACCESS_DENIED"
    | "ERROR"
    | "COMMAND_NOT_FOUND"
    | "COMMAND_CANNOT_BE_EXECUTED"
    | "PREREQUISITE_FAILED"
    | "INVALID_USAGE"
    | "TIMEOUT"
    | "INTERRUPTED"
    | "ASSET_AGENTLESS"
    | "AGENT_INACTIVE"
    | "AGENT_OVERLOADED"
    | "INFO"
    | "PARTIAL"
    | "MAYBE_PREVENTED"
    | "MAYBE_PARTIAL_PREVENTED";
  /** @format date-time */
  execution_time: string;
}

export interface Executor {
  executor_background_color?: string;
  /** @format date-time */
  executor_created_at: string;
  executor_doc?: string;
  executor_external?: boolean;
  /** @minLength 1 */
  executor_id: string;
  /** @minLength 1 */
  executor_name: string;
  executor_platforms?: string[];
  /** @minLength 1 */
  executor_type: string;
  /** @format date-time */
  executor_updated_at: string;
  listened?: boolean;
}

export interface ExecutorCreateInput {
  /** @minLength 1 */
  executor_id: string;
  /** @minLength 1 */
  executor_name: string;
  executor_platforms?: string[];
  /** @minLength 1 */
  executor_type: string;
}

/** Executor output */
export interface ExecutorOutput {
  /** Catalog simple output */
  catalog?: CatalogConnectorSimpleOutput;
  connector_instance?: ConnectorInstanceOutput;
  executor_background_color?: string;
  executor_doc?: string;
  /**
   * Executor id
   * @minLength 1
   */
  executor_id: string;
  /** @minLength 1 */
  executor_name: string;
  executor_platforms?: string[];
  /** @minLength 1 */
  executor_type: string;
  /** @format date-time */
  executor_updated_at?: string;
  existing_executor?: boolean;
  is_verified?: boolean;
}

export interface ExecutorUpdateInput {
  /** @format date-time */
  executor_last_execution?: string;
}

export interface Exercise {
  /** @format int64 */
  exercise_all_users_number?: number;
  exercise_articles?: string[];
  exercise_autonomous?: boolean;
  exercise_category?: string;
  /** @format int64 */
  exercise_communications_number?: number;
  /** @format date-time */
  exercise_created_at: string;
  exercise_custom_dashboard?: string;
  exercise_default_kill_chain?: string;
  exercise_description?: string;
  exercise_documents?: string[];
  /** @format date-time */
  exercise_end_date?: string;
  exercise_expectations_drift_dismissed?: boolean;
  /** @minLength 1 */
  exercise_id: string;
  exercise_injects?: string[];
  exercise_injects_statistics?: Record<string, number>;
  exercise_kill_chain_phases?: KillChainPhase[];
  exercise_lessons_anonymized?: boolean;
  /** @format int64 */
  exercise_lessons_answers_number?: number;
  exercise_lessons_categories?: string[];
  exercise_lessons_enabled?: boolean;
  exercise_logo_dark?: string;
  exercise_logo_light?: string;
  /** @format int64 */
  exercise_logs_number?: number;
  /**
   * @format email
   * @minLength 1
   */
  exercise_mail_from: string;
  /**
   * @minLength 0
   * @maxLength 100
   * @pattern ^[^\r\n\x00]*$
   */
  exercise_mail_from_name?: string;
  exercise_mails_reply_to?: string[];
  exercise_main_focus?: string;
  exercise_message_footer?: string;
  exercise_message_header?: string;
  /** @minLength 1 */
  exercise_name: string;
  /** @format date-time */
  exercise_next_inject_date?: string;
  exercise_next_possible_status?: (
    | "SCHEDULED"
    | "CANCELED"
    | "RUNNING"
    | "PAUSED"
    | "FINISHED"
  )[];
  exercise_observers?: string[];
  exercise_pauses?: string[];
  exercise_planners?: string[];
  exercise_platforms?: (
    | "Linux"
    | "Windows"
    | "MacOS"
    | "Android"
    | "iOS"
    | "Container"
    | "Service"
    | "Generic"
    | "Internal"
    | "Unknown"
  )[];
  exercise_scenario?: string;
  /** @format double */
  exercise_score?: number;
  exercise_severity?: "low" | "medium" | "high" | "critical";
  /** @format date-time */
  exercise_start_date?: string;
  exercise_status: "SCHEDULED" | "CANCELED" | "RUNNING" | "PAUSED" | "FINISHED";
  exercise_subtitle?: string;
  exercise_tags?: string[];
  exercise_teams?: string[];
  exercise_teams_users?: ExerciseTeamUser[];
  /** @format date-time */
  exercise_updated_at: string;
  exercise_users?: string[];
  /** @format int64 */
  exercise_users_number?: number;
  exercise_variables?: string[];
  listened?: boolean;
}

export interface ExerciseBulkProcessingInput {
  exercise_ids_to_ignore?: string[];
  exercise_ids_to_process?: string[];
  search_pagination_input?: SearchPaginationInput;
}

export interface ExerciseSimple {
  /** Whether this simulation was created by an autonomous (AI-driven) run */
  exercise_autonomous?: boolean;
  /** Exercise Category */
  exercise_category?: string;
  exercise_global_score: ExpectationResultsByType[];
  /**
   * Exercise Id
   * @minLength 1
   */
  exercise_id: string;
  /**
   * Exercise Name
   * @minLength 1
   */
  exercise_name: string;
  /**
   * Exercise Start Date
   * @format date-time
   */
  exercise_start_date?: string;
  /** Exercise status */
  exercise_status?:
    | "SCHEDULED"
    | "CANCELED"
    | "RUNNING"
    | "PAUSED"
    | "FINISHED";
  /** Exercise Subtitle */
  exercise_subtitle?: string;
  /**
   * Tags
   * @uniqueItems true
   */
  exercise_tags?: string[];
  exercise_targets?: TargetSimple[];
  /**
   * Exercise Update Date
   * @format date-time
   */
  exercise_updated_at?: string;
  /** Workflow ID associated with the simulation */
  exercise_workflow_id?: string;
}

export interface ExerciseTeamPlayersEnableInput {
  exercise_team_players?: string[];
}

export interface ExerciseTeamUser {
  exercise_id?: string;
  team_id?: string;
  user_id?: string;
}

export interface ExerciseUpdateLogoInput {
  exercise_logo_dark?: string;
  exercise_logo_light?: string;
}

export interface ExerciseUpdateStartDateInput {
  /** @format date-time */
  exercise_start_date?: string;
}

export interface ExerciseUpdateStatusInput {
  exercise_status?:
    | "SCHEDULED"
    | "CANCELED"
    | "RUNNING"
    | "PAUSED"
    | "FINISHED";
}

export interface ExerciseUpdateTagsInput {
  apply_tag_rule?: boolean;
  exercise_tags?: string[];
}

export interface ExerciseUpdateTeamsInput {
  exercise_teams?: string[];
}

export interface ExercisesGlobalScoresInput {
  exercise_ids: string[];
}

export interface ExercisesGlobalScoresOutput {
  global_scores_by_exercise_ids: Record<string, ExpectationResultsByType[]>;
}

export interface ExpectationResultsByType {
  avgResult: "FAILED" | "PENDING" | "PARTIAL" | "UNKNOWN" | "SUCCESS";
  distribution: ResultDistribution[];
  type: "DETECTION" | "HUMAN_RESPONSE" | "PREVENTION" | "VULNERABILITY";
}

export interface ExpectationUpdateInput {
  /** @format double */
  expectation_score: number;
  source_id: string;
  source_name: string;
  source_platform?: string;
  source_type: string;
}

export interface ExpectationsDriftDismissInput {
  /** True to dismiss the drift warning, false to restore it */
  dismissed: boolean;
}

export interface ExpectationsDriftOutput {
  /** True when at least one inject drifted from its contract expectations */
  drift_detected: boolean;
  /** True when the drift warning was dismissed (customized on purpose); shared between users and reset on realignment */
  drift_dismissed: boolean;
  /**
   * Number of injects whose expectations drifted from their contract
   * @format int32
   */
  drifted_inject_count: number;
  /**
   * Number of injects whose injector contract exposes expectations
   * @format int32
   */
  total_inject_count: number;
}

export interface ExpectationsRealignOutput {
  /**
   * Number of injects whose expectations were realigned onto their contract
   * @format int32
   */
  realigned_inject_count: number;
}

export interface ExportMapperInput {
  export_mapper_name?: string;
  ids_to_export: string[];
}

export interface ExportOptionsInput {
  with_players?: boolean;
  with_teams?: boolean;
  with_variable_values?: boolean;
}

export interface FileDrop {
  file_drop_file: string;
  listened?: boolean;
  payload_arguments?: PayloadArgument[];
  /** Organization author of the payload */
  payload_author_organization?: string;
  /** Team author of the payload */
  payload_author_team?: string;
  /** User author of the payload */
  payload_author_user?: string;
  payload_cleanup_command?: string;
  payload_cleanup_executor?: string;
  payload_collector_type?: string;
  /** @format date-time */
  payload_created_at: string;
  payload_description?: string;
  payload_detection_remediations?: DetectionRemediation[];
  payload_elevation_required?: boolean;
  payload_execution_arch: "x86_64" | "arm64" | "ALL_ARCHITECTURES";
  payload_expectations?: (
    | "ARTICLE"
    | "CHALLENGE"
    | "MANUAL"
    | "PREVENTION"
    | "DETECTION"
    | "VULNERABILITY"
  )[];
  payload_expected_security_platforms?: Record<
    string,
    (
      | "EDR"
      | "XDR"
      | "SIEM"
      | "SOAR"
      | "NDR"
      | "ISPM"
      | "EMAIL_SECURITY"
      | "LLM_FIREWALL"
      | "AI_GATEWAY"
      | "VULNERABILITY_SCANNER"
    )[]
  >;
  payload_external_id?: string;
  /** @minLength 1 */
  payload_id: string;
  /** @minLength 1 */
  payload_name: string;
  /** @uniqueItems true */
  payload_output_parsers?: OutputParser[];
  /** @minItems 1 */
  payload_platforms: (
    | "Linux"
    | "Windows"
    | "MacOS"
    | "Android"
    | "iOS"
    | "Container"
    | "Service"
    | "Generic"
    | "Internal"
    | "Unknown"
  )[];
  payload_prerequisites?: PayloadPrerequisite[];
  payload_source: "COMMUNITY" | "FILIGRAN" | "MANUAL";
  payload_status: "UNVERIFIED" | "VERIFIED" | "DEPRECATED";
  payload_type?: string;
  /** @format date-time */
  payload_updated_at: string;
  typeEnum?:
    | "COMMAND"
    | "EXECUTABLE"
    | "FILE_DROP"
    | "DNS_RESOLUTION"
    | "NETWORK_TRAFFIC"
    | "AI_ATTACK";
}

export interface Filter {
  id: string;
  key: string;
  mode?: "and" | "or";
  operator?:
    | "eq"
    | "not_eq"
    | "contains"
    | "not_contains"
    | "starts_with"
    | "not_starts_with"
    | "gt"
    | "gte"
    | "lt"
    | "lte"
    | "empty"
    | "not_empty";
  values?: string[];
}

export interface FilterGroup {
  filters?: Filter[];
  mode: "and" | "or";
}

export interface Finding {
  /** @uniqueItems true */
  finding_asset_groups?: AssetGroup[];
  finding_assets?: string[];
  /** @format date-time */
  finding_created_at: string;
  /** @minLength 1 */
  finding_field: string;
  /** @minLength 1 */
  finding_id: string;
  finding_inject_id?: string;
  /** @deprecated */
  finding_labels?: string[];
  finding_name?: string;
  finding_scenario?: Scenario;
  finding_simulation?: Exercise;
  finding_tags?: string[];
  finding_teams?: string[];
  finding_type:
    | "text"
    | "action_output"
    | "number"
    | "port"
    | "portscan"
    | "ipv4"
    | "ipv6"
    | "credentials"
    | "cve"
    | "username"
    | "email"
    | "share"
    | "file"
    | "admin_username"
    | "group"
    | "computer"
    | "password_policy"
    | "delegation"
    | "sid"
    | "vulnerability"
    | "account_with_password_not_required"
    | "asreproastable_account"
    | "kerberoastable_account"
    | "expectation_signature";
  /** @format date-time */
  finding_updated_at: string;
  finding_users?: string[];
  /** @minLength 1 */
  finding_value: string;
  listened?: boolean;
}

export interface FindingInput {
  /** @minLength 1 */
  finding_field: string;
  finding_inject_id?: string;
  finding_labels?: string[];
  finding_type:
    | "text"
    | "action_output"
    | "number"
    | "port"
    | "portscan"
    | "ipv4"
    | "ipv6"
    | "credentials"
    | "cve"
    | "username"
    | "email"
    | "share"
    | "file"
    | "admin_username"
    | "group"
    | "computer"
    | "password_policy"
    | "delegation"
    | "sid"
    | "vulnerability"
    | "account_with_password_not_required"
    | "asreproastable_account"
    | "kerberoastable_account"
    | "expectation_signature";
  /** @minLength 1 */
  finding_value: string;
}

export interface FindingSummaryOutput {
  /**
   * Number of distinct impacted asset groups across all occurrences
   * @format int64
   */
  finding_asset_groups_count?: number;
  /**
   * Number of distinct impacted assets across all occurrences
   * @format int64
   */
  finding_assets_count?: number;
  /**
   * First time this finding was seen across all occurrences
   * @format date-time
   */
  finding_first_seen?: string;
  /** Representative finding id used to resolve the (type, value) group */
  finding_id?: string;
  /**
   * Last time this finding was seen across all occurrences
   * @format date-time
   */
  finding_last_seen?: string;
  /**
   * Number of occurrences (one per inject that produced this finding)
   * @format int64
   */
  finding_occurrences?: number;
  /**
   * Number of distinct impacted teams across all occurrences
   * @format int64
   */
  finding_teams_count?: number;
  /** Finding type */
  finding_type:
    | "text"
    | "action_output"
    | "number"
    | "port"
    | "portscan"
    | "ipv4"
    | "ipv6"
    | "credentials"
    | "cve"
    | "username"
    | "email"
    | "share"
    | "file"
    | "admin_username"
    | "group"
    | "computer"
    | "password_policy"
    | "delegation"
    | "sid"
    | "vulnerability"
    | "account_with_password_not_required"
    | "asreproastable_account"
    | "kerberoastable_account"
    | "expectation_signature";
  /**
   * Number of distinct impacted persons across all occurrences
   * @format int64
   */
  finding_users_count?: number;
  /** Finding value */
  finding_value?: string;
}

export interface FlagInput {
  /** @minLength 1 */
  flag_type: string;
  /** @minLength 1 */
  flag_value: string;
}

export type FlatConfiguration = UtilRequiredKeys<
  WidgetConfiguration,
  "widget_configuration_type" | "time_range" | "date_attribute"
> & {
  series: Series[];
};

export interface FullTextSearchCountResult {
  /** @minLength 1 */
  clazz: string;
  /** @format int64 */
  count: number;
}

export interface FullTextSearchResult {
  /** @minLength 1 */
  clazz: string;
  description?: string;
  /** @minLength 1 */
  id: string;
  /** @minLength 1 */
  name: string;
  /** @uniqueItems true */
  tags?: Tag[];
}

export interface GetExercisesInput {
  exercise_ids?: string[];
}

export interface GetScenariosInput {
  scenario_ids?: string[];
}

export interface GlobalScoreBySimulationEndDate {
  /** @format float */
  global_score_success_percentage: number;
  /** @format date-time */
  simulation_end_date: string;
}

export interface Grant {
  grant_group?: string;
  /** @minLength 1 */
  grant_id: string;
  grant_name: "OBSERVER" | "PLANNER" | "LAUNCHER";
  grant_resource?: string;
  grant_resource_type?:
    | "SCENARIO"
    | "SIMULATION"
    | "ATOMIC_TESTING"
    | "THREAT_ARSENAL"
    | "PAYLOAD"
    | "UNKNOWN";
  listened?: boolean;
}

export interface Group {
  group_default_user_assign?: boolean;
  group_description?: string;
  group_grants?: Grant[];
  /** @minLength 1 */
  group_id: string;
  /** @minLength 1 */
  group_name: string;
  group_roles?: string[];
  group_users?: string[];
  listened?: boolean;
}

export interface GroupGrantInput {
  grant_name?: "OBSERVER" | "PLANNER" | "LAUNCHER";
  grant_resource?: string;
  grant_resource_type?:
    | "SCENARIO"
    | "SIMULATION"
    | "ATOMIC_TESTING"
    | "THREAT_ARSENAL"
    | "PAYLOAD"
    | "UNKNOWN";
}

export interface GroupUpdateRolesInput {
  /** List of role ids associated with the group */
  group_roles?: string[];
}

export interface GroupUpdateUsersInput {
  group_users?: string[];
}

export interface HealthCheck {
  /**
   * Date when the failure have been found
   * @format date-time
   */
  creation_date: string;
  /** Detail of the check failure */
  detail:
    | "SERVICE_UNAVAILABLE"
    | "NOT_READY"
    | "EMPTY"
    | "MANDATORY_CONTENT"
    | "MISSING_TECHNICAL_TARGETS"
    | "MISSING_AUDIENCE_TARGETS"
    | "INEFFECTIVE_TECHNICAL_TARGETS"
    | "INEFFECTIVE_AUDIENCE_TARGETS";
  /** Define if it's an error or a warning */
  status: "ERROR" | "WARNING";
  /** Type of the check, could be a service, an attribute, etc */
  type:
    | "SMTP"
    | "IMAP"
    | "AGENT_OR_EXECUTOR"
    | "SECURITY_SYSTEM_COLLECTOR"
    | "INJECT"
    | "TEAMS"
    | "NMAP"
    | "NUCLEI"
    | "INJECTOR_CONTRACT"
    | "ASSETS"
    | "ASSET_GROUPS"
    | "SUBJECT"
    | "BODY"
    | "OPTIONAL_ARGS"
    | "MESSAGE"
    | "SCOPE_DEFINITION"
    | "UNKNOWN";
}

export interface ImportMapper {
  /** @format date-time */
  import_mapper_created_at?: string;
  import_mapper_id: string;
  import_mapper_inject_importers?: InjectImporter[];
  import_mapper_inject_type_column: string;
  /** @minLength 1 */
  import_mapper_name: string;
  /** @format date-time */
  import_mapper_updated_at?: string;
  listened?: boolean;
}

export interface ImportMapperAddInput {
  import_mapper_inject_importers: InjectImporterAddInput[];
  /**
   * @minLength 1
   * @pattern ^[A-Z]{1,2}$
   */
  import_mapper_inject_type_column: string;
  /** @minLength 1 */
  import_mapper_name: string;
}

export interface ImportMapperUpdateInput {
  import_mapper_inject_importers: InjectImporterUpdateInput[];
  /**
   * @minLength 1
   * @pattern ^[A-Z]{1,2}$
   */
  import_mapper_inject_type_column: string;
  /** @minLength 1 */
  import_mapper_name: string;
}

export interface ImportMessage {
  message_code?:
    | "NO_POTENTIAL_MATCH_FOUND"
    | "SEVERAL_MATCHES"
    | "ABSOLUTE_TIME_WITHOUT_START_DATE"
    | "DATE_SET_IN_PAST"
    | "DATE_SET_IN_FUTURE"
    | "NO_TEAM_FOUND"
    | "EXPECTATION_SCORE_UNDEFINED";
  message_level?: "CRITICAL" | "ERROR" | "WARN" | "INFO";
  message_params?: Record<string, string>;
}

export interface ImportPostSummary {
  available_sheets: string[];
  /** @minLength 1 */
  import_id: string;
}

export interface ImportResult {
  missingActions?: MissingImportedAction[];
}

export interface ImportTestSummary {
  import_message?: ImportMessage[];
  injects?: InjectOutput[];
  /** @format int32 */
  total_injects?: number;
  /** @format int32 */
  total_rows_analysed?: number;
}

export interface Inject {
  footer?: string;
  header?: string;
  inject_all_teams?: boolean;
  inject_asset_groups?: string[];
  inject_assets?: string[];
  inject_attack_patterns?: AttackPattern[];
  inject_city?: string;
  inject_collect_status?: "COLLECTING" | "COMPLETED";
  inject_communications?: string[];
  /** @format int64 */
  inject_communications_not_ack_number?: number;
  /** @format int64 */
  inject_communications_number?: number;
  inject_content?: object;
  /** @uniqueItems true */
  inject_contract_domains?: Domain[];
  inject_country?: string;
  /** @format date-time */
  inject_created_at: string;
  /** @format date-time */
  inject_date?: string;
  /**
   * @format int64
   * @min 0
   */
  inject_depends_duration: number;
  inject_depends_on?: InjectDependency[];
  inject_description?: string;
  inject_documents?: string[];
  inject_enabled?: boolean;
  inject_exercise?: string;
  inject_expectations?: string[];
  inject_expectations_drift_dismissed?: boolean;
  /** @minLength 1 */
  inject_id: string;
  inject_injector?: string;
  inject_injector_contract?: InjectorContract;
  inject_kill_chain_phases?: KillChainPhase[];
  inject_recurrence?: string;
  /** @format date-time */
  inject_recurrence_end?: string;
  /** @format date-time */
  inject_recurrence_start?: string;
  inject_scenario?: string;
  /** @format date-time */
  inject_sent_at?: string;
  inject_status?: InjectStatus;
  inject_tags?: string[];
  inject_teams?: string[];
  inject_testable?: boolean;
  /** @minLength 1 */
  inject_title: string;
  /** @format date-time */
  inject_trigger_now_date?: string;
  inject_type?: string;
  /** @format date-time */
  inject_updated_at: string;
  inject_user?: string;
  /** @format int64 */
  inject_users_number?: number;
  listened?: boolean;
}

/** Input model for automatically generating injects, based on the provided attack pattern IDs and target asset or asset group IDs. */
export interface InjectAssistantInput {
  /** List of asset group IDs to target. Either asset_ids or asset_group_ids must be provided. */
  asset_group_ids?: string[];
  /** List of asset IDs to target. Either asset_ids or asset_group_ids must be provided. */
  asset_ids?: string[];
  /**
   * List of attack pattern used to generate injects
   * @minItems 1
   */
  attack_pattern_ids: string[];
  /**
   * Number of injects to generate for each TTP
   * @format int32
   */
  inject_by_ttp_number: number;
}

export interface InjectBulkProcessingInput {
  inject_ids_to_ignore?: string[];
  inject_ids_to_process?: string[];
  search_pagination_input?: SearchPaginationInput;
  simulation_or_scenario_id?: string;
}

export interface InjectBulkUpdateInputs {
  inject_ids_to_ignore?: string[];
  inject_ids_to_process?: string[];
  search_pagination_input?: SearchPaginationInput;
  simulation_or_scenario_id?: string;
  update_operations?: InjectBulkUpdateOperation[];
}

export interface InjectBulkUpdateOperation {
  field?: "assets" | "asset_groups" | "teams";
  operation?: "add" | "remove" | "replace";
  values?: string[];
}

export interface InjectDependency {
  dependency_condition?: InjectDependencyCondition;
  /** @format date-time */
  dependency_created_at?: string;
  dependency_relationship?: InjectDependencyId;
  /** @format date-time */
  dependency_updated_at?: string;
}

export interface InjectDependencyCondition {
  conditions?: Condition[];
  mode: "and" | "or";
}

export interface InjectDependencyId {
  inject_children_id?: string;
  inject_parent_id?: string;
}

export interface InjectDependencyIdInput {
  inject_children_id?: string;
  inject_parent_id?: string;
}

export interface InjectDependencyInput {
  dependency_condition?: InjectDependencyCondition;
  dependency_relationship?: InjectDependencyIdInput;
}

export interface InjectDocumentInput {
  document_attached?: boolean;
  document_id?: string;
}

export interface InjectExecutionInput {
  execution_action?:
    | "prerequisite_check"
    | "prerequisite_execution"
    | "cleanup_execution"
    | "command_execution"
    | "dns_resolution"
    | "file_execution"
    | "file_drop"
    | "data"
    | "complete";
  /**
   * Ids of the targets (assets / AI targets) this trace relates to. When set on an injector callback (no agent), the trace becomes target-scoped and shows up in the per-target execution view instead of the global timeline.
   * @maxItems 1000
   * @minItems 0
   */
  execution_context_identifiers?: string[];
  /**
   * Duration of the execution in miliseconds
   * @format int32
   */
  execution_duration?: number;
  /** @minLength 1 */
  execution_message: string;
  execution_output_raw?: string;
  execution_output_structured?: string;
  /** @minLength 1 */
  execution_status: string;
}

/** Represents a single inject expectation with agent name */
export interface InjectExpectationAgentOutput {
  inject_expectation_agent?: string;
  inject_expectation_agent_name?: string;
  inject_expectation_asset?: string;
  /** @format date-time */
  inject_expectation_created_at?: string;
  inject_expectation_group?: boolean;
  /** @minLength 1 */
  inject_expectation_id: string;
  inject_expectation_inject?: string;
  inject_expectation_name?: string;
  inject_expectation_results?: InjectExpectationResult[];
  /** @format double */
  inject_expectation_score?: number;
  inject_expectation_status?:
    | "FAILED"
    | "PENDING"
    | "PARTIAL"
    | "UNKNOWN"
    | "SUCCESS";
  inject_expectation_type:
    | "ARTICLE"
    | "CHALLENGE"
    | "MANUAL"
    | "PREVENTION"
    | "DETECTION"
    | "VULNERABILITY";
  /** @format int64 */
  inject_expiration_time: number;
}

export interface InjectExpectationBulkUpdateInput {
  inputs: Record<string, InjectExpectationUpdateInput>;
}

export interface InjectExpectationOutput {
  /** Agent ID associated with the inject expectation */
  inject_expectation_agent?: string;
  /** Article ID associated with the inject expectation */
  inject_expectation_article?: string;
  /** Asset ID associated with the inject expectation */
  inject_expectation_asset?: string;
  /** Asset group ID associated with the inject expectation */
  inject_expectation_asset_group?: string;
  /** Challenge ID associated with the inject expectation */
  inject_expectation_challenge?: string;
  /**
   * Creation date of the inject expectation
   * @format date-time
   */
  inject_expectation_created_at?: string;
  /** Description of the inject expectation */
  inject_expectation_description?: string;
  /** Exercise ID associated with the inject expectation */
  inject_expectation_exercise?: string;
  /**
   * Expected score of the inject expectation
   * @format double
   */
  inject_expectation_expected_score: number;
  /** Security platform types expected to fulfil this technical expectation. Empty means any security platform. */
  inject_expectation_expected_security_platforms?: (
    | "EDR"
    | "XDR"
    | "SIEM"
    | "SOAR"
    | "NDR"
    | "ISPM"
    | "EMAIL_SECURITY"
    | "LLM_FIREWALL"
    | "AI_GATEWAY"
    | "VULNERABILITY_SCANNER"
  )[];
  /** Whether this expectation is a group expectation */
  inject_expectation_group?: boolean;
  /**
   * ID of the inject expectation
   * @minLength 1
   */
  inject_expectation_id: string;
  /** Inject ID associated with the inject expectation */
  inject_expectation_inject?: string;
  /** Name of the inject expectation */
  inject_expectation_name?: string;
  /**
   * Display order of the expectation within its inject, ascending. Declared by the injector contract (e.g. phishing orders its steps email -> link -> submission); null means unordered and readers fall back to name / id.
   * @format int32
   */
  inject_expectation_order?: number;
  /** Results associated with the inject expectation */
  inject_expectation_results?: InjectExpectationResult[];
  /**
   * Current score of the inject expectation
   * @format double
   */
  inject_expectation_score?: number;
  /** Signatures associated with the inject expectation */
  inject_expectation_signatures?: InjectExpectationSignature[];
  /** Computed status of the inject expectation */
  inject_expectation_status?:
    | "FAILED"
    | "PENDING"
    | "PARTIAL"
    | "UNKNOWN"
    | "SUCCESS";
  /** Team ID associated with the inject expectation */
  inject_expectation_team?: string;
  /** Traces associated with the inject expectation */
  inject_expectation_traces?: InjectExpectationTrace[];
  /** Type of the inject expectation */
  inject_expectation_type:
    | "ARTICLE"
    | "CHALLENGE"
    | "MANUAL"
    | "PREVENTION"
    | "DETECTION"
    | "VULNERABILITY";
  /**
   * Last update date of the inject expectation
   * @format date-time
   */
  inject_expectation_updated_at?: string;
  /** User ID associated with the inject expectation */
  inject_expectation_user?: string;
  /**
   * Expiration time in seconds
   * @format int64
   */
  inject_expiration_time: number;
  /** Target ID resolved from user, team, agent, asset, or asset group */
  target_id?: string;
}

export interface InjectExpectationResult {
  date?: string;
  metadata?: Record<string, string>;
  /** @minLength 1 */
  result: string;
  /** @format double */
  score?: number;
  sourceAssetId?: string;
  sourceId?: string;
  sourceName?: string;
  sourcePlatform?: string;
  sourceType?: string;
}

export interface InjectExpectationResultsByAttackPattern {
  inject_attack_pattern?: string;
  inject_expectation_results?: InjectExpectationResultsByType[];
}

export interface InjectExpectationResultsByType {
  inject_id?: string;
  inject_title?: string;
  results?: ExpectationResultsByType[];
}

export interface InjectExpectationSignature {
  /** @minLength 1 */
  type: string;
  /** @minLength 1 */
  value: string;
}

export interface InjectExpectationSimple {
  /** @minLength 1 */
  inject_expectation_id: string;
  inject_expectation_name?: string;
}

export interface InjectExpectationTrace {
  inject_expectation_trace_alert_link?: string;
  inject_expectation_trace_alert_name?: string;
  /** @format date-time */
  inject_expectation_trace_created_at: string;
  /** @format date-time */
  inject_expectation_trace_date?: string;
  inject_expectation_trace_expectation?: string;
  /** @minLength 1 */
  inject_expectation_trace_id: string;
  inject_expectation_trace_source_id?: string;
  /** @format date-time */
  inject_expectation_trace_updated_at: string;
  listened?: boolean;
}

export interface InjectExpectationTraceBulkInsertInput {
  expectation_traces: InjectExpectationTraceInput[];
}

export interface InjectExpectationTraceInput {
  /** @minLength 1 */
  inject_expectation_trace_alert_link: string;
  /** @minLength 1 */
  inject_expectation_trace_alert_name: string;
  /** @format date-time */
  inject_expectation_trace_date: string;
  /** @minLength 1 */
  inject_expectation_trace_expectation: string;
  /** @minLength 1 */
  inject_expectation_trace_source_id: string;
}

export interface InjectExpectationUpdateInput {
  collector_id: string;
  is_success: boolean;
  metadata?: Record<string, string>;
  result: string;
}

export interface InjectExportFromSearchRequestInput {
  inject_ids_to_ignore?: string[];
  inject_ids_to_process?: string[];
  options?: ExportOptionsInput;
  search_pagination_input?: SearchPaginationInput;
  simulation_or_scenario_id?: string;
}

export interface InjectExportRequestInput {
  injects?: InjectExportTarget[];
  options?: ExportOptionsInput;
}

export interface InjectExportTarget {
  inject_id?: string;
}

export interface InjectImporter {
  /** @format date-time */
  inject_importer_created_at?: string;
  inject_importer_id: string;
  inject_importer_injector_contract: string;
  inject_importer_rule_attributes?: RuleAttribute[];
  /** @minLength 1 */
  inject_importer_type_value: string;
  /** @format date-time */
  inject_importer_updated_at?: string;
  listened?: boolean;
}

export interface InjectImporterAddInput {
  /** @minLength 1 */
  inject_importer_injector_contract: string;
  inject_importer_rule_attributes?: RuleAttributeAddInput[];
  /** @minLength 1 */
  inject_importer_type_value: string;
}

export interface InjectImporterUpdateInput {
  inject_importer_id?: string;
  /** @minLength 1 */
  inject_importer_injector_contract: string;
  inject_importer_rule_attributes?: RuleAttributeUpdateInput[];
  /** @minLength 1 */
  inject_importer_type_value: string;
}

export interface InjectIndividualExportRequestInput {
  options?: ExportOptionsInput;
}

export interface InjectInput {
  inject_all_teams?: boolean;
  inject_asset_groups?: string[];
  inject_assets?: string[];
  inject_city?: string;
  inject_content?: object;
  inject_country?: string;
  /** @format int64 */
  inject_depends_duration?: number;
  inject_depends_on?: InjectDependencyInput[];
  inject_description?: string;
  inject_documents?: InjectDocumentInput[];
  inject_enabled?: boolean;
  inject_injector?: string;
  inject_injector_contract?: string;
  inject_tags?: string[];
  inject_teams?: string[];
  /** @minLength 1 */
  inject_title: string;
}

export interface InjectOutput {
  /** Footer of the inject */
  footer?: string;
  /** Header of the inject */
  header?: string;
  inject_asset_groups?: string[];
  inject_assets?: string[];
  inject_attack_patterns?: AttackPattern[];
  inject_communications?: string[];
  /**
   * Communications not ack count of the inject
   * @format int64
   */
  inject_communications_not_ack_number?: number;
  /**
   * Communications count of the inject
   * @format int64
   */
  inject_communications_number?: number;
  /** Content of the inject */
  inject_content?: object;
  /**
   * Domain of the inject
   * @uniqueItems true
   */
  inject_contract_domains?: Domain[];
  /**
   * Date of the inject
   * @format date-time
   */
  inject_date?: string;
  /**
   * Depend duration of the inject
   * @format int64
   * @min 0
   */
  inject_depends_duration: number;
  inject_depends_on?: InjectDependency[];
  inject_documents?: string[];
  /** Enabled state of the inject */
  inject_enabled?: boolean;
  /** Simulation ID of the inject */
  inject_exercise?: string;
  inject_expectations?: InjectExpectationOutput[];
  inject_healthchecks?: HealthCheck[];
  /**
   * ID of the inject
   * @minLength 1
   */
  inject_id: string;
  /** Injector contract of the inject */
  inject_injector_contract?: InjectorContract;
  inject_kill_chain_phases?: KillChainPhase[];
  /** Ready state of the inject */
  inject_ready?: boolean;
  /** Scenario ID of the inject */
  inject_scenario?: string;
  /**
   * Sent date of the inject
   * @format date-time
   */
  inject_sent_at?: string;
  /** @uniqueItems true */
  inject_tags?: string[];
  inject_teams?: string[];
  /** Testable state of the inject */
  inject_testable?: boolean;
  /**
   * Title of the inject
   * @minLength 1
   */
  inject_title: string;
  /** Type of the inject */
  inject_type?: string;
  /**
   * Count of users targeted by the inject
   * @format int64
   */
  inject_users_number?: number;
  /** Stream listener value of the inject */
  listened?: boolean;
}

export interface InjectReceptionInput {
  /** @format int32 */
  tracking_total_count?: number;
}

export interface InjectRecurrenceInput {
  inject_recurrence?: string;
  /** @format date-time */
  inject_recurrence_end?: string;
  /** @format date-time */
  inject_recurrence_start?: string;
}

export interface InjectResultOutput {
  /** Domain of the inject */
  inject_contract_domains?: string[];
  /** Whether the inject is enabled (disabled injects are never executed) */
  inject_enabled?: boolean;
  /** Id of the simulation (exercise) this inject belongs to, if any */
  inject_exercise?: string;
  /** Result of expectations */
  inject_expectation_results: ExpectationResultsByType[];
  /**
   * Id of inject
   * @minLength 1
   */
  inject_id: string;
  /** Injector contract */
  inject_injector_contract?: InjectorContractSimple;
  /** Status */
  inject_status?: InjectStatusSimple;
  inject_targets?: TargetSimple[];
  /**
   * Title of inject
   * @minLength 1
   */
  inject_title: string;
  /** Type of inject */
  inject_type?: string;
  /**
   * Timestamp when the inject was last updated
   * @format date-time
   */
  inject_updated_at: string;
}

export interface InjectResultOverviewOutput {
  /** Content of inject */
  inject_content?: object;
  /** Description of inject */
  inject_description?: string;
  /** Result of expectations */
  inject_expectation_results: ExpectationResultsByType[];
  /** Expectations */
  inject_expectations?: InjectExpectationSimple[];
  /**
   * Id of inject
   * @minLength 1
   */
  inject_id: string;
  /** Full contract */
  inject_injector_contract?: AtomicInjectorContractOutput;
  /** Kill chain phases */
  inject_kill_chain_phases?: KillChainPhaseSimple[];
  /** Indicates whether the inject is ready for use */
  inject_ready?: boolean;
  /** Recurrence cron expression for scheduled relaunch */
  inject_recurrence?: string;
  /**
   * End date of the recurrence scheduling
   * @format date-time
   */
  inject_recurrence_end?: string;
  /**
   * Start date of the recurrence scheduling
   * @format date-time
   */
  inject_recurrence_start?: string;
  /** status */
  inject_status?: InjectStatusSimple;
  /**
   * Tags
   * @uniqueItems true
   */
  inject_tags?: string[];
  /**
   * Title of inject
   * @minLength 1
   */
  inject_title: string;
  /** Type of inject */
  inject_type?: string;
  /**
   * Timestamp when the inject was last updated
   * @format date-time
   */
  inject_updated_at?: string;
  /** Documents */
  injects_documents?: string[];
  /** Tags */
  injects_tags?: string[];
  ready?: boolean;
}

export interface InjectResultPayloadExecutionOutput {
  execution_traces: Record<string, ExecutionTraceOutput[]>;
  /** @minItems 1 */
  payload_command_blocks: PayloadCommandBlock[];
}

export interface InjectSimple {
  /**
   * Inject Id
   * @minLength 1
   */
  inject_id: string;
  /**
   * Inject Title
   * @minLength 1
   */
  inject_title: string;
}

export interface InjectStatus {
  listened?: boolean;
  status_id?: string;
  status_name:
    | "EXECUTED"
    | "PARTIAL"
    | "ERROR"
    | "DRAFT"
    | "QUEUING"
    | "EXECUTING"
    | "PENDING";
  status_payload_output?: StatusPayload;
  status_traces?: ExecutionTrace[];
  /** @format date-time */
  tracking_end_date?: string;
  /** @format date-time */
  tracking_sent_date?: string;
}

export interface InjectStatusOutput {
  status_id: string;
  status_main_traces?: ExecutionTraceOutput[];
  status_name?: string;
  /** @format date-time */
  tracking_end_date?: string;
  /** @format date-time */
  tracking_sent_date?: string;
}

export interface InjectStatusSimple {
  status_id: string;
  status_name?: string;
  /** @format date-time */
  tracking_end_date?: string;
  /** @format date-time */
  tracking_sent_date?: string;
}

export type InjectTarget = BaseInjectTarget &
  (
    | BaseInjectTargetTargetTypeMapping<"ASSETS_GROUPS", AssetGroupTarget>
    | BaseInjectTargetTargetTypeMapping<"ASSETS", EndpointTarget>
    | BaseInjectTargetTargetTypeMapping<"TEAMS", TeamTarget>
    | BaseInjectTargetTargetTypeMapping<"PLAYERS", PlayerTarget>
    | BaseInjectTargetTargetTypeMapping<"AGENT", AgentTarget>
    | BaseInjectTargetTargetTypeMapping<"AI_TARGETS", AiTargetTarget>
  );

export interface InjectTeamsInput {
  inject_teams?: string[];
}

export interface InjectTestStatusOutput {
  inject_id: string;
  inject_title: string;
  inject_type?: string;
  status_id: string;
  status_main_traces?: ExecutionTraceOutput[];
  status_name?: string;
  /** @format date-time */
  tracking_end_date?: string;
  /** @format date-time */
  tracking_sent_date?: string;
}

export interface InjectUpdateActivationInput {
  inject_enabled?: boolean;
}

export interface InjectUpdateStatusInput {
  message?: string;
  status?: string;
}

export interface Injector {
  injector_category?: string;
  /** @format date-time */
  injector_created_at: string;
  injector_custom_contracts?: boolean;
  injector_dependencies?: (
    | "SMTP"
    | "IMAP"
    | "NUCLEI"
    | "NMAP"
    | "NETEXEC"
    | "OpenAEV Email"
    | "OpenAEV Implant"
  )[];
  injector_executor_clear_commands?: Record<string, string>;
  injector_executor_commands?: Record<string, string>;
  injector_external?: boolean;
  /** @minLength 1 */
  injector_id: string;
  /** @minLength 1 */
  injector_name: string;
  injector_payloads?: boolean;
  injector_security_platform?: string;
  /** @minLength 1 */
  injector_type: string;
  /** @format date-time */
  injector_updated_at: string;
  listened?: boolean;
}

export interface InjectorContract {
  convertedContent?: object;
  injector_contract_arch?: "x86_64" | "arm64" | "ALL_ARCHITECTURES";
  injector_contract_atomic_testing?: boolean;
  injector_contract_attack_patterns?: string[];
  /** @minLength 1 */
  injector_contract_content: string;
  /** @format date-time */
  injector_contract_created_at: string;
  injector_contract_custom?: boolean;
  injector_contract_domains?: string[];
  injector_contract_external_id?: string;
  /** @minLength 1 */
  injector_contract_id: string;
  injector_contract_import_available?: boolean;
  injector_contract_injector_names?: Record<string, string>;
  injector_contract_injector_type?: string;
  injector_contract_injectors?: string[];
  injector_contract_labels?: Record<string, string>;
  injector_contract_manual?: boolean;
  injector_contract_needs_executor?: boolean;
  injector_contract_payload?: Payload;
  injector_contract_payload_author?: string;
  injector_contract_payload_author_organization?: Organization;
  injector_contract_payload_author_team?: Team;
  injector_contract_payload_author_user?: User;
  injector_contract_payload_status?: "UNVERIFIED" | "VERIFIED" | "DEPRECATED";
  injector_contract_platforms?: (
    | "Linux"
    | "Windows"
    | "MacOS"
    | "Android"
    | "iOS"
    | "Container"
    | "Service"
    | "Generic"
    | "Internal"
    | "Unknown"
  )[];
  injector_contract_providing?: (
    | "text"
    | "action_output"
    | "number"
    | "port"
    | "portscan"
    | "ipv4"
    | "ipv6"
    | "credentials"
    | "cve"
    | "username"
    | "email"
    | "share"
    | "file"
    | "admin_username"
    | "group"
    | "computer"
    | "password_policy"
    | "delegation"
    | "sid"
    | "vulnerability"
    | "account_with_password_not_required"
    | "asreproastable_account"
    | "kerberoastable_account"
    | "expectation_signature"
  )[];
  injector_contract_tags?: string[];
  /** @format date-time */
  injector_contract_updated_at: string;
  injector_contract_vulnerabilities?: string[];
  listened?: boolean;
}

export interface InjectorContractAddInput {
  contract_attack_patterns_external_ids?: string[];
  contract_attack_patterns_ids?: string[];
  /** @minLength 1 */
  contract_content: string;
  /** @uniqueItems true */
  contract_domains: InjectorContractDomainDTO[];
  /** @minLength 1 */
  contract_id: string;
  contract_labels?: Record<string, string>;
  contract_manual?: boolean;
  contract_platforms?: string[];
  contract_vulnerability_external_ids?: string[];
  contract_vulnerability_ids?: string[];
  external_contract_id?: string;
  /** @minLength 1 */
  injector_id: string;
  is_atomic_testing?: boolean;
}

export interface InjectorContractAuthorCountOutput {
  /** Author id (user, team or organization) */
  author?: string;
  /** Author display name */
  author_name?: string;
  /** Author type: user, team or organization */
  author_type?: string;
  /**
   * Number of contracts authored by this author under the current filters
   * @format int64
   */
  count?: number;
}

export type InjectorContractBaseOutput = BaseInjectorContractBaseOutput &
  (
    | BaseInjectorContractBaseOutputInjectorContractHasFullDetailsMapping<
        "false",
        InjectorContractBaseOutput
      >
    | BaseInjectorContractBaseOutputInjectorContractHasFullDetailsMapping<
        "true",
        InjectorContractFullOutput
      >
  );

export interface InjectorContractDomainCountOutput {
  /**
   * Total number of observations linked to this domain
   * @format int64
   * @example 42
   */
  count: number;
  /**
   * The domain name extracted from OpenAEV
   * @minLength 1
   * @example "Endpoints"
   */
  domain: string;
}

export interface InjectorContractDomainDTO {
  /** @minLength 1 */
  domain_color: string;
  /** @minLength 1 */
  domain_id: string;
  /** @minLength 1 */
  domain_name: string;
}

export interface InjectorContractFacetCountsOutput {
  /** Number of contracts per kill chain phase id under the current filters, through the attack pattern relation */
  kill_chain_phases?: Record<string, number>;
  /** Number of contracts per platform under the current filters */
  platforms?: Record<string, number>;
  /** Number of contracts per payload status under the current filters */
  statuses?: Record<string, number>;
}

export interface InjectorContractFullOutput {
  injector_contract_arch?: "x86_64" | "arm64" | "ALL_ARCHITECTURES";
  /** Attack pattern IDs */
  injector_contract_attack_patterns?: string[];
  /**
   * Content
   * @minLength 1
   */
  injector_contract_content: string;
  /**
   * Domain IDs
   * @minItems 1
   */
  injector_contract_domains: string[];
  /** Injector contract external Id */
  injector_contract_external_id?: string;
  injector_contract_has_full_details?: boolean;
  /**
   * Injector contract Id
   * @minLength 1
   */
  injector_contract_id: string;
  /** Map of injector ID to injector name for all injectors linked to this contract */
  injector_contract_injector_names?: Record<string, string>;
  /** Injector type */
  injector_contract_injector_type?: string;
  /** Injector IDs linked to this contract */
  injector_contract_injectors?: string[];
  /** Labels */
  injector_contract_labels?: Record<string, string>;
  /** Payload type */
  injector_contract_payload_type?: string;
  /** Platforms */
  injector_contract_platforms?: (
    | "Linux"
    | "Windows"
    | "MacOS"
    | "Android"
    | "iOS"
    | "Container"
    | "Service"
    | "Generic"
    | "Internal"
    | "Unknown"
  )[];
  /** Tag IDs */
  injector_contract_tags?: string[];
  /**
   * Timestamp when the injector contract was last updated
   * @format date-time
   */
  injector_contract_updated_at: string;
}

export interface InjectorContractInput {
  contract_attack_patterns_external_ids?: string[];
  /** @minLength 1 */
  contract_content: string;
  /** @uniqueItems true */
  contract_domains?: InjectorContractDomainDTO[];
  /** @minLength 1 */
  contract_id: string;
  contract_labels?: Record<string, string>;
  contract_manual?: boolean;
  contract_platforms?: (
    | "Linux"
    | "Windows"
    | "MacOS"
    | "Android"
    | "iOS"
    | "Container"
    | "Service"
    | "Generic"
    | "Internal"
    | "Unknown"
  )[];
  is_atomic_testing?: boolean;
}

export interface InjectorContractSearchPaginationInput {
  /** Filter object to search within filterable attributes */
  filterGroup?: FilterGroup;
  /** Include the injector contract content on the returned object if set to true */
  include_content_details?: boolean;
  /** Allow the return of a full object if true, partial object if false */
  include_full_details?: boolean;
  /** List of all the ids to ignore on the search */
  injector_contract_ids_to_ignore?: string[];
  /** List of all the ids to include on the search */
  injector_contract_ids_to_process?: string[];
  /**
   * Page number to get
   * @format int32
   * @min 0
   */
  page: number;
  /**
   * Element number by page
   * @format int32
   * @max 1000
   */
  size: number;
  /** List of sort fields : a field is composed of a property (for instance "label" and an optional direction ("asc" is assumed if no direction is specified) : ("desc", "asc") */
  sorts?: SortField[];
  /** Text to search within searchable attributes */
  textSearch?: string;
}

export interface InjectorContractSimple {
  convertedContent?: object;
  /** @minLength 1 */
  injector_contract_content: string;
  injector_contract_domains?: string[];
  /** @minLength 1 */
  injector_contract_id: string;
  injector_contract_labels: Record<string, string>;
  injector_contract_payload?: PayloadSimple;
  injector_contract_platforms?: (
    | "Linux"
    | "Windows"
    | "MacOS"
    | "Android"
    | "iOS"
    | "Container"
    | "Service"
    | "Generic"
    | "Internal"
    | "Unknown"
  )[];
}

export interface InjectorContractUpdateInput {
  contract_attack_patterns_ids?: string[];
  /** @minLength 1 */
  contract_content: string;
  /** @uniqueItems true */
  contract_domains?: InjectorContractDomainDTO[];
  contract_labels?: Record<string, string>;
  contract_manual?: boolean;
  contract_platforms?: string[];
  contract_vulnerability_external_ids?: string[];
  contract_vulnerability_ids?: string[];
  is_atomic_testing?: boolean;
}

export interface InjectorContractUpdateMappingInput {
  contract_attack_patterns_ids?: string[];
  /** Set list of domains */
  contract_domains: string[];
  /** Set list of tags ids */
  contract_tags_ids?: string[];
}

export interface InjectorCreateInput {
  injector_author?: string;
  injector_category?: string;
  injector_contracts?: InjectorContractInput[];
  injector_custom_contracts?: boolean;
  injector_executor_clear_commands?: Record<string, string>;
  injector_executor_commands?: Record<string, string>;
  /** @minLength 1 */
  injector_id: string;
  /** @minLength 1 */
  injector_name: string;
  injector_payloads?: boolean;
  /** @minLength 1 */
  injector_type: string;
}

/** Injector output */
export interface InjectorOutput {
  /** Catalog simple output */
  catalog?: CatalogConnectorSimpleOutput;
  connector_instance?: ConnectorInstanceOutput;
  existing_injector?: boolean;
  injector_external?: boolean;
  /**
   * Injector id
   * @minLength 1
   */
  injector_id: string;
  /** @minLength 1 */
  injector_name: string;
  /** @minLength 1 */
  injector_type: string;
  /** @format date-time */
  injector_updated_at?: string;
  is_verified?: boolean;
}

export interface InjectorRegistration {
  connection?: BrokerConnectionInfo;
  listen?: string;
}

export interface InjectorUpdateInput {
  injector_category?: string;
  injector_contracts?: InjectorContractInput[];
  injector_custom_contracts?: boolean;
  injector_executor_clear_commands?: Record<string, string>;
  injector_executor_commands?: Record<string, string>;
  /** @minLength 1 */
  injector_name: string;
  injector_payloads?: boolean;
}

export interface InjectsImportInput {
  /** @minLength 1 */
  import_mapper_id: string;
  /** @format date-time */
  launch_date?: string;
  /** @minLength 1 */
  sheet_name: string;
  /** @format int32 */
  timezone_offset: number;
}

export interface InjectsImportTestInput {
  import_mapper: ImportMapperAddInput;
  /** @minLength 1 */
  sheet_name: string;
  /** @format int32 */
  timezone_offset: number;
}

/** An installed injector and its activity state */
export interface InstalledInjector {
  /** Built-in injectors are always active; external ones must heartbeat */
  active?: boolean;
  injector_type?: string;
  name?: string;
  /** True when the injector can carry custom payloads */
  payloads?: boolean;
}

export interface Internal {
  /** @minLength 1 */
  work_id: string;
}

export interface JsonApiDocumentResourceObject {
  data?: ResourceObject;
  included?: any[];
}

export type JsonNode = any;

export interface JwkOutput {
  crv?: string;
  key_ops?: string[];
  kid?: string;
  kty?: string;
  x?: string;
}

export interface JwksOutput {
  keys?: JwkOutput[];
}

export interface KillChainPhase {
  listened?: boolean;
  /** @format date-time */
  phase_created_at: string;
  phase_description?: string;
  /** @minLength 1 */
  phase_external_id: string;
  /** @minLength 1 */
  phase_id: string;
  /** @minLength 1 */
  phase_kill_chain_name: string;
  /** @minLength 1 */
  phase_name: string;
  /** @format int64 */
  phase_order?: number;
  /** @minLength 1 */
  phase_shortname: string;
  phase_stix_id?: string;
  /** @format date-time */
  phase_updated_at: string;
}

export interface KillChainPhaseCoverage {
  phase_external_id?: string;
  phase_id?: string;
  phase_name?: string;
  /** @format int64 */
  phase_order?: number;
}

export interface KillChainPhaseCreateInput {
  phase_description?: string;
  /** @minLength 1 */
  phase_external_id: string;
  /** @minLength 1 */
  phase_kill_chain_name: string;
  /** @minLength 1 */
  phase_name: string;
  /** @format int64 */
  phase_order?: number;
  /** @minLength 1 */
  phase_shortname: string;
  phase_stix_id?: string;
}

export interface KillChainPhaseObject {
  /** @minLength 1 */
  id: string;
  name?: string;
  /** @format int64 */
  order?: number;
}

export interface KillChainPhaseOutput {
  /** Creation date of the phase */
  phase_created_at: string;
  /** Description of the phase */
  phase_description?: string;
  /**
   * External ID of the phase
   * @minLength 1
   */
  phase_external_id: string;
  /**
   * ID of the phase
   * @minLength 1
   */
  phase_id: string;
  /**
   * Name of the kill chain phase
   * @minLength 1
   */
  phase_kill_chain_name: string;
  /**
   * Name of the phase
   * @minLength 1
   */
  phase_name: string;
  /**
   * Order of the phase
   * @format int64
   */
  phase_order?: number;
  /**
   * Short name of the phase
   * @minLength 1
   */
  phase_shortname: string;
  /** Stix ID of the phase */
  phase_stix_id?: string;
  /** Update date of the phase */
  phase_updated_at: string;
}

export interface KillChainPhaseSimple {
  /** @minLength 1 */
  phase_id: string;
  phase_name?: string;
}

export interface KillChainPhaseUpdateInput {
  /** @minLength 1 */
  phase_kill_chain_name: string;
  /** @minLength 1 */
  phase_name: string;
  /** @format int64 */
  phase_order?: number;
}

export interface KillChainPhaseUpsertInput {
  kill_chain_phases: KillChainPhaseCreateInput[];
}

export interface LessonsAnswer {
  /** @format date-time */
  lessons_answer_created_at: string;
  lessons_answer_exercise?: string;
  lessons_answer_negative?: string;
  lessons_answer_positive?: string;
  lessons_answer_question: string;
  /** @format int32 */
  lessons_answer_score: number;
  /** @format date-time */
  lessons_answer_updated_at: string;
  lessons_answer_user?: string;
  /** @minLength 1 */
  lessonsanswer_id: string;
  listened?: boolean;
}

export interface LessonsAnswerCreateInput {
  lessons_answer_negative?: string;
  lessons_answer_positive?: string;
  /** @format int32 */
  lessons_answer_score?: number;
}

export interface LessonsCategory {
  /** @format date-time */
  lessons_category_created_at: string;
  lessons_category_description?: string;
  lessons_category_exercise?: string;
  /** @minLength 1 */
  lessons_category_name: string;
  /** @format int32 */
  lessons_category_order?: number;
  lessons_category_questions?: string[];
  lessons_category_scenario?: string;
  lessons_category_teams?: string[];
  /** @format date-time */
  lessons_category_updated_at: string;
  lessons_category_users?: string[];
  /** @minLength 1 */
  lessonscategory_id: string;
  listened?: boolean;
}

export interface LessonsCategoryCreateInput {
  lessons_category_description?: string;
  /** @minLength 1 */
  lessons_category_name: string;
  /** @format int32 */
  lessons_category_order?: number;
}

export interface LessonsCategoryTeamsInput {
  lessons_category_teams?: string[];
}

export interface LessonsCategoryUpdateInput {
  lessons_category_description?: string;
  /** @minLength 1 */
  lessons_category_name: string;
  /** @format int32 */
  lessons_category_order?: number;
}

export interface LessonsInput {
  /** Whether questionnaire answers are anonymized (unchanged when absent) */
  lessons_anonymized?: boolean;
  /** Whether the lessons learned module is enabled (unchanged when absent) */
  lessons_enabled?: boolean;
}

export interface LessonsQuestion {
  lessons_question_answers?: string[];
  lessons_question_category: string;
  /** @minLength 1 */
  lessons_question_content: string;
  /** @format date-time */
  lessons_question_created_at: string;
  lessons_question_exercise?: string;
  lessons_question_explanation?: string;
  /** @format int32 */
  lessons_question_order?: number;
  lessons_question_scenario?: string;
  /** @format date-time */
  lessons_question_updated_at: string;
  /** @minLength 1 */
  lessonsquestion_id: string;
  listened?: boolean;
}

export interface LessonsQuestionCreateInput {
  /** @minLength 1 */
  lessons_question_content: string;
  lessons_question_explanation?: string;
  /** @format int32 */
  lessons_question_order?: number;
}

export interface LessonsQuestionUpdateInput {
  /** @minLength 1 */
  lessons_question_content: string;
  lessons_question_explanation?: string;
  /** @format int32 */
  lessons_question_order?: number;
}

export interface LessonsSendInput {
  body?: string;
  subject?: string;
}

export interface LessonsTemplate {
  /** @format date-time */
  lessons_template_created_at: string;
  lessons_template_description?: string;
  /** @minLength 1 */
  lessons_template_name: string;
  /** @format date-time */
  lessons_template_updated_at: string;
  /** @minLength 1 */
  lessonstemplate_id: string;
  listened?: boolean;
}

export interface LessonsTemplateCategory {
  /** @format date-time */
  lessons_template_category_created_at: string;
  lessons_template_category_description?: string;
  /** @minLength 1 */
  lessons_template_category_name: string;
  /** @format int32 */
  lessons_template_category_order: number;
  lessons_template_category_questions?: string[];
  lessons_template_category_template?: string;
  /** @format date-time */
  lessons_template_category_updated_at: string;
  /** @minLength 1 */
  lessonstemplatecategory_id: string;
  listened?: boolean;
}

export interface LessonsTemplateCategoryInput {
  lessons_template_category_description?: string;
  /** @minLength 1 */
  lessons_template_category_name: string;
  /** @format int32 */
  lessons_template_category_order: number;
}

export interface LessonsTemplateInput {
  lessons_template_description?: string;
  /** @minLength 1 */
  lessons_template_name: string;
}

export interface LessonsTemplateQuestion {
  lessons_template_question_category?: string;
  /** @minLength 1 */
  lessons_template_question_content: string;
  /** @format date-time */
  lessons_template_question_created_at: string;
  lessons_template_question_explanation?: string;
  /** @format int32 */
  lessons_template_question_order: number;
  /** @format date-time */
  lessons_template_question_updated_at: string;
  /** @minLength 1 */
  lessonstemplatequestion_id: string;
  listened?: boolean;
}

export interface LessonsTemplateQuestionInput {
  /** @minLength 1 */
  lessons_template_question_content: string;
  lessons_template_question_explanation?: string;
  /** @format int32 */
  lessons_template_question_order: number;
}

export interface License {
  license_creator?: string;
  license_customer?: string;
  /** @format date-time */
  license_expiration_date?: string;
  /** @format int64 */
  license_extra_expiration_days?: number;
  license_is_by_configuration?: boolean;
  license_is_enterprise?: boolean;
  license_is_expired?: boolean;
  license_is_extra_expiration?: boolean;
  license_is_global?: boolean;
  license_is_platform_match?: boolean;
  license_is_prevention?: boolean;
  license_is_valid_cert?: boolean;
  license_is_valid_product?: boolean;
  license_is_validated?: boolean;
  license_platform?: string;
  /** @format date-time */
  license_start_date?: string;
  license_type?: "trial" | "nfr" | "standard" | "lts";
}

export type ListConfiguration = UtilRequiredKeys<
  WidgetConfiguration,
  "widget_configuration_type" | "time_range" | "date_attribute"
> & {
  columns?: string[];
  /**
   * @format int32
   * @min 1
   */
  limit?: number;
  perspective: ListPerspective;
  sorts?: EngineSortField[];
};

export interface ListPerspective {
  filter?: FilterGroup;
  name?: string;
}

export interface Log {
  listened?: boolean;
  /** @minLength 1 */
  log_content: string;
  /** @format date-time */
  log_created_at: string;
  log_exercise?: string;
  /** @minLength 1 */
  log_id: string;
  log_tags?: string[];
  /** @minLength 1 */
  log_title: string;
  /** @format date-time */
  log_updated_at: string;
  log_user?: string;
}

export interface LogCreateInput {
  log_content?: string;
  log_tags?: string[];
  log_title?: string;
}

export interface LoginUserInput {
  /**
   * The identifier of the user
   * @minLength 1
   */
  login: string;
  /**
   * The password of the user
   * @minLength 1
   */
  password: string;
  /** The tenant ID the user is logging into (optional) */
  tenantId?: string;
}

export interface MapperConditionOutput {
  condition_key?: string;
  condition_key_types?: (
    | "account_with_password_not_required"
    | "action_output"
    | "admin_username"
    | "asreproastable_account"
    | "asset_group_id"
    | "asset_id"
    | "computer_name"
    | "cve"
    | "delegation_account"
    | "document"
    | "domain"
    | "email"
    | "file_name"
    | "file_path"
    | "group_name"
    | "hash"
    | "host"
    | "ipv4"
    | "ipv6"
    | "ip_subnet"
    | "kerberoastable_account"
    | "key"
    | "number"
    | "password"
    | "permissions"
    | "port"
    | "service"
    | "severity"
    | "share_name"
    | "sid"
    | "targeted-asset"
    | "text"
    | "username"
    | "value"
    | "vulnerability_name"
    | "vulnerability_status"
  )[];
  condition_mapping_type?: "DEFAULT" | "LOCAL" | "GLOBAL";
  condition_value?: string;
}

export interface MissingImportedAction {
  name?: string;
  type?: string;
}

export interface Mitigation {
  listened?: boolean;
  mitigation_attack_patterns?: string[];
  /** @format date-time */
  mitigation_created_at: string;
  mitigation_description?: string;
  /** @minLength 1 */
  mitigation_external_id: string;
  /** @minLength 1 */
  mitigation_id: string;
  mitigation_log_sources?: string[];
  /** @minLength 1 */
  mitigation_name: string;
  /** @minLength 1 */
  mitigation_stix_id: string;
  mitigation_threat_hunting_techniques?: string;
  /** @format date-time */
  mitigation_updated_at: string;
}

export interface MitigationCreateInput {
  mitigation_attack_patterns?: string[];
  mitigation_description?: string;
  /** @minLength 1 */
  mitigation_external_id: string;
  mitigation_log_sources?: string[];
  /** @minLength 1 */
  mitigation_name: string;
  mitigation_stix_id?: string;
  mitigation_threat_hunting_techniques?: string;
}

export interface MitigationUpdateInput {
  mitigation_attack_patterns?: string[];
  mitigation_description?: string;
  /** @minLength 1 */
  mitigation_external_id: string;
  /** @minLength 1 */
  mitigation_name: string;
}

export interface MitigationUpsertInput {
  mitigations?: MitigationCreateInput[];
}

export interface NetworkTraffic {
  listened?: boolean;
  network_traffic_ip_dst: string;
  network_traffic_ip_src: string;
  /** @format int32 */
  network_traffic_port_dst: number;
  /** @format int32 */
  network_traffic_port_src: number;
  network_traffic_protocol: string;
  payload_arguments?: PayloadArgument[];
  /** Organization author of the payload */
  payload_author_organization?: string;
  /** Team author of the payload */
  payload_author_team?: string;
  /** User author of the payload */
  payload_author_user?: string;
  payload_cleanup_command?: string;
  payload_cleanup_executor?: string;
  payload_collector_type?: string;
  /** @format date-time */
  payload_created_at: string;
  payload_description?: string;
  payload_detection_remediations?: DetectionRemediation[];
  payload_elevation_required?: boolean;
  payload_execution_arch: "x86_64" | "arm64" | "ALL_ARCHITECTURES";
  payload_expectations?: (
    | "ARTICLE"
    | "CHALLENGE"
    | "MANUAL"
    | "PREVENTION"
    | "DETECTION"
    | "VULNERABILITY"
  )[];
  payload_expected_security_platforms?: Record<
    string,
    (
      | "EDR"
      | "XDR"
      | "SIEM"
      | "SOAR"
      | "NDR"
      | "ISPM"
      | "EMAIL_SECURITY"
      | "LLM_FIREWALL"
      | "AI_GATEWAY"
      | "VULNERABILITY_SCANNER"
    )[]
  >;
  payload_external_id?: string;
  /** @minLength 1 */
  payload_id: string;
  /** @minLength 1 */
  payload_name: string;
  /** @uniqueItems true */
  payload_output_parsers?: OutputParser[];
  /** @minItems 1 */
  payload_platforms: (
    | "Linux"
    | "Windows"
    | "MacOS"
    | "Android"
    | "iOS"
    | "Container"
    | "Service"
    | "Generic"
    | "Internal"
    | "Unknown"
  )[];
  payload_prerequisites?: PayloadPrerequisite[];
  payload_source: "COMMUNITY" | "FILIGRAN" | "MANUAL";
  payload_status: "UNVERIFIED" | "VERIFIED" | "DEPRECATED";
  payload_type?: string;
  /** @format date-time */
  payload_updated_at: string;
  typeEnum?:
    | "COMMAND"
    | "EXECUTABLE"
    | "FILE_DROP"
    | "DNS_RESOLUTION"
    | "NETWORK_TRAFFIC"
    | "AI_ATTACK";
}

export interface NotificationBulkProcessingInput {
  /** Ids excluded from the select-all scope */
  notification_ids_to_ignore?: string[];
  /** Explicit ids of the notifications to process */
  notification_ids_to_process?: string[];
  /** Search input selecting the notifications to process (select all) */
  search_pagination_input?: SearchPaginationInput;
}

export interface NotificationOutput {
  /** Content groups: [{title, events: [{operation, message, ...}]}] */
  notification_content?: Record<string, any>[];
  /**
   * Creation date
   * @format date-time
   */
  notification_created_at?: string;
  /** ID of the notification */
  notification_id: string;
  /** Whether the notification has been read */
  notification_is_read?: boolean;
  /** Name of the trigger that produced the notification */
  notification_name?: string;
  /** Type of the notification (LIVE or DIGEST) */
  notification_type?: "LIVE" | "DIGEST";
}

export interface NotificationTriggerInput {
  /** Composed live trigger ids for a digest */
  notification_trigger_children?: string[];
  /** Whether the trigger is enabled */
  notification_trigger_enabled?: boolean;
  /** Subscribed lifecycle operations (CREATE, UPDATE, DELETE) */
  notification_trigger_event_types?: (
    | "CREATE"
    | "UPDATE"
    | "DELETE"
    | "SCORE_DEGRADATION"
  )[];
  /** Filter group applied to matching entities */
  notification_trigger_filters?: FilterGroup;
  /** Entity id for instance triggers */
  notification_trigger_instance_id?: string;
  /**
   * Name of the notification trigger
   * @minLength 1
   */
  notification_trigger_name: string;
  /** Notifier ids used for delivery */
  notification_trigger_notifiers?: string[];
  /** Digest period (HOUR, DAY, WEEK, MONTH) */
  notification_trigger_period?: "HOUR" | "DAY" | "WEEK" | "MONTH";
  /** Targeted recipient group ids (admins only) */
  notification_trigger_recipient_groups?: string[];
  /** Targeted recipient user ids (admins only; empty = owner) */
  notification_trigger_recipient_users?: string[];
  /** Resource type watched by a live trigger */
  notification_trigger_resource_type?:
    | "ASSET"
    | "AGENT"
    | "SCENARIO"
    | "SIMULATION"
    | "PLAYER"
    | "USER"
    | "TEAM"
    | "ATOMIC_TESTING"
    | "NOTIFICATION_TRIGGER"
    | "NOTIFIER"
    | "NOTIFICATION"
    | "PAYLOAD"
    | "THREAT_ARSENAL"
    | "RESOURCE_TYPE"
    | "SECURITY_PLATFORM"
    | "CREDENTIAL"
    | "DOCUMENT"
    | "CHANNEL"
    | "PHISHING_LANDING_PAGE"
    | "PHISHING_EMAIL_TEMPLATE"
    | "FINDING"
    | "DASHBOARD"
    | "REPORT"
    | "PLATFORM_SETTING"
    | "LESSON_LEARNED"
    | "CHALLENGE"
    | "INJECT"
    | "JOB"
    | "TAG"
    | "TAG_RULE"
    | "KILL_CHAIN_PHASE"
    | "ATTACK_PATTERN"
    | "ASSET_GROUP"
    | "VULNERABILITY"
    | "USER_GROUP"
    | "INJECTOR"
    | "INJECTOR_CONTRACT"
    | "MAPPER"
    | "GROUP_ROLE"
    | "ORGANIZATION"
    | "COLLECTOR"
    | "STIX_BUNDLE"
    | "DOMAIN"
    | "OBJECTIVE"
    | "EVALUATION"
    | "CATALOG"
    | "CONNECTOR_INSTANCE_LOG"
    | "SECRET_PROVIDER"
    | "TENANT"
    | "TENANT_SETTING"
    | "PLATFORM_ROLE"
    | "PLATFORM_GROUP"
    | "PLATFORM_USER"
    | "XTM_HUB_REGISTRATION"
    | "UNKNOWN"
    | "SIMULATION_OR_SCENARIO"
    | "WORKFLOW"
    | "STEP"
    | "CONDITION"
    | "SKIP_RBAC";
  /** Digest firing time (UTC): DAY=HH:mm, WEEK=<1-7>-HH:mm, MONTH=<1-31>-HH:mm */
  notification_trigger_time?: string;
  /** Type of the trigger (LIVE or DIGEST) */
  notification_trigger_type: "LIVE" | "DIGEST";
}

export interface NotificationTriggerOutput {
  /** Composed live trigger ids for a digest */
  notification_trigger_children?: string[];
  /**
   * Creation date
   * @format date-time
   */
  notification_trigger_created_at?: string;
  /** Whether the trigger is enabled */
  notification_trigger_enabled?: boolean;
  /** Subscribed lifecycle operations */
  notification_trigger_event_types?: (
    | "CREATE"
    | "UPDATE"
    | "DELETE"
    | "SCORE_DEGRADATION"
  )[];
  /** Filter group applied to matching entities */
  notification_trigger_filters?: FilterGroup;
  /** ID of the notification trigger */
  notification_trigger_id: string;
  /** Entity id for instance triggers */
  notification_trigger_instance_id?: string;
  /** Name of the notification trigger */
  notification_trigger_name?: string;
  /** Notifier ids used for delivery */
  notification_trigger_notifiers?: string[];
  /** Owner user id */
  notification_trigger_owner?: string;
  /** Digest period */
  notification_trigger_period?: "HOUR" | "DAY" | "WEEK" | "MONTH";
  /** Targeted recipient group ids */
  notification_trigger_recipient_groups?: string[];
  /** Targeted recipient user ids */
  notification_trigger_recipient_users?: string[];
  /** Resource type watched by a live trigger */
  notification_trigger_resource_type?:
    | "ASSET"
    | "AGENT"
    | "SCENARIO"
    | "SIMULATION"
    | "PLAYER"
    | "USER"
    | "TEAM"
    | "ATOMIC_TESTING"
    | "NOTIFICATION_TRIGGER"
    | "NOTIFIER"
    | "NOTIFICATION"
    | "PAYLOAD"
    | "THREAT_ARSENAL"
    | "RESOURCE_TYPE"
    | "SECURITY_PLATFORM"
    | "CREDENTIAL"
    | "DOCUMENT"
    | "CHANNEL"
    | "PHISHING_LANDING_PAGE"
    | "PHISHING_EMAIL_TEMPLATE"
    | "FINDING"
    | "DASHBOARD"
    | "REPORT"
    | "PLATFORM_SETTING"
    | "LESSON_LEARNED"
    | "CHALLENGE"
    | "INJECT"
    | "JOB"
    | "TAG"
    | "TAG_RULE"
    | "KILL_CHAIN_PHASE"
    | "ATTACK_PATTERN"
    | "ASSET_GROUP"
    | "VULNERABILITY"
    | "USER_GROUP"
    | "INJECTOR"
    | "INJECTOR_CONTRACT"
    | "MAPPER"
    | "GROUP_ROLE"
    | "ORGANIZATION"
    | "COLLECTOR"
    | "STIX_BUNDLE"
    | "DOMAIN"
    | "OBJECTIVE"
    | "EVALUATION"
    | "CATALOG"
    | "CONNECTOR_INSTANCE_LOG"
    | "SECRET_PROVIDER"
    | "TENANT"
    | "TENANT_SETTING"
    | "PLATFORM_ROLE"
    | "PLATFORM_GROUP"
    | "PLATFORM_USER"
    | "XTM_HUB_REGISTRATION"
    | "UNKNOWN"
    | "SIMULATION_OR_SCENARIO"
    | "WORKFLOW"
    | "STEP"
    | "CONDITION"
    | "SKIP_RBAC";
  /** Digest firing time (UTC) */
  notification_trigger_time?: string;
  /** Type of the trigger (LIVE or DIGEST) */
  notification_trigger_type?: "LIVE" | "DIGEST";
  /**
   * Last update date
   * @format date-time
   */
  notification_trigger_updated_at?: string;
}

export interface NotifierInput {
  /** Type-specific configuration: email = subject/template (FreeMarker), webhook = url/verb/headers/template */
  notifier_configuration?: Record<string, any>;
  /** Description of the notifier */
  notifier_description?: string;
  /**
   * Name of the notifier
   * @minLength 1
   */
  notifier_name: string;
  /** Type of the notifier (UI, EMAIL, WEBHOOK) */
  notifier_type: "UI" | "EMAIL" | "WEBHOOK";
}

export interface NotifierOutput {
  /** Whether the notifier is built-in (read-only) */
  notifier_built_in?: boolean;
  /** Type-specific configuration */
  notifier_configuration?: Record<string, any>;
  /**
   * Creation date of the notifier
   * @format date-time
   */
  notifier_created_at?: string;
  /** Description of the notifier */
  notifier_description?: string;
  /** ID of the notifier */
  notifier_id: string;
  /** Name of the notifier */
  notifier_name?: string;
  /** Type of the notifier (UI, EMAIL, WEBHOOK) */
  notifier_type?: "UI" | "EMAIL" | "WEBHOOK";
  /**
   * Last update date of the notifier
   * @format date-time
   */
  notifier_updated_at?: string;
}

export interface OAuthProvider {
  provider_login?: string;
  provider_name?: string;
  provider_uri?: string;
}

export interface Objective {
  listened?: boolean;
  /** @format date-time */
  objective_created_at: string;
  objective_description?: string;
  objective_evaluations?: string[];
  objective_exercise?: string;
  /** @minLength 1 */
  objective_id: string;
  /** @format int32 */
  objective_priority?: number;
  objective_scenario?: string;
  /** @format double */
  objective_score?: number;
  objective_title?: string;
  /** @format date-time */
  objective_updated_at: string;
}

export interface ObjectiveInput {
  objective_description?: string;
  /** @format int32 */
  objective_priority?: number;
  objective_title?: string;
}

export interface Option {
  id?: string;
  label?: string;
}

export interface Organization {
  injects?: Inject[];
  listened?: boolean;
  /** @format date-time */
  organization_created_at: string;
  organization_description?: string;
  /** @minLength 1 */
  organization_id: string;
  organization_injects?: string[];
  /** @format int64 */
  organization_injects_number?: number;
  /** @minLength 1 */
  organization_name: string;
  organization_tags?: string[];
  /** @format date-time */
  organization_updated_at: string;
}

export interface OrganizationBulkProcessingInput {
  organization_ids_to_ignore?: string[];
  organization_ids_to_process?: string[];
  search_pagination_input?: SearchPaginationInput;
}

export interface OrganizationCreateInput {
  organization_description?: string;
  /** @minLength 1 */
  organization_name: string;
  organization_tags?: string[];
}

export interface OrganizationUpdateInput {
  organization_description?: string;
  /** @minLength 1 */
  organization_name: string;
  organization_tags?: string[];
}

export interface OutputParser {
  listened?: boolean;
  /** @uniqueItems true */
  output_parser_contract_output_elements: ContractOutputElement[];
  /** @format date-time */
  output_parser_created_at: string;
  /** @minLength 1 */
  output_parser_id: string;
  output_parser_mode: "STDOUT" | "STDERR" | "READ_FILE";
  output_parser_type: "REGEX";
  /** @format date-time */
  output_parser_updated_at: string;
}

export interface OutputParserInput {
  /**
   * List of Contract output elements
   * @uniqueItems true
   */
  output_parser_contract_output_elements: ContractOutputElementInput[];
  output_parser_id?: string;
  /** Paser Mode: STDOUT, STDERR, READ_FILE */
  output_parser_mode: "STDOUT" | "STDERR" | "READ_FILE";
  /** Parser Type: REGEX */
  output_parser_type: "REGEX";
}

/** Represents a single output parser */
export interface OutputParserSimple {
  /** @uniqueItems true */
  output_parser_contract_output_elements: ContractOutputElementSimple[];
  /** @minLength 1 */
  output_parser_id: string;
  /** Mode of parser, which output will be parsed, for now only STDOUT is supported */
  output_parser_mode: "STDOUT" | "STDERR" | "READ_FILE";
  /** Type of parser, for now only REGEX is supported */
  output_parser_type: "REGEX";
}

export interface PageAggregatedFindingOutput {
  content?: AggregatedFindingOutput[];
  empty?: boolean;
  first?: boolean;
  last?: boolean;
  /** @format int32 */
  number?: number;
  /** @format int32 */
  numberOfElements?: number;
  pageable?: PageableObject;
  /** @format int32 */
  size?: number;
  sort?: SortObject[];
  /** @format int64 */
  totalElements?: number;
  /** @format int32 */
  totalPages?: number;
}

export interface PageAsset {
  content?: Asset[];
  empty?: boolean;
  first?: boolean;
  last?: boolean;
  /** @format int32 */
  number?: number;
  /** @format int32 */
  numberOfElements?: number;
  pageable?: PageableObject;
  /** @format int32 */
  size?: number;
  sort?: SortObject[];
  /** @format int64 */
  totalElements?: number;
  /** @format int32 */
  totalPages?: number;
}

export interface PageAssetGroupOutput {
  content?: AssetGroupOutput[];
  empty?: boolean;
  first?: boolean;
  last?: boolean;
  /** @format int32 */
  number?: number;
  /** @format int32 */
  numberOfElements?: number;
  pageable?: PageableObject;
  /** @format int32 */
  size?: number;
  sort?: SortObject[];
  /** @format int64 */
  totalElements?: number;
  /** @format int32 */
  totalPages?: number;
}

export interface PageAssetOutput {
  content?: AssetOutput[];
  empty?: boolean;
  first?: boolean;
  last?: boolean;
  /** @format int32 */
  number?: number;
  /** @format int32 */
  numberOfElements?: number;
  pageable?: PageableObject;
  /** @format int32 */
  size?: number;
  sort?: SortObject[];
  /** @format int64 */
  totalElements?: number;
  /** @format int32 */
  totalPages?: number;
}

export interface PageAttackPattern {
  content?: AttackPattern[];
  empty?: boolean;
  first?: boolean;
  last?: boolean;
  /** @format int32 */
  number?: number;
  /** @format int32 */
  numberOfElements?: number;
  pageable?: PageableObject;
  /** @format int32 */
  size?: number;
  sort?: SortObject[];
  /** @format int64 */
  totalElements?: number;
  /** @format int32 */
  totalPages?: number;
}

export interface PageConnectorInstanceLog {
  content?: ConnectorInstanceLog[];
  empty?: boolean;
  first?: boolean;
  last?: boolean;
  /** @format int32 */
  number?: number;
  /** @format int32 */
  numberOfElements?: number;
  pageable?: PageableObject;
  /** @format int32 */
  size?: number;
  sort?: SortObject[];
  /** @format int64 */
  totalElements?: number;
  /** @format int32 */
  totalPages?: number;
}

export interface PageCredentialOutput {
  content?: CredentialOutput[];
  empty?: boolean;
  first?: boolean;
  last?: boolean;
  /** @format int32 */
  number?: number;
  /** @format int32 */
  numberOfElements?: number;
  pageable?: PageableObject;
  /** @format int32 */
  size?: number;
  sort?: SortObject[];
  /** @format int64 */
  totalElements?: number;
  /** @format int32 */
  totalPages?: number;
}

export interface PageCustomDashboard {
  content?: CustomDashboard[];
  empty?: boolean;
  first?: boolean;
  last?: boolean;
  /** @format int32 */
  number?: number;
  /** @format int32 */
  numberOfElements?: number;
  pageable?: PageableObject;
  /** @format int32 */
  size?: number;
  sort?: SortObject[];
  /** @format int64 */
  totalElements?: number;
  /** @format int32 */
  totalPages?: number;
}

export interface PageCustomDomain {
  content?: CustomDomain[];
  empty?: boolean;
  first?: boolean;
  last?: boolean;
  /** @format int32 */
  number?: number;
  /** @format int32 */
  numberOfElements?: number;
  pageable?: PageableObject;
  /** @format int32 */
  size?: number;
  sort?: SortObject[];
  /** @format int64 */
  totalElements?: number;
  /** @format int32 */
  totalPages?: number;
}

export interface PageEndpointOutput {
  content?: EndpointOutput[];
  empty?: boolean;
  first?: boolean;
  last?: boolean;
  /** @format int32 */
  number?: number;
  /** @format int32 */
  numberOfElements?: number;
  pageable?: PageableObject;
  /** @format int32 */
  size?: number;
  sort?: SortObject[];
  /** @format int64 */
  totalElements?: number;
  /** @format int32 */
  totalPages?: number;
}

export interface PageEndpointTargetOutput {
  content?: EndpointTargetOutput[];
  empty?: boolean;
  first?: boolean;
  last?: boolean;
  /** @format int32 */
  number?: number;
  /** @format int32 */
  numberOfElements?: number;
  pageable?: PageableObject;
  /** @format int32 */
  size?: number;
  sort?: SortObject[];
  /** @format int64 */
  totalElements?: number;
  /** @format int32 */
  totalPages?: number;
}

export interface PageExerciseSimple {
  content?: ExerciseSimple[];
  empty?: boolean;
  first?: boolean;
  last?: boolean;
  /** @format int32 */
  number?: number;
  /** @format int32 */
  numberOfElements?: number;
  pageable?: PageableObject;
  /** @format int32 */
  size?: number;
  sort?: SortObject[];
  /** @format int64 */
  totalElements?: number;
  /** @format int32 */
  totalPages?: number;
}

export interface PageFullTextSearchResult {
  content?: FullTextSearchResult[];
  empty?: boolean;
  first?: boolean;
  last?: boolean;
  /** @format int32 */
  number?: number;
  /** @format int32 */
  numberOfElements?: number;
  pageable?: PageableObject;
  /** @format int32 */
  size?: number;
  sort?: SortObject[];
  /** @format int64 */
  totalElements?: number;
  /** @format int32 */
  totalPages?: number;
}

export interface PageGroup {
  content?: Group[];
  empty?: boolean;
  first?: boolean;
  last?: boolean;
  /** @format int32 */
  number?: number;
  /** @format int32 */
  numberOfElements?: number;
  pageable?: PageableObject;
  /** @format int32 */
  size?: number;
  sort?: SortObject[];
  /** @format int64 */
  totalElements?: number;
  /** @format int32 */
  totalPages?: number;
}

export interface PageInjectResultOutput {
  content?: InjectResultOutput[];
  empty?: boolean;
  first?: boolean;
  last?: boolean;
  /** @format int32 */
  number?: number;
  /** @format int32 */
  numberOfElements?: number;
  pageable?: PageableObject;
  /** @format int32 */
  size?: number;
  sort?: SortObject[];
  /** @format int64 */
  totalElements?: number;
  /** @format int32 */
  totalPages?: number;
}

export interface PageInjectTarget {
  content?: InjectTarget[];
  empty?: boolean;
  first?: boolean;
  last?: boolean;
  /** @format int32 */
  number?: number;
  /** @format int32 */
  numberOfElements?: number;
  pageable?: PageableObject;
  /** @format int32 */
  size?: number;
  sort?: SortObject[];
  /** @format int64 */
  totalElements?: number;
  /** @format int32 */
  totalPages?: number;
}

export interface PageInjectTestStatusOutput {
  content?: InjectTestStatusOutput[];
  empty?: boolean;
  first?: boolean;
  last?: boolean;
  /** @format int32 */
  number?: number;
  /** @format int32 */
  numberOfElements?: number;
  pageable?: PageableObject;
  /** @format int32 */
  size?: number;
  sort?: SortObject[];
  /** @format int64 */
  totalElements?: number;
  /** @format int32 */
  totalPages?: number;
}

export interface PageInjectorContractBaseOutput {
  content?: InjectorContractBaseOutput[];
  empty?: boolean;
  first?: boolean;
  last?: boolean;
  /** @format int32 */
  number?: number;
  /** @format int32 */
  numberOfElements?: number;
  pageable?: PageableObject;
  /** @format int32 */
  size?: number;
  sort?: SortObject[];
  /** @format int64 */
  totalElements?: number;
  /** @format int32 */
  totalPages?: number;
}

export interface PageKillChainPhase {
  content?: KillChainPhase[];
  empty?: boolean;
  first?: boolean;
  last?: boolean;
  /** @format int32 */
  number?: number;
  /** @format int32 */
  numberOfElements?: number;
  pageable?: PageableObject;
  /** @format int32 */
  size?: number;
  sort?: SortObject[];
  /** @format int64 */
  totalElements?: number;
  /** @format int32 */
  totalPages?: number;
}

export interface PageLessonsTemplate {
  content?: LessonsTemplate[];
  empty?: boolean;
  first?: boolean;
  last?: boolean;
  /** @format int32 */
  number?: number;
  /** @format int32 */
  numberOfElements?: number;
  pageable?: PageableObject;
  /** @format int32 */
  size?: number;
  sort?: SortObject[];
  /** @format int64 */
  totalElements?: number;
  /** @format int32 */
  totalPages?: number;
}

export interface PageMitigation {
  content?: Mitigation[];
  empty?: boolean;
  first?: boolean;
  last?: boolean;
  /** @format int32 */
  number?: number;
  /** @format int32 */
  numberOfElements?: number;
  pageable?: PageableObject;
  /** @format int32 */
  size?: number;
  sort?: SortObject[];
  /** @format int64 */
  totalElements?: number;
  /** @format int32 */
  totalPages?: number;
}

export interface PageNotificationOutput {
  content?: NotificationOutput[];
  empty?: boolean;
  first?: boolean;
  last?: boolean;
  /** @format int32 */
  number?: number;
  /** @format int32 */
  numberOfElements?: number;
  pageable?: PageableObject;
  /** @format int32 */
  size?: number;
  sort?: SortObject[];
  /** @format int64 */
  totalElements?: number;
  /** @format int32 */
  totalPages?: number;
}

export interface PageNotificationTriggerOutput {
  content?: NotificationTriggerOutput[];
  empty?: boolean;
  first?: boolean;
  last?: boolean;
  /** @format int32 */
  number?: number;
  /** @format int32 */
  numberOfElements?: number;
  pageable?: PageableObject;
  /** @format int32 */
  size?: number;
  sort?: SortObject[];
  /** @format int64 */
  totalElements?: number;
  /** @format int32 */
  totalPages?: number;
}

export interface PageNotifierOutput {
  content?: NotifierOutput[];
  empty?: boolean;
  first?: boolean;
  last?: boolean;
  /** @format int32 */
  number?: number;
  /** @format int32 */
  numberOfElements?: number;
  pageable?: PageableObject;
  /** @format int32 */
  size?: number;
  sort?: SortObject[];
  /** @format int64 */
  totalElements?: number;
  /** @format int32 */
  totalPages?: number;
}

export interface PageOrganization {
  content?: Organization[];
  empty?: boolean;
  first?: boolean;
  last?: boolean;
  /** @format int32 */
  number?: number;
  /** @format int32 */
  numberOfElements?: number;
  pageable?: PageableObject;
  /** @format int32 */
  size?: number;
  sort?: SortObject[];
  /** @format int64 */
  totalElements?: number;
  /** @format int32 */
  totalPages?: number;
}

export interface PagePayload {
  content?: Payload[];
  empty?: boolean;
  first?: boolean;
  last?: boolean;
  /** @format int32 */
  number?: number;
  /** @format int32 */
  numberOfElements?: number;
  pageable?: PageableObject;
  /** @format int32 */
  size?: number;
  sort?: SortObject[];
  /** @format int64 */
  totalElements?: number;
  /** @format int32 */
  totalPages?: number;
}

export interface PagePhishingEmailTemplate {
  content?: PhishingEmailTemplate[];
  empty?: boolean;
  first?: boolean;
  last?: boolean;
  /** @format int32 */
  number?: number;
  /** @format int32 */
  numberOfElements?: number;
  pageable?: PageableObject;
  /** @format int32 */
  size?: number;
  sort?: SortObject[];
  /** @format int64 */
  totalElements?: number;
  /** @format int32 */
  totalPages?: number;
}

export interface PagePhishingLandingPage {
  content?: PhishingLandingPage[];
  empty?: boolean;
  first?: boolean;
  last?: boolean;
  /** @format int32 */
  number?: number;
  /** @format int32 */
  numberOfElements?: number;
  pageable?: PageableObject;
  /** @format int32 */
  size?: number;
  sort?: SortObject[];
  /** @format int64 */
  totalElements?: number;
  /** @format int32 */
  totalPages?: number;
}

export interface PagePlatformGroupOutput {
  content?: PlatformGroupOutput[];
  empty?: boolean;
  first?: boolean;
  last?: boolean;
  /** @format int32 */
  number?: number;
  /** @format int32 */
  numberOfElements?: number;
  pageable?: PageableObject;
  /** @format int32 */
  size?: number;
  sort?: SortObject[];
  /** @format int64 */
  totalElements?: number;
  /** @format int32 */
  totalPages?: number;
}

export interface PagePlatformRoleOutput {
  content?: PlatformRoleOutput[];
  empty?: boolean;
  first?: boolean;
  last?: boolean;
  /** @format int32 */
  number?: number;
  /** @format int32 */
  numberOfElements?: number;
  pageable?: PageableObject;
  /** @format int32 */
  size?: number;
  sort?: SortObject[];
  /** @format int64 */
  totalElements?: number;
  /** @format int32 */
  totalPages?: number;
}

export interface PagePlayerOutput {
  content?: PlayerOutput[];
  empty?: boolean;
  first?: boolean;
  last?: boolean;
  /** @format int32 */
  number?: number;
  /** @format int32 */
  numberOfElements?: number;
  pageable?: PageableObject;
  /** @format int32 */
  size?: number;
  sort?: SortObject[];
  /** @format int64 */
  totalElements?: number;
  /** @format int32 */
  totalPages?: number;
}

export interface PageRawPaginationDocument {
  content?: RawPaginationDocument[];
  empty?: boolean;
  first?: boolean;
  last?: boolean;
  /** @format int32 */
  number?: number;
  /** @format int32 */
  numberOfElements?: number;
  pageable?: PageableObject;
  /** @format int32 */
  size?: number;
  sort?: SortObject[];
  /** @format int64 */
  totalElements?: number;
  /** @format int32 */
  totalPages?: number;
}

export interface PageRawPaginationImportMapper {
  content?: RawPaginationImportMapper[];
  empty?: boolean;
  first?: boolean;
  last?: boolean;
  /** @format int32 */
  number?: number;
  /** @format int32 */
  numberOfElements?: number;
  pageable?: PageableObject;
  /** @format int32 */
  size?: number;
  sort?: SortObject[];
  /** @format int64 */
  totalElements?: number;
  /** @format int32 */
  totalPages?: number;
}

export interface PageRawPaginationScenario {
  content?: RawPaginationScenario[];
  empty?: boolean;
  first?: boolean;
  last?: boolean;
  /** @format int32 */
  number?: number;
  /** @format int32 */
  numberOfElements?: number;
  pageable?: PageableObject;
  /** @format int32 */
  size?: number;
  sort?: SortObject[];
  /** @format int64 */
  totalElements?: number;
  /** @format int32 */
  totalPages?: number;
}

export interface PageRelatedFindingOutput {
  content?: RelatedFindingOutput[];
  empty?: boolean;
  first?: boolean;
  last?: boolean;
  /** @format int32 */
  number?: number;
  /** @format int32 */
  numberOfElements?: number;
  pageable?: PageableObject;
  /** @format int32 */
  size?: number;
  sort?: SortObject[];
  /** @format int64 */
  totalElements?: number;
  /** @format int32 */
  totalPages?: number;
}

export interface PageReporting {
  content?: Reporting[];
  empty?: boolean;
  first?: boolean;
  last?: boolean;
  /** @format int32 */
  number?: number;
  /** @format int32 */
  numberOfElements?: number;
  pageable?: PageableObject;
  /** @format int32 */
  size?: number;
  sort?: SortObject[];
  /** @format int64 */
  totalElements?: number;
  /** @format int32 */
  totalPages?: number;
}

export interface PageRoleOutput {
  content?: RoleOutput[];
  empty?: boolean;
  first?: boolean;
  last?: boolean;
  /** @format int32 */
  number?: number;
  /** @format int32 */
  numberOfElements?: number;
  pageable?: PageableObject;
  /** @format int32 */
  size?: number;
  sort?: SortObject[];
  /** @format int64 */
  totalElements?: number;
  /** @format int32 */
  totalPages?: number;
}

export interface PageSecurityPlatform {
  content?: SecurityPlatform[];
  empty?: boolean;
  first?: boolean;
  last?: boolean;
  /** @format int32 */
  number?: number;
  /** @format int32 */
  numberOfElements?: number;
  pageable?: PageableObject;
  /** @format int32 */
  size?: number;
  sort?: SortObject[];
  /** @format int64 */
  totalElements?: number;
  /** @format int32 */
  totalPages?: number;
}

export interface PageTag {
  content?: Tag[];
  empty?: boolean;
  first?: boolean;
  last?: boolean;
  /** @format int32 */
  number?: number;
  /** @format int32 */
  numberOfElements?: number;
  pageable?: PageableObject;
  /** @format int32 */
  size?: number;
  sort?: SortObject[];
  /** @format int64 */
  totalElements?: number;
  /** @format int32 */
  totalPages?: number;
}

export interface PageTagRuleOutput {
  content?: TagRuleOutput[];
  empty?: boolean;
  first?: boolean;
  last?: boolean;
  /** @format int32 */
  number?: number;
  /** @format int32 */
  numberOfElements?: number;
  pageable?: PageableObject;
  /** @format int32 */
  size?: number;
  sort?: SortObject[];
  /** @format int64 */
  totalElements?: number;
  /** @format int32 */
  totalPages?: number;
}

export interface PageTeamOutput {
  content?: TeamOutput[];
  empty?: boolean;
  first?: boolean;
  last?: boolean;
  /** @format int32 */
  number?: number;
  /** @format int32 */
  numberOfElements?: number;
  pageable?: PageableObject;
  /** @format int32 */
  size?: number;
  sort?: SortObject[];
  /** @format int64 */
  totalElements?: number;
  /** @format int32 */
  totalPages?: number;
}

export interface PageTenantOutput {
  content?: TenantOutput[];
  empty?: boolean;
  first?: boolean;
  last?: boolean;
  /** @format int32 */
  number?: number;
  /** @format int32 */
  numberOfElements?: number;
  pageable?: PageableObject;
  /** @format int32 */
  size?: number;
  sort?: SortObject[];
  /** @format int64 */
  totalElements?: number;
  /** @format int32 */
  totalPages?: number;
}

export interface PageUserOutput {
  content?: UserOutput[];
  empty?: boolean;
  first?: boolean;
  last?: boolean;
  /** @format int32 */
  number?: number;
  /** @format int32 */
  numberOfElements?: number;
  pageable?: PageableObject;
  /** @format int32 */
  size?: number;
  sort?: SortObject[];
  /** @format int64 */
  totalElements?: number;
  /** @format int32 */
  totalPages?: number;
}

export interface PageVulnerabilitySimple {
  content?: VulnerabilitySimple[];
  empty?: boolean;
  first?: boolean;
  last?: boolean;
  /** @format int32 */
  number?: number;
  /** @format int32 */
  numberOfElements?: number;
  pageable?: PageableObject;
  /** @format int32 */
  size?: number;
  sort?: SortObject[];
  /** @format int64 */
  totalElements?: number;
  /** @format int32 */
  totalPages?: number;
}

export interface PageableObject {
  /** @format int64 */
  offset?: number;
  /** @format int32 */
  pageNumber?: number;
  /** @format int32 */
  pageSize?: number;
  paged?: boolean;
  sort?: SortObject[];
  unpaged?: boolean;
}

export interface Pagination {
  /**
   * Page number to get
   * @format int32
   * @min 0
   */
  page: number;
  /**
   * Element number by page
   * @format int32
   * @max 1000
   */
  size: number;
}

export type Payload = BasePayload &
  (
    | BasePayloadPayloadTypeMapping<"Command", Command>
    | BasePayloadPayloadTypeMapping<"Executable", Executable>
    | BasePayloadPayloadTypeMapping<"FileDrop", FileDrop>
    | BasePayloadPayloadTypeMapping<"DnsResolution", DnsResolution>
    | BasePayloadPayloadTypeMapping<"NetworkTraffic", NetworkTraffic>
    | BasePayloadPayloadTypeMapping<"AiAttack", AiAttack>
  );

export interface PayloadArgument {
  /** @minLength 1 */
  default_value: string;
  description?: string | null;
  /** @minLength 1 */
  key: string;
  separator?: string | null;
  type:
    | "account_with_password_not_required"
    | "action_output"
    | "admin_username"
    | "asreproastable_account"
    | "asset_group_id"
    | "asset_id"
    | "computer_name"
    | "cve"
    | "delegation_account"
    | "document"
    | "domain"
    | "email"
    | "file_name"
    | "file_path"
    | "group_name"
    | "hash"
    | "host"
    | "ipv4"
    | "ipv6"
    | "ip_subnet"
    | "kerberoastable_account"
    | "key"
    | "number"
    | "password"
    | "permissions"
    | "port"
    | "service"
    | "severity"
    | "share_name"
    | "sid"
    | "targeted-asset"
    | "text"
    | "username"
    | "value"
    | "vulnerability_name"
    | "vulnerability_status";
}

export interface PayloadCommandBlock {
  command_content?: string;
  command_executor?: string;
  payload_cleanup_command?: string[];
}

export type PayloadCreateInput = BasePayloadCreateInput &
  (
    | BasePayloadCreateInputPayloadTypeMapping<"Command", Command>
    | BasePayloadCreateInputPayloadTypeMapping<"Executable", Executable>
    | BasePayloadCreateInputPayloadTypeMapping<"FileDrop", FileDrop>
    | BasePayloadCreateInputPayloadTypeMapping<"DnsResolution", DnsResolution>
    | BasePayloadCreateInputPayloadTypeMapping<"NetworkTraffic", NetworkTraffic>
  );

export interface PayloadInput {
  agent_slug?: string;
  command_content?: string | null;
  command_executor?: string | null;
  dns_resolution_hostname?: string;
  executable_file?: string;
  file_drop_file?: string;
  payload_arguments?: PayloadArgument[];
  payload_attack_patterns?: string[];
  payload_cleanup_command?: string | null;
  payload_cleanup_executor?: string | null;
  payload_description?: string;
  /** List of detection remediation gaps for collectors */
  payload_detection_remediations?: DetectionRemediationInput[];
  /** Update list of domains */
  payload_domains: string[];
  payload_execution_arch: "x86_64" | "arm64" | "ALL_ARCHITECTURES";
  payload_expectations: (
    | "ARTICLE"
    | "CHALLENGE"
    | "MANUAL"
    | "PREVENTION"
    | "DETECTION"
    | "VULNERABILITY"
  )[];
  payload_expected_security_platforms?: Record<
    string,
    (
      | "EDR"
      | "XDR"
      | "SIEM"
      | "SOAR"
      | "NDR"
      | "ISPM"
      | "EMAIL_SECURITY"
      | "LLM_FIREWALL"
      | "AI_GATEWAY"
      | "VULNERABILITY_SCANNER"
    )[]
  >;
  /** @minLength 1 */
  payload_name: string;
  /**
   * Set of output parsers
   * @uniqueItems true
   */
  payload_output_parsers?: OutputParserInput[];
  /** @minItems 1 */
  payload_platforms: (
    | "Linux"
    | "Windows"
    | "MacOS"
    | "Android"
    | "iOS"
    | "Container"
    | "Service"
    | "Generic"
    | "Internal"
    | "Unknown"
  )[];
  payload_prerequisites?: PayloadPrerequisite[];
  payload_tags?: string[];
  payload_type?: string;
}

export interface PayloadOutput {
  /** Command content for command payloads */
  command_content?: string;
  /** Executor used for command payloads */
  command_executor?: string;
  /** Hostname resolved by DNS resolution payloads */
  dns_resolution_hostname?: string;
  /** Executable file path for executable payloads */
  executable_file?: string;
  /** Dropped file path for file-drop payloads */
  file_drop_file?: string;
  /** Payload input arguments definition */
  payload_arguments?: PayloadArgument[];
  /** MITRE ATT&CK patterns associated with the payload */
  payload_attack_patterns?: string[];
  /** Cleanup command executed after payload run */
  payload_cleanup_command?: string;
  /** Executor used for cleanup operations */
  payload_cleanup_executor?: string;
  /** Collector type associated with this payload */
  payload_collector_type?: string;
  /**
   * Payload creation timestamp
   * @format date-time
   */
  payload_created_at: string;
  /** Payload description */
  payload_description?: string;
  /** Detection and remediation mappings for this payload */
  payload_detection_remediations?: DetectionRemediation[];
  /** Domains related to the payload */
  payload_domains?: string[];
  /** CPU architecture targeted for payload execution */
  payload_execution_arch: "x86_64" | "arm64" | "ALL_ARCHITECTURES";
  /** Expected output types for payload execution */
  payload_expectations?: (
    | "ARTICLE"
    | "CHALLENGE"
    | "MANUAL"
    | "PREVENTION"
    | "DETECTION"
    | "VULNERABILITY"
  )[];
  /** Optional map of expectation type to expected security platform types (empty = any) */
  payload_expected_security_platforms?: Record<
    string,
    (
      | "EDR"
      | "XDR"
      | "SIEM"
      | "SOAR"
      | "NDR"
      | "ISPM"
      | "EMAIL_SECURITY"
      | "LLM_FIREWALL"
      | "AI_GATEWAY"
      | "VULNERABILITY_SCANNER"
    )[]
  >;
  /** External reference identifier */
  payload_external_id?: string;
  /**
   * Payload unique identifier
   * @minLength 1
   */
  payload_id: string;
  /**
   * Payload display name
   * @minLength 1
   */
  payload_name: string;
  /**
   * Parsers used to process payload outputs
   * @uniqueItems true
   */
  payload_output_parsers?: OutputParser[];
  /** Supported endpoint platforms for this payload */
  payload_platforms?: (
    | "Linux"
    | "Windows"
    | "MacOS"
    | "Android"
    | "iOS"
    | "Container"
    | "Service"
    | "Generic"
    | "Internal"
    | "Unknown"
  )[];
  /** Prerequisites required before payload execution */
  payload_prerequisites?: PayloadPrerequisite[];
  /** Payload source origin */
  payload_source: "COMMUNITY" | "FILIGRAN" | "MANUAL";
  /** Current payload lifecycle status */
  payload_status: "UNVERIFIED" | "VERIFIED" | "DEPRECATED";
  /** Tags attached to the payload */
  payload_tags?: string[];
  /** Payload implementation type */
  payload_type?: string;
  /**
   * Payload last update timestamp
   * @format date-time
   */
  payload_updated_at: string;
}

export interface PayloadPrerequisite {
  check_command?: string;
  description?: string | null;
  /** @minLength 1 */
  executor: string;
  /** @minLength 1 */
  get_command: string;
}

export interface PayloadSimple {
  payload_collector_type?: string;
  payload_id?: string;
  payload_status?: "UNVERIFIED" | "VERIFIED" | "DEPRECATED";
  payload_type?: string;
}

export interface PayloadUpdateInput {
  command_content?: string | null;
  command_executor?: string | null;
  dns_resolution_hostname?: string;
  executable_file?: string;
  file_drop_file?: string;
  payload_arguments?: PayloadArgument[];
  payload_attack_patterns?: string[];
  payload_cleanup_command?: string | null;
  payload_cleanup_executor?: string | null;
  payload_description?: string;
  /** List of detection remediation gaps for collectors */
  payload_detection_remediations?: DetectionRemediationInput[];
  /** Update list of domains */
  payload_domains: string[];
  payload_execution_arch: "x86_64" | "arm64" | "ALL_ARCHITECTURES";
  payload_expectations: (
    | "ARTICLE"
    | "CHALLENGE"
    | "MANUAL"
    | "PREVENTION"
    | "DETECTION"
    | "VULNERABILITY"
  )[];
  payload_expected_security_platforms?: Record<
    string,
    (
      | "EDR"
      | "XDR"
      | "SIEM"
      | "SOAR"
      | "NDR"
      | "ISPM"
      | "EMAIL_SECURITY"
      | "LLM_FIREWALL"
      | "AI_GATEWAY"
      | "VULNERABILITY_SCANNER"
    )[]
  >;
  /** @minLength 1 */
  payload_name: string;
  /**
   * Set of output parsers
   * @uniqueItems true
   */
  payload_output_parsers?: OutputParserInput[];
  /** @minItems 1 */
  payload_platforms: (
    | "Linux"
    | "Windows"
    | "MacOS"
    | "Android"
    | "iOS"
    | "Container"
    | "Service"
    | "Generic"
    | "Internal"
    | "Unknown"
  )[];
  payload_prerequisites?: PayloadPrerequisite[];
  payload_tags?: string[];
}

export interface PayloadUpsertInput {
  command_content?: string | null;
  command_executor?: string | null;
  dns_resolution_hostname?: string;
  executable_file?: string;
  file_drop_file?: string;
  payload_arguments?: PayloadArgument[];
  payload_attack_patterns?: string[];
  payload_cleanup_command?: string | null;
  payload_cleanup_executor?: string | null;
  payload_collector?: string;
  payload_description?: string;
  /** List of detection remediation gaps for collectors */
  payload_detection_remediations?: DetectionRemediationInput[];
  /**
   * Update list of domains
   * @uniqueItems true
   */
  payload_domains: InjectorContractDomainDTO[];
  payload_elevation_required?: boolean;
  payload_execution_arch?: "x86_64" | "arm64" | "ALL_ARCHITECTURES";
  payload_expectations: (
    | "ARTICLE"
    | "CHALLENGE"
    | "MANUAL"
    | "PREVENTION"
    | "DETECTION"
    | "VULNERABILITY"
  )[];
  payload_expected_security_platforms?: Record<
    string,
    (
      | "EDR"
      | "XDR"
      | "SIEM"
      | "SOAR"
      | "NDR"
      | "ISPM"
      | "EMAIL_SECURITY"
      | "LLM_FIREWALL"
      | "AI_GATEWAY"
      | "VULNERABILITY_SCANNER"
    )[]
  >;
  /** @minLength 1 */
  payload_external_id: string;
  /** @minLength 1 */
  payload_name: string;
  /**
   * Set of output parsers
   * @uniqueItems true
   */
  payload_output_parsers?: OutputParserInput[];
  payload_platforms?: (
    | "Linux"
    | "Windows"
    | "MacOS"
    | "Android"
    | "iOS"
    | "Container"
    | "Service"
    | "Generic"
    | "Internal"
    | "Unknown"
  )[];
  payload_prerequisites?: PayloadPrerequisite[];
  payload_source: "COMMUNITY" | "FILIGRAN" | "MANUAL";
  payload_status: "UNVERIFIED" | "VERIFIED" | "DEPRECATED";
  payload_tags?: string[];
  /** @minLength 1 */
  payload_type: string;
}

export interface PayloadsDeprecateInput {
  collector_id: string;
  payload_external_ids: string[];
}

export interface PhishingEmailTemplate {
  listened?: boolean;
  phishing_email_template_add_tracking_pixel?: boolean;
  /** @format date-time */
  phishing_email_template_created_at: string;
  phishing_email_template_description?: string;
  phishing_email_template_from_email?: string;
  phishing_email_template_from_name?: string;
  phishing_email_template_html_body?: string;
  /** @minLength 1 */
  phishing_email_template_id: string;
  /** @minLength 1 */
  phishing_email_template_name: string;
  /** @minLength 1 */
  phishing_email_template_subject: string;
  phishing_email_template_text_body?: string;
  /** @format date-time */
  phishing_email_template_updated_at: string;
}

export interface PhishingEmailTemplateBulkProcessingInput {
  email_template_ids_to_ignore?: string[];
  email_template_ids_to_process?: string[];
  search_pagination_input?: SearchPaginationInput;
}

export interface PhishingEmailTemplateInput {
  phishing_email_template_add_tracking_pixel?: boolean;
  phishing_email_template_description?: string;
  phishing_email_template_from_email?: string;
  phishing_email_template_from_name?: string;
  phishing_email_template_html_body?: string;
  /** @minLength 1 */
  phishing_email_template_name: string;
  /** @minLength 1 */
  phishing_email_template_subject: string;
  phishing_email_template_text_body?: string;
}

export interface PhishingLandingPage {
  listened?: boolean;
  logos?: Document[];
  phishing_landing_page_capture_passwords?: boolean;
  phishing_landing_page_capture_submitted_data?: boolean;
  /** @format date-time */
  phishing_landing_page_created_at: string;
  phishing_landing_page_css?: string;
  phishing_landing_page_custom_domain?: string;
  phishing_landing_page_description?: string;
  phishing_landing_page_html?: string;
  /** @minLength 1 */
  phishing_landing_page_id: string;
  phishing_landing_page_logo_dark?: string;
  phishing_landing_page_logo_light?: string;
  /** @minLength 1 */
  phishing_landing_page_name: string;
  phishing_landing_page_primary_color_dark?: string;
  phishing_landing_page_primary_color_light?: string;
  phishing_landing_page_redirect_url?: string;
  /** @format date-time */
  phishing_landing_page_updated_at: string;
}

export interface PhishingLandingPageBulkProcessingInput {
  landing_page_ids_to_ignore?: string[];
  landing_page_ids_to_process?: string[];
  search_pagination_input?: SearchPaginationInput;
}

export interface PhishingLandingPageInput {
  phishing_landing_page_capture_passwords?: boolean;
  phishing_landing_page_capture_submitted_data?: boolean;
  phishing_landing_page_css?: string;
  phishing_landing_page_custom_domain?: string;
  phishing_landing_page_description?: string;
  phishing_landing_page_html?: string;
  /** @minLength 1 */
  phishing_landing_page_name: string;
  phishing_landing_page_primary_color_dark?: string;
  phishing_landing_page_primary_color_light?: string;
  phishing_landing_page_redirect_url?: string;
}

export interface PhishingLandingPageLogoInput {
  phishing_landing_page_logo_dark?: string;
  phishing_landing_page_logo_light?: string;
}

export interface PhishingLandingPageReader {
  phishing_landing_page_css?: string;
  phishing_landing_page_html?: string;
  phishing_landing_page_logo_dark?: string;
  phishing_landing_page_logo_light?: string;
  phishing_landing_page_name?: string;
  phishing_landing_page_primary_color_dark?: string;
  phishing_landing_page_primary_color_light?: string;
}

export interface PhishingSubmitInput {
  data?: Record<string, string>;
  password?: string;
  username?: string;
}

export interface PlatformGroupInput {
  group_default_user_assign?: boolean;
  platform_group_description?: string;
  /** @minLength 1 */
  platform_group_name: string;
}

export interface PlatformGroupOutput {
  group_default_user_assign?: boolean;
  platform_group_description?: string;
  /** @minLength 1 */
  platform_group_id: string;
  /** @minLength 1 */
  platform_group_name: string;
}

export interface PlatformGroupUpdateRolesInput {
  platform_group_platform_roles?: string[];
}

export interface PlatformGroupUpdateUsersInput {
  platform_group_users?: string[];
}

export interface PlatformRoleInput {
  /** @uniqueItems true */
  platform_role_capabilities?: (
    | "BYPASS"
    | "ACCESS_ASSESSMENT"
    | "MANAGE_ASSESSMENT"
    | "DELETE_ASSESSMENT"
    | "LAUNCH_ASSESSMENT"
    | "ACCESS_TEAMS_AND_PLAYERS"
    | "MANAGE_TEAMS_AND_PLAYERS"
    | "DELETE_TEAMS_AND_PLAYERS"
    | "ACCESS_ASSETS"
    | "MANAGE_ASSETS"
    | "DELETE_ASSETS"
    | "ACCESS_PAYLOADS"
    | "MANAGE_PAYLOADS"
    | "DELETE_PAYLOADS"
    | "ACCESS_THREAT_ARSENALS"
    | "MANAGE_THREAT_ARSENALS"
    | "DELETE_THREAT_ARSENALS"
    | "ACCESS_CREDENTIALS"
    | "MANAGE_CREDENTIALS"
    | "DELETE_CREDENTIALS"
    | "ACCESS_DASHBOARDS"
    | "MANAGE_DASHBOARDS"
    | "DELETE_DASHBOARDS"
    | "ACCESS_REPORTINGS"
    | "MANAGE_REPORTINGS"
    | "DELETE_REPORTINGS"
    | "ACCESS_FINDINGS"
    | "MANAGE_FINDINGS"
    | "DELETE_FINDINGS"
    | "ACCESS_DOCUMENTS"
    | "MANAGE_DOCUMENTS"
    | "DELETE_DOCUMENTS"
    | "ACCESS_CHANNELS"
    | "MANAGE_CHANNELS"
    | "DELETE_CHANNELS"
    | "ACCESS_PHISHING"
    | "MANAGE_PHISHING"
    | "DELETE_PHISHING"
    | "ACCESS_CHALLENGES"
    | "MANAGE_CHALLENGES"
    | "DELETE_CHALLENGES"
    | "ACCESS_LESSONS_LEARNED"
    | "MANAGE_LESSONS_LEARNED"
    | "DELETE_LESSONS_LEARNED"
    | "ACCESS_SECURITY_PLATFORMS"
    | "MANAGE_SECURITY_PLATFORMS"
    | "DELETE_SECURITY_PLATFORMS"
    | "ACCESS_PLATFORM_SETTINGS"
    | "MANAGE_PLATFORM_SETTINGS"
    | "ACCESS_TENANTS"
    | "MANAGE_TENANTS"
    | "DELETE_TENANTS"
    | "ACCESS_TENANT_SETTINGS"
    | "MANAGE_TENANT_SETTINGS"
    | "DELETE_TENANT_SETTINGS"
    | "ACCESS_PLATFORM_USERS_GROUPS_AND_ROLES"
    | "MANAGE_PLATFORM_USERS_GROUPS_AND_ROLES"
    | "DELETE_PLATFORM_USERS_GROUPS_AND_ROLES"
    | "MANAGE_STIX_BUNDLE"
    | "AGENT_RUNTIME_ACCESS"
  )[];
  platform_role_description?: string;
  /** @minLength 1 */
  platform_role_name: string;
}

export interface PlatformRoleOutput {
  platform_role_description?: string;
  /** @minLength 1 */
  platform_role_id: string;
  /** @minLength 1 */
  platform_role_name: string;
}

export interface PlatformSettings {
  /** True if Saml2 is enabled */
  auth_saml2_enable?: boolean;
  /** List of Saml2 providers */
  platform_saml2_providers?: OAuthProvider[];
  /** Type of analytics engine */
  analytics_engine_type?: string;
  /** Current version of analytics engine */
  analytics_engine_version?: string;
  /** True if local authentication is enabled */
  auth_local_enable?: boolean;
  /** True if OpenID is enabled */
  auth_openid_enable?: boolean;
  /** Sender mail to use by default for injects */
  default_mailer?: string;
  /** Sender display name to use by default for injects */
  default_mailer_name?: string;
  /** Reply to mail to use by default for injects */
  default_reply_to?: string;
  /** UUID of the default tenant */
  default_tenant_id?: string;
  /** List of enabled dev features */
  enabled_dev_features?: (
    | "_RESERVED"
    | "FEATURE_FLAG_ALL"
    | "STIX_SECURITY_COVERAGE_FOR_VULNERABILITIES"
    | "TENANT_FIELDS_FOR_SECURITY_COVERAGE"
    | "LEGACY_INGESTION_EXECUTION_TRACE"
    | "OPENAEV_TRIALS_XTMHUB"
    | "CREDENTIAL_ASSET"
    | "SIGNATURE_OUTPUT_PROCESSOR"
  )[];
  /** True if the Tanium Executor is enabled */
  executor_tanium_enable?: boolean;
  /**
   * Time to wait before article time has expired
   * @format int64
   */
  expectation_article_expiration_time: number;
  /**
   * Time to wait before challenge time has expired
   * @format int64
   */
  expectation_challenge_expiration_time: number;
  /**
   * Time to wait before detection time has expired
   * @format int64
   */
  expectation_detection_expiration_time: number;
  /**
   * Default score for manuel expectation
   * @format int32
   */
  expectation_manual_default_score_value: number;
  /**
   * Time to wait before manual expectation time has expired
   * @format int64
   */
  expectation_manual_expiration_time: number;
  /**
   * Time to wait before prevention time has expired
   * @format int64
   */
  expectation_prevention_expiration_time: number;
  /**
   * Time to wait before vulnerability time has expired
   * @format int64
   */
  expectation_vulnerability_expiration_time: number;
  /** Chatbot AI CGU acceptance status: pending, enabled, or disabled */
  filigran_chatbot_ai_cgu_status?: string;
  /** IMAP Service availability */
  imap_service_available?: string;
  /** Current version of Java */
  java_version?: string;
  /** URL of the server containing the map tile with dark theme */
  map_tile_server_dark?: string;
  /** URL of the server containing the map tile with light theme */
  map_tile_server_light?: string;
  /** Agent URL of the platform */
  platform_agent_url?: string;
  /** True if we have an AI token */
  platform_ai_has_token?: boolean;
  /** Chosen model of AI */
  platform_ai_model?: string;
  /** Type of AI (mistralai or openai) */
  platform_ai_type?: string;
  /** Map of the messages to display on the screen by their level (the level available are DEBUG, INFO, WARN, ERROR, FATAL) */
  platform_banner_by_level?: Record<string, string[]>;
  /** Base URL of the platform */
  platform_base_url?: string;
  /** Definition of the dark theme */
  platform_dark_theme?: ThemeInput;
  /** id of the platform */
  platform_id?: string;
  /**
   * Language of the platform
   * @minLength 1
   */
  platform_lang: string;
  /** Platform licensing information */
  platform_license?: License;
  /** Definition of the light theme */
  platform_light_theme?: ThemeInput;
  /**
   * Name of the platform
   * @minLength 1
   */
  platform_name: string;
  /** List of OpenID providers */
  platform_openid_providers?: OAuthProvider[];
  /** Policies of the platform */
  platform_policies?: PolicyInput;
  /** Current platform run mode (normal or safe) */
  platform_run_mode?: "normal" | "safe";
  /**
   * Idle timeout in milliseconds before the UI locks the screen (0 = disabled). Read-only, driven by server configuration
   * @format int64
   */
  platform_session_idle_timeout?: number;
  /**
   * Maximum number of concurrent sessions per user (0 = unlimited)
   * @format int32
   */
  platform_session_max_concurrent?: number;
  /**
   * Rolling session timeout in milliseconds (every request extends the session by this duration). Read-only, driven by server configuration
   * @format int64
   */
  platform_session_timeout?: number;
  /**
   * Theme of the platform
   * @minLength 1
   */
  platform_theme: string;
  /** Current version of the platform */
  platform_version?: string;
  /** 'true' if the platform has the whitemark activated */
  platform_whitemark?: string;
  /** True if XTM One is configured (url and token set) */
  platform_xtm_one_configured?: boolean;
  /** XTM One platform URL */
  platform_xtm_one_url?: string;
  /** Current version of the PostgreSQL */
  postgre_version?: string;
  /** Current version of RabbitMQ */
  rabbitmq_version?: string;
  /** SMTP Service availability */
  smtp_service_available?: string;
  /** True if telemetry manager enable */
  telemetry_manager_enable?: boolean;
  /** True if connection with XTM Hub is enabled */
  xtm_hub_enable?: boolean;
  /** True if xtmhub backend is reachable */
  xtm_hub_reachable?: boolean;
  /** XTM Hub should send connectivity email */
  xtm_hub_should_send_connectivity_email?: string;
  /** Url of XTM Hub */
  xtm_hub_url?: string;
}

export interface PlayerBulkProcessingInput {
  search_pagination_input?: SearchPaginationInput;
  user_ids_to_ignore?: string[];
  user_ids_to_process?: string[];
}

export interface PlayerInput {
  /** @pattern ^$|^\+[\d\s\-.()]+$ */
  user_phone2?: string;
  user_country?: string;
  /**
   * @format email
   * @minLength 1
   */
  user_email: string;
  user_firstname?: string;
  user_lastname?: string;
  user_organization?: string;
  user_pgp_key?: string;
  /** @pattern ^$|^\+[\d\s\-.()]+$ */
  user_phone?: string;
  user_tags?: string[];
  user_teams?: string[];
}

export interface PlayerOutput {
  user_phone2?: string;
  user_admin?: boolean;
  user_country?: string;
  /** @minLength 1 */
  user_email: string;
  user_firstname?: string;
  /** @minLength 1 */
  user_id: string;
  user_lastname?: string;
  user_organization?: string;
  user_pgp_key?: string;
  user_phone?: string;
  /** @uniqueItems true */
  user_tags?: string[];
}

export interface PlayerTarget {
  target_category?: string;
  target_detection_status?:
    | "FAILED"
    | "PENDING"
    | "PARTIAL"
    | "UNKNOWN"
    | "SUCCESS";
  target_execution_status?:
    | "FAILED"
    | "PENDING"
    | "PARTIAL"
    | "UNKNOWN"
    | "SUCCESS";
  target_human_response_status?:
    | "FAILED"
    | "PENDING"
    | "PARTIAL"
    | "UNKNOWN"
    | "SUCCESS";
  /** @minLength 1 */
  target_id: string;
  target_name?: string;
  target_prevention_status?:
    | "FAILED"
    | "PENDING"
    | "PARTIAL"
    | "UNKNOWN"
    | "SUCCESS";
  target_subtype?: string;
  /** @uniqueItems true */
  target_tags?: string[];
  /** @uniqueItems true */
  target_teams?: string[];
  target_type?: string;
  target_vulnerability_status?:
    | "FAILED"
    | "PENDING"
    | "PARTIAL"
    | "UNKNOWN"
    | "SUCCESS";
}

export interface PolicyInput {
  /** Consent confirmation message */
  platform_consent_confirm_text?: string;
  /** Consent message to show at login */
  platform_consent_message?: string;
  /** Message to show at login */
  platform_login_message?: string;
}

export interface PropertySchemaDTO {
  schema_property_entity: string;
  schema_property_has_dynamic_value?: boolean;
  schema_property_label: string;
  /** @minLength 1 */
  schema_property_name: string;
  schema_property_override_operators?: (
    | "eq"
    | "not_eq"
    | "contains"
    | "not_contains"
    | "starts_with"
    | "not_starts_with"
    | "gt"
    | "gte"
    | "lt"
    | "lte"
    | "empty"
    | "not_empty"
  )[];
  schema_property_type: string;
  schema_property_type_array?: boolean;
  schema_property_values?: string[];
}

export interface PublicChallenge {
  challenge_category?: string;
  challenge_content?: string;
  challenge_documents?: string[];
  challenge_flags?: PublicChallengeFlag[];
  challenge_id?: string;
  /** @format int32 */
  challenge_max_attempts?: number;
  challenge_name?: string;
  /** @format double */
  challenge_score?: number;
  challenge_tags?: string[];
  /** @format date-time */
  challenge_virtual_publication?: string;
}

export interface PublicChallengeFlag {
  flag_challenge?: string;
  flag_id?: string;
  flag_type?: "VALUE" | "VALUE_CASE" | "REGEXP";
}

export interface PublicExercise {
  description?: string;
  id?: string;
  name?: string;
}

export interface PublicPlatformSettings {
  /** True if Saml2 is enabled */
  auth_saml2_enable?: boolean;
  /** List of Saml2 providers */
  platform_saml2_providers?: OAuthProvider[];
  /** True if local authentication is enabled */
  auth_local_enable?: boolean;
  /** True if OpenID is enabled */
  auth_openid_enable?: boolean;
  /** List of enabled dev features */
  enabled_dev_features?: (
    | "_RESERVED"
    | "FEATURE_FLAG_ALL"
    | "STIX_SECURITY_COVERAGE_FOR_VULNERABILITIES"
    | "TENANT_FIELDS_FOR_SECURITY_COVERAGE"
    | "LEGACY_INGESTION_EXECUTION_TRACE"
    | "OPENAEV_TRIALS_XTMHUB"
    | "CREDENTIAL_ASSET"
    | "SIGNATURE_OUTPUT_PROCESSOR"
  )[];
  /** Map of the messages to display on the screen by their level (the level available are DEBUG, INFO, WARN, ERROR, FATAL) */
  platform_banner_by_level?: Record<string, string[]>;
  /** Definition of the dark theme */
  platform_dark_theme?: ThemeInput;
  /**
   * Language of the platform
   * @minLength 1
   */
  platform_lang: string;
  /** Definition of the light theme */
  platform_light_theme?: ThemeInput;
  /** List of OpenID providers */
  platform_openid_providers?: OAuthProvider[];
  /** Policies of the platform */
  platform_policies?: PolicyInput;
  /** Current platform run mode (normal or safe) */
  platform_run_mode?: "normal" | "safe";
  /**
   * Theme of the platform
   * @minLength 1
   */
  platform_theme: string;
  /** 'true' if the platform has the whitemark activated */
  platform_whitemark?: string;
}

export interface PublicScenario {
  description?: string;
  id?: string;
  name?: string;
}

export interface RawAttackPatternIndexing {
  /** @format date-time */
  attack_pattern_created_at?: string;
  attack_pattern_description?: string;
  attack_pattern_external_id?: string;
  attack_pattern_id?: string;
  /** @uniqueItems true */
  attack_pattern_kill_chain_phases?: string[];
  attack_pattern_name?: string;
  attack_pattern_parent?: string;
  attack_pattern_permissions_required?: string[];
  attack_pattern_platforms?: string[];
  attack_pattern_stix_id?: string;
  /** @format date-time */
  attack_pattern_updated_at?: string;
  tenant_id?: string;
}

export interface RawDocument {
  document_description?: string;
  document_exercises?: string[];
  document_id?: string;
  document_name?: string;
  document_scenarios?: string[];
  document_tags?: string[];
  document_target?: string;
  document_type?: string;
}

export interface RawPaginationDocument {
  document_can_be_deleted?: boolean;
  document_can_be_updated?: boolean;
  document_description?: string;
  document_exercises?: string[];
  document_id?: string;
  document_name?: string;
  document_scenarios?: string[];
  document_tags?: string[];
  document_type?: string;
}

export interface RawPaginationImportMapper {
  /** @format date-time */
  import_mapper_created_at?: string;
  /** @minLength 1 */
  import_mapper_id: string;
  import_mapper_name?: string;
  /** @format date-time */
  import_mapper_updated_at?: string;
}

export interface RawPaginationScenario {
  scenario_category?: string;
  scenario_description?: string;
  scenario_id?: string;
  scenario_name?: string;
  /** @uniqueItems true */
  scenario_platforms?: string[];
  scenario_recurrence?: string;
  scenario_severity?: "low" | "medium" | "high" | "critical";
  /** @uniqueItems true */
  scenario_tags?: string[];
  /** @format date-time */
  scenario_updated_at?: string;
  scenario_workflow_id?: string;
}

export interface RawUser {
  user_email?: string;
  user_firstname?: string;
  user_gravatar?: string;
  user_groups?: string[];
  user_id?: string;
  user_lastname?: string;
  user_organization?: string;
  user_phone?: string;
  user_tags?: string[];
  user_teams?: string[];
}

export interface RegexGroup {
  listened?: boolean;
  /** @format date-time */
  regex_group_created_at: string;
  /** @minLength 1 */
  regex_group_field: string;
  /** @minLength 1 */
  regex_group_id: string;
  /** @minLength 1 */
  regex_group_index_values: string;
  /** @format date-time */
  regex_group_updated_at: string;
}

export interface RegexGroupInput {
  /**
   * Field
   * @minLength 1
   */
  regex_group_field: string;
  regex_group_id?: string;
  /**
   * Index of the group from the regex match: $index0$index1
   * @minLength 1
   */
  regex_group_index_values: string;
}

/** Represents the groups defined by the regex pattern. */
export interface RegexGroupSimple {
  /**
   * Represents the field name of specific captured groups.
   * @minLength 1
   */
  regex_group_field: string;
  /** @minLength 1 */
  regex_group_id: string;
  /**
   * Represents the indexes of specific captured groups.
   * @minLength 1
   */
  regex_group_index_values: string;
}

export interface RelatedEntityOutput {
  context?: string;
  id?: string;
  name?: string;
}

export interface RelatedFindingOutput {
  /**
   * Asset groups linked to assets
   * @uniqueItems true
   */
  finding_asset_groups?: AssetGroupSimple[];
  /**
   * Assets linked to the finding (any asset type, not only endpoints)
   * @uniqueItems true
   */
  finding_assets: EndpointSimple[];
  /**
   * First time the finding was seen
   * @format date-time
   */
  finding_created_at: string;
  /**
   * Finding Id
   * @minLength 1
   */
  finding_id: string;
  /** Inject linked to finding */
  finding_inject: InjectSimple;
  /** Scenario linked to inject */
  finding_scenario?: ScenarioSimple;
  /** Simulation linked to inject */
  finding_simulation?: ExerciseSimple;
  /**
   * Teams linked to the finding occurrence
   * @uniqueItems true
   */
  finding_teams?: TargetSimple[];
  /**
   * Represents the data type being extracted.
   * @example "text, number, port, portscan, ipv4, ipv6, credentials, cve"
   */
  finding_type:
    | "text"
    | "action_output"
    | "number"
    | "port"
    | "portscan"
    | "ipv4"
    | "ipv6"
    | "credentials"
    | "cve"
    | "username"
    | "email"
    | "share"
    | "file"
    | "admin_username"
    | "group"
    | "computer"
    | "password_policy"
    | "delegation"
    | "sid"
    | "vulnerability"
    | "account_with_password_not_required"
    | "asreproastable_account"
    | "kerberoastable_account"
    | "expectation_signature";
  /**
   * Last time the finding was seen
   * @format date-time
   */
  finding_updated_at: string;
  /**
   * Players (persons) linked to the finding occurrence
   * @uniqueItems true
   */
  finding_users?: TargetSimple[];
  /**
   * Finding Value
   * @minLength 1
   */
  finding_value: string;
}

export interface Relationship {
  data: any;
}

export interface RenewTokenInput {
  /** @minLength 1 */
  token_id: string;
}

export interface Reporting {
  listened?: boolean;
  reporting_branding?: ReportingBranding;
  reporting_context_id?: string;
  reporting_context_type:
    | "PLATFORM"
    | "SIMULATION"
    | "SCENARIO"
    | "ATOMIC_TESTING"
    | "ENDPOINT"
    | "ASSET_GROUP"
    | "PLAYER"
    | "TEAM";
  /** @format date-time */
  reporting_created_at?: string;
  reporting_default_format?: "PDF" | "HTML";
  reporting_description?: string;
  reporting_generations?: ReportingGeneration[];
  /** @minLength 1 */
  reporting_id: string;
  reporting_modules?: ReportingModule[];
  /** @minLength 1 */
  reporting_name: string;
  reporting_schedules?: ReportingSchedule[];
  reporting_time_range?:
    | "LAST_7_DAYS"
    | "LAST_30_DAYS"
    | "LAST_90_DAYS"
    | "LAST_180_DAYS"
    | "LAST_365_DAYS"
    | "ALL_TIME";
  /** @format date-time */
  reporting_updated_at?: string;
}

export interface ReportingBranding {
  accent_color?: string;
  background_color?: string;
  logo_document_id?: string;
  paper_color?: string;
  primary_color?: string;
  secondary_color?: string;
  text_color?: string;
  theme_mode?: "LIGHT" | "DARK";
}

export interface ReportingGenerateInput {
  reporting_generation_format: "PDF" | "HTML";
}

export interface ReportingGeneration {
  listened?: boolean;
  /** @format date-time */
  reporting_generation_completed_at?: string;
  /** @format date-time */
  reporting_generation_created_at?: string;
  reporting_generation_document?: string;
  reporting_generation_error?: string;
  reporting_generation_format?: "PDF" | "HTML";
  /** @minLength 1 */
  reporting_generation_id: string;
  reporting_generation_reporting?: string;
  reporting_generation_status?: "PENDING" | "RUNNING" | "SUCCESS" | "ERROR";
  reporting_generation_trigger?: "MANUAL" | "SCHEDULED";
}

export interface ReportingInput {
  reporting_branding?: ReportingBranding;
  reporting_context_id?: string;
  reporting_context_type:
    | "PLATFORM"
    | "SIMULATION"
    | "SCENARIO"
    | "ATOMIC_TESTING"
    | "ENDPOINT"
    | "ASSET_GROUP"
    | "PLAYER"
    | "TEAM";
  reporting_default_format?: "PDF" | "HTML";
  reporting_description?: string;
  reporting_modules?: ReportingModule[];
  /** @minLength 1 */
  reporting_name: string;
  reporting_time_range?:
    | "LAST_7_DAYS"
    | "LAST_30_DAYS"
    | "LAST_90_DAYS"
    | "LAST_180_DAYS"
    | "LAST_365_DAYS"
    | "ALL_TIME";
}

export interface ReportingModule {
  module_config?: Record<string, any>;
  module_title?: string;
  module_type?:
    | "COVER"
    | "EXECUTIVE_SUMMARY"
    | "SUBJECT_DETAILS"
    | "MITRE_COVERAGE"
    | "RESULTS_BREAKDOWN"
    | "SECURITY_DOMAINS"
    | "SCORE_TRENDS"
    | "FAILED_EXPECTATIONS"
    | "FINDINGS"
    | "ATTACK_PATHS"
    | "CUSTOM_MARKDOWN";
}

export interface ReportingSchedule {
  listened?: boolean;
  /** @format date-time */
  reporting_schedule_created_at?: string;
  reporting_schedule_enabled?: boolean;
  reporting_schedule_format?: "PDF" | "HTML";
  /** @minLength 1 */
  reporting_schedule_id: string;
  /** @format date-time */
  reporting_schedule_last_run_at?: string;
  reporting_schedule_name?: string;
  reporting_schedule_owner: string;
  reporting_schedule_period: "HOUR" | "DAY" | "WEEK" | "MONTH";
  reporting_schedule_recipient_emails?: string[];
  reporting_schedule_recipient_users?: string[];
  reporting_schedule_reporting?: string;
  reporting_schedule_time?: string;
  /** @format date-time */
  reporting_schedule_updated_at?: string;
}

export interface ReportingScheduleInput {
  reporting_schedule_enabled?: boolean;
  reporting_schedule_format?: "PDF" | "HTML";
  reporting_schedule_name?: string;
  reporting_schedule_period: "HOUR" | "DAY" | "WEEK" | "MONTH";
  reporting_schedule_recipient_emails?: string[];
  reporting_schedule_recipient_users?: string[];
  reporting_schedule_time?: string;
}

export interface ResetUserInput {
  lang?: string;
  /** @minLength 1 */
  login: string;
}

/** An installed injector contract that satisfies the capability */
export interface ResolvedContract {
  injector_contract_id?: string;
  injector_type?: string;
  label?: string;
  platforms?: string[];
}

export interface ResourceObject {
  attributes?: Record<string, any>;
  /** @minLength 1 */
  id: string;
  relationships?: Record<string, Relationship>;
  /** @minLength 1 */
  type: string;
}

export interface ResultDistribution {
  id: string;
  label: string;
  /** @format int32 */
  value: number;
}

export interface RoleInput {
  /** @uniqueItems true */
  role_capabilities?: (
    | "BYPASS"
    | "ACCESS_ASSESSMENT"
    | "MANAGE_ASSESSMENT"
    | "DELETE_ASSESSMENT"
    | "LAUNCH_ASSESSMENT"
    | "ACCESS_TEAMS_AND_PLAYERS"
    | "MANAGE_TEAMS_AND_PLAYERS"
    | "DELETE_TEAMS_AND_PLAYERS"
    | "ACCESS_ASSETS"
    | "MANAGE_ASSETS"
    | "DELETE_ASSETS"
    | "ACCESS_PAYLOADS"
    | "MANAGE_PAYLOADS"
    | "DELETE_PAYLOADS"
    | "ACCESS_THREAT_ARSENALS"
    | "MANAGE_THREAT_ARSENALS"
    | "DELETE_THREAT_ARSENALS"
    | "ACCESS_CREDENTIALS"
    | "MANAGE_CREDENTIALS"
    | "DELETE_CREDENTIALS"
    | "ACCESS_DASHBOARDS"
    | "MANAGE_DASHBOARDS"
    | "DELETE_DASHBOARDS"
    | "ACCESS_REPORTINGS"
    | "MANAGE_REPORTINGS"
    | "DELETE_REPORTINGS"
    | "ACCESS_FINDINGS"
    | "MANAGE_FINDINGS"
    | "DELETE_FINDINGS"
    | "ACCESS_DOCUMENTS"
    | "MANAGE_DOCUMENTS"
    | "DELETE_DOCUMENTS"
    | "ACCESS_CHANNELS"
    | "MANAGE_CHANNELS"
    | "DELETE_CHANNELS"
    | "ACCESS_PHISHING"
    | "MANAGE_PHISHING"
    | "DELETE_PHISHING"
    | "ACCESS_CHALLENGES"
    | "MANAGE_CHALLENGES"
    | "DELETE_CHALLENGES"
    | "ACCESS_LESSONS_LEARNED"
    | "MANAGE_LESSONS_LEARNED"
    | "DELETE_LESSONS_LEARNED"
    | "ACCESS_SECURITY_PLATFORMS"
    | "MANAGE_SECURITY_PLATFORMS"
    | "DELETE_SECURITY_PLATFORMS"
    | "ACCESS_PLATFORM_SETTINGS"
    | "MANAGE_PLATFORM_SETTINGS"
    | "ACCESS_TENANTS"
    | "MANAGE_TENANTS"
    | "DELETE_TENANTS"
    | "ACCESS_TENANT_SETTINGS"
    | "MANAGE_TENANT_SETTINGS"
    | "DELETE_TENANT_SETTINGS"
    | "ACCESS_PLATFORM_USERS_GROUPS_AND_ROLES"
    | "MANAGE_PLATFORM_USERS_GROUPS_AND_ROLES"
    | "DELETE_PLATFORM_USERS_GROUPS_AND_ROLES"
    | "MANAGE_STIX_BUNDLE"
    | "AGENT_RUNTIME_ACCESS"
  )[];
  role_description?: string;
  /** @minLength 1 */
  role_name: string;
}

export interface RoleOutput {
  /** @uniqueItems true */
  role_capabilities?: string[];
  role_created_at?: string;
  role_description?: string;
  /** @minLength 1 */
  role_id: string;
  /** @minLength 1 */
  role_name: string;
  role_updated_at?: string;
}

export interface RuleAttribute {
  listened?: boolean;
  rule_attribute_additional_config?: Record<string, string>;
  rule_attribute_columns?: string;
  /** @format date-time */
  rule_attribute_created_at?: string;
  rule_attribute_default_value?: string;
  rule_attribute_id: string;
  /** @minLength 1 */
  rule_attribute_name: string;
  /** @format date-time */
  rule_attribute_updated_at?: string;
}

export interface RuleAttributeAddInput {
  rule_attribute_additional_config?: Record<string, string>;
  rule_attribute_columns?: string | null;
  rule_attribute_default_value?: string;
  /** @minLength 1 */
  rule_attribute_name: string;
}

export interface RuleAttributeUpdateInput {
  rule_attribute_additional_config?: Record<string, string>;
  rule_attribute_columns?: string | null;
  rule_attribute_default_value?: string;
  rule_attribute_id?: string;
  /** @minLength 1 */
  rule_attribute_name: string;
}

export interface Scenario {
  listened?: boolean;
  /** @format int64 */
  scenario_all_users_number?: number;
  scenario_articles?: string[];
  scenario_autonomous?: boolean;
  scenario_category?: string;
  /** @format int64 */
  scenario_communications_number?: number;
  /** @format date-time */
  scenario_created_at: string;
  scenario_custom_dashboard?: string;
  scenario_default_kill_chain?: string;
  scenario_dependencies?: "STARTERPACK"[];
  scenario_description?: string;
  scenario_documents?: string[];
  scenario_exercises?: string[];
  scenario_expectations_drift_dismissed?: boolean;
  scenario_external_reference?: string;
  scenario_external_url?: string;
  /** @minLength 1 */
  scenario_id: string;
  scenario_injects?: string[];
  scenario_injects_statistics?: Record<string, number>;
  scenario_kill_chain_phases?: KillChainPhase[];
  scenario_lessons_anonymized?: boolean;
  scenario_lessons_categories?: string[];
  scenario_lessons_enabled?: boolean;
  /**
   * @format email
   * @minLength 1
   */
  scenario_mail_from: string;
  /**
   * @minLength 0
   * @maxLength 100
   * @pattern ^[^\r\n\x00]*$
   */
  scenario_mail_from_name?: string;
  scenario_mails_reply_to?: string[];
  scenario_main_focus?: string;
  scenario_message_footer?: string;
  scenario_message_header?: string;
  /** @minLength 1 */
  scenario_name: string;
  scenario_observers?: string[];
  scenario_planners?: string[];
  scenario_platforms?: (
    | "Linux"
    | "Windows"
    | "MacOS"
    | "Android"
    | "iOS"
    | "Container"
    | "Service"
    | "Generic"
    | "Internal"
    | "Unknown"
  )[];
  scenario_recurrence?: string;
  /** @format date-time */
  scenario_recurrence_end?: string;
  /** @format date-time */
  scenario_recurrence_start?: string;
  scenario_severity?: "low" | "medium" | "high" | "critical";
  scenario_subtitle?: string;
  scenario_tags?: string[];
  scenario_teams?: string[];
  scenario_teams_users?: ScenarioTeamUser[];
  scenario_type?: string;
  scenario_type_affinity?: string;
  /** @format date-time */
  scenario_updated_at: string;
  scenario_users?: string[];
  /** @format int64 */
  scenario_users_number?: number;
}

export interface ScenarioAndInjectorContractsInputs {
  injector_contract_search_pagination_input: InjectorContractSearchPaginationInput;
  /** @minLength 1 */
  locale: string;
  scenario_input: ScenarioInput;
}

export interface ScenarioBulkProcessingInput {
  scenario_ids_to_ignore?: string[];
  scenario_ids_to_process?: string[];
  search_pagination_input?: SearchPaginationInput;
}

export interface ScenarioChallengesReader {
  scenario_challenges?: ChallengeInformation[];
  scenario_id?: string;
  scenario_information?: PublicScenario;
}

export interface ScenarioIdsAndInjectorContractsInputs {
  injector_contract_search_pagination_input: InjectorContractSearchPaginationInput;
  /** @minLength 1 */
  locale: string;
  /** @minItems 1 */
  scenario_ids: string[];
}

export interface ScenarioInput {
  scenario_category?: string;
  scenario_custom_dashboard?: string;
  scenario_default_kill_chain?: string;
  scenario_description?: string;
  scenario_external_reference?: string;
  scenario_external_url?: string;
  scenario_is_chaining?: boolean;
  /**
   * @minLength 0
   * @maxLength 100
   * @pattern ^[^\r\n\x00]*$
   */
  scenario_mail_from_name?: string;
  scenario_mails_reply_to?: string[];
  scenario_main_focus?: string;
  scenario_message_footer?: string;
  scenario_message_header?: string;
  /** @minLength 1 */
  scenario_name: string;
  scenario_severity?: "low" | "medium" | "high" | "critical";
  scenario_subtitle?: string;
  scenario_tags?: string[];
}

export interface ScenarioOutput {
  /** Lesson anonymized state of the scenario */
  lessonsAnonymized?: boolean;
  /**
   * Total number of users of the scenario
   * @format int64
   */
  scenario_all_users_number?: number;
  /** Whether the scenario is an autonomous (AI-driven) attack-path scenario */
  scenario_autonomous?: boolean;
  /** Category of the scenario */
  scenario_category?: string;
  /**
   * Creation date of the scenario
   * @format date-time
   */
  scenario_created_at: string;
  /** Custom dashboard of the scenario */
  scenario_custom_dashboard?: string;
  /** Kill chain displayed first in the overview kill chain results */
  scenario_default_kill_chain?: string;
  /** @uniqueItems true */
  scenario_dependencies?: string[];
  /** Description of the scenario */
  scenario_description?: string;
  /** @uniqueItems true */
  scenario_exercises?: string[];
  /** External URL of the scenario */
  scenario_external_url?: string;
  /**
   * ID of the scenario
   * @minLength 1
   */
  scenario_id: string;
  /** @uniqueItems true */
  scenario_kill_chain_phases?: KillChainPhaseOutput[];
  /** Whether the lessons learned module is enabled for the scenario */
  scenario_lessons_enabled?: boolean;
  /**
   * From value of the scenario
   * @minLength 1
   */
  scenario_mail_from: string;
  /** Sender display name of the scenario */
  scenario_mail_from_name?: string;
  /** Main focus value of the scenario */
  scenario_main_focus?: string;
  /** Footer of the scenario */
  scenario_message_footer?: string;
  /** Header of the scenario */
  scenario_message_header?: string;
  /**
   * Name of the scenario
   * @minLength 1
   */
  scenario_name: string;
  /** @uniqueItems true */
  scenario_platforms?: string[];
  /** Recurrence of the scenario */
  scenario_recurrence?: string;
  /**
   * Recurrence end date of the scenario
   * @format date-time
   */
  scenario_recurrence_end?: string;
  /**
   * Recurrence start date of the scenario
   * @format date-time
   */
  scenario_recurrence_start?: string;
  /** Severity of the scenario */
  scenario_severity?: string;
  /** Subtitle of the scenario */
  scenario_subtitle?: string;
  /** @uniqueItems true */
  scenario_tags?: string[];
  /** @uniqueItems true */
  scenario_teams_users?: ScenarioTeamUserOutput[];
  /** Type affinity of the scenario */
  scenario_type_affinity?: string;
  /**
   * Update date of the scenario
   * @format date-time
   */
  scenario_updated_at: string;
  /**
   * Active total number of users of the scenario
   * @format int64
   */
  scenario_users_number?: number;
  /** Workflow ID associated with the scenario */
  scenario_workflow_id?: string;
}

export interface ScenarioRecurrenceInput {
  scenario_recurrence?: string;
  /** @format date-time */
  scenario_recurrence_end?: string;
  /** @format date-time */
  scenario_recurrence_start?: string;
}

export interface ScenarioSimple {
  scenario_id?: string;
  scenario_name?: string;
  scenario_subtitle?: string;
  scenario_tags?: string[];
}

export interface ScenarioStatistic {
  simulations_results_latest: SimulationsResultsLatest;
}

export interface ScenarioTeamPlayersEnableInput {
  scenario_team_players?: string[];
}

export interface ScenarioTeamUser {
  scenario_id?: string;
  team_id?: string;
  user_id?: string;
}

export interface ScenarioTeamUserOutput {
  /** ID of the scenario */
  scenario_id?: string;
  /** ID of the team */
  team_id?: string;
  /** ID of the user */
  user_id?: string;
}

export interface ScenarioUpdateTagsInput {
  apply_tag_rule?: boolean;
  scenario_tags?: string[];
}

export interface ScenarioUpdateTeamsInput {
  scenario_teams?: string[];
}

/** An asset that is in scope (allowlisted and not denylisted) for a workflow. */
export interface ScopeAssetOutput {
  /** External reference of the asset */
  asset_external_reference?: string;
  /** ID of the asset */
  asset_id?: string;
  /** Name of the asset */
  asset_name?: string;
  /** Type of the asset (Endpoint, SecurityPlatform, …) */
  asset_type?: string;
}

/** A team that is in scope (allowlisted and not denylisted) for a workflow. */
export interface ScopeTeamOutput {
  /** ID of the team */
  team_id?: string;
  /** Name of the team */
  team_name?: string;
}

export interface ScopeVariable {
  listened?: boolean;
  /** @format date-time */
  scope_variable_created_at?: string;
  scope_variable_description?: string;
  scope_variable_id: string;
  scope_variable_key: string;
  scope_variable_type:
    | "account_with_password_not_required"
    | "action_output"
    | "admin_username"
    | "asreproastable_account"
    | "asset_group_id"
    | "asset_id"
    | "computer_name"
    | "cve"
    | "delegation_account"
    | "document"
    | "domain"
    | "email"
    | "file_name"
    | "file_path"
    | "group_name"
    | "hash"
    | "host"
    | "ipv4"
    | "ipv6"
    | "ip_subnet"
    | "kerberoastable_account"
    | "key"
    | "number"
    | "password"
    | "permissions"
    | "port"
    | "service"
    | "severity"
    | "share_name"
    | "sid"
    | "targeted-asset"
    | "text"
    | "username"
    | "value"
    | "vulnerability_name"
    | "vulnerability_status";
  /** @format date-time */
  scope_variable_updated_at?: string;
  scope_variable_value?: string;
  scope_variable_workflow?: string;
}

/** Input for a scope variable attached to a workflow. */
export interface ScopeVariableInput {
  /** Optional description of the variable's purpose. */
  scope_variable_description?: string;
  /** ID of an existing scope variable. Null means a new variable will be created. */
  scope_variable_id?: string;
  /**
   * Unique key used to reference the variable in templates (e.g. company_name).
   * @minLength 1
   */
  scope_variable_key: string;
  /** Argument type driving how the variable value is interpreted. */
  scope_variable_type:
    | "account_with_password_not_required"
    | "action_output"
    | "admin_username"
    | "asreproastable_account"
    | "asset_group_id"
    | "asset_id"
    | "computer_name"
    | "cve"
    | "delegation_account"
    | "document"
    | "domain"
    | "email"
    | "file_name"
    | "file_path"
    | "group_name"
    | "hash"
    | "host"
    | "ipv4"
    | "ipv6"
    | "ip_subnet"
    | "kerberoastable_account"
    | "key"
    | "number"
    | "password"
    | "permissions"
    | "port"
    | "service"
    | "severity"
    | "share_name"
    | "sid"
    | "targeted-asset"
    | "text"
    | "username"
    | "value"
    | "vulnerability_name"
    | "vulnerability_status";
  /**
   * Value of the variable.
   * @minLength 1
   */
  scope_variable_value: string;
}

/** Output for a scope variable attached to a workflow. */
export interface ScopeVariableOutput {
  /** Optional description of the variable's purpose. */
  scope_variable_description?: string;
  /** Unique ID of the scope variable. */
  scope_variable_id?: string;
  /** Key used to reference the variable in templates. */
  scope_variable_key?: string;
  /** Argument type driving how the variable value is interpreted. */
  scope_variable_type?:
    | "account_with_password_not_required"
    | "action_output"
    | "admin_username"
    | "asreproastable_account"
    | "asset_group_id"
    | "asset_id"
    | "computer_name"
    | "cve"
    | "delegation_account"
    | "document"
    | "domain"
    | "email"
    | "file_name"
    | "file_path"
    | "group_name"
    | "hash"
    | "host"
    | "ipv4"
    | "ipv6"
    | "ip_subnet"
    | "kerberoastable_account"
    | "key"
    | "number"
    | "password"
    | "permissions"
    | "port"
    | "service"
    | "severity"
    | "share_name"
    | "sid"
    | "targeted-asset"
    | "text"
    | "username"
    | "value"
    | "vulnerability_name"
    | "vulnerability_status";
  /** Value of the variable. */
  scope_variable_value?: string;
}

export interface SearchPaginationInput {
  /** Filter object to search within filterable attributes */
  filterGroup?: FilterGroup;
  /**
   * Page number to get
   * @format int32
   * @min 0
   */
  page: number;
  /**
   * Element number by page
   * @format int32
   * @max 1000
   */
  size: number;
  /** List of sort fields : a field is composed of a property (for instance "label" and an optional direction ("asc" is assumed if no direction is specified) : ("desc", "asc") */
  sorts?: SortField[];
  /** Text to search within searchable attributes */
  textSearch?: string;
}

export interface SearchTerm {
  searchTerm?: string;
}

export interface SecretsProvider {
  external?: boolean;
  listened?: boolean;
  secrets_provider_id?: string;
  secrets_provider_name?: string;
  secrets_provider_type?: string;
}

/** Secrets provider output */
export interface SecretsProviderOutput {
  /** Catalog simple output */
  catalog?: CatalogConnectorSimpleOutput;
  connector_instance?: ConnectorInstanceOutput;
  existing_secret_provider?: boolean;
  is_verified?: boolean;
  /**
   * Secrets provider id
   * @minLength 1
   */
  secrets_provider_id: string;
  /** @minLength 1 */
  secrets_provider_name: string;
  /** @minLength 1 */
  secrets_provider_type: string;
}

export interface SecurityPlatform {
  ai_target_configuration?: Record<string, any>;
  ai_target_endpoint?: string;
  ai_target_modality?: "TEXT" | "VISION" | "AUDIO" | "MULTIMODAL";
  ai_target_model?: string;
  ai_target_provider?:
    | "OPENAI_COMPATIBLE"
    | "ANTHROPIC"
    | "AZURE_OPENAI"
    | "AWS_BEDROCK"
    | "GOOGLE_VERTEX"
    | "HUGGINGFACE"
    | "OLLAMA"
    | "CUSTOM_HTTP"
    | "MCP_SERVER"
    | "AGENT_HTTP"
    | "XTM_ONE";
  ai_target_system_prompt?: string;
  ai_target_token?: string;
  asset_category?:
    | "HOST"
    | "CONTAINER_WORKLOAD"
    | "CLOUD_RESOURCE"
    | "WEB_APPLICATION"
    | "NETWORK_DEVICE"
    | "MOBILE_DEVICE"
    | "IOT_OT_DEVICE"
    | "IDENTITY"
    | "SAAS_APPLICATION"
    | "AI_TARGET"
    | "SECURITY_PLATFORM"
    | "GENERIC_ASSET";
  asset_cloud_native_type?: string;
  asset_cloud_provider?:
    | "AWS"
    | "AZURE"
    | "GCP"
    | "OCI"
    | "ALIBABA"
    | "KUBERNETES"
    | "OTHER";
  asset_cloud_region?: string;
  /** @format date-time */
  asset_created_at: string;
  asset_criticality?: "VERY_HIGH" | "HIGH" | "MEDIUM" | "LOW" | "UNKNOWN";
  asset_description?: string;
  asset_external_reference?: string;
  asset_hostname?: string;
  /** @minLength 1 */
  asset_id: string;
  asset_internet_facing?: boolean;
  asset_ips?: string[];
  asset_linked_person?: string;
  asset_mac_addresses?: string[];
  asset_metadata?: Record<string, any>;
  /** @minLength 1 */
  asset_name: string;
  asset_seen_ip?: string;
  /** Activity status derived from agents (ACTIVE / INACTIVE / AGENTLESS) */
  asset_status?: "ACTIVE" | "INACTIVE" | "AGENTLESS";
  asset_subcategory?:
    | "SERVER"
    | "WORKSTATION"
    | "LAPTOP"
    | "VIRTUAL_MACHINE"
    | "HYPERVISOR"
    | "MAINFRAME"
    | "THIN_CLIENT"
    | "CONTAINER"
    | "CONTAINER_IMAGE"
    | "KUBERNETES_POD"
    | "KUBERNETES_CLUSTER"
    | "KUBERNETES_NODE"
    | "SERVERLESS_FUNCTION"
    | "COMPUTE"
    | "STORAGE"
    | "DATABASE"
    | "NETWORKING"
    | "SERVERLESS"
    | "CONTAINER_REGISTRY"
    | "KUBERNETES"
    | "IAM_PRINCIPAL"
    | "SECRETS_KEY_MGMT"
    | "MESSAGING_QUEUE"
    | "ANALYTICS_DATA"
    | "AI_ML_SERVICE"
    | "IAC_TEMPLATE"
    | "CLOUD_OTHER"
    | "WEBSITE"
    | "WEB_API"
    | "SINGLE_PAGE_APP"
    | "GRAPHQL_API"
    | "WEB_SERVICE"
    | "MICROSERVICE"
    | "ROUTER"
    | "SWITCH"
    | "FIREWALL"
    | "LOAD_BALANCER"
    | "VPN_GATEWAY"
    | "WIRELESS_AP"
    | "PROXY"
    | "DNS_SERVER"
    | "DHCP_SERVER"
    | "SAN_NAS"
    | "NETWORK_OTHER"
    | "SMARTPHONE"
    | "TABLET"
    | "IOT_SENSOR"
    | "IP_CAMERA"
    | "GATEWAY"
    | "POINT_OF_SALE"
    | "MEDIA_DEVICE"
    | "PLC"
    | "RTU"
    | "HMI"
    | "SCADA_HISTORIAN"
    | "MEDICAL_DEVICE"
    | "PRINTER_PERIPHERAL"
    | "BUILDING_MGMT"
    | "USER_ACCOUNT"
    | "SERVICE_ACCOUNT"
    | "GROUP"
    | "ROLE"
    | "SHARED_MAILBOX"
    | "NON_HUMAN_IDENTITY"
    | "SAAS_APP"
    | "SAAS_TENANT"
    | "LLM_MODEL"
    | "AI_AGENT"
    | "MCP_SERVER"
    | "RAG_PIPELINE"
    | "EDR"
    | "XDR"
    | "SIEM"
    | "SOAR"
    | "NDR"
    | "ISPM"
    | "EMAIL_SECURITY"
    | "LLM_FIREWALL"
    | "AI_GATEWAY"
    | "VULNERABILITY_SCANNER";
  asset_tags?: string[];
  asset_type?: string;
  /** @format date-time */
  asset_updated_at: string;
  asset_url?: string;
  listened?: boolean;
  security_platform_collectors?: string[];
  security_platform_injectors?: string[];
  security_platform_logo_dark?: string;
  security_platform_logo_light?: string;
  security_platform_traces?: InjectExpectationTrace[];
  security_platform_type:
    | "EDR"
    | "XDR"
    | "SIEM"
    | "SOAR"
    | "NDR"
    | "ISPM"
    | "EMAIL_SECURITY"
    | "LLM_FIREWALL"
    | "AI_GATEWAY"
    | "VULNERABILITY_SCANNER";
}

export interface SecurityPlatformInput {
  asset_description?: string;
  asset_external_reference?: string;
  /** @minLength 1 */
  asset_name: string;
  asset_tags?: string[];
  security_platform_logo_dark?: string | null;
  security_platform_logo_light?: string | null;
  security_platform_type:
    | "EDR"
    | "XDR"
    | "SIEM"
    | "SOAR"
    | "NDR"
    | "ISPM"
    | "EMAIL_SECURITY"
    | "LLM_FIREWALL"
    | "AI_GATEWAY"
    | "VULNERABILITY_SCANNER";
}

export interface SecurityPlatformSimpleOutput {
  /**
   * Security platform id
   * @minLength 1
   */
  asset_id: string;
  /**
   * Security platform name
   * @minLength 1
   */
  asset_name: string;
  /** Security platform type */
  security_platform_type:
    | "EDR"
    | "XDR"
    | "SIEM"
    | "SOAR"
    | "NDR"
    | "ISPM"
    | "EMAIL_SECURITY"
    | "LLM_FIREWALL"
    | "AI_GATEWAY"
    | "VULNERABILITY_SCANNER";
}

/** Connected security platform of a launched simulation, shown as its current effective frozen photo (end snapshot once the run is over, launch snapshot while running) plus its computed change status. */
export interface SecurityPlatformSnapshotOutput {
  /** Frozen security-platform id (a new id signals a reinstall). */
  security_platform_snapshot_id?: string;
  /** Frozen security-platform name. */
  security_platform_snapshot_name?: string;
  /** Computed change status of this platform vs the frozen snapshots. */
  security_platform_snapshot_status?:
    | "RESOLVED"
    | "MODIFIED_DURING_EXECUTION"
    | "DELETED_DURING_EXECUTION"
    | "MODIFIED_AFTER_EXECUTION"
    | "DELETED_AFTER_EXECUTION";
  /** Security-platform type (e.g. EDR / SIEM). */
  security_platform_snapshot_type?: string;
  /**
   * Frozen last-modified date (a later value signals a reconfiguration).
   * @format date-time
   */
  security_platform_snapshot_updated_at?: string;
}

export interface SecurityPlatformUpsertInput {
  asset_description?: string;
  asset_external_reference?: string;
  /** @minLength 1 */
  asset_name: string;
  asset_tags?: string[];
  security_platform_logo_dark?: string;
  security_platform_logo_light?: string;
  security_platform_type:
    | "EDR"
    | "XDR"
    | "SIEM"
    | "SOAR"
    | "NDR"
    | "ISPM"
    | "EMAIL_SECURITY"
    | "LLM_FIREWALL"
    | "AI_GATEWAY"
    | "VULNERABILITY_SCANNER";
}

export interface Series {
  filter?: FilterGroup;
  name?: string;
}

export interface SessionOutput {
  /**
   * Session creation time
   * @format date-time
   */
  session_created_at?: string;
  /**
   * Time at which the session expires if it stays idle
   * @format date-time
   */
  session_expires_at?: string;
  /** Identifier of the session */
  session_id?: string;
  /**
   * Last time the session was used
   * @format date-time
   */
  session_last_access_at?: string;
  /** Identifier of the user owning the session */
  session_user_id?: string;
}

export interface SettingsChatbotAiCguUpdateInput {
  /**
   * Chatbot AI CGU acceptance status: pending, enabled, or disabled
   * @minLength 1
   * @pattern pending|enabled|disabled
   */
  status: string;
}

export interface SettingsEnterpriseEditionUpdateInput {
  /** cert of enterprise edition */
  platform_enterprise_license?: string;
}

export interface SettingsPlatformWhitemarkUpdateInput {
  /**
   * The whitemark of the platform
   * @minLength 1
   */
  platform_whitemark: string;
}

export interface SettingsSessionsUpdateInput {
  /**
   * Maximum number of concurrent sessions per user (0 = unlimited)
   * @format int32
   * @min 0
   */
  platform_session_max_concurrent: number;
}

export interface SimulationChallengesReader {
  exercise_challenges?: ChallengeInformation[];
  exercise_id?: string;
  exercise_information?: PublicExercise;
}

export interface SimulationDetails {
  /** @format int64 */
  exercise_all_users_number?: number;
  exercise_autonomous?: boolean;
  exercise_category?: string;
  /** @format int64 */
  exercise_communications_number?: number;
  /** @format date-time */
  exercise_created_at?: string;
  exercise_custom_dashboard?: string;
  exercise_default_kill_chain?: string;
  exercise_description?: string;
  /** @format date-time */
  exercise_end_date?: string;
  /** @minLength 1 */
  exercise_id: string;
  exercise_kill_chain_phases?: KillChainPhase[];
  exercise_lessons_anonymized?: boolean;
  /** @format int64 */
  exercise_lessons_answers_number?: number;
  exercise_lessons_enabled?: boolean;
  /** @format int64 */
  exercise_logs_number?: number;
  /** @minLength 1 */
  exercise_mail_from: string;
  exercise_mail_from_name?: string;
  exercise_mails_reply_to?: string[];
  exercise_main_focus?: string;
  exercise_message_footer?: string;
  exercise_message_header?: string;
  /** @minLength 1 */
  exercise_name: string;
  /** @uniqueItems true */
  exercise_observers?: string[];
  /** @uniqueItems true */
  exercise_planners?: string[];
  exercise_platforms?: string[];
  exercise_scenario?: string;
  /** @format double */
  exercise_score?: number;
  exercise_severity?: "low" | "medium" | "high" | "critical";
  /** @format date-time */
  exercise_start_date?: string;
  exercise_status: "SCHEDULED" | "CANCELED" | "RUNNING" | "PAUSED" | "FINISHED";
  exercise_subtitle?: string;
  /** @uniqueItems true */
  exercise_tags?: string[];
  /** @uniqueItems true */
  exercise_teams_users?: ExerciseTeamUser[];
  /** @format date-time */
  exercise_updated_at?: string;
  /** @uniqueItems true */
  exercise_users?: string[];
  /** @format int64 */
  exercise_users_number?: number;
  exercise_workflow_id?: string;
}

export interface SimulationsResultsLatest {
  global_scores_by_expectation_type: Record<
    string,
    GlobalScoreBySimulationEndDate[]
  >;
}

export interface SortField {
  direction?: string;
  nullHandling?: "NATIVE" | "NULLS_FIRST" | "NULLS_LAST";
  property?: string;
}

export interface SortObject {
  ascending?: boolean;
  direction?: string;
  ignoreCase?: boolean;
  nullHandling?: string;
  property?: string;
}

export interface StatusPayload {
  dns_resolution_hostname?: string;
  executable_file?: StatusPayloadDocument;
  file_drop_file?: StatusPayloadDocument;
  network_traffic_ip_dst: string;
  network_traffic_ip_src: string;
  /** @format int32 */
  network_traffic_port_dst: number;
  /** @format int32 */
  network_traffic_port_src: number;
  network_traffic_protocol: string;
  payload_arguments?: PayloadArgument[];
  payload_cleanup_executor?: string;
  payload_command_blocks?: PayloadCommandBlock[];
  payload_description?: string;
  payload_external_id?: string;
  payload_name?: string;
  payload_prerequisites?: PayloadPrerequisite[];
  payload_type?: string;
}

export interface StatusPayloadDocument {
  /** @minLength 1 */
  document_id: string;
  /** @minLength 1 */
  document_name: string;
}

export interface StatusPayloadOutput {
  dns_resolution_hostname?: string;
  executable_arch?: "x86_64" | "arm64" | "ALL_ARCHITECTURES";
  executable_file?: StatusPayloadDocument;
  file_drop_file?: StatusPayloadDocument;
  payload_arguments?: PayloadArgument[];
  payload_attack_patterns?: AttackPatternSimple[];
  payload_cleanup_executor?: string;
  payload_collector_type?: string;
  payload_command_blocks?: PayloadCommandBlock[];
  payload_description?: string;
  payload_external_id?: string;
  payload_name?: string;
  payload_obfuscator?: string;
  /** @uniqueItems true */
  payload_output_parsers?: OutputParserSimple[];
  payload_platforms?: (
    | "Linux"
    | "Windows"
    | "MacOS"
    | "Android"
    | "iOS"
    | "Container"
    | "Service"
    | "Generic"
    | "Internal"
    | "Unknown"
  )[];
  payload_prerequisites?: PayloadPrerequisite[];
  /** @uniqueItems true */
  payload_tags?: string[];
  payload_type?: string;
}

export interface Step {
  listened?: boolean;
  /** Action executed by the step */
  step_action_class: "INJECT_EXECUTION";
  /** Condition evaluated to determine whether the step is executed */
  step_condition_executed?: string;
  step_condition_key_types?: (
    | "account_with_password_not_required"
    | "action_output"
    | "admin_username"
    | "asreproastable_account"
    | "asset_group_id"
    | "asset_id"
    | "computer_name"
    | "cve"
    | "delegation_account"
    | "document"
    | "domain"
    | "email"
    | "file_name"
    | "file_path"
    | "group_name"
    | "hash"
    | "host"
    | "ipv4"
    | "ipv6"
    | "ip_subnet"
    | "kerberoastable_account"
    | "key"
    | "number"
    | "password"
    | "permissions"
    | "port"
    | "service"
    | "severity"
    | "share_name"
    | "sid"
    | "targeted-asset"
    | "text"
    | "username"
    | "value"
    | "vulnerability_name"
    | "vulnerability_status"
  )[];
  step_conditions?: Condition[];
  /**
   * Timestamp when the step was created
   * @format date-time
   */
  step_created_at?: string;
  /** Configuration step data stored as JSON */
  step_data?: string;
  /** ID for the step */
  step_id?: string;
  /** Inputs provided to the step in JSON format */
  step_input?: string;
  /**
   * Maximum number of times this step can be executed
   * @format int32
   * @min 0
   */
  step_limit_execution?: number;
  /** Output produced by the step in JSON format */
  step_output?: string;
  /** Output parser configuration in JSON format */
  step_output_parser?: string;
  /** Current status of the step */
  step_status: "TEMPLATE" | "READY" | "RUN" | "END";
  /**
   * Timestamp when the step was last updated
   * @format date-time
   */
  step_updated_at?: string;
}

export interface StepInput {
  step_action: "INJECT_EXECUTION";
  step_condition_ids?: string[];
  step_conditions?: ConditionCreateInput[];
  step_data_step?: InjectInput;
  /** @minLength 1 */
  step_workflow_id: string;
}

export interface StepOutput {
  step_condition_ids?: string[];
  step_condition_key_types?: (
    | "account_with_password_not_required"
    | "action_output"
    | "admin_username"
    | "asreproastable_account"
    | "asset_group_id"
    | "asset_id"
    | "computer_name"
    | "cve"
    | "delegation_account"
    | "document"
    | "domain"
    | "email"
    | "file_name"
    | "file_path"
    | "group_name"
    | "hash"
    | "host"
    | "ipv4"
    | "ipv6"
    | "ip_subnet"
    | "kerberoastable_account"
    | "key"
    | "number"
    | "password"
    | "permissions"
    | "port"
    | "service"
    | "severity"
    | "share_name"
    | "sid"
    | "targeted-asset"
    | "text"
    | "username"
    | "value"
    | "vulnerability_name"
    | "vulnerability_status"
  )[];
  /** @format date-time */
  step_created_at?: string;
  step_data?: JsonNode;
  step_id?: string;
  step_mapper_conditions?: MapperConditionOutput[];
  step_output_types?: string[];
  step_status?: "TEMPLATE" | "READY" | "RUN" | "END";
  /** @format date-time */
  step_updated_at?: string;
}

export type StreamingResponseBody = any;

export type StructuralHistogramWidget = UtilRequiredKeys<
  WidgetConfiguration,
  "widget_configuration_type" | "time_range" | "date_attribute"
> & {
  display_legend?: boolean;
  /** @minLength 1 */
  field: string;
  /**
   * @format int32
   * @min 1
   */
  limit?: number;
  mode: string;
  series: Series[];
  stacked?: boolean;
};

/** A marketplace connector suggested to close a capability gap */
export interface SuggestedConnector {
  connector_id?: string;
  logo_url?: string;
  short_description?: string;
  slug?: string;
  source_code?: string;
  subscription_link?: string;
  title?: string;
}

export interface Tag {
  listened?: boolean;
  /** Color of the tag */
  tag_color?: string;
  /**
   * Unique identifier of the tag
   * @minLength 1
   */
  tag_id: string;
  /**
   * Name of the tag
   * @minLength 1
   */
  tag_name: string;
}

export interface TagCreateInput {
  /**
   * Color of the tag
   * @minLength 1
   */
  tag_color: string;
  /**
   * Name of the tag
   * @minLength 1
   */
  tag_name: string;
}

export interface TagRuleInput {
  /** Asset groups of the tag rule */
  asset_groups?: string[];
  /**
   * Name of the tag
   * @minLength 1
   */
  tag_name: string;
}

export interface TagRuleOutput {
  /** Asset groups of the tag rule */
  asset_groups?: Record<string, string>;
  /**
   * Name of the tag associated with the tag rule
   * @minLength 1
   */
  tag_name: string;
  /**
   * ID of the tag rule
   * @minLength 1
   */
  tag_rule_id: string;
  /** The tag rule is protected and cannot change the associated tag or be deleted. */
  tag_rule_protected: boolean;
}

export interface TagUpdateInput {
  /**
   * Color of the tag
   * @minLength 1
   */
  tag_color: string;
  /**
   * Name of the tag
   * @minLength 1
   */
  tag_name: string;
}

export interface TargetSimple {
  target_category?: string;
  /** @minLength 1 */
  target_id: string;
  target_name?: string;
  target_subtype?: string;
  target_type?:
    | "AGENT"
    | "AGENTS"
    | "ASSETS"
    | "ASSETS_GROUPS"
    | "AI_TARGETS"
    | "PLAYERS"
    | "TEAMS"
    | "ENDPOINTS"
    | "MANUAL";
}

export interface Team {
  listened?: boolean;
  /** List of communications of this team */
  team_communications?: Communication[];
  /** True if the team is contextual (exists only in the scenario/simulation it is linked to) */
  team_contextual?: boolean;
  /**
   * Creation date of the team
   * @format date-time
   */
  team_created_at: string;
  /** Description of the team */
  team_description?: string;
  team_exercise_injects?: string[];
  /**
   * Number of injects of all simulations of the team
   * @format int64
   */
  team_exercise_injects_number?: number;
  team_exercises?: string[];
  team_exercises_users?: string[];
  /**
   * ID of the team
   * @minLength 1
   */
  team_id: string;
  team_inject_expectations?: string[];
  /**
   * Number of expectations linked to this team
   * @format int64
   */
  team_injects_expectations_number?: number;
  /**
   * Total expected score of expectations linked to this team
   * @format double
   */
  team_injects_expectations_total_expected_score: number;
  /** Total expected score of expectations by simulation linked to this team */
  team_injects_expectations_total_expected_score_by_exercise: Record<
    string,
    number
  >;
  /**
   * Total score of expectations linked to this team
   * @format double
   */
  team_injects_expectations_total_score: number;
  /** Total score of expectations by simulation linked to this team */
  team_injects_expectations_total_score_by_exercise: Record<string, number>;
  /**
   * Name of the team
   * @minLength 1
   */
  team_name: string;
  /** Organization of the team */
  team_organization?: string;
  team_scenario_injects?: string[];
  /**
   * Number of injects of all scenarios of the team
   * @format int64
   */
  team_scenario_injects_number?: number;
  team_scenarios?: string[];
  /** @uniqueItems true */
  team_tags?: string[];
  /**
   * Update date of the team
   * @format date-time
   */
  team_updated_at: string;
  team_users?: string[];
  /**
   * Number of users of the team
   * @format int64
   */
  team_users_number?: number;
}

export interface TeamBulkProcessingInput {
  search_pagination_input?: SearchPaginationInput;
  team_ids_to_ignore?: string[];
  team_ids_to_process?: string[];
}

export interface TeamCreateInput {
  /** True if the team is contextual (exists only in the scenario/simulation it is linked to) */
  team_contextual?: boolean;
  /** Description of the team */
  team_description?: string;
  /** Id of the simulations linked to the team */
  team_exercises?: string[];
  /**
   * Name of the team
   * @minLength 1
   */
  team_name: string;
  /** ID of the organization of the team */
  team_organization?: string;
  /** Id of the scenarios linked to the team */
  team_scenarios?: string[];
  /** IDs of the tags of the team */
  team_tags?: string[];
}

export interface TeamOutput {
  /** True if the team is contextual (exists only in the scenario/simulation it is linked to) */
  team_contextual?: boolean;
  /** Description of the team */
  team_description?: string;
  /**
   * Simulation ids linked to this team
   * @uniqueItems true
   */
  team_exercises: string[];
  /**
   * ID of the team
   * @minLength 1
   */
  team_id: string;
  /**
   * Name of the team
   * @minLength 1
   */
  team_name: string;
  /** Organization of the team */
  team_organization?: string;
  /**
   * Scenario ids linked to this team
   * @uniqueItems true
   */
  team_scenarios: string[];
  /**
   * List of tags of the team
   * @uniqueItems true
   */
  team_tags?: string[];
  /**
   * Update date of the team
   * @format date-time
   */
  team_updated_at: string;
  /**
   * User ids of the team
   * @uniqueItems true
   */
  team_users?: string[];
  /**
   * Number of users of the team
   * @format int64
   */
  team_users_number?: number;
}

export interface TeamTarget {
  target_category?: string;
  target_detection_status?:
    | "FAILED"
    | "PENDING"
    | "PARTIAL"
    | "UNKNOWN"
    | "SUCCESS";
  target_execution_status?:
    | "FAILED"
    | "PENDING"
    | "PARTIAL"
    | "UNKNOWN"
    | "SUCCESS";
  target_human_response_status?:
    | "FAILED"
    | "PENDING"
    | "PARTIAL"
    | "UNKNOWN"
    | "SUCCESS";
  /** @minLength 1 */
  target_id: string;
  target_name?: string;
  target_prevention_status?:
    | "FAILED"
    | "PENDING"
    | "PARTIAL"
    | "UNKNOWN"
    | "SUCCESS";
  target_subtype?: string;
  /** @uniqueItems true */
  target_tags?: string[];
  target_type?: string;
  target_vulnerability_status?:
    | "FAILED"
    | "PENDING"
    | "PARTIAL"
    | "UNKNOWN"
    | "SUCCESS";
}

export interface TeamUpdateInput {
  /** Description of the team */
  team_description?: string;
  /**
   * Name of the team
   * @minLength 1
   */
  team_name: string;
  /** ID of the organization of the team */
  team_organization?: string;
  /** IDs of the tags of the team */
  team_tags?: string[];
}

export interface TenantGroupCreateInput {
  group_default_user_assign?: boolean;
  group_description?: string;
  /** @minLength 1 */
  group_name: string;
}

export interface TenantInput {
  tenant_description?: string;
  /** @minLength 1 */
  tenant_name: string;
}

export interface TenantOutput {
  /** @format date-time */
  tenant_deleted_at?: string;
  tenant_description?: string;
  /** @minLength 1 */
  tenant_id: string;
  /** @minLength 1 */
  tenant_name: string;
}

export interface TenantSettingsOutput {
  platform_dark_theme?: ThemeInput;
  platform_home_dashboard?: string;
  /** @minLength 1 */
  platform_lang: string;
  platform_light_theme?: ThemeInput;
  /** @minLength 1 */
  platform_name: string;
  platform_scenario_dashboard?: string;
  platform_simulation_dashboard?: string;
  /** @minLength 1 */
  platform_theme: string;
  xtm_opencti_enable?: boolean;
  xtm_opencti_url?: string;
}

export interface TenantSettingsUpdateInput {
  platform_home_dashboard?: string;
  /** @minLength 1 */
  platform_lang: string;
  /** @minLength 1 */
  platform_name: string;
  platform_scenario_dashboard?: string;
  platform_simulation_dashboard?: string;
  /** @minLength 1 */
  platform_theme: string;
}

export interface ThemeInput {
  /** Accent color of the theme */
  accent_color?: string;
  /** Background color of the theme */
  background_color?: string;
  /** Solid color of the login page aside */
  login_aside_color?: string;
  /** Gradient end color of the login page aside */
  login_aside_gradient_end?: string;
  /** Gradient start color of the login page aside */
  login_aside_gradient_start?: string;
  /** Url of the login page aside background image */
  login_aside_image?: string;
  /** Url of the login logo */
  logo_login_url?: string;
  /** Url of the logo */
  logo_url?: string;
  /** 'true' if the logo needs to be collapsed */
  logo_url_collapsed?: string;
  /** Navigation color of the theme */
  navigation_color?: string;
  /** Paper color of the theme */
  paper_color?: string;
  /** Primary color of the theme */
  primary_color?: string;
  /** Secondary color of the theme */
  secondary_color?: string;
}

export interface ThreatArsenalAction {
  /**
   * Attack Patterns IDs
   * @minItems 1
   * @uniqueItems true
   */
  action_attack_patterns_ids: string[];
  /** Author id (user, team or organization) */
  action_author?: string;
  /** Author display name */
  action_author_name?: string;
  /** Author type: user, team or organization */
  action_author_type?: string;
  /**
   * Domain IDs
   * @minItems 1
   * @uniqueItems true
   */
  action_domains_ids: string[];
  /** Injector type */
  action_injector_type?: string;
  /** Labels */
  action_labels?: Record<string, string>;
  /** Payload attached */
  action_payload?: PayloadSimple;
  /** Platforms */
  action_platforms?: (
    | "Linux"
    | "Windows"
    | "MacOS"
    | "Android"
    | "iOS"
    | "Container"
    | "Service"
    | "Generic"
    | "Internal"
    | "Unknown"
  )[];
  /**
   * Tags Ids
   * @uniqueItems true
   */
  action_tags_ids?: string[];
  /** Injector contract external Id */
  injector_contract_external_id?: string;
  injector_contract_has_full_details?: boolean;
  /**
   * Injector contract Id
   * @minLength 1
   */
  injector_contract_id: string;
  /**
   * Timestamp when the injector contract was last updated
   * @format date-time
   */
  injector_contract_updated_at: string;
}

export interface ThreatArsenalActionCreateInput {
  action_arguments?: PayloadArgument[];
  action_attack_patterns?: string[];
  action_cleanup_command?: string | null;
  action_cleanup_executor?: string | null;
  action_description?: string;
  /** List of detection remediation gaps for collectors */
  action_detection_remediations?: DetectionRemediationInput[];
  /** Set list of domains */
  action_domains: string[];
  action_execution_arch: "x86_64" | "arm64" | "ALL_ARCHITECTURES";
  action_expectations: (
    | "ARTICLE"
    | "CHALLENGE"
    | "MANUAL"
    | "PREVENTION"
    | "DETECTION"
    | "VULNERABILITY"
  )[];
  /** Optional map of technical expectation type to the security platform types expected to fulfil it (e.g. {"DETECTION": ["EDR","XDR"], "PREVENTION": ["EDR"]}). Empty or absent for a given type means any security platform. */
  action_expected_security_platforms?: Record<
    string,
    (
      | "EDR"
      | "XDR"
      | "SIEM"
      | "SOAR"
      | "NDR"
      | "ISPM"
      | "EMAIL_SECURITY"
      | "LLM_FIREWALL"
      | "AI_GATEWAY"
      | "VULNERABILITY_SCANNER"
    )[]
  >;
  /** @minLength 1 */
  action_name: string;
  /**
   * Set of output parsers
   * @uniqueItems true
   */
  action_output_parsers?: OutputParserInput[];
  /** @minItems 1 */
  action_platforms: (
    | "Linux"
    | "Windows"
    | "MacOS"
    | "Android"
    | "iOS"
    | "Container"
    | "Service"
    | "Generic"
    | "Internal"
    | "Unknown"
  )[];
  action_prerequisites?: PayloadPrerequisite[];
  action_source: "COMMUNITY" | "FILIGRAN" | "MANUAL";
  action_status: "UNVERIFIED" | "VERIFIED" | "DEPRECATED";
  action_tags?: string[];
  /** @minLength 1 */
  action_type: string;
  command_content?: string | null;
  command_executor?: string | null;
  dns_resolution_hostname?: string;
  executable_file?: string;
  file_drop_file?: string;
}

export interface ThreatArsenalActionFullOutput {
  /** Action input arguments definition */
  action_arguments?: PayloadArgument[];
  /** MITRE ATT&CK patterns associated with the action */
  action_attack_patterns?: string[];
  /** Cleanup command executed after action run */
  action_cleanup_command?: string;
  /** Executor used for cleanup operations */
  action_cleanup_executor?: string;
  /** Collector type associated with this action */
  action_collector_type?: string;
  /**
   * Action creation timestamp
   * @format date-time
   */
  action_created_at: string;
  /** Action description */
  action_description?: string;
  /** Detection and remediation mappings for this action */
  action_detection_remediations?: DetectionRemediation[];
  /** Domains related to the action */
  action_domains?: string[];
  /** CPU architecture targeted for action execution */
  action_execution_arch: "x86_64" | "arm64" | "ALL_ARCHITECTURES";
  /** Predefined expectations declared by the contract, each with its name, description and display order (e.g. phishing's ordered human steps). Omitted for payload-based actions, which declare expectations by type only - readers then fall back to action_expectations. */
  action_expectation_details?: ThreatArsenalExpectationDetail[];
  /** Expected output types for action execution */
  action_expectations?: (
    | "ARTICLE"
    | "CHALLENGE"
    | "MANUAL"
    | "PREVENTION"
    | "DETECTION"
    | "VULNERABILITY"
  )[];
  /** Security platform types expected to fulfil each predefined technical expectation (empty or absent = any security platform) */
  action_expected_security_platforms?: Record<
    string,
    (
      | "EDR"
      | "XDR"
      | "SIEM"
      | "SOAR"
      | "NDR"
      | "ISPM"
      | "EMAIL_SECURITY"
      | "LLM_FIREWALL"
      | "AI_GATEWAY"
      | "VULNERABILITY_SCANNER"
    )[]
  >;
  /** External reference identifier */
  action_external_id?: string;
  /**
   * Action unique identifier
   * @minLength 1
   */
  action_id: string;
  /** Action display name */
  action_labels: Record<string, string>;
  /**
   * Parsers used to process action outputs
   * @uniqueItems true
   */
  action_output_parsers?: OutputParser[];
  /** Supported endpoint platforms for this action */
  action_platforms?: (
    | "Linux"
    | "Windows"
    | "MacOS"
    | "Android"
    | "iOS"
    | "Container"
    | "Service"
    | "Generic"
    | "Internal"
    | "Unknown"
  )[];
  /** Prerequisites required before action execution */
  action_prerequisites?: PayloadPrerequisite[];
  /** Output/finding types this action can produce (empty = the action produces no parsed output). Derived from the payload output parsers or, for native injectors without a payload, from the contract content outputs. */
  action_providing?: (
    | "text"
    | "action_output"
    | "number"
    | "port"
    | "portscan"
    | "ipv4"
    | "ipv6"
    | "credentials"
    | "cve"
    | "username"
    | "email"
    | "share"
    | "file"
    | "admin_username"
    | "group"
    | "computer"
    | "password_policy"
    | "delegation"
    | "sid"
    | "vulnerability"
    | "account_with_password_not_required"
    | "asreproastable_account"
    | "kerberoastable_account"
    | "expectation_signature"
  )[];
  /** Action source origin */
  action_source: "COMMUNITY" | "FILIGRAN" | "MANUAL";
  /** Current action lifecycle status */
  action_status: "UNVERIFIED" | "VERIFIED" | "DEPRECATED";
  /** Tags attached to the action */
  action_tags?: string[];
  /** Action implementation type */
  action_type?: string;
  /**
   * Action last update timestamp
   * @format date-time
   */
  action_updated_at: string;
  /** Command content for command actions */
  command_content?: string;
  /** Executor used for command actions */
  command_executor?: string;
  /** Hostname resolved by DNS resolution actions */
  dns_resolution_hostname?: string;
  /** Executable file path for executable actions */
  executable_file?: string;
  /** Dropped file path for file-drop actions */
  file_drop_file?: string;
}

export interface ThreatArsenalActionUpdateInput {
  action_arguments?: PayloadArgument[];
  action_attack_patterns?: string[];
  action_cleanup_command?: string | null;
  action_cleanup_executor?: string | null;
  action_description?: string;
  /** List of detection remediation gaps for collectors */
  action_detection_remediations?: DetectionRemediationInput[];
  /** Update list of domains */
  action_domains: string[];
  action_execution_arch?: "x86_64" | "arm64" | "ALL_ARCHITECTURES";
  action_expectations?: (
    | "ARTICLE"
    | "CHALLENGE"
    | "MANUAL"
    | "PREVENTION"
    | "DETECTION"
    | "VULNERABILITY"
  )[];
  /** Optional map of technical expectation type to the security platform types expected to fulfil it (e.g. {"DETECTION": ["EDR","XDR"], "PREVENTION": ["EDR"]}). Empty or absent for a given type means any security platform. */
  action_expected_security_platforms?: Record<
    string,
    (
      | "EDR"
      | "XDR"
      | "SIEM"
      | "SOAR"
      | "NDR"
      | "ISPM"
      | "EMAIL_SECURITY"
      | "LLM_FIREWALL"
      | "AI_GATEWAY"
      | "VULNERABILITY_SCANNER"
    )[]
  >;
  /** @minLength 1 */
  action_name: string;
  /**
   * Set of output parsers
   * @uniqueItems true
   */
  action_output_parsers?: OutputParserInput[];
  action_platforms?: (
    | "Linux"
    | "Windows"
    | "MacOS"
    | "Android"
    | "iOS"
    | "Container"
    | "Service"
    | "Generic"
    | "Internal"
    | "Unknown"
  )[];
  action_prerequisites?: PayloadPrerequisite[];
  action_tags?: string[];
  command_content?: string | null;
  command_executor?: string | null;
  dns_resolution_hostname?: string;
  executable_file?: string;
  file_drop_file?: string;
}

export interface ThreatArsenalActionWithContentOutput {
  /** CPU architecture targeted for action execution */
  action_arch: "x86_64" | "arm64" | "ALL_ARCHITECTURES";
  /** Action content */
  action_content?: string;
  /**
   * Action injectors names
   * @minLength 1
   */
  action_injector_name: string;
  /** Action implementation injector type */
  action_injector_type?: string;
  /** Action display labels */
  action_labels: Record<string, string>;
  /** Action implementation payload type */
  action_payload_type?: string;
  /** Supported endpoint platforms for this action */
  action_platforms?: (
    | "Linux"
    | "Windows"
    | "MacOS"
    | "Android"
    | "iOS"
    | "Container"
    | "Service"
    | "Generic"
    | "Internal"
    | "Unknown"
  )[];
  /** Injector contract external Id */
  injector_contract_external_id?: string;
  injector_contract_has_full_details?: boolean;
  /**
   * Injector contract Id
   * @minLength 1
   */
  injector_contract_id: string;
  /**
   * Timestamp when the injector contract was last updated
   * @format date-time
   */
  injector_contract_updated_at: string;
}

export interface ThreatArsenalBulkDeleteOutput {
  /**
   * Number of actions that were actually deleted
   * @format int32
   */
  deleted_count?: number;
  /** Ids of the actions that were actually deleted */
  deleted_ids?: string[];
}

export interface ThreatArsenalExpectationDetail {
  /** Contract-declared expectation description (null = none) */
  expectation_description?: string;
  /** Contract-declared expectation name (null = unnamed, use the type label) */
  expectation_name?: string;
  /**
   * Contract-declared display order, ascending (e.g. phishing orders its steps email -> link -> submission); null = unordered
   * @format int32
   */
  expectation_order?: number;
  /** Expectation type */
  expectation_type?:
    | "ARTICLE"
    | "CHALLENGE"
    | "MANUAL"
    | "PREVENTION"
    | "DETECTION"
    | "VULNERABILITY";
}

export interface ThreatArsenalFacetCountsOutput {
  /** Number of contracts per platform under the current filters */
  platforms?: Record<string, number>;
  /** Number of contracts per payload status under the current filters */
  statuses?: Record<string, number>;
}

export interface Token {
  listened?: boolean;
  /** @format date-time */
  token_created_at: string;
  /** @minLength 1 */
  token_id: string;
  token_user?: string;
  /** @minLength 1 */
  token_value: string;
}

export interface UpdateAssetsOnAssetGroupInput {
  asset_group_assets?: string[];
}

export interface UpdateConnectorInstanceRequestedStatus {
  /** The connector instance current status */
  connector_instance_requested_status: "starting" | "stopping";
}

export interface UpdateExerciseInput {
  apply_tag_rule?: boolean;
  exercise_category?: string;
  exercise_custom_dashboard?: string;
  exercise_default_kill_chain?: string;
  exercise_description?: string;
  exercise_is_chaining?: boolean;
  /**
   * @minLength 0
   * @maxLength 100
   * @pattern ^[^\r\n\x00]*$
   */
  exercise_mail_from_name?: string;
  exercise_mails_reply_to?: string[];
  exercise_main_focus?: string;
  exercise_message_footer?: string;
  exercise_message_header?: string;
  /**
   * @minLength 0
   * @maxLength 255
   */
  exercise_name: string;
  exercise_severity?: string;
  exercise_subtitle?: string;
  exercise_tags?: string[];
}

export interface UpdateMePasswordInput {
  /** @minLength 1 */
  user_current_password: string;
  /** @minLength 1 */
  user_plain_password: string;
}

export interface UpdateProfileInput {
  user_country?: string;
  /**
   * @format email
   * @minLength 1
   */
  user_email: string;
  /** @minLength 1 */
  user_firstname: string;
  user_home_dashboard?: string;
  /** @minLength 1 */
  user_lang: string;
  /** @minLength 1 */
  user_lastname: string;
  user_organization?: string;
  /** @minLength 1 */
  user_theme: string;
}

export interface UpdateScenarioInput {
  apply_tag_rule?: boolean;
  scenario_category?: string;
  scenario_custom_dashboard?: string;
  scenario_default_kill_chain?: string;
  scenario_description?: string;
  scenario_external_reference?: string;
  scenario_external_url?: string;
  scenario_is_chaining?: boolean;
  /**
   * @minLength 0
   * @maxLength 100
   * @pattern ^[^\r\n\x00]*$
   */
  scenario_mail_from_name?: string;
  scenario_mails_reply_to?: string[];
  scenario_main_focus?: string;
  scenario_message_footer?: string;
  scenario_message_header?: string;
  /** @minLength 1 */
  scenario_name: string;
  scenario_severity?: "low" | "medium" | "high" | "critical";
  scenario_subtitle?: string;
  scenario_tags?: string[];
}

export interface UpdateUserInfoInput {
  user_phone2?: string;
  user_pgp_key?: string;
  user_phone?: string;
}

export interface UpdateUsersTeamInput {
  /** The list of users the team contains */
  team_users?: string[];
}

export interface User {
  /** Secondary phone number of the user */
  user_phone2?: string;
  listened?: boolean;
  team_exercises_users?: string[];
  unscopedGroups?: Group[];
  /** True if the user is admin */
  user_admin?: boolean;
  /** @uniqueItems true */
  user_capabilities?: (
    | "BYPASS"
    | "ACCESS_ASSESSMENT"
    | "MANAGE_ASSESSMENT"
    | "DELETE_ASSESSMENT"
    | "LAUNCH_ASSESSMENT"
    | "ACCESS_TEAMS_AND_PLAYERS"
    | "MANAGE_TEAMS_AND_PLAYERS"
    | "DELETE_TEAMS_AND_PLAYERS"
    | "ACCESS_ASSETS"
    | "MANAGE_ASSETS"
    | "DELETE_ASSETS"
    | "ACCESS_PAYLOADS"
    | "MANAGE_PAYLOADS"
    | "DELETE_PAYLOADS"
    | "ACCESS_THREAT_ARSENALS"
    | "MANAGE_THREAT_ARSENALS"
    | "DELETE_THREAT_ARSENALS"
    | "ACCESS_CREDENTIALS"
    | "MANAGE_CREDENTIALS"
    | "DELETE_CREDENTIALS"
    | "ACCESS_DASHBOARDS"
    | "MANAGE_DASHBOARDS"
    | "DELETE_DASHBOARDS"
    | "ACCESS_REPORTINGS"
    | "MANAGE_REPORTINGS"
    | "DELETE_REPORTINGS"
    | "ACCESS_FINDINGS"
    | "MANAGE_FINDINGS"
    | "DELETE_FINDINGS"
    | "ACCESS_DOCUMENTS"
    | "MANAGE_DOCUMENTS"
    | "DELETE_DOCUMENTS"
    | "ACCESS_CHANNELS"
    | "MANAGE_CHANNELS"
    | "DELETE_CHANNELS"
    | "ACCESS_PHISHING"
    | "MANAGE_PHISHING"
    | "DELETE_PHISHING"
    | "ACCESS_CHALLENGES"
    | "MANAGE_CHALLENGES"
    | "DELETE_CHALLENGES"
    | "ACCESS_LESSONS_LEARNED"
    | "MANAGE_LESSONS_LEARNED"
    | "DELETE_LESSONS_LEARNED"
    | "ACCESS_SECURITY_PLATFORMS"
    | "MANAGE_SECURITY_PLATFORMS"
    | "DELETE_SECURITY_PLATFORMS"
    | "ACCESS_PLATFORM_SETTINGS"
    | "MANAGE_PLATFORM_SETTINGS"
    | "ACCESS_TENANTS"
    | "MANAGE_TENANTS"
    | "DELETE_TENANTS"
    | "ACCESS_TENANT_SETTINGS"
    | "MANAGE_TENANT_SETTINGS"
    | "DELETE_TENANT_SETTINGS"
    | "ACCESS_PLATFORM_USERS_GROUPS_AND_ROLES"
    | "MANAGE_PLATFORM_USERS_GROUPS_AND_ROLES"
    | "DELETE_PLATFORM_USERS_GROUPS_AND_ROLES"
    | "MANAGE_STIX_BUNDLE"
    | "AGENT_RUNTIME_ACCESS"
  )[];
  /** City of the user */
  user_city?: string;
  user_communications?: string[];
  /** Country of the user */
  user_country?: string;
  /**
   * Creation date of the user
   * @format date-time
   */
  user_created_at: string;
  /**
   * Email of the user
   * @minLength 1
   */
  user_email: string;
  /** First name of the user */
  user_firstname?: string;
  user_grants?: Record<string, string>;
  /** Gravatar of the user */
  user_gravatar?: string;
  user_groups?: string[];
  /** Preferred home dashboard of the user; overrides the tenant home dashboard setting */
  user_home_dashboard?: string;
  /**
   * User ID
   * @minLength 1
   */
  user_id: string;
  /** True if the user is admin or has bypass capa */
  user_is_admin_or_bypass?: boolean;
  /** True if the user is external */
  user_is_external?: boolean;
  /** True if the user is manager */
  user_is_manager?: boolean;
  /** True if the user is observer */
  user_is_observer?: boolean;
  /** True if the user is only a player */
  user_is_only_player?: boolean;
  /** True if the user is planner */
  user_is_planner?: boolean;
  /** True if the user is player */
  user_is_player?: boolean;
  /** Language of the user */
  user_lang?: string;
  /** Last name of the user */
  user_lastname?: string;
  /** Organization ID of the user */
  user_organization?: string;
  /** PGP key of the user */
  user_pgp_key?: string;
  /** Phone number of the user */
  user_phone?: string;
  /**
   * Status of the user
   * @format int32
   */
  user_status: number;
  /** @uniqueItems true */
  user_tags?: string[];
  user_teams?: string[];
  /** Theme of the user */
  user_theme?: string;
  /**
   * Update date of the user
   * @format date-time
   */
  user_updated_at: string;
}

export interface UserInput {
  /** @pattern ^$|^\+[\d\s\-.()]+$ */
  user_phone2?: string;
  user_admin?: boolean;
  /**
   * @format email
   * @minLength 1
   */
  user_email: string;
  user_firstname?: string;
  user_lastname?: string;
  user_organization?: string;
  user_pgp_key?: string;
  /** @pattern ^$|^\+[\d\s\-.()]+$ */
  user_phone?: string;
  user_plain_password?: string;
  user_tags?: string[];
  user_tenants?: string[];
}

export interface UserOutput {
  user_phone2?: string;
  user_admin?: boolean;
  /**
   * @format email
   * @minLength 1
   */
  user_email: string;
  user_firstname?: string;
  /** @minLength 1 */
  user_id: string;
  user_lastname?: string;
  user_organization_id?: string;
  user_organization_name?: string;
  user_pgp_key?: string;
  user_phone?: string;
  /** @uniqueItems true */
  user_tags?: string[];
  user_tenants?: UserTenantOutput[];
}

export interface UserTenantOutput {
  tenant_id?: string;
  tenant_name?: string;
}

export interface ValidationContent {
  /** A list of errors */
  errors?: string[];
}

export interface ValidationError {
  /** Map of errors by input */
  children?: Record<string, ValidationContent>;
}

export interface ValidationErrorBag {
  /**
   * Return code
   * @format int32
   */
  code?: number;
  /** Errors raised */
  errors?: ValidationError;
  /** Return message */
  message?: string;
}

export interface Variable {
  listened?: boolean;
  /** @format date-time */
  variable_created_at: string;
  variable_description?: string;
  variable_exercise?: string;
  /** @minLength 1 */
  variable_id: string;
  /**
   * @minLength 1
   * @pattern ^[a-z_]+$
   */
  variable_key: string;
  variable_scenario?: string;
  variable_type: "String" | "Object";
  /** @format date-time */
  variable_updated_at: string;
  variable_value?: string;
}

export interface VariableInput {
  variable_description?: string;
  /**
   * @minLength 1
   * @pattern ^[a-z_]+$
   */
  variable_key: string;
  variable_value?: string;
}

export interface VulnerabilityBulkInsertInput {
  initial_dataset_completed?: boolean;
  /** @format int32 */
  last_index?: number;
  /** @format date-time */
  last_modified_date_fetched?: string;
  source_identifier: string;
  vulnerabilities: VulnerabilityCreateInput[];
}

/** Payload to create a Vulnerabilty */
export interface VulnerabilityCreateInput {
  /**
   * CVSS score
   * @min 0
   * @max 10
   * @example "7.5"
   */
  vulnerability_cvss_v31: number;
  /**
   * Date when action is due by CISA
   * @format date-time
   */
  vulnerability_cisa_action_due?: string;
  /**
   * Date when CISA added the vulnerability to the exploited list
   * @format date-time
   */
  vulnerability_cisa_exploit_add?: string;
  /** Action required by CISA */
  vulnerability_cisa_required_action?: string;
  /** Vulnerability name used by CISA */
  vulnerability_cisa_vulnerability_name?: string;
  /** List of linked CWEs */
  vulnerability_cwes?: CweInput[];
  /** Description of the vulnerability */
  vulnerability_description?: string;
  /**
   * External Unique Vulnerabilty Identifier
   * @minLength 1
   * @example "CVE-2024-0001"
   */
  vulnerability_external_id: string;
  /**
   * Publication date of the vulnerability
   * @format date-time
   */
  vulnerability_published?: string;
  /** List of reference URLs */
  vulnerability_reference_urls?: string[];
  /** Suggested remediation */
  vulnerability_remediation?: string;
  /**
   * Identifier of the vulnerability source
   * @example "MITRE"
   */
  vulnerability_source_identifier?: string;
  /**
   * Vulnerability status
   * @example "ANALYZED"
   */
  vulnerability_vuln_status?: "ANALYZED" | "DEFERRED" | "MODIFIED";
}

/** Full vulnerability output including references and CWEs */
export interface VulnerabilityOutput {
  /**
   * CVSS score
   * @example "7.8"
   */
  vulnerability_cvss_v31: number;
  /**
   * CISA required action due date
   * @format date-time
   */
  vulnerability_cisa_action_due?: string;
  /**
   * CISA exploit addition date
   * @format date-time
   */
  vulnerability_cisa_exploit_add?: string;
  /** Action required by CISA */
  vulnerability_cisa_required_action?: string;
  /** Name used by CISA for the vulnerability */
  vulnerability_cisa_vulnerability_name?: string;
  /** List of CWE outputs */
  vulnerability_cwes?: CweOutput[];
  /** Detailed vulnerability description */
  vulnerability_description?: string;
  /**
   * External Vulnerability identifier
   * @minLength 1
   * @example "CVE-2024-0001"
   */
  vulnerability_external_id: string;
  /**
   * Id
   * @minLength 1
   */
  vulnerability_id: string;
  /**
   * Vulnerability published date
   * @format date-time
   */
  vulnerability_published?: string;
  /** External references */
  vulnerability_reference_urls?: string[];
  /** Remediation suggestions */
  vulnerability_remediation?: string;
  /** Source identifier */
  vulnerability_source_identifier?: string;
  /** Status of the vulnerability */
  vulnerability_vuln_status?: "ANALYZED" | "DEFERRED" | "MODIFIED";
}

/** Simplified Vulnerability representation */
export interface VulnerabilitySimple {
  /**
   * CVSS score
   * @example "7.8"
   */
  vulnerability_cvss_v31: number;
  /**
   * External Vulnerability identifier
   * @minLength 1
   * @example "CVE-2024-0001"
   */
  vulnerability_external_id: string;
  /**
   * Id
   * @minLength 1
   */
  vulnerability_id: string;
  /**
   * Vulnerability published date
   * @format date-time
   */
  vulnerability_published?: string;
}

/** Payload to update a vulnerability */
export interface VulnerabilityUpdateInput {
  /**
   * Date when action is due by CISA
   * @format date-time
   */
  vulnerability_cisa_action_due?: string;
  /**
   * Date when CISA added the vulnerability to the exploited list
   * @format date-time
   */
  vulnerability_cisa_exploit_add?: string;
  /** Action required by CISA */
  vulnerability_cisa_required_action?: string;
  /** Vulnerability name used by CISA */
  vulnerability_cisa_vulnerability_name?: string;
  /** List of linked CWEs */
  vulnerability_cwes?: CweInput[];
  /** Description of the vulnerability */
  vulnerability_description?: string;
  /**
   * Publication date of the vulnerability
   * @format date-time
   */
  vulnerability_published?: string;
  /** List of reference URLs */
  vulnerability_reference_urls?: string[];
  /** Suggested remediation */
  vulnerability_remediation?: string;
  /**
   * Identifier of the vulnerability source
   * @example "MITRE"
   */
  vulnerability_source_identifier?: string;
  /**
   * Vulnerability status
   * @example "ANALYZED"
   */
  vulnerability_vuln_status?: "ANALYZED" | "DEFERRED" | "MODIFIED";
}

export interface Widget {
  listened?: boolean;
  widget_config:
    | AverageConfiguration
    | DateHistogramWidget
    | FlatConfiguration
    | ListConfiguration
    | StructuralHistogramWidget;
  /** @format date-time */
  widget_created_at: string;
  /** @minLength 1 */
  widget_id: string;
  widget_layout: WidgetLayout;
  widget_type:
    | "vertical-barchart"
    | "horizontal-barchart"
    | "security-coverage"
    | "line"
    | "donut"
    | "list"
    | "attack-path"
    | "number"
    | "average"
    | "exposure-score"
    | "posture-radar"
    | "command-center"
    | "resilience-gauge";
  /** @format date-time */
  widget_updated_at: string;
}

export interface WidgetConfiguration {
  /** @minLength 1 */
  date_attribute: string;
  end?: string | null;
  start?: string | null;
  time_range:
    | "DEFAULT"
    | "ALL_TIME"
    | "CUSTOM"
    | "LAST_DAY"
    | "LAST_WEEK"
    | "LAST_MONTH"
    | "LAST_QUARTER"
    | "LAST_SEMESTER"
    | "LAST_YEAR";
  title?: string;
  widget_configuration_type:
    | "flat"
    | "average"
    | "list"
    | "temporal-histogram"
    | "structural-histogram";
}

export interface WidgetInput {
  widget_config:
    | AverageConfiguration
    | DateHistogramWidget
    | FlatConfiguration
    | ListConfiguration
    | StructuralHistogramWidget;
  widget_layout: WidgetLayout;
  widget_type:
    | "vertical-barchart"
    | "horizontal-barchart"
    | "security-coverage"
    | "line"
    | "donut"
    | "list"
    | "attack-path"
    | "number"
    | "average"
    | "exposure-score"
    | "posture-radar"
    | "command-center"
    | "resilience-gauge";
}

export interface WidgetLayout {
  /** @format int32 */
  widget_layout_h: number;
  /** @format int32 */
  widget_layout_w: number;
  /** @format int32 */
  widget_layout_x: number;
  /** @format int32 */
  widget_layout_y: number;
}

export interface WidgetToEntitiesInput {
  /** Key-value pairs for filtering entities, where the key is the field name and the value is the filter criterion */
  filter_values_map?: Record<string, string[]>;
  /** Pagination for the widget */
  pagination?: Pagination;
  /** Additional parameters for the widget */
  parameters?: Record<string, string>;
  /**
   * The index of the series to filter by, if applicable, otherwise 0
   * @format int32
   */
  series_index?: number;
  /** The indexes of every series that produced the clicked number, ORed together. Takes precedence over series_index; use it whenever a widget displays a total spanning several series, so the drilled list resolves to exactly the documents that were counted */
  series_indexes?: number[];
}

export interface WidgetToEntitiesOutput {
  /** List of entities */
  es_entities?: EsEntities;
  /** List configuration generated based on the input widget id and filter value */
  list_configuration?: ListConfiguration;
}

export interface Workflow {
  edited?: boolean;
  listened?: boolean;
  /**
   * Creation date
   * @format date-time
   */
  workflow_created_at?: string;
  /** ID of the workflow */
  workflow_id?: string;
  /** Workflow template is edited */
  workflow_is_edited?: boolean;
  /** Keep the workflow alive (parked in RUN) while empty/idle - autonomous runs */
  workflow_keep_alive?: boolean;
  /**
   * @format int32
   * @min 1
   * @max 99
   */
  workflow_max_attempts?: number;
  /**
   * @format int64
   * @min 1
   * @max 5940
   */
  workflow_max_temporal_rate_seconds?: number;
  workflow_rate_limit_enabled?: boolean;
  workflow_safe_mode_enabled?: boolean;
  workflow_scope_rules?: WorkflowScopeRule[];
  workflow_scope_variables?: ScopeVariable[];
  workflow_standalone_conditions?: Condition[];
  /** Status of the workflow (TEMPLATE, RUN, STOP, END) */
  workflow_status: "TEMPLATE" | "RUN" | "STOP" | "END";
  /** Steps that belong to this workflow */
  workflow_steps?: Step[];
  workflow_timeout_enabled?: boolean;
  /**
   * @format int64
   * @min 0
   * @max 86400
   */
  workflow_timeout_seconds?: number;
  /**
   * Update date
   * @format date-time
   */
  workflow_updated_at?: string;
  /**
   * Version of the workflow, incremented at each launch if edited
   * @format int32
   * @min 0
   */
  workflow_version?: number;
}

/** Input for creating or updating a workflow configuration. */
export interface WorkflowConfigurationInput {
  /**
   * Maximum number of attempts allowed before the temporal rate limit kicks in (1–99).
   * @format int32
   * @min 1
   * @max 99
   */
  workflow_configuration_max_attempts?: number;
  /**
   * Seconds to wait between attempts (1–3540).
   * @format int64
   * @min 1
   * @max 5940
   */
  workflow_configuration_max_temporal_rate_seconds?: number;
  /** Whether rate limiting is enabled. */
  workflow_configuration_rate_limit_enabled?: boolean;
  /**
   * If enabled, exploits that could crash the customer environment will not be executed.
   * @default true
   */
  workflow_configuration_safe_mode_enabled?: boolean;
  /** Whether the timeout feature is enabled. */
  workflow_configuration_timeout_enabled?: boolean;
  /**
   * Total timeout in seconds for the attack workflow scenario (60–86400).
   * @format int64
   * @min 60
   * @max 86400
   * @default 3600
   */
  workflow_configuration_timeout_seconds?: number;
  /** List scope rules. */
  workflow_scope_rules?: WorkflowScopeRuleInput[];
  /** List of custom variables available for template substitution in this workflow. */
  workflow_scope_variables?: ScopeVariableInput[];
}

/** Output for a workflow configuration. */
export interface WorkflowConfigurationOutput {
  /**
   * Maximum number of attempts allowed before the temporal rate limit kicks in.
   * @format int32
   */
  workflow_configuration_max_attempts?: number;
  /**
   * Seconds to wait between attempts.
   * @format int64
   */
  workflow_configuration_max_temporal_rate_seconds?: number;
  /** Whether rate limiting is enabled. */
  workflow_configuration_rate_limit_enabled?: boolean;
  /** If enabled, exploits that could crash the customer environment will not be executed. */
  workflow_configuration_safe_mode_enabled?: boolean;
  /** Whether the timeout feature is enabled. */
  workflow_configuration_timeout_enabled?: boolean;
  /**
   * Total timeout in seconds for the attack workflow.
   * @format int64
   */
  workflow_configuration_timeout_seconds?: number;
  /** List scope rules */
  workflow_scope_rules?: WorkflowScopeRuleOutput[];
  /** Custom variables available for template substitution in this workflow. */
  workflow_scope_variables?: ScopeVariableOutput[];
  /** Connected security platforms frozen at launch (launched simulation only; empty for draft / scenario, where the frontend resolves the tenant's platforms live). */
  workflow_security_platforms?: SecurityPlatformSnapshotOutput[];
}

/** Injector contract referenced by a workflow step, exposed for the logic screen. Only the fields needed to render the action form are returned. */
export interface WorkflowInjectorContractOutput {
  /** Injector contract content (serialized fields) */
  injector_contract_content?: string;
  /**
   * Injector contract Id
   * @minLength 1
   */
  injector_contract_id: string;
}

export interface WorkflowScopeRule {
  listened?: boolean;
  /** @format date-time */
  workflow_scope_rule_created_at?: string;
  /** ID of the workflow scope rule */
  workflow_scope_rule_id?: string;
  workflow_scope_rule_selected_mode?: "ALLOWLIST" | "DENYLIST";
  workflow_scope_rule_source?:
    | "ASSET"
    | "ASSET_GROUP"
    | "TEAM"
    | "PLAYER"
    | "MANUAL"
    | "CSV"
    | "SECURITY_PLATFORM";
  /** @format date-time */
  workflow_scope_rule_updated_at?: string;
  workflow_scope_rule_value?: string;
  workflow_scope_rule_value_label?: string;
  workflow_scope_rule_value_type?:
    | "IP"
    | "IP_SUBNET"
    | "DOMAIN"
    | "ASSET_ID"
    | "ASSET_GROUP_ID"
    | "TEAM_ID"
    | "PLAYER_ID"
    | "SECURITY_PLATFORM_ID";
}

/** Input for a scope rule used in workflow configuration. */
export interface WorkflowScopeRuleInput {
  /** ID of an existing scope rule. Null means a new rule will be created. */
  workflow_scope_rule_id?: string;
  /** Selected list mode where the rule should be applied */
  workflow_scope_rule_selected_mode: "ALLOWLIST" | "DENYLIST";
  /** Source of the selected rule */
  workflow_scope_rule_source:
    | "ASSET"
    | "ASSET_GROUP"
    | "TEAM"
    | "PLAYER"
    | "MANUAL"
    | "CSV"
    | "SECURITY_PLATFORM";
  /**
   * Selected rule value
   * @minLength 1
   */
  workflow_scope_rule_value: string;
}

/** Output for a scope rule used in workflow configuration. */
export interface WorkflowScopeRuleOutput {
  /** ID of the scope rule. */
  workflow_scope_rule_id?: string;
  /** Selected list mode where the rule is applied. */
  workflow_scope_rule_selected_mode?: "ALLOWLIST" | "DENYLIST";
  /** Frozen composition at end of run (empty while still running). */
  workflow_scope_rule_snapshot_end_assets?: AssetSnapshotOutput[];
  /** Frozen label at end of run (null while the simulation is still running). */
  workflow_scope_rule_snapshot_end_label?: string;
  /** Frozen composition at launch, with agents (asset / group rules). */
  workflow_scope_rule_snapshot_start_assets?: AssetSnapshotOutput[];
  /** Frozen label at launch (for display when the target was deleted). */
  workflow_scope_rule_snapshot_start_label?: string;
  /** Source of the selected item */
  workflow_scope_rule_source?:
    | "ASSET"
    | "ASSET_GROUP"
    | "TEAM"
    | "PLAYER"
    | "MANUAL"
    | "CSV"
    | "SECURITY_PLATFORM";
  /** Change status vs the frozen snapshots (launched simulation only; null for draft / scenario, where the frontend resolves live). */
  workflow_scope_rule_status?:
    | "RESOLVED"
    | "MODIFIED_DURING_EXECUTION"
    | "DELETED_DURING_EXECUTION"
    | "MODIFIED_AFTER_EXECUTION"
    | "DELETED_AFTER_EXECUTION";
  /** Selected item value */
  workflow_scope_rule_value?: string;
  /** Display-name snapshot of the referenced asset / asset group / team / player, captured when the rule was created or updated. Lets a past simulation's scope stay readable after the referenced entity is deleted. Null for MANUAL / CSV rules or when the id could not be resolved within the tenant. */
  workflow_scope_rule_value_label?: string;
}

export interface XtmComposerInstanceOutput {
  /**
   * Connector image
   * @minLength 1
   */
  connector_image: string;
  /** Connector Instance configuration */
  connector_instance_configurations: Configuration[];
  /**
   * Connector Instance current status
   * @minLength 1
   */
  connector_instance_current_status: "started" | "stopped";
  /**
   * Connector Instance hash
   * @minLength 1
   */
  connector_instance_hash: string;
  /**
   * Connector Instance Id
   * @minLength 1
   */
  connector_instance_id: string;
  /**
   * Connector Instance name
   * @minLength 1
   */
  connector_instance_name: string;
  /**
   * Connector Instance requested status
   * @minLength 1
   */
  connector_instance_requested_status: "starting" | "stopping";
}

export interface XtmComposerOutput {
  /**
   * XTM Composer Id
   * @minLength 1
   */
  xtm_composer_id: string;
  /**
   * XTM Composer Version
   * @minLength 1
   */
  xtm_composer_version: string;
}

export interface XtmComposerRegisterInput {
  /**
   * The XTM Composer Id
   * @minLength 1
   */
  id: string;
  /**
   * The XTM Composer Name
   * @minLength 1
   */
  name: string;
  /**
   * The registration public key
   * @minLength 1
   */
  public_key: string;
}

export interface XtmComposerUpdateStatusInput {
  /** The connector instance current status */
  connector_instance_current_status: "started" | "stopped";
}

export interface XtmHubContactUsInput {
  /**
   * The message sent
   * @minLength 1
   */
  message: string;
}

export interface XtmHubRegisterInput {
  /**
   * The registration token
   * @minLength 1
   */
  token: string;
}

export interface XtmHubRegistrationOutput {
  /** @format date-time */
  tenant_xtmhub_registration_date?: string;
  tenant_xtmhub_registration_id?: string;
  /** @format date-time */
  tenant_xtmhub_registration_last_connectivity_check?: string;
  tenant_xtmhub_registration_status?:
    | "REGISTERED"
    | "UNREGISTERED"
    | "LOST_CONNECTIVITY";
  tenant_xtmhub_registration_token?: string;
  tenant_xtmhub_registration_user_id?: string;
  tenant_xtmhub_registration_user_name?: string;
}
