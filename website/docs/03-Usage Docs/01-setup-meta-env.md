# Setup LakeSoul Meta DB

LakeSoul use `lakesoul_home` (case insensitive) environment variable or `lakesoul_home` JVM property (case sensitive) to locate config file. The config file consists of required LakeSoul configs such as Postgres's connection info. An example property file is like the following:
```ini
lakesoul.pg.driver=com.lakesoul.shaded.org.postgresql.Driver
lakesoul.pg.url=jdbc:postgresql://localhost:5432/lakesoul_test?stringtype=unspecified
lakesoul.pg.username=lakesoul_test
lakesoul.pg.password=lakesoul_test
```
Change the connection url, username and password according to your deployment.

If the property file cannot be read, `LAKESOUL_PG_DRIVER`, `LAKESOUL_PG_URL`, `LAKESOUL_PG_USERNAME` and `LAKESOUL_PG_PASSWORD` environment variables will be used to set corresponding values.

:::caution
Version 2.0.1 and before only supports `lakesoul_home` env (in lower case) to locate the property file.
:::

:::caution
Since 2.1.0, the Spark and Flink jars already include all their dependencies via maven shade plugin. And the PG driver class name should become `com.lakesoul.shaded.org.postgresql.Driver`. And before 2.0.1 (included), the driver class name was `org.postgresql.Driver`.
:::

Then create LakeSoul meta tables:
```bash
PGPASSWORD=lakesoul_test psql -h localhost -p 5432 -U lakesoul_test -f script/meta_init.sql
```
`meta_init.sql` is located under `script` dir in the source code.