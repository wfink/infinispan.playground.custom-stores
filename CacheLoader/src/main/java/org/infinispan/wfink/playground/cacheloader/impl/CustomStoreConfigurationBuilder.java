package org.infinispan.wfink.playground.cacheloader.impl;

import static org.infinispan.wfink.playground.cacheloader.impl.CustomStoreConfiguration.AGE;
import static org.infinispan.wfink.playground.cacheloader.impl.CustomStoreConfiguration.NAME;

import org.infinispan.configuration.cache.AbstractStoreConfigurationBuilder;
import org.infinispan.configuration.cache.PersistenceConfigurationBuilder;

/**
 * Simple configuration for the customer store. It will simply use the given attributes name and age to return it as 'persisted' for every ID.
 * 
 * @author <a href="mailto:WolfDieter.Fink@gmail.com">Wolf-Dieter Fink</a>
 *
 */
public class CustomStoreConfigurationBuilder extends AbstractStoreConfigurationBuilder<CustomStoreConfiguration, CustomStoreConfigurationBuilder>{

  public CustomStoreConfigurationBuilder(
      PersistenceConfigurationBuilder builder) {
    super(builder, CustomStoreConfiguration.attributeDefinitionSet());
  }

  public CustomStoreConfigurationBuilder name(String name) {
    attributes.attribute(NAME).set(name);
    return this;
  }

  public CustomStoreConfigurationBuilder age(int age) {
    attributes.attribute(AGE).set(age);
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
