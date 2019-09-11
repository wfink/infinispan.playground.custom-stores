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
 
     This example use a simple CacheLoader implementation to retrieve data from a 'persistence'
     add the following XML configuration to the clustered.xml
     Copy the `CacheLoader/target/CacheLoader.jar` to the deployment folder of your server installation.

```xml
                <distributed-cache name="PersonCacheWithLoader">
                    <encoding>
                        <key media-type="application/x-protostream"/>
                        <value media-type="application/x-protostream"/>
                    </encoding>
                    <persistence>
                        <store class="org.infinispan.wfink.playground.cacheloader.impl.CustomCacheLoader">
                            <property name="name">
                                Wolf Fink
                            </property>
                            <property name="age">
                                32
                            </property>
                        </store>
                    </persistence>
                </distributed-cache>
```

     So each get which load from the 'persistence' will return the person 'Wolf Fink' with different ID's
     As there is no persistence behind and no writer nothing will survive a restart.

   HotRodClient
 
     The client will prompt to manipulate cache data.
     run the script 'runClient <path/to/infinispan/remote/libraries>'
     you are able to read and manipulate the cache.
     Note that a put will not be persisted and there is no warning or message for this.
     Also the person.proto need to be registered for the client side and the server as this is not done by the cache loader implementation.
