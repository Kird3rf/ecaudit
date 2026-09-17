/*
 * Copyright 2025 Telefonaktiebolaget LM Ericsson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ericsson.bss.cassandra.ecaudit.entry.factory;

import java.util.Collections;

import org.apache.cassandra.audit.AuditLogContext;
import org.apache.cassandra.auth.DataResource;
import org.apache.cassandra.auth.FunctionResource;
import org.apache.cassandra.auth.RoleResource;
import org.apache.cassandra.cql3.CQLStatement;
import org.apache.cassandra.cql3.QueryProcessor;
import org.apache.cassandra.cql3.statements.AuthenticationStatement;
import org.apache.cassandra.cql3.statements.CreateRoleStatement;
import org.apache.cassandra.cql3.statements.GrantRoleStatement;
import org.apache.cassandra.cql3.statements.ListPermissionsStatement;
import org.apache.cassandra.cql3.statements.PermissionsManagementStatement;
import org.apache.cassandra.cql3.statements.UseStatement;
import org.apache.cassandra.cql3.statements.schema.AlterViewStatement;
import org.apache.cassandra.cql3.statements.schema.CreateAggregateStatement;
import org.apache.cassandra.cql3.statements.schema.CreateFunctionStatement;
import org.apache.cassandra.cql3.statements.schema.CreateIndexStatement;
import org.apache.cassandra.cql3.statements.schema.CreateViewStatement;
import org.apache.cassandra.cql3.statements.schema.DropAggregateStatement;
import org.apache.cassandra.cql3.statements.schema.DropFunctionStatement;
import org.apache.cassandra.cql3.statements.schema.DropIndexStatement;
import org.apache.cassandra.cql3.statements.schema.DropViewStatement;
import org.apache.cassandra.exceptions.RequestExecutionException;
import org.apache.cassandra.exceptions.RequestValidationException;
import org.apache.cassandra.service.ClientState;
import org.apache.cassandra.transport.messages.ResultMessage;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.ericsson.bss.cassandra.ecaudit.test.mode.ClientInitializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestStatementResourceAdapter
{
    private final StatementResourceAdapter adapter = new StatementResourceAdapter();

    @BeforeClass
    public static void beforeClass()
    {
        ClientInitializer.beforeClass();
    }

    @AfterClass
    public static void afterClass()
    {
        ClientInitializer.afterClass();
    }

    @Test
    public void testResolveRoleResourceFromCreateRole()
    {
        CreateRoleStatement statement = parse("CREATE ROLE foo");

        assertThat(adapter.resolveRoleResource(statement)).isEqualTo(RoleResource.role("foo"));
    }

    @Test
    public void testResolveRoleResourceFromGrantRole()
    {
        GrantRoleStatement statement = parse("GRANT foo TO bar");

        assertThat(adapter.resolveRoleResource(statement)).isEqualTo(RoleResource.role("foo"));
    }

    @Test
    public void testResolveManagedResourceFromGrantPermissions()
    {
        PermissionsManagementStatement statement =
                parse("GRANT SELECT ON KEYSPACE ks1 TO foo");

        assertThat(adapter.resolveManagedResource(statement)).isEqualTo(DataResource.keyspace("ks1"));
    }

    @Test
    public void testResolveGranteeResourceFromGrantPermissions()
    {
        PermissionsManagementStatement statement =
                parse("GRANT SELECT ON KEYSPACE ks1 TO foo");

        assertThat(adapter.resolveGranteeResource(statement)).isEqualTo(RoleResource.role("foo"));
    }

    @Test
    public void testResolveGranteeResourceDefaultsToRootWhenNoGrantee()
    {
        ListPermissionsStatement statement = parse("LIST ALL PERMISSIONS");

        assertThat(adapter.resolveGranteeResource(statement)).isEqualTo(RoleResource.root());
    }

    @Test
    public void testResolveKeyspaceResourceFromUse()
    {
        UseStatement statement = parse("USE ks1");

        assertThat(adapter.resolveKeyspaceResource(statement)).isEqualTo(DataResource.keyspace("ks1"));
    }

    @Test
    public void testResolveBaseTableResourceFromCreateViewRaw()
    {
        CreateViewStatement.Raw statement = parse(createView());

        assertThat(adapter.resolveBaseTableResource(statement)).isEqualTo(DataResource.table("ks1", "t"));
    }

    @Test
    public void testResolveBaseTableResourceFromCreateView()
    {
        CreateViewStatement statement = prepare(createView());

        assertThat(adapter.resolveBaseTableResource(statement)).isEqualTo(DataResource.table("ks1", "t"));
    }

    @Test
    public void testResolveBaseTableResourceFromAlterViewFallsBackToKeyspace()
    {
        AlterViewStatement statement = prepare("ALTER MATERIALIZED VIEW ks1.mv WITH comment='x'");

        assertThat(adapter.resolveBaseTableResource(statement)).isEqualTo(DataResource.keyspace("ks1"));
    }

    @Test
    public void testResolveBaseTableResourceFromAlterViewRawFallsBackToKeyspace()
    {
        AlterViewStatement.Raw statement = parse("ALTER MATERIALIZED VIEW ks1.mv WITH comment='x'");

        assertThat(adapter.resolveBaseTableResource(statement)).isEqualTo(DataResource.keyspace("ks1"));
    }

    @Test
    public void testResolveBaseTableResourceFromDropViewFallsBackToKeyspace()
    {
        DropViewStatement statement = prepare("DROP MATERIALIZED VIEW ks1.mv");

        assertThat(adapter.resolveBaseTableResource(statement)).isEqualTo(DataResource.keyspace("ks1"));
    }

    @Test
    public void testResolveBaseTableResourceFromDropViewRawFallsBackToKeyspace()
    {
        DropViewStatement.Raw statement = parse("DROP MATERIALIZED VIEW ks1.mv");

        assertThat(adapter.resolveBaseTableResource(statement)).isEqualTo(DataResource.keyspace("ks1"));
    }

    @Test
    public void testResolveBaseTableResourceFromCreateIndexRaw()
    {
        CreateIndexStatement.Raw statement = parse("CREATE INDEX idx ON ks1.t (a)");

        assertThat(adapter.resolveBaseTableResource(statement)).isEqualTo(DataResource.table("ks1", "t"));
    }

    @Test
    public void testResolveBaseTableResourceFromCreateIndex()
    {
        CreateIndexStatement statement = prepare("CREATE INDEX idx ON ks1.t (a)");

        assertThat(adapter.resolveBaseTableResource(statement)).isEqualTo(DataResource.table("ks1", "t"));
    }

    @Test
    public void testResolveBaseTableResourceFromDropIndexRawFallsBackToKeyspace()
    {
        DropIndexStatement.Raw statement = parse("DROP INDEX ks1.idx");

        assertThat(adapter.resolveBaseTableResource(statement)).isEqualTo(DataResource.keyspace("ks1"));
    }

    @Test
    public void testResolveBaseTableResourceFromDropIndexFallsBackToKeyspace()
    {
        DropIndexStatement statement = prepare("DROP INDEX ks1.idx");

        assertThat(adapter.resolveBaseTableResource(statement)).isEqualTo(DataResource.keyspace("ks1"));
    }

    @Test
    public void testResolveFunctionKeyspaceResourceFromCreateFunctionRaw()
    {
        CreateFunctionStatement.Raw statement = parse(createFunction());

        assertThat(adapter.resolveFunctionKeyspaceResource(statement))
                .isEqualTo(FunctionResource.keyspace("ks1"));
    }

    @Test
    public void testResolveFunctionKeyspaceResourceFromCreateFunction()
    {
        CreateFunctionStatement statement = prepare(createFunction());

        assertThat(adapter.resolveFunctionKeyspaceResource(statement))
                .isEqualTo(FunctionResource.keyspace("ks1"));
    }

    @Test
    public void testResolveFunctionResourceFromDropFunctionRaw()
    {
        DropFunctionStatement.Raw statement = parse("DROP FUNCTION ks1.f");

        assertThat(adapter.resolveFunctionResource(statement))
                .isEqualTo(FunctionResource.functionFromCql("ks1", "f", Collections.emptyList()));
    }

    @Test
    public void testResolveFunctionResourceFromDropFunction()
    {
        DropFunctionStatement statement = prepare("DROP FUNCTION ks1.f");

        assertThat(adapter.resolveFunctionResource(statement))
                .isEqualTo(FunctionResource.functionFromCql("ks1", "f", Collections.emptyList()));
    }

    @Test
    public void testResolveAggregateKeyspaceResourceFromCreateAggregate()
    {
        CreateAggregateStatement statement =
                prepare("CREATE AGGREGATE ks1.agg (int) SFUNC sf STYPE int");

        assertThat(adapter.resolveAggregateKeyspaceResource(statement))
                .isEqualTo(FunctionResource.keyspace("ks1"));
    }

    @Test
    public void testResolveAggregateKeyspaceResourceFromCreateAggregateRaw()
    {
        CreateAggregateStatement.Raw statement =
                parse("CREATE AGGREGATE ks1.agg (int) SFUNC sf STYPE int");

        assertThat(adapter.resolveAggregateKeyspaceResource(statement))
                .isEqualTo(FunctionResource.keyspace("ks1"));
    }

    @Test
    public void testResolveAggregateResourceFromDropAggregateRaw()
    {
        DropAggregateStatement.Raw statement = parse("DROP AGGREGATE ks1.agg");

        assertThat(adapter.resolveAggregateResource(statement))
                .isEqualTo(FunctionResource.functionFromCql("ks1", "agg", Collections.emptyList()));
    }

    @Test
    public void testResolveAggregateResourceFromDropAggregate()
    {
        DropAggregateStatement statement = prepare("DROP AGGREGATE ks1.agg");

        assertThat(adapter.resolveAggregateResource(statement))
                .isEqualTo(FunctionResource.functionFromCql("ks1", "agg", Collections.emptyList()));
    }

    @Test
    public void testResolveRoleResourceFailsWhenRoleFieldIsAbsent()
    {
        assertThatThrownBy(() -> adapter.resolveRoleResource(new NoRoleStatement()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("role");
    }

    private static String createView()
    {
        return "CREATE MATERIALIZED VIEW ks1.mv AS SELECT a FROM ks1.t "
                + "WHERE a IS NOT NULL PRIMARY KEY (a)";
    }

    private static String createFunction()
    {
        return "CREATE FUNCTION ks1.f (a int) RETURNS NULL ON NULL INPUT "
                + "RETURNS int LANGUAGE java AS 'return a;'";
    }

    @SuppressWarnings("unchecked")
    private static <T> T parse(String cql)
    {
        return (T) QueryProcessor.parseStatement(cql);
    }

    @SuppressWarnings("unchecked")
    private static <T extends CQLStatement> T prepare(String cql)
    {
        return (T) QueryProcessor.parseStatement(cql).prepare(ClientState.forInternalCalls());
    }

    private static class NoRoleStatement extends AuthenticationStatement
    {
        @Override
        public void authorize(ClientState state)
        {
        }

        @Override
        public void validate(ClientState state)
        {
        }

        @Override
        public ResultMessage execute(ClientState state)
                throws RequestExecutionException, RequestValidationException
        {
            return null;
        }

        @Override
        public AuditLogContext getAuditLogContext()
        {
            return null;
        }
    }
}
