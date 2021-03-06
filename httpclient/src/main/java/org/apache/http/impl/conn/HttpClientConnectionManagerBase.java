/*
 * ====================================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.impl.conn;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.http.HttpClientConnection;
import org.apache.http.HttpHost;
import org.apache.http.annotation.ThreadSafe;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.config.Lookup;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.apache.http.conn.ConnectionRequest;
import org.apache.http.conn.DnsResolver;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.SchemePortResolver;
import org.apache.http.conn.SocketClientConnection;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.pool.ConnPool;
import org.apache.http.protocol.HttpContext;

/**
 * Base class for {@link HttpClientConnectionManager} implementations.
 * This class primarily provides consistent implementation of
 * {@link #connect(HttpClientConnection, HttpHost, InetAddress, HttpContext, HttpParams)}
 * and {@link #upgrade(HttpClientConnection, HttpHost, HttpContext, HttpParams)}
 * methods for all standard connection managers shipped with HttpClient.
 *
 * @since 4.3
 */
@ThreadSafe
abstract class HttpClientConnectionManagerBase implements HttpClientConnectionManager {

    private final ConnPool<HttpRoute, CPoolEntry> pool;
    private final HttpClientConnectionOperator connectionOperator;
    private final Map<HttpHost, SocketConfig> socketConfigMap;
    private final Map<HttpHost, ConnectionConfig> connectionConfigMap;
    private volatile SocketConfig defaultSocketConfig;
    private volatile ConnectionConfig defaultConnectionConfig;

    HttpClientConnectionManagerBase(
            final ConnPool<HttpRoute, CPoolEntry> pool,
            final Lookup<ConnectionSocketFactory> socketFactoryRegistry,
            final SchemePortResolver schemePortResolver,
            final DnsResolver dnsResolver) {
        super();
        if (pool == null) {
            throw new IllegalArgumentException("Connection pool may nor be null");
        }
        this.pool = pool;
        this.connectionOperator = new HttpClientConnectionOperator(
                socketFactoryRegistry, schemePortResolver, dnsResolver);
        this.socketConfigMap = new ConcurrentHashMap<HttpHost, SocketConfig>();
        this.connectionConfigMap = new ConcurrentHashMap<HttpHost, ConnectionConfig>();
        this.defaultSocketConfig = SocketConfig.DEFAULT;
        this.defaultConnectionConfig = ConnectionConfig.DEFAULT;
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            shutdown();
        } finally {
            super.finalize();
        }
    }

    protected void onConnectionLeaseRequest(final HttpRoute route, final Object state) {
    }

    protected void onConnectionLease(final CPoolEntry entry) {
    }

    protected void onConnectionKeepAlive(final CPoolEntry entry) {
    }

    protected void onConnectionRelease(final CPoolEntry entry) {
    }

    public ConnectionRequest requestConnection(
            final HttpRoute route,
            final Object state) {
        if (route == null) {
            throw new IllegalArgumentException("HTTP route may not be null");
        }
        onConnectionLeaseRequest(route, state);
        final Future<CPoolEntry> future = this.pool.lease(route, state, null);
        return new ConnectionRequest() {

            public boolean cancel() {
                return future.cancel(true);
            }

            public HttpClientConnection get(
                    final long timeout,
                    final TimeUnit tunit) throws InterruptedException, ConnectionPoolTimeoutException {
                return leaseConnection(future, timeout, tunit);
            }

        };

    }

    protected HttpClientConnection leaseConnection(
            final Future<CPoolEntry> future,
            final long timeout,
            final TimeUnit tunit) throws InterruptedException, ConnectionPoolTimeoutException {
        CPoolEntry entry;
        try {
            entry = future.get(timeout, tunit);
            if (entry == null || future.isCancelled()) {
                throw new InterruptedException();
            }
            if (entry.getConnection() == null) {
                throw new IllegalStateException("Pool entry with no connection");
            }
            onConnectionLease(entry);
            return CPoolProxy.newProxy(entry);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause == null) {
                cause = ex;
            }
            InterruptedException intex = new InterruptedException();
            intex.initCause(cause);
            throw intex;
        } catch (TimeoutException ex) {
            throw new ConnectionPoolTimeoutException("Timeout waiting for connection from pool");
        }
    }

    public void releaseConnection(
            final HttpClientConnection managedConn,
            final Object state,
            final long keepalive, final TimeUnit tunit) {
        if (managedConn == null) {
            throw new IllegalArgumentException("Managed connection may not be null");
        }
        synchronized (managedConn) {
            CPoolEntry entry = CPoolProxy.detach(managedConn);
            if (entry == null) {
                return;
            }
            SocketClientConnection conn = entry.getConnection();
            try {
                if (conn.isOpen()) {
                    entry.setState(state);
                    entry.updateExpiry(keepalive, tunit != null ? tunit : TimeUnit.MILLISECONDS);
                    onConnectionKeepAlive(entry);
                }
            } finally {
                this.pool.release(entry, conn.isOpen());
                onConnectionRelease(entry);
            }
        }
    }

    public void connect(
            final HttpClientConnection managedConn,
            final HttpHost host,
            final InetAddress local,
            final int connectTimeout,
            final HttpContext context) throws IOException {
        if (managedConn == null) {
            throw new IllegalArgumentException("Connection may not be null");
        }
        SocketClientConnection conn;
        synchronized (managedConn) {
            CPoolEntry entry = CPoolProxy.getPoolEntry(managedConn);
            conn = entry.getConnection();
        }
        SocketConfig socketConfig = this.socketConfigMap.get(host);
        if (socketConfig == null) {
            socketConfig = this.defaultSocketConfig;
        }
        ConnectionConfig connConfig = this.connectionConfigMap.get(host);
        if (connConfig == null) {
            connConfig = this.defaultConnectionConfig;
        }

        InetSocketAddress localAddress = local != null ? new InetSocketAddress(local, 0) : null;
        this.connectionOperator.connect(conn, host, localAddress,
                connectTimeout, socketConfig, context);
    }

    public void upgrade(
            final HttpClientConnection managedConn,
            final HttpHost host,
            final HttpContext context) throws IOException {
        if (managedConn == null) {
            throw new IllegalArgumentException("Connection may not be null");
        }
        SocketClientConnection conn;
        synchronized (managedConn) {
            CPoolEntry entry = CPoolProxy.getPoolEntry(managedConn);
            conn = entry.getConnection();
        }
        this.connectionOperator.upgrade(conn, host, context);
    }

    public SocketConfig getDefaultSocketConfig() {
        return this.defaultSocketConfig;
    }

    public void setDefaultSocketConfig(final SocketConfig defaultSocketConfig) {
        this.defaultSocketConfig = defaultSocketConfig != null ? defaultSocketConfig :
            SocketConfig.DEFAULT;
    }

    public ConnectionConfig getDefaultConnectionConfig() {
        return this.defaultConnectionConfig;
    }

    public void setDefaultConnectionConfig(final ConnectionConfig defaultConnectionConfig) {
        this.defaultConnectionConfig = defaultConnectionConfig != null ? defaultConnectionConfig :
            ConnectionConfig.DEFAULT;
    }

    public SocketConfig getSocketConfig(final HttpHost host) {
        return this.socketConfigMap.get(host);
    }

    public void setSocketConfig(final HttpHost host, final SocketConfig socketConfig) {
        this.socketConfigMap.put(host, socketConfig);
    }

    public ConnectionConfig getConnectionConfig(final HttpHost host) {
        return this.connectionConfigMap.get(host);
    }

    public void setConnectionConfig(final HttpHost host, final ConnectionConfig connectionConfig) {
        this.connectionConfigMap.put(host, connectionConfig);
    }

}
