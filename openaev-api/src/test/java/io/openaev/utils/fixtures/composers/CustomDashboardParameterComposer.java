package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.CustomDashboardParameters;
import io.openaev.database.repository.CustomDashboardParametersRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CustomDashboardParameterComposer extends ComposerBase<CustomDashboardParameters> {

  @Autowired private CustomDashboardParametersRepository customDashboardParametersRepository;

  public class Composer extends InnerComposerBase<CustomDashboardParameters> {

    private final CustomDashboardParameters customDashboardParameters;

    public Composer(CustomDashboardParameters customDashboardParameters) {
      this.customDashboardParameters = customDashboardParameters;
    }

    @Override
    public CustomDashboardParameterComposer.Composer persist() {
      customDashboardParametersRepository.save(this.customDashboardParameters);
      return this;
    }

    @Override
    public CustomDashboardParameterComposer.Composer delete() {
      customDashboardParametersRepository.delete(this.customDashboardParameters);
      return this;
    }

    @Override
    public CustomDashboardParameters get() {
      return this.customDashboardParameters;
    }
  }

  public CustomDashboardParameterComposer.Composer forCustomDashboardParameter(
      CustomDashboardParameters customDashboardParameters) {
    generatedItems.add(customDashboardParameters);
    return new CustomDashboardParameterComposer.Composer(customDashboardParameters);
  }
}
