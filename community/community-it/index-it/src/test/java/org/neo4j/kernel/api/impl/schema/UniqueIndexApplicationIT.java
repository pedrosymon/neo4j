/*
 * Copyright (c) 2002-2019 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.api.impl.schema;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.test.rule.DbmsRule;
import org.neo4j.test.rule.ImpermanentDbmsRule;

import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertThat;
import static org.neo4j.graphdb.Label.label;
import static org.neo4j.internal.helpers.collection.Iterators.loop;
import static org.neo4j.kernel.api.impl.schema.DatabaseFunctions.awaitIndexesOnline;
import static org.neo4j.kernel.api.impl.schema.DatabaseFunctions.index;
import static org.neo4j.kernel.api.impl.schema.DatabaseFunctions.uniquenessConstraint;

@RunWith( Parameterized.class )
public class UniqueIndexApplicationIT
{
    @Rule
    public final DbmsRule db = new ImpermanentDbmsRule();

    private final Function<GraphDatabaseService, ?> createIndex;

    @Parameterized.Parameters( name = "{0}" )
    public static List<Object[]> indexTypes()
    {
        return asList( createIndex( index( label( "Label1" ), "key1" ) ),
                createIndex( uniquenessConstraint( label( "Label1" ), "key1" ) ) );
    }

    @After
    public void then()
    {
        try ( var transaction = db.beginTx() )
        {
            assertThat( "Matching nodes from index lookup",
                    listNodeIdsFromIndexLookup( transaction, label( "Label1" ), "key1", "value1" ).apply( db ),
                    hasSize( 1 ) );
        }
    }

    @Before
    public void given()
    {
        db.executeAndCommit( tx -> createIndex.apply( db ) );
        db.executeAndCommit( transaction ->
        {
            awaitIndexesOnline( 5, SECONDS ).apply( db );
            return null;
        } );
    }

    @Test
    public void tx_createNode_addLabel_setProperty()
    {
        try ( var transaction = db.beginTx() )
        {
            var node = transaction.createNode();
            node.addLabel( label( "Label1" ) );
            node.setProperty( "key1", "value1" );
            transaction.commit();
        }
    }

    @Test
    public void tx_createNode_tx_addLabel_setProperty()
    {
        try ( var transaction = db.beginTx() )
        {
            var node = transaction.createNode();
            node.addLabel( label( "Label1" ) );
            node.setProperty( "key1", "value1" );
            transaction.commit();
        }
    }

    @Test
    public void tx_createNode_addLabel_tx_setProperty()
    {
        Node node;
        try ( var transaction = db.beginTx() )
        {
            node = transaction.createNode();
            node.addLabel( label( "Label1" ) );
            transaction.commit();
        }
        try ( var transaction = db.beginTx() )
        {
            node.setProperty( "key1", "value1" );
            transaction.commit();
        }
    }

    @Test
    public void tx_createNode_setProperty_tx_addLabel()
    {
        Node node;
        try ( var transaction = db.beginTx() )
        {
            node = transaction.createNode();
            node.addLabel( label( "Label1" ) );
            node.setProperty( "key1", "value1" );
            transaction.commit();
        }
        try ( var transaction = db.beginTx() )
        {
            node.addLabel( label( "Label1" ) );
            transaction.commit();
        }
    }

    @Test
    public void tx_createNode_tx_addLabel_tx_setProperty()
    {
        Node node;
        try ( var transaction = db.beginTx() )
        {
            node = transaction.createNode();
            transaction.commit();
        }
        try ( var transaction = db.beginTx() )
        {
            node.addLabel( label( "Label1" ) );
            transaction.commit();
        }
        try ( var transaction = db.beginTx() )
        {
            node.setProperty( "key1", "value1" );
            transaction.commit();
        }
    }

    @Test
    public void tx_createNode_tx_setProperty_tx_addLabel()
    {
        try ( var transaction = db.beginTx() )
        {
            var node = transaction.createNode();
            node.setProperty( "key1", "value1" );
            node.addLabel( label( "Label1" ) );
            transaction.commit();
        }
    }

    private static Matcher<List<?>> hasSize( final int size )
    {
        return new TypeSafeMatcher<>()
        {
            @Override
            protected boolean matchesSafely( List<?> item )
            {
                return item.size() == size;
            }

            @Override
            public void describeTo( Description description )
            {
                description.appendText( "List with size=" ).appendValue( size );
            }
        };
    }

    private Function<GraphDatabaseService, List<Long>> listNodeIdsFromIndexLookup( Transaction tx,
            final Label label, final String propertyKey, final Object value )
    {
        return graphDb ->
        {
            ArrayList<Long> ids = new ArrayList<>();
            for ( Node node : loop( tx.findNodes( label, propertyKey, value ) ) )
            {
                ids.add( node.getId() );
            }
            return ids;
        };
    }

    public UniqueIndexApplicationIT( Function<GraphDatabaseService, ?> createIndex )
    {
        this.createIndex = createIndex;
    }

    private static Object[] createIndex( Function<GraphDatabaseService, Void> createIndex )
    {
        return new Object[]{createIndex};
    }
}