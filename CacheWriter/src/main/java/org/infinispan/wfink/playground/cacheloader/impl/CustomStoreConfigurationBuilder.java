package org.infinispan.wfink.playground.cacheloader.impl;

import static org.infinispan.wfink.playground.cacheloader.impl.CustomStoreConfiguration.DATABASE;
import static org.infinispan.wfink.playground.cacheloader.impl.CustomStoreConfiguration.DATASOURCE;
import static org.infinispan.wfink.playground.cacheloader.impl.CustomStoreConfiguration.TABLE_NAME;

import org.infinispan.configuration.cache.AbstractStoreConfigurationBuilder;
import org.infinispan.configuration.cache.PersistenceConfigurationBuilder;

/**
 * Simple configuration for the customer store. The attributes will link the datasource JNDI name, database selection (if needed) and table name (if not Person)
 *
 * @author <a href="mailto:WolfDieter.Fink@gmail.com">Wolf-Dieter Fink</a>
 *
 */
public class CustomStoreConfigurationBuilder extends AbstractStoreConfigurationBuilder<CustomStoreConfiguration, CustomStoreConfigurationBuilder>{

  public CustomStoreConfigurationBuilder(PersistenceConfigurationBuilder builder) {
    super(builder, CustomStoreConfiguration.attributeDefinitionSet());
  }

  public CustomStoreConfigurationBuilder datasource(String datasource) {
    attributes.attribute(DATASOURCE).set(datasource);
    return this;
  }

  public CustomStoreConfigurationBuilder database(String database) {
    attributes.attribute(DATABASE).set(database);
    return this;
  }

  public CustomStoreConfigurationBuilder table(String tableName) {
    attributes.attribute(TABLE_NAME).set(tableName);
    return this;
  }

  @Override
  public CustomStoreConfiguration create() {
    return new CustomStoreConfiguration(attributes.protect(), async.create(), singletonStore.create());
  }

  @Override
  public CustomStoreConfigurationBuilder self() {
    return this;
  }

}
