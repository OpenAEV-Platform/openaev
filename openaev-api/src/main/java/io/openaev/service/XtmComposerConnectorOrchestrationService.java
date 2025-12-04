package io.openaev.service;

import io.openaev.api.xtm_composer.dto.XtmComposerInstanceOutput;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.Setting;
import io.openaev.rest.connector_instance.dto.CreateConnectorInstanceInput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static io.openaev.database.model.SettingKeys.*;


@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class XtmComposerConnectorOrchestrationService {

    private final ConnectorInstanceService connectorInstanceService;
    private final XtmComposerService xtmComposerService;

    /**
     * Find connector instances managed by xtm Composer
     *
     * @param xtmComposerId XTM Composer id
     * @return List of connector instances
     */
    public List<XtmComposerInstanceOutput> findConnectorInstancesManagedByComposer(String xtmComposerId) {
        this.xtmComposerService.validateXtmComposerId(xtmComposerId);

        List<ConnectorInstance> instances =
                connectorInstanceService.connectorInstancesManagedByXtmComposer();

        return instances.stream().map(xtmComposerService::toXtmComposerInstanceOutput).toList();
    }

    /**
     * Update connector instance status, called by XTM Composer
     *
     * @param xtmComposerId XTM Composer id
     * @param connectorInstanceId Connector Instance id
     * @param newCurrentStatus New current status
     * @return Updated connector instance formatted for XTM Composer
     */
    public XtmComposerInstanceOutput updateConnectorInstanceStatus(
            String xtmComposerId, String connectorInstanceId, ConnectorInstance.CURRENT_STATUS_TYPE newCurrentStatus) {
        this.xtmComposerService.validateXtmComposerId(xtmComposerId);

        ConnectorInstance instances =
                connectorInstanceService.updateCurrentStatus(connectorInstanceId, newCurrentStatus);

        return xtmComposerService.toXtmComposerInstanceOutput(instances);
    }

    /**
     * Create connector instance, only if XTM Composer is reachable
     * @param input CreateConnectorInstanceInput
     * @return Created ConnectorInstance
     */
    public ConnectorInstance createConnectorInstance(CreateConnectorInstanceInput input) {
        Map<String, Setting> xtmComposerInformation = xtmComposerService.getXtmComposerSettings();

        // TODO the front should understand the error
        this.xtmComposerService.validateXtmComposerReachability(
                xtmComposerInformation.get(XTM_COMPOSER_ID.key()).getValue(),
                xtmComposerInformation.get(XTM_COMPOSER_LAST_CONNECTIVITY_CHECK.key()).getValue());

        return connectorInstanceService.createConnectorInstance(input, xtmComposerInformation.get(XTM_COMPOSER_PUBLIC_KEY.key()).getValue());
    }

}
