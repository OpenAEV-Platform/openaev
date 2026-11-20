import { Alert, Chip, Skeleton, Text } from '@filigran/design-system';
import { ExpandLess, ExpandMore } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useReducer, useState } from 'react';

import { fdsGroupRowStyle, fdsTableFrameStyle } from '../../../../components/common/fdsTableSpec';
import { LIB_SURFACE_LAYER } from '../../../../components/common/libSurfaceBorder';
import { useFormatter } from '../../../../components/i18n';
import { type PayloadUsagesOutput, type PayloadVersionOutput } from '../../../../utils/api-types';
import useCountLabel from '../../../../utils/hooks/useCountLabel';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import OlderVersionsAlert from './OlderVersionsAlert';
import PayloadUsageMoveBar from './usages/PayloadUsageMoveBar';
import PayloadUsageMoveResult from './usages/PayloadUsageMoveResult';
import PayloadUsagesTable from './usages/PayloadUsagesTable';
import { olderUsageCount, usageCount } from './usages/usageCounts';
import useUsageMove from './usages/useUsageMove';
import useUsagesByVersion from './usages/useUsagesByVersion';
import useOlderVersionsKept from './useOlderVersionsKept';

interface VersionProps {
  version: PayloadVersionOutput;
  versions: PayloadVersionOutput[];
  usages: PayloadUsagesOutput | null;
  canMove: boolean;
  onMoved: () => void;
}

const PayloadVersion: FunctionComponent<VersionProps> = ({ version, versions, usages, canMove, onMoved }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const counted = useCountLabel();
  const [expanded, setExpanded] = useState(version.payload_is_latest);
  const move = useUsageMove({
    source: version,
    versions,
    usages,
    onMoved,
  });

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
          {version.payload_revoked && <Alert severity="warning" title={t('These injects will fail. Move them to another version.')} />}
          <PayloadUsageMoveResult move={move} />
          {usages
            ? <PayloadUsagesTable usages={usages} selection={canMove ? move.selection : undefined} />
            : <Skeleton className="h-16" />}
          {canMove && (usages?.payload_usages.length ?? 0) > 0 && <PayloadUsageMoveBar move={move} />}
        </>
      )}
    </div>
  );
};

interface Props { versions: PayloadVersionOutput[] | null }

const PayloadVersionsTab: FunctionComponent<Props> = ({ versions }) => {
  const theme = useTheme();
  const canMove = useAbility().can(ACTIONS.MANAGE, SUBJECTS.THREAT_ARSENALS);
  // A move changes the usages of two versions: they are all loaded again.
  const [reloadKey, reload] = useReducer((key: number) => key + 1, 0);
  const usages = useUsagesByVersion(versions, reloadKey);
  const latest = versions?.find(version => version.payload_is_latest);
  const { kept, toggle } = useOlderVersionsKept(latest);
  const olderUsages = versions && usages ? olderUsageCount(versions, latest, usages) : 0;

  if (versions === null) {
    return <Skeleton className="h-24" />;
  }
  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: theme.spacing(2),
    }}
    >
      {olderUsages > 0 && latest && (
        <OlderVersionsAlert usageCount={olderUsages} kept={kept} onToggle={canMove ? toggle : undefined} />
      )}
      <div className={LIB_SURFACE_LAYER} style={fdsTableFrameStyle(theme)}>
        {versions.map(version => (
          <PayloadVersion
            key={version.payload_id}
            version={version}
            versions={versions}
            usages={usages?.[version.payload_id] ?? null}
            canMove={canMove}
            onMoved={reload}
          />
        ))}
      </div>
    </div>
  );
};

export default PayloadVersionsTab;
