import { ArrowDropDown } from '@mui/icons-material';
import { Box, Divider, Menu, MenuItem, TextField } from '@mui/material';
import { type FunctionComponent, type MouseEvent, useState } from 'react';

import { updateFindingTriage } from '../../../actions/findings/finding-triage-actions';
import DialogConfirmation from '../../../components/common/DialogConfirmation';
import { useFormatter } from '../../../components/i18n';
import ItemStatus from '../../../components/ItemStatus';
import { type AggregatedFindingOutput } from '../../../utils/api-types';
import useAuth from '../../../utils/hooks/useAuth';

type TriageStatus = AggregatedFindingOutput['finding_triage_status'];

const MIN_JUSTIFICATION_LENGTH = 10;
const MAX_JUSTIFICATION_LENGTH = 4000;

// Mirror of the transition graph enforced server-side in ALLOWED_TRANSITIONS
// (openaev-api/src/main/java/io/openaev/rest/finding/FindingTriageService.java, static
// initializer block right below the field declaration). This is a deliberate FE
// duplication for immediate dropdown filtering / UX only - the backend remains the
// source of truth and re-validates every transition on PATCH (validateTransition()).
// If FindingTriageService#ALLOWED_TRANSITIONS ever changes, this constant must be
// updated to match.
const ALLOWED_TRANSITIONS: Record<NonNullable<TriageStatus>, NonNullable<TriageStatus>[]> = {
  UNTRIAGED: ['CONFIRMED', 'FALSE_POSITIVE'],
  CONFIRMED: ['RISK_ACCEPTED', 'FALSE_POSITIVE'],
  FALSE_POSITIVE: [],
  RISK_ACCEPTED: [],
};

interface Props {
  findingId: string;
  status: NonNullable<TriageStatus>;
  variant?: 'inList';
  /** Called with the new status once the PATCH has succeeded, so the caller can update its
   * own local state (list row / detail page) without waiting for a full refetch. */
  onStatusChange: (newStatus: NonNullable<TriageStatus>) => void;
}

const FindingTriageControl: FunctionComponent<Props> = ({ findingId, status, variant, onStatusChange }) => {
  const { t } = useFormatter();
  // Confirmed pattern for the current-user admin check, matching root.tsx and
  // PlayerPopover.tsx (both use `me.user_admin === true` off useAuth()).
  const { me } = useAuth();
  const isAdmin = me.user_admin === true;

  const [anchorEl, setAnchorEl] = useState<Element | null>(null);
  const [pendingTarget, setPendingTarget] = useState<NonNullable<TriageStatus> | null>(null);
  const [justification, setJustification] = useState('');

  // Backend gates revert-to-UNTRIAGED to Admins only (FindingTriageService#requireAdminIfRevert):
  // mirrored here so non-admins are never shown an option the API would reject with 403.
  const canRevert = isAdmin && status !== 'UNTRIAGED';
  const options: NonNullable<TriageStatus>[] = [
    ...ALLOWED_TRANSITIONS[status],
    ...(canRevert ? (['UNTRIAGED'] as NonNullable<TriageStatus>[]) : []),
  ];
  const hasOptions = options.length > 0;

  const closeMenu = () => setAnchorEl(null);
  const closeDialog = () => {
    setPendingTarget(null);
    setJustification('');
  };

  const handleOpenMenu = (event: MouseEvent) => {
    // The control is rendered inside list rows wrapped in a router Link (FindingList): stop
    // both the click and the native anchor navigation from firing when opening the menu.
    event.preventDefault();
    event.stopPropagation();
    if (hasOptions) {
      setAnchorEl(event.currentTarget);
    }
  };

  const handleSelectOption = (target: NonNullable<TriageStatus>) => {
    closeMenu();
    setPendingTarget(target);
  };

  const isJustificationValid = justification.trim().length >= MIN_JUSTIFICATION_LENGTH
    && justification.length <= MAX_JUSTIFICATION_LENGTH;

  const handleConfirm = () => {
    if (!pendingTarget || !isJustificationValid) return undefined;
    return updateFindingTriage(findingId, pendingTarget, justification).then(() => {
      onStatusChange(pendingTarget);
      closeDialog();
    });
  };

  return (
    <>
      <Box
        component="span"
        onClick={handleOpenMenu}
        sx={{
          cursor: hasOptions ? 'pointer' : 'default',
          display: 'inline-flex',
          alignItems: 'center',
        }}
      >
        <ItemStatus variant={variant} status={status} label={t(status)} />
        {hasOptions && <ArrowDropDown fontSize="small" sx={{ color: 'text.secondary' }} />}
      </Box>
      <Menu anchorEl={anchorEl} open={Boolean(anchorEl)} onClose={closeMenu} onClick={(e: MouseEvent) => e.stopPropagation()}>
        {options.map(option => (option === 'UNTRIAGED'
          ? [
              <Divider key="revert-divider" />,
              <MenuItem key={option} onClick={() => handleSelectOption(option)}>{t('Revert to Untriaged')}</MenuItem>,
            ]
          : (
              <MenuItem key={option} onClick={() => handleSelectOption(option)}>{t(option)}</MenuItem>
            )))}
      </Menu>
      <DialogConfirmation
        open={pendingTarget !== null}
        handleClose={closeDialog}
        handleSubmit={pendingTarget ? handleConfirm : null}
        submitLabel={t('Confirm')}
        text=""
        disableSubmit={!isJustificationValid}
        richContent={(
          <Box sx={{ minWidth: 400 }}>
            <Box sx={{ mb: 2 }}>
              {t('Confirm triage change')}
              {': '}
              {t(status)}
              {' → '}
              {pendingTarget ? t(pendingTarget) : ''}
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
    </>
  );
};

export default FindingTriageControl;
