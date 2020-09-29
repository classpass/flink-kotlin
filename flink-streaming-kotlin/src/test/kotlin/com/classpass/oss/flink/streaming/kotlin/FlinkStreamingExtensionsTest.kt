/**
 * Copyright 2020 ClassPass
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package com.classpass.oss.flink.streaming.kotlin

import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.datastream.DataStreamUtils
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.test.util.MiniClusterWithClientResource
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.math.max
import kotlin.test.assertEquals

internal class FlinkStreamingExtensionsTest {
    companion object {
        // as per https://ci.apache.org/projects/flink/flink-docs-release-1.10/dev/stream/testing.html#testing-flink-jobs
        private val cluster = MiniClusterWithClientResource(
            MiniClusterResourceConfiguration.Builder()
                .setNumberSlotsPerTaskManager(2)
                .setNumberTaskManagers(1)
                .build()
        )

        @JvmStatic
        @BeforeAll
        internal fun setUp() = cluster.before()

        @JvmStatic
        @AfterAll
        internal fun tearDown() = cluster.after()
    }

    @Test
    internal fun processJoinFunctionHelper() {
        data class User(val id: Int, val name: String, val timestampMs: Long)
        data class Click(val userId: Int, val target: String, val timestampMs: Long)

        // product of user and click
        data class UserClick(val id: Int, val name: String, val target: String, val timestampMs: Long)

        val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment()
        env.streamTimeCharacteristic = TimeCharacteristic.EventTime

        val users = env.fromElements(
            User(1, "one", 1_000),
        )
            .assignTimestampsAndWatermarks(
                WatermarkStrategy.forMonotonousTimestamps<User>()
                    .withTimestampAssigner { user, _ -> user.timestampMs }
            )
            .keyBy { it.id }

        val clicks = env.fromElements(
            Click(1, "1.1", 1_001),
        )
            .assignTimestampsAndWatermarks(
                WatermarkStrategy.forMonotonousTimestamps<Click>()
                    .withTimestampAssigner { click, _ -> click.timestampMs }
            )
            .keyBy { it.userId }

        val joined = users.intervalJoin(clicks)
            .between(Time.seconds(-20), Time.seconds(20))
            .process(
                processJoinFunction<User, Click, UserClick> { user, click, _, out ->
                    out.collect(UserClick(user.id, user.name, click.target, max(user.timestampMs, click.timestampMs)))
                }
            )
        assertEquals(
            listOf(
                UserClick(1, "one", "1.1", 1_001)
            ),
            collectToList(joined)
        )
    }
}

fun <T> collectToList(stream: DataStream<T>): List<T> = DataStreamUtils.collect(stream).asSequence().toList()
