/**
 * copyright visionary imaging services, inc.
 */
package com.vis.aneurysmdetector.detection;

import com.vis.aneurysmdetector.core.AneurysmCandidate;
import com.vis.aneurysmdetector.core.Branch;
import com.vis.aneurysmdetector.core.Point3D;
import com.vis.aneurysmdetector.core.VesselTree;

import java.util.ArrayList;
import java.util.List;

/**
 * 血管ネットワークの特徴量を評価し、動脈瘤の候補を検出するクラス。
 * 
 * @author tatsunidas
 */
public class AneurysmDetector {

    // 判定閾値（医学的アプローチに基づくデフォルト値）
    private double thresholdBulgeRatio = 1.35;
    private double thresholdShapeIndex = 0.65;
    private double thresholdGaussianCurvature = 0.0;

    public AneurysmDetector() {}

    public AneurysmDetector(double minBulge, double minShapeIndex, double minCurvature) {
        this.thresholdBulgeRatio = minBulge;
        this.thresholdShapeIndex = minShapeIndex;
        this.thresholdGaussianCurvature = minCurvature;
    }

    /**
     * 血管ツリーを走査し、動脈瘤候補のリストを返します。
     * @param tree 特徴量（Radius, Bulge Ratio, Curvatures）が抽出済みのVesselTree
     * @return 検出された動脈瘤候補のリスト
     */
    public List<AneurysmCandidate> detect(VesselTree tree) {
        System.out.println("Executing Aneurysm Detection...");
        System.out.println(String.format("  Thresholds -> Bulge >= %.2f, SI >= %.2f, K > %.2f", 
                thresholdBulgeRatio, thresholdShapeIndex, thresholdGaussianCurvature));

        List<AneurysmCandidate> candidates = new ArrayList<>();

        for (Branch branch : tree.getBranches()) {
            List<Point3D> path = branch.getPath();
            List<Double> bulgeRatios = branch.getBulgeRatios();
            List<Double> shapeIndices = branch.getShapeIndices();
            List<Double> gaussianCurvatures = branch.getGaussianCurvatures();

            AneurysmCandidate currentCandidate = null;

            for (int i = 0; i < path.size(); i++) {
                double br = bulgeRatios.get(i);
                double si = shapeIndices.get(i);
                double k = gaussianCurvatures.get(i);

                // 3つの条件をすべて満たしているかチェック
                boolean isAnomaly = (br >= thresholdBulgeRatio) && 
                                    (si >= thresholdShapeIndex) && 
                                    (k > thresholdGaussianCurvature);

                if (isAnomaly) {
                    // 連続している場合は既存の候補に追加、新規の場合は新しく作成
                    if (currentCandidate == null) {
                        currentCandidate = new AneurysmCandidate(branch);
                    }
                    currentCandidate.addPoint(path.get(i), br, si, k);
                } else {
                    // 条件から外れた場合、そこまでの候補をリストに保存してリセット
                    if (currentCandidate != null) {
                        candidates.add(currentCandidate);
                        currentCandidate = null;
                    }
                }
            }

            // 枝の末尾で候補が終了した場合の回収
            if (currentCandidate != null) {
                candidates.add(currentCandidate);
            }
        }

        // ノイズ除去: 関与するポイント数が少なすぎる（例: 2ピクセル以下）候補は除外する
        List<AneurysmCandidate> filteredCandidates = new ArrayList<>();
        for (AneurysmCandidate c : candidates) {
            if (c.getInvolvedPoints().size() >= 3) {
                filteredCandidates.add(c);
            }
        }

        System.out.println("  Detection completed. Found " + filteredCandidates.size() + " aneurysm candidates.");
        return filteredCandidates;
    }
}