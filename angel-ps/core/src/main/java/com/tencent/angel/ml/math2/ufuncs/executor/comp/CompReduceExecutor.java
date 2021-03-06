/*
 * Tencent is pleased to support the open source community by making Angel available.
 *
 * Copyright (C) 2017-2018 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/Apache-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 */


package com.tencent.angel.ml.math2.ufuncs.executor.comp;

import com.tencent.angel.exception.AngelException;
import com.tencent.angel.ml.math2.utils.ForkJoinUtils;
import com.tencent.angel.ml.math2.utils.UnionEle;
import com.tencent.angel.ml.math2.vector.CompIntDoubleVector;
import com.tencent.angel.ml.math2.vector.CompIntFloatVector;
import com.tencent.angel.ml.math2.vector.CompIntIntVector;
import com.tencent.angel.ml.math2.vector.CompIntLongVector;
import com.tencent.angel.ml.math2.vector.CompLongDoubleVector;
import com.tencent.angel.ml.math2.vector.CompLongFloatVector;
import com.tencent.angel.ml.math2.vector.CompLongIntVector;
import com.tencent.angel.ml.math2.vector.CompLongLongVector;
import com.tencent.angel.ml.math2.vector.ComponentVector;
import com.tencent.angel.ml.math2.vector.IntDoubleVector;
import com.tencent.angel.ml.math2.vector.IntFloatVector;
import com.tencent.angel.ml.math2.vector.IntIntVector;
import com.tencent.angel.ml.math2.vector.IntLongVector;
import com.tencent.angel.ml.math2.vector.LongDoubleVector;
import com.tencent.angel.ml.math2.vector.LongFloatVector;
import com.tencent.angel.ml.math2.vector.LongIntVector;
import com.tencent.angel.ml.math2.vector.LongLongVector;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.RecursiveTask;

public class CompReduceExecutor {

  private static final int THREADS = ForkJoinUtils.getNCores();
  private static ForkJoinPool pool = ForkJoinUtils.getPool();

  public static double apply(ComponentVector vector, ReduceOP op) {
    CompRedExe task = new CompRedExe(vector, op, 0, vector.getNumPartitions() - 1);
    Future<UnionEle> futRes = pool.submit(task);

    try {
      UnionEle tmpRes = futRes.get();
      switch (op) {
        case Avg:
          return tmpRes.getDouble1() / tmpRes.getLong1();
        case Std:
          double avg1 = tmpRes.getDouble1() / tmpRes.getLong1();
          double avg2 = tmpRes.getDouble2() / tmpRes.getLong1();
          return Math.sqrt(avg2 - avg1 * avg1);
        case Norm:
          return Math.sqrt(tmpRes.getDouble2());
        default:
          return tmpRes.getDouble1() + tmpRes.getLong1();
      }
    } catch (InterruptedException | ExecutionException e) {
      e.printStackTrace();
    }

    return Double.NaN;
  }

  private static UnionEle merge(UnionEle left, UnionEle right, ReduceOP op) {
    UnionEle res = new UnionEle();
    switch (op) {
      case Min:
        res.setDouble1(Math.min(left.getDouble1(), right.getDouble1()));
        res.setDouble2(Math.min(left.getDouble2(), right.getDouble2()));
        res.setFloat1(Math.min(left.getFloat1(), right.getFloat1()));
        res.setFloat2(Math.min(left.getFloat2(), right.getFloat2()));
        res.setLong1(Math.min(left.getLong1(), right.getLong1()));
        res.setLong2(Math.min(left.getLong2(), right.getLong2()));
        res.setInt1(Math.min(left.getInt1(), right.getInt1()));
        res.setInt2(Math.min(left.getInt2(), right.getInt2()));
        break;
      case Max:
        res.setDouble1(Math.max(left.getDouble1(), right.getDouble1()));
        res.setDouble2(Math.max(left.getDouble2(), right.getDouble2()));
        res.setFloat1(Math.max(left.getFloat1(), right.getFloat1()));
        res.setFloat2(Math.max(left.getFloat2(), right.getFloat2()));
        res.setLong1(Math.max(left.getLong1(), right.getLong1()));
        res.setLong2(Math.max(left.getLong2(), right.getLong2()));
        res.setInt1(Math.max(left.getInt1(), right.getInt1()));
        res.setInt2(Math.max(left.getInt2(), right.getInt2()));
        break;
      default:
        res.setDouble1(left.getDouble1() + right.getDouble1());
        res.setDouble2(left.getDouble2() + right.getDouble2());
        res.setFloat1(left.getFloat1() + right.getFloat1());
        res.setFloat2(left.getFloat2() + right.getFloat2());
        res.setLong1(left.getLong1() + right.getLong1());
        res.setLong2(left.getLong2() + right.getLong2());
        res.setInt1(left.getInt1() + right.getInt1());
        res.setInt2(left.getInt2() + right.getInt2());
    }

    res.setBool(left.isBool() && right.isBool());

    return res;
  }

  private static UnionEle apply(CompIntDoubleVector v, ReduceOP op, int start, int end) {
    UnionEle res = new UnionEle();

    IntDoubleVector[] parts = v.getPartitions();
    switch (op) {
      case Sum:
        for (int i = start; i <= end; i++) {
          res.setDouble1(res.getDouble1() + parts[i].sum());
        }
        break;
      case Avg:
        for (int i = start; i <= end; i++) {
          res.setDouble1(res.getDouble1() + parts[i].sum());
          res.setLong1(res.getLong1() + parts[i].getDim());
        }
        break;
      case Std:
        for (int i = start; i <= end; i++) {
          res.setDouble1(res.getDouble1() + parts[i].sum());
          double norm = parts[i].norm();
          res.setDouble2(res.getDouble2() + norm * norm);
          res.setLong1(res.getLong1() + parts[i].getDim());
        }
        break;
      case Norm:
        for (int i = start; i <= end; i++) {
          double norm = parts[i].norm();
          res.setDouble2(res.getDouble2() + norm * norm);
        }
        break;
      case Min:
        res.setDouble1(Double.MAX_VALUE);
        for (int i = start; i <= end; i++) {
          res.setDouble1(Math.min(res.getDouble1(), parts[i].min()));
        }
        break;
      case Max:
        res.setDouble1(Double.MIN_VALUE);
        for (int i = start; i <= end; i++) {
          res.setDouble1(Math.max(res.getDouble1(), parts[i].max()));
        }
        break;
      case Size:
        for (int i = start; i <= end; i++) {
          res.setLong1(res.getLong1() + parts[i].size());
        }
        break;
      case Numzeros:
        for (int i = start; i <= end; i++) {
          res.setLong1(res.getLong1() + parts[i].numZeros());
        }
        break;
    }

    return res;
  }

  private static UnionEle apply(CompIntFloatVector v, ReduceOP op, int start, int end) {
    UnionEle res = new UnionEle();

    IntFloatVector[] parts = v.getPartitions();
    switch (op) {
      case Sum:
        for (int i = start; i <= end; i++) {
          res.setDouble1(res.getDouble1() + parts[i].sum());
        }
        break;
      case Avg:
        for (int i = start; i <= end; i++) {
          res.setDouble1(res.getDouble1() + parts[i].sum());
          res.setLong1(res.getLong1() + parts[i].getDim());
        }
        break;
      case Std:
        for (int i = start; i <= end; i++) {
          res.setDouble1(res.getDouble1() + parts[i].sum());
          double norm = parts[i].norm();
          res.setDouble2(res.getDouble2() + norm * norm);
          res.setLong1(res.getLong1() + parts[i].getDim());
        }
        break;
      case Norm:
        for (int i = start; i <= end; i++) {
          double norm = parts[i].norm();
          res.setDouble2(res.getDouble2() + norm * norm);
        }
        break;
      case Min:
        res.setDouble1(Double.MAX_VALUE);
        for (int i = start; i <= end; i++) {
          res.setDouble1(Math.min(res.getDouble1(), parts[i].min()));
        }
        break;
      case Max:
        res.setDouble1(Double.MIN_VALUE);
        for (int i = start; i <= end; i++) {
          res.setDouble1(Math.max(res.getDouble1(), parts[i].max()));
        }
        break;
      case Size:
        for (int i = start; i <= end; i++) {
          res.setLong1(res.getLong1() + parts[i].size());
        }
        break;
      case Numzeros:
        for (int i = start; i <= end; i++) {
          res.setLong1(res.getLong1() + parts[i].numZeros());
        }
        break;
    }

    return res;
  }

  private static UnionEle apply(CompIntLongVector v, ReduceOP op, int start, int end) {
    UnionEle res = new UnionEle();

    IntLongVector[] parts = v.getPartitions();
    switch (op) {
      case Sum:
        for (int i = start; i <= end; i++) {
          res.setDouble1(res.getDouble1() + parts[i].sum());
        }
        break;
      case Avg:
        for (int i = start; i <= end; i++) {
          res.setDouble1(res.getDouble1() + parts[i].sum());
          res.setLong1(res.getLong1() + parts[i].getDim());
        }
        break;
      case Std:
        for (int i = start; i <= end; i++) {
          res.setDouble1(res.getDouble1() + parts[i].sum());
          double norm = parts[i].norm();
          res.setDouble2(res.getDouble2() + norm * norm);
          res.setLong1(res.getLong1() + parts[i].getDim());
        }
        break;
      case Norm:
        for (int i = start; i <= end; i++) {
          double norm = parts[i].norm();
          res.setDouble2(res.getDouble2() + norm * norm);
        }
        break;
      case Min:
        res.setDouble1(Double.MAX_VALUE);
        for (int i = start; i <= end; i++) {
          res.setDouble1(Math.min(res.getDouble1(), parts[i].min()));
        }
        break;
      case Max:
        res.setDouble1(Double.MIN_VALUE);
        for (int i = start; i <= end; i++) {
          res.setDouble1(Math.max(res.getDouble1(), parts[i].max()));
        }
        break;
      case Size:
        for (int i = start; i <= end; i++) {
          res.setLong1(res.getLong1() + parts[i].size());
        }
        break;
      case Numzeros:
        for (int i = start; i <= end; i++) {
          res.setLong1(res.getLong1() + parts[i].numZeros());
        }
        break;
    }

    return res;
  }

  private static UnionEle apply(CompIntIntVector v, ReduceOP op, int start, int end) {
    UnionEle res = new UnionEle();

    IntIntVector[] parts = v.getPartitions();
    switch (op) {
      case Sum:
        for (int i = start; i <= end; i++) {
          res.setDouble1(res.getDouble1() + parts[i].sum());
        }
        break;
      case Avg:
        for (int i = start; i <= end; i++) {
          res.setDouble1(res.getDouble1() + parts[i].sum());
          res.setLong1(res.getLong1() + parts[i].getDim());
        }
        break;
      case Std:
        for (int i = start; i <= end; i++) {
          res.setDouble1(res.getDouble1() + parts[i].sum());
          double norm = parts[i].norm();
          res.setDouble2(res.getDouble2() + norm * norm);
          res.setLong1(res.getLong1() + parts[i].getDim());
        }
        break;
      case Norm:
        for (int i = start; i <= end; i++) {
          double norm = parts[i].norm();
          res.setDouble2(res.getDouble2() + norm * norm);
        }
        break;
      case Min:
        res.setDouble1(Double.MAX_VALUE);
        for (int i = start; i <= end; i++) {
          res.setDouble1(Math.min(res.getDouble1(), parts[i].min()));
        }
        break;
      case Max:
        res.setDouble1(Double.MIN_VALUE);
        for (int i = start; i <= end; i++) {
          res.setDouble1(Math.max(res.getDouble1(), parts[i].max()));
        }
        break;
      case Size:
        for (int i = start; i <= end; i++) {
          res.setLong1(res.getLong1() + parts[i].size());
        }
        break;
      case Numzeros:
        for (int i = start; i <= end; i++) {
          res.setLong1(res.getLong1() + parts[i].numZeros());
        }
        break;
    }

    return res;
  }

  private static UnionEle apply(CompLongDoubleVector v, ReduceOP op, int start, int end) {
    UnionEle res = new UnionEle();

    LongDoubleVector[] parts = v.getPartitions();
    switch (op) {
      case Sum:
        for (int i = start; i <= end; i++) {
          res.setDouble1(res.getDouble1() + parts[i].sum());
        }
        break;
      case Avg:
        for (int i = start; i <= end; i++) {
          res.setDouble1(res.getDouble1() + parts[i].sum());
          res.setLong1(res.getLong1() + parts[i].getDim());
        }
        break;
      case Std:
        for (int i = start; i <= end; i++) {
          res.setDouble1(res.getDouble1() + parts[i].sum());
          double norm = parts[i].norm();
          res.setDouble2(res.getDouble2() + norm * norm);
          res.setLong1(res.getLong1() + parts[i].getDim());
        }
        break;
      case Norm:
        for (int i = start; i <= end; i++) {
          double norm = parts[i].norm();
          res.setDouble2(res.getDouble2() + norm * norm);
        }
        break;
      case Min:
        res.setDouble1(Double.MAX_VALUE);
        for (int i = start; i <= end; i++) {
          res.setDouble1(Math.min(res.getDouble1(), parts[i].min()));
        }
        break;
      case Max:
        res.setDouble1(Double.MIN_VALUE);
        for (int i = start; i <= end; i++) {
          res.setDouble1(Math.max(res.getDouble1(), parts[i].max()));
        }
        break;
      case Size:
        for (int i = start; i <= end; i++) {
          res.setLong1(res.getLong1() + parts[i].size());
        }
        break;
      case Numzeros:
        for (int i = start; i <= end; i++) {
          res.setLong1(res.getLong1() + parts[i].numZeros());
        }
        break;
    }

    return res;
  }

  private static UnionEle apply(CompLongFloatVector v, ReduceOP op, int start, int end) {
    UnionEle res = new UnionEle();

    LongFloatVector[] parts = v.getPartitions();
    switch (op) {
      case Sum:
        for (int i = start; i <= end; i++) {
          res.setDouble1(res.getDouble1() + parts[i].sum());
        }
        break;
      case Avg:
        for (int i = start; i <= end; i++) {
          res.setDouble1(res.getDouble1() + parts[i].sum());
          res.setLong1(res.getLong1() + parts[i].getDim());
        }
        break;
      case Std:
        for (int i = start; i <= end; i++) {
          res.setDouble1(res.getDouble1() + parts[i].sum());
          double norm = parts[i].norm();
          res.setDouble2(res.getDouble2() + norm * norm);
          res.setLong1(res.getLong1() + parts[i].getDim());
        }
        break;
      case Norm:
        for (int i = start; i <= end; i++) {
          double norm = parts[i].norm();
          res.setDouble2(res.getDouble2() + norm * norm);
        }
        break;
      case Min:
        res.setDouble1(Double.MAX_VALUE);
        for (int i = start; i <= end; i++) {
          res.setDouble1(Math.min(res.getDouble1(), parts[i].min()));
        }
        break;
      case Max:
        res.setDouble1(Double.MIN_VALUE);
        for (int i = start; i <= end; i++) {
          res.setDouble1(Math.max(res.getDouble1(), parts[i].max()));
        }
        break;
      case Size:
        for (int i = start; i <= end; i++) {
          res.setLong1(res.getLong1() + parts[i].size());
        }
        break;
      case Numzeros:
        for (int i = start; i <= end; i++) {
          res.setLong1(res.getLong1() + parts[i].numZeros());
        }
        break;
    }

    return res;
  }

  private static UnionEle apply(CompLongLongVector v, ReduceOP op, int start, int end) {
    UnionEle res = new UnionEle();

    LongLongVector[] parts = v.getPartitions();
    switch (op) {
      case Sum:
        for (int i = start; i <= end; i++) {
          res.setDouble1(res.getDouble1() + parts[i].sum());
        }
        break;
      case Avg:
        for (int i = start; i <= end; i++) {
          res.setDouble1(res.getDouble1() + parts[i].sum());
          res.setLong1(res.getLong1() + parts[i].getDim());
        }
        break;
      case Std:
        for (int i = start; i <= end; i++) {
          res.setDouble1(res.getDouble1() + parts[i].sum());
          double norm = parts[i].norm();
          res.setDouble2(res.getDouble2() + norm * norm);
          res.setLong1(res.getLong1() + parts[i].getDim());
        }
        break;
      case Norm:
        for (int i = start; i <= end; i++) {
          double norm = parts[i].norm();
          res.setDouble2(res.getDouble2() + norm * norm);
        }
        break;
      case Min:
        res.setDouble1(Double.MAX_VALUE);
        for (int i = start; i <= end; i++) {
          res.setDouble1(Math.min(res.getDouble1(), parts[i].min()));
        }
        break;
      case Max:
        res.setDouble1(Double.MIN_VALUE);
        for (int i = start; i <= end; i++) {
          res.setDouble1(Math.max(res.getDouble1(), parts[i].max()));
        }
        break;
      case Size:
        for (int i = start; i <= end; i++) {
          res.setLong1(res.getLong1() + parts[i].size());
        }
        break;
      case Numzeros:
        for (int i = start; i <= end; i++) {
          res.setLong1(res.getLong1() + parts[i].numZeros());
        }
        break;
    }

    return res;
  }

  private static UnionEle apply(CompLongIntVector v, ReduceOP op, int start, int end) {
    UnionEle res = new UnionEle();

    LongIntVector[] parts = v.getPartitions();
    switch (op) {
      case Sum:
        for (int i = start; i <= end; i++) {
          res.setDouble1(res.getDouble1() + parts[i].sum());
        }
        break;
      case Avg:
        for (int i = start; i <= end; i++) {
          res.setDouble1(res.getDouble1() + parts[i].sum());
          res.setLong1(res.getLong1() + parts[i].getDim());
        }
        break;
      case Std:
        for (int i = start; i <= end; i++) {
          res.setDouble1(res.getDouble1() + parts[i].sum());
          double norm = parts[i].norm();
          res.setDouble2(res.getDouble2() + norm * norm);
          res.setLong1(res.getLong1() + parts[i].getDim());
        }
        break;
      case Norm:
        for (int i = start; i <= end; i++) {
          double norm = parts[i].norm();
          res.setDouble2(res.getDouble2() + norm * norm);
        }
        break;
      case Min:
        res.setDouble1(Double.MAX_VALUE);
        for (int i = start; i <= end; i++) {
          res.setDouble1(Math.min(res.getDouble1(), parts[i].min()));
        }
        break;
      case Max:
        res.setDouble1(Double.MIN_VALUE);
        for (int i = start; i <= end; i++) {
          res.setDouble1(Math.max(res.getDouble1(), parts[i].max()));
        }
        break;
      case Size:
        for (int i = start; i <= end; i++) {
          res.setLong1(res.getLong1() + parts[i].size());
        }
        break;
      case Numzeros:
        for (int i = start; i <= end; i++) {
          res.setLong1(res.getLong1() + parts[i].numZeros());
        }
        break;
    }

    return res;
  }

  public enum ReduceOP {
    Sum, Avg, Std, Norm, Min, Max, Size, Numzeros
  }

  private static class CompRedExe extends RecursiveTask<UnionEle> {

    private ComponentVector v;
    private ReduceOP op;
    private int start, end, threshold;

    public CompRedExe(ComponentVector v, ReduceOP op, int start, int end) {
      assert v != null && op != null;
      this.v = v;
      this.op = op;
      this.start = start;
      this.end = end;
      this.threshold = (v.getNumPartitions() + THREADS - 1) / THREADS;
    }


    @Override
    protected UnionEle compute() {
      boolean canCompute = (end - start) < threshold;

      if (canCompute) {
        if (v instanceof CompIntDoubleVector) {
          return apply((CompIntDoubleVector) v, op, start, end);
        } else if (v instanceof CompIntFloatVector) {
          return apply((CompIntFloatVector) v, op, start, end);
        } else if (v instanceof CompIntLongVector) {
          return apply((CompIntLongVector) v, op, start, end);
        } else if (v instanceof CompIntIntVector) {
          return apply((CompIntIntVector) v, op, start, end);
        } else if (v instanceof CompLongDoubleVector) {
          return apply((CompLongDoubleVector) v, op, start, end);
        } else if (v instanceof CompLongFloatVector) {
          return apply((CompLongFloatVector) v, op, start, end);
        } else if (v instanceof CompLongLongVector) {
          return apply((CompLongLongVector) v, op, start, end);
        } else if (v instanceof CompLongIntVector) {
          return apply((CompLongIntVector) v, op, start, end);
        } else {
          throw new AngelException("");
        }
      } else {
        int middle = (start + end) >> 1;

        CompRedExe left = new CompRedExe(v, op, start, middle);
        CompRedExe right = new CompRedExe(v, op, middle + 1, end);

        left.fork();
        right.fork();

        UnionEle resLeft = left.join();
        UnionEle resRight = right.join();

        return merge(resLeft, resRight, op);
      }
    }
  }


}