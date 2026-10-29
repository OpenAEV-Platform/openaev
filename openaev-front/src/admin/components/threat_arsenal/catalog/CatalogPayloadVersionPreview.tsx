import { type FunctionComponent, useEffect, useMemo, useState } from 'react';

import { type AttackPatternHelper } from '../../../../actions/attack_patterns/attackpattern-helper';
import { fetchAttackPatterns } from '../../../../actions/AttackPattern';
import { fetchCatalogPayloadVersionContent } from '../../../../actions/catalog/payload-catalog-actions';
import { type DomainHelper } from '../../../../actions/domains/domain-helper';
import { useHelper } from '../../../../store';
import {
  type AttackPattern,
  type CatalogItemOutput,
  type Domain,
  type Payload,
  type ThreatArsenalAction,
} from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import ThreatArsenalActionOverview from '../ThreatArsenalActionOverview';
import CatalogPayloadVersionSummary, { CatalogVersionInstallBar } from './CatalogPayloadVersionSummary';

interface Props {
  item: CatalogItemOutput;
  canInstall: boolean;
  installing?: boolean;
  onInstall: () => void;
}

type OverviewProps = Parameters<typeof ThreatArsenalActionOverview>[0];

const strings = (value: unknown) => (Array.isArray(value) ? value.filter((v): v is string => typeof v === 'string') : []);

// A catalogue version not installed yet, shown like an installed one from its released file.
const CatalogPayloadVersionPreview: FunctionComponent<Props> = ({ item, canInstall, installing, onInstall }) => {
  const dispatch = useAppDispatch();
  useDataLoader(() => {
    dispatch(fetchAttackPatterns());
  });
  const { attackPatterns, allDomains } = useHelper((helper: AttackPatternHelper & DomainHelper) => ({
    attackPatterns: helper.getAttackPatterns(),
    allDomains: helper.getDomains(),
  }));
  const [content, setContent] = useState<Record<string, unknown> | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    fetchCatalogPayloadVersionContent(item.catalog_item_external_id, item.catalog_item_version)
      .then(setContent)
      .finally(() => setLoading(false));
  }, [item.catalog_item_external_id, item.catalog_item_version]);

  const shown = useMemo(() => {
    if (!content) return null;
    const techniques = strings(content.payload_attack_patterns).map(id => id.toUpperCase());
    const domainNames = Array.isArray(content.payload_domains)
      ? (content.payload_domains as { domain_name?: string }[]).map(domain => domain.domain_name)
      : [];
    const payload = {
      ...content,
      payload_id: '',
      payload_status: item.catalog_item_status,
    } as unknown as Payload;
    const action = {
      injector_contract_id: '',
      injector_contract_updated_at: item.catalog_item_published_at ?? '',
      action_labels: { en: String(content.payload_name ?? item.catalog_item_name ?? '') },
      action_author_name: item.catalog_item_author,
      action_platforms: strings(content.payload_platforms),
      action_attack_patterns_ids: (attackPatterns as AttackPattern[])
        .filter(pattern => techniques.includes(pattern.attack_pattern_external_id.toUpperCase()))
        .map(pattern => pattern.attack_pattern_id),
      action_domains_ids: (allDomains as Domain[])
        .filter(domain => domainNames.includes(domain.domain_name))
        .map(domain => domain.domain_id),
      action_payload: {
        payload_id: '',
        payload_type: content.payload_type,
        payload_status: item.catalog_item_status,
        payload_version: item.catalog_item_version,
      },
    } as unknown as ThreatArsenalAction;
    return {
      action,
      payload,
      expectations: strings(content.payload_expectations) as OverviewProps['expectations'],
      expectedSecurityPlatforms: content.payload_expected_security_platforms as OverviewProps['expectedSecurityPlatforms'],
    };
  }, [content, item, attackPatterns, allDomains]);

  if (!loading && !shown) {
    return <CatalogPayloadVersionSummary item={item} canInstall={canInstall} installing={installing} onInstall={onInstall} />;
  }
  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: 16,
    }}
    >
      <CatalogVersionInstallBar canInstall={canInstall} installing={installing} onInstall={onInstall} />
      {shown && (
        <ThreatArsenalActionOverview
          action={shown.action}
          payload={shown.payload}
          expectations={shown.expectations}
          expectedSecurityPlatforms={shown.expectedSecurityPlatforms}
          loading={false}
        />
      )}
    </div>
  );
};

export default CatalogPayloadVersionPreview;
