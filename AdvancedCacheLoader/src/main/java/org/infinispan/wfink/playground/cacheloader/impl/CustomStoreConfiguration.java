package org.infinispan.wfink.playground.cacheloader.impl;

import org.infinispan.commons.configuration.BuiltBy;
import org.infinispan.commons.configuration.ConfigurationFor;
import org.infinispan.commons.configuration.attributes.Attribute;
import org.infinispan.commons.configuration.attributes.AttributeDefinition;
import org.infinispan.commons.configuration.attributes.AttributeSet;
import org.infinispan.commons.persistence.Store;
import org.infinispan.configuration.cache.AbstractStoreConfiguration;
import org.infinispan.configuration.cache.AsyncStoreConfiguration;
import org.infinispan.configuration.cache.SingletonStoreConfiguration;

@Store(shared = true)
@BuiltBy(CustomStoreConfigurationBuilder.class)
@ConfigurationFor(CustomAdvancedCacheLoader.class)
public class CustomStoreConfiguration extends AbstractStoreConfiguration {

  static final AttributeDefinition<String> DATASOURCE = AttributeDefinition.builder("datasource", null, String.class).immutable().build();
  static final AttributeDefinition<String> TABLE_NAME = AttributeDefinition.builder("table", null, String.class).immutable().build();
  static final AttributeDefinition<String> CATALOG = AttributeDefinition.builder("catalog", null, String.class).immutable().build();

  private final Attribute<String> datasource;
  private final Attribute<String> table;
  private final Attribute<String> catalog;

  public static AttributeSet attributeDefinitionSet() {
    return new AttributeSet(CustomStoreConfiguration.class, AbstractStoreConfiguration.attributeDefinitionSet(), DATASOURCE, TABLE_NAME, CATALOG);
  }

  public CustomStoreConfiguration(AttributeSet attributes, AsyncStoreConfiguration async, SingletonStoreConfiguration singletonStore) {
    super(attributes, async, singletonStore);
    datasource = attributes.attribute(DATASOURCE);
    table = attributes.attribute(TABLE_NAME);
    catalog = attributes.attribute(CATALOG);
  }

  public String datasource() {
    return datasource.get();
  }

  public String table() {
    return table.get();
  }

  public String catalog() {
    return catalog.get();
  }
}
