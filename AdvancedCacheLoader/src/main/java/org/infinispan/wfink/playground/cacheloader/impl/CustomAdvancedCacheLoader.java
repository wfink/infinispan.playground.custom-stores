package org.infinispan.wfink.playground.cacheloader.impl;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.Executor;
import java.util.function.Predicate;

import javax.naming.InitialContext;
import javax.sql.DataSource;

import org.infinispan.commons.CacheException;
import org.infinispan.commons.configuration.ConfiguredBy;
import org.infinispan.commons.io.ByteBufferFactory;
import org.infinispan.commons.marshall.WrappedByteArray;
import org.infinispan.commons.persistence.Store;
import org.infinispan.commons.util.AbstractIterator;
import org.infinispan.filter.KeyFilter;
import org.infinispan.marshall.core.MarshalledEntry;
import org.infinispan.marshall.core.MarshalledEntryFactory;
import org.infinispan.persistence.spi.AdvancedCacheLoader;
import org.infinispan.persistence.spi.InitializationContext;
import org.infinispan.persistence.spi.PersistenceException;
import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.ProtobufUtil;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.wfink.playground.cacheloader.domain.Person;
import org.infinispan.wfink.playground.cacheloader.marshaller.PersonMarshaller;
import org.jboss.logging.Logger;
import org.reactivestreams.Publisher;

import io.reactivex.Flowable;
import io.reactivex.functions.Function;

@Store
@ConfiguredBy(CustomStoreConfiguration.class)
public class CustomAdvancedCacheLoader<K,V> implements AdvancedCacheLoader<K, V> {
  private static final Logger log = Logger.getLogger(CustomAdvancedCacheLoader.class);
  private static final String PROTOBUF_DEFINITION_RESOURCE = "person.proto";

  ByteBufferFactory byteBufferFactory;
  MarshalledEntryFactory<K, V> marshalledEntryFactory;
  CustomStoreConfiguration config;

  InitializationContext ctx;
  SerializationContext serializationCtx;
  boolean hasDatabase = false;
  String table = Person.class.getSimpleName();
  DataSource datasource;
  String dbJndiName;

  /**
   * TODO: do I need DataConversion for key/value and why?
   */

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
    log.debug("Initialize AdvancedCacheLoader");
    config = ctx.getConfiguration();
    byteBufferFactory = ctx.getByteBufferFactory();
    marshalledEntryFactory = ctx.getMarshalledEntryFactory();
    this.ctx = ctx;
  }

  @Override
  public void start() {
    /*
     * This method will be invoked by the PersistenceManager to start the CacheLoader. At this stage configuration
     * is complete and the loader can perform operations such as opening a connection to the external storage,
     * initialize internal data structures, etc.
     */
    log.debug("Start AdvancedCacheLoader");
    serializationCtx = ProtobufUtil.newSerializationContext();

    try {
      log.debugf("register proto file %s", PROTOBUF_DEFINITION_RESOURCE);
      serializationCtx.registerProtoFiles(FileDescriptorSource.fromResources(CustomAdvancedCacheLoader.class.getClassLoader(), PROTOBUF_DEFINITION_RESOURCE));
    } catch (IOException e) {
      throw new CacheException(e);
    }

    serializationCtx.registerMarshaller(new PersonMarshaller());

    if (config.datasource() != null) {
      this.hasDatabase = true;
      dbJndiName = config.datasource();
      if (config.table() != null) {
        // use the configured table name instead of class name
        this.table = config.table();
      }

      log.infof("CacheLoader will use DataBase with JNDI name %s and table %s", dbJndiName, table);

      try {
        InitialContext ctx = new InitialContext();
        datasource = (DataSource) ctx.lookup(dbJndiName);
      } catch (Exception e) {
        throw new PersistenceException("Unable to connect to the configured datasource " + dbJndiName, e);
      }
    }
  }

  private Connection getConnection() {
    try {
      Connection connection;

      connection = datasource.getConnection();
      connection.setCatalog(config.catalog());
      return connection;
    } catch (SQLException e) {
      throw new PersistenceException("Unable to connect to the configured datasource " + dbJndiName, e);
    }
  }

  @Override
  public void stop() {
    /*
     * This method will be invoked by the PersistenceManager to stop the CacheLoader. The CacheLoader should close any
     * connections to the external storage and perform any needed cleanup.
     */
    log.debug("Stop AdvancedCacheLoader");
    serializationCtx = null;
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
    boolean contains = false;
    byte[] keyBytes = ((WrappedByteArray) key).getBytes();
    // The key will be in serialized form, so we need to deserialize it to get the key
    Long id;
    try {
      id = ProtobufUtil.fromWrappedByteArray(serializationCtx, keyBytes);
    } catch (IOException e) {
      throw new CacheException(e);
    }

    log.tracef("check person with id %d", id);
    if (hasDatabase) {
      try {
        Connection con = getConnection();
        try (Statement st = con.createStatement()) {
          String sql = "SELECT Name, Age FROM " + table + " WHERE ID=" + id;
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
        CustomAdvancedCacheLoader.safeClose(con);
      } catch (SQLException e) {
        throw new PersistenceException("Unable to load person with id " + id, e);
      }
    } else {
      log.warnf("No datasource configured, returning false for id %d", id);
    }
    return contains;
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
    byte[] keyBytes = ((WrappedByteArray) key).getBytes();
    // The key will be in serialized form, so we need to deserialize it to get the key
    Long id;
    try {
      id = ProtobufUtil.fromWrappedByteArray(serializationCtx, keyBytes);
    } catch (IOException e) {
      throw new CacheException(e);
    }

    log.debugf("load person with id %d", id);
    // This would be where the code to talk to a database etc to retrieve the user object instance
    Person loadedPerson = null;
    if (hasDatabase) {
      try {
        Connection con = getConnection();
        try (Statement st = con.createStatement()) {
          String sql = "SELECT Name, Age FROM " + table + " WHERE ID=" + id;
          log.tracef("Run SQL %s", sql);
          try (ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
              int intAge = rs.getInt(2);
              loadedPerson = new Person(id, rs.getString(1), intAge == 0 ? null : new Integer(intAge));
              if (rs.next()) {
                throw new PersistenceException("Query SQL " + sql + " returned unecpected many results!");
              }
            } else {
              log.tracef("No Person found with id %d", id);
            }
          }
        }
        CustomAdvancedCacheLoader.safeClose(con);
      } catch (SQLException e) {
        throw new PersistenceException("Unable to load person with id " + id, e);
      }
    } else {
      log.warn("No datasource configured, returning a dummy person");
      loadedPerson = new Person(id, "test", 9999);
    }

    if (loadedPerson != null) {
      byte[] valueBytes;
      try {
        valueBytes = ProtobufUtil.toWrappedByteArray(serializationCtx, loadedPerson);
      } catch (IOException e) {
        throw new CacheException(e);
      }
      return marshalledEntryFactory.newMarshalledEntry(keyBytes, valueBytes, null);
    } else {
      return null;
    }

  }

  @Override
  public void process(KeyFilter<? super K> filter, org.infinispan.persistence.spi.AdvancedCacheLoader.CacheLoaderTask<K, V> task, Executor executor, boolean fetchValue, boolean fetchMetadata) {
    /*
     * The PersistenceManager will invoke this method to iterate in parallel over the entries in the storage using
     * the threads from the <b>executor</b> pool. For each entry, invoke the task.processEntry(MarshalledEntry, TaskContext)
     * method. Before passing an entry to the callback task, the entry should be validated against the <b>filter</b>, e.g.:
     *
     * if (filter.accept(key)) {
     *     task.processEntry(MarshalledEntry, TaskContext)
     * }
     *
     * Implementors should build an {@link TaskContext} instance (implementation) that is fed to the {@link
     * CacheLoaderTask} on every invocation. The {@link CacheLoaderTask} might invoke {@link
     * org.infinispan.persistence.spi.AdvancedCacheLoader.TaskContext#stop()} at any time, so implementors of this method
     * should verify TaskContext's state for early termination of iteration. The method should only return once the
     * iteration is complete or as soon as possible in the case TaskContext.stop() is invoked.
     * The parameters are as follows:
     * - filter        to validate which entries should be feed into the task. Might be null.
     * - task          callback to be invoked in parallel for each stored entry that passes the filter check
     * - executor      an external thread pool to be used for parallel iteration
     * - fetchValue    whether or not to fetch the value from the persistent store. E.g. if the iteration is
     *                 intended only over the key set, no point fetching the values from the persistent store as
     *                 well
     * - fetchMetadata whether or not to fetch the metadata from the persistent store. E.g. if the iteration is
     *                 intended only over the key set, then no pint fetching the metadata from the persistent store
     *                 as well
     * This method should throw a PersistenceException in case of an error, e.g. communicating with the external storage
     */
    log.fatalf("process called filter %s task=%s fetchValue=%b fetchMeta=%b", filter, task, fetchValue, fetchMetadata);
  }

  @Override
  public Publisher<MarshalledEntry<K, V>> publishEntries(Predicate<? super K> filter, boolean fetchValue, boolean fetchMetadata) {
    log.fatalf("publicEntry called filter %s fetchValue=%b fetchMeta=%b", filter, fetchValue, fetchMetadata);
    if (fetchMetadata) {
      log.warn("Metadata cannot be retrieved as the ustomAdvancedCacheLoader Store is not implemented to get metadata.");
    }
    return publish(rs -> Flowable.fromIterable(() -> new ResultSetEntryIterator(rs, filter, fetchValue, fetchMetadata)));
  }

  private <P> Flowable<P> publish(Function<ResultSet, Flowable<P>> function) {
    return Flowable.using(() -> {
      String sql = "SELECT ID, Name, Age FROM " + table;
      log.tracef("Run SQL '%s'", sql);

      return new FlowableConnection(getConnection(), sql);
    }, fc -> {
      PreparedStatement ps = fc.statement;
      ResultSet rs = ps.executeQuery();
      return function.apply(rs).doOnComplete(() -> CustomAdvancedCacheLoader.safeClose(rs));
    }, FlowableConnection::close);
    // return null;
  }

  class FlowableConnection {
    final Connection connection;
    final PreparedStatement statement;

    FlowableConnection(Connection connection, String sql) throws SQLException {
      this.connection = connection;
      this.statement = connection.prepareStatement(sql);
    }

    void close() {
      CustomAdvancedCacheLoader.safeClose(connection);
    }
  }

  public static void safeClose(ResultSet rs) {
    if (rs != null) {
      try {
        rs.close();
      } catch (SQLException e) {
        log.fatal("Unexpected SQL failure!",e);
      }
    }
  }

  public static void safeClose(Connection connection) {
    if (connection != null) {
      try {
        connection.close();
      } catch (SQLException e) {
        log.fatal("Unexpected SQL failure!", e);
      }
    }
  }

  @Override
  public int size() {
    /*
     * The CacheLoader should efficiently compute the number of entries in the store
     */
    if (hasDatabase) {
      throw new UnsupportedOperationException("Database not implemented yet");
    } else {
      log.warn("No datasource configured, returning size=0");
      return 0;
    }
  }

  private class ResultSetEntryIterator extends AbstractIterator<MarshalledEntry<K, V>> {
    private final ResultSet rs;
    private final Predicate<? super K> filter;
    private final boolean fetchValue;
    private final boolean fetchMetadata;

    public ResultSetEntryIterator(ResultSet rs, Predicate<? super K> filter, boolean fetchValue, boolean fetchMetadata) {
      this.rs = rs;
      this.filter = filter;
      this.fetchValue = fetchValue;
      this.fetchMetadata = fetchMetadata;
    }

    @Override
    protected MarshalledEntry<K, V> getNext() {
      MarshalledEntry<K, V> entry = null;
      try {
        while (entry == null && rs.next()) {
          long id = rs.getLong(1);
          K key = (K) ProtobufUtil.toWrappedByteArray(serializationCtx, id);

          if (filter == null || filter.test(key)) {
            if (fetchValue) {
              String name = rs.getString(2);
              int ageInt = rs.getInt(3);
              Integer age = ageInt == 0 ? null : new Integer(ageInt);

              Person p = new Person(id, name, age);
              log.tracef("Read person %s", p);
              byte[] loadedPerson = ProtobufUtil.toWrappedByteArray(serializationCtx, p);
              entry = marshalledEntryFactory.newMarshalledEntry(key, loadedPerson, null);
            } else {
              log.tracef("Read person key %d", id);
              entry = marshalledEntryFactory.newMarshalledEntry(key, (Object) null, null);
            }
          }
        }
      } catch (SQLException | IOException e) {
        throw new CacheException(e);
      }
      return entry;
    }
  }

}
