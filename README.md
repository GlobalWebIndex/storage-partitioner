## storage-partitioner

```
"net.globalwebindex" %% "storage-partitioner" % "0.01-SNAPSHOT"
```

Abstraction of storages with partitioned data, currently only time series data is supported and implementation is provided
for `s3` and `druid` storages.

The purpose of this library is providing user with a unified interface for storage operations and hiding the storage implementation details
from users that should only see partitions and data, not anything storage specific.

When building an `ETL` pipeline that extracts and loads data with the same partitioning between various storage types, the user
must focus on `Transform` instead of `Extract` and `Load`.

Note that this library is extremely **WIP**, adding one more storage could lead to heavy API changes.