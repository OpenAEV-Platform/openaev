import { type FunctionComponent, useEffect, useState } from 'react';

import { type AttackPatternHelper } from '../../../../actions/attack_patterns/attackpattern-helper';
import { fetchCatalogPayloadVersionContent } from '../../../../actions/catalog/payload-catalog-actions';
import { type DomainHelper } from '../../../../actions/domains/domain-helper';
import { useHelper } from '../../../../store';
import {
  type AttackPattern,
  type CatalogPayloadOutput,
  type Domain,
  type Payload,
  type PayloadUpsertInput,
  type ThreatArsenalAction,
} from '../../../../utils/api-types';
import ThreatArsenalActionOverview from '../ThreatArsenalActionOverview';

// The catalogue offers verified versions only, and installs them as verified.
const OFFERED_STATUS = 'VERIFIED';

interface Props { version: CatalogPayloadOutput }

const toPayload = (content: PayloadUpsertInput, publishedAt: string) => ({
  payload_id: '',
  payload_created_at: publishedAt,
  payload_updated_at: publishedAt,
  payload_source: content.payload_source,
  payload_name: content.payload_name,
  payload_description: content.payload_description,
  payload_type: content.payload_type,
  payload_status: OFFERED_STATUS,
  payload_platforms: content.payload_platforms ?? [],
  payload_execution_arch: content.payload_execution_arch,
  payload_external_id: content.payload_external_id,
  payload_arguments: content.payload_arguments,
  payload_prerequisites: content.payload_prerequisites,
  payload_cleanup_command: content.payload_cleanup_command,
  payload_cleanup_executor: content.payload_cleanup_executor,
  payload_expectations: content.payload_expectations,
  payload_expected_security_platforms: content.payload_expected_security_platforms,
  command_content: content.command_content,
  command_executor: content.command_executor,
  dns_resolution_hostname: content.dns_resolution_hostname,
  executable_file: content.executable_file,
  file_drop_file: content.file_drop_file,
} as Payload);

// A catalogue version not installed yet, shown as the Active tab shows an installed one, from its released file.
const CatalogPayloadVersionPreview: FunctionComponent<Props> = ({ version }) => {
  const { attackPatterns, domains }: {
    attackPatterns: AttackPattern[];
    domains: Domain[];
  } = useHelper((helper: AttackPatternHelper & DomainHelper) => ({
    attackPatterns: helper.getAttackPatterns(),
    domains: helper.getDomains(),
  }));
  const publishedAt = version.catalog_payload_published_at ?? '';
  const [content, setContent] = useState<PayloadUpsertInput | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setContent(null);
    setLoading(true);
    fetchCatalogPayloadVersionContent(version.catalog_payload_external_id, version.catalog_payload_version)
      .then(setContent)
      .catch(() => setContent(null))
      .finally(() => setLoading(false));
  }, [version.catalog_payload_external_id, version.catalog_payload_version]);

  const techniques = (content?.payload_attack_patterns ?? []).map(id => id.toUpperCase());
  const domainNames = (content?.payload_domains ?? []).map(domain => domain.domain_name);
  const action: Omit<ThreatArsenalAction, 'action_grant_resource_id'> = {
    injector_contract_id: '',
    injector_contract_updated_at: publishedAt,
    action_labels: { en: version.catalog_payload_name ?? version.catalog_payload_external_id },
    action_platforms: content?.payload_platforms,
    action_attack_patterns_ids: attackPatterns
      .filter(pattern => techniques.includes(pattern.attack_pattern_external_id.toUpperCase()))
      .map(pattern => pattern.attack_pattern_id),
    action_domains_ids: domains
      .filter(domain => domainNames.includes(domain.domain_name))
      .map(domain => domain.domain_id),
    action_payload: {
      payload_type: content?.payload_type,
      payload_status: OFFERED_STATUS,
    },
  };

  return (
    <ThreatArsenalActionOverview
      action={action}
      payload={content && toPayload(content, publishedAt)}
      loading={loading}
    />
  );
};

export default CatalogPayloadVersionPreview;
