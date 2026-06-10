package com.vis.aneurysmdetector.anatomy;

import com.vis.aneurysmdetector.core.Branch;
import com.vis.aneurysmdetector.core.Node;
import com.vis.aneurysmdetector.core.Point3D;
import com.vis.aneurysmdetector.core.VesselTree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 血管ネットワーク（グラフ）のトポロジー最適化を行うクラス。
 * 短いヒゲの刈り取り（Pruning）と、直線上の余分なノードの結合（Merging）を担います。
 */
public class GraphPruner {

    /**
     * 短い末端の枝を反復的（再帰的）に削除します。
     * @param tree 処理対象の血管ネットワーク
     * @param minLengthMM 削除対象とする末端枝の閾値（mm単位）
     * @return 削除された枝の総数
     */
    public int prune(VesselTree tree, double minLengthMM) {
        System.out.println("Executing Graph Pruning (Threshold: " + minLengthMM + " mm)...");
        int totalRemoved = 0;
        boolean removedInIteration;

        do {
            removedInIteration = false;
            List<Branch> branchesToRemove = new ArrayList<>();

            for (Branch branch : tree.getBranches()) {
                int startConnections = branch.getStartNode().getConnectedBranches().size();
                int endConnections = branch.getEndNode().getConnectedBranches().size();

                boolean isEndpointBranch = (startConnections == 1 || endConnections == 1);

                if (isEndpointBranch && branch.getLength() < minLengthMM) {
                    branchesToRemove.add(branch);
                }
            }

            for (Branch b : branchesToRemove) {
                tree.removeBranch(b);
                totalRemoved++;
                removedInIteration = true;
            }

            if (removedInIteration) {
                tree.cleanupIsolatedNodes();
            }

        } while (removedInIteration);

        System.out.println("  Pruning completed. Removed " + totalRemoved + " short branches.");
        return totalRemoved;
    }

    /**
     * 接続数が「2」になった通過点（余分なノード）を削除し、
     * 前後の2本の枝（Branch）を1本の長い枝にガッチャンコして結合します。
     * @param tree 処理対象の血管ネットワーク
     * @return 結合によって削除された余分なノードの数
     */
    public int mergeLinearBranches(VesselTree tree) {
        System.out.println("Executing Graph Merging (Combining linear branches)...");
        int mergedCount = 0;
        boolean mergedInIteration;

        do {
            mergedInIteration = false;

            for (int i = 0; i < tree.getNodes().size(); i++) {
                Node node = tree.getNodes().get(i);

                // 接続数がぴったり「2」の中継ノードを見つける
                if (node.getConnectedBranches().size() == 2) {
                    Branch b1 = node.getConnectedBranches().get(0);
                    Branch b2 = node.getConnectedBranches().get(1);

                    // 稀に発生するリング状の自己ループ（1本の枝の両端が同じノード）は除外
                    if (b1 == b2) continue;

                    Node nStart = b1.getOppositeNode(node);
                    Node nEnd = b2.getOppositeNode(node);

                    // ==========================================
                    // 経路（Point3Dのリスト）の向きを合わせて結合する
                    // ==========================================
                    List<Point3D> path1 = new ArrayList<>(b1.getPath());
                    // path1の末尾が結合ノードに来るように、逆向きなら反転させる
                    if (!path1.get(path1.size() - 1).equals(node.getCoordinate())) {
                        Collections.reverse(path1);
                    }

                    List<Point3D> path2 = new ArrayList<>(b2.getPath());
                    // path2の先頭が結合ノードに来るように、逆向きなら反転させる
                    if (!path2.get(0).equals(node.getCoordinate())) {
                        Collections.reverse(path2);
                    }

                    // 2つの経路を合体（結合部分の重複ピクセルは1つ削る）
                    List<Point3D> mergedPath = new ArrayList<>(path1);
                    mergedPath.remove(mergedPath.size() - 1);
                    mergedPath.addAll(path2);

                    // 物理的な長さを合算
                    double mergedLength = b1.getLength() + b2.getLength();

                    // 新しい1本の長い枝を生成
                    Branch mergedBranch = new Branch(nStart, nEnd, mergedPath, mergedLength);

                    // 古い枝と中継ノードをツリーから完全に削除
                    tree.removeBranch(b1);
                    tree.removeBranch(b2);
                    tree.removeNode(node);

                    // nStart と nEnd に新しい枝を接続
                    if (nStart != null) nStart.addConnectedBranch(mergedBranch);
                    if (nEnd != null) nEnd.addConnectedBranch(mergedBranch);

                    // ツリーに新しい枝を追加
                    tree.getBranches().add(mergedBranch);

                    mergedCount++;
                    mergedInIteration = true;
                    
                    // リストの構造が変わったのでforループを抜けて、最初から探索し直す（安全な反復処理）
                    break;
                }
            }
        } while (mergedInIteration);

        System.out.println("  Merging completed. Merged " + mergedCount + " nodes.");
        return mergedCount;
    }
}