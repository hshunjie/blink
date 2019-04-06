/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.api.common.resources;

import org.apache.flink.annotation.Internal;

/**
 * The variant resource in flink.
 */
@Internal
public class CommonExtendedResource extends Resource {

	private static final long serialVersionUID = -2276080061777135142L;

	public CommonExtendedResource(String name, double value) {
		this(name, value, ResourceAggregateType.AGGREGATE_TYPE_SUM);
	}

	public CommonExtendedResource(String name, double value, ResourceAggregateType type) {
		super(name, value, type);
	}

	@Override
	public Resource create(double value, ResourceAggregateType type) {
		return new CommonExtendedResource(this.getName(), value, type);
	}
}
