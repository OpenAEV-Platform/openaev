package io.openaev.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.FlushEventListener;
import org.hibernate.service.spi.ServiceRegistryImplementor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@DisplayName("Write-attribution: Hibernate listener registration retries after a wiring failure")
class WriteAttrHibernateListenersTest {

  @Nested
  @DisplayName("Given a session factory whose registry lookup throws on first registration")
  class RegistrationFailure {

    @Test
    @DisplayName(
        "Given a later context refresh with a working registry, should register the listeners")
    void given_laterRefreshWithWorkingRegistry_should_registerTheListeners() {
      // Arrange: the first call cannot obtain the event registry (a wiring failure), the second
      // call, on the same session factory instance, can.
      EntityManagerFactory emf = mock(EntityManagerFactory.class);
      SessionFactoryImplementor sessionFactory = mock(SessionFactoryImplementor.class);
      ServiceRegistryImplementor workingRegistry = mock(ServiceRegistryImplementor.class);
      EventListenerRegistry eventListenerRegistry = mock(EventListenerRegistry.class);
      when(emf.unwrap(SessionFactoryImplementor.class)).thenReturn(sessionFactory);
      when(sessionFactory.getServiceRegistry())
          .thenThrow(new IllegalStateException("registry not ready"))
          .thenReturn(workingRegistry);
      when(workingRegistry.requireService(EventListenerRegistry.class))
          .thenReturn(eventListenerRegistry);

      // Act
      WriteAttrHibernateListeners.register(emf);
      WriteAttrHibernateListeners.register(emf);

      // Assert: the second call actually re-attempted registration (a bug marks the factory
      // wired on the first, failed call, and every later refresh is then silently skipped).
      Mockito.verify(eventListenerRegistry)
          .appendListeners(Mockito.eq(EventType.FLUSH), Mockito.any(FlushEventListener.class));
    }
  }
}
