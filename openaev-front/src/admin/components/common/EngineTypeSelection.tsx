import { AccountTreeOutlined, TimelineOutlined } from '@mui/icons-material';
import { Card, CardActionArea, CardContent, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import Button from '../../../components/common/button/Button';
import { useFormatter } from '../../../components/i18n';

interface EngineTypeSelectionProps {
  onSelect: (isChaining: boolean) => void;
  onCancel: () => void;
}

const EngineTypeSelection: FunctionComponent<EngineTypeSelectionProps> = ({
  onSelect,
  onCancel,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const options: Array<{
    isChaining: boolean;
    icon: typeof TimelineOutlined;
    title: string;
    description: string;
  }> = [
    {
      isChaining: false,
      icon: TimelineOutlined,
      title: t('Time-based'),
      description: t('Scheduled inject execution based on a timeline.'),
    },
    {
      isChaining: true,
      icon: AccountTreeOutlined,
      title: t('Chaining'),
      description: t('Dynamic inject execution based on conditions and step outputs.'),
    },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: theme.spacing(3) }}>
      <Typography variant="body1">
        {t('Select the type you want to create.')}
      </Typography>
      <div
        style={{
          display: 'grid',
          gap: theme.spacing(3),
          gridTemplateColumns: '1fr 1fr',
        }}
      >
        {options.map((option) => {
          const Icon = option.icon;
          return (
            <Card
              key={option.title}
              variant="outlined"
              sx={{
                textAlign: 'center',
                transition: 'border-color 0.2s',
                '&:hover': {
                  borderColor: theme.palette.primary.main,
                },
              }}
            >
              <CardActionArea
                onClick={() => onSelect(option.isChaining)}
                sx={{ height: '100%', padding: theme.spacing(3) }}
              >
                <CardContent sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: theme.spacing(1) }}>
                  <Icon sx={{ fontSize: 48, color: theme.palette.primary.main }} />
                  <Typography variant="h6">
                    {option.title}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    {option.description}
                  </Typography>
                </CardContent>
              </CardActionArea>
            </Card>
          );
        })}
      </div>
      <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
        <Button variant="secondary" onClick={onCancel}>
          {t('Cancel')}
        </Button>
      </div>
    </div>
  );
};

export default EngineTypeSelection;



