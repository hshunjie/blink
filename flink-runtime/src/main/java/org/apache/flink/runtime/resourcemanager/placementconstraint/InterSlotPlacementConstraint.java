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

package org.apache.flink.runtime.resourcemanager.placementconstraint;

import java.util.List;

/**
 * A {@link PlacementConstraint} that describes what slots should or should not exist in the context.
 */
public class InterSlotPlacementConstraint extends PlacementConstraint {
	private final TaggedSlotContext context;
	private final PlacementConstraintScope scope;

	public InterSlotPlacementConstraint(TaggedSlot slot, TaggedSlotContext context, PlacementConstraintScope scope) {
		super(slot);
		this.context = context;
		this.scope = scope;
	}

	public InterSlotPlacementConstraint(TaggedSlot slot, TaggedSlotContext context) {
		this(slot, context, PlacementConstraintScope.TM);
	}

	@Override
	public boolean check(List<List<SlotTag>> taskExecutorTags) {
		return context.matchContextWithTags(taskExecutorTags);
	}
}
