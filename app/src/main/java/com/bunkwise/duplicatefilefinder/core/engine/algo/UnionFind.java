package com.bunkwise.duplicatefilefinder.core.engine.algo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Disjoint-set / union-find with path compression + union by rank (ARCHITECTURE
 * §5.7). Groups pairwise matches into connected components in ~O(alpha(n)) per
 * op. Keyed by arbitrary int ids (indices into the feature list).
 */
public final class UnionFind {

    private final int[] parent;
    private final int[] rank;

    public UnionFind(int n) {
        parent = new int[n];
        rank = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
    }

    public int find(int x) {
        int root = x;
        while (parent[root] != root) root = parent[root];
        // Path compression.
        while (parent[x] != root) {
            int next = parent[x];
            parent[x] = root;
            x = next;
        }
        return root;
    }

    public void union(int a, int b) {
        int ra = find(a);
        int rb = find(b);
        if (ra == rb) return;
        if (rank[ra] < rank[rb]) {
            parent[ra] = rb;
        } else if (rank[ra] > rank[rb]) {
            parent[rb] = ra;
        } else {
            parent[rb] = ra;
            rank[ra]++;
        }
    }

    /** Returns components with size >= 2 (a duplicate group needs >= 2 files). */
    public List<List<Integer>> componentsMinSize2() {
        Map<Integer, List<Integer>> byRoot = new HashMap<>();
        for (int i = 0; i < parent.length; i++) {
            int root = find(i);
            List<Integer> list = byRoot.get(root);
            if (list == null) {
                list = new ArrayList<>();
                byRoot.put(root, list);
            }
            list.add(i);
        }
        List<List<Integer>> out = new ArrayList<>();
        for (List<Integer> c : byRoot.values()) {
            if (c.size() >= 2) out.add(c);
        }
        return out;
    }
}
