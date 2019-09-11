package org.infinispan.wfink.playground.cacheloader.client.hotrod;

import java.io.Console;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.List;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.Search;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.client.hotrod.marshall.ProtoStreamMarshaller;
import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.query.dsl.Query;
import org.infinispan.query.dsl.QueryFactory;
import org.infinispan.query.remote.client.ProtobufMetadataManagerConstants;
import org.infinispan.wfink.playground.cacheloader.domain.Person;
import org.infinispan.wfink.playground.cacheloader.marshaller.PersonMarshaller;

/**
 * Simple interactive HotRod client with several options to test the Loader/Writer implementation and configuration.
 *
 * @author <a href="mailto:WolfDieter.Fink@gmail.com">Wolf-Dieter Fink</a>
 *
 */
public class PersonQueryHotRodClient {
  private static final String PROTOBUF_DEFINITION_PERSON = "/person.proto";
  private static final String msgEnterKey = "Enter person id: ";

  private final Console con = System.console();

  private RemoteCacheManager remoteCacheManager;
  private RemoteCache<Long, Person> personCache;

  public PersonQueryHotRodClient(String host, String port, String cacheName) {
    ConfigurationBuilder remoteBuilder = new ConfigurationBuilder();
    remoteBuilder.addServer().host(host).port(Integer.parseInt(port))
    .marshaller(new ProtoStreamMarshaller());  // The Protobuf based marshaller is required for query capabilities

    remoteCacheManager = new RemoteCacheManager(remoteBuilder.build());
    personCache = remoteCacheManager.getCache(cacheName);

    if (personCache == null) {
      throw new RuntimeException("Cache '" + cacheName + "' not found. Please make sure the server is properly configured");
    }

    registerSchemasAndMarshallers();
  }

  /**
   * Register the Protobuf schemas and marshallers with the client and then
   * register the schemas with the server too.
   */
  private void registerSchemasAndMarshallers() {
    // Register entity marshallers on the client side ProtoStreamMarshaller
    // instance associated with the remote cache manager.
    SerializationContext ctx = ProtoStreamMarshaller.getSerializationContext(remoteCacheManager);
    // register the necessary proto files
    try {
      ctx.registerProtoFiles(FileDescriptorSource.fromResources(PROTOBUF_DEFINITION_PERSON));
    } catch (Exception e) {
      throw new RuntimeException("Failed to read protobuf definition '" + PROTOBUF_DEFINITION_PERSON + "'", e);
    }
    ctx.registerMarshaller(new PersonMarshaller());


    // register the schemas with the server too
    final RemoteCache<String, String> protoMetadataCache = remoteCacheManager.getCache(ProtobufMetadataManagerConstants.PROTOBUF_METADATA_CACHE_NAME);

    protoMetadataCache.put(PROTOBUF_DEFINITION_PERSON, readResource(PROTOBUF_DEFINITION_PERSON));

    // check for definition error for the registered protobuf schemas
    String errors = protoMetadataCache.get(ProtobufMetadataManagerConstants.ERRORS_KEY_SUFFIX);
    if (errors != null) {
      throw new IllegalStateException("Some Protobuf schema files contain errors:\n" + errors);
    }
  }

  /**
   * Helper to read the protobuf file to String
   *
   * @param resourcePath
   * @return
   */
  private String readResource(String resourcePath) {
    try (InputStream is = Person.class.getClass().getResourceAsStream(resourcePath)) {
      InputStreamReader reader = new InputStreamReader(is, "UTF-8");
      StringWriter writer = new StringWriter();
      char[] buf = new char[1024];
      int len;
      while ((len = reader.read(buf)) != -1) {
        writer.write(buf, 0, len);
      }
      return writer.toString();
    } catch (Exception e) {
      throw new RuntimeException("Failed to read from resource '" + resourcePath + "'", e);
    }
  }


  private void runIckleQuery(String query) {
    QueryFactory qf = Search.getQueryFactory(personCache);
    Query q = qf.create(query);
    List<Person> results = q.list();
    con.printf("found %d matches\n", results.size());
    for (Person p : results) {
      con.printf("   %s\n", p);
    }
  }

  private void query() {
    con.printf("Example queries use 'p as prefix :\n   p.name = 'Aname'\n   p.age=42\n\n");
    String query = con.readLine(" complete query  : from wfink.playground.Person p where ");

    try {
      runIckleQuery("from wfink.playground.Person p where " + query);
    } catch (Exception e) {
      con.printf("Query failed with :  %s\n\n", e.getMessage());
    }
  }

  private void put() {
    Long id = null;
    Integer age = null;
    while (id == null) {
      String key = con.readLine(msgEnterKey);
      try {
        id = Long.parseLong(key);
      } catch (Exception e) {
      }
    }
    String name = con.readLine("Enter name: ");
    while (id == null) {
      String in = con.readLine("Enter age: ");
      try {
        age = Integer.parseInt(in);
      } catch (Exception e) {
      }
    }

    Person oldValue = personCache.put(id, new Person(id, name, age));

    if (oldValue != null) {
      con.printf("   Replace old value : %s\n", oldValue);
    }
  }

  private void get() {
    Long id = null;
    while (id == null) {
      String key = con.readLine(msgEnterKey);
      try {
        id = Long.parseLong(key);
      } catch (Exception e) {
      }
    }

    if (personCache.containsKey(id)) {
      con.printf("  value : %s\n", personCache.get(id));
    } else {
      con.printf("   No entry for key found!\n");
    }
  }

  private void contains() {
    Long id = null;
    while (id == null) {
      String key = con.readLine(msgEnterKey);
      try {
        id = Long.parseLong(key);
      } catch (Exception e) {
      }
    }

    if (personCache.containsKey(id)) {
      con.printf("   Entry found\n");
    } else {
      con.printf("   No entry for key found!\n");
    }
  }

  private void remove() {
    Long id = null;
    while (id == null) {
      String key = con.readLine(msgEnterKey);
      try {
        id = Long.parseLong(key);
      } catch (Exception e) {
      }
    }

    if (personCache.containsKey(id)) {
      personCache.remove(id);
    } else {
      con.printf("   No entry for key found!\n");
    }
  }

  private void size() {
    con.printf("   Total number of entries %s\n", personCache.size());
  }

  public void list() {
    for (Long key : personCache.keySet()) {
      con.printf("  %s\n", personCache.get(key));
    }
  }

  private void stop() {
    remoteCacheManager.stop();
  }

  private void inputLoop() {
    while (true) {
      String action = con.readLine(">");

      switch (action) {
      case "get":
      case "g":
        get();
        break;
      case "put":
      case "p":
        put();
        break;
      case "has":
      case "contain":
      case "c":
        contains();
        break;
      case "rm":
        remove();
        break;
      case "size":
      case "s":
        size();
        break;
      case "list":
      case "l":
        list();
        break;
      case "query":
      case "qu":
        query();
        break;
      case "q":
        return;
      case "help":
      case "h":
        printConsoleHelp();
      default:
        // do nothing
      }
    }
  }

  private void printConsoleHelp() {
    con.printf("Choose:\n" + "============= \n"
        + "put  -  add an entry\n"
        + "rm   -  remove an entry\n"
        + "get  -  print a value for key\n"
        + "has  -  check whether an entry exists\n"
        + "list -  list all entries which are store local\n"
        + "size -  size of cache\n"
        + "query-  use Ickle queries to find entries\n"
        + "h    -  show this help\n"
        + "q    -  quit\n");
  }

  public static void main(String[] args) {
    String host = "localhost";
    String port = "11222";
    String cache = "PersonCacheWithLoader";

    if (args.length > 0) {
      host = args[0];
    }
    if (args.length > 1) {
      port = args[1];
    }
    if (args.length > 2) {
      cache = args[2];
    }
    PersonQueryHotRodClient client = new PersonQueryHotRodClient(host, port, cache);

    client.printConsoleHelp();
    client.inputLoop();
    client.stop();
  }
}
