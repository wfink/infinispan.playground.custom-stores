package org.infinispan.wfink.playground.cacheloader.impl;

import java.io.IOException;

import org.infinispan.commons.CacheException;
import org.infinispan.commons.configuration.ConfiguredBy;
import org.infinispan.commons.io.ByteBufferFactory;
import org.infinispan.commons.marshall.WrappedBytes;
import org.infinispan.marshall.core.MarshalledEntry;
import org.infinispan.marshall.core.MarshalledEntryFactory;
import org.infinispan.persistence.spi.CacheLoader;
import org.infinispan.persistence.spi.InitializationContext;
import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.ProtobufUtil;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.wfink.playground.cacheloader.domain.Person;
import org.infinispan.wfink.playground.cacheloader.marshaller.PersonMarshaller;
import org.jboss.logging.Logger;

/**
 * Simple implementation for a CacheLoader.<br/>
 * This will load entries from the persistence and support simple get. If the persistence should support size() list() and preload the AdvancedCacheLoader is needed. Also note that this implementation will only support caches which are
 * configured as <memory><object>.
 *
 * @author <a href="mailto:WolfDieter.Fink@gmail.com">Wolf-Dieter Fink</a>
 */
@ConfiguredBy(CustomStoreConfiguration.class)
public class CustomCacheLoader<K,V> implements CacheLoader<K, V> {
  private static final Logger log = Logger.getLogger(CustomCacheLoader.class);
  ByteBufferFactory byteBufferFactory;
  MarshalledEntryFactory<K, V> marshalledEntryFactory;
  CustomStoreConfiguration config;

  SerializationContext ctx;

  private static final String PROTOBUF_DEFINITION_RESOURCE = "person.proto";

  @Override
  public void init(InitializationContext ctx) {
    /*
     * This method will be invoked by the PersistenceManager during initialization. The InitializationContext
     * contains:
     * - this CacheLoader's configuration
     * - the cache to which this loader is applied. Your loader might want to use the cache's name to construct
     *   cache-specific identifiers
     * - the StreamingMarshaller that needs to be used to marshall/unmarshall the entries
     * - a TimeService which the loader can use to determine expired entries
     * - a ByteBufferFactory which needs to be used to construct ByteBuffers
     * - a MarshalledEntryFactory which needs to be used to construct entries from the data retrieved by the loader
     */
    log.debug("Initialize CacheLoader");
    config = ctx.getConfiguration();
    byteBufferFactory = ctx.getByteBufferFactory();
    marshalledEntryFactory = ctx.getMarshalledEntryFactory();
  }

  @Override
  public void start() {
    /*
     * This method will be invoked by the PersistenceManager to start the CacheLoader. At this stage configuration
     * is complete and the loader can perform operations such as opening a connection to the external storage,
     * initialize internal data structures, etc.
     */
    log.debug("Start CacheLoader");
    ctx = ProtobufUtil.newSerializationContext();

    try {
      log.debugf("register proto file %s", PROTOBUF_DEFINITION_RESOURCE);
      ctx.registerProtoFiles(FileDescriptorSource.fromResources(CustomCacheLoader.class.getClassLoader(), PROTOBUF_DEFINITION_RESOURCE));
    } catch (IOException e) {
      throw new CacheException(e);
    }

    ctx.registerMarshaller(new PersonMarshaller());
  }

  @Override
  public void stop() {
    /*
     * This method will be invoked by the PersistenceManager to stop the CacheLoader. The CacheLoader should close any
     * connections to the external storage and perform any needed cleanup.
     */
    log.debug("Stop CacheLoader");
    ctx = null;
  }

  @Override
  public boolean contains(Object key) {
    /*
     * This method will be invoked by the PersistenceManager to determine if the loader contains the specified key.
     * The implementation should be as fast as possible, e.g. it should strive to transfer the least amount of data possible
     * from the external storage to perform the check. Also, if possible, make sure the field is indexed on the external storage
     * so that its existence can be determined as quickly as possible.
     *
     * Note that keys will be in the cache's native format, which means that if the cache is being used by a remoting protocol
     * such as HotRod or REST and compatibility mode has not been enabled, then they will be encoded in a byte[].
     */
    // as this implementation always return the configured Person no matter which ID it will always return true
    return true;
  }

  @Override
  public MarshalledEntry<K, V> load(Object key) {
    /*
     * Fetches an entry from the storage using the specified key. The CacheLoader should retrieve from the external storage all
     * of the data that is needed to reconstruct the entry in memory, i.e. the value and optionally the metadata. This method
     * needs to return a MarshalledEntry which can be constructed as follows:
     *
     * ctx.getMarshalledEntryFactory().newMarshalledEntry(key, value, metadata);
     *
     * If the entry does not exist or has expired, this method should return null.
     * If an error occurs while retrieving data from the external storage, this method should throw a PersistenceException
     *
     *

     *
     * Note that keys and values will be in the cache's native format, which means that if the cache is being used by a remoting protocol
     * such as HotRod or REST and compatibility mode has not been enabled, then they will be encoded in a byte[].
     * If the loader needs to have knowledge of the key/value data beyond their binary representation, then it needs access to the key's and value's
     * classes and the marshaller used to encode them.
     */
    byte[] keyBytes = ((WrappedBytes) key).getBytes();
    // The key will be in serialized form, so we need to deserialize it to store in the Person
    Long id;
    try {
      id = ProtobufUtil.fromWrappedByteArray(ctx, keyBytes);
    } catch (IOException e) {
      throw new CacheException(e);
    }
    // This would be where the code to talk to a database etc to retrieve the user object instance
    Person loadedPerson = new Person(id, config.name(), config.age());

    byte[] valueBytes;
    try {
      valueBytes = ProtobufUtil.toWrappedByteArray(ctx, loadedPerson);
    } catch (IOException e) {
      throw new CacheException(e);
    }
    return marshalledEntryFactory.newMarshalledEntry(keyBytes, valueBytes, null);
  }

}
