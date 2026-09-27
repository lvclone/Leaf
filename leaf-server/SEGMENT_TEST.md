# Segment Mode Test

This test suite covers the Docker MySQL-backed segment mode. Snowflake mode is intentionally excluded.

## Prerequisites

- Docker and Docker Compose
- Java and Maven
- `curl`, `ab`, `awk`, `sort`, `uniq`, and `xargs`
- A configured `leaf-server/.env`

## Run

From the repository root:

```shell
chmod +x leaf-server/scripts/segment-test.sh
leaf-server/scripts/segment-test.sh
```

The script starts or reuses the MySQL service from `leaf-server/docker-compose.yml`, packages the current source, starts temporary Leaf server instances on ports `18080` and `18081`, and cleans up those Java processes when finished. The MySQL container remains running.

The checks are:

1. Concurrent ID uniqueness on one instance.
2. ID uniqueness across a service restart.
3. ID generation recovery after stopping and starting MySQL.
4. Sustained HTTP load for 60 seconds.
5. Concurrent ID uniqueness across two Leaf instances sharing MySQL.

The test does not reset `T_LEAF_ALLOC`; `MAX_ID` will advance as part of normal segment allocation. Generated logs and ID samples are kept in a temporary directory printed at the end.

## Options

Override defaults with environment variables:

```shell
LEAF_LONG_SECONDS=300 \
LEAF_UNIQUE_REQUESTS=100000 \
LEAF_MULTI_INSTANCE_REQUESTS=10000 \
leaf-server/scripts/segment-test.sh
```

Useful variables include `LEAF_ENV_FILE`, `LEAF_TEST_KEY`, `LEAF_LONG_SECONDS`, `LEAF_LONG_BATCH_REQUESTS`, `LEAF_UNIQUE_REQUESTS`, and `LEAF_MULTI_INSTANCE_REQUESTS`.
