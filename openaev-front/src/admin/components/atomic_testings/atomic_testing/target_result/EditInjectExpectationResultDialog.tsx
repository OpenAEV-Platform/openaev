import { CloseOutlined } from '@mui/icons-material';
import { Box, Button, Dialog, DialogActions, DialogContent, IconButton, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';

import Transition from '../../../../../components/common/Transition';
import { useFormatter } from '../../../../../components/i18n';
import ItemStatus from '../../../../../components/ItemStatus';
import type { InjectExpectationResult } from '../../../../../utils/api-types';
import { computeLabel } from '../../../../../utils/String';
import { expectationTypeColor, expectationTypeIcon } from '../../../common/ExpectationIconByType';
import { type InjectExpectationsStore } from '../../../common/injects/expectations/Expectation';
import { isManualExpectation } from '../../../common/injects/expectations/ExpectationUtils';
import DetectionPreventionExpectationsValidationForm
  from '../../../simulations/simulation/validation/expectations/DetectionPreventionExpectationsValidationForm';
import ManualExpectationsValidationForm
  from '../../../simulations/simulation/validation/expectations/ManualExpectationsValidationForm';

interface Props {
  open: boolean;
  injectExpectation: InjectExpectationsStore | null;
  sourceIds: string[];
  resultToEdit?: InjectExpectationResult | null;
  onClose: () => void;
  onUpdate: () => void;
}

// Best-in-class result edition dialog: framed expectation-type icon, name +
// type/expected-score context and current status in the header, the form in the
// body, actions in a proper footer (same anatomy as TargetResultAlertsDialog).
const EditInjectExpectationResultDialog = ({ open, injectExpectation, sourceIds, resultToEdit, onClose, onUpdate }: Props) => {
  const { t } = useFormatter();
  const theme = useTheme();

  if (!injectExpectation) {
    return null;
  }

  const expectationType = injectExpectation.inject_expectation_type;
  const typeLabel = expectationType ? t(expectationType) : '';
  const TypeIcon = expectationTypeIcon(expectationType);
  const identityColor = expectationTypeColor(expectationType);
  const title = injectExpectation.inject_expectation_name?.trim() || typeLabel;
  const expectedScore = injectExpectation.inject_expectation_expected_score;
  const subtitleParts = [
    typeLabel !== title ? typeLabel : undefined,
    expectedScore != null ? `${t('Expected score:')} ${expectedScore}` : undefined,
  ].filter(Boolean);
  const description = injectExpectation.inject_expectation_description?.trim();
  // When editing an existing result show its outcome, otherwise the current
  // expectation status - both feed the same status chip in the header.
  const statusLabel = resultToEdit?.result || computeLabel(injectExpectation.inject_expectation_status);
  const statusValue = resultToEdit?.result || injectExpectation.inject_expectation_status;

  return (
    <Dialog
      open={open}
      onClose={onClose}
      fullWidth
      maxWidth="sm"
      slots={{ transition: Transition }}
      slotProps={{ paper: { elevation: 1 } }}
      data-testid="edit-expectation-result-dialog"
    >
      {/* Header: framed expectation-type icon + name + context + status + close */}
      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1.5,
        padding: theme.spacing(2, 2, 1.5, 2.5),
      }}
      >
        <Box
          aria-hidden
          sx={{
            width: 44,
            height: 44,
            flexShrink: 0,
            borderRadius: 1,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: identityColor,
            border: `1px solid ${alpha(identityColor, 0.2)}`,
            backgroundColor: alpha(identityColor, 0.08),
          }}
        >
          <TypeIcon />
        </Box>
        <div style={{
          minWidth: 0,
          flex: 1,
        }}
        >
          <Typography
            sx={{
              fontFamily: theme.typography.h1.fontFamily,
              fontSize: 16,
              fontWeight: 600,
              lineHeight: 1.3,
              whiteSpace: 'nowrap',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
            }}
          >
            {title}
          </Typography>
          {subtitleParts.length > 0 && (
            <Typography sx={{
              fontSize: 12,
              color: 'text.secondary',
              marginTop: 0.5,
              whiteSpace: 'nowrap',
            }}
            >
              {subtitleParts.join(' · ')}
            </Typography>
          )}
        </div>
        <ItemStatus variant="inList" label={t(statusLabel)} status={statusValue} />
        <IconButton
          aria-label={t('Close')}
          size="small"
          onClick={onClose}
          sx={{ alignSelf: 'flex-start' }}
        >
          <CloseOutlined fontSize="small" />
        </IconButton>
      </Box>
      <DialogContent sx={{
        padding: theme.spacing(2, 2.5),
        borderTop: `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
      }}
      >
        {description && (
          <Typography
            variant="body2"
            sx={{
              color: 'text.secondary',
              marginBottom: 2,
            }}
          >
            {description}
          </Typography>
        )}
        {isManualExpectation(expectationType)
          && <ManualExpectationsValidationForm expectation={injectExpectation} onUpdate={onUpdate} withSummary={false} hideActions />}
        {['DETECTION', 'PREVENTION', 'VULNERABILITY'].includes(expectationType)
          && (
            <DetectionPreventionExpectationsValidationForm
              expectation={injectExpectation}
              sourceIds={resultToEdit ? undefined : sourceIds}
              onUpdate={onUpdate}
              result={resultToEdit ?? undefined}
            />
          )}
      </DialogContent>
      <DialogActions sx={{ padding: theme.spacing(0, 2.5, 2) }}>
        <Button variant="outlined" color="primary" onClick={onClose}>
          {t('Cancel')}
        </Button>
        <Button variant="contained" color="primary" type="submit" form="expectationForm">
          {t('Validate')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default EditInjectExpectationResultDialog;
