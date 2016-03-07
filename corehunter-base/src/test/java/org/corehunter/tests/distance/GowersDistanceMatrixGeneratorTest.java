/*--------------------------------------------------------------*/
/* Licensed to the Apache Software Foundation (ASF) under one   */
/* or more contributor license agreements.  See the NOTICE file */
/* distributed with this work for additional information        */
/* regarding copyright ownership.  The ASF licenses this file   */
/* to you under the Apache License, Version 2.0 (the            */
/* "License"); you may not use this file except in compliance   */
/* with the License.  You may obtain a copy of the License at   */
/*                                                              */
/*   http://www.apache.org/licenses/LICENSE-2.0                 */
/*                                                              */
/* Unless required by applicable law or agreed to in writing,   */
/* software distributed under the License is distributed on an  */
/* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       */
/* KIND, either express or implied.  See the License for the    */
/* specific language governing permissions and limitations      */
/* under the License.                                           */
/*--------------------------------------------------------------*/

package org.corehunter.tests.distance;

import java.io.File;
import java.util.Iterator;
import java.util.List;

import org.corehunter.data.DistanceMatrixData;
import org.corehunter.distance.GowersDistanceMatrixGenerator;
import org.junit.Test;

import uno.informatics.common.io.FileProperties;
import uno.informatics.common.io.FileType;
import uno.informatics.data.DataType;
import uno.informatics.data.Feature;
import uno.informatics.data.FeatureDataset;
import uno.informatics.data.MatrixDataset;
import uno.informatics.data.ScaleType;
import uno.informatics.data.dataset.DatasetException;
import uno.informatics.data.feature.ColumnFeature;
import uno.informatics.data.feature.array.ArrayFeatureDataset;
import uno.informatics.data.matrix.array.DoubleArrayMatrixDataset;
import uno.informatics.data.pojo.SimpleFeaturePojo;
import uno.informatics.data.utils.DatasetUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * @author Guy Davenport
 */
public class GowersDistanceMatrixGeneratorTest {

    private static final String DATA_FILE = "/phenotypic_data.csv";
    private static final String DATA_FILE_WITH_TYPE = "/phenotypic_data_type.csv";
    private static final String MATRIX_FILE = "/matrix.csv";

    private static final String UID = "test";
    private static final String NAME = "test";
    private static final String DESCRIPTION = "test";

    private static final Object[][] DATA = new Object[][]{
        new Object[]{1, 1.0, "1", true},
        new Object[]{2, 3.0, "2", true},
        new Object[]{3, 3.0, "1", false},
        new Object[]{4, 5.0, "2", false},
        new Object[]{5, 4.0, "1", true}
    };

    private static final Feature[] FEATURES = new Feature[]{
        new SimpleFeaturePojo("feature1", DataType.INTEGER, ScaleType.INTERVAL, 0, 5),
        new SimpleFeaturePojo("feature1", DataType.DOUBLE, ScaleType.RATIO, 0.0, 5.0),
        new SimpleFeaturePojo("feature1", DataType.STRING, ScaleType.NOMINAL),
        new SimpleFeaturePojo("feature1", DataType.BOOLEAN, ScaleType.NOMINAL)
    };

    private static final double[][] MATRIX = new double[][]{
        new double[]{1.0, 0.6, 0.55, 0.15, 0.65},
        new double[]{0.6, 1.0, 0.45, 0.55, 0.55},
        new double[]{0.55, 0.45, 1.0, 0.35, 0.6},
        new double[]{0.15, 0.55, 0.35, 1.0, 0.4},
        new double[]{0.65, 0.55, 0.6, 0.4, 1.0}
    };

    private static final double DELTA = 0.0000001;

    /**
     * Test method for {@link GowersDistanceMatrixGenerator#GowersDistanceMatrixGenerator(Object[][], Feature[])}.
     */
    @Test
    public void testGowersDistanceMatrixGenerator() {
        new GowersDistanceMatrixGenerator(DATA, FEATURES);
    }

    /**
     * Test method for {@link GowersDistanceMatrixGenerator#generateDistanceMatrix()}.
     */
    @Test
    public void testGenerateDistanceMatrix() {
        GowersDistanceMatrixGenerator generator = new GowersDistanceMatrixGenerator(DATA, FEATURES);

        DistanceMatrixData matrix = generator.generateDistanceMatrix();

        Iterator<Integer> iterator1 = matrix.getIDs().iterator();
        Iterator<Integer> iterator2 = matrix.getIDs().iterator();

        int index1;
        int index2;

        while (iterator1.hasNext()) {
            index1 = iterator1.next();

            while (iterator2.hasNext()) {
                index2 = iterator2.next();
                assertEquals("cell (" + index1 + "," + index2 + ")",
                             MATRIX[index1][index2], matrix.getDistance(index1, index2), 0.0000000001);
            }
        }
    }

    //@Test TODO to fix this test we need a new expected matrix calculated in R
    //      where all values are treated as factors (Nominal)
    public void testGenerateDistanceMatrixFromFile() {
        try {

            FeatureDataset dataset = ArrayFeatureDataset.readFeatureDatasetFromTextFile(
                new File(GowersDistanceMatrixGeneratorTest.class.getResource(DATA_FILE).getPath()), FileType.CSV) ;

            GowersDistanceMatrixGenerator generator = new GowersDistanceMatrixGenerator(dataset);

            DistanceMatrixData data = generator.generateDistanceMatrix();

            Feature elementFeature = null;

            FileProperties fileProperties = new FileProperties(
                    GowersDistanceMatrixGeneratorTest.class.getResource(MATRIX_FILE).getPath(), FileType.CSV
            );

            fileProperties.setColumnHeaderPosition(0);
            fileProperties.setDataRowPosition(1);
            fileProperties.setRowHeaderPosition(0);
            
            List<Feature> features = dataset.getFeatures() ;
     
            MatrixDataset<Double> matrix
                    = DoubleArrayMatrixDataset.createMatrixDataset(
                            UID, NAME, DESCRIPTION, elementFeature,
                            fileProperties);

            for (int x = 0; x < features.size(); ++x) {
                for (int y = 0; y < features.size(); ++y) {
                    assertEquals(
                            "x=" + x + " y=" + y,
                            (double) matrix.getValue(x, y),
                            1 - data.getDistance(x, y),
                            DELTA
                    );
                }
            }

        } catch (DatasetException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
    
    @Test
    public void testGenerateDistanceMatrixFromFileWithType() {
        try {

            FeatureDataset dataset = ArrayFeatureDataset.readFeatureDatasetFromTextFile(
                    new File(GowersDistanceMatrixGeneratorTest.class.getResource(DATA_FILE_WITH_TYPE).getPath()),
                    FileType.CSV
            );

            GowersDistanceMatrixGenerator generator = new GowersDistanceMatrixGenerator(dataset);

            DistanceMatrixData data = generator.generateDistanceMatrix();

            Feature elementFeature = null;

            FileProperties fileProperties = new FileProperties(
                    GowersDistanceMatrixGeneratorTest.class.getResource(MATRIX_FILE).getPath(), FileType.CSV
            );

            fileProperties.setColumnHeaderPosition(0);
            fileProperties.setDataRowPosition(1);
            fileProperties.setRowHeaderPosition(0);
            
            List<Feature> features = dataset.getFeatures() ;

            MatrixDataset<Double> matrix
                    = DoubleArrayMatrixDataset.createMatrixDataset(
                            UID, NAME, DESCRIPTION, elementFeature,
                            fileProperties);

            for (int x = 0; x < features.size(); ++x) {
                for (int y = 0; y < features.size(); ++y) {
                    assertEquals(
                            "x=" + x + " y=" + y,
                            (double) matrix.getValue(x, y),
                            1 - data.getDistance(x, y),
                            DELTA
                    );
                }
            }

        } catch (DatasetException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
}
