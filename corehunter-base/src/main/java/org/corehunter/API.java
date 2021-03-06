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

package org.corehunter;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.corehunter.data.CoreHunterData;
import org.corehunter.data.DistanceMatrixData;
import org.corehunter.data.GenotypeData;
import org.corehunter.data.GenotypeDataFormat;
import org.corehunter.data.simple.SimpleBiAllelicGenotypeData;
import org.corehunter.data.simple.SimpleDistanceMatrixData;
import org.corehunter.data.simple.SimpleGenotypeData;
import org.corehunter.listener.SimpleCoreHunterListener;
import org.jamesframework.core.subset.SubsetSolution;
import uno.informatics.data.Data;
import uno.informatics.data.Feature;
import uno.informatics.data.Scale;
import uno.informatics.data.SimpleEntity;
import uno.informatics.data.dataset.FeatureData;
import uno.informatics.data.feature.array.ArrayFeatureData;
import uno.informatics.data.io.FileType;
import uno.informatics.data.pojo.DataPojo;
import uno.informatics.data.pojo.SimpleEntityPojo;

/**
 * Simple API used by the R interface and as a utility class.
 *
 * @author Herman De Beukelaer, Guy Davenport
 */
public class API {

    // rJava encodes NA with a specific NaN value
    private static final double NA = Double.longBitsToDouble(9218868437227407266L);
    
    private static final CoreHunterObjectiveType DEFAULT_OBJECTIVE = 
            CoreHunterObjectiveType.AV_ENTRY_TO_NEAREST_ENTRY;
    private static final CoreHunterMeasure DEFAULT_GENOTYPE_MEASURE = 
            CoreHunterMeasure.MODIFIED_ROGERS;

    /* -------- */
    /* All data */
    /* -------- */

    public static String[] getIds(Data data) {
        int n = data.getSize();
        String[] ids = new String[n];
        for (int i = 0; i < n; i++) {
            ids[i] = data.getHeader(i).getUniqueIdentifier();
        }
        return ids;
    }

    public static String[] getNames(Data data) {
        int n = data.getSize();
        String[] ids = new String[n];
        for (int i = 0; i < n; i++) {
            ids[i] = data.getHeader(i).getName();
        }
        return ids;
    }

    /**
     * Get unique identifier strings from item indices.
     * 
     * @param data
     *            a data object
     * @param indices
     *            item indices (zero-based).
     * @return unique identifiers
     */
    public static String[] getIdsFromIndices(Data data, int[] indices) {
        String[] ids = Arrays.stream(indices)
                             .mapToObj(i -> data.getHeader(i).getUniqueIdentifier())
                             .toArray(n -> new String[n]);
        return ids;
    }

    /**
     * Get item indices from their string identifiers.
     * 
     * @param data
     *            a data object
     * @param ids
     *            string identifiers
     * @return item indices (zero-based)
     */
    public static int[] getIndicesFromIds(DataPojo data, String[] ids) {
        int[] indices = Arrays.stream(ids).mapToInt(id -> data.indexOf(id)).toArray();
        return indices;
    }

    /* -------------------- */
    /* Distance matrix data */
    /* -------------------- */

    public static DistanceMatrixData readDistanceMatrixData(String file) throws IOException {
        return SimpleDistanceMatrixData.readData(Paths.get(file), inferFileType(file));
    }

    public static DistanceMatrixData createDistanceMatrixData(double[][] distances, String[] ids, String[] names){
        // check arguments
        if(distances == null){
            throw new IllegalArgumentException("Distances are required.");
        }
        int n = distances.length;
        if (ids == null) {
            throw new IllegalArgumentException("Ids are required.");
        }
        if (ids.length != n) {
            throw new IllegalArgumentException("Number of ids does not correspond to size of matrix.");
        }
        if(names != null && names.length != n){
            throw new IllegalArgumentException("Number of names does not correspond to size of matrix.");
        }
        // create and return data
        return new SimpleDistanceMatrixData(createHeaders(ids, names), distances);
    }

    /* ------------- */
    /* Genotype data */
    /* ------------- */

    public static GenotypeData readGenotypeData(String file, String format) throws IOException {
        return SimpleGenotypeData.readData(Paths.get(file), inferFileType(file),
                GenotypeDataFormat.valueOf(format.toUpperCase()));
    }
    
    public static GenotypeData createDefaultGenotypeData(String[][] data,
                                                         String[] ids, String[] names,
                                                         String[] columnNames){
        // check arguments
        if(data == null){
            throw new IllegalArgumentException("Data is required.");
        }
        if(data.length == 0){
            throw new IllegalArgumentException("Empty allele matrix.");
        }
        int n = data.length;
        int c = data[0].length;
        if(ids == null){
            throw new IllegalArgumentException("Ids are required.");
        }
        if(ids.length != n){
            throw new IllegalArgumentException("Number of ids does not correspond to number of rows.");
        }
        if(names != null && names.length != n){
            throw new IllegalArgumentException("Number of names does not correspond to number of rows.");
        }
        if(columnNames == null){
            throw new IllegalArgumentException("Column names are required.");
        }
        if(columnNames.length != c){
            throw new IllegalArgumentException("Number of column names does not correspond to number of columns.");
        }
        // infer marker names and number of columns per marker
        HashMap<String, Integer> markers = SimpleGenotypeData.inferMarkerNames(columnNames);
        int numMarkers = markers.size();
        String[] markerNames = markers.keySet().toArray(new String[0]);
        Integer[] markerNumCols = markers.values().toArray(new Integer[0]);
        // split data per marker
        String[][][] splitData = new String[n][numMarkers][];
        for(int i = 0; i < n; i++){
            if(data[i].length != c){
                throw new IllegalArgumentException("Incorrect number of values at row " + i + ".");
            }
            int j = 0;
            for(int m = 0; m < numMarkers; m++){
                int markerCols = markerNumCols[m];
                splitData[i][m] = new String[markerCols];
                for(int mc = 0; mc < markerCols; mc++){
                    splitData[i][m][mc] = data[i][j++];
                }
            }
        }
        // create and return data
        return SimpleGenotypeData.createDefaultData(splitData, ids, names, markerNames);
    }
    
    public static GenotypeData createBiparentalGenotypeData(int[][] alleleScores,
                                                            String[] ids, String[] names,
                                                            String[] markerNames){
        // check arguments
        if(alleleScores == null){
            throw new IllegalArgumentException("Allele scores are required.");
        }
        if(alleleScores.length == 0){
            throw new IllegalArgumentException("Empty allele score matrix.");
        }
        int n = alleleScores.length;
        int m = alleleScores[0].length;
        if(ids == null){
            throw new IllegalArgumentException("Ids are required.");
        }
        if(ids.length != n){
            throw new IllegalArgumentException("Number of ids does not correspond to number of rows.");
        }
        if(names != null && names.length != n){
            throw new IllegalArgumentException("Number of names does not correspond to number of rows.");
        }
        if(markerNames != null && markerNames.length != m){
            throw new IllegalArgumentException("Number of marker names does not correspond to number of columns.");
        }
        // create and return data
        return new SimpleBiAllelicGenotypeData(createHeaders(ids, names), markerNames, toIntegerMatrix(alleleScores));
    }
    
    public static GenotypeData createFrequencyGenotypeData(double[][] frequencies,
                                                           String[] ids, String[] names,
                                                           String[] columnNames, String[] alleleNames){
        // check arguments
        if(frequencies == null){
            throw new IllegalArgumentException("Allele frequencies are required.");
        }
        if(frequencies.length == 0){
            throw new IllegalArgumentException("Empty allele frequency matrix.");
        }
        int n = frequencies.length;
        int c = frequencies[0].length;
        if(ids == null){
            throw new IllegalArgumentException("Ids are required.");
        }
        if(ids.length != n){
            throw new IllegalArgumentException("Number of ids does not correspond to number of rows.");
        }
        if(names != null && names.length != n){
            throw new IllegalArgumentException("Number of names does not correspond to number of rows.");
        }
        if(columnNames == null){
            throw new IllegalArgumentException("Column names are required.");
        }
        if(columnNames.length != c){
            throw new IllegalArgumentException("Number of column names does not correspond to number of columns.");
        }
        if(alleleNames == null){
            throw new IllegalArgumentException("Allele names are required.");
        }
        if(alleleNames.length != c){
            throw new IllegalArgumentException("Number of allele names does not correspond to number of columns.");
        }
        // infer marker names and allele counts from column names
        HashMap<String, Integer> markers = SimpleGenotypeData.inferMarkerNames(columnNames);
        int numMarkers = markers.size();
        String[] markerNames = markers.keySet().toArray(new String[0]);
        Integer[] alleleCounts = markers.values().toArray(new Integer[0]);
        // split allele frequencies and names per marker
        Double[][][] convFreqs = new Double[n][numMarkers][];
        for(int i = 0; i < n; i++){
            if(frequencies[i].length != c){
                throw new IllegalArgumentException("Incorrect number of values at row " + i + ".");
            }
            int j = 0;
            for(int m = 0; m < numMarkers; m++){
                int markerAlleleCount = alleleCounts[m];
                convFreqs[i][m] = new Double[markerAlleleCount];
                for(int a = 0; a < markerAlleleCount; a++){
                    Double freq = frequencies[i][j++];
                    // rJava encodes missing double values (NA in R) as NaN in Java
                    if(Double.isNaN(freq)){
                        freq = null;
                    }
                    convFreqs[i][m][a] = freq;
                }
            }
        }
        String[][] convAlleles = new String[numMarkers][];
        int j = 0;
        for(int m = 0; m < numMarkers; m++){
            int markerAlleleCount = alleleCounts[m];
            convAlleles[m] = new String[markerAlleleCount];
            for(int a = 0; a < markerAlleleCount; a++){
                convAlleles[m][a] = alleleNames[j++];
            }
        }
        return new SimpleGenotypeData(createHeaders(ids, names), markerNames, convAlleles, convFreqs);
    }
    
    public static String[][] getAlleles(GenotypeData data){
        int numMarkers = data.getNumberOfMarkers();
        String[][] alleles = new String[numMarkers][];
        for (int m = 0; m < numMarkers; m++) {
            int numAlleles = data.getNumberOfAlleles(m);
            alleles[m] = new String[numAlleles];
            for (int a = 0; a < numAlleles; a++) {
                alleles[m][a] = data.getAlleleName(m, a);
            }
        }
        return alleles;
    }
    
    public static String[] getMarkerNames(GenotypeData data){
        int numMarkers = data.getNumberOfMarkers();
        String[] markerNames = new String[numMarkers];
        for(int m = 0; m < numMarkers; m++){
            markerNames[m] = data.getMarkerName(m);
        }
        return markerNames;
    }
    
    public static double[][] getAlleleFrequencies(GenotypeData data){
        int n = data.getSize();
        int m = data.getNumberOfMarkers();
        int numAlleles = data.getTotalNumberOfAlleles();
        double[][] freqs = new double[n][numAlleles];
        for(int i = 0; i < n; i++){
            int a = 0;
            for(int j = 0; j < m; j++){
                for(int k = 0; k < data.getNumberOfAlleles(j); k++){
                    Double freq = data.getAlleleFrequency(i, j, k);
                    // convert missing values
                    freqs[i][a++] = (freq == null ? NA : freq);
                }
            }
        }
        return freqs;
    }
    
    /* -------------- */
    /* Phenotype data */
    /* -------------- */

    public static FeatureData readPhenotypeData(String file) throws IOException {
        return ArrayFeatureData.readData(Paths.get(file), inferFileType(file));
    }
    
    public static Double[] getRanges(FeatureData data){
        List<Feature> features = data.getFeatures();
        int numTraits = features.size();
        Double[] ranges = new Double[numTraits];
        for(int t = 0; t < numTraits; t++){
            Scale scale = features.get(t).getMethod().getScale();
            Number min = scale.getMinimumValue();
            Number max = scale.getMaximumValue();
            if(min != null && max != null){
                ranges[t] = max.doubleValue() - min.doubleValue();
            }
        }
        return ranges;
    }

    /* --------- */
    /* Arguments */
    /* --------- */

    public static CoreHunterObjective createObjective(String type, String measure, double weight) {
        return createObjective(type, measure, weight, null);
    }
    
    public static CoreHunterObjective createObjective(String type, String measure,
                                                      double weight, double min, double max) {
        return createObjective(type, measure, weight, new Range<>(min, max));
    }
    
    private static CoreHunterObjective createObjective(String type, String measure,
                                                       double weight, Range<Double> normalizationRange) {
        return new CoreHunterObjective(
                CoreHunterObjectiveType.createFromAbbreviation(type),
                CoreHunterMeasure.createFromAbbreviation(measure),
                weight, normalizationRange
        );
    }
        
    public static CoreHunterArguments createArguments(CoreHunterData data, int size,
                                                      CoreHunterObjective[] objectives,
                                                      boolean normalizeMultiObjective){
        return new CoreHunterArguments(data, size, Arrays.asList(objectives), normalizeMultiObjective);
    }

    /* --------- */
    /* Execution */
    /* --------- */

    /**
     * Get normalization ranges of all objectives in a multi-objective configuration.
     * Executes a preliminary random descent search per objective (in parallel) to approximate the
     * best solution in terms of each included objective. For an objective that is being maximized,
     * the upper bound is set to the value of the best solution for that objective, while the lower
     * bound is set to the Pareto minimum, i.e. the minimum value obtained when evaluating all optimal
     * solutions with the considered objective. For an objective that is being minimized, the roles
     * of upper and lower bound are interchanged, and the Pareto maximum is used instead.
     * 
     * @param args Core Hunter arguments including data, objectives and subset size.
     * @param mode Execution mode, one of "default" or "fast". Only affects the default
     *             stop conditions, not the used algorithm (always random descent).
     * @param timeLimit Absolute runtime limit in seconds.
     *                  If set to a negative value no time limit is used.
     * @param maxTimeWithoutImprovement Maximum time without finding an improvement, in seconds.
     *                                  If set to a negative value, the default improvement time of
     *                                  the chosen execution mode is used: 10 seconds in default mode,
     *                                  2 seconds in fast mode.
     * @return Matrix containing normalization ranges.
     *         One row per objective, and two columns with lower and upper bound, respectively.
     */
    public static double[][] getNormalizationRanges(CoreHunterArguments args, String mode,
                                                    int timeLimit, int maxTimeWithoutImprovement){
        // interpret arguments
        CoreHunterExecutionMode exMode = CoreHunterExecutionMode.DEFAULT;
        if (mode.equals("fast")) {
            exMode = CoreHunterExecutionMode.FAST;
        }
        // create Core Hunter executor
        CoreHunter ch = new CoreHunter(exMode);
        if (timeLimit > 0) {
            ch.setTimeLimit(1000 * timeLimit); // convert to milliseconds
        }
        if (maxTimeWithoutImprovement > 0) {
            ch.setMaxTimeWithoutImprovement(1000 * maxTimeWithoutImprovement); // convert to milliseconds
        }
        // determine ranges
        List<Range<Double>> rangeList = ch.normalize(args);
        // convert result
        double[][] ranges = new double[rangeList.size()][2];
        for(int o = 0; o < rangeList.size(); o++){
            Range<Double> range = rangeList.get(o);
            ranges[o][0] = range.getLower();
            ranges[o][1] = range.getUpper();
        }
        return ranges;
    }
    
    /**
     * Sample a core collection.
     * 
     * @param args Core Hunter arguments including data, objective and subset size.
     * @param mode Execution mode, one of "default" or "fast".
     * @param timeLimit Absolute runtime limit in seconds.
     *                  If set to a negative value no time limit is used.
     * @param maxTimeWithoutImprovement Maximum time without finding an improvement, in seconds.
     *                                  If set to a negative value, the default improvement time of
     *                                  the chosen execution mode is used: 10 seconds in default mode,
     *                                  2 seconds in fast mode.
     * @param silent If <code>true</code> no output is written to the console.
     * @return Indices of selected items (zero-based).
     */
    public static int[] sampleCore(CoreHunterArguments args, String mode,
                                   int timeLimit, int maxTimeWithoutImprovement,
                                   boolean silent) {
        // interpret arguments
        CoreHunterExecutionMode exMode = CoreHunterExecutionMode.DEFAULT;
        if (mode.equals("fast")) {
            exMode = CoreHunterExecutionMode.FAST;
        }
        // create Core Hunter executor
        CoreHunter ch = new CoreHunter(exMode);
        if (timeLimit > 0) {
            ch.setTimeLimit(1000 * timeLimit); // convert to milliseconds
        }
        if (maxTimeWithoutImprovement > 0) {
            ch.setMaxTimeWithoutImprovement(1000 * maxTimeWithoutImprovement); // convert to milliseconds
        }
        if (!silent) {
            ch.setListener(new SimpleCoreHunterListener());
        }
        // sample core
        SubsetSolution core = ch.execute(args);
        // convert result
        int[] ids = new int[core.getNumSelectedIDs()];
        int i = 0;
        for (int id : core.getSelectedIDs()) {
            ids[i++] = id;
        }
        return ids;
    }

    /* ---------- */
    /* Evaluation */
    /* ---------- */

    /**
     * Evaluate a core collection.
     * 
     * @param selected
     *            Indices of selected items (zero-based).
     * @param data
     *            Core Hunter data
     * @param obj
     *            Objective used to evaluate the core.
     * @return Core value according to the used objective.
     */
    public static double evaluateCore(int[] selected, CoreHunterData data, CoreHunterObjective obj) {
        CoreHunter ch = new CoreHunter();
        SubsetSolution sol = new SubsetSolution(data.getIDs());
        for (int sel : selected) {
            sol.select(sel);
        }
        return ch.evaluate(sol, data, obj);
    }

    /**
     * Creates a list of default objectives, one for each type of data available, with equal weights.
     * 
     * @param coreHunterData the data for which the objectives are required
     * @return list of default objectives
     */
    public static final List<CoreHunterObjective> createDefaultObjectives(CoreHunterData coreHunterData) {
        List<CoreHunterObjective> objectives = new ArrayList<>();

        if (coreHunterData != null) {
            int count = 0;
            if (coreHunterData.hasGenotypes()) {
                ++count;
            }
            if (coreHunterData.hasPhenotypes()) {
                ++count;
            }
            if (coreHunterData.hasDistances()) {
                ++count;
            }

            if (coreHunterData.hasGenotypes()) {
                objectives.add(new CoreHunterObjective(DEFAULT_OBJECTIVE, DEFAULT_GENOTYPE_MEASURE, 1.0 / count));
            }
            if (coreHunterData.hasPhenotypes()) {
                objectives.add(new CoreHunterObjective(DEFAULT_OBJECTIVE, CoreHunterMeasure.GOWERS, 1.0 / count));
            }
            if (coreHunterData.hasDistances()) {
                objectives.add(new CoreHunterObjective(
                        DEFAULT_OBJECTIVE, CoreHunterMeasure.PRECOMPUTED_DISTANCE, 1.0 / count
                ));
            }
        }

        return objectives;
    }

    /**
     * Creates a default allowed objective for the data. If the data contains
     * genotypic data, the default genotypic data objective is returned. If no
     * genotypes are available, then the default phenotypic data objective
     * is returned. Finally, if no genotypic or phenotypic data is available,
     * then the default distances data objective is returned.
     * 
     * @param coreHunterData the data for which the objective is required
     * @return a default objective (with weight 1.0)
     */
    public static CoreHunterObjective createDefaultObjective(CoreHunterData coreHunterData) {

        if (coreHunterData != null) {

            if (coreHunterData.hasGenotypes()) {
                return new CoreHunterObjective(DEFAULT_OBJECTIVE, DEFAULT_GENOTYPE_MEASURE);
            }
            if (coreHunterData.hasPhenotypes()) {
                return new CoreHunterObjective(DEFAULT_OBJECTIVE, CoreHunterMeasure.GOWERS);
            }
            if (coreHunterData.hasDistances()) {
                return new CoreHunterObjective(DEFAULT_OBJECTIVE, CoreHunterMeasure.PRECOMPUTED_DISTANCE);
            }
        }

        return null;
    }

    /**
     * Creates a list of all possible Core Hunter objective types for a given data object.
     * 
     * @param coreHunterData  the data for which the objective types are required
     * @return list of all possible Core Hunter objective types
     */
    public static final List<CoreHunterObjectiveType> getAllowedObjectives(CoreHunterData coreHunterData) {
        List<CoreHunterObjectiveType> objectives = new ArrayList<>();

        if (coreHunterData != null
             && (coreHunterData.hasPhenotypes() || coreHunterData.hasGenotypes() || coreHunterData.hasDistances())) {

            objectives.add(CoreHunterObjectiveType.AV_ACCESSION_TO_NEAREST_ENTRY);
            objectives.add(CoreHunterObjectiveType.AV_ENTRY_TO_ENTRY);
            objectives.add(CoreHunterObjectiveType.AV_ENTRY_TO_NEAREST_ENTRY);

            if (coreHunterData.hasGenotypes()) {
                objectives.add(CoreHunterObjectiveType.COVERAGE);
                objectives.add(CoreHunterObjectiveType.HETEROZYGOUS_LOCI);
                objectives.add(CoreHunterObjectiveType.SHANNON_DIVERSITY);
            }
        }

        return objectives;
    }

    /**
     * Create a list of all possible Core Hunter measures for a given data object and objective type.
     * 
     * @param coreHunterData the data for which the measures are required
     * @param objectiveType the objective type for which the measures are required
     * @return list of all possible Core Hunter measures
     */
    public static final List<CoreHunterMeasure> getAllowedMeasures(CoreHunterData coreHunterData,
                                                                   CoreHunterObjectiveType objectiveType) {
        return getAllowedMeasures(
                coreHunterData.hasPhenotypes(),
                coreHunterData.hasGenotypes(),
                coreHunterData.hasDistances(),
                objectiveType
        );
    }

    /**
     * Create a list of all possible Core Hunter measures for a given objective type
     * taking into account whether genotypes, phenotypes and/or precomputed distances
     * are available.
     * 
     * @param hasGenotypes <code>true</code> if genotypic data is present in the data
     * @param hasPhenotypes <code>true</code> if phenotypic data is present in the data
     * @param hasDistances <code>true</code> if distances data is present in the data
     * @param objectiveType the objective type for which the measures are required
     * @return list of all possible Core Hunter measures
     */
    public static final List<CoreHunterMeasure> getAllowedMeasures(boolean hasGenotypes,
                                                                   boolean hasPhenotypes,
                                                                   boolean hasDistances,
                                                                   CoreHunterObjectiveType objectiveType) {
        List<CoreHunterMeasure> measures = new ArrayList<>();

        switch (objectiveType) {
            case AV_ACCESSION_TO_NEAREST_ENTRY:
            case AV_ENTRY_TO_ENTRY:
            case AV_ENTRY_TO_NEAREST_ENTRY:
                if (hasGenotypes) {
                    measures.add(CoreHunterMeasure.MODIFIED_ROGERS);
                    measures.add(CoreHunterMeasure.CAVALLI_SFORZA_EDWARDS);
                }
                if (hasPhenotypes) {
                    measures.add(CoreHunterMeasure.GOWERS);
                }
                if (hasDistances) {
                    measures.add(CoreHunterMeasure.PRECOMPUTED_DISTANCE);
                }
                break;
            case COVERAGE:
            case HETEROZYGOUS_LOCI:
            case SHANNON_DIVERSITY:
            default:
                break;
        }

        return measures;
    }

    /* ----------------------- */
    /* Private utility methods */
    /* ----------------------- */

    /**
     * Infer file type from the extension of the file. Only supports
     * {@link FileType#TXT} and {@link FileType#CSV} and returns
     * <code>null</code> for other extensions.
     * 
     * @param file
     *            file path
     * @return file type
     */
    private static FileType inferFileType(String file) {
        file = file.toLowerCase();
        if (file.endsWith(".txt")) {
            return FileType.TXT;
        }
        if (file.endsWith(".csv")) {
            return FileType.CSV;
        }
        return null;
    }
    
    private static SimpleEntity[] createHeaders(String[] ids, String[] names){
        if(names == null){
            names = ids;
        }
        int n = ids.length;
        SimpleEntity[] headers = new SimpleEntity[n];
        for(int i = 0; i < n; i++){
            if(names[i] != null){
                headers[i] = new SimpleEntityPojo(ids[i], names[i]);
            } else {
                headers[i] = new SimpleEntityPojo(ids[i]);
            }
        }
        return headers;
    }
    
    /**
     * Convert primitive to Integer matrix.
     * All occurrences of {@link Integer#MIN_VALUE} are replaced with <code>null</code>,
     * which is used by rJava to encode missing values (NAs in R).
     * 
     * @param matrix primitive integer matrix
     * @return Integer matrix
     */
    private static Integer[][] toIntegerMatrix(int[][] matrix){
        Integer[][] conv = new Integer[matrix.length][];
        for(int i = 0; i < conv.length; i++){
            conv[i] = new Integer[matrix[i].length];
            for(int j = 0; j < conv[i].length; j++){
                conv[i][j] = (matrix[i][j] != Integer.MIN_VALUE ? matrix[i][j] : null);
            }
        }
        return conv;
    }

}
