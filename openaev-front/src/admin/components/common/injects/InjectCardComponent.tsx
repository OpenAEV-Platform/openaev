import { Card, CardContent, CardHeader } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import type { FunctionComponent } from 'react';

interface Props {
  avatar: React.ReactNode;
  title: string | undefined;
  disabled?: boolean;
  action: React.ReactNode;
  content: string;
}

const InjectCardComponent: FunctionComponent<Props> = ({
  avatar,
  title,
  action,
  content,
  disabled,
}) => {
  const theme = useTheme();

  return (
    <Card elevation={0} sx={{ marginBottom: theme.spacing(4) }}>
      <CardHeader
        sx={{ backgroundColor: theme.palette.background.default }}
        avatar={avatar}
        title={title}
        action={action}
      />
      <CardContent sx={{
        fontSize: theme.typography.h6.fontSize,
        textAlign: 'center',
        ...disabled && { color: theme.palette?.text?.disabled },
        ...disabled && { fontStyle: 'italic' },
      }}
      >
        {content}
      </CardContent>
    </Card>
  );
};

export default InjectCardComponent;
