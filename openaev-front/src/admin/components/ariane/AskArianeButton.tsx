import { Button, SvgIcon, Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { LogoXtmOneIcon } from 'filigran-icon';
import { useContext, useState } from 'react';

import { useFormatter } from '../../../components/i18n';
import useAuth from '../../../utils/hooks/useAuth';
import useEnterpriseEdition from '../../../utils/hooks/useEnterpriseEdition';
import { AbilityContext } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import EEChip from '../common/entreprise_edition/EEChip';
import FiligranAiCguDialog from './FiligranAiCguDialog';
import { useChatbot } from './useChatbotHooks';

const AskArianeButton = () => {
  const theme = useTheme();
  const { t } = useFormatter();
  const { settings } = useAuth();
  const { isOpen, toggleChat } = useChatbot();
  const ability = useContext(AbilityContext);
  const [cguDialogOpen, setCguDialogOpen] = useState(false);
  const {
    isValidated: isEnterpriseEdition,
    openDialog: openEnterpriseEditionDialog,
    setEEFeatureDetectedInfo,
  } = useEnterpriseEdition();

  const chatbotCguStatus = settings.filigran_chatbot_ai_cgu_status;
  const isCguPending = chatbotCguStatus === 'pending' || chatbotCguStatus === undefined;
  const isChatbotEnabled = isEnterpriseEdition && chatbotCguStatus === 'enabled';

  // Hide if chatbot has been explicitly disabled
  if (chatbotCguStatus === 'disabled') {
    return null;
  }

  const canManage = ability.can(ACTIONS.MANAGE, SUBJECTS.PLATFORM_SETTINGS);

  const handleClick = () => {
    if (!isEnterpriseEdition) {
      // No EE license - open EE dialog
      setEEFeatureDetectedInfo(t('XTM One (Agentic IA)'));
      openEnterpriseEditionDialog();
    } else if (chatbotCguStatus === 'enabled') {
      // CGU accepted - normal toggle behavior
      toggleChat();
    } else if (canManage) {
      // CGU pending - show CGU dialog
      setCguDialogOpen(true);
    }
  };

  const buttonContent = (
    <Button
      variant="outlined"
      size="small"
      onClick={handleClick}
      startIcon={(
        <SvgIcon
          component={LogoXtmOneIcon}
          inheritViewBox
          sx={{ fontSize: '16px !important' }}
        />
      )}
      endIcon={!isEnterpriseEdition ? <span><EEChip /></span> : undefined}
      sx={{
        'borderColor': isOpen
          ? theme.palette.ai.main
          : theme.palette.ai.main + '80',
        'color': theme.palette.ai.main,
        'backgroundColor': isOpen
          ? theme.palette.ai.main + '1A'
          : 'transparent',
        'textTransform': 'none',
        'fontWeight': 500,
        'fontSize': '0.8125rem',
        'padding': '3px 12px',
        'borderRadius': '6px',
        'whiteSpace': 'nowrap',
        'marginRight': 1,
        'verticalAlign': 'middle',
        '&:hover': {
          borderColor: theme.palette.ai.main,
          backgroundColor: theme.palette.ai.main + '1A',
        },
        '& .MuiButton-startIcon': { marginRight: '6px' },
      }}
    >
      {t('Ask Ariane')}
    </Button>
  );

  // If CGU pending and user cannot manage, wrap with tooltip explaining
  if (isEnterpriseEdition && isCguPending && !canManage) {
    return (
      <Tooltip title={t('Ask Ariane isn\'t activated yet. Please reach out to your administrator to enable this feature.')}>
        <span>{buttonContent}</span>
      </Tooltip>
    );
  }

  return (
    <>
      {buttonContent}
      {cguDialogOpen && (
        <FiligranAiCguDialog
          open={cguDialogOpen}
          onClose={() => setCguDialogOpen(false)}
        />
      )}
    </>
  );
};

export default AskArianeButton;
