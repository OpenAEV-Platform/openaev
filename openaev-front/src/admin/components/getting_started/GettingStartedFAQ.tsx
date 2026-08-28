import { ExpandMore, ExtensionOutlined, InsightsOutlined, RocketLaunchOutlined, SupportAgentOutlined } from '@mui/icons-material';
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Box,
  Paper,
  Typography,
} from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type ComponentType } from 'react';
import ReactMarkdown from 'react-markdown';

import { SECTION_LABEL_SX } from '../../../components/common/detail/detailStyles';
import { useFormatter } from '../../../components/i18n';
import GettingStartedSectionHeader from './GettingStartedSectionHeader';

interface FAQQuestion {
  summary: string;
  details: string;
  subdetailsList?: string[];
}

interface FAQCategory {
  category: string;
  icon: ComponentType<{ sx?: object }>;
  questions: FAQQuestion[];
}

// One FAQ category rendered as its own titled card so the questions read in
// balanced columns instead of a single viewport-wide accordion stack.
const FAQCategoryCard = ({ category }: { category: FAQCategory }) => {
  const theme = useTheme();
  const Icon = category.icon;

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      height: '100%',
    }}
    >
      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1,
        marginBottom: 1.5,
      }}
      >
        <Icon sx={{
          fontSize: 15,
          color: 'primary.main',
        }}
        />
        <Typography sx={{
          ...SECTION_LABEL_SX,
          marginBottom: 0,
        }}
        >
          {category.category}
        </Typography>
        <Box sx={{
          fontSize: 10.5,
          fontWeight: 600,
          lineHeight: 1,
          padding: theme.spacing(0.5, 0.75),
          borderRadius: 0.5,
          color: 'text.secondary',
          backgroundColor: alpha(theme.palette.text.primary, 0.08),
        }}
        >
          {category.questions.length}
        </Box>
      </Box>
      <Paper
        variant="outlined"
        sx={{
          borderRadius: 1,
          overflow: 'hidden',
          flex: 1,
        }}
      >
        {category.questions.map((faq, idx) => (
          <Accordion
            key={faq.summary}
            elevation={0}
            disableGutters
            sx={{
              'backgroundColor': 'transparent',
              'backgroundImage': 'none',
              '&:before': { display: 'none' },
              'borderTop': idx === 0 ? 'none' : `1px solid ${alpha(theme.palette.text.primary, 0.06)}`,
            }}
          >
            <AccordionSummary expandIcon={<ExpandMore sx={{ fontSize: 18 }} />}>
              <Typography sx={{
                fontSize: 13,
                fontWeight: 500,
              }}
              >
                {faq.summary}
              </Typography>
            </AccordionSummary>
            <AccordionDetails sx={{ paddingTop: 0 }}>
              <Box sx={{
                'fontSize': 12.5,
                'lineHeight': 1.6,
                'color': 'text.secondary',
                '& p': { margin: theme.spacing(0, 0, 1) },
                '& p:last-child': { marginBottom: 0 },
                '& ul': {
                  margin: theme.spacing(0.5, 0, 0),
                  paddingLeft: theme.spacing(2.5),
                },
              }}
              >
                <ReactMarkdown
                  components={{
                    a: ({ ...props }) => (
                      <a {...props} target="_blank" rel="noopener noreferrer">
                        {props.children}
                      </a>
                    ),
                  }}
                >
                  {faq.subdetailsList?.length
                    ? faq.details + '\n' + faq.subdetailsList.map(subdetail => '- ' + subdetail).join('\n')
                    : faq.details}
                </ReactMarkdown>
              </Box>
            </AccordionDetails>
          </Accordion>
        ))}
      </Paper>
    </div>
  );
};

const GettingStartedFAQ = () => {
  const { t } = useFormatter();

  const faqCategories: FAQCategory[] = [
    {
      category: t('faq.usage.title'),
      icon: RocketLaunchOutlined,
      questions: [
        {
          summary: t('faq.howto.import_scenario.summary'),
          details: t('faq.howto.import_scenario.details'),
        },
        {
          summary: t('faq.howto.run_scenario.summary'),
          details: t('faq.howto.run_scenario.details'),
        },
        {
          summary: t('faq.howto.create_scenario.summary'),
          details: t('faq.howto.create_scenario.details'),
        },
        {
          summary: t('faq.question.simulations_production.summary'),
          details: t('faq.question.simulations_production.details'),
        },
        {
          summary: t('faq.question.inject_missing_content.summary'),
          details: t('faq.question.inject_missing_content.details'),
        },
        {
          summary: t('faq.question.share_scenarios.summary'),
          details: t('faq.question.share_scenarios.details'),
        },
      ],
    },
    {
      category: t('faq.results.title'),
      icon: InsightsOutlined,
      questions: [
        {
          summary: t('faq.howto.understand_results.summary'),
          details: t('faq.howto.understand_results.details'),
          subdetailsList: [
            t('faq.howto.understand_results.home_dashboard.details'),
            t('faq.howto.understand_results.scenario.details'),
            t('faq.howto.understand_results.simulation.details'),
            t('faq.howto.understand_results.inject_results.details'),
          ],
        },
        {
          summary: t('faq.question.expectations_expire.summary'),
          details: t('faq.question.expectations_expire.details'),
        },
      ],
    },
    {
      category: t('faq.components.title'),
      icon: ExtensionOutlined,
      questions: [
        {
          summary: t('faq.question.executor_injectors_collectors.summary'),
          details: t('faq.question.executor_injectors_collectors.details'),
        },
      ],
    },
    {
      category: t('faq.support.title'),
      icon: SupportAgentOutlined,
      questions: [
        {
          summary: t('faq.question.get_help.summary'),
          details: t('faq.question.get_help.details'),
        },
      ],
    },
  ];

  // Usage is by far the longest category: give it its own column and stack the
  // three short categories in the second column so both sides finish at
  // roughly the same height.
  const [usage, ...others] = faqCategories;

  return (
    <div>
      <GettingStartedSectionHeader
        title={t('getting_started_faq')}
        subtitle={t('getting_started_faq_explanation')}
      />
      <Box sx={{
        display: 'grid',
        gap: 2.5,
        marginTop: 2,
        alignItems: 'start',
        gridTemplateColumns: {
          xs: 'minmax(0, 1fr)',
          md: 'repeat(2, minmax(0, 1fr))',
        },
      }}
      >
        <FAQCategoryCard category={usage} />
        <Box sx={{
          display: 'flex',
          flexDirection: 'column',
          gap: 2.5,
        }}
        >
          {others.map(category => (
            <FAQCategoryCard key={category.category} category={category} />
          ))}
        </Box>
      </Box>
    </div>
  );
};

export default GettingStartedFAQ;
