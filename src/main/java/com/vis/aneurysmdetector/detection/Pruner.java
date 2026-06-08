package com.vis.aneurysmdetector.detection;

import com.vis.aneurysmdetector.core.AneurysmCandidate;
import com.vis.aneurysmdetector.core.Image3D;
import com.vis.aneurysmdetector.core.Point3D;
import com.vis.aneurysmdetector.core.VesselBranch;
import com.vis.aneurysmdetector.core.VesselTree;
import com.vis.aneurysmdetector.feature.DiameterMetrics;
import com.vis.aneurysmdetector.feature.ShapeMetrics;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 血管ツリー内の短い枝を評価し、ノイズの足切り（プルーニング）を行うとともに、
 * 側壁瘤の候補（Spurious Branch）を抽出するクラス。
 */
public class Pruner {

    private ShapeMetrics shapeMetrics;
    private DiameterMetrics diameterMetrics;
    private SaliencyScorer scorer;
    
    // 短い枝と判定する閾値（中心線のボクセル数）
    private static final int SHORT_BRANCH_THRESHOLD = 15;
    
    // プルーニング（ノイズとして削除）するかどうかのスコア閾値
    private static final double PRUNING_SCORE_THRESHOLD = 0.3;

    public Pruner(Image3D binaryMask) {
        this.shapeMetrics = new ShapeMetrics(binaryMask);
        this.diameterMetrics = new DiameterMetrics(binaryMask);
        this.scorer = new SaliencyScorer();
    }

    /**
     * ツリー内の枝を評価し、ノイズのフラグ立てと動脈瘤候補のリストを生成します。
     */
    public List<AneurysmCandidate> pruneAndExtractCandidates(VesselTree tree) {
        List<AneurysmCandidate> candidates = new ArrayList<>();

        for (VesselBranch branch : tree.getBranches()) {
            List<Point3D> centerline = branch.getCenterline();
            
            // 短い枝（行き止まり）だけを評価の対象とする
            if (centerline.size() < SHORT_BRANCH_THRESHOLD && branch.getEndNode() == null) {
                
                // 1. 特徴量の計算
                // ※本来は枝の周囲のボクセル（膨張領域）を抽出してShapeMetricsに渡しますが、ここではモックとして中心線を使用
                double[] shapeFeatures = shapeMetrics.evaluateAllMetrics(centerline);
                double sphericity = shapeFeatures[2];
                double vtsRatio = shapeFeatures[3];
                double diameterChange = diameterMetrics.calculateDiameterChangeRate(branch);

                // 2. サリアンシースコアの計算
                double score = scorer.calculateScore(sphericity, vtsRatio, diameterChange);

                if (score < PRUNING_SCORE_THRESHOLD) {
                    // スコアが低い場合は、単なるノイズ（ヒゲ）としてプルーニングフラグを立てる
                    branch.setPruned(true);
                } else {
                    // スコアが高い場合は、先端が膨らんだ動脈瘤候補として抽出
                    Point3D centerPoint = centerline.get(centerline.size() / 2); // 枝の中央
                    AneurysmCandidate candidate = new AneurysmCandidate(
                            "Cand-" + UUID.randomUUID().toString(),
                            AneurysmCandidate.CandidateType.SPURIOUS_BRANCH,
                            centerPoint
                    );
                    
                    candidate.setRelatedBranch(branch);
                    candidate.setAnatomicalLabel(branch.getAnatomicalLabel());
                    candidate.setSphericity(sphericity);
                    candidate.setVolumeToSurfaceRatio(vtsRatio);
                    candidate.setDiameterChangeRate(diameterChange);
                    candidate.setSaliencyScore(score);

                    candidates.add(candidate);
                }
            }
        }
        return candidates;
    }
}