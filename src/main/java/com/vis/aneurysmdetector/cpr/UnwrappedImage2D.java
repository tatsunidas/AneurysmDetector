package com.vis.aneurysmdetector.cpr;

import com.vis.aneurysmdetector.core.AneurysmCandidate;
import com.vis.aneurysmdetector.core.VesselBranch;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 血管を展開した2D画像（距離マップ）を保持し、異常起伏を探索するクラス。
 * 縦軸（Y）は血管の長さ方向、横軸（X）は角度（0〜360度）、画素値は中心線から壁までの距離です。
 */
public class UnwrappedImage2D {

    private VesselBranch sourceBranch;
    
    // distanceMap[z][theta] : zは中心線のインデックス、thetaは角度のステップ
    private double[][] distanceMap;
    
    // 角度の分割数（例: 36なら10度刻み）
    private int angleSteps;
    private int lengthSteps;

    // UIの各解剖学的部位ごとのClearチェック用ステータス
    private boolean isCleared = false;

    public UnwrappedImage2D(VesselBranch sourceBranch, double[][] distanceMap) {
        this.sourceBranch = sourceBranch;
        this.distanceMap = distanceMap;
        this.lengthSteps = distanceMap.length;
        this.angleSteps = (lengthSteps > 0) ? distanceMap[0].length : 0;
    }

    /**
     * 2Dの展開図を走査し、局所的に突出している領域（側壁瘤の候補）を検出します。
     * @return 検出された異常箇所のリスト（UI連携用）
     */
    public List<AneurysmCandidate> findAnomalies() {
        List<AneurysmCandidate> anomalies = new ArrayList<>();
        if (lengthSteps == 0 || angleSteps == 0) return anomalies;

        // ====================================================================
        // 【アルゴリズムの基本ロジック】
        // 1. 各Z位置（断面）における「基準となる半径（中央値など）」を計算。
        // 2. 基準半径よりも局所的に大きく突出している部分（閾値超え）を探索。
        // 3. 突出部が一定の面積と高さを持つ場合、側壁瘤候補として抽出。
        // ====================================================================

        double thresholdRatio = 1.5; // 基準半径の1.5倍以上の突出を異常とみなす

        for (int z = 2; z < lengthSteps - 2; z++) { // 両端はノイズが多いため除外
            double baselineRadius = calculateMedianRadiusAt(z);
            if (baselineRadius <= 0) continue;

            for (int t = 0; t < angleSteps; t++) {
                double currentRadius = distanceMap[z][t];
                
                if (currentRadius > baselineRadius * thresholdRatio) {
                    // 異常な起伏を検出。実際にはここで2Dの連結成分ラベリング等を行い
                    // ひとまとまりの領域として抽出します。
                    
                    AneurysmCandidate candidate = new AneurysmCandidate(
                            "Sidewall-" + UUID.randomUUID().toString(),
                            AneurysmCandidate.CandidateType.SIDEWALL_ANEURYSM,
                            sourceBranch.getCenterline().get(z) // 異常があった中心座標
                    );
                    
                    candidate.setRelatedBranch(sourceBranch);
                    candidate.setAnatomicalLabel(sourceBranch.getAnatomicalLabel());
                    
                    // 幾何学的特徴量（展開図上の高さや面積から擬似的に算出）
                    candidate.setDiameterChangeRate(currentRadius / baselineRadius);
                    candidate.setSaliencyScore(calculateLocalScore(currentRadius, baselineRadius));
                    
                    anomalies.add(candidate);
                    
                    // 同じZ断面での重複登録を防ぐため、一度見つけたら次のZへ進む（簡易実装）
                    break; 
                }
            }
        }
        return anomalies;
    }

    /**
     * 指定された断面（Zインデックス）における半径の中央値を計算します。
     */
    private double calculateMedianRadiusAt(int z) {
        double[] sliceRadii = distanceMap[z].clone();
        java.util.Arrays.sort(sliceRadii);
        return sliceRadii[angleSteps / 2];
    }

    private double calculateLocalScore(double radius, double baseline) {
        double ratio = radius / baseline;
        double score = (ratio - 1.0) / 1.5; 
        return Math.min(1.0, Math.max(0.0, score));
    }

    // --- Getters & Setters ---

    public VesselBranch getSourceBranch() { return sourceBranch; }
    public double[][] getDistanceMap() { return distanceMap; }
    public int getAngleSteps() { return angleSteps; }
    public int getLengthSteps() { return lengthSteps; }

    public boolean isCleared() { return isCleared; }
    public void setCleared(boolean cleared) { isCleared = cleared; }
}