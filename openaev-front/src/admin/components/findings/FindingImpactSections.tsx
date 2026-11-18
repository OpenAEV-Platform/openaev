import { Chip } from '@filigran/design-system';
import { DevicesOutlined, PlayCircleOutlineOutlined, RouteOutlined } from '@mui/icons-material';
import { Box, Typography } from '@mui/material';
import { SelectGroup } from 'mdi-material-ui';
import { type ReactElement, type ReactNode, useEffect, useMemo, useState } from 'react';
import { Link, useLocation } from 'react-router';

import chipLinkClassName from '../../../components/common/chips/chipLink';
import { DetailSections, SectionBlock } from '../../../components/common/detail/EntityDetailCommon';
import { initSorting, type Page } from '../../../components/common/queryable/Page';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import { useFormatter } from '../../../components/i18n';
import { ASSET_BASE_URL, ASSET_GROUP_BASE_URL, SCENARIO_BASE_URL, SIMULATION_BASE_URL } from '../../../constants/BaseUrls';
import type { FindingOutput, RelatedFindingOutput, SearchPaginationInput } from '../../../utils/api-types';
import { useAbility } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import { collectFindingImpact, IMPACTED_ASSETS_ANCHOR } from './FindingImpactUtils';
import { buildOccurrencesFilter } from './FindingOccurrencesUtils';

// Same window as the occurrence timeline: the impact is read from the most recent occurrences, and
// the sections say so when a pathological finding has more.
const IMPACT_FETCH_SIZE = 500;

const iconSx = { fontSize: '1rem' };

interface Props {
  searchFindings: (input: SearchPaginationInput) => Promise<{ data: Page<RelatedFindingOutput> }>;
  finding: Pick<FindingOutput, 'finding_type' | 'finding_value'>;
}

const ChipList = ({ children }: { children: ReactNode }) => (
  <Box sx={{
    display: 'flex',
    flexWrap: 'wrap',
    gap: 1,
    alignContent: 'start',
  }}
  >
    {children}
  </Box>
);

// A pivot chip: a real link around the library Chip when the user may open the target, the plain
// chip otherwise. The full name is always shown (no truncation): it is what the user came for.
const PivotChip = ({ label, url, icon }: {
  label: string;
  url?: string;
  icon: ReactElement;
}) => {
  const chip = <Chip startIcon={icon} label={label} />;
  return url
    ? <Link to={url} className={chipLinkClassName}>{chip}</Link>
    : chip;
};

// Pivots of a finding: every asset and asset group it was found on, and the scenarios and
// simulations its occurrences ran in, across all occurrences of the (type, value) group. Each
// section is drawn only when it holds something; the asset sections need the right to read assets.
const FindingImpactSections = ({ searchFindings, finding }: Props) => {
  const { t } = useFormatter();
  const ability = useAbility();
  const location = useLocation();
  const [occurrences, setOccurrences] = useState<RelatedFindingOutput[] | null>(null);
  const [total, setTotal] = useState<number>(0);

  useEffect(() => {
    let cancelled = false;
    searchFindings(buildSearchPagination({
      page: 0,
      size: IMPACT_FETCH_SIZE,
      sorts: initSorting('finding_updated_at', 'DESC'),
      filterGroup: buildOccurrencesFilter(finding),
    })).then((res) => {
      if (cancelled) return;
      setOccurrences(res.data.content ?? []);
      setTotal(res.data.totalElements ?? 0);
    });
    return () => {
      cancelled = true;
    };
  }, [finding.finding_type, finding.finding_value]);

  const impact = useMemo(() => collectFindingImpact(occurrences ?? []), [occurrences]);
  const canReadAssets = ability.can(ACTIONS.ACCESS, SUBJECTS.ASSETS);
  const showAssets = canReadAssets && impact.assets.length > 0;
  const showAssetGroups = canReadAssets && impact.assetGroups.length > 0;
  const showScenarios = impact.scenarios.length > 0;
  const showSimulations = impact.simulations.length > 0;

  // The hero counter links to #impacted-assets: scroll there once the section is drawn.
  useEffect(() => {
    if (showAssets && location.hash === `#${IMPACTED_ASSETS_ANCHOR}`) {
      document.getElementById(IMPACTED_ASSETS_ANCHOR)?.scrollIntoView({
        behavior: 'smooth',
        block: 'start',
      });
    }
  }, [showAssets, location.hash]);

  if (!occurrences || !(showAssets || showAssetGroups || showScenarios || showSimulations)) {
    return null;
  }

  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: 1,
    }}
    >
      <DetailSections>
        {showAssets && (
          <Box id={IMPACTED_ASSETS_ANCHOR} sx={{ scrollMarginTop: 80 }}>
            <SectionBlock title={t('Impacted assets')}>
              <ChipList>
                {impact.assets.map(asset => (
                  <PivotChip
                    key={asset.asset_id}
                    label={asset.asset_name}
                    url={`${ASSET_BASE_URL}/${asset.asset_id}`}
                    icon={<DevicesOutlined sx={iconSx} />}
                  />
                ))}
              </ChipList>
            </SectionBlock>
          </Box>
        )}
        {showAssetGroups && (
          <SectionBlock title={t('Asset groups')}>
            <ChipList>
              {impact.assetGroups.map(group => (
                <PivotChip
                  key={group.asset_group_id}
                  label={group.asset_group_name}
                  url={`${ASSET_GROUP_BASE_URL}/${group.asset_group_id}`}
                  icon={<SelectGroup sx={iconSx} />}
                />
              ))}
            </ChipList>
          </SectionBlock>
        )}
        {showScenarios && (
          <SectionBlock title={t('Scenarios')}>
            <ChipList>
              {impact.scenarios.map(scenario => (
                <PivotChip
                  key={scenario.scenario_id}
                  label={scenario.scenario_name ?? scenario.scenario_id ?? ''}
                  url={ability.can(ACTIONS.ACCESS, SUBJECTS.RESOURCE, scenario.scenario_id)
                    ? `${SCENARIO_BASE_URL}/${scenario.scenario_id}`
                    : undefined}
                  icon={<RouteOutlined sx={iconSx} />}
                />
              ))}
            </ChipList>
          </SectionBlock>
        )}
        {showSimulations && (
          <SectionBlock title={t('Simulations')}>
            <ChipList>
              {impact.simulations.map(simulation => (
                <PivotChip
                  key={simulation.exercise_id}
                  label={simulation.exercise_name}
                  url={ability.can(ACTIONS.ACCESS, SUBJECTS.RESOURCE, simulation.exercise_id)
                    ? `${SIMULATION_BASE_URL}/${simulation.exercise_id}`
                    : undefined}
                  icon={<PlayCircleOutlineOutlined sx={iconSx} />}
                />
              ))}
            </ChipList>
          </SectionBlock>
        )}
      </DetailSections>
      {total > occurrences.length && (
        <Typography variant="caption" sx={{ color: 'text.secondary' }}>
          {t('Showing the {shown} most recent of {total} occurrences.', {
            shown: occurrences.length,
            total,
          })}
        </Typography>
      )}
    </Box>
  );
};

export default FindingImpactSections;
