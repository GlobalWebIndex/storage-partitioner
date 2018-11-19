## storage-partitioner

[![DroneCI](https://drone.globalwebindex.net/api/badges/GlobalWebIndex/storage-partitioner/status.svg)](https://drone.globalwebindex.net/GlobalWebIndex/storage-partitioner)

```
"net.globalwebindex" %% "storage-partitioner-all" % "x.y.z"
"net.globalwebindex" %% "storage-partitioner-s3" % "x.y.z"
"net.globalwebindex" %% "storage-partitioner-druid" % "x.y.z"
```
or
```
dependsOn(ProjectRef(uri("https://github.com/GlobalWebIndex/storage-partitioner.git#vx.y.x"), "storage-partitioner-all"))
dependsOn(ProjectRef(uri("https://github.com/GlobalWebIndex/storage-partitioner.git#vx.y.x"), "storage-partitioner-s3"))
dependsOn(ProjectRef(uri("https://github.com/GlobalWebIndex/storage-partitioner.git#vx.y.x"), "storage-partitioner-druid"))
```

This project targets primarily storages like FS, S3, FTP, etc., that :
  - do not have any kind of built-in partitioning like databases do
  - cannot be searched easily, so that you want to reduce the area to be searched the hard way

But even columnar databases need some kind of partitioning management because they persist data denormalized
and it is not exactly easy to track partition state.

Partitioning then must be implemented on client side for such storages and this is what this library helps with.
Currently only time series data is supported and implementation is provided for `s3`, `druid`, `cassandra` or `scyllaDB`.

When building an `ETL` pipeline that extracts and loads data with the same partitioning between various storage types, the user
must focus on `Transform` instead of `Extract` and `Load`.

Note that :
 - this library is extremely **WIP**, adding one more storage could lead to heavy API changes.
 - this way of "integration by abstraction" might seem a bit wrong and a way of storage "Sinks" and "Sources"
   makes better sense but in case of time series data, if you take partitioning and granularity into consideration,
   it would be very hard to implement something like these generic Sinks and Sources, however it might go this direction further on