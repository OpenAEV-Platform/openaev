package io.openaev.architecture.fixture;

import jakarta.persistence.Table;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * Repositories and callers for the linked-id resolution guard: every shape of call it must find,
 * and the calls it must leave alone. The domain types carry {@code @Table} without {@code @Entity}
 * and the repositories are {@code @NoRepositoryBean}, so no Spring context picks them up.
 */
public final class LinkedIdResolutionFixtures {

  private LinkedIdResolutionFixtures() {}

  public static final String ACTIVE_TABLE = "fixture_linked_items";
  public static final String INACTIVE_TABLE = "fixture_inactive_items";

  // -- DOMAIN TYPES --

  @Table(name = ACTIVE_TABLE)
  public static class LinkedItem {}

  @Table(name = INACTIVE_TABLE)
  public static class InactiveItem {}

  public static class UnmappedItem {}

  // -- REPOSITORIES --

  @NoRepositoryBean
  public interface LinkedItemRepository extends JpaRepository<LinkedItem, String> {

    List<LinkedItem> findAllByIdIn(Collection<String> ids);

    List<LinkedItem> findAllByIdInAndTenantId(Collection<String> ids, String tenantId);

    default List<LinkedItem> findAllByIdInTenant(Collection<String> ids, String tenantId) {
      return findAllByIdInAndTenantId(ids, tenantId);
    }

    List<LinkedItem> findAllByIdInWithin(Collection<String> ids, String tenantId);
  }

  /** A second repository of the same table, so one inherited target has two receivers. */
  @NoRepositoryBean
  public interface SiblingLinkedItemRepository extends JpaRepository<LinkedItem, String> {}

  /** Declares the same method name twice: once with a tenant, once without. */
  @NoRepositoryBean
  public interface OverloadedItemRepository extends JpaRepository<LinkedItem, String> {

    List<LinkedItem> findAllByIdInWithin(Collection<String> ids);

    List<LinkedItem> findAllByIdInWithin(Collection<String> ids, String tenantId);
  }

  /** Inherits {@code findAllById} from a parent interface of the code base. */
  @NoRepositoryBean
  public interface LinkedItemChildRepository extends LinkedItemRepository {}

  /** Extends the bare marker interface, as a repository that offers only chosen methods does. */
  @NoRepositoryBean
  public interface BareLinkedItemRepository
      extends org.springframework.data.repository.Repository<LinkedItem, String> {

    Iterable<LinkedItem> findAllById(Iterable<String> ids);
  }

  @NoRepositoryBean
  public interface InactiveItemRepository extends JpaRepository<InactiveItem, String> {}

  /** A repository whose domain type has no table: the guard cannot tell which table it reads. */
  @NoRepositoryBean
  public interface UnmappedItemRepository extends JpaRepository<UnmappedItem, String> {}

  /** A generic base: no repository of one table, so nothing to resolve. */
  @NoRepositoryBean
  public interface GenericBaseRepository<T> extends JpaRepository<T, String> {}

  // -- CALLERS THAT RESOLVE IDS WITHOUT A TENANT --

  public static class CallsFindAllById {
    private LinkedItemRepository repository;

    public Object link(List<String> ids) {
      return repository.findAllById(ids);
    }
  }

  public static class CallsFindAllByIdTwice {
    private LinkedItemRepository repository;

    public void link(List<String> ids, List<String> others) {
      repository.findAllById(ids);
      repository.findAllById(others);
    }
  }

  public static class CallsTwoRepositoriesThroughMethodReferences {
    private LinkedItemRepository first;
    private SiblingLinkedItemRepository second;

    public Object link(List<String> ids) {
      resolve(ids, first::findAllById);
      return resolve(ids, second::findAllById);
    }

    private Object resolve(List<String> ids, Function<List<String>, Object> resolver) {
      return resolver.apply(ids);
    }
  }

  public static class CallsTheUnscopedOverload {
    private OverloadedItemRepository repository;

    public Object link(List<String> ids) {
      return repository.findAllByIdInWithin(ids);
    }
  }

  public static class CallsInALambda {
    private LinkedItemRepository repository;

    public Runnable link(List<String> ids) {
      return () -> repository.findAllById(ids);
    }
  }

  public static class CallsThroughAMethodReference {
    private LinkedItemRepository repository;

    public Object link(List<String> ids) {
      return resolve(ids, repository::findAllById);
    }

    private Object resolve(List<String> ids, Function<List<String>, Object> resolver) {
      return resolver.apply(ids);
    }
  }

  public static class CallsThroughAnUnboundMethodReference {
    public Object link(LinkedItemRepository repository, List<String> ids) {
      Function<List<String>, Object> resolver = repository::findAllById;
      return resolver.apply(ids);
    }
  }

  public static class CallsAnInheritedMethodOnTheChild {
    private LinkedItemChildRepository repository;

    public Object link(List<String> ids) {
      return repository.findAllById(ids);
    }
  }

  public static class CallsADeclaredFindAllByIdIn {
    private LinkedItemRepository repository;

    public Object link(List<String> ids) {
      return repository.findAllByIdIn(ids);
    }
  }

  public static class CallsGetReferenceById {
    private LinkedItemRepository repository;

    public Object link(String id) {
      return repository.getReferenceById(id);
    }
  }

  public static class CallsOnABareRepository {
    private BareLinkedItemRepository repository;

    public Object link(List<String> ids) {
      return repository.findAllById(ids);
    }
  }

  // -- CALLERS THE GUARD LEAVES ALONE --

  public static class CallsWithATenant {
    private LinkedItemRepository repository;

    public void link(List<String> ids, String tenantId) {
      repository.findAllByIdInTenant(ids, tenantId);
      repository.findAllByIdInAndTenantId(ids, tenantId);
      repository.findAllByIdInWithin(ids, tenantId);
    }
  }

  public static class CallsTheScopedOverload {
    private OverloadedItemRepository repository;

    public Object link(List<String> ids, String tenantId) {
      return repository.findAllByIdInWithin(ids, tenantId);
    }
  }

  public static class CallsOnAnInactiveTable {
    private InactiveItemRepository repository;

    public Object link(List<String> ids) {
      return repository.findAllById(ids);
    }
  }

  public static class CallsFindById {
    private LinkedItemRepository repository;

    public Object read(String id) {
      return repository.findById(id);
    }
  }
}
