package com.vis.aneurysmdetector.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 血管全体を表現するグラフ構造のルートコンテナクラス。
 */
public class VesselTree {
    private List<BifurcationNode> nodes;
    private List<VesselBranch> branches;

    public VesselTree() {
        this.nodes = new ArrayList<>();
        this.branches = new ArrayList<>();
    }

    public void addNode(BifurcationNode node) {
        if (!nodes.contains(node)) {
            nodes.add(node);
        }
    }

    public void addBranch(VesselBranch branch) {
        if (!branches.contains(branch)) {
            branches.add(branch);
        }
    }

    public List<BifurcationNode> getNodes() { return nodes; }
    public List<VesselBranch> getBranches() { return branches; }
}