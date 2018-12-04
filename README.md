## storage-partitioner

[![DroneCI](https://drone.globalwebindex.net/api/badges/GlobalWebIndex/storage-partitioner/status.svg)](https://drone.globalwebindex.net/GlobalWebIndex/storage-partitioner)
[![storage-partitioner-api](https://api.bintray.com/packages/l15k4/GlobalWebIndex/storage-partitioner-api/images/download.svg) ](https://bintray.com/l15k4/GlobalWebIndex/storage-partitioner-api/_latestVersion)
[![storage-partitioner-all](https://api.bintray.com/packages/l15k4/GlobalWebIndex/storage-partitioner-all/images/download.svg) ](https://bintray.com/l15k4/GlobalWebIndex/storage-partitioner-all/_latestVersion)
[![storage-partitioner-s3](https://api.bintray.com/packages/l15k4/GlobalWebIndex/storage-partitioner-s3/images/download.svg) ](https://bintray.com/l15k4/GlobalWebIndex/storage-partitioner-s3/_latestVersion)
[![storage-partitioner-gcs](https://api.bintray.com/packages/l15k4/GlobalWebIndex/storage-partitioner-gcs/images/download.svg) ](https://bintray.com/l15k4/GlobalWebIndex/storage-partitioner-gcs/_latestVersion)
[![storage-partitioner-cql](https://api.bintray.com/packages/l15k4/GlobalWebIndex/storage-partitioner-cql/images/download.svg) ](https://bintray.com/l15k4/GlobalWebIndex/storage-partitioner-cql/_latestVersion)
[![storage-partitioner-druid](https://api.bintray.com/packages/l15k4/GlobalWebIndex/storage-partitioner-druid/images/download.svg) ](https://bintray.com/l15k4/GlobalWebIndex/storage-partitioner-druid/_latestVersion)

```
"net.globalwebindex" %% "storage-partitioner-api" % "x.y.z"
"net.globalwebindex" %% "storage-partitioner-all" % "x.y.z"
"net.globalwebindex" %% "storage-partitioner-s3" % "x.y.z"
"net.globalwebindex" %% "storage-partitioner-gcs" % "x.y.z"
"net.globalwebindex" %% "storage-partitioner-cql" % "x.y.z"
"net.globalwebindex" %% "storage-partitioner-druid" % "x.y.z"
```
or
```
dependsOn(ProjectRef(uri("https://github.com/GlobalWebIndex/storage-partitioner.git#vx.y.x"), "storage-partitioner-api"))
dependsOn(ProjectRef(uri("https://github.com/GlobalWebIndex/storage-partitioner.git#vx.y.x"), "storage-partitioner-all"))
dependsOn(ProjectRef(uri("https://github.com/GlobalWebIndex/storage-partitioner.git#vx.y.x"), "storage-partitioner-s3"))
dependsOn(ProjectRef(uri("https://github.com/GlobalWebIndex/storage-partitioner.git#vx.y.x"), "storage-partitioner-gcs"))
dependsOn(ProjectRef(uri("https://github.com/GlobalWebIndex/storage-partitioner.git#vx.y.x"), "storage-partitioner-cql"))
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