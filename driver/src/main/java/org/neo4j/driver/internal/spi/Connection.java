/**
 * Copyright (c) 2002-2017 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
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
package org.neo4j.driver.internal.spi;

import java.util.Map;

import org.neo4j.driver.v1.Value;

/**
 * A connection is an abstraction provided by an underlying transport implementation,
 * it is the medium that a session is conducted over.
 */
public interface Connection extends AutoCloseable
{
    /**
     * Initialize the connection. This must be done before any other action is allowed.
     * @param clientName should be the driver name and version: "java-driver/1.0.0"
     * @param authToken a map value
     */
    void init( String clientName, Map<String,Value> authToken );

    /**
     * Queue up a run action. The collector will value called with metadata about the stream that will become available
     * for retrieval.
     * @param parameters a map value of parameters
     */
    void run( String statement, Map<String,Value> parameters, StreamCollector collector );

    /**
     * Queue a discard all action, consuming any items left in the current stream.This will
     * close the stream once its completed, allowing another {@link #run(String, java.util.Map, StreamCollector) run}
     */
    void discardAll();

    /**
     * Queue a pull-all action, output will be handed to the collector once the pull starts. This will
     * close the stream once its completed, allowing another {@link #run(String, java.util.Map, StreamCollector) run}
     */
    void pullAll( StreamCollector collector );

    /**
     * Queue a reset action, throw {@link org.neo4j.driver.v1.exceptions.ClientException} if an ignored message is received. This will
     * close the stream once its completed, allowing another {@link #run(String, java.util.Map, StreamCollector) run}
     */
    void reset();

    /**
     * Queue a ack_failure action, valid output could only be success. Throw {@link org.neo4j.driver.v1.exceptions.ClientException} if
     * a failure or ignored message is received. This will close the stream once it is completed, allowing another
     * {@link #run(String, java.util.Map, StreamCollector) run}
     */
    void ackFailure();

    /**
     * Ensure all outstanding actions are carried out on the server.
     */
    void sync();

    /**
     * Send all pending messages to the server and return the number of messages sent.
     */
    void flush();

    /**
     * Receive the next message available.
     */
    void receiveOne();

    @Override
    void close();

    /**
     * Test if the underlying socket connection with the server is still open.
     * When the socket connection with the server is closed,
     * the connection cannot take on any task, but be {@link #close() closed} to release resources it occupies.
     * Note: Invocation of {@link #close()} method would make this method to return false,
     * however this method cannot indicate whether {@link #close()} is already be called or not.
     * @return true if the socket connection with the server is open, otherwise false.
     */
    boolean isOpen();

    /**
     * If there are any errors that occur on this connection, invoke the given
     * runnable. This is used in the driver to clean up resources associated with
     * the connection, like an open transaction.
     *
     * @param runnable
     */
    void onError( Runnable runnable );


    boolean hasUnrecoverableErrors();
}
