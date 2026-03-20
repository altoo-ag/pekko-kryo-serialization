This is just a small benchmark to test performance between versions.
Entirely single-threaded. To test real performance, you should test your own system with the specific messages/setup you use.

# 1.5.0 - 2026.03.26
    io.altoo.serialization.kryo.pekko.performance.KryoSerializationBenchmark with 5s
    Intel(R) Core(TM) i7-9700K CPU @ 3.60GHz
    Java 25.0.2
    Scala 3.3.7


    === Benchmark Summary ===
    Serializer           MessageType                    Throughput(ops/sec)
    java                 SmallMessage                                305'087
    java                 CollectionsMessage                            1'132
    java                 NestedMessage                                 1'345
    java                 LargeMessageByte[]                           92'334
    java                 LargeMessageByteString                       79'887
    kryo                 SmallMessage                              1'313'322
    kryo                 CollectionsMessage                            2'847
    kryo                 NestedMessage                                 2'095
    kryo                 LargeMessageByte[]                          172'162
    kryo                 LargeMessageByteString                      165'769


# 1.4.0 - 2026.03.26
    io.altoo.serialization.kryo.pekko.performance.KryoSerializationBenchmark with 5s
    Intel(R) Core(TM) i7-9700K CPU @ 3.60GHz
    Java 25.0.2
    Scala 3.3.7

    === Benchmark Summary ===
    Serializer           MessageType                    Throughput(ops/sec)
    java                 SmallMessage                                304'051
    java                 CollectionsMessage                            1'164
    java                 NestedMessage                                 1'425
    java                 LargeMessageByte[]                           94'180
    java                 LargeMessageByteString                       82'039
    kryo                 SmallMessage                              1'267'164
    kryo                 CollectionsMessage                            2'648
    kryo                 NestedMessage                                 1'767
    kryo                 LargeMessageByte[]                          172'505
    kryo                 LargeMessageByteString                       61'828