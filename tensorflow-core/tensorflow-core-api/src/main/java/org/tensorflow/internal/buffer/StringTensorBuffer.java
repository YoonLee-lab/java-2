/*
 *  Copyright 2019 The TensorFlow Authors. All Rights Reserved.
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
 *  =======================================================================
 */

package org.tensorflow.internal.buffer;

import java.nio.ReadOnlyBufferException;
import java.util.function.Function;
import org.tensorflow.tools.buffer.ByteDataBuffer;
import org.tensorflow.tools.buffer.DataBuffer;
import org.tensorflow.tools.buffer.LongDataBuffer;
import org.tensorflow.tools.buffer.impl.AbstractDataBuffer;
import org.tensorflow.tools.buffer.impl.Validator;
import org.tensorflow.tools.ndarray.NdArray;

/**
 * Buffer for storing string tensor data.
 *
 * <p>The values are stored a sequence of bytes, with their length and offset in the tensor.
 * Given a string tensor of n elements, data is stored in the buffer as followed:
 * <pre>
 * off<sub>0</sub>, off<sub>1</sub>, ..., off<sub>n-1</sub>, len<sub>0</sub>, bytes<sub>0</sub>, len<sub>1</sub>, bytes<sub>1</sub>, ..., len<sub>n-1</sub>, bytes<sub>n-1</sub>
 * </pre>
 *
 * <p>Each offset is a 64-bit integer that indicates the location of the string in the tensor, i.e.
 * the position of the first byte of its length value.
 *
 * <p>The length of the string, which is the number of bytes in the sequence, is encoded as a varint
 * and is one byte long or more depending on its value.
 *
 * <p>The string bytes starts right after the varint length value.
 *
 * <p>The data of the buffer must be initialized only once, by calling {@link #init(NdArray, Function)},
 * and the buffer must have been allocated with enough space (use {@link #computeSize(NdArray, Function)}
 * priory to know exactly how many bytes are required to store the data).
 *
 * <p>After its data has been initialized, the buffer is read-only as it is not possible to change
 * safely a value without reinitializing the whole data.
 */
public class StringTensorBuffer extends AbstractDataBuffer<byte[]> {

  /**
   * Computes how many bytes are required to store the given data in a string buffer.
   *
   * @param data data to store eventually by calling {@link #init(NdArray, Function)}
   * @param getBytes method that converts one value of the data into a sequence of bytes
   * @return number of bytes required to store the data.
   */
  public static <T> long computeSize(NdArray<T> data, Function<T, byte[]> getBytes) {
    // reserve space to store 64-bit offsets
    long size = data.size() * Long.BYTES;

    // reserve space to store length and data of each values
    for (NdArray<T> scalar : data.scalars()) {
      byte[] elementBytes = getBytes.apply(scalar.getObject());
      size += elementBytes.length + StringTensorBuffer.varintLength(elementBytes.length);
    }
    return size;
  }

  /**
   * Initialize the data of this buffer.
   *
   * <p>While it is not enforced programmatically, it is mandatory that this method is called only
   * once after the creation of the buffer. The buffer must have been allocated according to the
   * same set of data, calling {@link #computeSize(NdArray, Function)} priory to make sure there is
   * enough space to store it.
   *
   * @param data data to store
   * @param getBytes method that converts one value of the data into a sequence of bytes
   */
  public <T> void init(NdArray<T> data, Function<T, byte[]> getBytes) {
    InitDataWriter writer = new InitDataWriter();
    for (NdArray<T> scalar : data.scalars()) {
      writer.writeNext(getBytes.apply(scalar.getObject()));
    }
  }

  @Override
  public long size() {
    return offsets.size();
  }

  @Override
  public byte[] getObject(long index) {
    Validator.getArgs(this, index);
    long offset = offsets.getLong(index);

    // Read string length as a varint from the given offset
    byte b;
    int pos = 0;
    int length = 0;
    do {
      b = data.getByte(offset++);
      length |= (b & 0x7F) << pos++;
    } while ((b & 0x80) != 0);

    // Read string of the given length
    byte[] bytes = new byte[length];
    data.offset(offset).read(bytes);
    return bytes;
  }

  @Override
  public DataBuffer<byte[]> setObject(byte[] values, long index) {
    throw new ReadOnlyBufferException();
  }

  @Override
  public boolean isReadOnly() {
    return true;
  }

  @Override
  public DataBuffer<byte[]> copyTo(DataBuffer<byte[]> dst, long size) {
    if (size == size() && dst instanceof StringTensorBuffer) {
      StringTensorBuffer tensorDst = (StringTensorBuffer) dst;
      if (offsets.size() != size || data.size() != size) {
        throw new IllegalArgumentException(
            "Cannot copy string tensor data to another tensor of a different size");
      }
      offsets.copyTo(tensorDst.offsets, size);
      data.copyTo(tensorDst.data, size);
    } else {
      slowCopyTo(dst, size);
    }
    return this;
  }

  @Override
  public DataBuffer<byte[]> offset(long index) {
    return new StringTensorBuffer(offsets.offset(index), data.offset(offsets.getLong(index)));
  }

  @Override
  public DataBuffer<byte[]> narrow(long size) {
    return new StringTensorBuffer(offsets.narrow(size), data.narrow(offsets.getLong(size)));
  }

  StringTensorBuffer(LongDataBuffer offsets, ByteDataBuffer data) {
    this.offsets = offsets;
    this.data = data;
  }

  private class InitDataWriter {
    long offsetIndex = 0;
    long dataIndex = 0;

    void writeNext(byte[] bytes) {
      offsets.setLong(dataIndex, offsetIndex++);

      // Encode string length as a varint first
      int v = bytes.length;
      while (v >= 0x80) {
        data.setByte((byte) ((v & 0x7F) | 0x80), dataIndex++);
        v >>= 7;
      }
      data.setByte((byte) v, dataIndex++);

      // Then write string data bytes
      data.offset(dataIndex).write(bytes);
      dataIndex += bytes.length;
    }
  }

  private static int varintLength(int length) {
    int len = 1;
    while (length >= 0x80) {
      length >>= 7;
      len++;
    }
    return len;
  }

  private final LongDataBuffer offsets;
  private final ByteDataBuffer data;
}
