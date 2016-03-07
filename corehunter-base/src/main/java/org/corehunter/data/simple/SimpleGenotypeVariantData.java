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

package org.corehunter.data.simple;

import static uno.informatics.common.Constants.UNKNOWN_INDEX;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import uno.informatics.common.io.IOUtilities;
import uno.informatics.common.io.RowReader;
import org.corehunter.data.GenotypeVariantData;
import org.corehunter.data.Header;
import uno.informatics.common.io.FileType;

/**
 * @author Guy Davenport, Herman De Beukelaer
 */
public class SimpleGenotypeVariantData extends SimpleNamedData implements GenotypeVariantData {

    private static final double DELTA = 1e-10;

    private final Double[][][] alleleFrequencies;   // null element means missing value
    private final int numberOfMarkers;
    private final int[] numberOfAllelesForMarker;
    private final int totalNumberAlleles;
    private final String[] markerNames;             // null element means no marker name assigned
    private final String[][] alleleNames;           // null element means no allele name assigned

    /**
     * Create data with name "Multi-allelic marker data".
     * For details of the arguments see 
     * {@link #SimpleGenotypeVariantData(String, String[], String[], String[][], Double[][][])}.
     * 
     * @param itemHeaders item headers, specifying name and/or unique identifier
     * @param markerNames marker names
     * @param alleleNames allele names per marker
     * @param alleleFrequencies allele frequencies
     */
    public SimpleGenotypeVariantData(Header[] itemHeaders, String[] markerNames, String[][] alleleNames,
                                     Double[][][] alleleFrequencies) {
        this("Multi-allelic marker data", itemHeaders, markerNames, alleleNames, alleleFrequencies);
    }
    
    /**
     * Create data with given dataset name, item headers, marker/allele names and allele frequencies.
     * The length of <code>alleleFrequencies</code> denotes the number of items in
     * the dataset. The length of <code>alleleFrequencies[i]</code> should be the same
     * for all <code>i</code> and denotes the number of markers. Finally, the length of
     * <code>alleleFrequencies[i][m]</code> should also be the same for all <code>i</code>
     * and denotes the number of alleles of the <code>m</code>-th marker. Allele counts may
     * differ for different markers.
     * <p>
     * All frequencies should be positive and the values in <code>alleleFrequencies[i][m]</code> should
     * sum to one for all <code>i</code> and <code>m</code>. Missing values are encoded as <code>null</code>.
     * If one or more allele frequencies are missing at a certain marker for a certain individual, the
     * remaining frequencies should sum to a value less than or equal to one.
     * <p>
     * Item headers and marker/allele names are optional. Missing names/headers are encoded as <code>null</code>.
     * Alternatively, if no item headers or no marker or allele names are assigned, the respective array
     * itself may also be <code>null</code>. If not <code>null</code> the length of each header/name array
     * should correspond to the dimensions of <code>alleleFrequencies</code> (number of individuals,
     * markers and alleles per marker).
     * <p>
     * Violating any of the requirements will produce an exception.
     * <p>
     * Allele frequencies as well as assigned headers and names are copied into internal data structures,
     * i.e. no references are retained to any of the arrays passed as arguments.
     * 
     * @param datasetName name of the dataset
     * @param itemHeaders item headers, <code>null</code> if no headers are assigned; if not <code>null</code>
     *                    its length should correspond to the number of individuals
     * @param markerNames marker names, <code>null</code> if no marker names are assigned; if not
     *                    <code>null</code> its length should correspond to the number of markers
     * @param alleleNames allele names per marker, <code>null</code> if no allele names are assigned;
     *                    if not <code>null</code> the length of <code>alleleNames</code> should correspond
     *                    to the number of markers and the length of <code>alleleNames[m]</code> to the number
     *                    of alleles of the m-th marker
     * @param alleleFrequencies allele frequencies, may not be <code>null</code>; dimensions indicate number of
     *                          individuals, markers and alleles per marker
     */
    public SimpleGenotypeVariantData(String datasetName, Header[] itemHeaders, String[] markerNames,
                                     String[][] alleleNames, Double[][][] alleleFrequencies) {
        
        // pass dataset name, size and item headers to parent
        super(datasetName, alleleFrequencies.length, itemHeaders);

        // check allele frequencies and infer number of individuals, markers and alleles per marker
        if (alleleFrequencies == null) {
            throw new IllegalArgumentException("Allele frequency entries not defined.");
        }
        int n = alleleFrequencies.length;
        int m = -1;
        int[] a = null;
        // loop over individuals
        for(int i = 0; i < n; i++){
            Double[][] indFreqs = alleleFrequencies[i];
            if(indFreqs == null){
                throw new IllegalArgumentException("Allele frequencies not defined for individual " + i);
            }
            if(m == -1){
                m = indFreqs.length;
                a = new int[m];
                Arrays.fill(a, -1);
            } else if (indFreqs.length != m) {
                throw new IllegalArgumentException("All individuals should have same number of markers.");
            }
            // loop over markers
            for(int j = 0; j < m; j++){
                Double[] alleleFreqs = indFreqs[j];
                if(alleleFreqs == null){
                    throw new IllegalArgumentException(String.format(
                            "Allele frequencies not defined for individual %d at marker %d.", i, j
                    ));
                }
                if(a[j] == -1){
                    a[j] = alleleFreqs.length;
                } else if (alleleFreqs.length != a[j]){
                    throw new IllegalArgumentException(
                            "Number of alleles per marker should be consistent across all individuals."
                    );
                }
                // loop over alleles
                if(Arrays.stream(alleleFreqs).filter(Objects::nonNull).anyMatch(f -> f < 0.0)){
                    throw new IllegalArgumentException("All frequencies should be positive.");
                }
                double sum = Arrays.stream(alleleFreqs)
                                   .filter(Objects::nonNull)
                                   .mapToDouble(Double::doubleValue)
                                   .sum();
                // sum should not exceed 1.0
                if(sum > 1.0){
                    throw new IllegalArgumentException("Allele frequency sum per marker should not exceed one.");
                }
                if(Arrays.stream(alleleFreqs).noneMatch(Objects::isNull)){
                    // no missing values: should sum to 1.0
                    if(1.0 - sum > DELTA){
                        throw new IllegalArgumentException("Allele frequencies for marker should sum to one.");
                    }
                }
            }
        }
        numberOfMarkers = m;
        numberOfAllelesForMarker = a;
        
        // set total number of alleles
        totalNumberAlleles = Arrays.stream(numberOfAllelesForMarker).sum();
        
        // copy allele frequencies
        this.alleleFrequencies = new Double[n][m][];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                this.alleleFrequencies[i][j] = Arrays.copyOf(
                        alleleFrequencies[i][j], numberOfAllelesForMarker[j]
                );
            }
        }
        
        // check and copy marker names
        if(markerNames == null){
            this.markerNames = new String[m];
        } else {
            if(markerNames.length != m){
                throw new IllegalArgumentException(String.format(
                        "Incorrect number of marker names provided. Expected: %d, actual: %d.",
                        m, markerNames.length
                ));
            }
            this.markerNames = Arrays.copyOf(markerNames, m);
        }
        
        // check and copy allele names
        this.alleleNames = new String[m][];
        if(alleleNames == null){
            for(int j = 0; j < m; j++){
                this.alleleNames[j] = new String[numberOfAllelesForMarker[j]];
            }
        } else {
            if(alleleNames.length != m){
                throw new IllegalArgumentException(String.format(
                        "Incorrect number of marker-allele names provided. Expected: %d, actual: %d.",
                        m, alleleNames.length
                ));
            }
            for(int j = 0; j < m; j++){
                if(alleleNames[j] == null){
                    this.alleleNames[j] = new String[numberOfAllelesForMarker[j]];
                } else if (alleleNames[j].length != numberOfAllelesForMarker[j]) {
                    throw new IllegalArgumentException(String.format(
                            "Incorrect number of allele names provided for marker %d. Expected: %d, actual: %d.",
                            j, numberOfAllelesForMarker[j], alleleNames[j].length
                    ));
                } else {
                    this.alleleNames[j] = Arrays.copyOf(alleleNames[j], numberOfAllelesForMarker[j]);
                }
            }
        }
        
    }

    @Override
    public int getNumberOfMarkers() {
        return numberOfMarkers;
    }
    
    @Override
    public int getNumberOfAlleles(int markerIndex) {
        return numberOfAllelesForMarker[markerIndex];
    }
    
    @Override
    public String getMarkerName(int markerIndex) {
        return markerNames[markerIndex];
    }
    
    @Override
    public int getTotalNumberOfAlleles() {
        return totalNumberAlleles;
    }
    
    @Override
    public String getAlleleName(int markerIndex, int alleleIndex) {
        return alleleNames[markerIndex][alleleIndex];
    }

    @Override
    public Double getAlelleFrequency(int id, int markerIndex, int alleleIndex) {
        return alleleFrequencies[id][markerIndex][alleleIndex];
    }
    
    @Override
    public double getAverageAlelleFrequency(Collection<Integer> entityIds, int markerIndex, int alleleIndex) {
        return entityIds.stream()
                        .mapToDouble(id -> {
                            Double f = getAlelleFrequency(id, markerIndex, alleleIndex);
                            return f == null ? 0.0 : f;
                        })
                        .average()
                        .getAsDouble();
    }

    /**
     * Read genotype variant data from file. Only file types {@link FileType#TXT} and {@link FileType#CSV} are allowed.
     * Values are separated with a single tab (txt) or comma (csv) character. Allele frequencies should follow the
     * requirements as described in the constructor {@link #SimpleGenotypeVariantData(String, Header[], String[],
     * String[][], Double[][][])}. Missing frequencies are encoding as empty cells.
     * <p>
     * The first row specifies the marker names. This header row is compulsory and the number of alleles (columns)
     * per marker is inferred from the marker names. Each marker should be uniquely identified by its name.
     * All columns corresponding to the same marker should occur consecutively in the file.
     * A second optional header row can be included to specify allele names, which need not be unique.
     * Depending on the number of header columns (if any, see below) some additional column header cells
     * may have been prepended to the header rows.
     * <p>
     * Two optional (leftmost) header columns can be included to specify individual names and/or
     * unique identifiers. The former is identified with column header "NAME", the latter with column header "ID".
     * The column headers should be placed in the corresponding cell of the lowest header row (marker/allele names).
     * Assigned identifiers should be unique and are used to distinguish between individuals with the same name.
     * <p>
     * Leading and trailing whitespace is removed from names and unique identifiers and they are unquoted if wrapped
     * in single or double quotes after whitespace removal. If it is intended to start or end a name/identifier with
     * whitespace this whitespace should be contained within the quotes, as it will then not be removed. It is allowed
     * that names/identifiers are missing for some individuals/alleles but marker names are required.
     * <p>
     * The dataset name is set to the name of the file to which <code>filePath</code> points.
     * 
     * @param filePath path to file that contains the data
     * @param type {@link FileType#TXT} or {@link FileType#CSV}
     * @return genotype variant data
     * @throws IOException if the file can not be read or is not correctly formatted
     */
    public final static SimpleGenotypeVariantData readData(Path filePath, FileType type) throws IOException {
        
        // validate arguments
        
        if (filePath == null) {
            throw new IllegalArgumentException("File path not defined.");
        }
        
        if(!filePath.toFile().exists()){
            throw new IOException("File does not exist : " + filePath + ".");
        }

        if(type == null){
            throw new IllegalArgumentException("File type not defined.");
        }
        
        if(type != FileType.TXT && type != FileType.CSV){
            throw new IllegalArgumentException(
                    String.format("Only file types TXT and CSV are supported. Got: %s.", type)
            );
        }

        // read data from file
        // TODO
        return null;
        
    }
}
