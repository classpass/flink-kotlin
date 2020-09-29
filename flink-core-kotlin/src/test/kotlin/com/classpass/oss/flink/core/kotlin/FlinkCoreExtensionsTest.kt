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
package com.classpass.oss.flink.core.kotlin

import org.apache.flink.api.common.state.MapState
import org.apache.flink.api.common.state.ValueState
import org.apache.flink.configuration.Configuration
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.datastream.DataStreamUtils
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.test.util.MiniClusterWithClientResource
import org.apache.flink.util.Collector
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class FlinkCoreExtensionsTest {

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
    internal fun getStateHelperWorks() {
        val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment()

        // calculate the running max
        val dataStream = env
            .fromElements(
                Measurement("foo", 1),
                Measurement("bar", 100),
                Measurement("foo", 7),
                Measurement("bar", 101),
                Measurement("bar", 102),
                Measurement("foo", 3)
            )
            .keyBy { it.type }
            .process(object : KeyedProcessFunction<String, Measurement, Measurement>() {
                @Transient
                private lateinit var maxMagnitudeState: ValueState<Int>

                override fun open(parameters: Configuration) {
                    maxMagnitudeState = runtimeContext.getState("measurement-max-magnitude")
                }

                override fun processElement(value: Measurement, ctx: Context, out: Collector<Measurement>) {
                    val oldMax = maxMagnitudeState.value() ?: 0

                    if (value.magnitude > oldMax) {
                        maxMagnitudeState.update(value.magnitude)
                        out.collect(Measurement(value.type, value.magnitude))
                    } else {
                        out.collect(Measurement(value.type, oldMax))
                    }
                }
            })

        assertEquals(
            mapOf(
                "foo" to listOf(1, 7, 7),
                "bar" to listOf(100, 101, 102)
            ),
            collectToList(dataStream)
                .groupBy { it.type }
                .mapValues { (_, values) -> values.map { it.magnitude } }
        )
    }

    @Test
    internal fun getMapStateHelperWorks() {
        val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment()

        // map each measuremnt to the max of its type
        val dataStream = env
            .fromElements(
                Measurement("foo", 1),
                Measurement("bar", 100),
                Measurement("foo", 7),
                Measurement("bar", 101),
                Measurement("bar", 102),
                Measurement("foo", 3)
            )
            .keyBy { 1 } // fake key to let us use state
            .process(object : KeyedProcessFunction<Int, Measurement, Measurement>() {
                @Transient
                private lateinit var maxMagnitudeState: MapState<String, Int>

                override fun open(parameters: Configuration) {
                    maxMagnitudeState = runtimeContext.getMapState("measurement-max-magnitude")
                }

                override fun processElement(value: Measurement, ctx: Context, out: Collector<Measurement>) {
                    val oldMax = maxMagnitudeState.get(value.type) ?: 0

                    if (value.magnitude > oldMax) {
                        maxMagnitudeState.put(value.type, value.magnitude)
                        out.collect(Measurement(value.type, value.magnitude))
                    } else {
                        out.collect(Measurement(value.type, oldMax))
                    }
                }
            })

        assertEquals(
            mapOf(
                "foo" to listOf(1, 7, 7),
                "bar" to listOf(100, 101, 102)
            ),
            collectToList(dataStream)
                .groupBy { it.type }
                .mapValues { (_, values) -> values.map { it.magnitude } }
        )
    }
}

internal fun <T> collectToList(stream: DataStream<T>): List<T> = DataStreamUtils.collect(stream).asSequence().toList()

internal data class Measurement(
    val type: String,
    val magnitude: Int
)
