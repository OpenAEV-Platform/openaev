import { ArrowForwardIos, CheckCircle, RadioButtonUnchecked, RemoveCircleOutline } from '@mui/icons-material';
import { Box, Button, ClickAwayListener, Divider, List, ListItem, ListItemButton, ListItemText, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import type React from 'react';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import { useLocalStorage } from 'usehooks-ts';

import { getOnboardingConfig, getOnboardingProgress, skippedCategory } from '../../../actions/onboarding/onboarding-action';
import { type OnboardingHelper } from '../../../actions/onboarding/onboarding-helper';
import { updateMeOnboarding } from '../../../actions/User';
import { useFormatter } from '../../../components/i18n';
import { BACKGROUND_COLOR_GREY } from '../../../components/ThemeDark';
import { ADMIN_BASE_URL } from '../../../constants/BaseUrls';
import { useHelper } from '../../../store';
import { type OnboardingCategoryDTO, type OnboardingItemDTO, type UserOnboardingProgress } from '../../../utils/api-types';
import { useAppDispatch } from '../../../utils/hooks';
import useAuth from '../../../utils/hooks/useAuth';
import useDataLoader from '../../../utils/hooks/useDataLoader';
import { OnboardingConfigIconMap, OnboardingConfigUriMap } from './onboarding-utils';
import { ONBOARDING_WELCOME_DIALOG_KEY } from './OnboardingWelcomeDialog';

const THEME_DARK_ONBOARDING_HEADER = '#0C3545';

interface Props { onCloseSidebar: () => void }

const OnboardingProgressSidebar: React.FC<Props> = ({ onCloseSidebar }) => {
  const { t } = useFormatter();
  const { me } = useAuth();
  const navigate = useNavigate();
  const theme = useTheme();
  const dispatch = useAppDispatch();
  const [progress, setProgress] = useState<number>(0);
  const [total, setTotal] = useState<number>(0);
  const [onboardingConfig, setOnboardingConfig] = useState<OnboardingCategoryDTO[]>();
  const [_, setDisplayOnboardingWelcome] = useLocalStorage<boolean>(ONBOARDING_WELCOME_DIALOG_KEY, true);

  // Fetching data
  const { onboarding }: { onboarding: UserOnboardingProgress } = useHelper((helper: OnboardingHelper) => ({ onboarding: helper.getOnboarding() }));
  useDataLoader(() => {
    dispatch(getOnboardingProgress());
  });

  useEffect(() => {
    getOnboardingConfig().then(result => setOnboardingConfig(result.data));
  }, []);

  useEffect(() => {
    if (!onboarding) {
      setProgress(1);
      setTotal((onboardingConfig?.flatMap(i => i.items).length ?? 0) + 1);
    } else {
      setProgress((onboarding.progress ?? []).filter(i => i.completed || i.skipped).length + 1);
      setTotal((onboarding.progress ?? []).length + 1);
    }
  }, [onboarding, onboardingConfig]);

  const completed = (item: OnboardingItemDTO) => {
    if (!onboarding) return false;
    return onboarding.progress.find(i => i.step === item.labelKey)?.completed;
  };

  const skipped = (item: OnboardingItemDTO) => {
    if (!onboarding) return false;
    return onboarding.progress.find(i => i.step === item.labelKey)?.skipped;
  };

  const renderStatusIcon = (item: OnboardingItemDTO) => {
    if (completed(item)) return <CheckCircle color="primary" />;
    if (skipped(item)) return <RemoveCircleOutline sx={{ color: '#888' }} />;
    return <RadioButtonUnchecked />;
  };

  const handleOnEnd = () => {
    dispatch(updateMeOnboarding('DISABLED', me.user_onboarding_contextual_help_enable));
    onCloseSidebar();
  };

  return (
    <ClickAwayListener onClickAway={onCloseSidebar}>
      <Box
        display="flex"
        flexDirection="column"
      >
        <Box
          bgcolor={THEME_DARK_ONBOARDING_HEADER}
          p={2}
          display="flex"
          alignItems="center"
          justifyContent="space-between"
        >
          <div>
            <Typography variant="body2" fontWeight="bold">
              { progress < total ? t('onboarding_almost_ready') : t('onboarding_ready')}
            </Typography>
            { progress < total
              && (
                <Typography variant="caption">
                  {t('onboarding_finish_setup')}
                </Typography>
              )}
          </div>
        </Box>
        <Box
          bgcolor={BACKGROUND_COLOR_GREY}
          flex={1}
          overflow="auto"
        >
          <List>
            <ListItemButton
              onClick={() => {
                onCloseSidebar();
                navigate(ADMIN_BASE_URL);
                setDisplayOnboardingWelcome(true);
              }}
              sx={{
                'justifyContent': 'space-between',
                '&:hover .arrow-hover': { opacity: 1 },
                'marginBottom': theme.spacing(1),
              }}
            >
              <Box display="flex" flexDirection="row" gap={theme.spacing(1)}>
                <CheckCircle color="primary" />
                <ListItemText primary={<Typography variant="body2" sx={{ textDecoration: 'line-through' }}>{t('onboarding_discover_obas')}</Typography>} />
              </Box>
              <ArrowForwardIos
                color="primary"
                fontSize="small"
                className="arrow-hover"
                sx={{
                  opacity: 0,
                  transition: 'opacity 0.2s ease-in-out',
                }}
              />
            </ListItemButton>
            {onboardingConfig?.map((category) => {
              const Icon = OnboardingConfigIconMap[category.icon];
              return (
                <Box key={category.category} mb={2}>
                  <Box display="flex" alignItems="center" justifyContent="space-between" pl={2} pr={2}>
                    <Box display="flex" alignItems="center" gap={1}>
                      <Typography
                        variant="body2"
                        sx={{ color: theme.typography.h4.color }}
                      >
                        <Icon />
                      </Typography>
                      <Typography
                        variant="body2"
                        sx={{ color: theme.typography.h4.color }}
                      >
                        {t(category.category)}
                      </Typography>
                    </Box>
                    <Button
                      type="button"
                      variant="text"
                      size="small"
                      onClick={() => dispatch(skippedCategory(category.items.map(i => i.labelKey)))}
                    >
                      {t('Skip')}
                    </Button>

                  </Box>
                  <List
                    disablePadding
                    sx={{
                      display: 'flex',
                      flexDirection: 'column',
                    }}
                  >
                    {category.items.map(item => (
                      <ListItem key={item.labelKey} disablePadding>
                        <ListItemButton
                          onClick={() => {
                            navigate(OnboardingConfigUriMap[item.labelKey]);
                            onCloseSidebar();
                          }}
                          sx={{
                            'justifyContent': 'space-between',
                            '&:hover .arrow-hover': { opacity: 1 },
                          }}
                        >
                          <Box display="flex" flexDirection="row" gap={theme.spacing(1)}>
                            {renderStatusIcon(item)}
                            <ListItemText
                              primary={<Typography variant="body2" sx={{ textDecoration: completed(item) ? 'line-through' : 'none' }}>{t(item.labelKey)}</Typography>}
                            />
                          </Box>
                          <ArrowForwardIos
                            color="primary"
                            fontSize="small"
                            className="arrow-hover"
                            sx={{
                              opacity: 0,
                              transition: 'opacity 0.2s ease-in-out',
                            }}
                          />
                        </ListItemButton>
                      </ListItem>
                    ))}
                  </List>
                </Box>
              );
            })}
          </List>
        </Box>
        <Divider />
        <Box bgcolor={BACKGROUND_COLOR_GREY} p={1} display="flex" justifyContent="end" gap={1}>
          <Button onClick={handleOnEnd}>{t('onboarding_hide')}</Button>
        </Box>
      </Box>
    </ClickAwayListener>
  );
};

export default OnboardingProgressSidebar;
