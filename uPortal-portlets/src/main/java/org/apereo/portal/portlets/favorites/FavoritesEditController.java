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

import java.util.List;

import javax.portlet.RenderRequest;

import org.apereo.portal.UserPreferencesManager;
import org.apereo.portal.layout.IUserLayout;
import org.apereo.portal.layout.IUserLayoutManager;
import org.apereo.portal.layout.node.IUserLayoutNodeDescription;
import org.apereo.portal.user.IUserInstance;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.portlet.bind.annotation.RenderMapping;

/**
 * Spring PortletMVC Controller for Favorites Portlet implementing EDIT mode.
 *
 * @since 4.1
 */
@Controller
@RequestMapping("EDIT")
public class FavoritesEditController extends AbstractFavoritesController {

    /**
     * Handles all Favorites portlet EDIT mode renders. Populates model with user's favorites and
     * selects a view to display those favorites.
     *
     * <p>View selection:
     *
     * <p>Returns "jsp/Favorites/edit" in the normal case where the user has at least one favorited
     * portlet or favorited collection.
     *
     * <p>Returns "jsp/Favorites/edit_zero" in the edge case where the user has zero favorited
     * portlets AND zero favorited collections.
     *
     * <p>Model: marketPlaceFname --> String functional name of Marketplace portlet, or null if not
     * available. collections --> List of favorited collections (IUserLayoutNodeDescription s)
     * favorites --> List of favorited individual portlets (IUserLayoutNodeDescription s)
     * successMessageCode --> String success message bundle key, or null if none errorMessageCode
     * --> String error message bundle key, or null if none nameOfFavoriteActedUpon --> Name of
     * favorite acted upon, intended as parameter to success or error message
     *
     * @param model . Spring model. This method adds five model attributes.
     * @return jsp/Favorites/edit[_zero]
     */
    @RenderMapping
    public String initializeView(Model model, RenderRequest renderRequest) {

        IUserInstance ui =
                userInstanceManager.getUserInstance(portalRequestUtils.getCurrentPortalRequest());
        UserPreferencesManager upm = (UserPreferencesManager) ui.getPreferencesManager();
        IUserLayoutManager ulm = upm.getUserLayoutManager();

        IUserLayout userLayout = ulm.getUserLayout();

        // TODO: the portlet could predicate including a non-null marketplace portlet fname
        // on the accessing user having permission to render the portlet referenced by that fname
        // so that portlet would gracefully degrade when configured with bad marketplace portlet
        // fname
        // and also gracefully degrade when the accessing user doesn't have permission to access an
        // otherwise
        // viable configured marketplace.  This complexity may not be worth it.  Anyway it is not
        // yet implemented.

        model.addAttribute("marketplaceFname", this.marketplaceFName);

        List<IUserLayoutNodeDescription> collections =
                FavoritesUtils.getFavoriteCollections(userLayout);
        model.addAttribute("collections", collections);

        List<IUserLayoutNodeDescription> favorites = FavoritesUtils.getFavoritePortlets(userLayout);
        model.addAttribute("favorites", favorites);

        model.addAttribute("successMessageCode", renderRequest.getParameter("successMessageCode"));
        model.addAttribute("errorMessageCode", renderRequest.getParameter("errorMessageCode"));

        model.addAttribute(
                "nameOfFavoriteActedUpon", renderRequest.getParameter("nameOfFavoriteActedUpon"));

        // default to the regular old edit view
        String viewName = "jsp/Favorites/edit";

        if (collections.isEmpty() && favorites.isEmpty()) {
            // use the special case view
            viewName = "jsp/Favorites/edit_zero";
        }

        logger.trace(
                "Favorites Portlet EDIT mode built model [{}] and selected view {}.",
                model,
                viewName);

        return viewName;
    }
}
