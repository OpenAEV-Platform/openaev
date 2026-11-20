import { Alert, Button, Chip, Skeleton, Text } from '@filigran/design-system';
import { ExpandLess, ExpandMore } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useState } from 'react';

import { fdsGroupRowStyle, fdsTableFrameStyle } from '../../../../components/common/fdsTableSpec';
import { LIB_SURFACE_LAYER } from '../../../../components/common/libSurfaceBorder';
import { useFormatter } from '../../../../components/i18n';
import { type PayloadUsagesOutput, type PayloadVersionOutput } from '../../../../utils/api-types';
import useCountLabel from '../../../../utils/hooks/useCountLabel';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import AskAfterSavingChoice from './AskAfterSavingChoice';
import OlderVersionsAlert from './OlderVersionsAlert';
import PayloadUsageMoveBar from './usages/PayloadUsageMoveBar';
import PayloadUsageMoveResult from './usages/PayloadUsageMoveResult';
import PayloadUsagesTable from './usages/PayloadUsagesTable';
import { olderUsageCount, usageCount } from './usages/usageCounts';
import useUsageMove from './usages/useUsageMove';
import useFocusMove from './useFocusMove';
import useOlderVersionsKept from './useOlderVersionsKept';
import { versionRows } from './versionRows';

interface VersionProps {
  version: PayloadVersionOutput;
  versions: PayloadVersionOutput[];
  usages: PayloadUsagesOutput | null;
  canMove: boolean;
  onMoved: () => void;
  // Opened from the New version dialog: an older version in use opens with every usage ticked; the latest stays folded.
  focusMove: boolean;
}

const PayloadVersion: FunctionComponent<VersionProps> = ({ version, versions, usages, canMove, onMoved, focusMove }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const counted = useCountLabel();
  const [expanded, setExpanded] = useState(version.payload_is_latest && !focusMove);
  const move = useUsageMove({
    source: version,
    versions,
    usages,
    onMoved,
  });
  const row = useFocusMove({
    version,
    usages,
    active: focusMove && canMove,
    onFocus: (focused) => {
      setExpanded(true);
      move.selection.setSelectedElements(Object.fromEntries(focused.payload_usages.map(usage => [usage.usage_id, usage])));
    },
  });

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

interface NotInstalledProps {
  version: string;
  installing: boolean;
  // Without the right to install, the row only tells the version is not installed.
  onInstall?: (version: string) => void;
}

// A catalogue version not installed in the tenant: nothing runs it.
const NotInstalledVersion: FunctionComponent<NotInstalledProps> = ({ version, installing, onInstall }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  return (
    <div style={{
      ...fdsGroupRowStyle(theme),
      cursor: 'default',
    }}
    >
      <Text variant="content-base-bold">{`v${version}`}</Text>
      <Chip label={t('Not installed')} />
      <span style={{ flex: 1 }} />
      {onInstall && <Button priority="secondary" size="sm" loading={installing} onClick={() => onInstall(version)}>{t('Install')}</Button>}
    </div>
  );
};

interface Props {
  versions: PayloadVersionOutput[] | null;
  usages: Record<string, PayloadUsagesOutput> | null;
  // A move changes the usages of two versions: they are all loaded again.
  onMoved: () => void;
  notInstalled?: string[];
  focusMove?: boolean;
  // The catalogue version being installed.
  installing?: string | null;
  onInstall?: (version: string) => void;
}

const PayloadVersionsTab: FunctionComponent<Props> = ({ versions, usages, onMoved, notInstalled, focusMove = false, installing, onInstall }) => {
  const theme = useTheme();
  const canMove = useAbility().can(ACTIONS.MANAGE, SUBJECTS.THREAT_ARSENALS);
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
        {versionRows(versions, notInstalled).map(({ version, installed }) => (installed
          ? (
              <PayloadVersion
                key={installed.payload_id}
                version={installed}
                versions={versions}
                usages={usages?.[installed.payload_id] ?? null}
                canMove={canMove}
                onMoved={onMoved}
                focusMove={focusMove}
              />
            )
          : <NotInstalledVersion key={version} version={version} installing={installing === version} onInstall={onInstall} />))}
      </div>
      {canMove && <AskAfterSavingChoice />}
    </div>
  );
};

export default PayloadVersionsTab;
