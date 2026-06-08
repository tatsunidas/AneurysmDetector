package com.vis.aneurysmdetector.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 血管の「分岐部」を表現するクラス。
 * グラフネットワークのノードとして機能します。
 */
public class BifurcationNode {
    private String nodeId;
    private Point3D centerPoint;
    private List<VesselBranch> connectedBranches;

    public BifurcationNode(String nodeId, Point3D centerPoint) {
        this.nodeId = nodeId;
        this.centerPoint = centerPoint;
        this.connectedBranches = new ArrayList<>();
    }

    public void addBranch(VesselBranch branch) {
        if (!connectedBranches.contains(branch)) {
            connectedBranches.add(branch);
        }
    }

    public String getNodeId() { return nodeId; }
    public Point3D getCenterPoint() { return centerPoint; }
    public List<VesselBranch> getConnectedBranches() { return connectedBranches; }
}