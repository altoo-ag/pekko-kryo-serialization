pekko-kryo-serialization - migration guide
=========================================

Migration from akka-kryo-serialization to pekko-kryo-serialization
-----------------------------
* You should upgrade to akka-kryo-serialization 2.5.0 before migrating to pekko-kryo-serialization and perform the respective [migrations](https://github.com/altoo-ag/akka-kryo-serialization/blob/master/migration-guide.md).
* To support efforts for live migration from Akka to Pekko, compat modules are available in both Akka and Pekko Kryo Serialization to help with wire compatibility of custom messages containing ActorRefs and ByteStrings.
  ```
  # on Pekko
  libraryDependencies += "io.altoo" %% "pekko-kryo-serialization-akka-compat" % "1.0.1"
  
  # on Akka
  libraryDependencies += "io.altoo" %% "pekko-kryo-serialization-akka-compat" % "2.5.2"
  ```
  Then configure (or derive from if using a custom initializer) `AkkaCompatKryoInitializer` on Pekko, and `PekkoCompatKryoInitializer` on Akka.
  ```
  # on Pekko
  pekko-kryo-serialization.kryo-initializer = "io.altoo.pekko.serialization.kryo.compat.AkkaCompatKryoInitializer"
  
  # on Akka
  kka-kryo-serialization.kryo-initializer = "io.altoo.akka.serialization.kryo.compat.PekkoCompatKryoInitializer"
  ```
