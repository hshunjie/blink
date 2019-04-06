/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.streaming.api.datastream;

import org.apache.flink.annotation.Public;
import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.api.common.functions.InvalidTypesException;
import org.apache.flink.api.common.operators.ResourceSpec;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.typeutils.TypeInfoParser;
import org.apache.flink.configuration.ConfigOption;
import org.apache.flink.configuration.CoreOptions;
import org.apache.flink.streaming.api.collector.selector.OutputSelector;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.operators.ChainingStrategy;
import org.apache.flink.streaming.api.transformations.SideOutputTransformation;
import org.apache.flink.streaming.api.transformations.StreamTransformation;
import org.apache.flink.util.OutputTag;
import org.apache.flink.util.Preconditions;

import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static org.apache.flink.util.Preconditions.checkArgument;

/**
 * {@code SingleOutputStreamOperator} represents a user defined transformation
 * applied on a {@link DataStream} with one predefined output type.
 *
 * @param <T> The type of the elements in this stream.
 */
@Public
public class SingleOutputStreamOperator<T> extends DataStream<T> {

	/** Indicate this is a non-parallel operator and cannot set a non-1 degree of parallelism. **/
	protected boolean nonParallel = false;

	/**
	 * We keep track of the side outputs that were already requested and their types. With this,
	 * we can catch the case when a side output with a matching id is requested for a different
	 * type because this would lead to problems at runtime.
	 */
	private Map<OutputTag<?>, TypeInformation> requestedSideOutputs = new HashMap<>();

	private boolean wasSplitApplied = false;

	protected SingleOutputStreamOperator(StreamExecutionEnvironment environment, StreamTransformation<T> transformation) {
		super(environment, transformation);
	}

	/**
	 * Gets the name of the current data stream. This name is
	 * used by the visualization and logging during runtime.
	 *
	 * @return Name of the stream.
	 */
	public String getName() {
		return transformation.getName();
	}

	/**
	 * Sets the name of the current data stream. This name is
	 * used by the visualization and logging during runtime.
	 *
	 * @return The named operator.
	 */
	public SingleOutputStreamOperator<T> name(String name){
		transformation.setName(name);
		return this;
	}

	/**
	 * Sets an ID for this operator.
	 *
	 * <p>The specified ID is used to assign the same operator ID across job
	 * submissions (for example when starting a job from a savepoint).
	 *
	 * <p><strong>Important</strong>: this ID needs to be unique per
	 * transformation and job. Otherwise, job submission will fail.
	 *
	 * @param uid The unique user-specified ID of this transformation.
	 * @return The operator with the specified ID.
	 */
	@PublicEvolving
	public SingleOutputStreamOperator<T> uid(String uid) {
		transformation.setUid(uid);
		return this;
	}

	/**
	 * Sets an user provided hash for this operator. This will be used AS IS the create the
	 * JobVertexID.
	 *
	 * <p>The user provided hash is an alternative to the generated hashes, that is considered when
	 * identifying an operator through the default hash mechanics fails (e.g. because of changes
	 * between Flink versions).
	 *
	 * <p><strong>Important</strong>: this should be used as a workaround or for trouble shooting.
	 * The provided hash needs to be unique per transformation and job. Otherwise, job submission
	 * will fail. Furthermore, you cannot assign user-specified hash to intermediate nodes in an
	 * operator chain and trying so will let your job fail.
	 *
	 * <p>A use case for this is in migration between Flink versions or changing the jobs in a way
	 * that changes the automatically generated hashes. In this case, providing the previous hashes
	 * directly through this method (e.g. obtained from old logs) can help to reestablish a lost
	 * mapping from states to their target operator.
	 * <p/>
	 *
	 * @param uidHash The user provided hash for this operator. This will become the JobVertexID,
	 *                  which is shown in the logs and web ui.
	 * @return The operator with the user provided hash.
	 */
	@PublicEvolving
	public SingleOutputStreamOperator<T> setUidHash(String uidHash) {
		transformation.setUidHash(uidHash);
		return this;
	}

	/**
	 * Sets the parallelism for this operator.
	 *
	 * @param parallelism
	 *            The parallelism for this operator.
	 * @return The operator with set parallelism.
	 */
	public SingleOutputStreamOperator<T> setParallelism(int parallelism) {
		Preconditions.checkArgument(canBeParallel() || parallelism == 1,
				"The parallelism of non parallel operator must be 1.");

		transformation.setParallelism(parallelism);

		return this;
	}

	/**
	 * Sets the maximum parallelism of this operator.
	 *
	 * <p>The maximum parallelism specifies the upper bound for dynamic scaling. It also defines the
	 * number of key groups used for partitioned state.
	 *
	 * @param maxParallelism Maximum parallelism
	 * @return The operator with set maximum parallelism
	 */
	@PublicEvolving
	public SingleOutputStreamOperator<T> setMaxParallelism(int maxParallelism) {
		Preconditions.checkArgument(maxParallelism > 0,
				"The maximum parallelism must be greater than 0.");

		Preconditions.checkArgument(canBeParallel() || maxParallelism == 1,
				"The maximum parallelism of non parallel operator must be 1.");

		transformation.setMaxParallelism(maxParallelism);

		return this;
	}

	//	---------------------------------------------------------------------------
	//	 Fine-grained resource profiles are an incomplete work-in-progress feature
	//	 The setters are hence private at this point.
	//	---------------------------------------------------------------------------

	/**
	 * Sets the minimum and preferred resources for this operator, and the lower and upper resource limits will
	 * be considered in dynamic resource resize feature for future plan.
	 *
	 * @param minResources The minimum resources for this operator.
	 * @param preferredResources The preferred resources for this operator.
	 * @return The operator with set minimum and preferred resources.
	 */
	public SingleOutputStreamOperator<T> setResources(ResourceSpec minResources, ResourceSpec preferredResources) {
		Preconditions.checkNotNull(minResources, "The min resources must be not null.");
		Preconditions.checkNotNull(preferredResources, "The preferred resources must be not null.");
		Preconditions.checkArgument(minResources.isValid() && preferredResources.isValid() && minResources.lessThanOrEqual(preferredResources),
				"The values in resources must be not less than 0 and the preferred resources must be greater than the min resources.");

		transformation.setResources(minResources, preferredResources);

		return this;
	}

	/**
	 * Sets the resources for this operator, the minimum and preferred resources are the same by default.
	 *
	 * <p>Note: If the resources of the operator is explicitly set, the default resource settings will be used
	 * by other operators in the same job that are not explicitly set required resources. Please see
	 * {@link StreamExecutionEnvironment#setDefaultResources(ResourceSpec)}, {@link CoreOptions#DEFAULT_RESOURCE_CPU_CORES}
	 * and {@link CoreOptions#DEFAULT_RESOURCE_HEAP_MEMORY} to set the default resources.
	 *
	 * @param resources The resources for this operator.
	 * @return The operator with set minimum and preferred resources.
	 */
	public SingleOutputStreamOperator<T> setResources(ResourceSpec resources) {
		Preconditions.checkNotNull(resources, "The resources must be not null.");
		Preconditions.checkArgument(resources.isValid(), "The values in resources must be not less than 0.");

		transformation.setResources(resources, resources);

		return this;
	}

	private boolean canBeParallel() {
		return !nonParallel;
	}

	/**
	 * Sets the parallelism and maximum parallelism of this operator to one.
	 * And mark this operator cannot set a non-1 degree of parallelism.
	 *
	 * @return The operator with only one parallelism.
	 */
	@PublicEvolving
	public SingleOutputStreamOperator<T> forceNonParallel() {
		transformation.setParallelism(1);
		transformation.setMaxParallelism(1);
		nonParallel = true;
		return this;
	}

	/**
	 * Sets the buffering timeout for data produced by this operation.
	 * The timeout defines how long data may linger in a partially full buffer
	 * before being sent over the network.
	 *
	 * <p>Lower timeouts lead to lower tail latencies, but may affect throughput.
	 * Timeouts of 1 ms still sustain high throughput, even for jobs with high parallelism.
	 *
	 * <p>A value of '-1' means that the default buffer timeout should be used. A value
	 * of '0' indicates that no buffering should happen, and all records/events should be
	 * immediately sent through the network, without additional buffering.
	 *
	 * @param timeoutMillis
	 *            The maximum time between two output flushes.
	 * @return The operator with buffer timeout set.
	 */
	public SingleOutputStreamOperator<T> setBufferTimeout(long timeoutMillis) {
		checkArgument(timeoutMillis >= -1, "timeout must be >= -1");
		transformation.setBufferTimeout(timeoutMillis);
		return this;
	}

	/**
	 * Sets the {@link ChainingStrategy} for the given operator affecting the
	 * way operators will possibly be co-located on the same thread for
	 * increased performance.
	 *
	 * @param strategy
	 *            The selected {@link ChainingStrategy}
	 * @return The operator with the modified chaining strategy
	 */
	@PublicEvolving
	private SingleOutputStreamOperator<T> setChainingStrategy(ChainingStrategy strategy) {
		this.transformation.setChainingStrategy(strategy);
		return this;
	}

	/**
	 * Turns off chaining for this operator so thread co-location will not be used as an
	 * optimization.
	 *
	 * <p>Chaining can be turned off for the whole job by
	 * {@link StreamExecutionEnvironment#disableOperatorChaining()} however it is not advised for
	 * performance considerations.
	 *
	 * @return The operator with chaining disabled
	 */
	@PublicEvolving
	public SingleOutputStreamOperator<T> disableChaining() {
		return setChainingStrategy(ChainingStrategy.NEVER);
	}

	/**
	 * Starts a new task chain beginning at this operator. This operator will
	 * not be chained (thread co-located for increased performance) to any
	 * previous tasks even if possible.
	 *
	 * @return The operator with chaining set.
	 */
	@PublicEvolving
	public SingleOutputStreamOperator<T> startNewChain() {
		return setChainingStrategy(ChainingStrategy.HEAD);
	}

	// ------------------------------------------------------------------------
	//  Type hinting
	// ------------------------------------------------------------------------

	/**
	 * Adds a type information hint about the return type of this operator. This method
	 * can be used in cases where Flink cannot determine automatically what the produced
	 * type of a function is. That can be the case if the function uses generic type variables
	 * in the return type that cannot be inferred from the input type.
	 *
	 * <p>Classes can be used as type hints for non-generic types (classes without generic parameters),
	 * but not for generic types like for example Tuples. For those generic types, please
	 * use the {@link #returns(TypeHint)} method.
	 *
	 * @param typeClass The class of the returned data type.
	 * @return This operator with the type information corresponding to the given type class.
	 */
	public SingleOutputStreamOperator<T> returns(Class<T> typeClass) {
		requireNonNull(typeClass, "type class must not be null.");

		try {
			return returns(TypeInformation.of(typeClass));
		}
		catch (InvalidTypesException e) {
			throw new InvalidTypesException("Cannot infer the type information from the class alone." +
					"This is most likely because the class represents a generic type. In that case," +
					"please use the 'returns(TypeHint)' method instead.");
		}
	}

	/**
	 * Adds a type information hint about the return type of this operator. This method
	 * can be used in cases where Flink cannot determine automatically what the produced
	 * type of a function is. That can be the case if the function uses generic type variables
	 * in the return type that cannot be inferred from the input type.
	 *
	 * <p>Use this method the following way:
	 * <pre>{@code
	 *     DataStream<Tuple2<String, Double>> result =
	 *         stream.flatMap(new FunctionWithNonInferrableReturnType())
	 *               .returns(new TypeHint<Tuple2<String, Double>>(){});
	 * }</pre>
	 *
	 * @param typeHint The type hint for the returned data type.
	 * @return This operator with the type information corresponding to the given type hint.
	 */
	public SingleOutputStreamOperator<T> returns(TypeHint<T> typeHint) {
		requireNonNull(typeHint, "TypeHint must not be null");

		try {
			return returns(TypeInformation.of(typeHint));
		}
		catch (InvalidTypesException e) {
			throw new InvalidTypesException("Cannot infer the type information from the type hint. " +
					"Make sure that the TypeHint does not use any generic type variables.");
		}
	}

	/**
	 * Adds a type information hint about the return type of this operator. This method
	 * can be used in cases where Flink cannot determine automatically what the produced
	 * type of a function is. That can be the case if the function uses generic type variables
	 * in the return type that cannot be inferred from the input type.
	 *
	 * <p>In most cases, the methods {@link #returns(Class)} and {@link #returns(TypeHint)}
	 * are preferable.
	 *
	 * @param typeInfo type information as a return type hint
	 * @return This operator with a given return type hint.
	 */
	public SingleOutputStreamOperator<T> returns(TypeInformation<T> typeInfo) {
		requireNonNull(typeInfo, "TypeInformation must not be null");

		transformation.setOutputType(typeInfo);
		return this;
	}

	/**
	 * Adds a type information hint about the return type of this operator.
	 *
	 * <p>Type hints are important in cases where the Java compiler throws away generic type
	 * information necessary for efficient execution.
	 *
	 * <p>This method takes a type information string that will be parsed. A type information string
	 * can contain the following types:
	 *
	 * <ul>
	 * <li>Basic types such as <code>Integer</code>, <code>String</code>, etc.
	 * <li>Basic type arrays such as <code>Integer[]</code>,
	 * <code>String[]</code>, etc.
	 * <li>Tuple types such as <code>Tuple1&lt;TYPE0&gt;</code>,
	 * <code>Tuple2&lt;TYPE0, TYPE1&gt;</code>, etc.</li>
	 * <li>Pojo types such as <code>org.my.MyPojo&lt;myFieldName=TYPE0,myFieldName2=TYPE1&gt;</code>, etc.</li>
	 * <li>Generic types such as <code>java.lang.Class</code>, etc.
	 * <li>Custom type arrays such as <code>org.my.CustomClass[]</code>,
	 * <code>org.my.CustomClass$StaticInnerClass[]</code>, etc.
	 * <li>Value types such as <code>DoubleValue</code>,
	 * <code>StringValue</code>, <code>IntegerValue</code>, etc.</li>
	 * <li>Tuple array types such as <code>Tuple2&lt;TYPE0,TYPE1&gt;[], etc.</code></li>
	 * <li>Writable types such as <code>Writable&lt;org.my.CustomWritable&gt;</code></li>
	 * <li>Enum types such as <code>Enum&lt;org.my.CustomEnum&gt;</code></li>
	 * </ul>
	 *
	 * <p>Example:
	 * <code>"Tuple2&lt;String,Tuple2&lt;Integer,org.my.MyJob$Pojo&lt;word=String&gt;&gt;&gt;"</code>
	 *
	 * @param typeInfoString
	 *            type information string to be parsed
	 * @return This operator with a given return type hint.
	 *
	 * @deprecated Please use {@link #returns(Class)} or {@link #returns(TypeHint)} instead.
	 */
	@Deprecated
	@PublicEvolving
	public SingleOutputStreamOperator<T> returns(String typeInfoString) {
		if (typeInfoString == null) {
			throw new IllegalArgumentException("Type information string must not be null.");
		}
		return returns(TypeInfoParser.<T>parse(typeInfoString));
	}

	// ------------------------------------------------------------------------
	//  Miscellaneous
	// ------------------------------------------------------------------------

	/**
	 * Sets the slot sharing group of this operation. Parallel instances of
	 * operations that are in the same slot sharing group will be co-located in the same
	 * TaskManager slot, if possible.
	 *
	 * <p>Operations inherit the slot sharing group of input operations if all input operations
	 * are in the same slot sharing group and no slot sharing group was explicitly specified.
	 *
	 * <p>Initially an operation is in the default slot sharing group. An operation can be put into
	 * the default group explicitly by setting the slot sharing group to {@code "default"}.
	 *
	 * @param slotSharingGroup The slot sharing group name.
	 */
	@PublicEvolving
	public SingleOutputStreamOperator<T> slotSharingGroup(String slotSharingGroup) {
		transformation.setSlotSharingGroup(slotSharingGroup);
		return this;
	}

	@Override
	public SplitStream<T> split(OutputSelector<T> outputSelector) {
		if (requestedSideOutputs.isEmpty()) {
			wasSplitApplied = true;
			return super.split(outputSelector);
		} else {
			throw new UnsupportedOperationException("getSideOutput() and split() may not be called on the same DataStream. " +
				"As a work-around, please add a no-op map function before the split() call.");
		}
	}

	/**
	 * Gets the {@link DataStream} that contains the elements that are emitted from an operation
	 * into the side output with the given {@link OutputTag}.
	 *
	 * @see org.apache.flink.streaming.api.functions.ProcessFunction.Context#output(OutputTag, Object)
	 */
	public <X> DataStream<X> getSideOutput(OutputTag<X> sideOutputTag) {
		if (wasSplitApplied) {
			throw new UnsupportedOperationException("getSideOutput() and split() may not be called on the same DataStream. " +
				"As a work-around, please add a no-op map function before the split() call.");
		}

		sideOutputTag = clean(requireNonNull(sideOutputTag));

		// make a defensive copy
		sideOutputTag = new OutputTag<X>(sideOutputTag.getId(), sideOutputTag.getTypeInfo());

		TypeInformation<?> type = requestedSideOutputs.get(sideOutputTag);
		if (type != null && !type.equals(sideOutputTag.getTypeInfo())) {
			throw new UnsupportedOperationException("A side output with a matching id was " +
					"already requested with a different type. This is not allowed, side output " +
					"ids need to be unique.");
		}

		requestedSideOutputs.put(sideOutputTag, sideOutputTag.getTypeInfo());

		SideOutputTransformation<X> sideOutputTransformation = new SideOutputTransformation<>(this.getTransformation(), sideOutputTag);
		return new DataStream<>(this.getExecutionEnvironment(), sideOutputTransformation);
	}

	// ------------------------------------------------------------------------
	//  Configuration
	// ------------------------------------------------------------------------

	/**
	 * Sets the value of the given option for the operator.
	 *
	 * @param key The option to be updated.
	 * @param value The value of the option to be updated.
	 */
	@PublicEvolving
	public SingleOutputStreamOperator<T> setConfigItem(ConfigOption<String> key, String value) {
		transformation.getCustomConfiguration().setString(key, value);
		return this;
	}

	/**
	 * Sets the value of the given option for the operator.
	 *
	 * @param key The name of the option to be updated.
	 * @param value The value of the option to be updated.
	 */
	public SingleOutputStreamOperator<T> setConfigItem(String key, String value) {
		transformation.getCustomConfiguration().setString(key, value);
		return this;
	}
}
