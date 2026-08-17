import { Box, List, ListItem, ListItemText, Typography } from '@mui/material';
import { ArchiveArrowUpOutline, ArchiveOutline } from 'mdi-material-ui';
import { useEffect, useState } from 'react';

import { fetchFindingTriageHistory } from '../../../actions/findings/finding-triage-actions';
import Empty from '../../../components/Empty';
import { useFormatter } from '../../../components/i18n';
import Loader from '../../../components/Loader';
import { type FindingTriageHistoryOutput } from '../../../utils/api-types';

interface Props {
  findingId: string;
  /** Bumped by the parent whenever a triage change is confirmed elsewhere on the page (e.g.
   * FindingTriageControl in the Information section), so the tab reflects the new entry
   * immediately even if it stays mounted across the update (not just on next tab switch). */
  refreshKey?: number;
}

type LoadState = 'loading' | 'loaded' | 'forbidden' | 'error';

/**
 * Read-only triage history list for the finding detail page. Fetch-on-mount (and on
 * `refreshKey` change), no caching - always reflects the latest state on (re)mount.
 */
const FindingTriageHistory = ({ findingId, refreshKey }: Props) => {
  const { t, nsdt } = useFormatter();

  const [history, setHistory] = useState<FindingTriageHistoryOutput[]>([]);
  const [state, setState] = useState<LoadState>('loading');

  useEffect(() => {
    setState('loading');
    fetchFindingTriageHistory(findingId)
      .then((result: { data: FindingTriageHistoryOutput[] }) => {
        setHistory(result.data);
        setState('loaded');
      })
      .catch((error: { status?: number }) => {
        // GET .../triage/history is gated on Action.TRIAGE (stricter than the Action.READ used
        // for the current status - see FindingTriageApi.java), so a user who can see the
        // finding may still lack triage rights. Surface that explicitly instead of a
        // misleading "no history yet" empty state or a raw error.
        //
        // NOTE: this branch relies on Spring Boot's default
        // (server.error.include-message=never, not explicitly configured in this repo)
        // returning an empty "message" body for 403s thrown by AccessControlAspect.
        // If that config ever changes (e.g. include-message=always), this check would
        // stop matching and the backend's real message (which includes the user's
        // email) would leak into the global toast instead of the permission message
        // below.
        setState(error?.status === 403 ? 'forbidden' : 'error');
      });
  }, [findingId, refreshKey]);

  if (state === 'loading') {
    return <Loader variant="inElement" />;
  }
  if (state === 'forbidden') {
    return <Empty message={t('You do not have permission to view triage history')} />;
  }
  if (state === 'error') {
    return <Empty message={t('Unable to load triage history')} />;
  }
  if (history.length === 0) {
    return <Empty message={t('No triage history yet')} />;
  }

  return (
    <List disablePadding>
      {history.map((entry) => {
        let authorName: string | undefined;
        if (entry.finding_triage_history_is_system) {
          authorName = t('System');
        } else if (entry.finding_triage_history_actor_firstname && entry.finding_triage_history_actor_lastname) {
          authorName = `${entry.finding_triage_history_actor_firstname} ${entry.finding_triage_history_actor_lastname}`;
        } else {
          authorName = entry.finding_triage_history_actor_id;
        }
        return (
          <ListItem
            key={entry.finding_triage_history_id}
            alignItems="flex-start"
            divider
          >
            <ListItemText
              primary={(
                <Box sx={{
                  display: 'flex',
                  alignItems: 'baseline',
                  gap: 1,
                }}
                >
                  {entry.finding_triage_history_action === 'ARCHIVE' && (
                    <ArchiveOutline fontSize="small" sx={{ color: 'text.secondary' }} />
                  )}
                  {entry.finding_triage_history_action === 'UNARCHIVE' && (
                    <ArchiveArrowUpOutline fontSize="small" sx={{ color: 'text.secondary' }} />
                  )}
                  <Typography sx={{ fontWeight: 600 }}>
                    {entry.finding_triage_history_action === 'ARCHIVE' && t('Archived')}
                    {entry.finding_triage_history_action === 'UNARCHIVE' && t('Un-archived')}
                    {(entry.finding_triage_history_action === 'TRIAGE_CHANGE' || !entry.finding_triage_history_action) && (
                      <>
                        {t(entry.finding_triage_history_from_status ?? 'UNTRIAGED')}
                        {' → '}
                        {t(entry.finding_triage_history_to_status ?? 'UNTRIAGED')}
                      </>
                    )}
                  </Typography>
                  <Typography
                    variant="body2"
                    color="text.secondary"
                  >
                    {authorName}
                    {' · '}
                    {nsdt(entry.finding_triage_history_created_at)}
                  </Typography>
                </Box>
              )}
              secondary={entry.finding_triage_history_action === 'TRIAGE_CHANGE' || !entry.finding_triage_history_action
                ? (
                    <Typography
                      variant="body2"
                      sx={{ whiteSpace: 'pre-wrap' }}
                      component="span"
                    >
                      {entry.finding_triage_history_justification}
                    </Typography>
                  )
                : undefined}
            />
          </ListItem>
        );
      })}
    </List>
  );
};

export default FindingTriageHistory;
