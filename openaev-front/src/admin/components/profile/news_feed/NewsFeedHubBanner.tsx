import { Button, Paper, Text } from '@filigran/design-system';
import { LinkOffOutlined } from '@mui/icons-material';
import { Link } from 'react-router';

import { useFormatter } from '../../../../components/i18n';
import { REDIRECT_CONNECT_XTM_HUB_URL } from '../../../../constants/BaseUrls';

export type NewsFeedHubBannerVariant = 'connect' | 'ask_administrator' | 'unreachable';

interface Props {
  /**
   * `connect` invites to connect the product, `ask_administrator` is for a user who cannot connect it,
   * and `unreachable` is for when XTM Hub cannot be reached: connecting is no use then.
   * Only `connect` offers the button.
   */
  variant?: NewsFeedHubBannerVariant;
}

/** Tells the user the product is not connected to XTM Hub, which is what delivers the news feed. */
const NewsFeedHubBanner = ({ variant = 'connect' }: Props) => {
  const { t } = useFormatter();
  const messages: Record<NewsFeedHubBannerVariant, string> = {
    connect: t('Please connect your product so you can receive News Feeds'),
    ask_administrator: t('Ask an administrator to connect your product to XTM Hub so you can receive News Feeds'),
    unreachable: t('XTM Hub is unreachable and connection can\'t be established'),
  };

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
            {messages[variant]}
          </Text>
        </div>
        {variant === 'connect' && (
          <Button asChild>
            <Link to={REDIRECT_CONNECT_XTM_HUB_URL}>{t('Connect product to XTM Hub')}</Link>
          </Button>
        )}
      </div>
    </Paper>
  );
};

export default NewsFeedHubBanner;
