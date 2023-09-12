/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.sysds.test.functions.builtin.part1;

import org.apache.sysds.common.Types.ExecMode;
import org.apache.sysds.common.Types.ExecType;
import org.apache.sysds.runtime.matrix.data.MatrixValue;
import org.apache.sysds.test.AutomatedTestBase;
import org.apache.sysds.test.TestConfiguration;
import org.apache.sysds.test.TestUtils;
import org.junit.Test;

import java.util.HashMap;
import java.util.Random;
// if A should be generated one row at a time
//import java.util.stream.Stream;
//import java.util.stream.DoubleStream;

public class BuiltinImageCutoutLinTest extends AutomatedTestBase {
    private final static String TEST_NAME = "image_cutout_linearized";
    private final static String TEST_DIR = "functions/builtin/";
    private final static String TEST_CLASS_DIR = TEST_DIR + BuiltinImageCutoutLinTest.class.getSimpleName() + "/";

    private final static double eps = 1e-10;
    private final static double spSparse = 0.1;
    private final static double spDense = 0.9;
    private final static Random random = new Random();

    @Override
    public void setUp() {
        addTestConfiguration(TEST_NAME, new TestConfiguration(TEST_CLASS_DIR, TEST_NAME, new String[] { "B" }));
    }

    @Test
    public void testImageTranslateMatrixDenseCP() {
        runImageCutoutLinTest(false, ExecType.CP);
    }

    @Test
    public void testImageTranslateMatrixSparseCP() {
        runImageCutoutLinTest(true, ExecType.CP);
    }

    @Test
    public void testImageTranslateMatrixDenseSP() {
        runImageCutoutLinTest(false, ExecType.SPARK);
    }

    @Test
    public void testImageTranslateMatrixSparseSP() {
        runImageCutoutLinTest(false, ExecType.SPARK);
    }

    private void runImageCutoutLinTest(boolean sparse, ExecType instType) {
        ExecMode platformOld = setExecMode(instType);
        disableOutAndExpectedDeletion();

        setOutputBuffering(true);

        int s_rows = random.nextInt(100) + 1;
        int s_cols = random.nextInt(100) + 1;
        int x = random.nextInt(s_cols);
        int y = random.nextInt(s_rows);
        int width = random.nextInt(s_cols - x) + 1;
        int height = random.nextInt(s_rows - y) + 1;
        int fill_color = random.nextInt(256);
        int n_imgs = random.nextInt(100) + 1;

        try {
            loadTestConfiguration(getTestConfiguration(TEST_NAME));
            double sparsity = sparse ? spSparse : spDense;

            String HOME = SCRIPT_DIR + TEST_DIR;
            fullDMLScriptName = HOME + TEST_NAME + ".dml";
            programArgs = new String[] { "-nvargs", "in_file=" + input("A"), "out_file=" + output("B"),
                    "width=" + (s_cols * s_rows),
                    "height=" + n_imgs, "x=" + (x + 1), "y=" + (y + 1), "w=" + width, "h=" + height,
                    "fill_color=" + fill_color, "s_cols=" + s_cols, "s_rows=" + s_rows };

            // overall sparsity of the dataset or a single image/row?
            double[][] A = getRandomMatrix(n_imgs, s_cols * s_rows, 0, 255, sparsity, 7);
            /*
             * double[][] A = new double[n_imgs][s_cols*s_rows];
             * for (int i = 0; i < n_imgs; i++) {
             * double[][] matrix = getRandomMatrix(s_cols, s_rows, 0, 255, sparsity, 7);
             * double[] row = Stream.of(matrix).flatMapToDouble(DoubleStream::of).toArray();
             * A[i] = row;
             * }
             */

            writeInputMatrixWithMTD("A", A, true);

            double[][] ref = new double[n_imgs][s_cols * s_rows];
            for (int i = 0; i < n_imgs; i++) {
                for (int j = 0; j < s_cols * s_rows; j++) {
                    ref[i][j] = A[i][j];
                    if (y <= (int) Math.floor(j / s_cols) && (int) Math.floor(j / s_cols) < y + height
                            && x <= (j % s_cols) && (j % s_cols) < x + width) {
                        ref[i][j] = fill_color;
                    }
                }
            }

            runTest(true, false, null, -1);

            HashMap<MatrixValue.CellIndex, Double> dmlfile = readDMLMatrixFromOutputDir("B");
            double[][] dml_res = TestUtils.convertHashMapToDoubleArray(dmlfile, n_imgs, (s_cols * s_rows));

            writeInputMatrixWithMTD("ref", ref, true);
            TestUtils.compareMatrices(ref, dml_res, eps, "Java vs. DML");

        } finally {
            rtplatform = platformOld;
        }
    }
}
