package org.infinispan.wfink.playground.cacheloader.impl;

import static org.infinispan.wfink.playground.cacheloader.impl.CustomStoreConfiguration.CATALOG;
import static org.infinispan.wfink.playground.cacheloader.impl.CustomStoreConfiguration.DATASOURCE;
import static org.infinispan.wfink.playground.cacheloader.impl.CustomStoreConfiguration.TABLE_NAME;

import org.infinispan.configuration.cache.AbstractStoreConfigurationBuilder;
import org.infinispan.configuration.cache.PersistenceConfigurationBuilder;

public class CustomStoreConfigurationBuilder extends AbstractStoreConfigurationBuilder<CustomStoreConfiguration, CustomStoreConfigurationBuilder>{

  public CustomStoreConfigurationBuilder(PersistenceConfigurationBuilder builder) {
    super(builder, CustomStoreConfiguration.attributeDefinitionSet());
  }

  public CustomStoreConfigurationBuilder datasource(String datasource) {
    attributes.attribute(DATASOURCE).set(datasource);
    return this;
  }

  public CustomStoreConfigurationBuilder table(String tableName) {
    attributes.attribute(TABLE_NAME).set(tableName);
    return this;
  }

  public CustomStoreConfigurationBuilder catalog(String catalog) {
    attributes.attribute(CATALOG).set(catalog);
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
