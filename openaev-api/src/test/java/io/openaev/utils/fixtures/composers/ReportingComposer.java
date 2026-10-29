package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.Reporting;
import io.openaev.database.repository.ReportingRepository;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ReportingComposer extends ComposerBase<Reporting> {

  @Autowired private ReportingRepository reportingRepository;
  @Autowired private EntityManager entityManager;

  public class Composer extends InnerComposerBase<Reporting> {

    private final Reporting reporting;

    public Composer(Reporting reporting) {
      this.reporting = reporting;
    }

    @Override
    public ReportingComposer.Composer persist() {
      reportingRepository.save(this.reporting);
      // Flush and clear: reportings is tenant-active, and a managed entity left dirty in the
      // persistence context (its JSON-typed modules/branding columns) triggers an auto-flush
      // UPDATE on the very next query. If that flush happens before the request's own tenant
      // scope is set on the transaction, the inspector-rewritten UPDATE matches zero rows and
      // Hibernate throws, well before the request even reaches its own read.
      entityManager.flush();
      entityManager.clear();
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
