/**
 * Licensed to Apereo under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Apereo licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License.  You may obtain a
 * copy of the License at the following location:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.jasig.portal.portlets.groupadmin;

import org.jasig.portal.EntityIdentifier;
import org.jasig.portal.groups.IEntityGroup;
import org.jasig.portal.groups.IGroupConstants;
import org.jasig.portal.groups.pags.dao.*;
import org.jasig.portal.groups.pags.testers.AdHocGroupTester;
import org.jasig.portal.layout.dlm.remoting.IGroupListHelper;
import org.jasig.portal.security.IAuthorizationPrincipal;
import org.jasig.portal.security.IPermission;
import org.jasig.portal.security.IPerson;
import org.jasig.portal.security.RuntimeAuthorizationException;
import org.jasig.portal.services.AuthorizationService;
import org.jasig.portal.services.GroupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;

/**
 * Provides helper methods for PAGS group administration webflows.  Uses
 * {@linkplain PagsService}, rather than the PAGS DAOs, so permission checking
 * is not necessary (done at the service layer).
 *
 * @author  Benito J. Gonzalez <bgonzalez@unicon.net>
 * @see     org.jasig.portal.groups.pags.dao.IPersonAttributesGroupDefinitionDao
 * @see     org.jasig.portal.groups.pags.dao.IPersonAttributesGroupTestDefinitionDao
 * @see     org.jasig.portal.groups.pags.dao.IPersonAttributesGroupTestGroupDefinitionDao
 * @see     org.jasig.portal.groups.pags.testers.AdHocGroupTester
 * @since   4.3
 */
@Service
public final class PagsAdministrationHelper {

    private static final String AD_HOC_GROUP_TESTER = AdHocGroupTester.class.getName();

    private static final Set<IPersonAttributesGroupTestDefinition> EMPTY_TESTS =
            Collections.emptySet();

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Value("${ad.hoc.group.parent:Ad Hoc Groups}")
    private String adHocParentGroupName;

    @Autowired
    private IGroupListHelper groupListHelper;

    /*
     * It's a little weird (and regrettable) that we're using the DAOs directly
     * for testGroups & tests;  use of the PagsService here gives us permissions
     * checking and some business logic.  The JPA PAGS DAOs need some
     * refactoring before we can stop using them.
     */
    @Autowired
    private IPersonAttributesGroupTestGroupDefinitionDao pagsTestGroupDefDao;
    @Autowired
    private IPersonAttributesGroupTestDefinitionDao pagsTestDefDao;

    @Autowired
    private PagsService pagsService;

    /**
     * Construct an {@link AdHocPagsForm} for the specified group name.
     *
     * @param groupName      PAGS group name
     * @return          form version of specified group
     * @see             #isManagedAdHocGroup(IPersonAttributesGroupDefinition)
     */
    public AdHocPagsForm initializeAdHocPagsFormForUpdate(IPerson person, String groupName) {

        logger.debug("Initializing group form for ad hoc PAGS group named {}", groupName);

        IPersonAttributesGroupDefinition group = pagsService.getPagsDefinitionByName(person, groupName);
        if (group == null) {
            // The group does not exists or this user can't see it
            throw new IllegalArgumentException("Group not found:  " + groupName);
        }
        if (!isManagedAdHocGroup(group)) {
            // The group is not one that we can manage with this form
            final String msg = "The specified group is not manageable in this UI:  " + groupName;
            throw new IllegalArgumentException(msg);
        }

        final AdHocPagsForm rslt = new AdHocPagsForm();
        rslt.setName(group.getName());  // Once set, the name field may not be changed
        Set<IPersonAttributesGroupTestGroupDefinition> testGroups = group.getTestGroups();
        if (!testGroups.isEmpty()) {
            Set<IPersonAttributesGroupTestDefinition> tests = testGroups.iterator().next().getTests();
            for (IPersonAttributesGroupTestDefinition test : tests) {
                // We already asserted that the tests were all AdHocGroupTester classes
                switch (test.getAttributeName()) {
                    case AdHocGroupTester.MEMBER_OF:
                        rslt.addIncludes(test.getTestValue());
                        break;
                    case AdHocGroupTester.NOT_MEMBER_OF:
                        rslt.addExcludes(test.getTestValue());
                        break;
                    default:
                        // Garbage...
                        logger.warn("Invalid value for IPersonAttributesGroupTestDefinition.attributeName "
                                + "'{}' where test class is AdHocGroupTester.", test.getAttributeName());
                        break;
                }
            }
        }
        return rslt;
    }

    /**
     * Create a new group under the well-known ad hoc groups parent.
     *
     * @param form      form representing the new group configuration
     * @param user      user performing the update
     */
    public void createGroup(String parentGroupName, AdHocPagsForm form, IPerson user) {

        logger.debug("Creating group for group form [{}]", form.toString());


        EntityIdentifier[] eids = GroupService.searchForGroups(parentGroupName, IGroupConstants.IS, IPerson.class);
        if (eids.length == 0) {
            throw new IllegalArgumentException("Parent group does not exist: " + parentGroupName);
        }
        IEntityGroup parentGroup = GroupService.findGroup(eids[0].toString());  // Names must be unique
        IPersonAttributesGroupDefinition group = pagsService.createPagsDefinition(user, parentGroup, form.getName(), form.getDescription());

        IPersonAttributesGroupTestGroupDefinition testGroup = this.pagsTestGroupDefDao.createPersonAttributesGroupTestGroupDefinition(group);
        updateAdHocTesters(user, testGroup, form.getIncludes(), form.getExcludes());

        // This is a bit of a mess, since this method will check for
        //EDIT_GROUP permission (and we supposedly require CREATE_GROUP)
        pagsService.updatePagsDefinition(user, group);  // Because we populated the testGroup

    }

    /**
     * Update includes/excludes lists for a group. Changes to group name or parent/members lists
     * are not supported.
     *
     * @param user      user performing the update
     * @param form      form representing the new group configuration
     */
    public void updateGroup(IPerson user, AdHocPagsForm form) {

        logger.debug("Updating group for group form [{}]", form.toString());

        // Note:  name of an existing group may not be changed on the form
        final String groupName = form.getName();
        IPersonAttributesGroupDefinition group = pagsService.getPagsDefinitionByName(user, groupName);
        if (group == null) {
            throw new IllegalArgumentException("Group not found:  " + groupName);
        }

        group.setDescription(form.getDescription());  // Generated;  will be updated if includes/excludes change

        IPersonAttributesGroupTestGroupDefinition testGroup;
        if (group.getTestGroups().isEmpty()) {
            testGroup = this.pagsTestGroupDefDao.createPersonAttributesGroupTestGroupDefinition(group);
        } else {
            testGroup = group.getTestGroups().iterator().next();
        }
        updateAdHocTesters(user, testGroup, form.getIncludes(), form.getExcludes());

        pagsService.updatePagsDefinition(user, group);

    }

    /**
     * Delete a named PAGS group from the group store, checking if the user has permission.
     * 
     * @param groupName     name of the group to be deleted
     * @param user          performing the delete operation
     */
    public void deleteGroup(String groupName, IPerson user) {
        logger.info("Deleting ad hoc PAGS group named {}", groupName);

        IPersonAttributesGroupDefinition group = pagsService.getPagsDefinitionByName(user, groupName);
        if (group == null) {
            throw new IllegalArgumentException("Group not found:  " + groupName);
        }

        pagsService.deletePagsDefinition(user, group);
    }

    /*
     * Implementation
     */

    /**
     * Verify that the group is one of the ad hoc groups that is eligible for management via this feature.
     * <ul>
     *     <li>Child of {@code ad hoc groups} group -- THIS NEEDED? -- check not implemented</li>
     *     <li>Single test group</li>
     *     <li>At least one ad hoc group tester</li>
     *     <li>Only ad hoc group testers</li>
     *     <li>No group members</li>
     * </ul>
     *
     * @param group     PAGS group
     * @return          {@code true} if group meets the criteria to be managed by this UI feature; otherwise, {@code false}
     */
    private boolean isManagedAdHocGroup(IPersonAttributesGroupDefinition group) {
        if (group.getMembers().size() > 0) {
            return false;
        }
        Set<IPersonAttributesGroupTestGroupDefinition> testGroups = group.getTestGroups();
        if (testGroups.size() == 0) {
            return true; // No direct tests, so group is a branch node
        }
        if (testGroups.size() > 1) {
            return false;
        }
        Set<IPersonAttributesGroupTestDefinition> tests = testGroups.iterator().next().getTests();
        if (tests.isEmpty()) {
            return false;
        }
        for (IPersonAttributesGroupTestDefinition test: tests) {
            if (!AD_HOC_GROUP_TESTER.equals(test.getTesterClassName())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Add test definitions to the test group for the given list of includes and excludes group lists.
     * @param user          user to check permission for adding each group
     * @param testGroup     target test group that is update with the group lists
     * @param includes      set of group names to add ad hoc testers to test group
     * @param excludes      set of group names to add ad hoc testers to test group with exclude set
     */
    private void updateAdHocTesters(IPerson user, IPersonAttributesGroupTestGroupDefinition testGroup, Set<String> includes, Set<String> excludes) {

        /* In case this is an existing group receiving edits,
         * we need to clear the testGroup we've been sent.
         */
        testGroup.setTests(EMPTY_TESTS);

        /*
         * NOTE:  The "normal" permissions checks are handled by the PagesService
         * bean;  the AdHocGroupTester is a special case, in that the group(s)
         * selected for the tests need to require VIEW_GROUP permission as well.
         */

        for (String group: includes) {
            if (hasPermission(user, IPermission.VIEW_GROUP_ACTIVITY, group)) {
                this.pagsTestDefDao.createPersonAttributesGroupTestDefinition(
                        testGroup, AdHocGroupTester.MEMBER_OF, AD_HOC_GROUP_TESTER, group);
            } else {
                String msg = "User '" + user.getUserName() +"' does not have "
                        + "permission to choose the specified group:  " + group;
                throw new RuntimeAuthorizationException(msg);
            }
        }

        for (String group: excludes) {
            if (hasPermission(user, IPermission.VIEW_GROUP_ACTIVITY, group)) {
                this.pagsTestDefDao.createPersonAttributesGroupTestDefinition(
                        testGroup, AdHocGroupTester.NOT_MEMBER_OF, AD_HOC_GROUP_TESTER, group);
            } else {
                String msg = "User '" + user.getUserName() +"' does not have "
                        + "permission to choose the specified group:  " + group;
                throw new RuntimeAuthorizationException(msg);
            }
        }

    }

    /**
     * Check the authorization principal matching the supplied IPerson, permission and target.
     * 
     * @param person        current user to check permission against
     * @param permission    permission name to check
     * @param target        the key of the target
     * @return              {@code true} if the person has permission for the checked principal; otherwise, {@code false}
     */
    private boolean hasPermission(IPerson person, String permission, String target) {
        EntityIdentifier ei = person.getEntityIdentifier();
        IAuthorizationPrincipal ap = AuthorizationService.instance().newPrincipal(ei.getKey(), ei.getType());
        return ap.hasPermission(IPermission.PORTAL_GROUPS, permission, target);
    }

}