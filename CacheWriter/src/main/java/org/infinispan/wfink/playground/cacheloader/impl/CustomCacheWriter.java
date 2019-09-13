package org.infinispan.wfink.playground.cacheloader.impl;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.naming.InitialContext;
import javax.sql.DataSource;

import org.infinispan.AdvancedCache;
import org.infinispan.commons.CacheException;
import org.infinispan.commons.configuration.ConfiguredBy;
import org.infinispan.commons.io.ByteBufferFactory;
import org.infinispan.commons.persistence.Store;
import org.infinispan.encoding.DataConversion;
import org.infinispan.marshall.core.MarshalledEntry;
import org.infinispan.marshall.core.MarshalledEntryFactory;
import org.infinispan.metadata.InternalMetadata;
import org.infinispan.persistence.spi.CacheLoader;
import org.infinispan.persistence.spi.CacheWriter;
import org.infinispan.persistence.spi.InitializationContext;
import org.infinispan.persistence.spi.PersistenceException;
import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.ProtobufUtil;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.wfink.playground.cacheloader.domain.Person;
import org.infinispan.wfink.playground.cacheloader.marshaller.PersonMarshaller;
import org.jboss.logging.Logger;

/**
 * Simple implementation for a CacheLoader and CacheWriter.<br/>
 * This will load entries from the persistence and support simple get and put. size(), list() are not using the persistence, as result only the entries in memory are considered. preload=true within the configuration will throw a
 * Configuration exception during startup. To have full support of such batches the AdvancedCacheLoader is needed.<br/>
 *
 * This implementation will support caches which are configured with <memory> as <object> <binary> and <off-heap>
 *
 * If there is no database configured any access will simply log a warning.
 *
 * @author <a href="mailto:WolfDieter.Fink@gmail.com">Wolf-Dieter Fink</a>
 */
@Store
@ConfiguredBy(CustomStoreConfiguration.class)
public class CustomCacheWriter<K, V> implements CacheWriter<K, V>, CacheLoader<K, V> {
  private static final Logger log = Logger.getLogger(CustomCacheWriter.class);
  private static final String PROTOBUF_DEFINITION_RESOURCE = "person.proto";

  ByteBufferFactory byteBufferFactory;
  MarshalledEntryFactory<K, V> marshalledEntryFactory;
  CustomStoreConfiguration config;
  private DataConversion keyDataConversion;
  private DataConversion valueDataConversion;

  SerializationContext ctx;
  boolean hasDatabase = false;
  String table = Person.class.getSimpleName();
  DataSource datasource;
  Connection connection;

  @Override
  public void init(InitializationContext ctx) {
    /*
     * This method will be invoked by the PersistenceManager during initialization. The InitializationContext
     * contains:
     * - this CacheWriter's configuration
     * - the cache to which this loader is applied. Your loader might want to use the cache's name to construct
     *   cache-specific identifiers
     * - the StreamingMarshaller that needs to be used to marshall/unmarshall the entries
     * - a TimeService which the loader can use to determine expired entries
     * - a ByteBufferFactory which needs to be used to construct ByteBuffers
     * - a MarshalledEntryFactory which needs to be used to construct entries from the data retrieved by the loader
     */
    log.debug("Initialize CacheWriter");
    config = ctx.getConfiguration();
    byteBufferFactory = ctx.getByteBufferFactory();
    marshalledEntryFactory = ctx.getMarshalledEntryFactory();
    AdvancedCache<K, V> cache = ctx.getCache().getAdvancedCache();
    keyDataConversion = cache.getKeyDataConversion();
    valueDataConversion = cache.getValueDataConversion();
  }

  @Override
  public void start() {
    /*
     * This method will be invoked by the PersistenceManager to start the CacheWriter. At this stage configuration
     * is complete and the loader can perform operations such as opening a connection to the external storage,
     * initialize internal data structures, etc.
     */
    log.debug("Start CacheWriter");
    ctx = ProtobufUtil.newSerializationContext();

    try {
      log.debugf("register proto file %s", PROTOBUF_DEFINITION_RESOURCE);
      ctx.registerProtoFiles(FileDescriptorSource.fromResources(CustomCacheWriter.class.getClassLoader(), PROTOBUF_DEFINITION_RESOURCE));
    } catch (IOException e) {
      throw new CacheException(e);
    }

    ctx.registerMarshaller(new PersonMarshaller());

    if (config.datasource() != null) {
      this.hasDatabase = true;
      String dbJndiName = config.datasource();
      if (config.table() != null) {
        // use the configured table name instead of class name
        this.table = config.table();
      }

      log.infof("CacheWriter will use DataBase with JNDI name %s and table %s", dbJndiName, table);

      try {
        InitialContext ctx = new InitialContext();
        datasource = (DataSource) ctx.lookup(dbJndiName);
        connection = datasource.getConnection();
        if (config.database() != null) {
          connection.setCatalog(config.database());
        }
      } catch (Exception e) {
        throw new PersistenceException("Unable to connect to the configured datasource " + dbJndiName, e);
      }
    }
  }

  @Override
  public void stop() {
    /*
     * This method will be invoked by the PersistenceManager to stop the CacheWriter. The CacheWriter should close any
     * connections to the external storage and perform any needed cleanup.
     */
    log.debug("Stop CacheWriter");
    ctx = null;
  }

  @Override
  public boolean delete(Object key) {
    /*
     * The CacheWriter should remove from the external storage the entry identified by the specified key.
     *
     * Note that keys will be in the cache's native format, which means that if the cache is being used by a remoting protocol
     * such as HotRod or REST and compatibility mode has not been enabled, then they will be encoded in a byte[].
     */
    final boolean deleted;

    // The key will be in serialized form, so we need to deserialize it to get the key
    Long id;
    try {
      id = ProtobufUtil.fromWrappedByteArray(ctx, (byte[]) keyDataConversion.fromStorage(key));
    } catch (IOException e) {
      throw new CacheException(e);
    }

    log.debugf("delete person with id %d", id);
    // This would be where the code to talk to a database etc to retrieve the user object instance
    if (hasDatabase) {
      try {
        try (Statement st = connection.createStatement()) {
          String sql = "DELETE FROM " + table + " WHERE ID=" + id;
          log.tracef("Run SQL %s", sql);
          int rc = st.executeUpdate(sql);
          if (rc == 0) {
            log.tracef("No person found with id %d", id);
            deleted = false;
          } else if (rc == 1) {
            log.tracef("person with id %d deleted", id);
            deleted = true;
          } else {
            throw new PersistenceException("SQL:" + sql + " deleted more results than expected (" + rc + ") Possible data inconsistence!");
          }
        }
      } catch (SQLException e) {
        throw new PersistenceException("Unable to load person with id " + id, e);
      }
    } else {
      log.warn("No datasource configured");
      deleted = false;
    }
    return deleted;
  }

  @Override
  public void write(MarshalledEntry<? extends K, ? extends V> marshalledEntry) {
    /*
     * The CacheWriter should write the specified entry to the external storage.
     *
     * The PersistenceManager uses MarshalledEntry as the default format so that CacheWriters can efficiently store data coming
     * from a remote node, thus avoiding any additional transformation steps.
     *
     * Note that keys and values will be in the cache's native format, which means that if the cache is being used by a remoting protocol
     * such as HotRod or REST and compatibility mode has not been enabled, then they will be encoded in a byte[].
     */
    // The key will be in serialized form, so we need to deserialize it to get the key
    Long id;
    try {
      id = ProtobufUtil.fromWrappedByteArray(ctx, (byte[]) keyDataConversion.fromStorage(marshalledEntry.getKey()));
    } catch (IOException e) {
      throw new CacheException(e);
    }
    log.tracef("Person ID to write is %d", id);

    // byte[] valueBytes = ((WrappedBytes) marshalledEntry.getValue()).getBytes();
    Person person;
    try {
      person = ProtobufUtil.fromWrappedByteArray(ctx, (byte[]) valueDataConversion.fromStorage(marshalledEntry.getValue()));
    } catch (IOException e) {
      throw new CacheException(e);
    }
    log.tracef("Person to write is %s", person);

    // byte[] metaBytes = ((WrappedBytes) marshalledEntry.getMetadata()).getBytes();
    InternalMetadata meta = marshalledEntry.getMetadata();
    log.tracef("Person META is %s", meta);
    if (meta != null && (meta.expiryTime() > 0 || meta.lastUsed() > 0)) {
      log.warnf("Writer will not handle META %s", meta);
    }

    if (!id.equals(person.getId())) {
      throw new PersistenceException("KEY " + id + " does not match the person ID " + person);
    }

    log.debugf("write person %s", person);
    // This would be where the code to talk to a database etc to retrieve the user object instance
    if (hasDatabase) {
      try {
        final String sql;
        if (checkIdExists(id)) {
          sql = "UPDATE " + table + " SET Name='" + person.getName() + "', Age=" + person.getAge() + " WHERE ID=" + person.getId();
        } else {
          sql = "INSERT INTO " + table + " (ID, Name, Age) VALUES(" + person.getId() + ",'" + person.getName() + "'," + person.getAge() + ")";
        }
        log.tracef("Run SQL %s", sql);
        try (Statement st = connection.createStatement()) {
          int rc = st.executeUpdate(sql);
          if (rc != 1) {
            throw new PersistenceException("Inconsistent result update persistence (" + sql + ") rows affected : " + rc);
          }
        }
      } catch (SQLException e) {
        throw new PersistenceException("Unable to store " + person, e);
      }
    } else {
      log.warn("No datasource configured, entry not persisted");
    }
  }

  @Override
  public MarshalledEntry<K, V> load(Object key) {
    /*
     * Fetches an entry from the storage using the specified key. The CacheLoader should retrieve from the external storage all of the data that is needed to reconstruct the entry in memory, i.e. the value and optionally the metadata. This
     * method needs to return a MarshalledEntry which can be constructed as follows:
     *
     * ctx.getMarshalledEntryFactory().newMarshalledEntry(key, value, metadata);
     *
     * If the entry does not exist or has expired, this method should return null. If an error occurs while retrieving data from the external storage, this method should throw a PersistenceException
     *
     *
     *
     *
     * Note that keys and values will be in the cache's native format, which means that if the cache is being used by a remoting protocol such as HotRod or REST and compatibility mode has not been enabled, then they will be encoded in a
     * byte[]. If the loader needs to have knowledge of the key/value data beyond their binary representation, then it needs access to the key's and value's classes and the marshaller used to encode them.
     */
    // The key will be in serialized form, so we need to deserialize it to get the key
    Long id;
    try {
      id = ProtobufUtil.fromWrappedByteArray(ctx, (byte[]) keyDataConversion.fromStorage(key));
    } catch (IOException e) {
      throw new CacheException(e);
    }

    log.debugf("load person with id %d", id);
    // This would be where the code to talk to a database etc to retrieve the user object instance
    Person loadedPerson = null;
    if (hasDatabase) {
      try {
        try (Statement st = connection.createStatement()) {
          String sql = "SELECT Name, Age FROM " + table + " WHERE ID=" + id;
          log.tracef("Run SQL %s", sql);
          try (ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
              loadedPerson = new Person(id, rs.getString(1), rs.getInt(2));
              if (rs.next()) {
                throw new PersistenceException("Query SQL " + sql + " returned unecpected many results!");
              }
            } else {
              log.tracef("No Person found with id %d", id);
            }
          }
        }
      } catch (SQLException e) {
        throw new PersistenceException("Unable to load person with id " + id, e);
      }
    } else {
      log.warn("No datasource configured, returning a dummy person");
      loadedPerson = new Person(id, "test", 9999);
    }

    if(loadedPerson != null) {
      byte[] valueBytes;
      try {
        valueBytes = ProtobufUtil.toWrappedByteArray(ctx, loadedPerson);
      } catch (IOException e) {
        throw new CacheException(e);
      }
      return marshalledEntryFactory.newMarshalledEntry(key, valueDataConversion.toStorage(valueBytes), null);
    } else {
      return null;
    }
  }

  @Override
  public boolean contains(Object key) {
    /*
     * This method will be invoked by the PersistenceManager to determine if the loader contains the specified key. The implementation should be as fast as possible, e.g. it should strive to transfer the least amount of data possible from
     * the external storage to perform the check. Also, if possible, make sure the field is indexed on the external storage so that its existence can be determined as quickly as possible.
     *
     * Note that keys will be in the cache's native format, which means that if the cache is being used by a remoting protocol such as HotRod or REST and compatibility mode has not been enabled, then they will be encoded in a byte[].
     */
    boolean contains = false;
    // The key will be in serialized form, so we need to deserialize it to get the key
    Long id;
    try {
      id = ProtobufUtil.fromWrappedByteArray(ctx, (byte[]) keyDataConversion.fromStorage(key));
    } catch (IOException e) {
      throw new CacheException(e);
    }

    log.tracef("check person with id %d", id);
    if (hasDatabase) {
      contains = checkIdExists(id);
    } else {
      log.warnf("No datasource configured, returning false for id %d", id);
    }
    return contains;
  }

  private boolean checkIdExists(Long id) {
    boolean contains = false;
    try {
      try (Statement st = connection.createStatement()) {
        String sql = "SELECT 'X' FROM " + table + " WHERE ID=" + id;
        log.tracef("Run SQL %s", sql);
        try (ResultSet rs = st.executeQuery(sql)) {
          if (rs.next()) {
            contains = true;
            log.tracef("Person found with id %d", id);
            if (rs.next()) {
              throw new PersistenceException("Query SQL " + sql + " returned unecpected many results!");
            }
          } else {
            log.tracef("No Person found with id %d", id);
          }
        }
      }
    } catch (SQLException e) {
      throw new PersistenceException("Unable to load person with id " + id, e);
    }
    return contains;
  }

  /**
   * TODO why duplicate in Loader Writer and is it necessary to overwrite???
   */
  @Override
  public boolean isAvailable() {
    return true;
  }
}
