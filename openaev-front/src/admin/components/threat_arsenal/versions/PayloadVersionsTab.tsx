import { Chip, Skeleton, Text } from '@filigran/design-system';
import { ExpandLess, ExpandMore } from '@mui/icons-material';
import { Alert, Collapse } from '@mui/material';
import { type FunctionComponent, useState } from 'react';

import { FDS_GROUP_ROW_STYLE, FDS_TABLE_FRAME_STYLE } from '../../../../components/common/fdsTableSpec';
import { useFormatter } from '../../../../components/i18n';
import { type PayloadUsagesOutput, type PayloadVersionOutput } from '../../../../utils/api-types';
import PayloadUsagesTable from './PayloadUsagesTable';
import usePayloadVersions from './usePayloadVersions';

interface VersionProps {
  version: PayloadVersionOutput;
  usages?: PayloadUsagesOutput;
}

const PayloadVersion: FunctionComponent<VersionProps> = ({ version, usages }) => {
  const { t } = useFormatter();
  const [expanded, setExpanded] = useState(version.payload_is_latest);
  const count = usages ? usages.payload_usages.length + (usages.payload_usages_hidden_count ?? 0) : null;

  return (
    <div>
      <button type="button" style={FDS_GROUP_ROW_STYLE} aria-expanded={expanded} onClick={() => setExpanded(!expanded)}>
        <Text variant="content-base-bold">{`v${version.payload_version}`}</Text>
        {count !== null && (
          <Text variant="content-base" className="text-default-secondary">
            {`(${t('{count, plural, one {# inject} other {# injects}}', { count })})`}
          </Text>
        )}
        {version.payload_revoked && <Chip label={t('Revoked')} severity="critical" />}
        <span style={{ flex: 1 }} />
        {expanded ? <ExpandLess fontSize="small" /> : <ExpandMore fontSize="small" />}
      </button>
      <Collapse in={expanded} unmountOnExit>
        {version.payload_revoked && (
          <Alert severity="warning" sx={{ borderRadius: 0 }}>{t('These injects will fail.')}</Alert>
        )}
        {usages ? <PayloadUsagesTable usages={usages} /> : <Skeleton className="h-16" />}
      </Collapse>
    </div>
  );
};

interface Props { actionId: string }

const PayloadVersionsTab: FunctionComponent<Props> = ({ actionId }) => {
  const { versions, usages } = usePayloadVersions(actionId);

  if (versions === null) {
    return <Skeleton className="h-24" />;
  }
  return (
    <div style={FDS_TABLE_FRAME_STYLE}>
      {versions.map(version => <PayloadVersion key={version.payload_id} version={version} usages={usages[version.payload_id]} />)}
    </div>
  );
};

export default PayloadVersionsTab;
