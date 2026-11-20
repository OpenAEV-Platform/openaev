import { Alert, Chip, Skeleton, Text } from '@filigran/design-system';
import { ExpandLess, ExpandMore } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useState } from 'react';

import { fdsGroupRowStyle, fdsTableFrameStyle } from '../../../../components/common/fdsTableSpec';
import { LIB_SURFACE_LAYER } from '../../../../components/common/libSurfaceBorder';
import { useFormatter } from '../../../../components/i18n';
import { type PayloadVersionOutput } from '../../../../utils/api-types';
import useCountLabel from '../../../../utils/hooks/useCountLabel';
import PayloadUsagesTable from './usages/PayloadUsagesTable';
import { usageCount } from './usages/usageCounts';
import useVersionUsages from './usages/useVersionUsages';

interface VersionProps { version: PayloadVersionOutput }

const PayloadVersion: FunctionComponent<VersionProps> = ({ version }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const counted = useCountLabel();
  const usages = useVersionUsages(version.payload_action_id);
  const [expanded, setExpanded] = useState(version.payload_is_latest);

  return (
    <div>
      <button type="button" style={fdsGroupRowStyle(theme)} aria-expanded={expanded} onClick={() => setExpanded(!expanded)}>
        <Text variant="content-base-bold">{`v${version.payload_version}`}</Text>
        {usages && (
          <Text variant="content-base" className="text-default-secondary">
            {`(${counted(usageCount(usages), '1 inject', '{count} injects')})`}
          </Text>
        )}
        {version.payload_revoked && <Chip label={t('Revoked')} severity="critical" />}
        <span style={{ flex: 1 }} />
        {expanded ? <ExpandLess fontSize="small" /> : <ExpandMore fontSize="small" />}
      </button>
      {expanded && (
        <>
          {version.payload_revoked && <Alert severity="warning" title={t('These injects will fail.')} />}
          {usages ? <PayloadUsagesTable usages={usages} /> : <Skeleton className="h-16" />}
        </>
      )}
    </div>
  );
};

interface Props { versions: PayloadVersionOutput[] | null }

const PayloadVersionsTab: FunctionComponent<Props> = ({ versions }) => {
  const theme = useTheme();
  if (versions === null) {
    return <Skeleton className="h-24" />;
  }
  return (
    <div className={LIB_SURFACE_LAYER} style={fdsTableFrameStyle(theme)}>
      {versions.map(version => <PayloadVersion key={version.payload_id} version={version} />)}
    </div>
  );
};

export default PayloadVersionsTab;
