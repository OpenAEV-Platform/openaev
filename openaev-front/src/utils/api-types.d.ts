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

export interface Agent {
  agent_active?: boolean;
  agent_asset: string;
  /** @format date-time */
  agent_cleared_at?: string;
  /** @format date-time */
  agent_created_at: string;
  agent_deployment_mode: "service" | "session";
  agent_executed_by_user: string;
  agent_executor?: string;
  agent_external_reference?: string;
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

/** List of primary agents */
export interface AgentOutput {
  /** Indicates whether the endpoint is active. The endpoint is considered active if it was seen in the last 3 minutes. */
  agent_active?: boolean;
  /** Agent deployment mode */
  agent_deployment_mode?: "service" | "session";
  /** The user who executed the agent */
  agent_executed_by_user?: string;
  /** Agent executor */
  agent_executor?: ExecutorOutput;
  /** Agent id */
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
   * Asset groups linked to endpoints
   * @uniqueItems true
   */
  finding_asset_groups?: AssetGroupSimple[];
  /**
   * Endpoint linked to finding
   * @uniqueItems true
   */
  finding_assets: EndpointSimple[];
  /** @format date-time */
  finding_created_at: string;
  /** Finding Id */
  finding_id: string;
  /**
   * Represents the data type being extracted.
   * @example "text, number, port, portscan, ipv4, ipv6, credentials, cve"
   */
  finding_type:
    | "text"
    | "number"
    | "port"
    | "portscan"
    | "ipv4"
    | "ipv6"
    | "credentials"
    | "cve";
  /** Finding Value */
  finding_value: string;
}

export interface AiGenericTextInput {
  ai_content: string;
  ai_format?: string;
  ai_tone?: string;
}

export interface AiMediaInput {
  ai_author?: string;
  ai_context?: string;
  ai_format: string;
  ai_input: string;
  /** @format int32 */
  ai_paragraphs?: number;
  ai_tone?: string;
}

export interface AiMessageInput {
  ai_context?: string;
  ai_format: string;
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
  article_channel: string;
  /** @format int32 */
  article_comments?: number;
  article_content?: string;
  article_documents?: string[];
  /** @format int32 */
  article_likes?: number;
  article_name: string;
  article_published?: boolean;
  /** @format int32 */
  article_shares?: number;
}

export interface ArticleUpdateInput {
  article_author?: string;
  article_channel: string;
  /** @format int32 */
  article_comments?: number;
  article_content?: string;
  article_documents?: string[];
  /** @format int32 */
  article_likes?: number;
  article_name: string;
  article_published?: boolean;
  /** @format int32 */
  article_shares?: number;
}

export interface AssetAgentJob {
  asset_agent_agent?: string;
  /** @deprecated */
  asset_agent_asset?: string;
  asset_agent_command: string;
  asset_agent_id: string;
  asset_agent_inject?: string;
  listened?: boolean;
}

export interface AssetGroup {
  asset_group_assets?: string[];
  /** @format date-time */
  asset_group_created_at: string;
  asset_group_description?: string;
  asset_group_dynamic_assets?: string[];
  /** Filter object to search within filterable attributes */
  asset_group_dynamic_filter: FilterGroup;
  asset_group_external_reference?: string;
  asset_group_id: string;
  asset_group_name: string;
  asset_group_tags?: string[];
  /** @format date-time */
  asset_group_updated_at: string;
  listened?: boolean;
}

export interface AssetGroupInput {
  asset_group_description?: string;
  /** Filter object to search within filterable attributes */
  asset_group_dynamic_filter?: FilterGroup;
  asset_group_name: string;
  asset_group_tags?: string[];
}

export interface AssetGroupOutput {
  /** @uniqueItems true */
  asset_group_assets?: string[];
  asset_group_description?: string;
  /** Filter object to search within filterable attributes */
  asset_group_dynamic_filter?: FilterGroup;
  asset_group_id: string;
  asset_group_name: string;
  /** @uniqueItems true */
  asset_group_tags?: string[];
}

/** Asset groups linked to endpoints */
export interface AssetGroupSimple {
  /** Asset group Id */
  asset_group_id: string;
  /** Asset group Name */
  asset_group_name: string;
}

export interface AssetGroupTarget {
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

/** Full contract */
export interface AtomicInjectorContractOutput {
  convertedContent?: object;
  injector_contract_content: string;
  injector_contract_id: string;
  injector_contract_labels: Record<string, string>;
  injector_contract_payload?: PayloadSimple;
  injector_contract_platforms?: (
    | "Linux"
    | "Windows"
    | "MacOS"
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
  inject_injector_contract?: string;
  inject_tags?: string[];
  inject_teams?: string[];
  inject_title: string;
}

export interface AtomicTestingUpdateTagsInput {
  atomic_tags?: string[];
}

export interface AttackPattern {
  /** @format date-time */
  attack_pattern_created_at?: string;
  attack_pattern_description?: string;
  attack_pattern_external_id: string;
  attack_pattern_id: string;
  attack_pattern_kill_chain_phases?: string[];
  attack_pattern_name: string;
  attack_pattern_parent?: string;
  attack_pattern_permissions_required?: string[];
  attack_pattern_platforms?: string[];
  attack_pattern_stix_id: string;
  /** @format date-time */
  attack_pattern_updated_at?: string;
  listened?: boolean;
}

export interface AttackPatternCreateInput {
  attack_pattern_description?: string;
  attack_pattern_external_id: string;
  attack_pattern_kill_chain_phases?: string[];
  attack_pattern_name: string;
  attack_pattern_parent?: string;
  attack_pattern_permissions_required?: string[];
  attack_pattern_platforms?: string[];
  attack_pattern_stix_id?: string;
}

export interface AttackPatternSimple {
  attack_pattern_external_id: string;
  attack_pattern_id: string;
  attack_pattern_name: string;
}

export interface AttackPatternUpdateInput {
  attack_pattern_description?: string;
  attack_pattern_external_id: string;
  attack_pattern_kill_chain_phases?: string[];
  attack_pattern_name: string;
}

export interface AttackPatternUpsertInput {
  attack_patterns?: AttackPatternCreateInput[];
  ignore_dependencies?: boolean;
}

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
  /** Injector contract Id */
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
  payload_attack_patterns?: string[];
  payload_cleanup_command?: string;
  payload_cleanup_executor?: string;
  payload_collector?: string;
  payload_collector_type?: string;
  /** @format date-time */
  payload_created_at: string;
  payload_description?: string;
  payload_detection_remediations?: DetectionRemediation[];
  /** @uniqueItems true */
  payload_domains: Domain[];
  payload_elevation_required?: boolean;
  payload_execution_arch: "x86_64" | "arm64" | "ALL_ARCHITECTURES";
  payload_expectations?: (
    | "TEXT"
    | "DOCUMENT"
    | "ARTICLE"
    | "CHALLENGE"
    | "MANUAL"
    | "PREVENTION"
    | "DETECTION"
    | "VULNERABILITY"
  )[];
  payload_external_id?: string;
  payload_id: string;
  payload_name: string;
  /** @uniqueItems true */
  payload_output_parsers?: OutputParser[];
  payload_platforms: (
    | "Linux"
    | "Windows"
    | "MacOS"
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
  payload_type?: string;
  /** @format date-time */
  payload_updated_at: string;
  typeEnum?:
    | "COMMAND"
    | "EXECUTABLE"
    | "FILE_DROP"
    | "DNS_RESOLUTION"
    | "NETWORK_TRAFFIC";
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
  payload_execution_arch: "x86_64" | "arm64" | "ALL_ARCHITECTURES";
  payload_expectations: (
    | "TEXT"
    | "DOCUMENT"
    | "ARTICLE"
    | "CHALLENGE"
    | "MANUAL"
    | "PREVENTION"
    | "DETECTION"
    | "VULNERABILITY"
  )[];
  payload_name: string;
  /**
   * Set of output parsers
   * @uniqueItems true
   */
  payload_output_parsers?: OutputParserInput[];
  payload_platforms: (
    | "Linux"
    | "Windows"
    | "MacOS"
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
  payload_type: string;
}

type BasePayloadCreateInputPayloadTypeMapping<Key, Type> = {
  payload_type: Key;
} & Type;

type BasePayloadPayloadTypeMapping<Key, Type> = {
  payload_type: Key;
} & Type;

export interface CVEBulkInsertInput {
  cves: CveCreateInput[];
  initial_dataset_completed?: boolean;
  /** @format int32 */
  last_index?: number;
  /** @format date-time */
  last_modified_date_fetched?: string;
  source_identifier: string;
}

export interface CatalogConnectorOutput {
  catalog_connector_description?: string;
  catalog_connector_id: string;
  /** @format date-time */
  catalog_connector_last_verified_date?: string;
  catalog_connector_logo_url?: string;
  catalog_connector_manager_supported?: boolean;
  catalog_connector_short_description?: string;
  catalog_connector_source_code?: string;
  catalog_connector_subscription_link?: string;
  catalog_connector_title: string;
  catalog_connector_type: "COLLECTOR" | "INJECTOR" | "EXECUTOR";
  /** @uniqueItems true */
  catalog_connector_use_cases?: string[];
  catalog_connector_verified?: boolean;
}

export interface Challenge {
  challenge_category?: string;
  challenge_content?: string;
  /** @format date-time */
  challenge_created_at: string;
  challenge_documents?: string[];
  challenge_exercises?: string[];
  challenge_flags: ChallengeFlag[];
  challenge_id: string;
  /** @format int32 */
  challenge_max_attempts?: number;
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
  challenge_expectation?: InjectExpectation;
}

export interface ChallengeInput {
  challenge_category?: string;
  challenge_content?: string;
  challenge_documents?: string[];
  challenge_flags: FlagInput[];
  /** @format int32 */
  challenge_max_attempts?: number;
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
  /** The new password */
  password: string;
  /** The new password again to validate it's been typed well */
  password_validation: string;
}

export interface Channel {
  /** @format date-time */
  channel_created_at: string;
  channel_description?: string;
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
  channel_description: string;
  channel_name: string;
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
  channel_description: string;
  channel_mode?: string;
  channel_name: string;
  channel_primary_color_dark?: string;
  channel_primary_color_light?: string;
  channel_secondary_color_dark?: string;
  channel_secondary_color_light?: string;
  channel_type: string;
}

export interface ChannelUpdateLogoInput {
  channel_logo_dark?: string;
  channel_logo_light?: string;
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
  /** @format date-time */
  collector_created_at: string;
  collector_external?: boolean;
  collector_id: string;
  /** @format date-time */
  collector_last_execution?: string;
  collector_name: string;
  /** @format int32 */
  collector_period?: number;
  collector_security_platform?: SecurityPlatform;
  collector_state?: object;
  collector_type: string;
  /** @format date-time */
  collector_updated_at: string;
  listened?: boolean;
}

export interface CollectorCreateInput {
  collector_id: string;
  collector_name: string;
  /** @format int32 */
  collector_period?: number;
  collector_security_platform?: string;
  collector_type: string;
}

export interface CollectorUpdateInput {
  /** @format date-time */
  collector_last_execution?: string;
}

export interface Comcheck {
  /** @format date-time */
  comcheck_end_date: string;
  comcheck_exercise?: string;
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
  payload_attack_patterns?: string[];
  payload_cleanup_command?: string;
  payload_cleanup_executor?: string;
  payload_collector?: string;
  payload_collector_type?: string;
  /** @format date-time */
  payload_created_at: string;
  payload_description?: string;
  /** @uniqueItems true */
  payload_domains: Domain[];
  payload_elevation_required?: boolean;
  payload_execution_arch: "x86_64" | "arm64" | "ALL_ARCHITECTURES";
  payload_expectations?: (
    | "TEXT"
    | "DOCUMENT"
    | "ARTICLE"
    | "CHALLENGE"
    | "MANUAL"
    | "PREVENTION"
    | "DETECTION"
    | "VULNERABILITY"
  )[];
  payload_external_id?: string;
  payload_id: string;
  payload_name: string;
  /** @uniqueItems true */
  payload_output_parsers?: OutputParser[];
  payload_platforms: (
    | "Linux"
    | "Windows"
    | "MacOS"
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
  payload_type?: string;
  /** @format date-time */
  payload_updated_at: string;
  typeEnum?:
    | "COMMAND"
    | "EXECUTABLE"
    | "FILE_DROP"
    | "DNS_RESOLUTION"
    | "NETWORK_TRAFFIC";
}

/** List of communications of this team */
export interface Communication {
  communication_ack?: boolean;
  communication_animation?: boolean;
  communication_attachments?: string[];
  communication_content?: string;
  communication_content_html?: string;
  communication_exercise?: string;
  communication_from: string;
  communication_id: string;
  communication_inject?: string;
  communication_message_id: string;
  /** @format date-time */
  communication_received_at: string;
  /** @format date-time */
  communication_sent_at: string;
  communication_subject?: string;
  communication_to: string;
  communication_users?: string[];
  listened?: boolean;
}

export interface Condition {
  key: string;
  operator: "eq";
  value?: boolean;
}

export interface ContractOutputElement {
  /** @format date-time */
  contract_output_element_created_at: string;
  contract_output_element_id: string;
  contract_output_element_is_finding: boolean;
  contract_output_element_key: string;
  contract_output_element_name: string;
  /** @uniqueItems true */
  contract_output_element_regex_groups: RegexGroup[];
  contract_output_element_rule: string;
  contract_output_element_tags?: string[];
  contract_output_element_type:
    | "text"
    | "number"
    | "port"
    | "portscan"
    | "ipv4"
    | "ipv6"
    | "credentials"
    | "cve";
  /** @format date-time */
  contract_output_element_updated_at: string;
  listened?: boolean;
}

/** List of Contract output elements */
export interface ContractOutputElementInput {
  contract_output_element_id?: string;
  /** Indicates whether this contract output element can be used to generate a finding */
  contract_output_element_is_finding: boolean;
  /** Key */
  contract_output_element_key: string;
  /** Name */
  contract_output_element_name: string;
  /**
   * Set of regex groups
   * @uniqueItems true
   */
  contract_output_element_regex_groups: RegexGroupInput[];
  /** Parser Rule */
  contract_output_element_rule: string;
  /** List of tags */
  contract_output_element_tags?: string[];
  /** Contract Output element type, can be: text, number, port, IPV6, IPV4, portscan, credentials */
  contract_output_element_type:
    | "text"
    | "number"
    | "port"
    | "portscan"
    | "ipv4"
    | "ipv6"
    | "credentials"
    | "cve";
}

/** Represents the rules for parsing the output of an execution. */
export interface ContractOutputElementSimple {
  contract_output_element_id: string;
  /** Represents a unique key identifier. */
  contract_output_element_key: string;
  /** Represents the name of the rule. */
  contract_output_element_name: string;
  /** @uniqueItems true */
  contract_output_element_regex_groups: RegexGroupSimple[];
  /** The rule to apply for parsing the output, for example, can be a regex. */
  contract_output_element_rule: string;
  contract_output_element_tags?: string[];
  /**
   * Represents the data type being extracted.
   * @example "text, number, port, portscan, ipv4, ipv6, credentials"
   */
  contract_output_element_type:
    | "text"
    | "number"
    | "port"
    | "portscan"
    | "ipv4"
    | "ipv6"
    | "credentials"
    | "cve";
}

export interface CreateExerciseInput {
  exercise_category?: string;
  exercise_custom_dashboard?: string;
  exercise_description?: string;
  exercise_mail_from?: string;
  exercise_mails_reply_to?: string[];
  exercise_main_focus?: string;
  exercise_message_footer?: string;
  exercise_message_header?: string;
  exercise_name: string;
  exercise_severity?: string;
  /** @format date-time */
  exercise_start_date?: string | null;
  exercise_subtitle?: string;
  exercise_tags?: string[];
}

export interface CreateNotificationRuleInput {
  resource_id: string;
  resource_type: string;
  subject: string;
  trigger: string;
  type: string;
}

export interface CreateUserInput {
  /** True if the user is admin */
  user_admin?: boolean;
  /** The email of the user */
  user_email: string;
  /** First name of the user */
  user_firstname?: string;
  /** Last name of the user */
  user_lastname?: string;
  /** Organization of the user */
  user_organization?: string;
  /** Password of the user as plain text */
  user_plain_password?: string;
  /** Tags of the user */
  user_tags?: string[];
}

export interface CustomDashboard {
  /** @format date-time */
  custom_dashboard_created_at: string;
  custom_dashboard_description?: string;
  custom_dashboard_id: string;
  custom_dashboard_name: string;
  custom_dashboard_parameters?: CustomDashboardParameters[];
  /** @format date-time */
  custom_dashboard_updated_at: string;
  custom_dashboard_widgets?: Widget[];
  listened?: boolean;
}

export interface CustomDashboardInput {
  custom_dashboard_description?: string;
  custom_dashboard_name: string;
  custom_dashboard_parameters?: CustomDashboardParametersInput[];
}

export interface CustomDashboardOutput {
  custom_dashboard_id?: string;
  custom_dashboard_name?: string;
}

export interface CustomDashboardParameters {
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

/** Payload to create a CVE */
export interface CveCreateInput {
  /**
   * CVSS score
   * @min 0
   * @exclusiveMin false
   * @max 10
   * @exclusiveMax false
   * @example 7.5
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

/** Full CVE output including references and CWEs */
export interface CveOutput {
  /**
   * CVSS score
   * @example 7.8
   */
  cve_cvss_v31: number;
  /**
   * CISA required action due date
   * @format date-time
   */
  cve_cisa_action_due?: string;
  /**
   * CISA exploit addition date
   * @format date-time
   */
  cve_cisa_exploit_add?: string;
  /** Action required by CISA */
  cve_cisa_required_action?: string;
  /** Name used by CISA for the vulnerability */
  cve_cisa_vulnerability_name?: string;
  /** List of CWE outputs */
  cve_cwes?: CweOutput[];
  /** Detailed CVE description */
  cve_description?: string;
  /**
   * External CVE identifier
   * @example "CVE-2024-0001"
   */
  cve_external_id: string;
  /** Id */
  cve_id: string;
  /**
   * CVE published date
   * @format date-time
   */
  cve_published?: string;
  /** External references */
  cve_reference_urls?: string[];
  /** Remediation suggestions */
  cve_remediation?: string;
  /** Source identifier */
  cve_source_identifier?: string;
  /** Status of the vulnerability */
  cve_vuln_status?: "ANALYZED" | "DEFERRED" | "MODIFIED";
}

/** Simplified CVE representation */
export interface CveSimple {
  /**
   * CVSS score
   * @example 7.8
   */
  cve_cvss_v31: number;
  /**
   * External CVE identifier
   * @example "CVE-2024-0001"
   */
  cve_external_id: string;
  /** Id */
  cve_id: string;
  /**
   * CVE published date
   * @format date-time
   */
  cve_published?: string;
}

/** CWE input used in vulnerability creation/update */
export interface CweInput {
  /**
   * External CWE identifier
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
   * @example "CWE-79"
   */
  cwe_external_id: string;
  /** Source of the CWE */
  cwe_source?: string;
}

export type DateHistogramWidget = UtilRequiredKeys<
  WidgetConfiguration,
  "series" | "widget_configuration_type" | "time_range" | "date_attribute"
> & {
  display_legend?: boolean;
  interval: "year" | "month" | "week" | "day" | "hour" | "quarter";
  mode: string;
  stacked?: boolean;
};

export interface DetectionRemediation {
  author_rule: "HUMAN" | "AI" | "AI_OUTDATED";
  detection_remediation_collector_type: string;
  /** @format date-time */
  detection_remediation_created_at?: string;
  detection_remediation_id: string;
  detection_remediation_payload_id: Payload;
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

/** List of detection remediation gaps for collectors */
export interface DetectionRemediationInput {
  author_rule: "HUMAN" | "AI" | "AI_OUTDATED";
  /** Collector type */
  detection_remediation_collector: string;
  detection_remediation_id?: string;
  /** Value of detection remediation, for exemple: query for sentinel */
  detection_remediation_values: string;
}

export interface DetectionRemediationOutput {
  /** Author of rules: Human, AI or AI out of date (for rules generated before payload updated) */
  detection_remediation_author_rule: "HUMAN" | "AI" | "AI_OUTDATED";
  /** Collector type */
  detection_remediation_collector: string;
  detection_remediation_id?: string;
  /** Payload id */
  detection_remediation_payload: string;
  /** Value of detection remediation, for exemple: query for sentinel */
  detection_remediation_values: string;
}

export interface DirectInjectInput {
  inject_content?: object;
  inject_description?: string;
  inject_documents?: InjectDocumentInput[];
  inject_injector_contract?: string;
  inject_title?: string;
  inject_users?: string[];
}

export interface DnsResolution {
  dns_resolution_hostname: string;
  listened?: boolean;
  payload_arguments?: PayloadArgument[];
  payload_attack_patterns?: string[];
  payload_cleanup_command?: string;
  payload_cleanup_executor?: string;
  payload_collector?: string;
  payload_collector_type?: string;
  /** @format date-time */
  payload_created_at: string;
  payload_description?: string;
  /** @uniqueItems true */
  payload_domains: Domain[];
  payload_elevation_required?: boolean;
  payload_execution_arch: "x86_64" | "arm64" | "ALL_ARCHITECTURES";
  payload_expectations?: (
    | "TEXT"
    | "DOCUMENT"
    | "ARTICLE"
    | "CHALLENGE"
    | "MANUAL"
    | "PREVENTION"
    | "DETECTION"
    | "VULNERABILITY"
  )[];
  payload_external_id?: string;
  payload_id: string;
  payload_name: string;
  /** @uniqueItems true */
  payload_output_parsers?: OutputParser[];
  payload_platforms: (
    | "Linux"
    | "Windows"
    | "MacOS"
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
  payload_type?: string;
  /** @format date-time */
  payload_updated_at: string;
  typeEnum?:
    | "COMMAND"
    | "EXECUTABLE"
    | "FILE_DROP"
    | "DNS_RESOLUTION"
    | "NETWORK_TRAFFIC";
}

export interface Document {
  document_description?: string;
  document_exercises?: string[];
  document_id: string;
  document_name: string;
  document_scenarios?: string[];
  document_tags?: string[];
  document_target?: string;
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

/** Domain of the inject */
export interface Domain {
  domain_color: string;
  /** @format date-time */
  domain_created_at?: string;
  domain_id: string;
  domain_name: string;
  /** @format date-time */
  domain_updated_at?: string;
  listened?: boolean;
}

export interface DomainBaseInput {
  /** Color of the domain */
  domain_color: string;
  /** Name of the domain */
  domain_name: string;
}

export interface Endpoint {
  asset_agents?: Agent[];
  /** @format date-time */
  asset_created_at: string;
  asset_description?: string;
  asset_external_reference?: string;
  asset_id: string;
  asset_name: string;
  asset_tags?: string[];
  asset_type?: string;
  /** @format date-time */
  asset_updated_at: string;
  endpoint_arch: "x86_64" | "arm64" | "Unknown";
  endpoint_hostname?: string;
  endpoint_ips?: string[];
  endpoint_is_eol?: boolean;
  endpoint_mac_addresses?: string[];
  endpoint_platform:
    | "Linux"
    | "Windows"
    | "MacOS"
    | "Container"
    | "Service"
    | "Generic"
    | "Internal"
    | "Unknown";
  endpoint_seen_ip?: string;
  listened?: boolean;
}

export interface EndpointInput {
  asset_description?: string;
  asset_external_reference?: string;
  asset_name: string;
  asset_tags?: string[];
  endpoint_agent_version?: string;
  endpoint_arch: "x86_64" | "arm64" | "Unknown";
  endpoint_hostname?: string;
  endpoint_ips?: string[];
  /** True if the endpoint is in an End of Life state */
  endpoint_is_eol?: boolean;
  endpoint_mac_addresses?: string[];
  endpoint_platform:
    | "Linux"
    | "Windows"
    | "MacOS"
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
  /** Asset external reference */
  asset_external_reference?: string;
  /** Asset Id */
  asset_id: string;
  /** Asset name */
  asset_name: string;
  /**
   * Tags
   * @uniqueItems true
   */
  asset_tags?: string[];
  /** Asset type */
  asset_type?: string;
  /** Architecture */
  endpoint_arch: "x86_64" | "arm64" | "Unknown";
  /** Platform */
  endpoint_platform:
    | "Linux"
    | "Windows"
    | "MacOS"
    | "Container"
    | "Service"
    | "Generic"
    | "Internal"
    | "Unknown";
  /** The endpoint is associated with an asset group, either statically or dynamically. */
  is_static?: boolean;
}

export interface EndpointOverviewOutput {
  /**
   * List of primary agents
   * @uniqueItems true
   */
  asset_agents: AgentOutput[];
  /** Asset description */
  asset_description?: string;
  /** Asset Id */
  asset_id: string;
  /** Asset name */
  asset_name: string;
  /**
   * Tags
   * @uniqueItems true
   */
  asset_tags?: string[];
  /** Architecture */
  endpoint_arch?: "x86_64" | "arm64" | "Unknown";
  /** Hostname */
  endpoint_hostname?: string;
  /**
   * List IPs
   * @uniqueItems true
   */
  endpoint_ips?: string[];
  /** True if the endpoint is in an End of Life state */
  endpoint_is_eol?: boolean;
  /**
   * List of MAC addresses
   * @uniqueItems true
   */
  endpoint_mac_addresses?: string[];
  /** Platform */
  endpoint_platform?:
    | "Linux"
    | "Windows"
    | "MacOS"
    | "Container"
    | "Service"
    | "Generic"
    | "Internal"
    | "Unknown";
  /** Seen IP */
  endpoint_seen_ip?: string;
}

export interface EndpointRegisterInput {
  agent_executed_by_user?: string;
  agent_installation_directory?: string;
  agent_installation_mode?: string;
  agent_is_elevated?: boolean;
  agent_is_service?: boolean;
  agent_service_name?: string;
  asset_description?: string;
  asset_external_reference: string;
  asset_name: string;
  asset_tags?: string[];
  endpoint_agent_version?: string;
  endpoint_arch: "x86_64" | "arm64" | "Unknown";
  endpoint_hostname?: string;
  endpoint_ips?: string[];
  /** True if the endpoint is in an End of Life state */
  endpoint_is_eol?: boolean;
  endpoint_mac_addresses?: string[];
  endpoint_platform:
    | "Linux"
    | "Windows"
    | "MacOS"
    | "Container"
    | "Service"
    | "Generic"
    | "Internal"
    | "Unknown";
  seenIp?: string;
}

/** Endpoint linked to finding */
export interface EndpointSimple {
  /** Asset Id */
  asset_id: string;
  /** Asset name */
  asset_name: string;
}

export interface EndpointTarget {
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
  /** Asset Id */
  asset_id: string;
  /** Hostname */
  endpoint_hostname?: string;
  /**
   * List IPs
   * @uniqueItems true
   */
  endpoint_ips?: string[];
  /** Seen IP */
  endpoint_seen_ip?: string;
}

export interface EngineSortField {
  direction: "ASC" | "DESC";
  fieldName: string;
}

export interface EsAssetGroup {
  /** @format date-time */
  base_created_at?: string;
  base_dependencies?: string[];
  base_entity?: string;
  base_id?: string;
  base_representative?: string;
  base_restrictions?: string[];
  /** @format date-time */
  base_updated_at?: string;
  name?: string;
}

export interface EsAttackPath {
  /** @uniqueItems true */
  attackPatternChildrenIds?: string[];
  attackPatternExternalId: string;
  attackPatternId: string;
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
  /** @format date-time */
  base_updated_at?: string;
  description?: string;
  externalId?: string;
  name?: string;
  platforms?: string[];
  stixId?: string;
}

export type EsBase = BaseEsBase &
  (
    | BaseEsBaseBaseEntityMapping<"attack-pattern", EsAttackPattern>
    | BaseEsBaseBaseEntityMapping<"endpoint", EsEndpoint>
    | BaseEsBaseBaseEntityMapping<"finding", EsFinding>
    | BaseEsBaseBaseEntityMapping<"inject", EsInject>
    | BaseEsBaseBaseEntityMapping<"expectation-inject", EsInjectExpectation>
    | BaseEsBaseBaseEntityMapping<"simulation", EsSimulation>
    | BaseEsBaseBaseEntityMapping<"scenario", EsScenario>
    | BaseEsBaseBaseEntityMapping<"tag", EsTag>
    | BaseEsBaseBaseEntityMapping<"vulnerable-endpoint", EsVulnerableEndpoint>
    | BaseEsBaseBaseEntityMapping<"team", EsTeam>
    | BaseEsBaseBaseEntityMapping<"security-platform", EsSecurityPlatform>
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

export interface EsEndpoint {
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
  /** @format date-time */
  base_updated_at?: string;
  endpoint_arch?: string;
  endpoint_description?: string;
  endpoint_external_reference?: string;
  endpoint_hostname?: string;
  /** @uniqueItems true */
  endpoint_ips?: string[];
  endpoint_is_eol?: boolean;
  /** @uniqueItems true */
  endpoint_mac_addresses?: string[];
  endpoint_name?: string;
  endpoint_platform?: string;
  endpoint_seen_ip?: string;
}

export interface EsFinding {
  /** @format date-time */
  base_created_at?: string;
  base_dependencies?: string[];
  base_endpoint_side?: string;
  base_entity?: string;
  base_id?: string;
  base_inject_side?: string;
  base_representative?: string;
  base_restrictions?: string[];
  base_scenario_side?: string;
  base_simulation_side?: string;
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
  base_security_platforms_side?: string[];
  base_simulation_side?: string;
  base_team_side?: string;
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
  /** @format date-time */
  base_updated_at?: string;
  name?: string;
  status?: string;
}

export interface EsSearch {
  base_created_at?: string;
  base_entity?: string;
  base_id: string;
  base_representative?: string;
  /** @format double */
  base_score?: number;
  base_updated_at?: string;
}

export interface EsSecurityPlatform {
  /** @format date-time */
  base_created_at?: string;
  base_dependencies?: string[];
  base_entity?: string;
  base_id?: string;
  base_representative?: string;
  base_restrictions?: string[];
  /** @format date-time */
  base_updated_at?: string;
  name?: string;
}

export interface EsSeries {
  color?: string;
  data?: EsSeriesData[];
  label?: string;
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

export interface Executable {
  executable_file: string;
  listened?: boolean;
  payload_arguments?: PayloadArgument[];
  payload_attack_patterns?: string[];
  payload_cleanup_command?: string;
  payload_cleanup_executor?: string;
  payload_collector?: string;
  payload_collector_type?: string;
  /** @format date-time */
  payload_created_at: string;
  payload_description?: string;
  /** @uniqueItems true */
  payload_domains: Domain[];
  payload_elevation_required?: boolean;
  payload_execution_arch: "x86_64" | "arm64" | "ALL_ARCHITECTURES";
  payload_expectations?: (
    | "TEXT"
    | "DOCUMENT"
    | "ARTICLE"
    | "CHALLENGE"
    | "MANUAL"
    | "PREVENTION"
    | "DETECTION"
    | "VULNERABILITY"
  )[];
  payload_external_id?: string;
  payload_id: string;
  payload_name: string;
  /** @uniqueItems true */
  payload_output_parsers?: OutputParser[];
  payload_platforms: (
    | "Linux"
    | "Windows"
    | "MacOS"
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
  payload_type?: string;
  /** @format date-time */
  payload_updated_at: string;
  typeEnum?:
    | "COMMAND"
    | "EXECUTABLE"
    | "FILE_DROP"
    | "DNS_RESOLUTION"
    | "NETWORK_TRAFFIC";
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
    | "SUCCESS"
    | "ERROR"
    | "MAYBE_PREVENTED"
    | "COMMAND_NOT_FOUND"
    | "COMMAND_CANNOT_BE_EXECUTED"
    | "WARNING"
    | "PARTIAL"
    | "MAYBE_PARTIAL_PREVENTED"
    | "ASSET_AGENTLESS"
    | "AGENT_INACTIVE"
    | "INFO";
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
  /** List of primary agents */
  execution_agent?: AgentOutput;
  /** A detailed message describing the execution */
  execution_message: string;
  /**
   * The status of the execution trace
   * @example "SUCCESS, ERROR, COMMAND_NOT_FOUND, WARNING, COMMAND_CANNOT_BE_EXECUTED.."
   */
  execution_status:
    | "SUCCESS"
    | "ERROR"
    | "MAYBE_PREVENTED"
    | "COMMAND_NOT_FOUND"
    | "COMMAND_CANNOT_BE_EXECUTED"
    | "WARNING"
    | "PARTIAL"
    | "MAYBE_PARTIAL_PREVENTED"
    | "ASSET_AGENTLESS"
    | "AGENT_INACTIVE"
    | "INFO";
  /** @format date-time */
  execution_time: string;
}

export interface Executor {
  executor_background_color?: string;
  /** @format date-time */
  executor_created_at: string;
  executor_doc?: string;
  executor_id: string;
  executor_name: string;
  executor_platforms?: string[];
  executor_type: string;
  /** @format date-time */
  executor_updated_at: string;
  listened?: boolean;
}

export interface ExecutorCreateInput {
  executor_id: string;
  executor_name: string;
  executor_platforms?: string[];
  executor_type: string;
}

/** Agent executor */
export interface ExecutorOutput {
  /** Agent executor id */
  executor_id?: string;
  /** Agent executor name */
  executor_name?: string;
  /** Agent executor type */
  executor_type?: string;
}

export interface ExecutorUpdateInput {
  /** @format date-time */
  executor_last_execution?: string;
}

export interface Exercise {
  /** @format int64 */
  exercise_all_users_number?: number;
  exercise_articles?: string[];
  exercise_category?: string;
  /** @format int64 */
  exercise_communications_number?: number;
  /** @format date-time */
  exercise_created_at: string;
  exercise_custom_dashboard?: string;
  exercise_description?: string;
  exercise_documents?: string[];
  /** @format date-time */
  exercise_end_date?: string;
  exercise_id: string;
  exercise_injects?: string[];
  exercise_injects_statistics?: Record<string, number>;
  exercise_kill_chain_phases?: KillChainPhase[];
  exercise_lessons_anonymized?: boolean;
  /** @format int64 */
  exercise_lessons_answers_number?: number;
  exercise_lessons_categories?: string[];
  exercise_logo_dark?: string;
  exercise_logo_light?: string;
  /** @format int64 */
  exercise_logs_number?: number;
  exercise_mail_from: string;
  exercise_mails_reply_to?: string[];
  exercise_main_focus?: string;
  exercise_message_footer?: string;
  exercise_message_header?: string;
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

export interface ExerciseSimple {
  /** Exercise Category */
  exercise_category?: string;
  exercise_global_score: ExpectationResultsByType[];
  /** Exercise Id */
  exercise_id: string;
  /** Exercise Name */
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
  source_type: string;
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
  payload_attack_patterns?: string[];
  payload_cleanup_command?: string;
  payload_cleanup_executor?: string;
  payload_collector?: string;
  payload_collector_type?: string;
  /** @format date-time */
  payload_created_at: string;
  payload_description?: string;
  /** @uniqueItems true */
  payload_domains: Domain[];
  payload_elevation_required?: boolean;
  payload_execution_arch: "x86_64" | "arm64" | "ALL_ARCHITECTURES";
  payload_expectations?: (
    | "TEXT"
    | "DOCUMENT"
    | "ARTICLE"
    | "CHALLENGE"
    | "MANUAL"
    | "PREVENTION"
    | "DETECTION"
    | "VULNERABILITY"
  )[];
  payload_external_id?: string;
  payload_id: string;
  payload_name: string;
  /** @uniqueItems true */
  payload_output_parsers?: OutputParser[];
  payload_platforms: (
    | "Linux"
    | "Windows"
    | "MacOS"
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
  payload_type?: string;
  /** @format date-time */
  payload_updated_at: string;
  typeEnum?:
    | "COMMAND"
    | "EXECUTABLE"
    | "FILE_DROP"
    | "DNS_RESOLUTION"
    | "NETWORK_TRAFFIC";
}

export interface Filter {
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

/** Filter object to search within filterable attributes */
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
  finding_field: string;
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
    | "number"
    | "port"
    | "portscan"
    | "ipv4"
    | "ipv6"
    | "credentials"
    | "cve";
  /** @format date-time */
  finding_updated_at: string;
  finding_users?: string[];
  finding_value: string;
  listened?: boolean;
}

export interface FindingInput {
  finding_field: string;
  finding_inject_id?: string;
  finding_labels?: string[];
  finding_type:
    | "text"
    | "number"
    | "port"
    | "portscan"
    | "ipv4"
    | "ipv6"
    | "credentials"
    | "cve";
  finding_value: string;
}

export interface FlagInput {
  flag_type: string;
  flag_value: string;
}

export type FlatConfiguration = UtilRequiredKeys<
  WidgetConfiguration,
  "series" | "widget_configuration_type" | "time_range" | "date_attribute"
>;

export interface FullTextSearchCountResult {
  clazz: string;
  /** @format int64 */
  count: number;
}

export interface FullTextSearchResult {
  clazz: string;
  description?: string;
  id: string;
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
  grant_id: string;
  grant_name: "OBSERVER" | "PLANNER" | "LAUNCHER";
  grant_resource?: string;
  grant_resource_type?:
    | "SCENARIO"
    | "SIMULATION"
    | "ATOMIC_TESTING"
    | "PAYLOAD"
    | "UNKNOWN";
  listened?: boolean;
}

export interface Group {
  group_default_user_assign?: boolean;
  group_description?: string;
  group_grants?: Grant[];
  group_id: string;
  group_name: string;
  group_roles?: string[];
  group_users?: string[];
  listened?: boolean;
}

export interface GroupCreateInput {
  group_default_user_assign?: boolean;
  group_description?: string;
  group_name: string;
}

export interface GroupGrantInput {
  grant_name?: "OBSERVER" | "PLANNER" | "LAUNCHER";
  grant_resource?: string;
  grant_resource_type?:
    | "SCENARIO"
    | "SIMULATION"
    | "ATOMIC_TESTING"
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

/** Healthchecks of the inject */
export interface HealthCheck {
  /**
   * Date when the failure have been found
   * @format date-time
   */
  creation_date: string;
  /** Detail of the check failure */
  detail: "SERVICE_UNAVAILABLE" | "NOT_READY" | "EMPTY";
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
    | "NUCLEI";
}

export interface ImportMapper {
  /** @format date-time */
  import_mapper_created_at?: string;
  import_mapper_id: string;
  import_mapper_inject_importers?: InjectImporter[];
  import_mapper_inject_type_column: string;
  import_mapper_name: string;
  /** @format date-time */
  import_mapper_updated_at?: string;
  listened?: boolean;
}

export interface ImportMapperAddInput {
  import_mapper_inject_importers: InjectImporterAddInput[];
  /** @pattern ^[A-Z]{1,2}$ */
  import_mapper_inject_type_column: string;
  import_mapper_name: string;
}

export interface ImportMapperUpdateInput {
  import_mapper_inject_importers: InjectImporterUpdateInput[];
  /** @pattern ^[A-Z]{1,2}$ */
  import_mapper_inject_type_column: string;
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
  import_id: string;
}

export interface ImportTestSummary {
  import_message?: ImportMessage[];
  /** @deprecated */
  injects?: InjectOutput[];
  /** @format int32 */
  total_injects?: number;
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
  inject_id: string;
  /** Injector contract of the inject */
  inject_injector_contract?: InjectorContract;
  inject_kill_chain_phases?: KillChainPhase[];
  inject_ready?: boolean;
  inject_scenario?: string;
  /** @format date-time */
  inject_sent_at?: string;
  inject_status?: InjectStatus;
  inject_tags?: string[];
  inject_teams?: string[];
  inject_testable?: boolean;
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
  /** List of attack pattern used to generate injects */
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

/** Inject dependencies of the inject */
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
    | "complete";
  /**
   * Duration of the execution in miliseconds
   * @format int32
   */
  execution_duration?: number;
  execution_message: string;
  execution_output_raw?: string;
  execution_output_structured?: string;
  execution_status: string;
}

export interface InjectExpectation {
  inject_expectation_agent?: string;
  inject_expectation_article?: string;
  inject_expectation_asset?: string;
  inject_expectation_asset_group?: string;
  inject_expectation_challenge?: string;
  /** @format date-time */
  inject_expectation_created_at?: string;
  inject_expectation_description?: string;
  inject_expectation_exercise?: string;
  /** @format double */
  inject_expectation_expected_score: number;
  inject_expectation_group?: boolean;
  inject_expectation_id: string;
  inject_expectation_inject?: string;
  inject_expectation_name?: string;
  inject_expectation_results?: InjectExpectationResult[];
  /** @format double */
  inject_expectation_score?: number;
  inject_expectation_signatures?: InjectExpectationSignature[];
  inject_expectation_status?:
    | "FAILED"
    | "PENDING"
    | "PARTIAL"
    | "UNKNOWN"
    | "SUCCESS";
  inject_expectation_team?: string;
  inject_expectation_traces?: InjectExpectationTrace[];
  inject_expectation_type:
    | "TEXT"
    | "DOCUMENT"
    | "ARTICLE"
    | "CHALLENGE"
    | "MANUAL"
    | "PREVENTION"
    | "DETECTION"
    | "VULNERABILITY";
  /** @format date-time */
  inject_expectation_updated_at?: string;
  inject_expectation_user?: string;
  /** @format int64 */
  inject_expiration_time: number;
  listened?: boolean;
  target_id?: string;
}

export interface InjectExpectationBulkUpdateInput {
  inputs: Record<string, InjectExpectationUpdateInput>;
}

export interface InjectExpectationResult {
  date?: string;
  metadata?: Record<string, string>;
  result: string;
  /** @format double */
  score?: number;
  sourceId?: string;
  sourceName?: string;
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
  type?: string;
  value?: string;
}

/** Expectations */
export interface InjectExpectationSimple {
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
  inject_expectation_trace_alert_link: string;
  inject_expectation_trace_alert_name: string;
  /** @format date-time */
  inject_expectation_trace_date: string;
  inject_expectation_trace_expectation: string;
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
  inject_importer_type_value: string;
  /** @format date-time */
  inject_importer_updated_at?: string;
  listened?: boolean;
}

export interface InjectImporterAddInput {
  inject_importer_injector_contract: string;
  inject_importer_rule_attributes?: RuleAttributeAddInput[];
  inject_importer_type_value: string;
}

export interface InjectImporterUpdateInput {
  inject_importer_id?: string;
  inject_importer_injector_contract: string;
  inject_importer_rule_attributes?: RuleAttributeUpdateInput[];
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
  inject_injector_contract?: string;
  inject_tags?: string[];
  inject_teams?: string[];
  inject_title: string;
}

export interface InjectOutput {
  inject_asset_groups?: string[];
  inject_assets?: string[];
  inject_content?: object;
  /**
   * Domain of the inject
   * @uniqueItems true
   */
  inject_contract_domains?: Domain[];
  /**
   * Depend duration of the inject
   * @format int64
   * @min 0
   */
  inject_depends_duration: number;
  inject_depends_on?: InjectDependency[];
  /** Enabled state of the inject */
  inject_enabled?: boolean;
  /** Simulation ID of the inject */
  inject_exercise?: string;
  inject_healthchecks?: HealthCheck[];
  /** ID of the inject */
  inject_id: string;
  /** Injector contract of the inject */
  inject_injector_contract?: InjectorContract;
  /** Ready state of the inject */
  inject_ready?: boolean;
  /** Scenario ID of the inject */
  inject_scenario?: string;
  /** @uniqueItems true */
  inject_tags?: string[];
  inject_teams?: string[];
  /** Testable state of the inject */
  inject_testable?: boolean;
  /** Title of the inject */
  inject_title: string;
  /** Type of the inject */
  inject_type?: string;
}

export interface InjectReceptionInput {
  /** @format int32 */
  tracking_total_count?: number;
}

export interface InjectResultOutput {
  /** Result of expectations */
  inject_expectation_results: ExpectationResultsByType[];
  /** Id of inject */
  inject_id: string;
  /** Injector contract */
  inject_injector_contract?: InjectorContractSimple;
  /** status */
  inject_status?: InjectStatusSimple;
  inject_targets?: TargetSimple[];
  /** Title of inject */
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
  inject_content?: object;
  /** Description of inject */
  inject_description?: string;
  /** Result of expectations */
  inject_expectation_results: ExpectationResultsByType[];
  /** Expectations */
  inject_expectations?: InjectExpectationSimple[];
  /** Id of inject */
  inject_id: string;
  /** Full contract */
  inject_injector_contract?: AtomicInjectorContractOutput;
  /** Kill chain phases */
  inject_kill_chain_phases?: KillChainPhaseSimple[];
  /** Indicates whether the inject is ready for use */
  inject_ready?: boolean;
  /** status */
  inject_status?: InjectStatusSimple;
  /**
   * Tags
   * @uniqueItems true
   */
  inject_tags?: string[];
  /** Title of inject */
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
}

export interface InjectResultPayloadExecutionOutput {
  execution_traces: ExecutionTraceOutput[];
  payload_command_blocks: PayloadCommandBlock[];
}

/** Inject linked to finding */
export interface InjectSimple {
  /** Inject Id */
  inject_id: string;
  /** Inject Title */
  inject_title: string;
}

export interface InjectStatus {
  listened?: boolean;
  status_id?: string;
  status_name:
    | "SUCCESS"
    | "ERROR"
    | "MAYBE_PREVENTED"
    | "PARTIAL"
    | "MAYBE_PARTIAL_PREVENTED"
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

/** status */
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
  injector_dependencies?: ("SMTP" | "IMAP" | "NUCLEI" | "NMAP")[];
  injector_executor_clear_commands?: Record<string, string>;
  injector_executor_commands?: Record<string, string>;
  injector_external?: boolean;
  injector_id: string;
  injector_name: string;
  injector_payloads?: boolean;
  injector_type: string;
  /** @format date-time */
  injector_updated_at: string;
  listened?: boolean;
}

export interface InjectorConnection {
  host?: string;
  pass?: string;
  /** @format int32 */
  port?: number;
  use_ssl?: boolean;
  user?: string;
  vhost?: string;
}

/** Injector contract of the inject */
export interface InjectorContract {
  convertedContent?: object;
  injector_contract_arch?: "x86_64" | "arm64" | "ALL_ARCHITECTURES";
  injector_contract_atomic_testing?: boolean;
  injector_contract_attack_patterns?: string[];
  injector_contract_content: string;
  /** @format date-time */
  injector_contract_created_at: string;
  injector_contract_custom?: boolean;
  /** @uniqueItems true */
  injector_contract_domains?: Domain[];
  injector_contract_external_id?: string;
  injector_contract_id: string;
  injector_contract_import_available?: boolean;
  injector_contract_injector: string;
  injector_contract_injector_type?: string;
  injector_contract_injector_type_name?: string;
  injector_contract_labels?: Record<string, string>;
  injector_contract_manual?: boolean;
  injector_contract_needs_executor?: boolean;
  injector_contract_payload?: Payload;
  injector_contract_platforms?: (
    | "Linux"
    | "Windows"
    | "MacOS"
    | "Container"
    | "Service"
    | "Generic"
    | "Internal"
    | "Unknown"
  )[];
  /** @format date-time */
  injector_contract_updated_at: string;
  injector_contract_vulnerabilities?: string[];
  listened?: boolean;
}

export interface InjectorContractAddInput {
  contract_attack_patterns_external_ids?: string[];
  contract_attack_patterns_ids?: string[];
  contract_content: string;
  /** @uniqueItems true */
  contract_domains?: Domain[];
  contract_id: string;
  contract_labels?: Record<string, string>;
  contract_manual?: boolean;
  contract_platforms?: string[];
  contract_vulnerability_external_ids?: string[];
  contract_vulnerability_ids?: string[];
  external_contract_id?: string;
  injector_id: string;
  is_atomic_testing?: boolean;
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

export interface InjectorContractFullOutput {
  injector_contract_arch?: "x86_64" | "arm64" | "ALL_ARCHITECTURES";
  /** Attack pattern IDs */
  injector_contract_attack_patterns?: string[];
  /** Content */
  injector_contract_content: string;
  /** Domain IDs */
  injector_contract_domains: string[];
  /** Injector contract external Id */
  injector_contract_external_id?: string;
  injector_contract_has_full_details?: boolean;
  /** Injector contract Id */
  injector_contract_id: string;
  /** Injector name */
  injector_contract_injector_name?: string;
  /** Injector type */
  injector_contract_injector_type?: string;
  /** Labels */
  injector_contract_labels?: Record<string, string>;
  /** Payload type */
  injector_contract_payload_type?: string;
  /** Platforms */
  injector_contract_platforms?: (
    | "Linux"
    | "Windows"
    | "MacOS"
    | "Container"
    | "Service"
    | "Generic"
    | "Internal"
    | "Unknown"
  )[];
  /**
   * Timestamp when the injector contract was last updated
   * @format date-time
   */
  injector_contract_updated_at: string;
}

export interface InjectorContractInput {
  contract_attack_patterns_external_ids?: string[];
  contract_content: string;
  /** @uniqueItems true */
  contract_domains?: Domain[];
  contract_id: string;
  contract_labels?: Record<string, string>;
  contract_manual?: boolean;
  contract_platforms?: (
    | "Linux"
    | "Windows"
    | "MacOS"
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
  include_full_details?: boolean;
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

/** Injector contract */
export interface InjectorContractSimple {
  convertedContent?: object;
  injector_contract_content: string;
  injector_contract_id: string;
  injector_contract_labels: Record<string, string>;
  injector_contract_payload?: PayloadSimple;
  injector_contract_platforms?: (
    | "Linux"
    | "Windows"
    | "MacOS"
    | "Container"
    | "Service"
    | "Generic"
    | "Internal"
    | "Unknown"
  )[];
}

export interface InjectorContractUpdateInput {
  contract_attack_patterns_ids?: string[];
  contract_content: string;
  /** @uniqueItems true */
  contract_domains?: Domain[];
  contract_labels?: Record<string, string>;
  contract_manual?: boolean;
  contract_platforms?: string[];
  contract_vulnerability_external_ids?: string[];
  contract_vulnerability_ids?: string[];
  is_atomic_testing?: boolean;
}

export interface InjectorContractUpdateMappingInput {
  contract_attack_patterns_ids?: string[];
  contract_vulnerability_ids?: string[];
}

export interface InjectorCreateInput {
  injector_category?: string;
  injector_contracts?: InjectorContractInput[];
  injector_custom_contracts?: boolean;
  injector_executor_clear_commands?: Record<string, string>;
  injector_executor_commands?: Record<string, string>;
  injector_id: string;
  injector_name: string;
  injector_payloads?: boolean;
  injector_type: string;
}

export interface InjectorRegistration {
  connection?: InjectorConnection;
  listen?: string;
}

export interface InjectorUpdateInput {
  injector_category?: string;
  injector_contracts?: InjectorContractInput[];
  injector_custom_contracts?: boolean;
  injector_executor_clear_commands?: Record<string, string>;
  injector_executor_commands?: Record<string, string>;
  injector_name: string;
  injector_payloads?: boolean;
}

export interface InjectsImportInput {
  import_mapper_id: string;
  /** @format date-time */
  launch_date?: string;
  sheet_name: string;
  /** @format int32 */
  timezone_offset: number;
}

export interface InjectsImportTestInput {
  import_mapper: ImportMapperAddInput;
  sheet_name: string;
  /** @format int32 */
  timezone_offset: number;
}

export interface JsonApiDocumentResourceObject {
  data?: ResourceObject;
  included?: object[];
}

export type JsonNode = object;

export interface KillChainPhase {
  listened?: boolean;
  /** @format date-time */
  phase_created_at: string;
  phase_description?: string;
  phase_external_id: string;
  phase_id: string;
  phase_kill_chain_name: string;
  phase_name: string;
  /** @format int64 */
  phase_order?: number;
  phase_shortname: string;
  phase_stix_id?: string;
  /** @format date-time */
  phase_updated_at: string;
}

export interface KillChainPhaseCreateInput {
  phase_description?: string;
  phase_external_id?: string;
  phase_kill_chain_name: string;
  phase_name: string;
  /** @format int64 */
  phase_order?: number;
  phase_shortname: string;
  phase_stix_id?: string;
}

export interface KillChainPhaseObject {
  id: string;
  name?: string;
  /** @format int64 */
  order?: number;
}

/** Kill chain phases */
export interface KillChainPhaseSimple {
  phase_id: string;
  phase_name?: string;
}

export interface KillChainPhaseUpdateInput {
  phase_kill_chain_name: string;
  phase_name: string;
  /** @format int64 */
  phase_order?: number;
}

export interface KillChainPhaseUpsertInput {
  kill_chain_phases?: KillChainPhaseCreateInput[];
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
  lessons_category_name: string;
  /** @format int32 */
  lessons_category_order?: number;
  lessons_category_questions?: string[];
  lessons_category_scenario?: string;
  lessons_category_teams?: string[];
  /** @format date-time */
  lessons_category_updated_at: string;
  lessons_category_users?: string[];
  lessonscategory_id: string;
  listened?: boolean;
}

export interface LessonsCategoryCreateInput {
  lessons_category_description?: string;
  lessons_category_name: string;
  /** @format int32 */
  lessons_category_order?: number;
}

export interface LessonsCategoryTeamsInput {
  lessons_category_teams?: string[];
}

export interface LessonsCategoryUpdateInput {
  lessons_category_description?: string;
  lessons_category_name: string;
  /** @format int32 */
  lessons_category_order?: number;
}

export interface LessonsInput {
  lessons_anonymized?: boolean;
}

export interface LessonsQuestion {
  lessons_question_answers?: string[];
  lessons_question_category: string;
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
  lessonsquestion_id: string;
  listened?: boolean;
}

export interface LessonsQuestionCreateInput {
  lessons_question_content: string;
  lessons_question_explanation?: string;
  /** @format int32 */
  lessons_question_order?: number;
}

export interface LessonsQuestionUpdateInput {
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
  lessons_template_name: string;
  /** @format date-time */
  lessons_template_updated_at: string;
  lessonstemplate_id: string;
  listened?: boolean;
}

export interface LessonsTemplateCategory {
  /** @format date-time */
  lessons_template_category_created_at: string;
  lessons_template_category_description?: string;
  lessons_template_category_name: string;
  /** @format int32 */
  lessons_template_category_order: number;
  lessons_template_category_questions?: string[];
  lessons_template_category_template?: string;
  /** @format date-time */
  lessons_template_category_updated_at: string;
  lessonstemplatecategory_id: string;
  listened?: boolean;
}

export interface LessonsTemplateCategoryInput {
  lessons_template_category_description?: string;
  lessons_template_category_name: string;
  /** @format int32 */
  lessons_template_category_order: number;
}

export interface LessonsTemplateInput {
  lessons_template_description?: string;
  lessons_template_name: string;
}

export interface LessonsTemplateQuestion {
  lessons_template_question_category?: string;
  lessons_template_question_content: string;
  /** @format date-time */
  lessons_template_question_created_at: string;
  lessons_template_question_explanation?: string;
  /** @format int32 */
  lessons_template_question_order: number;
  /** @format date-time */
  lessons_template_question_updated_at: string;
  lessonstemplatequestion_id: string;
  listened?: boolean;
}

export interface LessonsTemplateQuestionInput {
  lessons_template_question_content: string;
  lessons_template_question_explanation?: string;
  /** @format int32 */
  lessons_template_question_order: number;
}

/** Platform licensing */
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
  "series" | "widget_configuration_type" | "time_range" | "date_attribute"
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
  /** Filter object to search within filterable attributes */
  filter?: FilterGroup;
  name?: string;
}

export interface Log {
  listened?: boolean;
  log_content: string;
  /** @format date-time */
  log_created_at: string;
  log_exercise?: string;
  log_id: string;
  log_tags?: string[];
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
  /** The identifier of the user */
  login: string;
  /** The password of the user */
  password: string;
}

export interface Mitigation {
  listened?: boolean;
  mitigation_attack_patterns?: string[];
  /** @format date-time */
  mitigation_created_at: string;
  mitigation_description?: string;
  mitigation_external_id: string;
  mitigation_id: string;
  mitigation_log_sources?: string[];
  mitigation_name: string;
  mitigation_stix_id: string;
  mitigation_threat_hunting_techniques?: string;
  /** @format date-time */
  mitigation_updated_at: string;
}

export interface MitigationCreateInput {
  mitigation_attack_patterns?: string[];
  mitigation_description?: string;
  mitigation_external_id: string;
  mitigation_log_sources?: string[];
  mitigation_name: string;
  mitigation_stix_id?: string;
  mitigation_threat_hunting_techniques?: string;
}

export interface MitigationUpdateInput {
  mitigation_attack_patterns?: string[];
  mitigation_description?: string;
  mitigation_external_id: string;
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
  payload_attack_patterns?: string[];
  payload_cleanup_command?: string;
  payload_cleanup_executor?: string;
  payload_collector?: string;
  payload_collector_type?: string;
  /** @format date-time */
  payload_created_at: string;
  payload_description?: string;
  /** @uniqueItems true */
  payload_domains: Domain[];
  payload_elevation_required?: boolean;
  payload_execution_arch: "x86_64" | "arm64" | "ALL_ARCHITECTURES";
  payload_expectations?: (
    | "TEXT"
    | "DOCUMENT"
    | "ARTICLE"
    | "CHALLENGE"
    | "MANUAL"
    | "PREVENTION"
    | "DETECTION"
    | "VULNERABILITY"
  )[];
  payload_external_id?: string;
  payload_id: string;
  payload_name: string;
  /** @uniqueItems true */
  payload_output_parsers?: OutputParser[];
  payload_platforms: (
    | "Linux"
    | "Windows"
    | "MacOS"
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
  payload_type?: string;
  /** @format date-time */
  payload_updated_at: string;
  typeEnum?:
    | "COMMAND"
    | "EXECUTABLE"
    | "FILE_DROP"
    | "DNS_RESOLUTION"
    | "NETWORK_TRAFFIC";
}

export interface NotificationRuleOutput {
  /** ID of the notification rule */
  notification_rule_id: string;
  /** Owner of the notification rule */
  notification_rule_owner?: string;
  /** Resource id of the resource associated with the rule */
  notification_rule_resource_id?: string;
  /** Resource type of the resource associated with the rule */
  notification_rule_resource_type?: string;
  /** Subject of the notification rule */
  notification_rule_subject?: string;
  /** Event that will trigger the notification */
  notification_rule_trigger?: string;
}

/** List of Saml2 providers */
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
  listened?: boolean;
  /** @format date-time */
  organization_created_at: string;
  organization_description?: string;
  organization_id: string;
  organization_injects?: string[];
  /** @format int64 */
  organization_injects_number?: number;
  organization_name: string;
  organization_tags?: string[];
  /** @format date-time */
  organization_updated_at: string;
}

export interface OrganizationCreateInput {
  organization_description?: string;
  organization_name: string;
  organization_tags?: string[];
}

export interface OrganizationUpdateInput {
  organization_description?: string;
  organization_name: string;
  organization_tags?: string[];
}

export interface OutputParser {
  listened?: boolean;
  /** @uniqueItems true */
  output_parser_contract_output_elements: ContractOutputElement[];
  /** @format date-time */
  output_parser_created_at: string;
  output_parser_id: string;
  output_parser_mode: "STDOUT" | "STDERR" | "READ_FILE";
  output_parser_type: "REGEX";
  /** @format date-time */
  output_parser_updated_at: string;
}

/** Set of output parsers */
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

export interface PageCveSimple {
  content?: CveSimple[];
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

export interface PageNotificationRuleOutput {
  content?: NotificationRuleOutput[];
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

export type Payload = BasePayload &
  (
    | BasePayloadPayloadTypeMapping<"Command", Command>
    | BasePayloadPayloadTypeMapping<"Executable", Executable>
    | BasePayloadPayloadTypeMapping<"FileDrop", FileDrop>
    | BasePayloadPayloadTypeMapping<"DnsResolution", DnsResolution>
    | BasePayloadPayloadTypeMapping<"NetworkTraffic", NetworkTraffic>
  );

export interface PayloadArgument {
  default_value: string;
  description?: string | null;
  key: string;
  separator?: string | null;
  type: string;
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

export interface PayloadExportRequestInput {
  payloads?: PayloadExportTarget[];
}

export interface PayloadExportTarget {
  payload_id?: string;
}

export interface PayloadInput {
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
    | "TEXT"
    | "DOCUMENT"
    | "ARTICLE"
    | "CHALLENGE"
    | "MANUAL"
    | "PREVENTION"
    | "DETECTION"
    | "VULNERABILITY"
  )[];
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

export interface PayloadPrerequisite {
  check_command?: string;
  description?: string | null;
  executor: string;
  get_command: string;
}

export interface PayloadSimple {
  payload_collector_type?: string;
  payload_id?: string;
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
    | "TEXT"
    | "DOCUMENT"
    | "ARTICLE"
    | "CHALLENGE"
    | "MANUAL"
    | "PREVENTION"
    | "DETECTION"
    | "VULNERABILITY"
  )[];
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
  payload_domains: Domain[];
  payload_elevation_required?: boolean;
  payload_execution_arch?: "x86_64" | "arm64" | "ALL_ARCHITECTURES";
  payload_expectations: (
    | "TEXT"
    | "DOCUMENT"
    | "ARTICLE"
    | "CHALLENGE"
    | "MANUAL"
    | "PREVENTION"
    | "DETECTION"
    | "VULNERABILITY"
  )[];
  payload_external_id: string;
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
  payload_type: string;
}

export interface PayloadsDeprecateInput {
  collector_id: string;
  payload_external_ids: string[];
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
  /** Reply to mail to use by default for injects */
  default_reply_to?: string;
  /** List of enabled dev features */
  enabled_dev_features?: (
    | "_RESERVED"
    | "STIX_SECURITY_COVERAGE_FOR_VULNERABILITIES"
    | "LEGACY_INGESTION_EXECUTION_TRACE"
  )[];
  /** True if the Caldera Executor is enabled */
  executor_caldera_enable?: boolean;
  /** Url of the Caldera Executor */
  executor_caldera_public_url?: string;
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
  /** True if AI is enabled for the platform */
  platform_ai_enabled?: boolean;
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
  /** Default home dashboard of the platform */
  platform_home_dashboard?: string;
  /** id of the platform */
  platform_id?: string;
  /** Language of the platform */
  platform_lang: string;
  /** Platform licensing */
  platform_license?: License;
  /** Definition of the dark theme */
  platform_light_theme?: ThemeInput;
  /** Name of the platform */
  platform_name: string;
  /** List of OpenID providers */
  platform_openid_providers?: OAuthProvider[];
  /** Policies of the platform */
  platform_policies?: PolicyInput;
  /** Default scenario dashboard of the platform */
  platform_scenario_dashboard?: string;
  /** Default simulation dashboard of the platform */
  platform_simulation_dashboard?: string;
  /** Theme of the platform */
  platform_theme: string;
  /** Current version of the platform */
  platform_version?: string;
  /** 'true' if the platform has the whitemark activated */
  platform_whitemark?: string;
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
  /** XTM Hub last connectivity check */
  xtm_hub_last_connectivity_check?: string;
  /** True if xtmhub backend is reachable */
  xtm_hub_reachable?: boolean;
  /** XTM Hub registration date */
  xtm_hub_registration_date?: string;
  /** XTM Hub registration status */
  xtm_hub_registration_status?: string;
  /** XTM Hub registration user id */
  xtm_hub_registration_user_id?: string;
  /** XTM Hub registration user name */
  xtm_hub_registration_user_name?: string;
  /** XTM Hub should send connectivity email */
  xtm_hub_should_send_connectivity_email?: string;
  /** XTM Hub token */
  xtm_hub_token?: string;
  /** Url of XTM Hub */
  xtm_hub_url?: string;
  /** True if connection with OpenCTI is enabled */
  xtm_opencti_enable?: boolean;
  /** Url of OpenCTI */
  xtm_opencti_url?: string;
}

export interface PlayerInput {
  /** @pattern ^\+[\d\s\-.()]+$ */
  user_phone2?: string;
  user_country?: string;
  user_email: string;
  user_firstname?: string;
  user_lastname?: string;
  user_organization?: string;
  user_pgp_key?: string;
  /** @pattern ^\+[\d\s\-.()]+$ */
  user_phone?: string;
  user_tags?: string[];
  user_teams?: string[];
}

export interface PlayerOutput {
  user_phone2?: string;
  user_country?: string;
  user_email: string;
  user_firstname?: string;
  user_id: string;
  user_lastname?: string;
  user_organization?: string;
  user_pgp_key?: string;
  user_phone?: string;
  /** @uniqueItems true */
  user_tags?: string[];
}

export interface PlayerTarget {
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

/** Policies of the platform */
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

export interface PublicScenario {
  description?: string;
  id?: string;
  name?: string;
}

export interface RawAttackPattern {
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
  regex_group_field: string;
  regex_group_id: string;
  regex_group_index_values: string;
  /** @format date-time */
  regex_group_updated_at: string;
}

/** Set of regex groups */
export interface RegexGroupInput {
  /** Field */
  regex_group_field: string;
  regex_group_id?: string;
  /** Index of the group from the regex match: $index0$index1 */
  regex_group_index_values: string;
}

/** Represents the groups defined by the regex pattern. */
export interface RegexGroupSimple {
  /** Represents the field name of specific captured groups. */
  regex_group_field: string;
  regex_group_id: string;
  /** Represents the indexes of specific captured groups. */
  regex_group_index_values: string;
}

export interface RelatedEntityOutput {
  context?: string;
  id?: string;
  name?: string;
}

export interface RelatedFindingOutput {
  /**
   * Asset groups linked to endpoints
   * @uniqueItems true
   */
  finding_asset_groups?: AssetGroupSimple[];
  /**
   * Endpoint linked to finding
   * @uniqueItems true
   */
  finding_assets: EndpointSimple[];
  /** @format date-time */
  finding_created_at: string;
  /** Finding Id */
  finding_id: string;
  /** Inject linked to finding */
  finding_inject: InjectSimple;
  /** Scenario linked to inject */
  finding_scenario?: ScenarioSimple;
  finding_simulation?: ExerciseSimple;
  /**
   * Represents the data type being extracted.
   * @example "text, number, port, portscan, ipv4, ipv6, credentials, cve"
   */
  finding_type:
    | "text"
    | "number"
    | "port"
    | "portscan"
    | "ipv4"
    | "ipv6"
    | "credentials"
    | "cve";
  /** Finding Value */
  finding_value: string;
}

export interface Relationship {
  data: object;
}

export interface RenewTokenInput {
  token_id: string;
}

export interface Report {
  listened?: boolean;
  /** @format date-time */
  report_created_at: string;
  report_exercise?: string;
  report_global_observation?: string;
  report_id: string;
  report_informations?: ReportInformation[];
  report_injects_comments?: ReportInjectComment[];
  report_name: string;
  /** @format date-time */
  report_updated_at: string;
}

export interface ReportInformation {
  id: string;
  listened?: boolean;
  report: string;
  report_informations_display?: boolean;
  report_informations_type:
    | "MAIN_INFORMATION"
    | "SCORE_DETAILS"
    | "INJECT_RESULT"
    | "GLOBAL_OBSERVATION"
    | "PLAYER_SURVEYS"
    | "EXERCISE_DETAILS";
}

export interface ReportInformationInput {
  report_informations_display: boolean;
  report_informations_type:
    | "MAIN_INFORMATION"
    | "SCORE_DETAILS"
    | "INJECT_RESULT"
    | "GLOBAL_OBSERVATION"
    | "PLAYER_SURVEYS"
    | "EXERCISE_DETAILS";
}

export interface ReportInjectComment {
  inject_id?: string;
  report_id?: string;
  report_inject_comment?: string;
}

export interface ReportInjectCommentInput {
  inject_id: string;
  report_inject_comment?: string;
}

export interface ReportInput {
  report_global_observation?: string;
  report_informations?: ReportInformationInput[];
  report_name: string;
}

export interface ResetUserInput {
  lang?: string;
  login: string;
}

export interface ResourceObject {
  attributes?: Record<string, object>;
  id: string;
  relationships?: Record<string, Relationship>;
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
    | "MANAGE_TEAMS_AND_PLAYERS"
    | "DELETE_TEAMS_AND_PLAYERS"
    | "ACCESS_ASSETS"
    | "MANAGE_ASSETS"
    | "DELETE_ASSETS"
    | "ACCESS_PAYLOADS"
    | "MANAGE_PAYLOADS"
    | "DELETE_PAYLOADS"
    | "ACCESS_DASHBOARDS"
    | "MANAGE_DASHBOARDS"
    | "DELETE_DASHBOARDS"
    | "ACCESS_FINDINGS"
    | "MANAGE_FINDINGS"
    | "DELETE_FINDINGS"
    | "ACCESS_DOCUMENTS"
    | "MANAGE_DOCUMENTS"
    | "DELETE_DOCUMENTS"
    | "ACCESS_CHANNELS"
    | "MANAGE_CHANNELS"
    | "DELETE_CHANNELS"
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
    | "MANAGE_STIX_BUNDLE"
  )[];
  role_description?: string;
  role_name: string;
}

export interface RoleOutput {
  /** @uniqueItems true */
  role_capabilities?: string[];
  role_created_at?: string;
  role_description?: string;
  role_id: string;
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
  rule_attribute_name: string;
  /** @format date-time */
  rule_attribute_updated_at?: string;
}

export interface RuleAttributeAddInput {
  rule_attribute_additional_config?: Record<string, string>;
  rule_attribute_columns?: string | null;
  rule_attribute_default_value?: string;
  rule_attribute_name: string;
}

export interface RuleAttributeUpdateInput {
  rule_attribute_additional_config?: Record<string, string>;
  rule_attribute_columns?: string | null;
  rule_attribute_default_value?: string;
  rule_attribute_id?: string;
  rule_attribute_name: string;
}

export interface Scenario {
  listened?: boolean;
  /** @format int64 */
  scenario_all_users_number?: number;
  scenario_articles?: string[];
  scenario_category?: string;
  /** @format int64 */
  scenario_communications_number?: number;
  /** @format date-time */
  scenario_created_at: string;
  scenario_custom_dashboard?: string;
  scenario_dependencies?: "STARTERPACK"[];
  scenario_description?: string;
  scenario_documents?: string[];
  scenario_exercises?: string[];
  scenario_external_reference?: string;
  scenario_external_url?: string;
  scenario_id: string;
  scenario_injects?: string[];
  scenario_injects_statistics?: Record<string, number>;
  scenario_kill_chain_phases?: KillChainPhase[];
  scenario_lessons_anonymized?: boolean;
  scenario_lessons_categories?: string[];
  scenario_mail_from: string;
  scenario_mails_reply_to?: string[];
  scenario_main_focus?: string;
  scenario_message_footer?: string;
  scenario_message_header?: string;
  scenario_name: string;
  scenario_observers?: string[];
  scenario_planners?: string[];
  scenario_platforms?: (
    | "Linux"
    | "Windows"
    | "MacOS"
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
  /** @format date-time */
  scenario_updated_at: string;
  scenario_users?: string[];
  /** @format int64 */
  scenario_users_number?: number;
}

export interface ScenarioChallengesReader {
  scenario_challenges?: ChallengeInformation[];
  scenario_id?: string;
  scenario_information?: PublicScenario;
}

export interface ScenarioInput {
  scenario_category?: string;
  scenario_custom_dashboard?: string;
  scenario_description?: string;
  scenario_external_reference?: string;
  scenario_external_url?: string;
  scenario_mail_from?: string;
  scenario_mails_reply_to?: string[];
  scenario_main_focus?: string;
  scenario_message_footer?: string;
  scenario_message_header?: string;
  scenario_name: string;
  scenario_severity?: "low" | "medium" | "high" | "critical";
  scenario_subtitle?: string;
  scenario_tags?: string[];
}

export interface ScenarioRecurrenceInput {
  scenario_recurrence?: string;
  /** @format date-time */
  scenario_recurrence_end?: string;
  /** @format date-time */
  scenario_recurrence_start?: string;
}

/** Scenario linked to inject */
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

export interface ScenarioUpdateTagsInput {
  apply_tag_rule?: boolean;
  scenario_tags?: string[];
}

export interface ScenarioUpdateTeamsInput {
  scenario_teams?: string[];
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

export interface SecurityPlatform {
  /** @format date-time */
  asset_created_at: string;
  asset_description?: string;
  asset_external_reference?: string;
  asset_id: string;
  asset_name: string;
  asset_tags?: string[];
  asset_type?: string;
  /** @format date-time */
  asset_updated_at: string;
  listened?: boolean;
  security_platform_logo_dark?: string;
  security_platform_logo_light?: string;
  security_platform_traces?: InjectExpectationTrace[];
  security_platform_type: "EDR" | "XDR" | "SIEM" | "SOAR" | "NDR" | "ISPM";
}

export interface SecurityPlatformInput {
  asset_description?: string;
  asset_external_reference?: string;
  asset_name: string;
  asset_tags?: string[];
  security_platform_logo_dark?: string | null;
  security_platform_logo_light?: string | null;
  security_platform_type: "EDR" | "XDR" | "SIEM" | "SOAR" | "NDR" | "ISPM";
}

export interface SecurityPlatformUpsertInput {
  asset_description?: string;
  asset_external_reference?: string;
  asset_name: string;
  asset_tags?: string[];
  security_platform_logo_dark?: string;
  security_platform_logo_light?: string;
  security_platform_type: "EDR" | "XDR" | "SIEM" | "SOAR" | "NDR" | "ISPM";
}

export interface Series {
  /** Filter object to search within filterable attributes */
  filter?: FilterGroup;
  name?: string;
}

export interface SettingsEnterpriseEditionUpdateInput {
  /** cert of enterprise edition */
  platform_enterprise_license?: string;
}

export interface SettingsPlatformWhitemarkUpdateInput {
  /** The whitemark of the platform */
  platform_whitemark: string;
}

export interface SettingsUpdateInput {
  /** Default home dashboard of the platform */
  platform_home_dashboard?: string;
  /** Language of the platform */
  platform_lang: string;
  /** Name of the platform */
  platform_name: string;
  /** Default scenario dashboard of the platform */
  platform_scenario_dashboard?: string;
  /** Default simulation dashboard of the platform */
  platform_simulation_dashboard?: string;
  /** Theme of the platform */
  platform_theme: string;
}

export interface SimulationChallengesReader {
  exercise_challenges?: ChallengeInformation[];
  exercise_id?: string;
  exercise_information?: PublicExercise;
}

export interface SimulationDetails {
  /** @format int64 */
  exercise_all_users_number?: number;
  exercise_category?: string;
  /** @format int64 */
  exercise_communications_number?: number;
  /** @format date-time */
  exercise_created_at?: string;
  exercise_custom_dashboard?: string;
  exercise_description?: string;
  /** @format date-time */
  exercise_end_date?: string;
  exercise_id: string;
  exercise_kill_chain_phases?: KillChainPhase[];
  exercise_lessons_anonymized?: boolean;
  /** @format int64 */
  exercise_lessons_answers_number?: number;
  /** @format int64 */
  exercise_logs_number?: number;
  exercise_mail_from: string;
  exercise_mails_reply_to?: string[];
  exercise_main_focus?: string;
  exercise_message_footer?: string;
  exercise_message_header?: string;
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
}

export interface SimulationsResultsLatest {
  global_scores_by_expectation_type: Record<
    string,
    GlobalScoreBySimulationEndDate[]
  >;
}

/** List of sort fields : a field is composed of a property (for instance "label" and an optional direction ("asc" is assumed if no direction is specified) : ("desc", "asc") */
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
  document_id: string;
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

export type StructuralHistogramWidget = UtilRequiredKeys<
  WidgetConfiguration,
  "series" | "widget_configuration_type" | "time_range" | "date_attribute"
> & {
  display_legend?: boolean;
  field: string;
  /**
   * @format int32
   * @min 1
   */
  limit?: number;
  mode: string;
  stacked?: boolean;
};

export interface Tag {
  listened?: boolean;
  /** Color of the tag */
  tag_color?: string;
  /** ID of the tag */
  tag_id: string;
  /** Name of the tag */
  tag_name: string;
}

export interface TagCreateInput {
  /** Color of the tag */
  tag_color: string;
  /** Name of the tag */
  tag_name: string;
}

export interface TagRuleInput {
  /** Asset groups of the tag rule */
  asset_groups?: string[];
  /** Name of the tag */
  tag_name: string;
}

export interface TagRuleOutput {
  /** Asset groups of the tag rule */
  asset_groups?: Record<string, string>;
  /** Name of the tag associated with the tag rule */
  tag_name: string;
  /** ID of the tag rule */
  tag_rule_id: string;
}

export interface TagUpdateInput {
  /** Color of the tag */
  tag_color: string;
  /** Name of the tag */
  tag_name: string;
}

export interface TargetSimple {
  target_id: string;
  target_name?: string;
  target_type?:
    | "AGENT"
    | "ASSETS"
    | "ASSETS_GROUPS"
    | "PLAYERS"
    | "TEAMS"
    | "ENDPOINTS";
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
  /** ID of the team */
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
  /** Name of the team */
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

export interface TeamCreateInput {
  /** True if the team is contextual (exists only in the scenario/simulation it is linked to) */
  team_contextual?: boolean;
  /** Description of the team */
  team_description?: string;
  /** Id of the simulations linked to the team */
  team_exercises?: string[];
  /** Name of the team */
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
  /** ID of the team */
  team_id: string;
  /** Name of the team */
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
  /** Name of the team */
  team_name: string;
  /** ID of the organization of the team */
  team_organization?: string;
  /** IDs of the tags of the team */
  team_tags?: string[];
}

/** Definition of the dark theme */
export interface ThemeInput {
  /** Accent color of the theme */
  accent_color?: string;
  /** Background color of the theme */
  background_color?: string;
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

export interface Token {
  listened?: boolean;
  /** @format date-time */
  token_created_at: string;
  token_id: string;
  token_user?: string;
  token_value: string;
}

export interface UpdateAssetsOnAssetGroupInput {
  asset_group_assets?: string[];
}

export interface UpdateExerciseInput {
  apply_tag_rule?: boolean;
  exercise_category?: string;
  exercise_custom_dashboard?: string;
  exercise_description?: string;
  exercise_mail_from?: string;
  exercise_mails_reply_to?: string[];
  exercise_main_focus?: string;
  exercise_message_footer?: string;
  exercise_message_header?: string;
  exercise_name: string;
  exercise_severity?: string;
  exercise_subtitle?: string;
  exercise_tags?: string[];
}

export interface UpdateMePasswordInput {
  user_current_password: string;
  user_plain_password: string;
}

export interface UpdateNotificationRuleInput {
  subject: string;
}

export interface UpdateProfileInput {
  user_country?: string;
  user_email: string;
  user_firstname: string;
  user_lang: string;
  user_lastname: string;
  user_organization?: string;
  user_theme: string;
}

export interface UpdateScenarioInput {
  apply_tag_rule?: boolean;
  scenario_category?: string;
  scenario_custom_dashboard?: string;
  scenario_description?: string;
  scenario_external_reference?: string;
  scenario_external_url?: string;
  scenario_mail_from?: string;
  scenario_mails_reply_to?: string[];
  scenario_main_focus?: string;
  scenario_message_footer?: string;
  scenario_message_header?: string;
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

export interface UpdateUserInput {
  /**
   * Secondary phone of the user
   * @pattern ^\+[\d\s\-.()]+$
   */
  user_phone2?: string;
  /** True if the user is admin */
  user_admin?: boolean;
  /** The email of the user */
  user_email?: string;
  /** First name of the user */
  user_firstname?: string;
  /** Last name of the user */
  user_lastname?: string;
  /** Organization of the user */
  user_organization?: string;
  /** PGP key of the user */
  user_pgp_key?: string;
  /**
   * Phone of the user
   * @pattern ^\+[\d\s\-.()]+$
   */
  user_phone?: string;
  /** Tags of the user */
  user_tags?: string[];
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
  /** True if the user is admin */
  user_admin?: boolean;
  /** @uniqueItems true */
  user_capabilities?: (
    | "BYPASS"
    | "ACCESS_ASSESSMENT"
    | "MANAGE_ASSESSMENT"
    | "DELETE_ASSESSMENT"
    | "LAUNCH_ASSESSMENT"
    | "MANAGE_TEAMS_AND_PLAYERS"
    | "DELETE_TEAMS_AND_PLAYERS"
    | "ACCESS_ASSETS"
    | "MANAGE_ASSETS"
    | "DELETE_ASSETS"
    | "ACCESS_PAYLOADS"
    | "MANAGE_PAYLOADS"
    | "DELETE_PAYLOADS"
    | "ACCESS_DASHBOARDS"
    | "MANAGE_DASHBOARDS"
    | "DELETE_DASHBOARDS"
    | "ACCESS_FINDINGS"
    | "MANAGE_FINDINGS"
    | "DELETE_FINDINGS"
    | "ACCESS_DOCUMENTS"
    | "MANAGE_DOCUMENTS"
    | "DELETE_DOCUMENTS"
    | "ACCESS_CHANNELS"
    | "MANAGE_CHANNELS"
    | "DELETE_CHANNELS"
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
    | "MANAGE_STIX_BUNDLE"
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
  /** Email of the user */
  user_email: string;
  /** First name of the user */
  user_firstname?: string;
  user_grants?: Record<string, string>;
  /** Gravatar of the user */
  user_gravatar?: string;
  user_groups?: string[];
  /** User ID */
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

export interface UserOutput {
  /** True if the user is admin */
  user_admin?: boolean;
  /** Email of the user */
  user_email: string;
  /** First name of the user */
  user_firstname?: string;
  /** User ID */
  user_id: string;
  /** Last name of the user */
  user_lastname?: string;
  /** Organization of the user */
  user_organization_id?: string;
  /** Organization of the user */
  user_organization_name?: string;
  /**
   * Tags of the user
   * @uniqueItems true
   */
  user_tags?: string[];
}

/** Map of errors by input */
export interface ValidationContent {
  /** A list of errors */
  errors?: string[];
}

/** Errors raised */
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
  variable_id: string;
  /** @pattern ^[a-z_]+$ */
  variable_key: string;
  variable_scenario?: string;
  variable_type: "String" | "Object";
  /** @format date-time */
  variable_updated_at: string;
  variable_value?: string;
}

export interface VariableInput {
  variable_description?: string;
  /** @pattern ^[a-z_]+$ */
  variable_key: string;
  variable_value?: string;
}

export interface ViolationErrorBag {
  /** The error */
  error?: string;
  /** The message of the error */
  message?: string;
  /** The type of error */
  type?: string;
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
   * @exclusiveMin false
   * @max 10
   * @exclusiveMax false
   * @example 7.5
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
   * @example 7.8
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
   * @example "CVE-2024-0001"
   */
  vulnerability_external_id: string;
  /** Id */
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
   * @example 7.8
   */
  vulnerability_cvss_v31: number;
  /**
   * External Vulnerability identifier
   * @example "CVE-2024-0001"
   */
  vulnerability_external_id: string;
  /** Id */
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
    | DateHistogramWidget
    | FlatConfiguration
    | ListConfiguration
    | StructuralHistogramWidget;
  /** @format date-time */
  widget_created_at: string;
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
    | "number";
  /** @format date-time */
  widget_updated_at: string;
}

export interface WidgetConfiguration {
  date_attribute: string;
  end?: string;
  series: Series[];
  start?: string;
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
    | "list"
    | "temporal-histogram"
    | "structural-histogram";
}

export interface WidgetInput {
  widget_config:
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
    | "number";
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
  /** The values to filter the entities by */
  filter_values?: string[];
  /** Additional parameters for the widget */
  parameters?: Record<string, string>;
  /**
   * The index of the series to filter by, if applicable, otherwise 0
   * @format int32
   */
  series_index?: number;
}

export interface WidgetToEntitiesOutput {
  /** List of entities */
  es_entities?: EsBase[];
  list_configuration?: ListConfiguration;
}

export interface XtmHubRegisterInput {
  /** The registration token */
  token: string;
}
