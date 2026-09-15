package io.openaev.config;

import jakarta.persistence.EntityManagerFactory;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.WeakHashMap;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.MergeContext;
import org.hibernate.event.spi.MergeEvent;
import org.hibernate.event.spi.MergeEventListener;
import org.hibernate.event.spi.PersistContext;
import org.hibernate.event.spi.PersistEvent;
import org.hibernate.event.spi.PersistEventListener;
import org.hibernate.event.spi.PreInsertEvent;
import org.hibernate.event.spi.PreInsertEventListener;
import org.hibernate.event.spi.PreUpdateEvent;
import org.hibernate.event.spi.PreUpdateEventListener;

/**
 * Test-scope Hibernate event listeners that give the write-attribution detector the production
 * entry frame of a JPA write at the moment the application asks for it, not at the later flush
 * where the INSERT actually runs. A {@code @Transactional} test's controller write joins the test
 * transaction and flushes at the next query or at test end, from a test frame; reading the frame
 * off the stack then attributes the write to the test and waives it, the exact class the gate
 * exists to catch.
 *
 * <ul>
 *   <li>At {@code persist}/{@code merge} the outermost production frame is still on the stack. The
 *       {@link PersistEventListener}/{@link MergeEventListener} capture it via {@link
 *       WriteAttrEntryFrames#askedToWrite}, keyed by entity identity.
 *   <li>At {@code pre-insert}/{@code pre-update} the row's table and primary key are known. The
 *       {@link PreInsertEventListener}/{@link PreUpdateEventListener} bind the captured frame to
 *       that {@code table + id}, the same key {@link WriteAttrDetectorTrigger} emits in its
 *       warning, so {@link WriteAttrDetectorListener} can look it up when it reads the warning.
 * </ul>
 *
 * <p>Only active while a test is recording ({@link WriteAttrDetectorRecorder#isRecording()}): a
 * write the trigger can observe has, by definition, flushed during the test body (Hibernate does
 * not flush pending inserts on a rollback), which is inside the recording window. This bounds the
 * per-persist stack capture to test execution and keeps context startup free. Every callback is
 * defensive: it never throws and {@code pre-insert}/{@code pre-update} never veto, so a bug here
 * can never change a test's outcome.
 */
final class WriteAttrHibernateListeners {

  /** SessionFactories already wired, so a repeated context refresh does not double-register. */
  private static final Set<SessionFactoryImplementor> WIRED =
      Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

  private WriteAttrHibernateListeners() {}

  /**
   * Appends the attribution listeners to the given factory's event registry, once per factory. Safe
   * to call on every context refresh. Never throws: a factory that cannot be unwrapped simply goes
   * unwired, degrading the detector to stack-based attribution rather than failing the run.
   */
  static void register(EntityManagerFactory entityManagerFactory) {
    try {
      SessionFactoryImplementor sessionFactory =
          entityManagerFactory.unwrap(SessionFactoryImplementor.class);
      if (!WIRED.add(sessionFactory)) {
        return;
      }
      EventListenerRegistry registry =
          sessionFactory.getServiceRegistry().requireService(EventListenerRegistry.class);
      AskCapture ask = new AskCapture();
      RowPromote promote = new RowPromote();
      registry.appendListeners(EventType.PERSIST, ask);
      registry.appendListeners(EventType.MERGE, ask);
      registry.appendListeners(EventType.PRE_INSERT, promote);
      registry.appendListeners(EventType.PRE_UPDATE, promote);
    } catch (RuntimeException e) {
      // Never turn a wiring failure into a test failure; the detector falls back to the stack.
    }
  }

  /** Captures the production entry frame when the application asks to write an entity. */
  private static final class AskCapture implements PersistEventListener, MergeEventListener {

    @Override
    public void onPersist(PersistEvent event) {
      capture(event.getObject());
    }

    @Override
    public void onPersist(PersistEvent event, PersistContext createdAlready) {
      capture(event.getObject());
    }

    @Override
    public void onMerge(MergeEvent event) {
      capture(event.getOriginal());
    }

    @Override
    public void onMerge(MergeEvent event, MergeContext copiedAlready) {
      capture(event.getOriginal());
    }

    private static void capture(Object entity) {
      if (!WriteAttrDetectorRecorder.isRecording() || entity == null) {
        return;
      }
      try {
        WriteAttrEntryFrames.askedToWrite(entity, WriteAttrStack.entryFrame());
      } catch (RuntimeException e) {
        // observation only
      }
    }
  }

  /** Binds a captured frame to the row's {@code table + id} once the id is assigned, at flush. */
  private static final class RowPromote implements PreInsertEventListener, PreUpdateEventListener {

    @Override
    public boolean onPreInsert(PreInsertEvent event) {
      promote(event.getEntity(), tableOf(event.getPersister()), event.getId());
      return false;
    }

    @Override
    public boolean onPreUpdate(PreUpdateEvent event) {
      promote(event.getEntity(), tableOf(event.getPersister()), event.getId());
      return false;
    }

    private static void promote(Object entity, String table, Object id) {
      if (!WriteAttrDetectorRecorder.isRecording()) {
        return;
      }
      try {
        WriteAttrEntryFrames.promoteToRow(table, id, entity);
      } catch (RuntimeException e) {
        // observation only
      }
    }

    private static String tableOf(org.hibernate.persister.entity.EntityPersister persister) {
      String raw = persister.getMappedTableDetails().getTableName();
      String unquoted = raw.replace("\"", "");
      int dot = unquoted.lastIndexOf('.');
      return (dot < 0 ? unquoted : unquoted.substring(dot + 1)).toLowerCase(Locale.ROOT);
    }
  }
}
