/**
 * Licensed to Apereo under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright ownership. Apereo
 * licenses this file to you under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the License at the
 * following location:
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apereo.portal.groups;

import javax.naming.Name;

/** A composite key that identifies a component group service. */
public class CompositeServiceIdentifier extends CompositeEntityIdentifier {

    private static final long serialVersionUID = 1L;

    /**
     * CompositeServiceIdentifier.
     *
     * @param serviceKey java.lang.String
     * @exception GroupsException
     */
    public CompositeServiceIdentifier(String serviceKey) throws GroupsException {
        super(serviceKey, ICompositeGroupService.GROUP_ENTITY_TYPE);
    }
    /**
     * CompositeServiceIdentifier.
     *
     * @param entityKey java.lang.String
     * @param entityType java.lang.Class
     * @exception GroupsException
     */
    public CompositeServiceIdentifier(String entityKey, Class entityType) throws GroupsException {
        super(entityKey, entityType);
    }
    /**
     * The service name is the entire key.
     *
     * @return javax.naming.Name
     */
    @Override
    public synchronized Name getServiceName() {
        return getCompositeKey();
    }
    /**
     * Returns a String that represents the value of this object.
     *
     * @return java.lang.String
     */
    @Override
    public String toString() {
        return "CompositeServiceIdentifier (" + getKey() + ")";
    }
}
