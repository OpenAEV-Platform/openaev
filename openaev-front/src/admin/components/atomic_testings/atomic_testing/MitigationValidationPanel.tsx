import { Button, Chip, Paper, Text } from '@filigran/design-system';
import { CheckCircleOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';

import { useFormatter } from '../../../../components/i18n';
import { tint } from '../../../../utils/tint';
import { type ValidationState } from './useMitigationValidation';

// PROTOTYPE - MOCK ONLY. Shown under the remediation of a group once it is generated. The user applies
// the remediation in their own environment, then confirms it here: the action is re-run on the targets
// of the group to check it is now prevented.

interface Props {
  // Number of targets of the group.
  total: number;
  state?: ValidationState;
  onApplied: () => void;
}

const MAX_NAMES = 5;

const MitigationValidationPanel = ({ total, state, onApplied }: Props) => {
  const { t, n } = useFormatter();
  const theme = useTheme();
  // Every target of the group is now prevented: the mitigation is proven to fix it.
  const isFixed = state?.status === 'done' && state.failed.length === 0;

  const renderStatus = () => {
    if (!state) {
      return (
        <Text variant="content-compact" className="text-default-secondary">
          {`${t('Once the remediation is applied in your environment, the action is re-run on')} ${n(total)} ${t('targets to confirm the weakness is gone.')}`}
        </Text>
      );
    }
    if (state.status === 'validating') {
      return (
        <>
          <Chip label={t('Validating')} severity="neutral" />
          <Text variant="content-compact" className="text-default-secondary">
            {`${t('Re-running the action on')} ${n(state.count)} ${t('targets…')}`}
          </Text>
        </>
      );
    }
    if (state.failed.length === 0) {
      return (
        <>
          <Chip startIcon={<CheckCircleOutlined fontSize="small" />} label={t('Fixed · validated')} severity="low" />
          <Text variant="content-compact" className="text-default-secondary">
            {`${t('The weakness is gone on all')} ${n(state.total)} ${t('targets.')}`}
          </Text>
        </>
      );
    }
    const shown = state.failed.slice(0, MAX_NAMES).join(', ');
    const more = state.failed.length - MAX_NAMES;
    return (
      <>
        <Chip label={t('Partially validated')} severity="medium" />
        <Text variant="content-compact" className="text-default-secondary">
          {`${t('The weakness is still present on')} ${n(state.failed.length)} ${t('of')} ${n(state.total)} ${t('targets:')} ${shown}${more > 0 ? ` +${n(more)}` : ''}`}
        </Text>
      </>
    );
  };

  const buttonLabel = () => {
    if (state?.status === 'done') {
      return state.failed.length > 0
        ? `${t('Applied again, re-validate')} (${n(state.failed.length)})`
        : t('Validate again');
    }
    return t('Remediation applied, validate');
  };

  return (
    <Paper
      padding={16}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        flexWrap: 'wrap',
        ...(isFixed && {
          backgroundColor: tint(theme.palette.success.main, 15),
          border: `1px solid ${theme.palette.success.main}`,
        }),
      }}
    >
      {renderStatus()}
      <div style={{ marginLeft: 'auto' }}>
        <Button
          priority={isFixed ? 'secondary' : 'primary'}
          disabled={state?.status === 'validating'}
          onClick={onApplied}
        >
          {buttonLabel()}
        </Button>
      </div>
    </Paper>
  );
};

export default MitigationValidationPanel;
