package io.openaev.config;

import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerGroup;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.InitializeCollectionEvent;
import org.hibernate.event.spi.InitializeCollectionEventListener;

/**
 * Names the lazy collection a statement is initialising, so {@link LazyAfterCommitProbeListener}
 * can report {@code Challenge.tags} rather than a join over two tables.
 *
 * <p>The SQL of a collection load does not carry its role, and nothing on the stack does either, so
 * the role is taken from Hibernate's own collection-initialisation event and held on the thread for
 * as long as that initialisation runs. The original listeners are wrapped rather than prepended to,
 * so the role is cleared even when the load throws.
 */
final class LazyCollectionInitTracker {

  /** The collection being initialised on this thread, innermost last. */
  record Initialization(String role, String ownerId) {}

  private static final ThreadLocal<Deque<Initialization>> CURRENT =
      ThreadLocal.withInitial(ArrayDeque::new);

  private LazyCollectionInitTracker() {}

  /** The innermost collection initialisation running on this thread, or {@code null}. */
  static Initialization current() {
    return CURRENT.get().peekLast();
  }

  /** Wraps the session factory's collection-initialisation listeners, once. */
  static void install(EntityManagerFactory entityManagerFactory) {
    SessionFactoryImplementor sessionFactory =
        entityManagerFactory.unwrap(SessionFactoryImplementor.class);
    EventListenerGroup<InitializeCollectionEventListener> group =
        sessionFactory
            .getServiceRegistry()
            .requireService(EventListenerRegistry.class)
            .getEventListenerGroup(EventType.INIT_COLLECTION);
    List<InitializeCollectionEventListener> originals = new ArrayList<>();
    group.listeners().forEach(originals::add);
    if (originals.stream().anyMatch(Tracking.class::isInstance)) {
      return;
    }
    group.clearListeners();
    group.appendListener(new Tracking(originals));
  }

  private record Tracking(List<InitializeCollectionEventListener> delegates)
      implements InitializeCollectionEventListener {

    @Override
    public void onInitializeCollection(InitializeCollectionEvent event) {
      Deque<Initialization> stack = CURRENT.get();
      stack.addLast(describe(event));
      try {
        for (InitializeCollectionEventListener delegate : delegates) {
          delegate.onInitializeCollection(event);
        }
      } finally {
        stack.pollLast();
      }
    }

    private static Initialization describe(InitializeCollectionEvent event) {
      String role = event.getCollection().getRole();
      String shortRole =
          role == null
              ? "unknown"
              : role.substring(role.lastIndexOf('.', role.lastIndexOf('.') - 1) + 1);
      Object ownerId = event.getAffectedOwnerIdOrNull();
      return new Initialization(shortRole, ownerId == null ? null : ownerId.toString());
    }
  }
}
