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
package org.apereo.portal.portlets.favorites;

import javax.portlet.ActionResponse;
import javax.portlet.PortletMode;
import javax.servlet.http.HttpServletRequest;

import org.apereo.portal.IUserPreferencesManager;
import org.apereo.portal.layout.IUserLayout;
import org.apereo.portal.layout.IUserLayoutManager;
import org.apereo.portal.layout.node.IUserLayoutNodeDescription;
import org.apereo.portal.url.IPortalRequestUtils;
import org.apereo.portal.user.IUserInstance;
import org.apereo.portal.user.IUserInstanceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.portlet.bind.annotation.ActionMapping;

/**
 * The Favorites controllers handling the VIEW and EDIT modes share dependency auto-wiring needs.
 * This abstract class implements those dependencies and auto-wiring once so that the concrete
 * controller implementations can inherit that functionality.
 *
 * @since 4.1
 */
public abstract class AbstractFavoritesController {

    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected IUserInstanceManager userInstanceManager;
    protected IPortalRequestUtils portalRequestUtils;

    /**
     * Functional name of Marketplace portlet, or null if links to a Marketplace are not desired or
     * are not feasible.
     */
    protected String marketplaceFName;

    @Autowired
    public void setUserInstanceManager(IUserInstanceManager userInstanceManager) {
        this.userInstanceManager = userInstanceManager;
    }

    @Autowired
    public void setPortalRequestUtils(IPortalRequestUtils portalRequestUtils) {
        this.portalRequestUtils = portalRequestUtils;
    }

    /**
     * Configures FavoritesController to include a Marketplace portlet functional name in the Model,
     * which ultimately signals and enables the View to include convenient link to Marketplace for
     * user to add new favorites.
     *
     * <p>When set to null, signals Favorites portlet to suppress links to Marketplace. Setting to
     * the empty String or to the literal value "null" (ignoring case) is equivalent to setting to
     * null.
     *
     * <p>This is for convenience in expressing no-marketplace-fname-available via injected value
     * from properties file. Defaults to the value of the property
     * "org.apereo.portal.portlets.favorites.MarketplaceFunctionalName", or null if that property is
     * not set.
     *
     * <p>The functional name can technically be the fname of any portlet. It doesn't have to be The
     * Marketplace Portlet. Perhaps you've got your own take on Marketplace.
     *
     * <p>This allows Favorites to support integration with Marketplace without requiring a
     * Marketplace, gracefully degrading when no Marketplace available.
     *
     * @param marketplaceFunctionalName String fname of a marketplace portlet, or null.
     */
    @Value("${org.apereo.portal.portlets.favorites.MarketplaceFunctionalName:null}")
    public void setMarketplaceFName(String marketplaceFunctionalName) {

        // interpret null, non-text-having, or literal "null" as
        // signaling lack of Marketplace functional name.
        if (!StringUtils.hasText(marketplaceFunctionalName)
                || "null".equalsIgnoreCase(marketplaceFunctionalName)) {
            marketplaceFunctionalName = null;
        }

        this.marketplaceFName = marketplaceFunctionalName;
    }

    /**
     * Un-favorite a favorite node (portlet or collection) identified by node ID. Routed by the
     * action=delete parameter. If no favorites remain after un-favoriting, switches portlet mode to
     * VIEW.
     *
     * <p>Sets render parameters: successMessageCode: message code of success message if applicable
     * errorMessageCode: message code of error message if applicable nameOfFavoriteActedUpon:
     * user-facing name of favorite acted upon. action: will be set to "list" to facilitate not
     * repeatedly attempting delete.
     *
     * <p>Exactly one of [successMessageCode|errorMessageCode] render parameters will be set.
     * nameOfFavoriteActedUpon and action will always be set.
     *
     * @param nodeId identifier of target node
     * @param response ActionResponse onto which render parameters will, mode may, be set
     */
    @ActionMapping(params = {"action=delete"})
    public void unFavoriteNode(@RequestParam("nodeId") String nodeId, ActionResponse response) {

        try {

            // ferret out the layout manager
            HttpServletRequest servletRequest = this.portalRequestUtils.getCurrentPortalRequest();
            IUserInstance userInstance = this.userInstanceManager.getUserInstance(servletRequest);
            IUserPreferencesManager preferencesManager = userInstance.getPreferencesManager();
            IUserLayoutManager layoutManager = preferencesManager.getUserLayoutManager();

            IUserLayoutNodeDescription nodeDescription = layoutManager.getNode(nodeId);

            String userFacingNodeName = nodeDescription.getName();
            response.setRenderParameter("nameOfFavoriteActedUpon", userFacingNodeName);

            if (nodeDescription.isDeleteAllowed()) {

                boolean nodeSuccessfullyDeleted = layoutManager.deleteNode(nodeId);

                if (nodeSuccessfullyDeleted) {
                    layoutManager.saveUserLayout();

                    response.setRenderParameter(
                        "successMessageCode", "favorites.unfavorite.success.parameterized");

                    IUserLayout updatedLayout = layoutManager.getUserLayout();

                    // if removed last favorite, return to VIEW mode
                    if (!FavoritesUtils.hasAnyFavorites(updatedLayout)) {
                        response.setPortletMode(PortletMode.VIEW);
                    }

                    logger.debug("Successfully unfavorited [{}]", nodeDescription);

                } else {
                    logger.error(
                        "Failed to delete node [{}] on unfavorite request, but this should have succeeded?",
                        nodeDescription);

                    response.setRenderParameter(
                        "errorMessageCode", "favorites.unfavorite.fail.parameterized");
                }

            } else {
                logger.warn(
                    "Attempt to unfavorite [{}] failed because user lacks permission to delete that layout node.",
                    nodeDescription);

                response.setRenderParameter(
                    "errorMessageCode",
                    "favorites.unfavorite.fail.lack.permission.parameterized");
            }

        } catch (Exception e) {

            // TODO: this log message is kind of useless without the username to put the node in
            // context
            logger.error("Something went wrong unfavoriting nodeId [{}].", nodeId);

            // may have failed to load node description, so fall back on describing by id
            final String fallbackUserFacingNodeName = "node with id " + nodeId;

            response.setRenderParameter(
                "errorMessageCode", "favorites.unfavorite.fail.parameterized");
            response.setRenderParameter("nameOfFavoriteActedUpon", fallbackUserFacingNodeName);
        }

        response.setRenderParameter("action", "list");
    }
}
