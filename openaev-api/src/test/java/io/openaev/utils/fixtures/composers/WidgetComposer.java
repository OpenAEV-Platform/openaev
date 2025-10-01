package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.Widget;
import io.openaev.database.repository.WidgetRepository;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WidgetComposer extends ComposerBase<Widget> {

  @Autowired private WidgetRepository widgetRepository;

  public class Composer extends InnerComposerBase<Widget> {

    private final Widget widget;
    private Optional<CustomDashboardComposer.Composer> customDashboardComposer = Optional.empty();

    public Composer(Widget widget) {
      this.widget = widget;
    }

    public Composer withCustomDashboard(CustomDashboardComposer.Composer customDashboardComposer) {
      this.customDashboardComposer = Optional.of(customDashboardComposer);
      this.widget.setCustomDashboard(customDashboardComposer.get());
      return this;
    }

    @Override
    public WidgetComposer.Composer persist() {
      customDashboardComposer.ifPresent(CustomDashboardComposer.Composer::persist);
      widgetRepository.save(this.widget);
      return this;
    }

    @Override
    public WidgetComposer.Composer delete() {
      customDashboardComposer.ifPresent(CustomDashboardComposer.Composer::delete);
      widgetRepository.delete(this.widget);
      return this;
    }

    @Override
    public Widget get() {
      return this.widget;
    }
  }

  public WidgetComposer.Composer forWidget(Widget widget) {
    generatedItems.add(widget);
    return new WidgetComposer.Composer(widget);
  }
}
