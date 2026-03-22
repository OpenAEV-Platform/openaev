import { Box, Stack, useTheme } from '@mui/material';
import { type FunctionComponent, type ReactNode } from 'react';

import logoFiligranBaselineDark from '../../../static/images/logo_filigran_baseline_dark.svg';
import logoFiligranBaselineLight from '../../../static/images/logo_filigran_baseline_light.svg';
import logoFiligranGradientDark from '../../../static/images/logo_filigran_gradient_dark.svg';
import logoFiligranGradientLight from '../../../static/images/logo_filigran_gradient_light.svg';

interface LoginLayoutProps {
  children: ReactNode;
  isWhitemarkEnable: boolean;
}

const LoginLayout: FunctionComponent<LoginLayoutProps> = ({
  children,
  isWhitemarkEnable,
}) => {
  const theme = useTheme();
  const isDark = theme.palette.mode === 'dark';

  const defaultAsideGradient = isDark
    ? 'linear-gradient(100deg, #050A14 0%, #0C1728 100%)'
    : 'linear-gradient(100deg, #EAEAED 0%, #FEFEFF 100%)';

  const backgroundContent = theme.palette.designSystem.background.main;

  const logoGradient = isDark
    ? logoFiligranGradientDark
    : logoFiligranGradientLight;

  const logoBaseline = isDark
    ? logoFiligranBaselineDark
    : logoFiligranBaselineLight;

  return (
    <Stack
      data-testid="login-page"
      direction="row"
      sx={{ height: '100vh' }}
    >
      <Stack
        flex={1}
        sx={{
          minWidth: 500,
          overflow: 'hidden',
          background: backgroundContent,
          boxShadow: '8px 0px 9px 0px #0000002F',
          zIndex: 2,
        }}
        justifyContent="center"
        alignItems="center"
        gap={4}
      >
        {children}
      </Stack>
      <Box
        flex={1}
        sx={{
          background: defaultAsideGradient,
          position: 'relative',
          overflow: 'hidden',
        }}
      >
        <img
          src={logoGradient}
          alt=""
          style={{
            userSelect: 'none',
            pointerEvents: 'none',
            height: `calc(100% + ${theme.spacing(10)})`,
            position: 'absolute',
            top: theme.spacing(-5),
            right: theme.spacing(-5),
          }}
        />
        {!isWhitemarkEnable && (
          <img
            src={logoBaseline}
            alt="Made by Filigran"
            width={130}
            style={{
              userSelect: 'none',
              pointerEvents: 'none',
              position: 'absolute',
              bottom: theme.spacing(3),
              left: theme.spacing(3),
              zIndex: 2,
            }}
          />
        )}
      </Box>
    </Stack>
  );
};

export default LoginLayout;
