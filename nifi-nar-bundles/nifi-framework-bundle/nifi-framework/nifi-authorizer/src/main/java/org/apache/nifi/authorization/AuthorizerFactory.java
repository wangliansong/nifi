/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nifi.authorization;

import org.apache.nifi.authorization.exception.AuthorizationAccessException;
import org.apache.nifi.authorization.exception.AuthorizerCreationException;
import org.apache.nifi.authorization.exception.AuthorizerDestructionException;
import org.apache.nifi.authorization.exception.UninheritableAuthorizationsException;
import org.apache.nifi.nar.NarCloseable;

import java.util.Set;

public final class AuthorizerFactory {

    /**
     * Checks if another policy exists with the same resource and action as the given policy.
     *
     * @param checkAccessPolicy an access policy being checked
     * @return true if another access policy exists with the same resource and action, false otherwise
     */
    private static boolean policyExists(final AccessPolicyProvider accessPolicyProvider, final AccessPolicy checkAccessPolicy) {
        for (AccessPolicy accessPolicy : accessPolicyProvider.getAccessPolicies()) {
            if (!accessPolicy.getIdentifier().equals(checkAccessPolicy.getIdentifier())
                    && accessPolicy.getResource().equals(checkAccessPolicy.getResource())
                    && accessPolicy.getAction().equals(checkAccessPolicy.getAction())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if another user exists with the same identity.
     *
     * @param identifier identity of the user
     * @param identity identity of the user
     * @return true if another user exists with the same identity, false otherwise
     */
    private static boolean tenantExists(final UserGroupProvider userGroupProvider, final String identifier, final String identity) {
        for (User user : userGroupProvider.getUsers()) {
            if (!user.getIdentifier().equals(identifier)
                    && user.getIdentity().equals(identity)) {
                return true;
            }
        }

        for (Group group : userGroupProvider.getGroups()) {
            if (!group.getIdentifier().equals(identifier)
                    && group.getName().equals(identity)) {
                return true;
            }
        }

        return false;
    }

    public static Authorizer installIntegrityChecks(final Authorizer baseAuthorizer) {
        if (baseAuthorizer instanceof ManagedAuthorizer) {
            final ManagedAuthorizer baseManagedAuthorizer = (ManagedAuthorizer) baseAuthorizer;
            return new ManagedAuthorizer() {
                @Override
                public String getFingerprint() throws AuthorizationAccessException {
                    return baseManagedAuthorizer.getFingerprint();
                }

                @Override
                public void inheritFingerprint(String fingerprint) throws AuthorizationAccessException {
                    baseManagedAuthorizer.inheritFingerprint(fingerprint);
                }

                @Override
                public void checkInheritability(String proposedFingerprint) throws AuthorizationAccessException, UninheritableAuthorizationsException {
                    baseManagedAuthorizer.checkInheritability(proposedFingerprint);
                }

                @Override
                public AccessPolicyProvider getAccessPolicyProvider() {
                    final AccessPolicyProvider baseAccessPolicyProvider = baseManagedAuthorizer.getAccessPolicyProvider();
                    if (baseAccessPolicyProvider instanceof ConfigurableAccessPolicyProvider) {
                        final ConfigurableAccessPolicyProvider baseConfigurableAccessPolicyProvider = (ConfigurableAccessPolicyProvider) baseAccessPolicyProvider;
                        return new ConfigurableAccessPolicyProvider() {
                            @Override
                            public String getFingerprint() throws AuthorizationAccessException {
                                return baseConfigurableAccessPolicyProvider.getFingerprint();
                            }

                            @Override
                            public void inheritFingerprint(String fingerprint) throws AuthorizationAccessException {
                                baseConfigurableAccessPolicyProvider.inheritFingerprint(fingerprint);
                            }

                            @Override
                            public void checkInheritability(String proposedFingerprint) throws AuthorizationAccessException, UninheritableAuthorizationsException {
                                baseConfigurableAccessPolicyProvider.checkInheritability(proposedFingerprint);
                            }

                            @Override
                            public AccessPolicy addAccessPolicy(AccessPolicy accessPolicy) throws AuthorizationAccessException {
                                if (policyExists(baseConfigurableAccessPolicyProvider, accessPolicy)) {
                                    throw new IllegalStateException(String.format("Found multiple policies for '%s' with '%s'.", accessPolicy.getResource(), accessPolicy.getAction()));
                                }
                                return baseConfigurableAccessPolicyProvider.addAccessPolicy(accessPolicy);
                            }

                            @Override
                            public AccessPolicy updateAccessPolicy(AccessPolicy accessPolicy) throws AuthorizationAccessException {
                                return baseConfigurableAccessPolicyProvider.updateAccessPolicy(accessPolicy);
                            }

                            @Override
                            public AccessPolicy deleteAccessPolicy(AccessPolicy accessPolicy) throws AuthorizationAccessException {
                                return baseConfigurableAccessPolicyProvider.deleteAccessPolicy(accessPolicy);
                            }

                            @Override
                            public Set<AccessPolicy> getAccessPolicies() throws AuthorizationAccessException {
                                return baseConfigurableAccessPolicyProvider.getAccessPolicies();
                            }

                            @Override
                            public AccessPolicy getAccessPolicy(String identifier) throws AuthorizationAccessException {
                                return baseConfigurableAccessPolicyProvider.getAccessPolicy(identifier);
                            }

                            @Override
                            public AccessPolicy getAccessPolicy(String resourceIdentifier, RequestAction action) throws AuthorizationAccessException {
                                return baseConfigurableAccessPolicyProvider.getAccessPolicy(resourceIdentifier, action);
                            }

                            @Override
                            public UserGroupProvider getUserGroupProvider() {
                                final UserGroupProvider baseUserGroupProvider = baseConfigurableAccessPolicyProvider.getUserGroupProvider();
                                if (baseUserGroupProvider instanceof ConfigurableUserGroupProvider) {
                                    final ConfigurableUserGroupProvider baseConfigurableUserGroupProvider = (ConfigurableUserGroupProvider) baseUserGroupProvider;
                                    return new ConfigurableUserGroupProvider() {
                                        @Override
                                        public String getFingerprint() throws AuthorizationAccessException {
                                            return baseConfigurableUserGroupProvider.getFingerprint();
                                        }

                                        @Override
                                        public void inheritFingerprint(String fingerprint) throws AuthorizationAccessException {
                                            baseConfigurableUserGroupProvider.inheritFingerprint(fingerprint);
                                        }

                                        @Override
                                        public void checkInheritability(String proposedFingerprint) throws AuthorizationAccessException, UninheritableAuthorizationsException {
                                            baseConfigurableUserGroupProvider.checkInheritability(proposedFingerprint);
                                        }

                                        @Override
                                        public User addUser(User user) throws AuthorizationAccessException {
                                            if (tenantExists(baseConfigurableUserGroupProvider, user.getIdentifier(), user.getIdentity())) {
                                                throw new IllegalStateException(String.format("User/user group already exists with the identity '%s'.", user.getIdentity()));
                                            }
                                            return baseConfigurableUserGroupProvider.addUser(user);
                                        }

                                        @Override
                                        public User updateUser(User user) throws AuthorizationAccessException {
                                            if (tenantExists(baseConfigurableUserGroupProvider, user.getIdentifier(), user.getIdentity())) {
                                                throw new IllegalStateException(String.format("User/user group already exists with the identity '%s'.", user.getIdentity()));
                                            }
                                            return baseConfigurableUserGroupProvider.updateUser(user);
                                        }

                                        @Override
                                        public User deleteUser(User user) throws AuthorizationAccessException {
                                            return baseConfigurableUserGroupProvider.deleteUser(user);
                                        }

                                        @Override
                                        public Group addGroup(Group group) throws AuthorizationAccessException {
                                            if (tenantExists(baseConfigurableUserGroupProvider, group.getIdentifier(), group.getName())) {
                                                throw new IllegalStateException(String.format("User/user group already exists with the identity '%s'.", group.getName()));
                                            }
                                            return baseConfigurableUserGroupProvider.addGroup(group);
                                        }

                                        @Override
                                        public Group updateGroup(Group group) throws AuthorizationAccessException {
                                            if (tenantExists(baseConfigurableUserGroupProvider, group.getIdentifier(), group.getName())) {
                                                throw new IllegalStateException(String.format("User/user group already exists with the identity '%s'.", group.getName()));
                                            }
                                            return baseConfigurableUserGroupProvider.updateGroup(group);
                                        }

                                        @Override
                                        public Group deleteGroup(Group group) throws AuthorizationAccessException {
                                            return baseConfigurableUserGroupProvider.deleteGroup(group);
                                        }

                                        @Override
                                        public Set<User> getUsers() throws AuthorizationAccessException {
                                            return baseConfigurableUserGroupProvider.getUsers();
                                        }

                                        @Override
                                        public User getUser(String identifier) throws AuthorizationAccessException {
                                            return baseConfigurableUserGroupProvider.getUser(identifier);
                                        }

                                        @Override
                                        public User getUserByIdentity(String identity) throws AuthorizationAccessException {
                                            return baseConfigurableUserGroupProvider.getUserByIdentity(identity);
                                        }

                                        @Override
                                        public Set<Group> getGroups() throws AuthorizationAccessException {
                                            return baseConfigurableUserGroupProvider.getGroups();
                                        }

                                        @Override
                                        public Group getGroup(String identifier) throws AuthorizationAccessException {
                                            return baseConfigurableUserGroupProvider.getGroup(identifier);
                                        }

                                        @Override
                                        public UserAndGroups getUserAndGroups(String identity) throws AuthorizationAccessException {
                                            return baseConfigurableUserGroupProvider.getUserAndGroups(identity);
                                        }

                                        @Override
                                        public void initialize(UserGroupProviderInitializationContext initializationContext) throws AuthorizerCreationException {
                                            baseConfigurableUserGroupProvider.initialize(initializationContext);
                                        }

                                        @Override
                                        public void onConfigured(AuthorizerConfigurationContext configurationContext) throws AuthorizerCreationException {
                                            baseConfigurableUserGroupProvider.onConfigured(configurationContext);
                                        }

                                        @Override
                                        public void preDestruction() throws AuthorizerDestructionException {
                                            baseConfigurableUserGroupProvider.preDestruction();
                                        }
                                    };
                                } else {
                                    return baseUserGroupProvider;
                                }
                            }

                            @Override
                            public void initialize(AccessPolicyProviderInitializationContext initializationContext) throws AuthorizerCreationException {
                                baseConfigurableAccessPolicyProvider.initialize(initializationContext);
                            }

                            @Override
                            public void onConfigured(AuthorizerConfigurationContext configurationContext) throws AuthorizerCreationException {
                                baseConfigurableAccessPolicyProvider.onConfigured(configurationContext);
                            }

                            @Override
                            public void preDestruction() throws AuthorizerDestructionException {
                                baseConfigurableAccessPolicyProvider.preDestruction();
                            }
                        };
                    } else {
                        return baseAccessPolicyProvider;
                    }
                }

                @Override
                public AuthorizationResult authorize(AuthorizationRequest request) throws AuthorizationAccessException {
                    return baseManagedAuthorizer.authorize(request);
                }

                @Override
                public void initialize(AuthorizerInitializationContext initializationContext) throws AuthorizerCreationException {
                    baseManagedAuthorizer.initialize(initializationContext);
                }

                @Override
                public void onConfigured(AuthorizerConfigurationContext configurationContext) throws AuthorizerCreationException {
                    baseManagedAuthorizer.onConfigured(configurationContext);

                    final AccessPolicyProvider accessPolicyProvider = baseManagedAuthorizer.getAccessPolicyProvider();
                    final UserGroupProvider userGroupProvider = accessPolicyProvider.getUserGroupProvider();

                    // ensure that only one policy per resource-action exists
                    for (AccessPolicy accessPolicy : accessPolicyProvider.getAccessPolicies()) {
                        if (policyExists(accessPolicyProvider, accessPolicy)) {
                            throw new AuthorizerCreationException(String.format("Found multiple policies for '%s' with '%s'.", accessPolicy.getResource(), accessPolicy.getAction()));
                        }
                    }

                    // ensure that only one group exists per identity
                    for (User user : userGroupProvider.getUsers()) {
                        if (tenantExists(userGroupProvider, user.getIdentifier(), user.getIdentity())) {
                            throw new AuthorizerCreationException(String.format("Found multiple users/user groups with identity '%s'.", user.getIdentity()));
                        }
                    }

                    // ensure that only one group exists per identity
                    for (Group group : userGroupProvider.getGroups()) {
                        if (tenantExists(userGroupProvider, group.getIdentifier(), group.getName())) {
                            throw new AuthorizerCreationException(String.format("Found multiple users/user groups with name '%s'.", group.getName()));
                        }
                    }
                }

                @Override
                public void preDestruction() throws AuthorizerDestructionException {
                    baseManagedAuthorizer.preDestruction();
                }
            };
        } else {
            return baseAuthorizer;
        }
    }

    /**
     * Decorates the base authorizer to ensure the nar context classloader is used when invoking the underlying methods.
     *
     * @param baseAuthorizer base authorizer
     * @return authorizer
     */
    public static Authorizer withNarLoader(final Authorizer baseAuthorizer) {
        if (baseAuthorizer instanceof ManagedAuthorizer) {
            final ManagedAuthorizer baseManagedAuthorizer = (ManagedAuthorizer) baseAuthorizer;
            return new ManagedAuthorizer() {
                @Override
                public String getFingerprint() throws AuthorizationAccessException {
                    try (final NarCloseable narCloseable = NarCloseable.withNarLoader()) {
                        return baseManagedAuthorizer.getFingerprint();
                    }
                }

                @Override
                public void inheritFingerprint(String fingerprint) throws AuthorizationAccessException {
                    try (final NarCloseable narCloseable = NarCloseable.withNarLoader()) {
                        baseManagedAuthorizer.inheritFingerprint(fingerprint);
                    }
                }

                @Override
                public void checkInheritability(String proposedFingerprint) throws AuthorizationAccessException, UninheritableAuthorizationsException {
                    try (final NarCloseable narCloseable = NarCloseable.withNarLoader()) {
                        baseManagedAuthorizer.checkInheritability(proposedFingerprint);
                    }
                }

                @Override
                public AccessPolicyProvider getAccessPolicyProvider() {
                    try (final NarCloseable narCloseable = NarCloseable.withNarLoader()) {
                        return baseManagedAuthorizer.getAccessPolicyProvider();
                    }
                }

                @Override
                public AuthorizationResult authorize(AuthorizationRequest request) throws AuthorizationAccessException {
                    try (final NarCloseable narCloseable = NarCloseable.withNarLoader()) {
                        return baseManagedAuthorizer.authorize(request);
                    }
                }

                @Override
                public void initialize(AuthorizerInitializationContext initializationContext) throws AuthorizerCreationException {
                    try (final NarCloseable narCloseable = NarCloseable.withNarLoader()) {
                        baseManagedAuthorizer.initialize(initializationContext);
                    }
                }

                @Override
                public void onConfigured(AuthorizerConfigurationContext configurationContext) throws AuthorizerCreationException {
                    try (final NarCloseable narCloseable = NarCloseable.withNarLoader()) {
                        baseManagedAuthorizer.onConfigured(configurationContext);
                    }
                }

                @Override
                public void preDestruction() throws AuthorizerDestructionException {
                    try (final NarCloseable narCloseable = NarCloseable.withNarLoader()) {
                        baseManagedAuthorizer.preDestruction();
                    }
                }
            };
        } else {
            return new Authorizer() {
                @Override
                public AuthorizationResult authorize(final AuthorizationRequest request) throws AuthorizationAccessException {
                    try (final NarCloseable narCloseable = NarCloseable.withNarLoader()) {
                        return baseAuthorizer.authorize(request);
                    }
                }

                @Override
                public void initialize(AuthorizerInitializationContext initializationContext) throws AuthorizerCreationException {
                    try (final NarCloseable narCloseable = NarCloseable.withNarLoader()) {
                        baseAuthorizer.initialize(initializationContext);
                    }
                }

                @Override
                public void onConfigured(AuthorizerConfigurationContext configurationContext) throws AuthorizerCreationException {
                    try (final NarCloseable narCloseable = NarCloseable.withNarLoader()) {
                        baseAuthorizer.onConfigured(configurationContext);
                    }
                }

                @Override
                public void preDestruction() throws AuthorizerDestructionException {
                    try (final NarCloseable narCloseable = NarCloseable.withNarLoader()) {
                        baseAuthorizer.preDestruction();
                    }
                }
            };
        }
    }

    private AuthorizerFactory() {}
}
