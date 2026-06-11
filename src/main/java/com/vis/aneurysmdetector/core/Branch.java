package com.vis.aneurysmdetector.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 血管ネットワークの枝（Node間を結ぶ経路）と、
 * その経路上における物理的な特徴量を管理するクラス。
 */
public class Branch {

    private Node startNode;
    private Node endNode;
    private List<Point3D> path;
    private double length; // 物理的な長さ (mm)

    // --- Phase 4 追加フィールド ---
    // 各経路ポイント（path）に1対1で対応する「本来の内接球半径 (mm)」
    private List<Double> inscribedRadii;
    // 各経路ポイントに対応する「最大膨らみ率 (Bulge Ratio)」
    private List<Double> bulgeRatios; 
    private List<Double> shapeIndices;
    private List<Double> gaussianCurvatures;

    public Branch(Node startNode, Node endNode, List<Point3D> path, double length) {
        this.startNode = startNode;
        this.endNode = endNode;
        this.path = path;
        this.length = length;
        
        // 特徴量リストをパスの長さと同じサイズで初期化
        this.inscribedRadii = new ArrayList<>(path.size());
        this.bulgeRatios = new ArrayList<>(path.size());
        for (int i = 0; i < path.size(); i++) {
            this.inscribedRadii.add(0.0);
            this.bulgeRatios.add(0.0);
        }
         // コンストラクタ内での初期化（既存の bulgeRatios の下に追加）
        this.shapeIndices = new ArrayList<>(path.size());
        this.gaussianCurvatures = new ArrayList<>(path.size());
        for (int i = 0; i < path.size(); i++) {
            this.shapeIndices.add(-1.0); // SIの最小値で初期化
            this.gaussianCurvatures.add(0.0);
        }
    }
    
    public Node getStartNode() { return startNode; }
    public Node getEndNode() { return endNode; }
    public List<Point3D> getPath() { return path; }
    public double getLength() { return length; }
    
    public Node getOppositeNode(Node node) {
        if (node.equals(startNode)) return endNode;
        else if (node.equals(endNode)) return startNode;
        return null;
    }

    // --- Phase 4 追加ゲッター＆セッター ---
    public List<Double> getInscribedRadii() {
        return inscribedRadii;
    }

    public void setInscribedRadiusAt(int index, double radius) {
        if (index >= 0 && index < inscribedRadii.size()) {
            inscribedRadii.set(index, radius);
        }
    }

    public List<Double> getBulgeRatios() {
        return bulgeRatios;
    }

    public void setBulgeRatioAt(int index, double ratio) {
        if (index >= 0 && index < bulgeRatios.size()) {
            bulgeRatios.set(index, ratio);
        }
    }
    
	// --- 追加ゲッター＆セッター ---
	public List<Double> getShapeIndices() {
		return shapeIndices;
	}

	public void setShapeIndexAt(int index, double si) {
		if (index >= 0 && index < shapeIndices.size())
			shapeIndices.set(index, si);
	}

	public List<Double> getGaussianCurvatures() {
		return gaussianCurvatures;
	}

	public void setGaussianCurvatureAt(int index, double k) {
		if (index >= 0 && index < gaussianCurvatures.size())
			gaussianCurvatures.set(index, k);
	}
}