package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.CustomDashboard;
import io.openaev.database.model.CustomDashboardParameters;
import io.openaev.database.model.Widget;
import io.openaev.database.repository.CustomDashboardRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CustomDashboardComposer extends ComposerBase<CustomDashboard> {

  @Autowired private CustomDashboardRepository customDashboardRepository;

  public class Composer extends InnerComposerBase<CustomDashboard> {

    private final CustomDashboard customDashboard;
    private final List<CustomDashboardParameterComposer.Composer>
        customDashboardParameterComposers = new ArrayList<>();
    private final List<WidgetComposer.Composer> widgetComposers = new ArrayList<>();

    public Composer(CustomDashboard customDashboard) {
      this.customDashboard = customDashboard;
    }

    public Composer withCustomDashboardParameter(
        CustomDashboardParameterComposer.Composer composer) {
      customDashboardParameterComposers.add(composer);
      List<CustomDashboardParameters> tempParams = this.customDashboard.getParameters();
      composer.get().setCustomDashboard(this.customDashboard);
      tempParams.add(composer.get());
      this.customDashboard.setParameters(tempParams);
      return this;
    }

    public Composer withWidget(WidgetComposer.Composer composer) {
      widgetComposers.add(composer);
      List<Widget> tempParams = this.customDashboard.getWidgets();
      composer.get().setCustomDashboard(this.customDashboard);
      tempParams.add(composer.get());
      this.customDashboard.setWidgets(tempParams);
      return this;
    }

    @Override
    public CustomDashboardComposer.Composer persist() {
      customDashboardRepository.save(this.customDashboard);
      widgetComposers.forEach(WidgetComposer.Composer::persist);
      customDashboardParameterComposers.forEach(CustomDashboardParameterComposer.Composer::persist);
      return this;
    }

    @Override
    public CustomDashboardComposer.Composer delete() {
      customDashboardParameterComposers.forEach(CustomDashboardParameterComposer.Composer::delete);
      widgetComposers.forEach(WidgetComposer.Composer::delete);
      customDashboardRepository.delete(this.customDashboard);
      return this;
    }

    @Override
    public CustomDashboard get() {
      return this.customDashboard;
    }
  }

  public CustomDashboardComposer.Composer forCustomDashboard(CustomDashboard customDashboard) {
    generatedItems.add(customDashboard);
    return new CustomDashboardComposer.Composer(customDashboard);
  }
}
