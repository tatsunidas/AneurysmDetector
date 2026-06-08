package com.vis.aneurysmdetector.anatomy;

import com.vis.aneurysmdetector.core.BifurcationNode;
import com.vis.aneurysmdetector.core.VesselBranch;
import com.vis.aneurysmdetector.core.VesselTree;

import java.util.List;

/**
 * トポロジーと空間座標を解析し、VesselTreeの各枝や分岐部に
 * 解剖学的名称（ICA, MCA, ACA, Basilarなど）のタグを付与するクラス。
 */
public class AnatomyLabeler {

    // 空間座標を物理サイズで評価するための定数（Image3DのSpacingを利用可能）
    private double spacingX;
    private double spacingY;
    private double spacingZ;

    public AnatomyLabeler(double spacingX, double spacingY, double spacingZ) {
        this.spacingX = spacingX;
        this.spacingY = spacingY;
        this.spacingZ = spacingZ;
    }

    /**
     * グラフ構造を解析し、解剖学的ラベルを割り当てます。
     * @param tree 構築済みの血管ツリー
     */
    public void label(VesselTree tree) {
        List<VesselBranch> branches = tree.getBranches();
        if (branches == null || branches.isEmpty()) return;

        // ====================================================================
        // 【アルゴリズムの基本ロジック】
        // 1. Z軸の最も低い（足側）にある太い枝を2本（左右の内頸動脈: ICA）と、
        //    背側（Y軸）かつ中央（X軸）にある1本（脳底動脈: BA）をルートとして特定する。
        // 2. グラフ探索（DFS/BFS）を行い、ICAから分岐する枝を追跡。
        // 3. 外側（X軸の左右方向）に伸びる枝を中大脳動脈（MCA）、
        //    内側前向き（X軸中央、Y軸前方）に伸びる枝を前大脳動脈（ACA）としてタグ付けする。
        // ====================================================================

        // 例: ルートの特定（簡略化されたモックロジック）
        VesselBranch rightICA = findRootBranch(tree, "Right_ICA");
        VesselBranch leftICA = findRootBranch(tree, "Left_ICA");
        VesselBranch basilar = findRootBranch(tree, "BA");

        if (rightICA != null) {
            rightICA.setAnatomicalLabel("R-ICA");
            propagateLabels(rightICA, "R-");
        }
        
        if (leftICA != null) {
            leftICA.setAnatomicalLabel("L-ICA");
            propagateLabels(leftICA, "L-");
        }
    }

    /**
     * Z軸（スライス位置）が最も低く、特定の条件を満たす枝をルートとして特定します。
     */
    private VesselBranch findRootBranch(VesselTree tree, String type) {
        // FIXME: 実際のロジックを実装
        // Z座標の最小値を持つ端点を含み、かつX,Y座標のヒューリスティクス
        // （例えばXが中央より右なら右ICA、左なら左ICA、中央後方ならBA）で特定。
        return null; 
    }

    /**
     * ルートから再帰的にグラフを辿り、分岐の方向（ベクトル）からMCAやACAを判定します。
     */
    private void propagateLabels(VesselBranch currentBranch, String sidePrefix) {
        BifurcationNode nextNode = currentBranch.getEndNode();
        if (nextNode == null) return;

        List<VesselBranch> children = nextNode.getConnectedBranches();
        
        for (VesselBranch child : children) {
            // 逆流を防ぐ
            if (child == currentBranch) continue;

            // 枝の始点から終点への方向ベクトルを計算
            double[] vector = calculateDirectionVector(child);
            
            // X方向（左右）の成分が大きい場合はMCA
            if (Math.abs(vector[0]) > Math.abs(vector[1]) && Math.abs(vector[0]) > Math.abs(vector[2])) {
                child.setAnatomicalLabel(sidePrefix + "MCA");
            } 
            // Y方向（前方）の成分が大きい場合はACA
            else if (vector[1] < 0) { // Y軸の向き（前後）はDICOMの定義(LPS/RAS)に依存
                child.setAnatomicalLabel(sidePrefix + "ACA");
            } else {
                child.setAnatomicalLabel(sidePrefix + "Unknown");
            }

            // 再帰的にラベリング
            propagateLabels(child, sidePrefix);
        }
    }

    /**
     * 枝の始点から終点への物理的な方向ベクトルを計算します。
     */
    private double[] calculateDirectionVector(VesselBranch branch) {
        if (branch.getCenterline().size() < 2) return new double[]{0,0,0};
        
        com.vis.aneurysmdetector.core.Point3D start = branch.getCenterline().get(0);
        com.vis.aneurysmdetector.core.Point3D end = branch.getCenterline().get(branch.getCenterline().size() - 1);
        
        // 物理サイズ（Spacing）を考慮したベクトル計算
        double dx = (end.x - start.x) * spacingX;
        double dy = (end.y - start.y) * spacingY;
        double dz = (end.z - start.z) * spacingZ;
        
        return new double[]{dx, dy, dz};
    }
}