/*
 * Copyright (c) 2002-2017 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
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
package org.neo4j.values;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.PropertyContainer;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.spatial.Geometry;
import org.neo4j.graphdb.spatial.Point;
import org.neo4j.helpers.collection.Iterables;
import org.neo4j.values.storable.NumberValue;
import org.neo4j.values.storable.TextValue;
import org.neo4j.values.storable.Values;
import org.neo4j.values.virtual.CoordinateReferenceSystem;
import org.neo4j.values.virtual.EdgeValue;
import org.neo4j.values.virtual.ListValue;
import org.neo4j.values.virtual.MapValue;
import org.neo4j.values.virtual.NodeValue;
import org.neo4j.values.virtual.PathValue;
import org.neo4j.values.virtual.PointValue;
import org.neo4j.values.virtual.VirtualValueGroup;
import org.neo4j.values.virtual.VirtualValues;

import static java.util.stream.StreamSupport.stream;
import static org.neo4j.values.virtual.VirtualValues.list;
import static org.neo4j.values.virtual.VirtualValues.map;

@SuppressWarnings( "WeakerAccess" )
public final class AnyValues
{
    /**
     * Default AnyValue comparator. Will correctly compare all storable and virtual values.
     */
    public static final Comparator<AnyValue> COMPARATOR =
            new AnyValueComparator( Values.COMPARATOR, VirtualValueGroup::compareTo );

    /**
     * Creates an AnyValue by doing type inspection. Do not use in production code where performance is important.
     *
     * @param object the object to turned into a AnyValue
     * @return the AnyValue corresponding to object.
     */
    @SuppressWarnings( "unchecked" )
    public static AnyValue of( Object object )
    {
        try
        {
            return Values.of( object );
        }
        catch ( IllegalArgumentException e )
        {
            if ( object instanceof Node )
            {
                return asNodeValue( (Node) object );
            }
            else if ( object instanceof Relationship )
            {
                return asEdgeValue( (Relationship) object );
            }
            else if ( object instanceof Path )
            {
                return asPathValue( (Path) object );
            }
            else if ( object instanceof Map<?,?> )
            {
                return asMapValue( (Map<String,Object>) object );
            }
            else if ( object instanceof Collection<?> )
            {
                return asListValue( (Collection<?>) object );
            }
            else if ( object instanceof Point )
            {
                return asPointValue( (Point) object );
            }
            else if ( object instanceof Geometry )
            {
                return asPointValue( (Geometry) object );
            }
            else if ( object instanceof Object[] )
            {
                Object[] array = (Object[]) object;
                AnyValue[] anyValues = new AnyValue[array.length];
                for ( int i = 0; i < array.length; i++ )
                {
                    anyValues[i] = of( array[i] );
                }
                return VirtualValues.list( anyValues );
            }
            else
            {
                throw new IllegalArgumentException(
                        String.format( "Cannot convert %s to AnyValue", object.getClass().getName() ) );
            }
        }
    }

    public static PointValue asPointValue( Point point )
    {
        return toPoint( point );
    }

    public static PointValue asPointValue( Geometry geometry )
    {
        if ( !geometry.getGeometryType().equals( "Point" ) )
        {
            throw new IllegalArgumentException( "Cannot handle geometry type: " + geometry.getCRS().getType() );
        }
        return toPoint( geometry );
    }

    private static PointValue toPoint( Geometry geometry )
    {
        List<Double> coordinate = geometry.getCoordinates().get( 0 ).getCoordinate();
        if ( geometry.getCRS().getCode() == CoordinateReferenceSystem.Cartesian.code )
        {
            return VirtualValues.pointCartesian( coordinate.get( 0 ), coordinate.get( 1 ) );
        }
        else if ( geometry.getCRS().getCode() == CoordinateReferenceSystem.WGS84.code )
        {
            return VirtualValues.pointGeographic( coordinate.get( 0 ), coordinate.get( 1 ) );
        }
        else
        {
            throw new IllegalArgumentException( "Unknown coordinate reference system " + geometry.getCRS() );
        }
    }

    public static ListValue asListValue( Iterable<?> collection )
    {
        AnyValue[] anyValues =
                Iterables.stream( collection ).map( AnyValues::of ).toArray( AnyValue[]::new );
        return list( anyValues );
    }

    public static NodeValue asNodeValue( Node node )
    {
        return VirtualValues.fromNodeProxy( node );
    }

    public static EdgeValue asEdgeValue( Relationship rel )
    {
        return VirtualValues.fromRelationshipProxy( rel );
    }

    public static AnyValue asNodeOrEdgeValue( PropertyContainer container )
    {
        if ( container instanceof Node )
        {
            return asNodeValue( (Node) container );
        }
        else if ( container instanceof Relationship )
        {
            return asEdgeValue( (Relationship) container );
        }
        else
        {
            throw new IllegalArgumentException(
                    "Cannot produce a node or edge from " + container.getClass().getName() );
        }
    }

    public static PathValue asPathValue( Path path )
    {
        NodeValue[] nodes = stream( path.nodes().spliterator(), false )
                .map( AnyValues::asNodeValue ).toArray( NodeValue[]::new );
        EdgeValue[] edges = stream( path.relationships().spliterator(), false )
                .map( AnyValues::asEdgeValue ).toArray( EdgeValue[]::new );

        return VirtualValues.path( nodes, edges );
    }

    public static ListValue asListOfEdges( Iterable<Relationship> rels )
    {
        return VirtualValues.list( StreamSupport.stream( rels.spliterator(), false )
                .map( AnyValues::asEdgeValue ).toArray( EdgeValue[]::new ) );
    }

    public static ListValue asListOfEdges( Relationship[] rels )
    {
        EdgeValue[] edgeValues = new EdgeValue[rels.length];
        for ( int i = 0; i < edgeValues.length; i++ )
        {
            edgeValues[i] = asEdgeValue( rels[i] );
        }
        return VirtualValues.list( edgeValues );
    }

    public static MapValue asMapValue( Map<String,Object> map )
    {
        return map( mapValues( map ) );
    }

    public static ListValue concat( ListValue... lists )
    {
        int totalSize = 0;
        for ( ListValue list : lists )
        {
            totalSize += list.size();
        }

        AnyValue[] anyValues = new AnyValue[totalSize];
        int startPoint = 0;
        for ( ListValue list : lists )
        {
            System.arraycopy( list.asArray(), 0, anyValues, startPoint, list.size() );
            startPoint += list.size();
        }

        return VirtualValues.list( anyValues );
    }

    public static MapValue combine( MapValue a, MapValue b )
    {
        HashMap<String,AnyValue> map = new HashMap<>( a.size() + b.size() );
        a.foreach( map::put );
        b.foreach( map::put );
        return VirtualValues.map( map );
    }

    public static ListValue appendToList( ListValue list, AnyValue value )
    {
        AnyValue[] newValues = new AnyValue[list.size() + 1];
        System.arraycopy( list.asArray(), 0, newValues, 0, list.size() );
        newValues[list.size()] = value;
        return VirtualValues.list( newValues );
    }

    public static ListValue prependToList( ListValue list, AnyValue value )
    {
        AnyValue[] newValues = new AnyValue[list.size() + 1];
        newValues[0] = value;
        System.arraycopy( list.asArray(), 0, newValues, 1, list.size() );
        return VirtualValues.list( newValues );
    }

    public static PointValue fromMap( MapValue map )
    {
        if ( map.containsKey( "x" ) && map.containsKey( "y" ) )
        {
            double x = ((NumberValue) map.get( "x" )).doubleValue();
            double y = ((NumberValue) map.get( "y" )).doubleValue();
            if ( !map.containsKey( "crs" ) )
            {
                return VirtualValues.pointCartesian( x, y );
            }

            TextValue crs = (TextValue) map.get( "crs" );
            if ( crs.stringValue().equals( CoordinateReferenceSystem.Cartesian.type() ) )
            {
                return VirtualValues.pointCartesian( x, y );
            }
            else if ( crs.stringValue().equals( CoordinateReferenceSystem.WGS84.type() ) )
            {
                return VirtualValues.pointGeographic( x, y );
            }
            else
            {
                throw new IllegalArgumentException( "Unknown coordinate reference system: " + crs.stringValue() );
            }
        }
        else if ( map.containsKey( "latitude" ) && map.containsKey( "longitude" ) )
        {
            double latitude = ((NumberValue) map.get( "latitude" )).doubleValue();
            double longitude = ((NumberValue) map.get( "longitude" )).doubleValue();
            if ( !map.containsKey( "crs" ) )
            {
                return VirtualValues.pointGeographic( longitude, latitude );
            }

            TextValue crs = (TextValue) map.get( "crs" );
            if ( crs.stringValue().equals( CoordinateReferenceSystem.WGS84.type() ) )
            {
                return VirtualValues.pointGeographic( longitude, latitude );
            }
            else
            {
                throw new IllegalArgumentException(
                        "Geographic points does not support coordinate reference system: " + crs.stringValue() );
            }
        }
        else
        {
            throw new IllegalArgumentException(
                    "A point must contain either 'x' and 'y' or 'latitude' and 'longitude'" );
        }
    }

    private static Map<String,AnyValue> mapValues( Map<String,Object> map )
    {
        HashMap<String,AnyValue> newMap = new HashMap<>( map.size() );
        for ( Map.Entry<String,Object> entry : map.entrySet() )
        {
            newMap.put( entry.getKey(), of( entry.getValue() ) );
        }

        return newMap;
    }
}
