/* Copyright 2018 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
=======================================================================*/

// This class has been generated, DO NOT EDIT!

package org.tensorflow.op.tpu;

import org.tensorflow.Operation;
import org.tensorflow.OperationBuilder;
import org.tensorflow.Output;
import org.tensorflow.op.PrimitiveOp;
import org.tensorflow.op.Scope;
import org.tensorflow.types.TFloat32;

/**
 * Retrieve RMSProp embedding parameters.
 * <p>
 * An op that retrieves optimization parameters from embedding to host
 * memory. Must be preceded by a ConfigureTPUEmbeddingHost op that sets up
 * the correct embedding table configuration. For example, this op is
 * used to retrieve updated parameters before saving a checkpoint.
 */
public final class RetrieveTPUEmbeddingRMSPropParameters extends PrimitiveOp {
  
  /**
   * Optional attributes for {@link org.tensorflow.op.tpu.RetrieveTPUEmbeddingRMSPropParameters}
   */
  public static class Options {
    
    /**
     * @param tableId 
     */
    public Options tableId(Long tableId) {
      this.tableId = tableId;
      return this;
    }
    
    /**
     * @param tableName 
     */
    public Options tableName(String tableName) {
      this.tableName = tableName;
      return this;
    }
    
    /**
     * @param config 
     */
    public Options config(String config) {
      this.config = config;
      return this;
    }
    
    private Long tableId;
    private String tableName;
    private String config;
    
    private Options() {
    }
  }
  
  /**
   * Factory method to create a class wrapping a new RetrieveTPUEmbeddingRMSPropParameters operation.
   * 
   * @param scope current scope
   * @param numShards 
   * @param shardId 
   * @param options carries optional attributes values
   * @return a new instance of RetrieveTPUEmbeddingRMSPropParameters
   */
  public static RetrieveTPUEmbeddingRMSPropParameters create(Scope scope, Long numShards, Long shardId, Options... options) {
    OperationBuilder opBuilder = scope.env().opBuilder("RetrieveTPUEmbeddingRMSPropParameters", scope.makeOpName("RetrieveTPUEmbeddingRMSPropParameters"));
    opBuilder = scope.applyControlDependencies(opBuilder);
    opBuilder.setAttr("num_shards", numShards);
    opBuilder.setAttr("shard_id", shardId);
    if (options != null) {
      for (Options opts : options) {
        if (opts.tableId != null) {
          opBuilder.setAttr("table_id", opts.tableId);
        }
        if (opts.tableName != null) {
          opBuilder.setAttr("table_name", opts.tableName);
        }
        if (opts.config != null) {
          opBuilder.setAttr("config", opts.config);
        }
      }
    }
    return new RetrieveTPUEmbeddingRMSPropParameters(opBuilder.build());
  }
  
  /**
   * @param tableId 
   */
  public static Options tableId(Long tableId) {
    return new Options().tableId(tableId);
  }
  
  /**
   * @param tableName 
   */
  public static Options tableName(String tableName) {
    return new Options().tableName(tableName);
  }
  
  /**
   * @param config 
   */
  public static Options config(String config) {
    return new Options().config(config);
  }
  
  /**
   * Parameter parameters updated by the RMSProp optimization algorithm.
   */
  public Output<TFloat32> parameters() {
    return parameters;
  }
  
  /**
   * Parameter ms updated by the RMSProp optimization algorithm.
   */
  public Output<TFloat32> ms() {
    return ms;
  }
  
  /**
   * Parameter mom updated by the RMSProp optimization algorithm.
   */
  public Output<TFloat32> mom() {
    return mom;
  }
  
  private Output<TFloat32> parameters;
  private Output<TFloat32> ms;
  private Output<TFloat32> mom;
  
  private RetrieveTPUEmbeddingRMSPropParameters(Operation operation) {
    super(operation);
    int outputIdx = 0;
    parameters = operation.output(outputIdx++);
    ms = operation.output(outputIdx++);
    mom = operation.output(outputIdx++);
  }
}
