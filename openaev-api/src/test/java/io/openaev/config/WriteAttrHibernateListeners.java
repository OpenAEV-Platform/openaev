package io.openaev.config;

import jakarta.persistence.EntityManagerFactory;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.WeakHashMap;
import org.hibernate.engine.spi.CollectionEntry;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.AbstractCollectionEvent;
import org.hibernate.event.spi.AutoFlushEvent;
import org.hibernate.event.spi.AutoFlushEventListener;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.FlushEvent;
import org.hibernate.event.spi.FlushEventListener;
import org.hibernate.event.spi.MergeContext;
import org.hibernate.event.spi.MergeEvent;
import org.hibernate.event.spi.MergeEventListener;
import org.hibernate.event.spi.PersistContext;
import org.hibernate.event.spi.PersistEvent;
import org.hibernate.event.spi.PersistEventListener;
import org.hibernate.event.spi.PostLoadEvent;
import org.hibernate.event.spi.PostLoadEventListener;
import org.hibernate.event.spi.PreCollectionRecreateEvent;
import org.hibernate.event.spi.PreCollectionRecreateEventListener;
import org.hibernate.event.spi.PreCollectionUpdateEvent;
import org.hibernate.event.spi.PreCollectionUpdateEventListener;
import org.hibernate.event.spi.PreInsertEvent;
import org.hibernate.event.spi.PreInsertEventListener;
import org.hibernate.event.spi.PreUpdateEvent;
import org.hibernate.event.spi.PreUpdateEventListener;
import org.hibernate.persister.collection.AbstractCollectionPersister;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.type.CompositeType;

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
 *       WriteAttrEntryFrames#askedToWrite}, keyed by entity identity. A merge is captured for both
 *       the instance handed in and the managed copy Hibernate returns: with an assigned id the copy
 *       is a different object, and it is the copy that reaches {@code pre-insert}. A load is
 *       captured the same way ({@link PostLoadEventListener}), with the frame of the statement that
 *       hydrated it, so an entity a request loads and then mutates through dirty checking, or whose
 *       collection it changes, is attributed to that request although it never reaches {@code
 *       persist}/{@code merge}. An entity already in the persistence context raises no load event,
 *       so a row the test loaded first and a request mutated later keeps the test's null frame: a
 *       known residue, waived as test-driven.
 *   <li>At {@code pre-insert}/{@code pre-update} the row's table and key are known. The {@link
 *       PreInsertEventListener}/{@link PreUpdateEventListener} bind the captured frame to that
 *       {@code table + key}, the same key {@link WriteAttrDetectorTrigger} emits in its warning, so
 *       {@link WriteAttrDetectorListener} can look it up when it reads the warning. For an entity
 *       whose identifier is composite ({@code (id, tenant_id)} on the connector tables) the key is
 *       the identifier's non-tenant part, which is also what the trigger emits.
 *   <li>At {@code pre-collection-recreate}/{@code pre-collection-update} the join table's rows are
 *       about to be written with no entity event of their own. The listener binds the join table to
 *       its owner's captured frame for the duration of the flush ({@link
 *       WriteAttrEntryFrames#bindOwner}); the binding is dropped when the flush completes.
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
      OwnerBind bind = new OwnerBind();
      FlushBindingsClear clear = new FlushBindingsClear();
      registry.appendListeners(EventType.PERSIST, ask);
      registry.appendListeners(EventType.MERGE, ask);
      registry.appendListeners(EventType.POST_LOAD, ask);
      registry.appendListeners(EventType.PRE_INSERT, promote);
      registry.appendListeners(EventType.PRE_UPDATE, promote);
      registry.appendListeners(EventType.PRE_COLLECTION_RECREATE, bind);
      registry.appendListeners(EventType.PRE_COLLECTION_UPDATE, bind);
      // Appended after the default flush listeners, so they run once the flush has executed its
      // statements: the join-table bindings live exactly one flush.
      registry.appendListeners(EventType.FLUSH, clear);
      registry.appendListeners(EventType.AUTO_FLUSH, clear);
    } catch (RuntimeException e) {
      // Never turn a wiring failure into a test failure; the detector falls back to the stack.
    }
  }

  /**
   * The row key the trigger emits for this entity: the identifier itself for a single-column key,
   * the identifier's non-tenant part for a composite {@code (id, tenant_id)} key, null when the key
   * cannot be reduced to one value (the trigger then reports {@code id=?}).
   */
  static String rowKeyOf(EntityPersister persister, Object id) {
    if (id == null) {
      return null;
    }
    if (persister.getIdentifierType() instanceof CompositeType composite) {
      String[] names = composite.getPropertyNames();
      Object[] values = composite.getPropertyValues(id);
      Object key = null;
      int nonTenant = 0;
      for (int i = 0; i < names.length; i++) {
        if (!"tenant".equals(names[i]) && !"tenantId".equals(names[i])) {
          key = values[i];
          nonTenant++;
        }
      }
      return nonTenant == 1 && key != null ? key.toString() : null;
    }
    return id.toString();
  }

  /**
   * Captures the production entry frame when the application asks to write an entity, and when it
   * loads one: a managed entity mutated through dirty checking, or whose collection changes, is
   * never handed to {@code persist}/{@code merge}, so the frame that loaded it is the only one that
   * says which request or job holds it. The last capture wins, so a merge inside a request takes
   * over from an earlier load.
   */
  private static final class AskCapture
      implements PersistEventListener, MergeEventListener, PostLoadEventListener {

    @Override
    public void onPostLoad(PostLoadEvent event) {
      if (!WriteAttrDetectorRecorder.isRecording()) {
        return;
      }
      try {
        // No stack walk here: the frame is the one taken once for the statement that hydrated
        // this entity (see WriteAttrDetectorListener.beforeQuery).
        WriteAttrEntryFrames.loaded(event.getEntity());
      } catch (RuntimeException e) {
        // observation only
      }
    }

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
      capture(event.getResult());
    }

    @Override
    public void onMerge(MergeEvent event, MergeContext copiedAlready) {
      capture(event.getOriginal());
      capture(event.getResult());
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

  /** Binds a captured frame to the row's {@code table + key} once the key is assigned, at flush. */
  private static final class RowPromote implements PreInsertEventListener, PreUpdateEventListener {

    @Override
    public boolean onPreInsert(PreInsertEvent event) {
      promote(event.getEntity(), event.getPersister(), event.getId());
      return false;
    }

    @Override
    public boolean onPreUpdate(PreUpdateEvent event) {
      promote(event.getEntity(), event.getPersister(), event.getId());
      return false;
    }

    private static void promote(Object entity, EntityPersister persister, Object id) {
      if (!WriteAttrDetectorRecorder.isRecording()) {
        return;
      }
      try {
        WriteAttrEntryFrames.promoteToRow(
            tableOf(persister.getMappedTableDetails().getTableName()),
            rowKeyOf(persister, id),
            entity);
      } catch (RuntimeException e) {
        // observation only
      }
    }
  }

  /** Binds a join table to its owner's captured frame while the flush writes its rows. */
  private static final class OwnerBind
      implements PreCollectionRecreateEventListener, PreCollectionUpdateEventListener {

    @Override
    public void onPreRecreateCollection(PreCollectionRecreateEvent event) {
      bind(event);
    }

    @Override
    public void onPreUpdateCollection(PreCollectionUpdateEvent event) {
      bind(event);
    }

    private static void bind(AbstractCollectionEvent event) {
      if (!WriteAttrDetectorRecorder.isRecording()) {
        return;
      }
      try {
        Object owner = event.getAffectedOwnerOrNull();
        CollectionEntry entry =
            event.getSession().getPersistenceContext().getCollectionEntry(event.getCollection());
        if (owner == null || entry == null) {
          return;
        }
        CollectionPersister persister =
            entry.getCurrentPersister() != null
                ? entry.getCurrentPersister()
                : entry.getLoadedPersister();
        if (persister instanceof AbstractCollectionPersister table) {
          WriteAttrEntryFrames.bindOwner(tableOf(table.getTableName()), owner);
        }
      } catch (RuntimeException e) {
        // observation only
      }
    }
  }

  /** Drops the join-table bindings once the flush that used them has run. */
  private static final class FlushBindingsClear
      implements FlushEventListener, AutoFlushEventListener {

    @Override
    public void onFlush(FlushEvent event) {
      WriteAttrEntryFrames.clearFlushBindings();
    }

    @Override
    public void onAutoFlush(AutoFlushEvent event) {
      WriteAttrEntryFrames.clearFlushBindings();
    }
  }

  private static String tableOf(String raw) {
    String unquoted = raw.replace("\"", "");
    int dot = unquoted.lastIndexOf('.');
    return (dot < 0 ? unquoted : unquoted.substring(dot + 1)).toLowerCase(Locale.ROOT);
  }
}
