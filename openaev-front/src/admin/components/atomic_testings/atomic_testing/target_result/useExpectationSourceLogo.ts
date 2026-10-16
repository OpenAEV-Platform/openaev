import { useTheme } from '@mui/material/styles';
import { type SyntheticEvent } from 'react';

import { type SecurityPlatformHelper } from '../../../../../actions/assets/asset-helper';
import { fetchSecurityPlatforms } from '../../../../../actions/assets/securityPlatform-actions';
import { type CollectorHelper } from '../../../../../actions/collectors/collector-helper';
import { useHelper } from '../../../../../store';
import { type InjectExpectationResult, type SecurityPlatform } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import { useAbility } from '../../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../../utils/permissions/types';
import { buildTenantApiPath } from '../../../../../utils/url-helper';

// Generic detector icon (also the server-side fallback for platforms without
// logo documents). Served by collector *type*, so it keeps working even when
// every collector row has been deleted.
const GENERIC_DETECTOR_LOGO_PATH = '/api/collectors/openaev_fake_detector/image';

/**
 * Resolution of the icon and the security platform behind an expectation result
 * line. Collector-written results reference the collector by id, but collectors
 * are transient (deleting a stopped collector is a normal operation): the icon
 * must primarily come from the security platform, which outlives its collector.
 */
const useExpectationSourceLogo = () => {
  const theme = useTheme();
  const dispatch = useAppDispatch();
  const ability = useAbility();

  const collectorsMap = useHelper((helper: CollectorHelper) => helper.getCollectorsMap());
  const securityPlatforms: SecurityPlatform[] = useHelper((helper: SecurityPlatformHelper) => helper.getSecurityPlatforms());
  useDataLoader(() => {
    if (ability.can(ACTIONS.ACCESS, SUBJECTS.SECURITY_PLATFORMS)) {
      dispatch(fetchSecurityPlatforms());
    }
  });

  // Security-platform sources carry the platform id directly; collector sources
  // go through the live collector link; results written by a since-deleted
  // collector fall back to a name match against the (surviving) platforms.
  const resolveSecurityPlatformId = (expectationResult: InjectExpectationResult): string | undefined => {
    if (!expectationResult.inject_expectation_result_source_id) {
      return undefined;
    }
    if (expectationResult.inject_expectation_result_source_type === 'security-platform') {
      return expectationResult.inject_expectation_result_source_id;
    }
    if (expectationResult.inject_expectation_result_source_type !== 'collector') {
      return undefined;
    }
    const linkedPlatformId = collectorsMap[expectationResult.inject_expectation_result_source_id]?.collector_security_platform?.asset_id;
    if (linkedPlatformId) {
      return linkedPlatformId;
    }
    const sourceName = expectationResult.inject_expectation_result_source_name?.trim().toLowerCase();
    if (!sourceName) {
      return undefined;
    }
    return securityPlatforms.find(platform => platform.asset_name?.trim().toLowerCase() === sourceName)?.asset_id;
  };

  // Prefer the security platform logo (a Document, survives collector deletion,
  // never 404s while the platform exists); only pure collector sources without a
  // platform (e.g. the expectation managers) use the collector image.
  const resolveLogoSrc = (expectationResult: InjectExpectationResult): string | undefined => {
    const platformId = resolveSecurityPlatformId(expectationResult);
    if (platformId) {
      return buildTenantApiPath(`/api/images/security_platforms/id/${platformId}/${theme.palette.mode}`);
    }
    if (expectationResult.inject_expectation_result_source_type === 'collector' && expectationResult.inject_expectation_result_source_id) {
      return buildTenantApiPath(`/api/collectors/id/${expectationResult.inject_expectation_result_source_id}/image`);
    }
    return undefined;
  };

  // Last resort: swap to the generic detector icon when the source image cannot
  // be served (deleted collector), never render the broken-image glyph.
  const onLogoError = (event: SyntheticEvent<HTMLImageElement>) => {
    const image = event.currentTarget;
    if (!image.dataset.fallbackApplied) {
      image.dataset.fallbackApplied = 'true';
      image.src = buildTenantApiPath(GENERIC_DETECTOR_LOGO_PATH);
    }
  };

  return {
    resolveSecurityPlatformId,
    resolveLogoSrc,
    onLogoError,
  };
};

export default useExpectationSourceLogo;
