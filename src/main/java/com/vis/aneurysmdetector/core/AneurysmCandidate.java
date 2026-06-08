package com.vis.aneurysmdetector.core;

/**
 * 検出された動脈瘤の候補を保持するクラス。
 * アルゴリズム層からUI層への受け渡しに使用されます。
 */
public class AneurysmCandidate {
    
    public enum CandidateType {
        BIFURCATION_ANEURYSM, // 分岐部瘤
        SIDEWALL_ANEURYSM,    // 側壁瘤
        SPURIOUS_BRANCH       // 俯瞰時の「短い枝」候補
    }

    private String candidateId;
    private CandidateType type;
    
    // 候補が位置する中心座標
    private Point3D centerPoint;
    
    // 関連する枝や分岐部（Typeに応じてどちらかがセットされる）
    private BifurcationNode relatedNode;
    private VesselBranch relatedBranch;
    
    // --- 幾何学的特徴量 ---
    private double sphericity;
    private double volumeToSurfaceRatio;
    private double diameterChangeRate;
    private double saliencyScore; // リスクの総合スコア（優先度付け用）
    
    // --- UIステータス ---
    // ユーザーが「これは瘤ではない（偽陽性）」と判断した場合にtrueになる
    private boolean isCleared = false;
    
    private String anatomicalLabel;

    public AneurysmCandidate(String candidateId, CandidateType type, Point3D centerPoint) {
        this.candidateId = candidateId;
        this.type = type;
        this.centerPoint = centerPoint;
    }

    // --- Getters & Setters ---

    public String getCandidateId() { return candidateId; }
    public CandidateType getType() { return type; }
    public Point3D getCenterPoint() { return centerPoint; }

    public BifurcationNode getRelatedNode() { return relatedNode; }
    public void setRelatedNode(BifurcationNode relatedNode) { this.relatedNode = relatedNode; }

    public VesselBranch getRelatedBranch() { return relatedBranch; }
    public void setRelatedBranch(VesselBranch relatedBranch) { this.relatedBranch = relatedBranch; }

    public double getSphericity() { return sphericity; }
    public void setSphericity(double sphericity) { this.sphericity = sphericity; }

    public double getVolumeToSurfaceRatio() { return volumeToSurfaceRatio; }
    public void setVolumeToSurfaceRatio(double ratio) { this.volumeToSurfaceRatio = ratio; }

    public double getDiameterChangeRate() { return diameterChangeRate; }
    public void setDiameterChangeRate(double rate) { this.diameterChangeRate = rate; }

    public double getSaliencyScore() { return saliencyScore; }
    public void setSaliencyScore(double score) { this.saliencyScore = score; }

    public boolean isCleared() { return isCleared; }
    public void setCleared(boolean cleared) { isCleared = cleared; }

    public String getAnatomicalLabel() { return anatomicalLabel; }
    public void setAnatomicalLabel(String label) { this.anatomicalLabel = label; }
}