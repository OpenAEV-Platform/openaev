import { Alert, Button, Chip, Skeleton, Text } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect, useState } from 'react';

import { fdsGroupRowStyle, fdsTableFrameStyle } from '../../../../components/common/fdsTableSpec';
import FoldRow from '../../../../components/common/FoldRow';
import { LIB_SURFACE_LAYER } from '../../../../components/common/libSurfaceBorder';
import { useFormatter } from '../../../../components/i18n';
import { type PayloadUsagesOutput, type PayloadVersionOutput } from '../../../../utils/api-types';
import useCountLabel from '../../../../utils/hooks/useCountLabel';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import OlderVersionsNote from './OlderVersionsNote';
import SavedVersionChanges from './SavedVersionChanges';
import PayloadUsageMoveBar from './usages/PayloadUsageMoveBar';
import PayloadUsageMoveResult from './usages/PayloadUsageMoveResult';
import PayloadUsagesTable from './usages/PayloadUsagesTable';
import { olderUsageCount, olderVersionsInUse, type UsageCounts } from './usages/usageCounts';
import useUsageMove from './usages/useUsageMove';
import useFocusMove from './useFocusMove';
import useOlderVersionsKept from './useOlderVersionsKept';
import { versionRows } from './versionRows';

interface VersionProps {
  version: PayloadVersionOutput;
  versions: PayloadVersionOutput[];
  // Undefined while the counts load.
  count: number | undefined;
  usages: PayloadUsagesOutput | null;
  onOpen: (version: PayloadVersionOutput) => void;
  canMove: boolean;
  onMoved: () => void;
  // Opened to move injects: an older version in use opens; the latest stays folded.
  focusMove: boolean;
  // Its usages ticked too (a card's Move link); right after a save nothing is: the user picks what moves.
  preselect: boolean;
}

const PayloadVersion: FunctionComponent<VersionProps> = ({ version, versions, count, usages, onOpen, canMove, onMoved, focusMove, preselect }) => {
  const { t } = useFormatter();
  const counted = useCountLabel();
  const [expanded, setExpanded] = useState(version.payload_is_latest && !focusMove);
  useEffect(() => {
    if (expanded) onOpen(version);
  }, [expanded]);
  const move = useUsageMove({
    source: version,
    versions,
    usages,
    onMoved,
  });
  const row = useFocusMove({
    version,
    count: count ?? 0,
    usages,
    active: focusMove && canMove,
    onFocus: () => setExpanded(true),
    onLoaded: (loaded) => {
      if (preselect) move.selection.setSelectedElements(Object.fromEntries(loaded.payload_usages.map(usage => [usage.usage_id, usage])));
    },
  });

  return (
    <div ref={row}>
      <FoldRow open={expanded} onToggle={() => setExpanded(!expanded)}>
        <Text variant="content-base-bold">{`v${version.payload_version}`}</Text>
        {count !== undefined && (
          <Text variant="content-base" className="text-default-secondary">
            {`(${counted(count, '1 inject', '{count} injects')})`}
          </Text>
        )}
        {version.payload_revoked && <Chip label={t('Revoked')} severity="critical" />}
      </FoldRow>
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
  counts: UsageCounts | null;
  // Loaded when a version is opened.
  usages: Record<string, PayloadUsagesOutput>;
  onOpen: (version: PayloadVersionOutput) => void;
  // A move changes the usages of two versions: they are all loaded again.
  onMoved: () => void;
  notInstalled?: string[];
  focusMove?: boolean;
  // Opened right after a save that added the latest version: "Saved as v…" on top, nothing ticked.
  saved?: boolean;
  // The catalogue version being installed.
  installing?: string | null;
  onInstall?: (version: string) => void;
}

const PayloadVersionsTab: FunctionComponent<Props> = ({ versions, counts, usages, onOpen, onMoved, notInstalled, focusMove = false, saved = false, installing, onInstall }) => {
  const theme = useTheme();
  const canMove = useAbility().can(ACTIONS.MANAGE, SUBJECTS.THREAT_ARSENALS);
  const latest = versions?.find(version => version.payload_is_latest);
  const { kept, toggle } = useOlderVersionsKept(latest);
  const olderVersions = versions && counts && latest ? olderVersionsInUse(versions, counts, latest.payload_action_id) : [];
  const olderUsages = versions && counts && latest ? olderUsageCount(versions, counts, latest.payload_action_id) : 0;
  // Right after a save: what it changed, the saved version against the one before it (newest first).
  const previous = latest && versions ? versions[versions.indexOf(latest) + 1] : undefined;

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
      {olderUsages > 0 && (
        // After a save, the note and what the save changed share one frame above the usual tab.
        <div style={saved && !kept
          ? {
              ...fdsTableFrameStyle(theme),
              display: 'flex',
              flexDirection: 'column',
              gap: theme.spacing(1.5),
              padding: theme.spacing(2),
            }
          : undefined}
        >
          <OlderVersionsNote
            usageCount={olderUsages}
            versions={olderVersions.map(older => older.payload_version)}
            savedVersion={saved ? latest?.payload_version : undefined}
            kept={kept}
            onToggle={canMove ? toggle : undefined}
          />
          {saved && !kept && latest && previous && <SavedVersionChanges saved={latest} previous={previous} />}
        </div>
      )}
      <div className={LIB_SURFACE_LAYER} style={fdsTableFrameStyle(theme)}>
        {versionRows(versions, notInstalled).map(({ version, installed }) => (installed
          ? (
              <PayloadVersion
                key={installed.payload_id}
                version={installed}
                versions={versions}
                count={counts ? counts[installed.payload_id] ?? 0 : undefined}
                usages={usages[installed.payload_id] ?? null}
                onOpen={onOpen}
                canMove={canMove}
                onMoved={onMoved}
                focusMove={focusMove}
                preselect={!saved}
              />
            )
          : <NotInstalledVersion key={version} version={version} installing={installing === version} onInstall={onInstall} />))}
      </div>
    </div>
  );
};

export default PayloadVersionsTab;
