import type { Page } from '../../../../components/common/queryable/Page';
import type {
  AggregatedFindingOutput,
  Finding,
  FindingSummaryOutput,
  RelatedFindingOutput,
  SearchPaginationInput,
} from '../../../../utils/api-types';
import { resolveOCSFCloudPartition } from '../OCSFProvider';

interface ProwlerPrototypeContext {
  outcome: 'FAIL';
  ruleId: string;
  description: string;
  message: string;
  statusDetail: string;
  resourceName: string;
  resourceType: string;
  service: string;
  riskDetails: string;
  categories: string[];
  mitreAttack: {
    id: string;
    name: string;
    tactic: string;
  }[];
  evidenceMetadata: Record<string, string | number | boolean>;
}

interface PrototypeVulnerabilityContext {
  cvssScore: number;
  description: string;
  remediation: string;
}

interface PrototypeFindingRecord {
  list: AggregatedFindingOutput;
  detail: Finding;
  summary: FindingSummaryOutput;
  occurrences: RelatedFindingOutput[];
  context?: ProwlerPrototypeContext;
  vulnerability?: PrototypeVulnerabilityContext;
}

type FindingType = Finding['finding_type'];

const PROTOTYPE_ID_PREFIX = 'finding-prototype-';
const PROWLER_SOURCE = {
  injector_id: 'prowler-prototype-injector',
  injector_name: 'Prowler',
  injector_type: 'openaev_aws',
};
const RED_TEAM_SOURCE = {
  injector_id: 'red-team-prototype-injector',
  injector_name: 'Caldera',
  injector_type: 'openaev_caldera',
};
const PROWLER_SIMULATION = {
  exercise_global_score: [],
  exercise_id: 'prowler-prototype-simulation',
  exercise_name: 'Continuous AWS exposure assessment',
  exercise_start_date: '2026-08-25T07:00:00Z',
  exercise_status: 'RUNNING' as const,
};
const RED_TEAM_SIMULATION = {
  exercise_global_score: [],
  exercise_id: 'red-team-prototype-simulation',
  exercise_name: 'Operation Northstar',
  exercise_start_date: '2026-09-08T08:00:00Z',
  exercise_status: 'FINISHED' as const,
};
const PROWLER_SCENARIO = {
  scenario_id: 'prowler-prototype-scenario',
  scenario_name: 'AWS production posture validation',
};
const RED_TEAM_SCENARIO = {
  scenario_id: 'red-team-prototype-scenario',
  scenario_name: 'Enterprise intrusion assessment',
};

const createOccurrence = ({
  recordId,
  findingType,
  value,
  seenAt,
  injectId,
  injectTitle,
  assets,
  source,
  cloud,
}: {
  recordId: string;
  findingType: FindingType;
  value: string;
  seenAt: string;
  injectId: string;
  injectTitle: string;
  assets: AggregatedFindingOutput['finding_assets'];
  source: typeof PROWLER_SOURCE;
  cloud?: Pick<AggregatedFindingOutput, 'finding_cloud_account' | 'finding_cloud_provider' | 'finding_cloud_region' | 'finding_compliance' | 'finding_remediation' | 'finding_resource' | 'finding_severity'>;
}): RelatedFindingOutput => ({
  finding_id: `${recordId}-occurrence-${injectId}`,
  finding_type: findingType,
  finding_value: value,
  finding_created_at: seenAt,
  finding_updated_at: seenAt,
  finding_assets: assets,
  finding_asset_groups: [],
  finding_inject: {
    inject_id: injectId,
    inject_title: injectTitle,
  },
  finding_scenario: source === PROWLER_SOURCE ? PROWLER_SCENARIO : RED_TEAM_SCENARIO,
  finding_simulation: source === PROWLER_SOURCE ? PROWLER_SIMULATION : RED_TEAM_SIMULATION,
  finding_source: source,
  finding_triage_status: 'UNTRIAGED',
  ...cloud,
});

const createGenericRecord = ({
  type,
  value,
  field,
  assetName,
  firstSeen = '2026-09-10T09:15:00Z',
  vulnerability,
}: {
  type: Exclude<FindingType, 'ocsf'>;
  value: string;
  field: string;
  assetName: string;
  firstSeen?: string;
  vulnerability?: PrototypeVulnerabilityContext;
}): PrototypeFindingRecord => {
  const recordId = `${PROTOTYPE_ID_PREFIX}${type.replaceAll('_', '-')}`;
  const asset = {
    asset_id: `${recordId}-asset`,
    asset_name: assetName,
    asset_category: 'ENDPOINT',
    asset_type: 'Endpoint',
  };
  const injectId = `${recordId}-inject`;
  const injectTitle = `Northstar discovery · ${type.replaceAll('_', ' ')}`;
  const occurrences = [
    createOccurrence({
      recordId,
      findingType: type,
      value,
      seenAt: firstSeen,
      injectId,
      injectTitle,
      assets: [asset],
      source: RED_TEAM_SOURCE,
    }),
  ];

  return {
    list: {
      finding_id: recordId,
      finding_type: type,
      finding_value: value,
      finding_assets: [asset],
      finding_asset_groups: [],
      finding_source: RED_TEAM_SOURCE,
      finding_created_at: firstSeen,
      finding_updated_at: firstSeen,
      finding_triage_status: 'UNTRIAGED',
    },
    detail: {
      finding_id: recordId,
      finding_type: type,
      finding_value: value,
      finding_field: field,
      finding_name: value,
      finding_created_at: firstSeen,
      finding_updated_at: firstSeen,
      finding_assets: [asset.asset_id],
      finding_inject_id: injectId,
      finding_tags: ['prototype', 'red-team'],
    },
    summary: {
      finding_id: recordId,
      finding_type: type,
      finding_value: value,
      finding_occurrences: occurrences.length,
      finding_assets_count: 1,
      finding_asset_groups_count: 0,
      finding_teams_count: 0,
      finding_users_count: 0,
      finding_first_seen: firstSeen,
      finding_last_seen: firstSeen,
    },
    occurrences,
    vulnerability,
  };
};

const createProwlerRecord = ({
  id,
  title,
  ruleId,
  severity,
  resourceName,
  resourceUid,
  resourceType,
  account,
  region,
  service,
  detectionDates,
  resourceNames,
  message,
  statusDetail,
  description,
  riskDetails,
  categories,
  mitreAttack,
  evidenceMetadata,
  remediation,
  compliance,
}: {
  id: string;
  title: string;
  ruleId: string;
  severity: 'Critical' | 'High';
  resourceName: string;
  resourceUid: string;
  resourceType: string;
  account: string;
  region: string;
  service: string;
  detectionDates: string[];
  resourceNames: string[];
  message: string;
  statusDetail: string;
  description: string;
  riskDetails: string;
  categories: string[];
  mitreAttack: ProwlerPrototypeContext['mitreAttack'];
  evidenceMetadata: ProwlerPrototypeContext['evidenceMetadata'];
  remediation: string;
  compliance: string;
}): PrototypeFindingRecord => {
  const findingId = `prowler-prototype-${id}`;
  const firstSeen = detectionDates[0];
  const lastSeen = detectionDates[detectionDates.length - 1];
  const assets = resourceNames.map((name, index) => ({
    asset_id: `${findingId}-resource-${index + 1}`,
    asset_name: name,
    asset_category: 'CLOUD_RESOURCE',
    asset_type: resourceType,
  }));
  const resources = resourceNames.map((name, index) => ({
    name,
    uid: index === 0 ? resourceUid : `${resourceUid}#${name}`,
    type: resourceType,
    cloud_partition: 'aws',
    region,
    data: { metadata: { arn: index === 0 ? resourceUid : `${resourceUid}#${name}` } },
  }));
  const cloudProvider = resolveOCSFCloudPartition(resources);
  const rawResponse = {
    finding_info: {
      title,
      uid: ruleId,
    },
    status_code: 'FAIL',
    status_detail: statusDetail,
    message,
    severity,
    resources,
    cloud: {
      account: { uid: account },
      region,
    },
    risk_details: riskDetails,
    category_name: categories,
    mitre_attack: mitreAttack,
    metadata: evidenceMetadata,
  };
  const cloud = {
    finding_cloud_provider: cloudProvider,
    finding_cloud_account: account,
    finding_cloud_region: region,
    finding_compliance: compliance,
    finding_resource: resourceUid,
    finding_severity: severity,
    finding_remediation: remediation,
  };
  const occurrences = detectionDates.map((seenAt, index) => createOccurrence({
    recordId: findingId,
    findingType: 'ocsf',
    value: title,
    seenAt,
    injectId: `${findingId}-scan-${index + 1}`,
    injectTitle: `Prowler AWS production scan · ${seenAt.slice(0, 10)}`,
    assets,
    source: PROWLER_SOURCE,
    cloud,
  }));
  const latestOccurrence = occurrences[occurrences.length - 1];

  return {
    list: {
      finding_id: findingId,
      finding_type: 'ocsf',
      finding_value: title,
      finding_assets: assets,
      finding_asset_groups: [],
      finding_source: PROWLER_SOURCE,
      finding_created_at: firstSeen,
      finding_updated_at: lastSeen,
      finding_triage_status: 'UNTRIAGED',
      ...cloud,
    },
    detail: {
      finding_id: findingId,
      finding_type: 'ocsf',
      finding_value: title,
      finding_field: ruleId,
      finding_name: title,
      finding_created_at: firstSeen,
      finding_updated_at: lastSeen,
      finding_assets: assets.map(asset => asset.asset_id),
      finding_inject_id: latestOccurrence.finding_inject.inject_id,
      finding_tags: categories,
      finding_raw_data: JSON.stringify(rawResponse),
      ...cloud,
    },
    summary: {
      finding_id: findingId,
      finding_type: 'ocsf',
      finding_value: title,
      finding_occurrences: occurrences.length,
      finding_assets_count: resourceNames.length,
      finding_asset_groups_count: 0,
      finding_teams_count: 0,
      finding_users_count: 0,
      finding_first_seen: firstSeen,
      finding_last_seen: lastSeen,
    },
    occurrences,
    context: {
      outcome: 'FAIL',
      ruleId,
      description,
      message,
      statusDetail,
      resourceName,
      resourceType,
      service,
      riskDetails,
      categories,
      mitreAttack,
      evidenceMetadata: {
        ...evidenceMetadata,
        cloud_partitions: resources.map(resource => resource.cloud_partition).join(', '),
      },
    },
  };
};

const PROWLER_PROTOTYPE_RECORDS: PrototypeFindingRecord[] = [
  createProwlerRecord({
    id: 's3-public-access',
    title: 'S3 bucket allows public access',
    ruleId: 's3_bucket_public_access_block',
    severity: 'Critical',
    resourceName: 'acme-prod-customer-exports',
    resourceUid: 'arn:aws:s3:::acme-prod-customer-exports',
    resourceType: 'AWS::S3::Bucket',
    account: 'prod-security (123456789012)',
    region: 'eu-west-1',
    service: 'S3',
    detectionDates: ['2026-09-03T07:14:22Z', '2026-09-07T07:18:41Z', '2026-09-11T12:31:09Z'],
    resourceNames: ['acme-prod-customer-exports', 'acme-marketing-assets'],
    message: 'Block public access is not fully enabled for the bucket.',
    statusDetail: 'BlockPublicAcls=false, IgnorePublicAcls=false, BlockPublicPolicy=false, RestrictPublicBuckets=false',
    description: 'The S3 bucket permits public access through its access-control configuration.',
    riskDetails: 'An unauthenticated attacker can enumerate or retrieve exposed objects, potentially leaking customer exports and internal artifacts.',
    categories: ['Data exposure', 'Cloud misconfiguration', 'Initial access'],
    mitreAttack: [
      {
        id: 'T1530',
        name: 'Data from Cloud Storage',
        tactic: 'Collection',
      },
      {
        id: 'T1580',
        name: 'Cloud Infrastructure Discovery',
        tactic: 'Discovery',
      },
    ],
    evidenceMetadata: {
      bucket_policy_status: 'Public',
      block_public_acls: false,
      ignore_public_acls: false,
      block_public_policy: false,
      restrict_public_buckets: false,
    },
    remediation: 'Enable all four S3 Block Public Access controls at bucket and account level, then remove public ACL and bucket-policy statements.',
    compliance: 'CIS AWS Foundations 2.1.5',
  }),
  createProwlerRecord({
    id: 'iam-user-mfa',
    title: 'IAM console user does not have MFA enabled',
    ruleId: 'iam_user_mfa_enabled_console_access',
    severity: 'High',
    resourceName: 'deploy-operator',
    resourceUid: 'arn:aws:iam::123456789012:user/deploy-operator',
    resourceType: 'AWS::IAM::User',
    account: 'prod-security (123456789012)',
    region: 'global',
    service: 'IAM',
    detectionDates: ['2026-08-26T15:08:44Z', '2026-09-02T07:16:10Z', '2026-09-11T12:31:18Z'],
    resourceNames: ['deploy-operator'],
    message: 'IAM user deploy-operator can sign in to the console without MFA.',
    statusDetail: 'Password enabled: true; MFA devices: 0; access keys: 1 active',
    description: 'A console-enabled IAM user has no active multi-factor authentication device.',
    riskDetails: 'Stolen credentials provide direct console access to a privileged production identity without a second authentication factor.',
    categories: ['Credential access', 'Privilege escalation', 'Identity and access management'],
    mitreAttack: [
      {
        id: 'T1078.004',
        name: 'Valid Accounts: Cloud Accounts',
        tactic: 'Initial Access, Persistence, Privilege Escalation, Defense Evasion',
      },
      {
        id: 'T1098',
        name: 'Account Manipulation',
        tactic: 'Persistence, Privilege Escalation',
      },
    ],
    evidenceMetadata: {
      password_enabled: true,
      mfa_active: false,
      access_keys_active: 1,
      administrator_access: true,
      password_last_used: '2026-09-10T18:23:02Z',
    },
    remediation: 'Enroll the user in phishing-resistant MFA. Validate that automation uses a role and remove console access if it is not required.',
    compliance: 'CIS AWS Foundations 1.10',
  }),
  createProwlerRecord({
    id: 'ec2-ssh-open',
    title: 'EC2 security group exposes SSH to the Internet',
    ruleId: 'ec2_securitygroup_allow_ingress_from_internet_to_tcp_port_22',
    severity: 'High',
    resourceName: 'prod-bastion-sg',
    resourceUid: 'arn:aws:ec2:eu-central-1:123456789012:security-group/sg-0a12bc34de56f7890',
    resourceType: 'AWS::EC2::SecurityGroup',
    account: 'prod-security (123456789012)',
    region: 'eu-central-1',
    service: 'EC2',
    detectionDates: ['2026-09-01T11:55:04Z', '2026-09-05T07:19:26Z', '2026-09-08T07:21:12Z', '2026-09-11T12:31:31Z'],
    resourceNames: ['prod-bastion-sg', 'legacy-admin-sg', 'sandbox-default-sg'],
    message: 'Security group prod-bastion-sg allows inbound TCP/22 from 0.0.0.0/0.',
    statusDetail: 'IpProtocol=tcp, FromPort=22, ToPort=22, IPv4 range=0.0.0.0/0',
    description: 'An EC2 security group allows SSH ingress from every IPv4 address.',
    riskDetails: 'Internet-wide SSH exposure increases brute-force and credential-stuffing attack surface and may provide an initial foothold on attached workloads.',
    categories: ['Network exposure', 'Initial access', 'Cloud misconfiguration'],
    mitreAttack: [
      {
        id: 'T1133',
        name: 'External Remote Services',
        tactic: 'Persistence, Initial Access',
      },
      {
        id: 'T1110',
        name: 'Brute Force',
        tactic: 'Credential Access',
      },
    ],
    evidenceMetadata: {
      security_group_id: 'sg-0a12bc34de56f7890',
      protocol: 'tcp',
      from_port: 22,
      to_port: 22,
      source_ipv4: '0.0.0.0/0',
      attached_network_interfaces: 2,
    },
    remediation: 'Restrict TCP/22 to approved corporate or VPN CIDRs. Prefer AWS Systems Manager Session Manager and remove SSH ingress where possible.',
    compliance: 'CIS AWS Foundations 5.2',
  }),
];

const GENERIC_PROTOTYPE_RECORDS: PrototypeFindingRecord[] = [
  createGenericRecord({
    type: 'text',
    value: 'Windows Defender real-time protection disabled',
    field: 'status_message',
    assetName: 'FIN-WS-042',
  }),
  createGenericRecord({
    type: 'action_output',
    value: 'Credential access simulation completed with detections',
    field: 'stdout',
    assetName: 'FIN-WS-042',
  }),
  createGenericRecord({
    type: 'number',
    value: '47',
    field: 'failed_logon_count',
    assetName: 'AD-DC-01',
  }),
  createGenericRecord({
    type: 'port',
    value: '3389/tcp',
    field: 'listening_port',
    assetName: 'WIN-JUMP-01',
  }),
  createGenericRecord({
    type: 'portscan',
    value: '22/tcp, 80/tcp, 443/tcp',
    field: 'open_ports',
    assetName: 'DMZ-WEB-01',
  }),
  createGenericRecord({
    type: 'ipv4',
    value: '10.20.30.45',
    field: 'discovered_address',
    assetName: 'CORP-NETWORK',
  }),
  createGenericRecord({
    type: 'ipv6',
    value: '2001:db8:85a3::8a2e:370:7334',
    field: 'discovered_address',
    assetName: 'EDGE-GW-01',
  }),
  createGenericRecord({
    type: 'credentials',
    value: 'svc-backup / demo-password-captured',
    field: 'credential_pair',
    assetName: 'AD-DC-01',
  }),
  createGenericRecord({
    type: 'cve',
    value: 'CVE-2024-3094',
    field: 'package_vulnerability',
    assetName: 'LINUX-BUILD-01',
    vulnerability: {
      cvssScore: 10,
      description: 'Malicious code was discovered in the upstream xz/liblzma release chain, enabling an authentication bypass under affected configurations.',
      remediation: 'Remove affected xz versions and update to a vendor-confirmed safe package release.',
    },
  }),
  createGenericRecord({
    type: 'username',
    value: 'j.smith',
    field: 'domain_user',
    assetName: 'AD-DC-01',
  }),
  createGenericRecord({
    type: 'email',
    value: 'finance-admin@acme.example',
    field: 'mailbox',
    assetName: 'M365-TENANT',
  }),
  createGenericRecord({
    type: 'share',
    value: '\\\\fileserver\\finance',
    field: 'network_share',
    assetName: 'FILESERVER-01',
  }),
  createGenericRecord({
    type: 'file',
    value: 'C:\\Users\\Public\\beacon.exe',
    field: 'dropped_file',
    assetName: 'FIN-WS-042',
  }),
  createGenericRecord({
    type: 'admin_username',
    value: 'domain-admin-temp',
    field: 'privileged_user',
    assetName: 'AD-DC-01',
  }),
  createGenericRecord({
    type: 'group',
    value: 'Domain Admins',
    field: 'privileged_group',
    assetName: 'AD-DC-01',
  }),
  createGenericRecord({
    type: 'computer',
    value: 'FIN-WS-042.acme.local',
    field: 'domain_computer',
    assetName: 'AD-DC-01',
  }),
  createGenericRecord({
    type: 'password_policy',
    value: 'Minimum password length: 8',
    field: 'domain_password_policy',
    assetName: 'AD-DC-01',
  }),
  createGenericRecord({
    type: 'delegation',
    value: 'websvc → cifs/DC01.acme.local',
    field: 'constrained_delegation',
    assetName: 'AD-DC-01',
  }),
  createGenericRecord({
    type: 'sid',
    value: 'S-1-5-21-3623811015-3361044348-30300820-512',
    field: 'security_identifier',
    assetName: 'AD-DC-01',
  }),
  createGenericRecord({
    type: 'vulnerability',
    value: 'SMB signing is not required',
    field: 'scanner_finding',
    assetName: 'FILESERVER-01',
  }),
  createGenericRecord({
    type: 'account_with_password_not_required',
    value: 'legacy-scanner',
    field: 'user_account_control',
    assetName: 'AD-DC-01',
  }),
  createGenericRecord({
    type: 'asreproastable_account',
    value: 'svc-reporting',
    field: 'asrep_account',
    assetName: 'AD-DC-01',
  }),
  createGenericRecord({
    type: 'kerberoastable_account',
    value: 'svc-sql-prod',
    field: 'service_principal_account',
    assetName: 'AD-DC-01',
  }),
  createGenericRecord({
    type: 'expectation_signature',
    value: 'sha256:53cb2f7b95e1126d04299bdf910a68d7eec8b632b43ce47f5eb80a5199b22f0d',
    field: 'artifact_signature',
    assetName: 'FIN-WS-042',
  }),
];

const PROTOTYPE_RECORDS = [...PROWLER_PROTOTYPE_RECORDS, ...GENERIC_PROTOTYPE_RECORDS];
const recordById = new Map(PROTOTYPE_RECORDS.map(record => [record.detail.finding_id, record]));

export const getProwlerPrototypeRecord = (findingId: string): PrototypeFindingRecord | undefined => (
  import.meta.env.DEV ? recordById.get(findingId) : undefined
);

const hasOnlyArchiveFilter = (input: SearchPaginationInput): boolean => (
  (input.filterGroup?.filters ?? []).every(filter => filter.key === 'finding_archived')
);

const includesPrototype = (input: SearchPaginationInput): boolean => (
  import.meta.env.DEV
    && !input.textSearch
    && hasOnlyArchiveFilter(input)
    && (input.filterGroup?.filters ?? []).every(filter => !filter.values?.includes('true'))
);

export const getProwlerPrototypeSearchInput = (input: SearchPaginationInput): SearchPaginationInput => (
  includesPrototype(input)
    ? {
        ...input,
        page: 0,
        size: (input.page + 1) * input.size,
      }
    : input
);

export const addProwlerPrototypeFindings = (
  response: { data: Page<AggregatedFindingOutput> },
  input: SearchPaginationInput,
): { data: Page<AggregatedFindingOutput> } => {
  if (!includesPrototype(input)) {
    return response;
  }

  const mockFindings = PROTOTYPE_RECORDS.map(record => record.list);
  const offset = input.page * input.size;
  const content = [...mockFindings, ...response.data.content].slice(offset, offset + input.size);
  const totalElements = response.data.totalElements + mockFindings.length;
  const totalPages = Math.ceil(totalElements / input.size);

  return {
    data: {
      ...response.data,
      content,
      empty: content.length === 0,
      first: input.page === 0,
      last: input.page >= totalPages - 1,
      number: input.page,
      numberOfElements: content.length,
      pageable: {
        ...response.data.pageable,
        offset,
        pageNumber: input.page,
        pageSize: input.size,
      },
      size: input.size,
      totalElements,
      totalPages,
    },
  };
};

export const searchProwlerPrototypeOccurrences = (
  findingId: string,
  input: SearchPaginationInput,
): Promise<{ data: Page<RelatedFindingOutput> }> => {
  const record = getProwlerPrototypeRecord(findingId);
  const allOccurrences = record?.occurrences ?? [];
  const offset = input.page * input.size;
  const content = [...allOccurrences]
    .sort((left, right) => right.finding_updated_at.localeCompare(left.finding_updated_at))
    .slice(offset, offset + input.size);
  const totalPages = Math.ceil(allOccurrences.length / input.size);
  const sort = {
    empty: false,
    sorted: true,
    unsorted: false,
  };

  return Promise.resolve({
    data: {
      content,
      empty: content.length === 0,
      first: input.page === 0,
      last: input.page >= totalPages - 1,
      number: input.page,
      numberOfElements: content.length,
      pageable: {
        offset,
        pageNumber: input.page,
        pageSize: input.size,
        paged: true,
        sort,
        unpaged: false,
      },
      size: input.size,
      sort,
      totalElements: allOccurrences.length,
      totalPages,
    },
  });
};
