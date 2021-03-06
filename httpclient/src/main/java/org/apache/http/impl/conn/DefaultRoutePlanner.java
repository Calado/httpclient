/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.impl.conn;

import java.net.InetAddress;

import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.annotation.Immutable;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.SchemePortResolver;
import org.apache.http.conn.params.ConnRouteParams;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.protocol.HttpContext;

/**
 * Default implementation of an {@link HttpRoutePlanner}. This implementation
 * is based on {@link org.apache.http.conn.params.ConnRoutePNames parameters}.
 * It will not make use of any Java system properties, nor of system or
 * browser proxy settings.
 *
 * @since 4.3
 */
@Immutable
@SuppressWarnings("deprecation")
public class DefaultRoutePlanner implements HttpRoutePlanner {

    private final SchemePortResolver schemePortResolver;

    public DefaultRoutePlanner(final SchemePortResolver schemePortResolver) {
        super();
        this.schemePortResolver = schemePortResolver != null ? schemePortResolver :
            DefaultSchemePortResolver.INSTANCE;
    }

    public HttpRoute determineRoute(
            final HttpHost host,
            final HttpRequest request,
            final HttpContext context) throws HttpException {
        if (host == null) {
            throw new IllegalArgumentException("Target host may not be null");
        }
        if (request == null) {
            throw new IllegalArgumentException("Request may not be null");
        }

        // If we have a forced route, we can do without a target.
        HttpRoute route = ConnRouteParams.getForcedRoute(request.getParams());
        if (route != null) {
            return route;
        }

        HttpClientContext clientContext = HttpClientContext.adapt(context);
        RequestConfig config = clientContext.getRequestConfig();
        InetAddress local = config.getLocalAddress();
        HttpHost proxy = determineProxy(host, request, context);
        if (proxy == null) {
            proxy = config.getDefaultProxy();
        }

        HttpHost target;
        if (host.getPort() <= 0) {
            target = new HttpHost(
                    host.getHostName(),
                    this.schemePortResolver.resolve(host),
                    host.getSchemeName());
        } else {
            target = host;
        }
        boolean secure = target.getSchemeName().equalsIgnoreCase("https");
        if (proxy == null) {
            return new HttpRoute(target, local, secure);
        } else {
            return new HttpRoute(target, local, proxy, secure);
        }
    }

    protected HttpHost determineProxy(
            final HttpHost target,
            final HttpRequest request,
            final HttpContext context) throws HttpException {
        return null;
    }

}
