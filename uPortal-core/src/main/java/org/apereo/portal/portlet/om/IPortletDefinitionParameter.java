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
package org.apereo.portal.portlet.om;

/**
 * Parameters based on <code>IPortletDefinitionParameter</code> function as defaults for a portlet
 * and may sometimes be overridden by end users.
 *
 */
public interface IPortletDefinitionParameter {

    /**
     * Get the name of the channel parameter.
     *
     * @return the name of the channel parameter.
     */
    String getName();

    /**
     * Get the default value of the channel parameter.
     *
     * @return the default value for this channel parameter.
     */
    String getValue();

    /**
     * Get a description of this channel parameter.
     *
     * @return a description of this channel parameter.
     */
    String getDescription();

    /**
     * Set the default value for this channel parameter.
     *
     * @param value the default value for this channel parameter.
     */
    void setValue(String value);

    /**
     * Set the description of this channel parameter.
     *
     * @param descr description of this channel parameter.
     */
    void setDescription(String descr);

}
