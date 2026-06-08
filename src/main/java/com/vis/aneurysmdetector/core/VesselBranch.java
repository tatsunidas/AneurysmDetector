package com.vis.aneurysmdetector.core;

import java.util.List;

/**
 * 血管の「枝」を表現するクラス。
 * グラフネットワークのエッジ（リンク）として機能します。
 */
public class VesselBranch {
    private String branchId;
    private List<Point3D> centerline;
    
    // 接続されているノード（末端の場合はnullになり得る）
    private BifurcationNode startNode;
    private BifurcationNode endNode;
    
    // AnatomyLabelerによって付与される解剖学的タグ (例: "MCA", "ICA")
    private String anatomicalLabel;
    
    // スケルトンのトポロジー解析で「短い枝（Spurious branch）」と判定されたか
    private boolean isPruned = false;

    public VesselBranch(String branchId, List<Point3D> centerline) {
        this.branchId = branchId;
        this.centerline = centerline;
    }

    public void setNodes(BifurcationNode start, BifurcationNode end) {
        this.startNode = start;
        this.endNode = end;
    }

    public String getBranchId() { return branchId; }
    public List<Point3D> getCenterline() { return centerline; }
    public BifurcationNode getStartNode() { return startNode; }
    public BifurcationNode getEndNode() { return endNode; }
    
    public String getAnatomicalLabel() { return anatomicalLabel; }
    public void setAnatomicalLabel(String label) { this.anatomicalLabel = label; }

    public boolean isPruned() { return isPruned; }
    public void setPruned(boolean pruned) { isPruned = pruned; }
}