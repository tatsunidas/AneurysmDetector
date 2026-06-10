package com.vis.aneurysmdetector.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 血管ネットワークのノード（分岐点または端点）を表すクラス。
 */
public class Node {

    public enum NodeType {
        ENDPOINT,    // 連結数が1の端点
        BIFURCATION  // 連結数が3以上の分岐点
    }

    private Point3D coordinate;
    private NodeType type;
    private List<Branch> connectedBranches;

    public Node(Point3D coordinate, NodeType type) {
        this.coordinate = coordinate;
        this.type = type;
        this.connectedBranches = new ArrayList<>();
    }

    public Point3D getCoordinate() {
        return coordinate;
    }

    public NodeType getType() {
        return type;
    }

    public List<Branch> getConnectedBranches() {
        return connectedBranches;
    }

    public void addConnectedBranch(Branch branch) {
        if (!connectedBranches.contains(branch)) {
            connectedBranches.add(branch);
        }
    }

    /**
     * ノードの座標ベースでの同一性判定（HashMap等での利用を想定）
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Node node = (Node) obj;
        return coordinate.equals(node.coordinate);
    }

    @Override
    public int hashCode() {
        return coordinate.hashCode();
    }
}