package io.openaev.utils.fixtures;

import io.openaev.database.model.Reporting;
import io.openaev.database.model.ReportingContextType;
import io.openaev.database.model.ReportingFormat;
import io.openaev.database.model.ReportingModule;
import io.openaev.database.model.ReportingModuleType;
import io.openaev.database.model.ReportingSchedulePeriod;
import io.openaev.database.model.ReportingTimeRange;
import io.openaev.rest.reporting.form.ReportingInput;
import io.openaev.rest.reporting.form.ReportingScheduleInput;
import java.util.ArrayList;
import java.util.List;

public class ReportingFixture {

  public static final String REPORTING_NAME = "Reporting Template";

  // -- ENTITIES --

  public static Reporting createDefaultReporting() {
    return createReporting(REPORTING_NAME, ReportingContextType.PLATFORM, null);
  }

  public static Reporting createReporting(
      String name, ReportingContextType contextType, String contextId) {
    Reporting reporting = new Reporting();
    reporting.setName(name);
    reporting.setContextType(contextType);
    reporting.setContextId(contextId);
    reporting.setModules(new ArrayList<>(List.of(createModule(ReportingModuleType.COVER))));
    reporting.setDefaultFormat(ReportingFormat.PDF);
    reporting.setTimeRange(ReportingTimeRange.LAST_30_DAYS);
    return reporting;
  }

  public static ReportingModule createModule(ReportingModuleType moduleType) {
    ReportingModule module = new ReportingModule();
    module.setModuleType(moduleType);
    return module;
  }

  // -- INPUTS --

  public static ReportingInput createDefaultReportingInput() {
    return createReportingInput(REPORTING_NAME, ReportingContextType.PLATFORM, null);
  }

  public static ReportingInput createReportingInput(
      String name, ReportingContextType contextType, String contextId) {
    ReportingInput input = new ReportingInput();
    input.setName(name);
    input.setContextType(contextType);
    input.setContextId(contextId);
    input.setModules(List.of(createModule(ReportingModuleType.COVER)));
    input.setDefaultFormat(ReportingFormat.PDF);
    input.setTimeRange(ReportingTimeRange.LAST_30_DAYS);
    return input;
  }

  public static ReportingScheduleInput createDailyScheduleInput(String name, String triggerTime) {
    ReportingScheduleInput input = new ReportingScheduleInput();
    input.setName(name);
    input.setPeriod(ReportingSchedulePeriod.DAY);
    input.setTriggerTime(triggerTime);
    input.setFormat(ReportingFormat.PDF);
    input.setEnabled(true);
    return input;
  }
}
