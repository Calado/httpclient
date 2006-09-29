/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Copyright 2002-2004 The Apache Software Foundation
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.http.cookie.CookieAttributeHandler;
import org.apache.http.cookie.CookieSpec;


/**
 * Abstract cookie specification which can delegate the job of parsing,
 * validation or matching cookie attributes to a number of arbitrary 
 * {@link CookieAttributeHandler}s.
 * 
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 * 
 * @since 4.0 
 */
public abstract class AbstractCookieSpec implements CookieSpec {
    
    /**
     * Stores the list of attribute handlers
     */
    private final List attribHandlerList;
    
    /**
    * Stores attribute name -> attribute handler mappings
    */
    private final Map attribHandlerMap;

    /** 
     * Default constructor 
     * */
    public AbstractCookieSpec() {
        super();
        this.attribHandlerMap = new HashMap(10);        
        this.attribHandlerList = new ArrayList(10);
    }

    public void registerAttribHandler(
            final String name, final CookieAttributeHandler handler) {
        if (name == null) {
            throw new IllegalArgumentException("Attribute name may not be null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("Attribute handler may not be null");
        }
        if (!this.attribHandlerList.contains(handler)) {
            this.attribHandlerList.add(handler);
        }
        this.attribHandlerMap.put(name, handler);
    }
    
    /**
     * Finds an attribute handler {@link CookieAttributeHandler} for the
     * given attribute. Returns <tt>null</tt> if no attribute handler is
     * found for the specified attribute.
     *
     * @param name attribute name. e.g. Domain, Path, etc.
     * @return an attribute handler or <tt>null</tt>
     */
    protected CookieAttributeHandler findAttribHandler(final String name) {
        return (CookieAttributeHandler) this.attribHandlerMap.get(name);
    }
    
    /**
     * Gets attribute handler {@link CookieAttributeHandler} for the
     * given attribute.
     *
     * @param name attribute name. e.g. Domain, Path, etc.
     * @throws IllegalStateException if handler not found for the
     *          specified attribute.
     */
    protected CookieAttributeHandler getAttribHandler(final String name) {
        CookieAttributeHandler handler = findAttribHandler(name);
        if (handler == null) {
            throw new IllegalStateException("Handler not registered for " +
                                            name + " attribute.");
        } else {
            return handler;
        }
    }

    protected Iterator getAttribHandlerIterator() {
        return this.attribHandlerList.iterator();
    }
    
}