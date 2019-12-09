/*
 Copyright 2019 The TensorFlow Authors. All Rights Reserved.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 =======================================================================
 */
package org.tensorflow.tools.ndarray;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.tensorflow.tools.ndarray.NdArrays.scalarOf;
import static org.tensorflow.tools.ndarray.NdArrays.vectorOfObjects;
import static org.tensorflow.tools.ndarray.index.Indices.all;
import static org.tensorflow.tools.ndarray.index.Indices.at;
import static org.tensorflow.tools.ndarray.index.Indices.even;
import static org.tensorflow.tools.ndarray.index.Indices.flip;
import static org.tensorflow.tools.ndarray.index.Indices.from;
import static org.tensorflow.tools.ndarray.index.Indices.odd;
import static org.tensorflow.tools.ndarray.index.Indices.range;
import static org.tensorflow.tools.ndarray.index.Indices.seq;
import static org.tensorflow.tools.ndarray.index.Indices.to;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.LongStream;
import org.junit.Test;
import org.tensorflow.tools.Shape;
import org.tensorflow.tools.buffer.DataBuffer;
import org.tensorflow.tools.ndarray.IllegalRankException;
import org.tensorflow.tools.ndarray.LongNdArray;
import org.tensorflow.tools.ndarray.NdArray;
import org.tensorflow.tools.ndarray.NdArrays;

public abstract class NdArrayTestBase<T> {

  protected abstract NdArray<T> allocate(Shape shape);

  protected abstract DataBuffer<T> allocateBuffer(long size);

  protected abstract T valueOf(Long val);

  protected T zeroOrNull() {
    return valueOf(0L);
  }

  @Test
  public void shapeAndSizes() {
    Shape scalarShape = Shape.scalar();
    NdArray<T> scalar = allocate(scalarShape);
    assertEquals(scalarShape, scalar.shape());
    assertEquals(0, scalar.rank());
    assertEquals(scalarShape, Shape.make());

    Shape vectorShape = Shape.make(10);
    NdArray<T> vector = allocate(vectorShape);
    assertEquals(vectorShape, vector.shape());
    assertEquals(1, vector.rank());
  }

  @Test
  public void setAndGetValues() {
    NdArray<T> matrix = allocate(Shape.make(5, 4));
    assertEquals(zeroOrNull(), matrix.getObject(3, 3));

    matrix.setObject(valueOf(10L), 3, 3);
    assertEquals(valueOf(10L), matrix.getObject(3, 3));
    try {
      matrix.setObject(valueOf(10L), 3, 4);
      fail();
    } catch (IndexOutOfBoundsException e) {
      // as expected
    }
    try {
      matrix.setObject(valueOf(10L), -1, 3);
      fail();
    } catch (IndexOutOfBoundsException e) {
      // as expected
    }
    try {
      matrix.getObject(3);
      fail();
    } catch (IllegalRankException e) {
      // as expected
    }
    try {
      matrix.setObject(valueOf(10L), 3);
      fail();
    } catch (IllegalRankException e) {
      // as expected
    }

    NdArray<T> matrix2 = allocate(Shape.make(3, 2))
        .set(vectorOfObjects(valueOf(1L), valueOf(2L)), 0)
        .set(vectorOfObjects(valueOf(3L), valueOf(4L)), 1)
        .setObject(valueOf(5L), 2, 0)
        .setObject(valueOf(6L), 2, 1);

    assertEquals(valueOf(1L), matrix2.getObject(0, 0));
    assertEquals(valueOf(2L), matrix2.getObject(0, 1));
    assertEquals(valueOf(3L), matrix2.getObject(1, 0));
    assertEquals(valueOf(4L), matrix2.getObject(1, 1));
    assertEquals(valueOf(5L), matrix2.getObject(2, 0));
    assertEquals(valueOf(6L), matrix2.getObject(2, 1));
  }

  @Test
  public void iterateElements() {
    NdArray<T> matrix3d = allocate(Shape.make(5, 4, 5));

    matrix3d.scalars().forEachIndexed((coords, scalar) -> {
      scalar.setObject(valueOf(coords[2]));
    });

    assertEquals(valueOf(0L), matrix3d.getObject(0, 0, 0));
    assertEquals(valueOf(1L), matrix3d.getObject(0, 0, 1));
    assertEquals(valueOf(4L), matrix3d.getObject(0, 0, 4));
    assertEquals(valueOf(2L), matrix3d.getObject(0, 1, 2));

    matrix3d.elements(1).forEach(vector -> {
      vector.set(vectorOfObjects(valueOf(5L), valueOf(6L), valueOf(7L), valueOf(8L), valueOf(9L)));
    });

    assertEquals(valueOf(5L), matrix3d.getObject(0, 0, 0));
    assertEquals(valueOf(6L), matrix3d.getObject(0, 0, 1));
    assertEquals(valueOf(9L), matrix3d.getObject(0, 0, 4));
    assertEquals(valueOf(7L), matrix3d.getObject(0, 1, 2));

    AtomicLong value = new AtomicLong();
    matrix3d.elements(0).forEach(matrix -> {
      assertEquals(2L, matrix.shape().numDimensions());
      assertEquals(4L, matrix.shape().size(0));
      assertEquals(5L, matrix.shape().size(1));

      matrix.elements(0).forEach(vector -> {
        assertEquals(1L, vector.shape().numDimensions()) ;
        assertEquals(5L, vector.shape().size(0));

        vector.scalars().forEach(scalar -> {
          assertEquals(0L, scalar.shape().numDimensions()) ;
          scalar.setObject(valueOf(value.getAndIncrement()));
          try {
            scalar.elements(0);
            fail();
          } catch (IllegalArgumentException e) {
            // as expected
          }
        });
      });
    });
    assertEquals(valueOf(0L), matrix3d.getObject(0, 0, 0));
    assertEquals(valueOf(5L), matrix3d.getObject(0, 1, 0));
    assertEquals(valueOf(9L), matrix3d.getObject(0, 1, 4));
    assertEquals(valueOf(20L), matrix3d.getObject(1, 0, 0));
    assertEquals(valueOf(25L), matrix3d.getObject(1, 1, 0));
    assertEquals(valueOf(99L), matrix3d.getObject(4, 3, 4));
  }

  @Test
  public void slices() {
    NdArray<T> matrix3d = allocate(Shape.make(5, 4, 5));
    
    T val100 = valueOf(100L);
    matrix3d.setObject(val100, 1, 0, 0);
    T val101 = valueOf(101L);
    matrix3d.setObject(val101, 1, 0, 1);

    // Vector (1,0,*)
    NdArray<T> vector10X = matrix3d.get(1, 0);
    assertEquals(Shape.make(5), vector10X.shape());
    assertEquals(val100, vector10X.getObject(0));
    assertEquals(val101, vector10X.getObject(1));

    T val102 = valueOf(102L);
    vector10X.setObject(val102, 2);
    assertEquals(val102, vector10X.getObject(2));
    assertEquals(val102, matrix3d.getObject(1, 0, 2));

    // Vector (*,0,0)
    NdArray<T> vectorX00 = matrix3d.slice(all(), at(0), at(0));
    assertEquals(Shape.make(5), vectorX00.shape());
    assertEquals(val100, vectorX00.getObject(1));
    T val200 = valueOf(200L);
    vectorX00.setObject(val200, 2);
    assertEquals(val200, vectorX00.getObject(2));
    assertEquals(val200, matrix3d.getObject(2, 0, 0));

    // Vector (1,0,[2,0])
    NdArray<T> vector10_20 = matrix3d.slice(at(1), at(0), seq(2, 0));
    assertEquals(vector10_20.shape(), Shape.make(2));
    assertEquals(val102, vector10_20.getObject(0));
    assertEquals(val100, vector10_20.getObject(1));

    // Vector (1,0,[even])
    NdArray<T> vector10_even = matrix3d.slice(at(1), at(0), even());
    assertEquals(vector10_even.shape(), Shape.make(3));
    assertEquals(val100, vector10_even.getObject(0));
    assertEquals(val102, vector10_even.getObject(1));

    // Vector ([odd]) from vector (1,0,[even])
    NdArray<T> vector10_even_odd = vector10_even.slice(odd());
    assertEquals(vector10_even_odd.shape(), Shape.make(1));
    assertEquals(val102, vector10_even_odd.getObject(0));

    // Vector (1,0,[flip])
    NdArray<T> vector10_flip = matrix3d.slice(at(1), at(0), flip());
    assertEquals(vector10_flip.shape(), Shape.make(5));
    assertEquals(val100, vector10_flip.getObject(4));
    assertEquals(val101, vector10_flip.getObject(3));

    // Vector (1,0,[from 1]) from vector (1,0,*)
    NdArray<T> vector10_1toX = vector10X.slice(from(1));
    assertEquals(vector10_1toX.shape(), Shape.make(4));
    assertEquals(val101, vector10_1toX.getObject(0));
    assertEquals(val102, vector10_1toX.getObject(1));

    // Vector (1,0,[to 1]) from vector (1,0,*)
    NdArray<T> vector10_Xto1 = vector10X.slice(to(2));
    assertEquals(vector10_Xto1.shape(), Shape.make(2));
    assertEquals(val100, vector10_Xto1.getObject(0));
    assertEquals(val101, vector10_Xto1.getObject(1));

    // Vector (1,0,[1 to 3])
    NdArray<T> vector10_1to3 = matrix3d.slice(at(1), at(0), range(1, 3));
    assertEquals(vector10_1to3.shape(), Shape.make(2));
    assertEquals(val101, vector10_1to3.getObject(0));
    assertEquals(val102, vector10_1to3.getObject(1));

    // Scalar (1,0,0) from vector (1,0,*)
    NdArray<T> scalar100 = vector10X.get(0);
    assertEquals(Shape.make(), scalar100.shape());
    assertEquals(val100, scalar100.getObject());

    // Slice scalar (1,0,z)
    LongNdArray z = NdArrays.scalarOf(2L);
    NdArray<T> scalar102 = matrix3d.slice(at(1), at(0), at(z));
    assertEquals(scalar102.shape(), Shape.make());
    assertEquals(val102, scalar102.getObject());

    // Slicing the 3D matrix so we only keep the first element of the second dimension
    NdArray<T> matrix_X0Z = matrix3d.slice(all(), at(0));
    assertEquals(2, matrix_X0Z.rank());
    assertEquals(Shape.make(5, 5), matrix_X0Z.shape());
    assertEquals(val100, matrix_X0Z.getObject(1, 0));
    assertEquals(val101, matrix_X0Z.getObject(1, 1));
    assertEquals(val200, matrix_X0Z.getObject(2, 0));
  }

  @Test
  public void writeAndReadWithBuffers() {
    DataBuffer<T> buffer = allocateBuffer(15L);
    for (long val = 0L; val < buffer.size(); ++val) {
      buffer.setObject(valueOf(val), val);
    }
    NdArray<T> matrix = allocate(Shape.make(3, 5));
    matrix.write(buffer);
    assertEquals(valueOf(0L), matrix.getObject(0, 0));
    assertEquals(valueOf(4L), matrix.getObject(0, 4));
    assertEquals(valueOf(5L), matrix.getObject(1, 0));
    assertEquals(valueOf(10L), matrix.getObject(2, 0));
    assertEquals(valueOf(14L), matrix.getObject(2, 4));

    matrix.setObject(valueOf(100L), 1, 0);
    matrix.read(buffer);
    assertEquals(valueOf(0L), buffer.getObject(0));
    assertEquals(valueOf(4L), buffer.getObject(4));
    assertEquals(valueOf(100L), buffer.getObject(5));
    assertEquals(valueOf(10L), buffer.getObject(10));
    assertEquals(valueOf(14L), buffer.getObject(14));
  }

  @Test
  public void ndArrayCopies() {
    NdArray<T> matrixA = allocate(Shape.make(3, 5));

    AtomicLong value = new AtomicLong();
    matrixA.scalars().forEach(e -> e.setObject(valueOf(value.getAndIncrement())));

    NdArray<T> matrixB = allocate(Shape.make(3, 5)).setObject(valueOf(100L), 1, 0);
    matrixA.copyTo(matrixB);
    assertEquals(valueOf(0L), matrixB.getObject(0, 0));
    assertEquals(valueOf(4L), matrixB.getObject(0, 4));
    assertEquals(valueOf(5L), matrixB.getObject(1, 0));
    assertEquals(valueOf(10L), matrixB.getObject(2, 0));
    assertEquals(valueOf(14L), matrixB.getObject(2, 4));

    NdArray<T> matrixC = allocate(Shape.make(3, 4));
    try {
      matrixA.copyTo(matrixC);
      fail();
    } catch (IllegalArgumentException e) {
      // as expected
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  public void writeAndReadWithArrays() {
    T[] values = (T[])LongStream.range(0L, 16L).boxed().map(this::valueOf).toArray();

    NdArray<T> matrix = allocate(Shape.make(3, 4));
    matrix.write(values);
    assertEquals(valueOf(0L), matrix.getObject(0, 0));
    assertEquals(valueOf(3L), matrix.getObject(0, 3));
    assertEquals(valueOf(4L), matrix.getObject(1, 0));
    assertEquals(valueOf(11L), matrix.getObject(2, 3));

    matrix.write(values, 4);
    assertEquals(valueOf(4L), matrix.getObject(0, 0));
    assertEquals(valueOf(7L), matrix.getObject(0, 3));
    assertEquals(valueOf(8L), matrix.getObject(1, 0));
    assertEquals(valueOf(15L), matrix.getObject(2, 3));

    matrix.setObject(valueOf(100L), 1, 0);
    matrix.read(values, 2);
    assertEquals(valueOf(4L), values[2]);
    assertEquals(valueOf(7L), values[5]);
    assertEquals(valueOf(100L), values[6]);
    assertEquals(valueOf(15L), values[13]);
    assertEquals(valueOf(15L), values[15]);

    matrix.read(values);
    assertEquals(valueOf(4L), values[0]);
    assertEquals(valueOf(7L), values[3]);
    assertEquals(valueOf(100L), values[4]);
    assertEquals(valueOf(15L), values[11]);
    assertEquals(valueOf(15L), values[13]);
    assertEquals(valueOf(15L), values[15]);

    try {
      matrix.write((T[])LongStream.range(0L, 4L).boxed().map(this::valueOf).toArray());
      fail();
    } catch (BufferUnderflowException e) {
      // as expected
    }
    try {
      matrix.write(values, values.length);
      fail();
    } catch (BufferUnderflowException e) {
      // as expected
    }
    try {
      matrix.write(values, -1);
      fail();
    } catch (IllegalArgumentException e) {
      // as expected
    }
    try {
      matrix.write(values, values.length + 1);
      fail();
    } catch (IllegalArgumentException e) {
      // as expected
    }
    try {
      matrix.read((T[])LongStream.range(0L, 4L).boxed().map(this::valueOf).toArray());
      fail();
    } catch (BufferOverflowException e) {
      // as expected
    }
    try {
      matrix.read(values, values.length);
      fail();
    } catch (BufferOverflowException e) {
      // as expected
    }
    try {
      matrix.read(values, -1);
      fail();
    } catch (IllegalArgumentException e) {
      // as expected
    }
    try {
      matrix.read(values, values.length + 1);
      fail();
    } catch (IllegalArgumentException e) {
      // as expected
    }
  }
}