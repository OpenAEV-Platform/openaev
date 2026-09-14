import { PlayArrowRounded } from '@mui/icons-material';
import { Box, Typography } from '@mui/material';
import { alpha } from '@mui/material/styles';
import { type FunctionComponent, type ReactNode } from 'react';

import { type Document } from '../../../../utils/api-types';
import { colorOrFallback } from '../../../../utils/Colors';
import { buildTenantApiPath } from '../../../../utils/url-helper';

// The preview renders the channel exactly as readers will see it, in the
// channel's own theme (dark or light) regardless of the current app theme.
// Both surfaces are therefore intentionally fixed: the dark one mirrors the
// platform's default dark background, the light one is plain white.
interface Surface {
  background: string;
  card: string;
  text: string;
  muted: string;
  block: string;
  border: string;
}

const SURFACES: Record<'dark' | 'light', Surface> = {
  dark: {
    background: '#070d19',
    card: '#0d1526',
    text: 'rgba(255,255,255,0.92)',
    muted: 'rgba(255,255,255,0.5)',
    block: 'rgba(255,255,255,0.1)',
    border: 'rgba(255,255,255,0.08)',
  },
  light: {
    background: '#ffffff',
    card: '#f5f6f8',
    text: 'rgba(0,0,0,0.87)',
    muted: 'rgba(0,0,0,0.5)',
    block: 'rgba(0,0,0,0.08)',
    border: 'rgba(0,0,0,0.1)',
  },
};

export interface ChannelPreviewData {
  channel_type?: string;
  channel_name?: string;
  channel_description?: string;
  channel_mode?: string;
  channel_primary_color_dark?: string;
  channel_primary_color_light?: string;
  channel_secondary_color_dark?: string;
  channel_secondary_color_light?: string;
  logoDark?: Document | null;
  logoLight?: Document | null;
}

interface Props {
  channel: ChannelPreviewData;
  mode: 'dark' | 'light';
}

// A grey placeholder line standing in for future article text.
const MockLine = ({ width, height = 9, color }: {
  width: string | number;
  height?: number;
  color: string;
}) => (
  <Box sx={{
    width,
    height,
    borderRadius: 0.5,
    backgroundColor: color,
  }}
  />
);

// A framed content card of the mocked front page.
const MockCard = ({ surface, children }: {
  surface: Surface;
  children: ReactNode;
}) => (
  <Box sx={{
    display: 'flex',
    flexDirection: 'column',
    gap: 1,
    padding: 1.5,
    borderRadius: 1,
    backgroundColor: surface.card,
    border: `1px solid ${surface.border}`,
    minWidth: 0,
  }}
  >
    {children}
  </Box>
);

const ChannelPreview: FunctionComponent<Props> = ({ channel, mode }) => {
  const surface = SURFACES[mode];
  const primary = colorOrFallback(mode === 'dark' ? channel.channel_primary_color_dark : channel.channel_primary_color_light, surface.text);
  const secondary = colorOrFallback(mode === 'dark' ? channel.channel_secondary_color_dark : channel.channel_secondary_color_light, surface.muted);
  const logo = mode === 'dark' ? channel.logoDark : channel.logoLight;
  const showLogo = channel.channel_mode !== 'title' && !!logo;
  const showTitle = channel.channel_mode !== 'logo' || !logo;

  const tag = (width: number, color: string) => (
    <Box sx={{
      width,
      height: 14,
      borderRadius: 0.5,
      backgroundColor: alpha(color, 0.18),
      border: `1px solid ${alpha(color, 0.45)}`,
    }}
    />
  );

  const avatarAndLines = (
    <Box sx={{
      display: 'flex',
      alignItems: 'center',
      gap: 1,
    }}
    >
      <Box sx={{
        width: 28,
        height: 28,
        borderRadius: '50%',
        flexShrink: 0,
        backgroundColor: alpha(primary, 0.25),
        border: `1px solid ${alpha(primary, 0.5)}`,
      }}
      />
      <Box sx={{
        display: 'flex',
        flexDirection: 'column',
        gap: 0.5,
        flex: 1,
      }}
      >
        <MockLine width="35%" color={surface.block} />
        <MockLine width="20%" height={7} color={alpha(surface.muted, 0.25)} />
      </Box>
    </Box>
  );

  const newspaper = (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: 1.5,
    }}
    >
      <Box sx={{
        display: 'grid',
        gridTemplateColumns: '2fr 1fr',
        gap: 1.5,
      }}
      >
        {/* Featured article */}
        <MockCard surface={surface}>
          <Box sx={{
            height: 140,
            borderRadius: 1,
            backgroundColor: surface.block,
          }}
          />
          {tag(64, primary)}
          <MockLine width="90%" height={12} color={alpha(surface.text, 0.35)} />
          <MockLine width="100%" color={surface.block} />
          <MockLine width="70%" color={surface.block} />
        </MockCard>
        {/* Headlines column */}
        <Box sx={{
          display: 'flex',
          flexDirection: 'column',
          gap: 1.5,
        }}
        >
          {[0, 1, 2].map(index => (
            <MockCard key={index} surface={surface}>
              {tag(48, index === 1 ? secondary : primary)}
              <MockLine width="95%" color={alpha(surface.text, 0.3)} />
              <MockLine width="60%" color={surface.block} />
            </MockCard>
          ))}
        </Box>
      </Box>
      <Box sx={{
        display: 'grid',
        gridTemplateColumns: 'repeat(3, 1fr)',
        gap: 1.5,
      }}
      >
        {[0, 1, 2].map(index => (
          <MockCard key={index} surface={surface}>
            <Box sx={{
              height: 64,
              borderRadius: 1,
              backgroundColor: surface.block,
            }}
            />
            {tag(56, index === 2 ? secondary : primary)}
            <MockLine width="90%" color={alpha(surface.text, 0.3)} />
            <MockLine width="65%" color={surface.block} />
          </MockCard>
        ))}
      </Box>
    </Box>
  );

  const microblogging = (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: 1.5,
      maxWidth: 480,
      margin: '0 auto',
      width: '100%',
    }}
    >
      {[0, 1, 2].map(index => (
        <MockCard key={index} surface={surface}>
          {avatarAndLines}
          <MockLine width="100%" color={surface.block} />
          <MockLine width="85%" color={surface.block} />
          {index === 1 && (
            <Box sx={{
              height: 110,
              borderRadius: 1,
              backgroundColor: surface.block,
            }}
            />
          )}
          <Box sx={{
            display: 'flex',
            gap: 2,
            marginTop: 0.5,
          }}
          >
            {[0, 1, 2].map(action => (
              <Box
                key={action}
                sx={{
                  width: 24,
                  height: 8,
                  borderRadius: 0.5,
                  backgroundColor: alpha(secondary, 0.35),
                }}
              />
            ))}
          </Box>
        </MockCard>
      ))}
    </Box>
  );

  const tv = (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: 1.5,
    }}
    >
      {/* Player */}
      <Box sx={{
        position: 'relative',
        aspectRatio: '16 / 8',
        borderRadius: 1,
        backgroundColor: mode === 'dark' ? '#000000' : '#111827',
        border: `1px solid ${surface.border}`,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        overflow: 'hidden',
      }}
      >
        <Box sx={{
          width: 52,
          height: 52,
          borderRadius: '50%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          backgroundColor: alpha(primary, 0.9),
          boxShadow: `0 0 24px ${alpha(primary, 0.5)}`,
        }}
        >
          <PlayArrowRounded sx={{
            color: '#ffffff',
            fontSize: 32,
          }}
          />
        </Box>
        {/* Progress bar */}
        <Box sx={{
          position: 'absolute',
          left: 12,
          right: 12,
          bottom: 10,
          height: 4,
          borderRadius: 2,
          backgroundColor: 'rgba(255,255,255,0.25)',
        }}
        >
          <Box sx={{
            width: '35%',
            height: '100%',
            borderRadius: 2,
            backgroundColor: primary,
          }}
          />
        </Box>
        {/* Live badge */}
        <Box sx={{
          position: 'absolute',
          top: 10,
          left: 12,
          width: 42,
          height: 16,
          borderRadius: 0.5,
          backgroundColor: alpha(secondary, 0.85),
        }}
        />
      </Box>
      <Box sx={{
        display: 'grid',
        gridTemplateColumns: 'repeat(3, 1fr)',
        gap: 1.5,
      }}
      >
        {[0, 1, 2].map(index => (
          <MockCard key={index} surface={surface}>
            <Box sx={{
              height: 56,
              borderRadius: 1,
              backgroundColor: surface.block,
            }}
            />
            <MockLine width="90%" color={alpha(surface.text, 0.3)} />
            <MockLine width="55%" height={7} color={surface.block} />
          </MockCard>
        ))}
      </Box>
    </Box>
  );

  return (
    <Box
      data-testid="channel-preview"
      sx={{
        backgroundColor: surface.background,
        borderRadius: 1,
        padding: 3,
        display: 'flex',
        flexDirection: 'column',
        gap: 2,
        minHeight: 480,
      }}
    >
      {/* Masthead: logo and/or title depending on the channel header mode */}
      <Box sx={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        gap: 0.5,
        paddingBottom: 2,
        borderBottom: `2px solid ${alpha(primary, 0.5)}`,
      }}
      >
        {showLogo && logo && (
          <img
            src={buildTenantApiPath(`/api/documents/${logo.document_id}/file`)}
            alt={channel.channel_name}
            style={{
              maxHeight: 60,
              maxWidth: 240,
              objectFit: 'contain',
            }}
          />
        )}
        {showTitle && (
          <Typography sx={{
            fontFamily: '"Geologica", sans-serif',
            fontWeight: 700,
            fontSize: 30,
            lineHeight: 1.15,
            textAlign: 'center',
            color: primary,
          }}
          >
            {channel.channel_name}
          </Typography>
        )}
        {channel.channel_description && (
          <Typography sx={{
            fontSize: 13,
            textAlign: 'center',
            color: surface.muted,
          }}
          >
            {channel.channel_description}
          </Typography>
        )}
      </Box>
      {channel.channel_type === 'microblogging' && microblogging}
      {channel.channel_type === 'tv' && tv}
      {(channel.channel_type === 'newspaper' || !channel.channel_type) && newspaper}
    </Box>
  );
};

export default ChannelPreview;
