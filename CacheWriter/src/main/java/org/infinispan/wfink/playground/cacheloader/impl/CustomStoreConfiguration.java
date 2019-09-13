package org.infinispan.wfink.playground.cacheloader.impl;

import org.infinispan.commons.configuration.BuiltBy;
import org.infinispan.commons.configuration.ConfigurationFor;
import org.infinispan.commons.configuration.attributes.Attribute;
import org.infinispan.commons.configuration.attributes.AttributeDefinition;
import org.infinispan.commons.configuration.attributes.AttributeSet;
import org.infinispan.configuration.cache.AbstractStoreConfiguration;
import org.infinispan.configuration.cache.AsyncStoreConfiguration;
import org.infinispan.configuration.cache.SingletonStoreConfiguration;

/**
 * Simple configuration for the customer store. The attributes will link the datasource JNDI name, database selection (if needed) and table name (if not Person)
 *
 * @author <a href="mailto:WolfDieter.Fink@gmail.com">Wolf-Dieter Fink</a>
 *
 */
@BuiltBy(CustomStoreConfigurationBuilder.class)
@ConfigurationFor(CustomCacheWriter.class)
public class CustomStoreConfiguration extends AbstractStoreConfiguration {

  static final AttributeDefinition<String> DATASOURCE = AttributeDefinition.builder("datasource", null, String.class).immutable().build();
  static final AttributeDefinition<String> DATABASE = AttributeDefinition.builder("database", null, String.class).immutable().build();
  static final AttributeDefinition<String> TABLE_NAME = AttributeDefinition.builder("table", null, String.class).immutable().build();

  private final Attribute<String> datasource;
  private final Attribute<String> database;
  private final Attribute<String> table;

  public static AttributeSet attributeDefinitionSet() {
    return new AttributeSet(CustomStoreConfiguration.class, AbstractStoreConfiguration.attributeDefinitionSet(), DATASOURCE, DATABASE, TABLE_NAME);
  }

  public CustomStoreConfiguration(AttributeSet attributes, AsyncStoreConfiguration async, SingletonStoreConfiguration singletonStore) {
    super(attributes, async, singletonStore);
    datasource = attributes.attribute(DATASOURCE);
    database = attributes.attribute(DATABASE);
    table = attributes.attribute(TABLE_NAME);
  }

  public String datasource() {
    return datasource.get();
  }

  public String database() {
    return database.get();
  }

  public String table() {
    return table.get();
  }
}

