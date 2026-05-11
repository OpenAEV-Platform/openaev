import { Button, SvgIcon } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { LogoXtmOneIcon } from 'filigran-icon';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../components/i18n';
import useAI from '../../../utils/hooks/useAI';
import useAuth from '../../../utils/hooks/useAuth';
import useEnterpriseEdition from '../../../utils/hooks/useEnterpriseEdition';
import EEChip from '../common/entreprise_edition/EEChip';
import EETooltip from '../common/entreprise_edition/EETooltip';
import AskArianePanel from './AskArianePanel';
import { useChatbot } from './useChatbotHooks';

const AskArianeButton: FunctionComponent = () => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { settings } = useAuth();
  const { isValidated: isEnterpriseEdition, openDialog: openEnterpriseEditionDialog, setEEFeatureDetectedInfo } = useEnterpriseEdition();
  const { enabled, configured, xtmOneConfigured } = useAI();
  const { isOpen, mode, toggleChat, closeChat, setMode, setSidebarWidth, setIsResizing } = useChatbot();

  // When XTM One is configured, AI is provided through it and legacy AI tokens are not required.
  const isAvailable = isEnterpriseEdition && (xtmOneConfigured || (enabled && configured));

  if (!settings.platform_xtm_one_configured) {
    return null;
  }

  const handleClick = () => {
    if (!isEnterpriseEdition) {
      setEEFeatureDetectedInfo(t('Ariane AI'));
      openEnterpriseEditionDialog();
    } else {
      toggleChat();
    }
  };

  return (
    <>
      <EETooltip forAi title={`${t('Ask Ariane')}${!isAvailable ? ' (EE)' : ''}`}>
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
          {!isEnterpriseEdition && <EEChip />}
        </Button>
      </EETooltip>

      {isAvailable && isOpen && (
        <AskArianePanel
          mode={mode}
          onClose={closeChat}
          onModeChange={setMode}
          onWidthChange={setSidebarWidth}
          onResizeStart={() => setIsResizing(true)}
          onResizeEnd={() => setIsResizing(false)}
        />
      )}
    </>
  );
};

export default AskArianeButton;
