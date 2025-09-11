package io.openbas.rest.custom_dashboard;

import static io.openbas.rest.custom_dashboard.CustomDashboardApi.CUSTOM_DASHBOARDS_URI;

import io.openbas.aop.RBAC;
import io.openbas.database.model.Action;
import io.openbas.database.model.ResourceType;
import io.openbas.database.model.Widget;
import io.openbas.database.model.WidgetLayout;
import io.openbas.rest.custom_dashboard.form.WidgetInput;
import io.openbas.rest.helper.RestBehavior;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(CustomDashboardWidgetApi.CUSTOM_DASHBOARDS_WIDGET_URI)
@RequiredArgsConstructor
public class CustomDashboardWidgetApi extends RestBehavior {

  public static final String CUSTOM_DASHBOARDS_WIDGET_URI = CUSTOM_DASHBOARDS_URI + "/{id}/widgets";
  private final WidgetService widgetService;

  // -- CRUD --

  @PostMapping
  @RBAC(resourceId = "#id", actionPerformed = Action.WRITE, resourceType = ResourceType.DASHBOARD)
  public ResponseEntity<Widget> createWidget(
      @PathVariable @NotBlank final String id,
      @RequestBody @Valid @NotNull final WidgetInput input) {
    return ResponseEntity.ok(this.widgetService.createWidget(id, input.toWidget(new Widget())));
  }

  @GetMapping
  @RBAC(resourceId = "#id", actionPerformed = Action.READ, resourceType = ResourceType.DASHBOARD)
  public ResponseEntity<List<Widget>> widgets(@PathVariable @NotBlank final String id) {
    return ResponseEntity.ok(this.widgetService.widgets(id));
  }

  @GetMapping("/{widgetId}")
  @RBAC(resourceId = "#id", actionPerformed = Action.READ, resourceType = ResourceType.DASHBOARD)
  public ResponseEntity<Widget> widget(
      @PathVariable @NotBlank final String id, @PathVariable @NotBlank final String widgetId) {
    return ResponseEntity.ok(this.widgetService.widget(id, widgetId));
  }

  @PutMapping("/{widgetId}")
  @RBAC(resourceId = "#id", actionPerformed = Action.WRITE, resourceType = ResourceType.DASHBOARD)
  public ResponseEntity<Widget> updateWidget(
      @PathVariable @NotBlank final String id,
      @PathVariable @NotBlank final String widgetId,
      @RequestBody @Valid @NotNull final WidgetInput input) {
    Widget existingWidget = this.widgetService.widget(id, widgetId);
    Widget updatedWidget = input.toWidget(existingWidget);
    return ResponseEntity.ok(this.widgetService.updateWidget(updatedWidget));
  }

  @PutMapping("/{widgetId}/layout")
  @RBAC(resourceId = "#id", actionPerformed = Action.WRITE, resourceType = ResourceType.DASHBOARD)
  public ResponseEntity<Widget> updateWidgetLayout(
      @PathVariable @NotBlank final String id,
      @PathVariable @NotBlank final String widgetId,
      @RequestBody @Valid @NotNull final WidgetLayout layout) {
    Widget existingWidget = this.widgetService.widget(id, widgetId);
    existingWidget.setLayout(layout);
    return ResponseEntity.ok(this.widgetService.updateWidget(existingWidget));
  }

  @DeleteMapping("/{widgetId}")
  @RBAC(resourceId = "#id", actionPerformed = Action.WRITE, resourceType = ResourceType.DASHBOARD)
  public ResponseEntity<Void> deleteWidget(
      @PathVariable @NotBlank final String id, @PathVariable @NotBlank final String widgetId) {
    this.widgetService.deleteWidget(id, widgetId);
    return ResponseEntity.noContent().build();
  }
}
