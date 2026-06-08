package com.vis.aneurysmdetector.detection;

/**
 * 算出された各特徴量を組み合わせて、「動脈瘤らしさ（サリアンシースコア）」を計算する評価エンジン。
 */
public class SaliencyScorer {

    // 特徴量の重み付け（合計が1.0になるように設定）
    private static final double WEIGHT_SPHERICITY = 0.4;
    private static final double WEIGHT_DIAMETER_CHANGE = 0.4;
    private static final double WEIGHT_COMPACTNESS = 0.2;

    public SaliencyScorer() {}

    /**
     * 動脈瘤のサリアンシースコア (0.0 ~ 1.0) を計算します。
     * * @param sphericity 真球度 (0.0 ~ 1.0, 1.0が完全な球)
     * @param volumeToSurfaceRatio 体積表面積比 (大きいほどコンパクト)
     * @param diameterChangeRate 直径変化率 (正常血管からの膨張率。1.5以上で瘤の疑い)
     * @return 0.0 ~ 1.0 のスコア（1.0に近いほど怪しい）
     */
    public double calculateScore(double sphericity, double volumeToSurfaceRatio, double diameterChangeRate) {
        
        // 1. 直径変化率の正規化 (1.0~2.5 の範囲を 0.0~1.0 にマッピング。1.0以下は0とする)
        double normDiameterChange = 0.0;
        if (diameterChangeRate > 1.0) {
            normDiameterChange = (diameterChangeRate - 1.0) / 1.5; 
            normDiameterChange = Math.min(1.0, normDiameterChange); // 最大1.0でクリップ
        }

        // 2. 体積表面積比の正規化（簡易的なシグモイド的マッピング）
        // ※ 実際のMRAの解像度に合わせてチューニングが必要なパラメータです。
        double normCompactness = Math.min(1.0, volumeToSurfaceRatio / 2.0);

        // 3. 重み付き和によるベーススコアの算出
        double baseScore = (sphericity * WEIGHT_SPHERICITY) +
                           (normDiameterChange * WEIGHT_DIAMETER_CHANGE) +
                           (normCompactness * WEIGHT_COMPACTNESS);

        // 4. スコアの非線形な強調（怪しいものをより際立たせる）
        return applySigmoid(baseScore);
    }

    /**
     * スコアを0.0~1.0の範囲にS字カーブで押し込みます。
     */
    private double applySigmoid(double x) {
        // x=0.5付近で傾きが急になるシグモイド
        double k = 10.0;
        double x0 = 0.5;
        return 1.0 / (1.0 + Math.exp(-k * (x - x0)));
    }
}