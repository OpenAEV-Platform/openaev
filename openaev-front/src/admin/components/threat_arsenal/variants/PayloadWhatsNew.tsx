import { Text } from '@filigran/design-system';
import { type FunctionComponent, useEffect, useState } from 'react';

import { type VariantRebasePreview } from '../../../../actions/payloads/payload-variant-actions';
import { fetchThreatArsenalVersions } from '../../../../actions/threat_arsenals/threatArsenal-version-actions';
import { ChangesTable } from '../../../../components/common/content_versions/ContentVersionsTable';
import { useFormatter } from '../../../../components/i18n';
import { type PayloadVersionOutput } from '../../../../utils/api-types';
import { compareVersions } from '../../../../utils/versions';
import { changedFields } from './variantFields';

interface Props {
  baseActionId?: string;
  preview: VariantRebasePreview;
}

// What the newer Filigran versions bring: their release notes, then the fields they changed.
const PayloadWhatsNew: FunctionComponent<Props> = ({ baseActionId, preview }) => {
  const { t } = useFormatter();
  const [notes, setNotes] = useState<PayloadVersionOutput[]>([]);

  useEffect(() => {
    if (!baseActionId) return;
    fetchThreatArsenalVersions(baseActionId).then(({ data }: { data: PayloadVersionOutput[] }) => setNotes(data
      .filter(version => version.payload_changelog
        && compareVersions(version.payload_version, preview.base_version) > 0
        && compareVersions(version.payload_version, preview.target_version) <= 0)
      .sort((a, b) => compareVersions(b.payload_version, a.payload_version))));
  }, [baseActionId, preview.base_version, preview.target_version]);

  const changes = preview.blocks.flatMap(block => changedFields(block, 'catalog').map(field => ({
    field,
    before: block.base[field],
    after: block.catalog[field],
  })));

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: 12,
    }}
    >
      <Text variant="title-xs">{t('What\'s new in Filigran v{version}', { version: preview.target_version })}</Text>
      {notes.map(version => (
        <div
          key={version.payload_version}
          style={{
            display: 'grid',
            gridTemplateColumns: '64px minmax(0, 1fr)',
            gap: 12,
          }}
        >
          <Text variant="content-caption">{`v${version.payload_version}`}</Text>
          <Text variant="content-base">{version.payload_changelog}</Text>
        </div>
      ))}
      {changes.length > 0 && <ChangesTable changes={changes} />}
      {notes.length === 0 && changes.length === 0 && (
        <Text variant="content-caption">{t('No change in the fields you can customize.')}</Text>
      )}
    </div>
  );
};

export default PayloadWhatsNew;
