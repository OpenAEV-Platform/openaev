package io.openaev.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DelegatingDataSource;

class TenantAwareDataSourceConfigTest {

  private TenantAwareDataSourceConfig config;
  private DataSource mockDataSource;
  private Connection mockConnection;
  private Statement mockStatement;
  private PreparedStatement mockPreparedStatement;

  @BeforeEach
  void setUp() throws SQLException {
    config = new TenantAwareDataSourceConfig();
    mockDataSource = mock(DataSource.class);
    mockConnection = mock(Connection.class);
    mockStatement = mock(Statement.class);
    mockPreparedStatement = mock(PreparedStatement.class);

    when(mockDataSource.getConnection()).thenReturn(mockConnection);
    when(mockConnection.createStatement()).thenReturn(mockStatement);
    when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);

    TenantContext.clearCurrentTenant();
    TenantContext.clearRlsBypass();
  }

  @AfterEach
  void tearDown() {
    TenantContext.clearCurrentTenant();
    TenantContext.clearRlsBypass();
  }

  private DataSource wrapDataSource() {
    return (DataSource) config.postProcessAfterInitialization(mockDataSource, "dataSource");
  }

  @Nested
  @DisplayName("Normal RLS flow")
  class NormalFlow {

    @Test
    @DisplayName("should SET ROLE and set tenant variable on getConnection")
    void given_normalRequest_should_setRoleAndTenantVariable() throws SQLException {
      // Arrange
      TenantContext.setCurrentTenant("tenant-123");
      DataSource wrapped = wrapDataSource();

      // Act
      Connection conn = wrapped.getConnection();

      // Assert
      assertThat(conn).isNotNull();
      verify(mockStatement).execute("SET ROLE openaev_app");
      verify(mockPreparedStatement).setString(1, "tenant-123");
      verify(mockPreparedStatement).execute();
    }

    @Test
    @DisplayName("should use default tenant when no tenant is set")
    void given_noTenantSet_should_useDefaultTenant() throws SQLException {
      // Arrange — no explicit tenant set, defaults to DEFAULT_TENANT_UUID
      DataSource wrapped = wrapDataSource();

      // Act
      wrapped.getConnection();

      // Assert
      verify(mockPreparedStatement).setString(1, Tenant.DEFAULT_TENANT_UUID);
    }
  }

  @Nested
  @DisplayName("RLS bypass")
  class RlsBypass {

    @Test
    @DisplayName("should RESET ROLE when RLS is bypassed")
    void given_rlsBypassed_should_resetRole() throws SQLException {
      // Arrange
      TenantContext.setRlsBypass();
      DataSource wrapped = wrapDataSource();

      // Act
      wrapped.getConnection();

      // Assert
      verify(mockStatement).execute("RESET ROLE");
      verify(mockConnection, never()).prepareStatement(anyString());
    }
  }

  @Nested
  @DisplayName("Bootstrap (role does not exist)")
  class Bootstrap {

    @Test
    @DisplayName("should skip gracefully when role does not exist")
    void given_roleDoesNotExist_should_skipWithoutError() throws SQLException {
      // Arrange
      when(mockStatement.execute("SET ROLE openaev_app"))
          .thenThrow(new SQLException("role \"openaev_app\" does not exist"));
      DataSource wrapped = wrapDataSource();

      // Act
      Connection conn = wrapped.getConnection();

      // Assert — no exception, no set_config call
      assertThat(conn).isNotNull();
      verify(mockConnection, never()).prepareStatement(anyString());
    }
  }

  @Nested
  @DisplayName("Unexpected SET ROLE failure")
  class UnexpectedFailure {

    @Test
    @DisplayName("should throw when SET ROLE fails for unexpected reason")
    void given_unexpectedSetRoleFailure_should_throw() throws SQLException {
      // Arrange
      when(mockStatement.execute("SET ROLE openaev_app"))
          .thenThrow(new SQLException("permission denied"));
      DataSource wrapped = wrapDataSource();

      // Act & Assert
      assertThatThrownBy(wrapped::getConnection)
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("Refusing to proceed without RLS");
    }
  }

  @Nested
  @DisplayName("Non-DataSource beans")
  class NonDataSource {

    @Test
    @DisplayName("should not wrap non-DataSource beans")
    void given_nonDataSourceBean_should_returnAsIs() {
      // Arrange
      String someBean = "not a datasource";

      // Act
      Object result = config.postProcessAfterInitialization(someBean, "someBean");

      // Assert
      assertThat(result).isSameAs(someBean);
    }

    @Test
    @DisplayName("should not wrap DataSource with wrong bean name")
    void given_wrongBeanName_should_returnAsIs() {
      // Act
      Object result = config.postProcessAfterInitialization(mockDataSource, "otherDataSource");

      // Assert
      assertThat(result).isSameAs(mockDataSource);
      assertThat(result).isNotInstanceOf(DelegatingDataSource.class);
    }
  }
}
