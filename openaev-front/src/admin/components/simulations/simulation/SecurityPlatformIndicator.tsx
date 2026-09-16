import { Box, Tooltip } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { SecurityNetwork } from 'mdi-material-ui';
import { type FunctionComponent, useEffect } from 'react';

import { type SecurityPlatformHelper } from '../../../../actions/assets/asset-helper';
import { fetchSecurityPlatforms } from '../../../../actions/assets/securityPlatform-actions';
import { fetchWorkflowConfiguration } from '../../../../actions/chaining/workflow-actions';
import type { WorkflowConfigurationHelper } from '../../../../actions/chaining/workflow-helper';
import { useFormatter } from '../../../../components/i18n';
import ItemSecurityPlatformType from '../../../../components/ItemSecurityPlatformType';
import { useHelper } from '../../../../store';
import { type SecurityPlatform, type SecurityPlatformSnapshotOutput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { buildTenantApiPath } from '../../../../utils/url-helper';
import isConnectorManaged from '../../assets/security_platforms/securityPlatformUtils';

// Unified display model over the two sources: the frozen launch/end snapshot of a launched chained
// simulation (workflow_security_platforms, see ADR-006) and the live connector-managed platforms of
// the tenant (draft simulations and pre-snapshot runs).
interface DisplayPlatform {
  id: string;
  name: string;
  type?: string;
  /** Translated change-status suffix (e.g. "Deleted after execution"); undefined when RESOLVED. */
  statusSuffix?: string;
  /** The platform may no longer exist, so its brand image cannot be loaded. */
  deleted: boolean;
}

interface SecurityPlatformIndicatorProps {
  /**
   * The simulation's chaining workflow: when provided together with {@link launched}, the frozen
   * security-platform snapshot of the launched run is shown instead of the live tenant platforms,
   * so the header reflects the platforms connected at execution time (immune to later installs /
   * uninstalls).
   */
  workflowId?: string;
  /**
   * The simulation has been launched: its snapshot is authoritative EVEN WHEN EMPTY (zero platforms
   * connected at launch must not fall back to platforms installed later). Draft simulations and
   * non-chained contexts resolve the live tenant platforms.
   */
  launched?: boolean;
}

const SecurityPlatformIndicator: FunctionComponent<SecurityPlatformIndicatorProps> = ({ workflowId, launched = false }) => {
  const theme = useTheme();
  const dispatch = useAppDispatch();
  const { t } = useFormatter();

  const { securityPlatforms, workflowConfiguration } = useHelper(
    (helper: SecurityPlatformHelper & WorkflowConfigurationHelper) => ({
      securityPlatforms: helper.getSecurityPlatforms(),
      workflowConfiguration: workflowId ? helper.getWorkflowConfiguration(workflowId) : undefined,
    }),
  );
  useDataLoader(() => {
    dispatch(fetchSecurityPlatforms());
  });
  useEffect(() => {
    if (workflowId) {
      dispatch(fetchWorkflowConfiguration(workflowId));
    }
  }, [dispatch, workflowId]);

  // Frozen photo of a launched simulation (empty for draft / scenario / pre-snapshot runs).
  const frozenPlatforms: DisplayPlatform[] = (workflowConfiguration?.workflow_security_platforms ?? [])
    .map((platform: SecurityPlatformSnapshotOutput) => ({
      id: platform.security_platform_snapshot_id ?? '',
      name: platform.security_platform_snapshot_name ?? platform.security_platform_snapshot_id ?? '',
      type: platform.security_platform_snapshot_type,
      statusSuffix: platform.security_platform_snapshot_status && platform.security_platform_snapshot_status !== 'RESOLVED'
        ? t(platform.security_platform_snapshot_status)
        : undefined,
      deleted: platform.security_platform_snapshot_status === 'DELETED_DURING_EXECUTION'
        || platform.security_platform_snapshot_status === 'DELETED_AFTER_EXECUTION',
    }))
    .filter((platform: DisplayPlatform) => !!platform.id);

  const livePlatforms: DisplayPlatform[] = securityPlatforms
    .filter(isConnectorManaged)
    .map((platform: SecurityPlatform) => ({
      id: platform.asset_id,
      name: platform.asset_name,
      type: platform.security_platform_type,
      deleted: false,
    }));

  // A launched chained run shows its frozen photo exclusively - an empty frozen list means zero
  // platforms were connected at launch, never "fall back to the live set". Pre-snapshot launched
  // runs (no chaining workflow) and drafts resolve live.
  const useFrozen = launched && !!workflowId;
  const displayPlatforms = useFrozen ? frozenPlatforms : livePlatforms;
  if (displayPlatforms.length === 0) {
    return null;
  }

  return (
    <Tooltip
      slotProps={{ tooltip: { sx: { maxWidth: 'none' } } }}
      title={(
        <Box sx={{
          display: 'flex',
          flexDirection: 'column',
          gap: 1.5,
          padding: 0.5,
        }}
        >

          <Box sx={{
            display: 'grid',
            gridTemplateColumns: displayPlatforms.length > 1 ? 'repeat(2, minmax(0, 1fr))' : '1fr',
            gap: 1.5,
          }}
          >
            {displayPlatforms.map(platform => (
              <Box
                key={platform.id}
                sx={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 1,
                  minWidth: 180,
                }}
              >
                <Box sx={{
                  width: 36,
                  height: 36,
                  flexShrink: 0,
                  borderRadius: 1,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  border: `1px solid ${alpha(theme.palette.common.white, 0.12)}`,
                  backgroundColor: alpha(theme.palette.common.white, 0.04),
                }}
                >
                  {platform.deleted ? (
                    <SecurityNetwork sx={{
                      fontSize: 24,
                      color: theme.palette.text.secondary,
                    }}
                    />
                  ) : (
                    <img
                      src={buildTenantApiPath(`/api/images/security_platforms/id/${platform.id}/${theme.palette.mode}`)}
                      alt={platform.name}
                      style={{
                        width: 24,
                        height: 24,
                        borderRadius: 4,
                      }}
                    />
                  )}
                </Box>
                <Box sx={{ minWidth: 0 }}>
                  <div style={{
                    fontSize: 13,
                    fontWeight: 600,
                    lineHeight: 1.3,
                  }}
                  >
                    {platform.name}
                  </div>
                  <Box sx={{
                    marginTop: 0.5,
                    display: 'flex',
                    alignItems: 'center',
                    gap: 0.75,
                  }}
                  >
                    <ItemSecurityPlatformType type={platform.type} />
                    {platform.statusSuffix && (
                      <Box
                        component="span"
                        sx={{
                          fontSize: 11,
                          color: theme.palette.warning.main,
                        }}
                      >
                        {platform.statusSuffix}
                      </Box>
                    )}
                  </Box>
                </Box>
              </Box>
            ))}
          </Box>
        </Box>
      )}
    >
      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        color: theme.palette.text.secondary,
      }}
      >
        <SecurityNetwork fontSize="small" />
      </Box>
    </Tooltip>
  );
};

export default SecurityPlatformIndicator;
