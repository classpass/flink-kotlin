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

import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.api.common.state.MapState
import org.apache.flink.api.common.state.MapStateDescriptor
import org.apache.flink.api.common.state.ValueState
import org.apache.flink.api.common.state.ValueStateDescriptor

inline fun <reified T> RuntimeContext.getState(name: String): ValueState<T> =
    getState(ValueStateDescriptor(name, T::class.java))

inline fun <reified K, reified V> RuntimeContext.getMapState(name: String): MapState<K, V> =
    getMapState(MapStateDescriptor(name, K::class.java, V::class.java))
