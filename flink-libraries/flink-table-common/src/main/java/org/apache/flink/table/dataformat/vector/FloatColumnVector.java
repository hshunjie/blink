/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.table.dataformat.vector;

/**
 * This class represents a nullable double precision floating point column vector.
 * This class will be used for operations on all floating point float types.
 *
 * <p>The vector[] field is public by design for high-performance access in the inner
 * loop of query execution.
 */
public class FloatColumnVector extends ColumnVector {
	private static final long serialVersionUID = 8928878923550041110L;

	public float[] vector;

	/**
	 * Don't use this except for testing purposes.
	 *
	 * @param len the number of rows
	 */
	public FloatColumnVector(int len) {
		super(len);
		vector = new float[len];
	}

	@Override
	public Object get(int index) {
		return vector[index];
	}

	// Copy the current object contents into the output. Only copy selected entries,
	// as indicated by selectedInUse and the sel array.
	@Override
	public void copySelected(boolean selectedInUse, int[] sel, int size, ColumnVector output) {

		// Output has nulls if and only if input has nulls.
		output.noNulls = noNulls;

		// Copy data values over
		if (selectedInUse) {
			for (int j = 0; j < size; j++) {
				int i = sel[j];
				((FloatColumnVector) output).vector[i] = vector[i];
			}
		} else {
			System.arraycopy(vector, 0, ((FloatColumnVector) output).vector, 0, size);
		}

		// Copy nulls over if needed
		super.copySelected(selectedInUse, sel, size, output);
	}

	@Override
	public void setElement(int outElementNum, int inputElementNum, ColumnVector inputVector) {
		if (inputVector.noNulls || !inputVector.isNull[inputElementNum]) {
			isNull[outElementNum] = false;
			vector[outElementNum] =
					((FloatColumnVector) inputVector).vector[inputElementNum];
		} else {
			isNull[outElementNum] = true;
			noNulls = false;
		}
	}

	@Override
	public void shallowCopyTo(ColumnVector otherCv) {
		FloatColumnVector other = (FloatColumnVector) otherCv;
		super.shallowCopyTo(other);
		other.vector = vector;
	}
}
