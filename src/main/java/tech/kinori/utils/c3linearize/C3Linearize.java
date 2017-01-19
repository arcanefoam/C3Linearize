/*******************************************************************************
 * Copyright (c) 2017 Kinori Tech
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * 	http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Horacio Hoyos - initial API and implementation
 ******************************************************************************/
package tech.kinori.utils.c3linearize;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * The Class RefinementLinearization.
 * Based on https://github.com/mikeboers/C3Linearize
 *
 * @param <T> the generic type
 */
public class C3Linearize<T> {

	/** The results cache. */
	HashMap<T, List<T>> results = new HashMap<T, List<T>>();

	/**
	 * Merge object sequences preserving order in initial sequences.
     * This is the merge function as described for C3, see:
     * http://www.python.org/download/releases/2.3/mro/
	 * @param sequences The list of sequences to merge
	 * @return The result of the merge
	 */
    private List<T> merge(List<List<T>> sequences) {
        List<List<T>> partialresult = new ArrayList<List<T>>();
        for (List<T> seq : sequences) {
            partialresult.add(new ArrayList<T>(seq));
        }
        List<T> result = new ArrayList<T>();
        for(;;) {
            // Remove empty sequences
            partialresult = partialresult.stream().filter(seq ->!seq.isEmpty()).collect(Collectors.toList());
            if (partialresult.isEmpty()) {
                return result;
            }

            // Find the first clean head
            T head = null;
            for (List<T> seq :partialresult) {
                T found = seq.get(0);
                 // If this is not a bad head (ie. not in any other sequence)...
                if (!partialresult.stream().anyMatch(s -> s.subList(1, s.size()).contains(found))) {
                    head = found;
                    break;
                }
            }
            if (head == null) {
                throw new IllegalArgumentException("Inconsistent hierarchy while merging " + partialresult.toString());
            }
            result.add(head);
            Iterator<List<T>> it = partialresult.iterator();
            while (it.hasNext()) {
                List<T> seq = it.next();
                if (seq.get(0).equals(head)) {
                    seq.remove(0);
                }
            }
        }
    }

    /**
     * Build a graph of an object given a function to return it's bases.
     *
     * @param obj the object
     * @param bases_function the function that retrieves the hierarchy of the objects
     * @return the map
     */
    public Map<T, List<T>> build_graph(T obj, Function<T, List<T>> bases_function) {
        HashMap<T, List<T>> graph = new HashMap<T, List<T>>();
        add_to_graph(obj, graph, bases_function);
        return graph;
    }

    /**
     * Add the closure of the bases to the graph
     *
     * @param obj the m
     * @param graph the graph
     * @param bases_function 
     */
    private void add_to_graph(T obj, HashMap<T, List<T>> graph, Function<T, List<T>> bases_function) {
        if (!graph.containsKey(obj)) {
            graph.put(obj, bases_function.apply(obj));
            for (T x : graph.get(obj)) {
                add_to_graph(x, graph, bases_function);
            }
        }
    }

    /**
     * Linearize a dependency graph using the C3 method, maintaining the order of direct dependants
     * 
     *
     * @param graph the graph
     * @return the map
     */
    public Map<T, List<T>> linearize(Map<T, List<T>> graph) {
        return linearize(graph, true, graph.keySet());
    }
    
    
    /**
     * Linearize a dependency graph using the C3 method, maintaining the order of direct dependants. Linearization
     * is only done for the supplied list of objects in the grpah. 
     *
     * @param graph the graph
     * @param heads the heads
     * @return the map
     */
    public Map<T, List<T>> linearize(Map<T, List<T>> graph, List<T> heads) {
        return linearize(graph, true, heads);
    }
    

    /**
     * Linearize.
     *
     * @param graph the graph
     * @param order the order
     * @return the map
     */
    public Map<T, List<T>> linearize(Map<T, List<T>> graph, boolean order, Collection<T> heads) {
       
        Map<T, List<T>> g = graph.entrySet().stream()
                    .sorted(comparingByValueSize())
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (u, v) -> {
                                throw new IllegalStateException(String.format("Duplicate key %s", u));
                            },
                            LinkedHashMap::new));

        for(T head : heads) {
            linearize(head, g, order, results);
        }
        return results;

    }

    /**
     * Linearize and collect the results
     *
     * @param head the head
     * @param g the g
     * @param order the order
     * @param results the results
     * @return the list
     */
    private List<T> linearize(T head, Map<T, List<T>> g, boolean order, HashMap<T, List<T>> results) {

        if (results.containsKey(head)) {
            return results.get(head);
        }
        List<List<T>> sequences = new ArrayList<List<T>>();
        sequences.add(new ArrayList<T>(Arrays.asList(head)));
        for (T x : g.get(head)) {
            sequences.add(linearize(x, g, order, results));
        }
        if(order) {
            sequences.add(g.get(head));
        }
        List<T> res = merge(sequences);
        results.put(head, res);
        return res;
    }

    /**
     * Returns a comparator that compares {@link Map.Entry} in according to value size (assumes values are lists)
     *
     * @param <K> the type of the map keys
     * @param <T> the generic type
     * @param <V> the {@link Comparable} type of the map values
     * @return a comparator that compares {@link Map.Entry} in natural order on value.
     * @see Comparable
     * @since 1.8
     */
    private <K, U, V extends Comparable<? super List<U>>> Comparator<Map.Entry<K,List<U>>> comparingByValueSize() {
        return (Comparator<Map.Entry<K, List<U>>> & Serializable)
            (c1, c2) -> Integer.compare(c1.getValue().size(), c2.getValue().size());
    }

    /**
     * The linearizations are cached in a static map. This cache can lead
     * to classloader leaks. By calling this method, the cache is flushed.
     */
    public void clearCache() {
        results.clear();
    }


}
