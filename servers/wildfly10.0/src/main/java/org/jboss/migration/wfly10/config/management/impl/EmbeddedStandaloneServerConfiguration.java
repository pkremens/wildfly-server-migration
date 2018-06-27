/*
 * Copyright 2016 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.migration.wfly10.config.management.impl;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.migration.core.jboss.JBossServerConfiguration;
import org.jboss.migration.wfly10.WildFlyServer10;
import org.jboss.migration.wfly10.config.management.DeploymentResource;
import org.jboss.migration.wfly10.config.management.ManagementOperationException;
import org.jboss.migration.wfly10.config.management.StandaloneServerConfiguration;
import org.jboss.migration.wfly10.config.task.ServerConfigurationMigration;
import org.wildfly.core.embedded.EmbeddedProcessFactory;
import org.wildfly.core.embedded.EmbeddedProcessStartException;
import org.wildfly.core.embedded.StandaloneServer;
import org.wildfly.security.manager.WildFlySecurityManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;

/**
 * @author emmartins
 */
public class EmbeddedStandaloneServerConfiguration extends AbstractManageableServerConfiguration implements StandaloneServerConfiguration {

    private final String config;
    private StandaloneServer standaloneServer;

    private final DeploymentResourceImpl.Factory deploymentResources;
    private final DeploymentOverlayResourceImpl.Factory deploymentOverlayResources;
    private final ManagementInterfaceResourceImpl.Factory managementInterfaceResources;
    private final SecurityRealmResourceImpl.Factory securityRealmResources;
    private final SubsystemResourceImpl.Factory subsystemResources;
    private final Map<String, String> propertiesToReset;

    public EmbeddedStandaloneServerConfiguration(JBossServerConfiguration configurationPath, WildFlyServer10 server) {
        super("", PathAddress.EMPTY_ADDRESS, configurationPath, server);
        propertiesToReset = new HashMap<>();
        this.config = configurationPath.getPathRelativeToConfigurationDir().toString();
        deploymentResources = new DeploymentResourceImpl.Factory(getResourcePathAddress(), this);
        addChildResourceFactory(deploymentResources);
        deploymentOverlayResources = new DeploymentOverlayResourceImpl.Factory(getResourcePathAddress(), this);
        addChildResourceFactory(deploymentOverlayResources);
        subsystemResources = new SubsystemResourceImpl.Factory(getResourcePathAddress(), this);
        addChildResourceFactory(subsystemResources);
        final PathAddress managementCoreServicePathAddress = getResourcePathAddress().append(CORE_SERVICE, MANAGEMENT);
        managementInterfaceResources = new ManagementInterfaceResourceImpl.Factory(managementCoreServicePathAddress, this);
        addChildResourceFactory(managementInterfaceResources);
        securityRealmResources = new SecurityRealmResourceImpl.Factory(managementCoreServicePathAddress, this);
        addChildResourceFactory(securityRealmResources);
    }

    @Override
    protected ModelControllerClient startConfiguration() {
        final List<String> cmds = new ArrayList<>();
        cmds.add("--server-config="+config);
        cmds.add("--admin-only");
        cmds.add("-Dorg.wildfly.logging.embedded=false");
        if (!getServer().getEnvironment().isDefaultStandaloneServerDir()) {
            setSystemProperty("jboss.server.base.dir", getServer().getStandaloneDir());
        }
        if (!getServer().getEnvironment().isDefaultStandaloneConfigDir()) {
            setSystemProperty("jboss.server.config.dir", getServer().getStandaloneConfigurationDir());
        }
        final String[] systemPackages = {"org.jboss.logmanager"};
        standaloneServer = EmbeddedProcessFactory.createStandaloneServer(getServer().getBaseDir().toString(), null, systemPackages, cmds.toArray(new String[cmds.size()]));
        try {
            standaloneServer.start();
        } catch (EmbeddedProcessStartException e) {
            throw new ManagementOperationException(e);
        }
        return standaloneServer.getModelControllerClient();
    }

    @Override
    protected void stopConfiguration() {
        writeConfiguration();
        standaloneServer.stop();
        standaloneServer = null;
        resetProperties();
    }

    @Override
    public DeploymentResource getDeploymentResource(String resourceName) throws ManagementOperationException {
        return deploymentResources.getResource(resourceName);
    }

    @Override
    public List<DeploymentResource> getDeploymentResources() throws ManagementOperationException {
        return deploymentResources.getResources();
    }

    @Override
    public Set<String> getDeploymentResourceNames() throws ManagementOperationException {
        return deploymentResources.getResourceNames();
    }

    @Override
    public PathAddress getDeploymentResourcePathAddress(String resourceName) {
        return deploymentResources.getResourcePathAddress(resourceName);
    }

    @Override
    public void removeDeploymentResource(String resourceName) throws ManagementOperationException {
        deploymentResources.removeResource(resourceName);
    }

    public static class ConfigFileMigrationFactory implements ServerConfigurationMigration.ManageableConfigurationProvider {
        @Override
        public StandaloneServerConfiguration getManageableConfiguration(JBossServerConfiguration configurationPath, WildFlyServer10 server) {
            return new EmbeddedStandaloneServerConfiguration(configurationPath, server);
        }
    }

    private void setSystemProperty(final String name, final Object value) {
        if (value != null) {
            final String currentValue = WildFlySecurityManager.getPropertyPrivileged(name, null);
            WildFlySecurityManager.setPropertyPrivileged(name, value.toString());
            propertiesToReset.put(name, currentValue);
        }
    }

    private void resetProperties() {
        final Iterator<Map.Entry<String, String>> iterator = propertiesToReset.entrySet().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<String, String> entry = iterator.next();
            if (entry.getValue() == null) {
                WildFlySecurityManager.clearPropertyPrivileged(entry.getKey());
            } else {
                WildFlySecurityManager.setPropertyPrivileged(entry.getKey(), entry.getValue());
            }
            iterator.remove();
        }
    }
}
