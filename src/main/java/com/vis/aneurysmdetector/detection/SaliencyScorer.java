package com.vis.aneurysmdetector.detection;

import com.vis.aneurysmdetector.core.AneurysmCandidate;
import java.util.List;

/**
 * 算出された各特徴量を組み合わせて、「動脈瘤らしさ（サリアンシースコア: 0〜100）」を計算する評価エンジン。
 * 現在の曲率ベースの指標と、将来の体積ベースの指標（真球度など）をハイブリッドで評価可能な拡張設計です。
 */
public class SaliencyScorer {

    // --- 特徴量の重み付けマトリクス ---
    // 合計が1.0になるように設計されています。

    // [現在の稼働指標：トポロジー・曲率ベース]
    private static final double WEIGHT_SHAPE_INDEX = 0.35; // ドーム形状の完璧さ
    private static final double WEIGHT_BULGE_RATIO = 0.35; // 正常血管からの膨張率（旧：直径変化率に相当）

    // [将来の拡張指標：3Dメッシュ・体積ベース]
    private static final double WEIGHT_SPHERICITY = 0.20;  // 真球度
    private static final double WEIGHT_COMPACTNESS = 0.10; // 体積表面積比（コンパクトさ）

    public SaliencyScorer() {}

    /**
     * 候補リストの各要素にスコアを計算・セットし、スコアの降順（危険な順）にソートします。
     * @param candidates 検出済みの動脈瘤候補リスト
     */
    public void scoreAndSort(List<AneurysmCandidate> candidates) {
        System.out.println("Executing Saliency Scoring (Sigmoid Enhanced)...");

        for (AneurysmCandidate candidate : candidates) {
            double score = calculateScore(candidate);
            candidate.setScore(score);
        }

        // スコアの高い順（降順）にソート
        candidates.sort((c1, c2) -> Double.compare(c2.getScore(), c1.getScore()));
        
        System.out.println("  Scoring and sorting completed.");
    }

    /**
     * 1つの候補に対する危険度スコア（0.0 〜 100.0）を計算します。
     */
    private double calculateScore(AneurysmCandidate c) {
        // ==========================================
        // 1. 各特徴量の取得と正規化 (0.0 ~ 1.0 にマッピング)
        // ==========================================
        
        // 【現在実装済みの指標】
        double br = c.getMaxBulgeRatio();
        double si = c.getMaxShapeIndex();

        // Shape Index の正規化 (閾値0.65 〜 1.0 を 0.0〜1.0へ)
        double normSI = Math.max(0.0, Math.min((si - 0.65) / (1.0 - 0.65), 1.0));
        
        // Bulge Ratio の正規化 (閾値1.35 〜 5.0 を 0.0〜1.0へ。5.0以上は満点)
        double normBR = Math.max(0.0, Math.min((br - 1.35) / (5.0 - 1.35), 1.0));

        // 【将来実装予定の指標】
        // ※ 将来 AneurysmCandidate に c.getSphericity() 等が追加されたらここを書き換えます
        double sphericity = 0.0; 
        double volumeToSurfaceRatio = 0.0;
        
        double normSphericity = Math.max(0.0, Math.min(sphericity, 1.0));
        double normCompactness = Math.max(0.0, Math.min(volumeToSurfaceRatio / 2.0, 1.0));

        // ==========================================
        // 2. 動的重み付けによるベーススコアの算出
        // ==========================================
        
        double activeWeightTotal = WEIGHT_SHAPE_INDEX + WEIGHT_BULGE_RATIO; // 現在は 0.70
        // ※将来、体積計算が実装されたら以下のように動的に足し合わせます。
        // if (sphericity > 0) activeWeightTotal += WEIGHT_SPHERICITY;
        // if (volumeToSurfaceRatio > 0) activeWeightTotal += WEIGHT_COMPACTNESS;

        double weightedSum = (normSI * WEIGHT_SHAPE_INDEX) + 
                             (normBR * WEIGHT_BULGE_RATIO) + 
                             (normSphericity * WEIGHT_SPHERICITY) + 
                             (normCompactness * WEIGHT_COMPACTNESS);

        // 未実装の指標があっても、実装済みの指標だけで 0.0〜1.0 のフルスケールになるよう正規化
        double baseScore = weightedSum / activeWeightTotal;

        // ==========================================
        // 3. 非線形なシグモイド強調と 100点満点化
        // ==========================================
        
        double finalScore = applySigmoid(baseScore) * 100.0;
        return finalScore;
    }

    /**
     * スコアを0.0~1.0の範囲にS字カーブで押し込みます。
     * 中途半端なスコアを抑制し、確度の高いものを際立たせます。
     */
    private double applySigmoid(double x) {
        // x=0.5付近で傾きが急になるシグモイド関数
        double k = 10.0;  // カーブの急峻さ
        double x0 = 0.5;  // 変曲点（50%のライン）
        return 1.0 / (1.0 + Math.exp(-k * (x - x0)));
    }
}