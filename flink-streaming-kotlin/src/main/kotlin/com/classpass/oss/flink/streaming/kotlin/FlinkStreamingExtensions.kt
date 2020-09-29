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

import org.apache.flink.streaming.api.functions.co.ProcessJoinFunction
import org.apache.flink.util.Collector

inline fun <I, J, O> processJoinFunction(
    crossinline block: (I, J, ProcessJoinFunction<I, J, O>.Context, Collector<O>) -> Unit
): ProcessJoinFunction<I, J, O> {
    return object : ProcessJoinFunction<I, J, O>() {
        override fun processElement(left: I, right: J, ctx: Context, out: Collector<O>) =
            block(left, right, ctx, out)
    }
}
