package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.Reporting;
import io.openaev.database.repository.ReportingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ReportingComposer extends ComposerBase<Reporting> {

  @Autowired private ReportingRepository reportingRepository;

  public class Composer extends InnerComposerBase<Reporting> {

    private final Reporting reporting;

    public Composer(Reporting reporting) {
      this.reporting = reporting;
    }

    @Override
    public ReportingComposer.Composer persist() {
      reportingRepository.save(this.reporting);
      return this;
    }

    @Override
    public ReportingComposer.Composer delete() {
      reportingRepository.delete(this.reporting);
      return this;
    }

    @Override
    public Reporting get() {
      return this.reporting;
    }
  }

  public ReportingComposer.Composer forReporting(Reporting reporting) {
    generatedItems.add(reporting);
    return new ReportingComposer.Composer(reporting);
  }
}
