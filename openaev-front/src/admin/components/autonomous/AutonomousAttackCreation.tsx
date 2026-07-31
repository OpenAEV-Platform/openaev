import {
  ArrowUpward,
  AutoAwesome,
  Diamond,
  Dns,
  HowToReg,
  Hub,
  Key,
  Lock,
  MailOutline,
  MeetingRoom,
  Public,
  Shield,
  Storage,
  TrackChanges,
} from '@mui/icons-material';
import { Alert, Box, Button, Card, CardActionArea, Stack, type SvgIconTypeMap, TextField, Typography } from '@mui/material';
import { type OverridableComponent } from '@mui/material/OverridableComponent';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect, useState } from 'react';
import { useNavigate } from 'react-router';

import { createAutonomousRun, fetchObjectiveTemplates, startAutonomousRun } from '../../../actions/autonomous/autonomous-actions';
import { type AutonomousObjectiveTemplate } from '../../../actions/autonomous/autonomous-types';
import Drawer from '../../../components/common/Drawer';
import { useFormatter } from '../../../components/i18n';
import useAuth from '../../../utils/hooks/useAuth';
import useEnterpriseEdition from '../../../utils/hooks/useEnterpriseEdition';
import { isFeatureEnabled } from '../../../utils/utils';
import EEChip from '../common/entreprise_edition/EEChip';

// Maps the objective-template icon tokens seeded server-side (kebab-case, see
// AutonomousObjectiveTemplateService) to MUI icons. Unknown/empty tokens fall
// back to a generic "objective" target icon.
type MuiIcon = OverridableComponent<SvgIconTypeMap>;
const OBJECTIVE_ICONS: Record<string, MuiIcon> = {
  'domain': Dns,
  'database': Storage,
  'shield': Shield,
  'door-open': MeetingRoom,
  'arrow-up': ArrowUpward,
  'key': Key,
  'mail': MailOutline,
  'network': Hub,
  'lock': Lock,
  'gem': Diamond,
  'globe': Public,
  'user-check': HowToReg,
};

/**
 * Dedicated, deeply-integrated entry point for the Autonomous (AI-driven) attack path. Rendered as a
 * visible top-right action (scenarios list, and reusable elsewhere), gated behind the
 * {@code AUTONOMOUS_ATTACK_PATH} preview feature and the Enterprise Edition license (every AI feature
 * is EE-only). The drawer is intentionally minimal - pick an objective (template or free text),
 * optionally label the run, then launch. There is nothing to build by hand: the attack-path
 * substrate is auto-provisioned server-side and the AI orchestrator builds and executes the path.
 */
const AutonomousAttackCreation: FunctionComponent = () => {
  const { t } = useFormatter();
  const theme = useTheme();
  const navigate = useNavigate();
  const { settings } = useAuth();
  const featureEnabled = isFeatureEnabled('AUTONOMOUS_ATTACK_PATH');
  const {
    isValidated: isEnterpriseEdition,
    openDialog: openEnterpriseEditionDialog,
    setEEFeatureDetectedInfo,
  } = useEnterpriseEdition();

  const [open, setOpen] = useState(false);
  const [templates, setTemplates] = useState<AutonomousObjectiveTemplate[]>([]);
  const [selectedTemplateKey, setSelectedTemplateKey] = useState<string | null>(null);
  const [objective, setObjective] = useState('');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) {
      return;
    }
    fetchObjectiveTemplates()
      .then(res => setTemplates(res.data ?? []))
      .catch(() => setTemplates([]));
  }, [open]);

  const handleOpen = () => {
    if (!isEnterpriseEdition) {
      setEEFeatureDetectedInfo(t('Autonomous attack path'));
      openEnterpriseEditionDialog();
      return;
    }
    setOpen(true);
  };

  const handleClose = () => {
    setOpen(false);
    setSelectedTemplateKey(null);
    setObjective('');
    setName('');
    setDescription('');
    setError(null);
  };

  const handleSelectTemplate = (template: AutonomousObjectiveTemplate) => {
    setSelectedTemplateKey(template.autonomous_objective_template_key);
    setObjective(template.autonomous_objective_template_prompt);
  };

  const canLaunch = objective.trim().length > 0 && !submitting;

  const handleLaunch = () => {
    if (objective.trim().length === 0) {
      return;
    }
    setSubmitting(true);
    setError(null);
    createAutonomousRun({
      objective: objective.trim(),
      objective_template_key: selectedTemplateKey ?? undefined,
      name: name.trim() || undefined,
      description: description.trim() || undefined,
    })
      .then((res) => {
        const runId = res.data.autonomous_run_id;
        return startAutonomousRun(runId).then(() => {
          handleClose();
          navigate(`/admin/autonomous/${runId}`);
        });
      })
      .catch(() => setError(t('Failed to launch the autonomous run')))
      .finally(() => setSubmitting(false));
  };

  // The autonomous run is driven by the XTM One orchestrator (the AI brain), so
  // the entry point is meaningless without a configured XTM One - hide it exactly
  // like the CTEM Command Center shortcut does, and when agentic AI is disabled.
  const xtmOneReady =
    settings.platform_xtm_one_configured === true
    && settings.filigran_chatbot_ai_cgu_status !== 'disabled';
  if (!featureEnabled || !xtmOneReady) {
    return null;
  }

  return (
    <>
      <Button
        onClick={handleOpen}
        variant="contained"
        size="small"
        data-testid="button-autonomous-attack"
        startIcon={<AutoAwesome />}
        sx={{
          'whiteSpace': 'nowrap',
          'flexShrink': 0,
          // AI purple: this is an XTM One (agentic AI) action, like the CTEM and
          // Ask Ariane buttons - not a generic primary CTA.
          'backgroundColor': theme.palette.ai.main,
          'color': theme.palette.ai.contrastText,
          '&:hover': { backgroundColor: theme.palette.ai.dark },
        }}
      >
        {t('Autonomous attack')}
        {!isEnterpriseEdition && <EEChip />}
      </Button>
      <Drawer open={open} handleClose={handleClose} title={t('Launch an autonomous attack')}>
        {() => (
          <Stack sx={{ gap: theme.spacing(3) }}>
            <Alert
              severity="info"
              variant="outlined"
              icon={<AutoAwesome fontSize="inherit" />}
              sx={{
                'color': theme.palette.ai.main,
                'borderColor': alpha(theme.palette.ai.main, 0.5),
                'backgroundColor': alpha(theme.palette.ai.main, 0.08),
                '& .MuiAlert-icon': { color: theme.palette.ai.main },
              }}
            >
              {t('An AI orchestrator provisions and drives a real attack path autonomously, adapting in real time. Just set an objective - you never build anything by hand. You can steer it live and it will ask for input only when stuck.')}
            </Alert>

            <Box>
              <Typography variant="h2" gutterBottom>
                {t('Objective')}
              </Typography>
              <Stack
                sx={{
                  display: 'grid',
                  gap: theme.spacing(1),
                  gridTemplateColumns: 'repeat(2, 1fr)',
                  marginBottom: theme.spacing(2),
                }}
              >
                {templates.map((template) => {
                  const isSelected = selectedTemplateKey === template.autonomous_objective_template_key;
                  const ObjectiveIcon = OBJECTIVE_ICONS[template.autonomous_objective_template_icon ?? ''] ?? TrackChanges;
                  return (
                    <Card
                      key={template.autonomous_objective_template_key}
                      variant="outlined"
                      sx={{
                        borderColor: isSelected ? theme.palette.ai.main : undefined,
                        borderWidth: isSelected ? 2 : 1,
                        backgroundColor: isSelected ? alpha(theme.palette.ai.main, 0.08) : undefined,
                      }}
                    >
                      <CardActionArea
                        onClick={() => handleSelectTemplate(template)}
                        sx={{ padding: theme.spacing(1.5), height: '100%' }}
                      >
                        <Stack direction="row" spacing={1.5} alignItems="flex-start">
                          <ObjectiveIcon
                            fontSize="small"
                            sx={{
                              marginTop: '2px',
                              flexShrink: 0,
                              color: isSelected ? theme.palette.ai.main : theme.palette.text.secondary,
                            }}
                          />
                          <Box>
                            <Typography variant="subtitle2" sx={{ fontWeight: 'bold' }}>
                              {t(template.autonomous_objective_template_label)}
                            </Typography>
                            {template.autonomous_objective_template_description && (
                              <Typography variant="caption" color="text.secondary">
                                {t(template.autonomous_objective_template_description)}
                              </Typography>
                            )}
                          </Box>
                        </Stack>
                      </CardActionArea>
                    </Card>
                  );
                })}
              </Stack>
              <TextField
                value={objective}
                onChange={(event) => {
                  setObjective(event.target.value);
                  setSelectedTemplateKey(null);
                }}
                label={t('Objective (free text)')}
                placeholder={t('e.g. Reach the domain controller and prove domain admin from an initial foothold')}
                multiline
                minRows={3}
                fullWidth
              />
            </Box>

            <Box>
              <Typography variant="h2" gutterBottom>
                {t('Label (optional)')}
              </Typography>
              <Stack sx={{ gap: theme.spacing(2) }}>
                <TextField
                  value={name}
                  onChange={event => setName(event.target.value)}
                  label={t('Name')}
                  placeholder={t('Auto-generated if left empty')}
                  fullWidth
                />
                <TextField
                  value={description}
                  onChange={event => setDescription(event.target.value)}
                  label={t('Description')}
                  placeholder={t('Auto-generated from the objective if left empty')}
                  multiline
                  minRows={2}
                  fullWidth
                />
              </Stack>
            </Box>

            {error && <Alert severity="error">{error}</Alert>}

            <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: theme.spacing(1) }}>
              <Button onClick={handleClose} disabled={submitting}>
                {t('Cancel')}
              </Button>
              <Button
                onClick={handleLaunch}
                variant="contained"
                disabled={!canLaunch}
                startIcon={<AutoAwesome />}
                data-testid="button-autonomous-launch"
                sx={{
                  'backgroundColor': theme.palette.ai.main,
                  'color': theme.palette.ai.contrastText,
                  '&:hover': { backgroundColor: theme.palette.ai.dark },
                }}
              >
                {t('Launch')}
              </Button>
            </Box>
          </Stack>
        )}
      </Drawer>
    </>
  );
};

export default AutonomousAttackCreation;
