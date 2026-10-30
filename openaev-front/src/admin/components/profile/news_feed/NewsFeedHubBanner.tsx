import { Button, Paper, Text } from '@filigran/design-system';
import { LinkOffOutlined } from '@mui/icons-material';
import { Link } from 'react-router';

import { useFormatter } from '../../../../components/i18n';
import { REDIRECT_CONNECT_XTM_HUB_URL } from '../../../../constants/BaseUrls';

/** Invites the user to connect the product to XTM Hub, which is what delivers the news feed. */
const NewsFeedHubBanner = () => {
  const { t } = useFormatter();

  return (
    <Paper padding={24} style={{ marginBottom: 16 }}>
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        flexWrap: 'wrap',
        gap: 16,
      }}
      >
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          gap: 8,
        }}
        >
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
          }}
          >
            <LinkOffOutlined />
            <Text variant="title-md" as="h2" style={{ margin: 0 }}>{t('XTM Hub is disconnected')}</Text>
          </div>
          <Text variant="content-compact" className="text-default-secondary">
            {t('Please connect yourself so you can receive News Feeds')}
          </Text>
        </div>
        <Button asChild>
          <Link to={REDIRECT_CONNECT_XTM_HUB_URL}>{t('Connect product to XTM Hub')}</Link>
        </Button>
      </div>
    </Paper>
  );
};

export default NewsFeedHubBanner;
