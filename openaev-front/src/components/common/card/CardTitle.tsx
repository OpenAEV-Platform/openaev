import { Stack, type StackProps, type SxProps, Typography, useTheme } from '@mui/material';
import { type PropsWithChildren, type ReactNode } from 'react';

interface CardTitleProps extends PropsWithChildren {
  action?: ReactNode;
  alignItems?: StackProps['alignItems'];
  sx?: SxProps;
}

const CardTitle = ({
  children,
  alignItems = 'center',
  action,
  sx = {},
}: CardTitleProps) => {
  const theme = useTheme();

  const containerSx: SxProps = {
    height: alignItems !== 'center' ? 'inherit' : '19px',
    marginBottom: theme.spacing(1),
    flex: 0,
    ...sx,
  };

  const titleSx: SxProps = {
    marginBottom: 0,
    textTransform: 'capitalize',
    color: theme.palette.text.light,
    lineHeight: '19px',
    fontSize: '12px',
    fontWeight: 400,
    fontFamily: '"IBM Plex Sans", sans-serif',
  };

  return (
    <Stack
      direction="row"
      justifyContent="space-between"
      alignItems={alignItems}
      sx={containerSx}
    >
      <Typography variant="h5" sx={titleSx}>
        {children}
      </Typography>
      <Stack direction="row" gap={1}>
        {action}
      </Stack>
    </Stack>
  );
};

export default CardTitle;
