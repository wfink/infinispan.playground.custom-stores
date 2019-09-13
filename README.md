Infinispan Customized Cache Stores
==================================

Author: Wolf-Dieter Fink
Level: Advanced
Technologies: Infinispan, Hot Rod, Query, Ickle


What is it?
-----------

Different examples how to implement and configure customized cache stores.



Prepare a server instance
-------------
Simple start a Infinispan or JDG server and add the following caches.

Build and Run the example
-------------------------
1. Type this command to build and deploy the archive:

        mvn clean package

2. Deploy the CacheStore implementation and use the HotRod client to access the cache

   Custom Cache Loader
 
     This example use a simple CacheLoader implementation to retrieve data from a 'persistence'.
     Copy the `CacheLoader/target/CacheLoader.jar` to the deployment folder of your server installation.
     Add the following XML configuration to the clustered.xml

```xml
                <distributed-cache name="PersonCacheWithLoader">
                    <encoding>
                        <key media-type="application/x-protostream"/>
                        <value media-type="application/x-protostream"/>
                    </encoding>
                    <persistence>
                        <store class="org.infinispan.wfink.playground.cacheloader.impl.CustomCacheLoader">
                            <property name="name">Wolf Fink</property>
                            <property name="age">42</property>
                        </store>
                    </persistence>
                </distributed-cache>
```

     So each get which load from the 'persistence' will return the person 'Wolf Fink' with different ID's
     As there is no persistence behind and no writer nothing will survive a restart.


   Custom Cache Writer
 
     This example implement CacheLoader and CacheWriter to retrieve and store the data from/to a 'persistence'.

     Copy the `CacheWriter/target/CacheWriter.jar` to the deployment folder of your server installation.
     Add the following XML configuration to the clustered.xml, change or remove the properties as needed.
     For the database you need to add the datasource and driver and add the driver as module or deployment as well.

```xml
                <distributed-cache name="PersonCacheWithLoader">
                    <encoding>
                        <key media-type="application/x-protostream"/>
                        <value media-type="application/x-protostream"/>
                    </encoding>
                    <persistence>
                        <store class="org.infinispan.wfink.playground.cacheloader.impl.CustomCacheWriter">
                            <property name="datasource">java:jboss/datasources/mySQLDS</property>
                            <property name="database">jdg</property>
                            <property name="table">MyPerson</property>
                        </store>
                    </persistence>
                </distributed-cache>
```

     So each get which load from the persistence, a put will store the Person to the database.
     The implemented SQL is simple and should work for all kind of database.


   HotRodClient
 
     The client will prompt to manipulate cache data.
     run the script 'runClient <path/to/infinispan/remote/libraries>'
     you are able to read and manipulate the cache.
     Depending on the installed Store implementation you can check the behaviour.
     If 'org.infninispan.wfink' category is set to trace the server will log messages for each action.
     Also the person.proto need to be registered for the client side and the server as this is not done by the cache loader implementation.
