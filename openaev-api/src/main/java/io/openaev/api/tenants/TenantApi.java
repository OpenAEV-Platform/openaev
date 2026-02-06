package io.openaev.api.tenants;

import io.openaev.aop.AccessControl;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.service.tenants.TenantService;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

import static io.openaev.api.tenants.TenantMapper.toOutput;

@RestController
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
public class TenantApi {

    private final TenantService tenantService;

    // -- CREATE --

    @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.TENANT, isEnterpriseEdition = true)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantOutput create(@Valid @RequestBody TenantInput input) {
        return toOutput(tenantService.create(TenantMapper.fromInput(null, input)));
    }

    // -- READ --

    @AccessControl(resourceId = "#tenantId", actionPerformed = Action.READ, resourceType = ResourceType.TENANT, isEnterpriseEdition = true)
    @GetMapping("/{tenantId}")
    public TenantOutput getById(@PathVariable String tenantId) {
        return toOutput(tenantService.findById(tenantId));
    }

    // -- SEARCH --

    @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.TENANT, isEnterpriseEdition = true)
    @PostMapping("/search")
    public Page<TenantOutput> search(@RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
        return tenantService.search(searchPaginationInput)
                .map(TenantMapper::toOutput);
    }

    // -- UPDATE --

    @AccessControl(resourceId = "#tenantId", actionPerformed = Action.WRITE, resourceType = ResourceType.TENANT, isEnterpriseEdition = true)
    @PutMapping("/{tenantId}")
    public TenantOutput update(@PathVariable String tenantId, @Valid @RequestBody TenantInput input) {

        return toOutput(tenantService.update(tenantId, TenantMapper.fromInput(tenantId, input)));
    }

    // -- DELETE --

    @AccessControl(resourceId = "#tenantId", actionPerformed = Action.DELETE, resourceType = ResourceType.TENANT, isEnterpriseEdition = true)
    @DeleteMapping("/{tenantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String tenantId) {
        tenantService.delete(tenantId);
    }
}
