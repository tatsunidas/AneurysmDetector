package com.vis.aneurysmdetector.core;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 構築された血管ネットワーク全体（NodeとBranchの集合）を管理するコンテナクラス。
 * グラフの動的な改修（枝切りなど）や統計情報の取得に対応しています。
 */
public class VesselTree {

    private List<Node> nodes;
    private List<Branch> branches;

    public VesselTree(List<Node> nodes, List<Branch> branches) {
        this.nodes = new ArrayList<>(nodes);
        this.branches = new ArrayList<>(branches);
    }

    public List<Node> getNodes() {
        return nodes;
    }

    public List<Branch> getBranches() {
        return branches;
    }

    /**
     * 指定された枝（Branch）をツリーから安全に削除します。
     * 接続されていた両端のノードの内部参照からも自動で切り離します。
     */
    public void removeBranch(Branch branch) {
        if (branch == null) return;
        
        // 1. ツリーのメインリストから削除
        branches.remove(branch);

        // 2. 始点ノード・終点ノードの接続リストからこの枝を切り離す
        if (branch.getStartNode() != null) {
            branch.getStartNode().getConnectedBranches().remove(branch);
        }
        if (branch.getEndNode() != null) {
            branch.getEndNode().getConnectedBranches().remove(branch);
        }
    }

    /**
     * 指定されたノード（Node）をツリーから安全に削除します。
     */
    public void removeNode(Node node) {
        if (node == null) return;
        nodes.remove(node);
    }

	/**
	 * 枝切り（プルーニング）などの結果、どこにも繋がらなくなった孤立ノード（接続数0）を ツリーから一括でクリーンアップします。
	 * 
	 * @return 削除された孤立ノードの数
	 */
	public int cleanupIsolatedNodes() {
		int initialCount = nodes.size();
		Iterator<Node> iterator = nodes.iterator();

		while (iterator.hasNext()) {
			Node node = iterator.next();
			// 接続されている枝の数が0であれば孤立ノードとみなす
			if (node.getConnectedBranches().isEmpty()) {
				iterator.remove();
			}
		}

		return initialCount - nodes.size();
	}

    /**
     * ネットワーク全体の総延長（物理的な血管の総長さ: mm）を計算します。
     */
    public double getTotalLengthMM() {
        double total = 0.0;
        for (Branch b : branches) {
            total += b.getLength();
        }
        return total;
    }

    /**
     * 現在のネットワークの統計情報をコンソールに出力します（デバッグ用）。
     */
    public void printStatistics() {
        int endpoints = 0;
        int bifurcations = 0;
        for (Node n : nodes) {
            if (n.getType() == Node.NodeType.ENDPOINT) endpoints++;
            else if (n.getType() == Node.NodeType.BIFURCATION) bifurcations++;
        }

        System.out.println("=== Vessel Tree Graph Statistics ===");
        System.out.println("  Total Nodes        : " + nodes.size() + " (Endpoints: " + endpoints + ", Bifurcations: " + bifurcations + ")");
        System.out.println("  Total Branches     : " + branches.size());
        System.out.println("  Total Vessel Length: " + String.format("%.2f", getTotalLengthMM()) + " mm");
        System.out.println("====================================");
    }
}