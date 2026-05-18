import { Button, SvgIcon } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { LogoXtmOneIcon } from 'filigran-icon';

import { useFormatter } from '../../../components/i18n';
import useAuth from '../../../utils/hooks/useAuth';
import { useChatbot } from './useChatbotHooks';

const AskArianeButton = () => {
  const theme = useTheme();
  const { t } = useFormatter();
  const { settings } = useAuth();
  const { isOpen, toggleChat } = useChatbot();

  if (!settings.platform_xtm_one_configured) {
    return null;
  }

  const handleToggle = () => {
    toggleChat();
  };

  return (
    <Button
      variant="outlined"
      size="small"
      onClick={handleToggle}
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
    </Button>
  );
};

export default AskArianeButton;
