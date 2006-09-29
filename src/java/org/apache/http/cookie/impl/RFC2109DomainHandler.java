/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Copyright 2002-2006 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
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
package org.apache.http.cookie.impl;

import org.apache.http.cookie.Cookie;
import org.apache.http.cookie.CookieAttributeHandler;
import org.apache.http.cookie.CookieOrigin;
import org.apache.http.cookie.MalformedCookieException;

public class RFC2109DomainHandler implements CookieAttributeHandler {

    public RFC2109DomainHandler() {
        super();
    }
    
    public void parse(final Cookie cookie, final String value) 
            throws MalformedCookieException {
        if (cookie == null) {
            throw new IllegalArgumentException("Cookie may not be null");
        }
        if (value == null) {
            throw new MalformedCookieException("Missing value for domain attribute");
        }
        if (value.trim().equals("")) {
            throw new MalformedCookieException("Blank value for domain attribute");
        }
        cookie.setDomain(value);
        cookie.setDomainAttributeSpecified(true);
    }

    public void validate(final Cookie cookie, final CookieOrigin origin) 
            throws MalformedCookieException {
        if (cookie == null) {
            throw new IllegalArgumentException("Cookie may not be null");
        }
        if (origin == null) {
            throw new IllegalArgumentException("Cookie origin may not be null");
        }
        String host = origin.getHost();
        String domain = cookie.getDomain();
        if (domain == null) {
            throw new MalformedCookieException("Cookie domain may not be null");
        }
        if (!domain.equals(host)) {
            int dotIndex = domain.indexOf('.');
            if (dotIndex == -1) {
                throw new MalformedCookieException("Domain attribute \"" 
                        + domain 
                        + "\" does not match the host \"" 
                        + host + "\"");
            }
            // domain must start with dot
            if (!domain.startsWith(".")) {
                throw new MalformedCookieException("Domain attribute \"" 
                    + domain 
                    + "\" violates RFC 2109: domain must start with a dot");
            }
            // domain must have at least one embedded dot
            dotIndex = domain.indexOf('.', 1);
            if (dotIndex < 0 || dotIndex == domain.length() - 1) {
                throw new MalformedCookieException("Domain attribute \"" 
                    + domain 
                    + "\" violates RFC 2109: domain must contain an embedded dot");
            }
            host = host.toLowerCase();
            if (!host.endsWith(domain)) {
                throw new MalformedCookieException(
                    "Illegal domain attribute \"" + domain 
                    + "\". Domain of origin: \"" + host + "\"");
            }
            // host minus domain may not contain any dots
            String hostWithoutDomain = host.substring(0, host.length() - domain.length());
            if (hostWithoutDomain.indexOf('.') != -1) {
                throw new MalformedCookieException("Domain attribute \"" 
                    + domain 
                    + "\" violates RFC 2109: host minus domain may not contain any dots");
            }
        }
    }
    
    public boolean match(final Cookie cookie, final CookieOrigin origin) {
        if (cookie == null) {
            throw new IllegalArgumentException("Cookie may not be null");
        }
        if (origin == null) {
            throw new IllegalArgumentException("Cookie origin may not be null");
        }
        String host = origin.getHost();
        String domain = cookie.getDomain();
        if (domain == null) {
            return false;
        }
        return host.equals(domain) || (domain.startsWith(".") && host.endsWith(domain));
    }
    
}