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

package org.jboss.migration.wfly10.config.task.update;

import org.jboss.migration.core.JBossServer;
import org.jboss.migration.core.ServerPath;
import org.jboss.migration.wfly10.WildFly10Server;
import org.jboss.migration.wfly10.config.task.HostConfigurationMigration;
import org.jboss.migration.wfly10.config.task.HostConfigurationsMigration;

import java.util.Collection;

/**
 * @author emmartins
 */
class HostConfigurationsUpdate<S extends JBossServer<S>> extends HostConfigurationsMigration<S, ServerPath<S>> {

    HostConfigurationsUpdate(HostConfigurationMigration<ServerPath<S>> configurationMigration) {
        super(new SourceHostConfigurations<S>(), configurationMigration);
    }

    private static class SourceHostConfigurations<S extends JBossServer<S>> implements SourceConfigurations<S, ServerPath<S>> {
        @Override
        public Collection<ServerPath<S>> getConfigurations(S source, WildFly10Server target) {
            return source.getDomainHostConfigs();
        }
    }
}