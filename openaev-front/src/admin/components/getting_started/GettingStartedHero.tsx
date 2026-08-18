import { Paper as FdsPaper } from '@filigran/design-system';
import { OpenInNew, RocketLaunchOutlined } from '@mui/icons-material';
import { Box, Button, Link as MUILink, Paper, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { Link } from 'react-router';

import { SECTION_LABEL_SX } from '../../../components/common/detail/detailStyles';
import LIB_SURFACE_BORDER from '../../../components/common/libSurfaceBorder';
import { useFormatter } from '../../../components/i18n';
import { SCENARIO_BASE_URL } from '../../../constants/BaseUrls';
import { XTM_HUB_DEFAULT_URL } from '../../../utils/Environment';
import VideoPlayer from './VideoPlayer';

const VIDEO_LINK = 'https://www.youtube.com/embed/wb_v7sa7y8w?rel=0&modestbranding=1&loop=1&playlist=wb_v7sa7y8w';

// A single numbered step of the onboarding journey ("how it works" strip).
const JourneyStep = ({ index, title, children }: {
  index: number;
  title: string;
  children: string;
}) => {
  return (
    <FdsPaper
      padding={16}
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 8,
      }}
    >
      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1.5,
      }}
      >
        <Typography sx={{
          fontFamily: '"Geologica", sans-serif',
          fontSize: 20,
          fontWeight: 600,
          lineHeight: 1,
          color: 'primary.main',
        }}
        >
          {String(index).padStart(2, '0')}
        </Typography>
        <Typography sx={{
          fontSize: 13.5,
          fontWeight: 600,
          lineHeight: 1.3,
        }}
        >
          {title}
        </Typography>
      </Box>
      <Typography sx={{
        fontSize: 12.5,
        lineHeight: 1.55,
        color: 'text.secondary',
      }}
      >
        {children}
      </Typography>
    </FdsPaper>
  );
};

// The landing hero: headline + description + CTAs on the left, product video
// on the right, and the three-step journey strip beneath - one balanced block
// instead of the previous text wall.
const GettingStartedHero = () => {
  const { t } = useFormatter();
  const theme = useTheme();

  return (
    <Paper
      variant="outlined"
      sx={{
        position: 'relative',
        overflow: 'hidden',
        borderRadius: 1,
        padding: 3,
        display: 'flex',
        flexDirection: 'column',
        gap: 3,
        // Stays on MUI: its two radial gradients are the screen's identity and
        // the library Paper paints no gradient. Only the border is aligned, so
        // the block reads as one with its converted neighbours.
        border: LIB_SURFACE_BORDER,
        background: `radial-gradient(ellipse at top left, ${alpha(theme.palette.primary.main, 0.08)} 0%, transparent 55%),
          radial-gradient(ellipse at bottom right, ${alpha(theme.palette.secondary.main, 0.05)} 0%, transparent 55%)`,
      }}
    >
      <Box sx={{
        display: 'grid',
        gap: 4,
        alignItems: 'center',
        gridTemplateColumns: {
          xs: 'minmax(0, 1fr)',
          md: 'minmax(0, 1.15fr) minmax(0, 1fr)',
        },
      }}
      >
        <Box sx={{
          display: 'flex',
          flexDirection: 'column',
          gap: 2,
        }}
        >
          <Typography sx={{
            ...SECTION_LABEL_SX,
            marginBottom: 0,
            color: 'primary.main',
          }}
          >
            {t('Getting Started')}
          </Typography>
          <div>
            <Typography sx={{
              fontFamily: '"Geologica", sans-serif',
              fontSize: 28,
              fontWeight: 500,
              lineHeight: 1.15,
            }}
            >
              {t('getting_started_hero_title')}
            </Typography>
            <Typography sx={{
              fontFamily: '"Geologica", sans-serif',
              fontSize: 16,
              fontWeight: 300,
              marginTop: 0.75,
              color: 'text.secondary',
            }}
            >
              {t('getting_started_hero_subtitle')}
            </Typography>
          </div>
          <Typography sx={{
            fontSize: 13.5,
            lineHeight: 1.6,
            color: 'text.secondary',
            maxWidth: 720,
          }}
          >
            {t('getting_started_description_text')}
          </Typography>
          <Box sx={{
            display: 'flex',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: 1.5,
            marginTop: 0.5,
          }}
          >
            <Button
              variant="contained"
              color="primary"
              component={Link}
              to={SCENARIO_BASE_URL}
              startIcon={<RocketLaunchOutlined />}
            >
              {t('getting_started_browse_scenarios')}
            </Button>
            <Button
              variant="outlined"
              color="primary"
              href={`${XTM_HUB_DEFAULT_URL}/cybersecurity-solutions/open-bas-scenarios`}
              target="_blank"
              rel="noopener noreferrer"
              endIcon={<OpenInNew />}
            >
              {t('XTM Hub Library')}
            </Button>
          </Box>
        </Box>
        <VideoPlayer videoLink={VIDEO_LINK} />
      </Box>
      <div>
        <Typography sx={SECTION_LABEL_SX}>
          {t('getting_started_how_it_works')}
        </Typography>
        <Box sx={{
          display: 'grid',
          gap: 2,
          gridTemplateColumns: {
            xs: 'minmax(0, 1fr)',
            md: 'repeat(3, minmax(0, 1fr))',
          },
        }}
        >
          <JourneyStep index={1} title={t('getting_started_step_import')}>
            {t('getting_started_description_first_task_text')}
          </JourneyStep>
          <JourneyStep index={2} title={t('getting_started_step_launch')}>
            {t('getting_started_description_scenario_text')}
          </JourneyStep>
          <JourneyStep index={3} title={t('getting_started_step_results')}>
            {t('getting_started_description_end_text')}
          </JourneyStep>
        </Box>
        <Typography sx={{
          fontSize: 12.5,
          color: 'text.secondary',
          marginTop: 2,
        }}
        >
          {t('getting_started_description_conclusion_text')}
          {' '}
          {t('getting_started_description_test_scenarios_text', {
            xtmHubLink: (
              <MUILink
                href={`${XTM_HUB_DEFAULT_URL}/cybersecurity-solutions/open-bas-scenarios`}
                target="_blank"
                rel="noopener noreferrer"
              >
                {t('XTM Hub Library')}
              </MUILink>
            ),
          })}
        </Typography>
      </div>
    </Paper>
  );
};

export default GettingStartedHero;
