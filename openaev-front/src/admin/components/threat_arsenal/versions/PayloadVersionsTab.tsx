import { Alert, Chip, Skeleton, Text } from '@filigran/design-system';
import { ExpandLess, ExpandMore } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect, useReducer, useRef, useState } from 'react';

import { fdsGroupRowStyle, fdsTableFrameStyle } from '../../../../components/common/fdsTableSpec';
import { LIB_SURFACE_LAYER } from '../../../../components/common/libSurfaceBorder';
import { useFormatter } from '../../../../components/i18n';
import { type PayloadVersionOutput } from '../../../../utils/api-types';
import useCountLabel from '../../../../utils/hooks/useCountLabel';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import AskAfterSavingChoice from './AskAfterSavingChoice';
import PayloadUsageMoveBar from './usages/PayloadUsageMoveBar';
import PayloadUsageMoveResult from './usages/PayloadUsageMoveResult';
import PayloadUsagesTable from './usages/PayloadUsagesTable';
import { usageCount } from './usages/usageCounts';
import useUsageMove from './usages/useUsageMove';
import useVersionUsages from './usages/useVersionUsages';

interface VersionProps {
  version: PayloadVersionOutput;
  versions: PayloadVersionOutput[];
  canMove: boolean;
  reloadKey: number;
  onMoved: () => void;
  // Opened from the New version dialog: an older version in use opens with every usage ticked; the latest stays folded.
  focusMove: boolean;
}

const PayloadVersion: FunctionComponent<VersionProps> = ({ version, versions, canMove, reloadKey, onMoved, focusMove }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const counted = useCountLabel();
  const usages = useVersionUsages(version.payload_action_id, reloadKey);
  const [expanded, setExpanded] = useState(version.payload_is_latest && !focusMove);
  const move = useUsageMove({
    source: version,
    versions,
    usages,
    onMoved,
  });
  const focused = useRef(false);
  const row = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (focusMove && canMove && !version.payload_is_latest && usages && usageCount(usages) > 0 && !focused.current) {
      focused.current = true;
      setExpanded(true);
      move.selection.setSelectedElements(Object.fromEntries(usages.payload_usages.map(usage => [usage.usage_id, usage])));
      row.current?.scrollIntoView({ block: 'nearest' });
    }
  }, [usages, focusMove]);

  return (
    <div ref={row}>
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

interface Props {
  versions: PayloadVersionOutput[] | null;
  focusMove?: boolean;
}

const PayloadVersionsTab: FunctionComponent<Props> = ({ versions, focusMove = false }) => {
  const theme = useTheme();
  const canMove = useAbility().can(ACTIONS.MANAGE, SUBJECTS.THREAT_ARSENALS);
  // A move changes the usages of two versions: each version loads its own again.
  const [reloadKey, reload] = useReducer((key: number) => key + 1, 0);

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
      {canMove && <AskAfterSavingChoice />}
      <div className={LIB_SURFACE_LAYER} style={fdsTableFrameStyle(theme)}>
        {versions.map(version => (
          <PayloadVersion
            key={version.payload_id}
            version={version}
            versions={versions}
            canMove={canMove}
            reloadKey={reloadKey}
            onMoved={reload}
            focusMove={focusMove}
          />
        ))}
      </div>
    </div>
  );
};

export default PayloadVersionsTab;
