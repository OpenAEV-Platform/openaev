import { ArrowDropDown } from '@mui/icons-material';
import { Box, Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, Divider, List, ListItem, ListItemText, Menu, MenuItem, TextField, Typography } from '@mui/material';
import { ArchiveArrowUpOutline, ArchiveOutline } from 'mdi-material-ui';
import { type MouseEvent, useState } from 'react';

import DialogConfirmation from '../../../components/common/DialogConfirmation';
import { useFormatter } from '../../../components/i18n';
import { type AggregatedFindingOutput, type FindingArchiveBulkItemOutput, type FindingTriageBulkItemOutput } from '../../../utils/api-types';
import useAuth from '../../../utils/hooks/useAuth';

type TriageStatus = NonNullable<AggregatedFindingOutput['finding_triage_status']>;

const MIN_JUSTIFICATION_LENGTH = 10;
const MAX_JUSTIFICATION_LENGTH = 4000;

// Every non-revert status is always offered here, unlike FindingTriageControl's per-row
// ALLOWED_TRANSITIONS dropdown: a bulk selection can contain findings in different starting
// statuses, so the target-status choice cannot be narrowed client-side. The backend validates
// each finding's transition independently and reports per-finding success/failure (see
// FindingTriageService#triageBulk), exactly like a single triage change would.
const BULK_TRIAGE_TARGETS: TriageStatus[] = ['CONFIRMED', 'FALSE_POSITIVE', 'RISK_ACCEPTED'];

interface FailureGroup {
  message: string;
  count: number;
}

interface ResultSummary {
  successCount: number;
  failureGroups: FailureGroup[];
}

// Groups per-item failures by their error message (e.g. every "Invalid triage transition from
// RISK_ACCEPTED to FALSE_POSITIVE" collapses into one line with a count), instead of listing one
// row per finding - a bulk selection can easily contain dozens of findings failing for the same
// reason.
const summarizeResults = (items: {
  success?: boolean;
  error?: string;
}[]): ResultSummary => {
  const counts = new Map<string, number>();
  let successCount = 0;
  items.forEach((item) => {
    if (item.success) {
      successCount += 1;
      return;
    }
    const message = item.error ?? 'Unknown error';
    counts.set(message, (counts.get(message) ?? 0) + 1);
  });
  return {
    successCount,
    failureGroups: Array.from(counts.entries()).map(([message, count]) => ({
      message,
      count,
    })),
  };
};

interface Props {
  numberOfSelectedElements: number;
  onClear: () => void;
  onTriage: (status: TriageStatus, justification: string) => Promise<FindingTriageBulkItemOutput[]>;
  onArchive: (archived: boolean) => Promise<FindingArchiveBulkItemOutput[]>;
}

// Lightweight, Finding-specific bulk action bar - deliberately not the shared ToolBar component
// (openaev-front/src/admin/components/common/ToolBar.tsx), which is tightly coupled to
// injects/scenarios concerns (teams, asset groups, endpoints, bulk-test) that do not apply here.
const FindingBulkActionBar = ({ numberOfSelectedElements, onClear, onTriage, onArchive }: Props) => {
  const { t } = useFormatter();
  const { me } = useAuth();
  const isAdmin = me.user_admin === true;

  const [anchorEl, setAnchorEl] = useState<Element | null>(null);
  const [pendingTarget, setPendingTarget] = useState<TriageStatus | null>(null);
  const [justification, setJustification] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [archiveDialog, setArchiveDialog] = useState<'archive' | 'unarchive' | null>(null);
  // Populated whenever a bulk action returns at least one per-finding failure (e.g. an invalid
  // triage transition, or a finding that no longer exists) - never fails silently, see the
  // grouping helper above.
  const [resultDialog, setResultDialog] = useState<ResultSummary | null>(null);

  const closeMenu = () => setAnchorEl(null);
  const closeTriageDialog = () => {
    setPendingTarget(null);
    setJustification('');
  };

  const isJustificationValid = justification.trim().length >= MIN_JUSTIFICATION_LENGTH
    && justification.length <= MAX_JUSTIFICATION_LENGTH;

  const handleConfirmTriage = () => {
    if (!pendingTarget || !isJustificationValid) return undefined;
    setSubmitting(true);
    return onTriage(pendingTarget, justification).then((results) => {
      const summary = summarizeResults(results);
      if (summary.failureGroups.length > 0) setResultDialog(summary);
    }).finally(() => {
      setSubmitting(false);
      closeTriageDialog();
    });
  };

  const handleConfirmArchive = () => {
    if (!archiveDialog) return undefined;
    setSubmitting(true);
    return onArchive(archiveDialog === 'archive').then((results) => {
      const summary = summarizeResults(results);
      if (summary.failureGroups.length > 0) setResultDialog(summary);
    }).finally(() => {
      setSubmitting(false);
      setArchiveDialog(null);
    });
  };

  return (
    <Box sx={{
      display: 'flex',
      alignItems: 'center',
      gap: 2,
    }}
    >
      <Typography variant="body2">
        {t('{count} selected', { count: String(numberOfSelectedElements) })}
      </Typography>
      <Button size="small" onClick={onClear}>{t('Clear')}</Button>
      <Divider orientation="vertical" flexItem />
      <Button
        size="small"
        variant="outlined"
        endIcon={<ArrowDropDown fontSize="small" />}
        onClick={(e: MouseEvent) => setAnchorEl(e.currentTarget)}
      >
        {t('Triage')}
      </Button>
      <Menu anchorEl={anchorEl} open={Boolean(anchorEl)} onClose={closeMenu}>
        {BULK_TRIAGE_TARGETS.map(target => (
          <MenuItem
            key={target}
            onClick={() => {
              closeMenu();
              setPendingTarget(target);
            }}
          >
            {t(target)}
          </MenuItem>
        ))}
        {isAdmin && [
          <Divider key="revert-divider" />,
          <MenuItem
            key="UNTRIAGED"
            onClick={() => {
              closeMenu();
              setPendingTarget('UNTRIAGED');
            }}
          >
            {t('Revert to Untriaged')}
          </MenuItem>,
        ]}
      </Menu>
      <Button
        size="small"
        variant="outlined"
        startIcon={<ArchiveOutline fontSize="small" />}
        onClick={() => setArchiveDialog('archive')}
      >
        {t('Archive')}
      </Button>
      <Button
        size="small"
        variant="outlined"
        startIcon={<ArchiveArrowUpOutline fontSize="small" />}
        onClick={() => setArchiveDialog('unarchive')}
      >
        {t('Un-archive')}
      </Button>
      <DialogConfirmation
        open={pendingTarget !== null}
        handleClose={closeTriageDialog}
        handleSubmit={pendingTarget ? handleConfirmTriage : null}
        submitLabel={t('Confirm')}
        text=""
        disableSubmit={!isJustificationValid || submitting}
        richContent={(
          <Box sx={{ minWidth: 400 }}>
            <Box sx={{ mb: 2 }}>
              {t('Set triage status of {count} findings to {status}', {
                count: String(numberOfSelectedElements),
                status: pendingTarget ? t(pendingTarget) : '',
              })}
            </Box>
            <TextField
              label={t('Justification')}
              placeholder={t('Explain your decision (10 to 4000 characters)')}
              value={justification}
              onChange={e => setJustification(e.target.value)}
              multiline
              minRows={3}
              fullWidth
              error={justification.length > 0 && !isJustificationValid}
              helperText={`${justification.length}/${MAX_JUSTIFICATION_LENGTH}`}
            />
          </Box>
        )}
      />
      <DialogConfirmation
        open={archiveDialog !== null}
        handleClose={() => setArchiveDialog(null)}
        handleSubmit={archiveDialog ? handleConfirmArchive : null}
        submitLabel={t('Confirm')}
        disableSubmit={submitting}
        text={archiveDialog === 'archive'
          ? t('Archive {count} finding(s)? They will move out of the active view, but stay retrievable anytime from the Archived tab.', { count: String(numberOfSelectedElements) })
          : t('Restore {count} finding(s) to the active view?', { count: String(numberOfSelectedElements) })}
      />
      {/* Never silent: whenever at least one finding in the batch could not be updated (e.g. an
          invalid triage transition like Risk Accepted -> False Positive, or a finding deleted in
          the meantime), this dialog explains exactly why, grouped by reason, instead of the
          change appearing to "just not happen" for some rows. */}
      <Dialog open={resultDialog !== null} onClose={() => setResultDialog(null)}>
        <DialogTitle>{t('Some findings could not be updated')}</DialogTitle>
        <DialogContent>
          <DialogContentText sx={{ mb: 1 }}>
            {t('{success} finding(s) updated successfully. {failed} could not be updated:', {
              success: String(resultDialog?.successCount ?? 0),
              failed: String(resultDialog?.failureGroups.reduce((sum, g) => sum + g.count, 0) ?? 0),
            })}
          </DialogContentText>
          <List dense>
            {resultDialog?.failureGroups.map(group => (
              <ListItem key={group.message} disableGutters>
                <ListItemText
                  primary={t('{count} finding(s): {message}', {
                    count: String(group.count),
                    message: group.message,
                  })}
                />
              </ListItem>
            ))}
          </List>
        </DialogContent>
        <DialogActions>
          <Button variant="contained" color="primary" onClick={() => setResultDialog(null)}>
            {t('Close')}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default FindingBulkActionBar;
