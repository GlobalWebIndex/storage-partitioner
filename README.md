## storage-partitioner

[![Build Status](https://travis-ci.org/GlobalWebIndex/storage-partitioner.svg?branch=master)](https://travis-ci.org/GlobalWebIndex/storage-partitioner)

```
"net.globalwebindex" %% "storage-partitioner" % "x.y.z"
```
or
```
dependsOn(ProjectRef(uri("https://github.com/GlobalWebIndex/storage-partitioner.git#vx.y.x"), "all"))
```

Abstraction of storages with partitioned data, currently only time series data is supported and implementation is provided
for `s3` and `druid` storages.

The purpose of this library is providing user with a unified interface for storage operations and hiding the storage implementation details
from users that should only see partitions and data, not anything storage specific.

When building an `ETL` pipeline that extracts and loads data with the same partitioning between various storage types, the user
must focus on `Transform` instead of `Extract` and `Load`.

Note that :
 - this library is extremely **WIP**, adding one more storage could lead to heavy API changes.
 - this way of "integration by abstraction" might seem a bit wrong and a way of storage "Sinks" and "Sources"
   makes better sense but in case of time series data, if you take partitioning and granularity into consideration,
   it would be very hard to implement something like these generic Sinks and Sources, however it might go this direction further on