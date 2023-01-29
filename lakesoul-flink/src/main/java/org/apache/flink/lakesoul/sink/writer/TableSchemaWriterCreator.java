/*
 *
 * Copyright [2022] [DMetaSoul Team]
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 */

package org.apache.flink.lakesoul.sink.writer;

import org.apache.arrow.lakesoul.io.NativeIOBase;
import org.apache.flink.api.common.serialization.BulkWriter;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.ReadableConfig;
import org.apache.flink.core.fs.FileSystem;
import org.apache.flink.core.fs.Path;
import org.apache.flink.formats.parquet.row.ParquetRowDataBuilder;
import org.apache.flink.lakesoul.sink.bucket.CdcPartitionComputer;
import org.apache.flink.lakesoul.sink.bucket.FlinkBucketAssigner;
import org.apache.flink.lakesoul.tool.FlinkUtil;
import org.apache.flink.lakesoul.tool.LakeSoulSinkOptions;
import org.apache.flink.lakesoul.types.TableId;
import org.apache.flink.lakesoul.types.TableSchemaIdentity;
import org.apache.flink.streaming.api.functions.sink.filesystem.BucketAssigner;
import org.apache.flink.streaming.api.functions.sink.filesystem.BucketWriter;
import org.apache.flink.streaming.api.functions.sink.filesystem.BulkBucketWriter;
import org.apache.flink.streaming.api.functions.sink.filesystem.OutputFileConfig;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.types.logical.RowType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Properties;

import static org.apache.flink.formats.parquet.ParquetFileFormatFactory.IDENTIFIER;
import static org.apache.flink.formats.parquet.ParquetFileFormatFactory.UTC_TIMEZONE;

public class TableSchemaWriterCreator implements Serializable {
    private static final Logger LOG = LoggerFactory.getLogger(TableSchemaWriterCreator.class);

    public TableSchemaIdentity identity;

    public List<String> primaryKeys;

    public List<String> partitionKeyList;

    public OutputFileConfig outputFileConfig;

    public CdcPartitionComputer partitionComputer;

    public BucketAssigner<RowData, String> bucketAssigner;

    public Path tableLocation;

    public BulkWriter.Factory<RowData> writerFactory;

    public Configuration conf;

    public static TableSchemaWriterCreator create(
            TableId tableId,
            RowType rowType,
            String tableLocation,
            List<String> primaryKeys,
            List<String> partitionKeyList,
            Configuration conf) throws IOException {
        TableSchemaWriterCreator creator = new TableSchemaWriterCreator();
        creator.conf = conf;
        creator.identity = new TableSchemaIdentity(tableId, rowType, tableLocation, primaryKeys, partitionKeyList);
        creator.primaryKeys = primaryKeys;
        creator.partitionKeyList = partitionKeyList;
        creator.outputFileConfig = OutputFileConfig.builder().build();

        creator.partitionComputer = new CdcPartitionComputer(
                "default",
                rowType.getFieldNames().toArray(new String[0]),
                rowType,
                partitionKeyList.toArray(new String[0]),
                conf.getBoolean(LakeSoulSinkOptions.USE_CDC)
        );

        creator.bucketAssigner = new FlinkBucketAssigner(creator.partitionComputer);

        creator.tableLocation = FlinkUtil.makeQualifiedPath(tableLocation);
        creator.writerFactory = ParquetRowDataBuilder.createWriterFactory(
                        rowType,
                        getParquetConfiguration(conf),
                        conf.get(UTC_TIMEZONE));

        return creator;
    }

    public BucketWriter<RowData, String> createBucketWriter() throws IOException {
        if (NativeIOBase.isNativeIOLibExist()) {
            LOG.info("Create natvie bucket writer");
            return new NativeBucketWriter(this.identity.rowType, this.primaryKeys, this.conf);
        } else {
            // TODO: we should throw
            return new BulkBucketWriter<>(
                    FileSystem.get(tableLocation.toUri()).createRecoverableWriter(), writerFactory);
        }
    }

    public static org.apache.hadoop.conf.Configuration getParquetConfiguration(ReadableConfig options) {
        org.apache.hadoop.conf.Configuration conf = new org.apache.hadoop.conf.Configuration();
        conf.set("parquet.compression", "snappy");
        conf.setInt("parquet.block.size", 32 * 1024 * 1024);
        Properties properties = new Properties();
        ((org.apache.flink.configuration.Configuration) options).addAllToProperties(properties);
        properties.forEach((k, v) -> conf.set(IDENTIFIER + "." + k, v.toString()));
        return conf;
    }
}
