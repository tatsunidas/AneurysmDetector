/**
 * copyright visionary imaging services, inc.
 */
package com.vis.aneurysmdetector.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 検出された動脈瘤の候補を表現するクラス。
 * 危険度（最大膨らみ率など）や、3D空間上の位置情報を保持します。
 * 
 * @author tatsunidas
 */
public class AneurysmCandidate {

    private Branch parentBranch;
    private List<Point3D> involvedPoints;

    // 候補領域内での最大（最悪）の数値
    private double maxBulgeRatio;
    private double maxShapeIndex;
    private double peakGaussianCurvature;

    // 最も膨らんでいる中心座標
    private Point3D peakPoint;
    
 // --- 追加: 危険度スコア (0.0 ~ 100.0) ---
    private double score = 0.0;
    
    // 動脈瘤の形態分類（外部Enum）
    private CandidateType type;

    public AneurysmCandidate(Branch parentBranch) {
        this.parentBranch = parentBranch;
        this.involvedPoints = new ArrayList<>();
        this.maxBulgeRatio = 0.0;
        this.maxShapeIndex = 0.0;
        this.peakGaussianCurvature = 0.0;
        this.type = CandidateType.UNKNOWN; // 初期値としてUNKNOWNを設定
    }

    public void addPoint(Point3D p, double bulgeRatio, double shapeIndex, double gaussianCurvature) {
        involvedPoints.add(p);

        // 最大のBulgeRatioを更新した場合、そこを「ピーク座標」とする
        if (bulgeRatio > this.maxBulgeRatio) {
            this.maxBulgeRatio = bulgeRatio;
            this.peakPoint = p;
        }
        if (shapeIndex > this.maxShapeIndex) {
            this.maxShapeIndex = shapeIndex;
        }
        if (gaussianCurvature > this.peakGaussianCurvature) {
            this.peakGaussianCurvature = gaussianCurvature;
        }
    }

    // --- Getters & Setters ---

    public Branch getParentBranch() {
        return parentBranch;
    }

    public List<Point3D> getInvolvedPoints() {
        return involvedPoints;
    }

    public double getMaxBulgeRatio() {
        return maxBulgeRatio;
    }

    public double getMaxShapeIndex() {
        return maxShapeIndex;
    }

    public double getPeakGaussianCurvature() {
        return peakGaussianCurvature;
    }

    public Point3D getPeakPoint() {
        return peakPoint;
    }

    public CandidateType getType() {
        return type;
    }

    public void setType(CandidateType type) {
        this.type = type;
    }
    
 // --- Getter & Setter ---
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }

    @Override
    public String toString() {
        return String.format("Score: %5.1f | Aneurysm [%s] at (X:%d, Y:%d, Z:%d) | BulgeRatio: %5.2f | ShapeIndex: %.2f",
                score, type, peakPoint.x, peakPoint.y, peakPoint.z, maxBulgeRatio, maxShapeIndex);
    }
}