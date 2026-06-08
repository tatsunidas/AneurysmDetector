package com.vis.aneurysmdetector.anatomy;

import com.vis.aneurysmdetector.core.BifurcationNode;
import com.vis.aneurysmdetector.core.Image3D;
import com.vis.aneurysmdetector.core.Point3D;
import com.vis.aneurysmdetector.core.VesselBranch;
import com.vis.aneurysmdetector.core.VesselTree;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 細線化された3D画像からグラフ構造（VesselTree）を構築するクラス。
 */
public class TreeGraphBuilder {

    // 26近傍の相対座標（自身を除く）
    private static final int[][] NEIGHBORS_26 = generate26Neighbors();

    public TreeGraphBuilder() {}

    /**
     * スケルトン画像から血管ツリーを構築します。
     * @param skeletonImage 細線化された血管のバイナリ画像 (血管=255, 背景=0)
     * @return 構築されたグラフ構造（VesselTree）
     */
    public VesselTree build(Image3D skeletonImage) {
        VesselTree tree = new VesselTree();
        
        int width = skeletonImage.getWidth();
        int height = skeletonImage.getHeight();
        int depth = skeletonImage.getDepth();

        // 1. 全ボクセルをスキャンし、分岐点（ノード）を検出
        List<Point3D> bifurcationPoints = new ArrayList<>();
        List<Point3D> endPoints = new ArrayList<>();
        
        for (int z = 0; z < depth; z++) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (skeletonImage.getPixelValue(x, y, z) > 0) {
                        int neighbors = countNeighbors(skeletonImage, x, y, z);
                        Point3D p = new Point3D(x, y, z);
                        
                        if (neighbors >= 3) {
                            bifurcationPoints.add(p);
                            BifurcationNode node = new BifurcationNode("Node-" + UUID.randomUUID().toString(), p);
                            tree.addNode(node);
                        } else if (neighbors == 1) {
                            endPoints.add(p);
                        }
                    }
                }
            }
        }

        // 2. 分岐点および端点を起点として、隣接するボクセルを追跡し、枝（Branch）を構築する
        // ====================================================================
        // 【アルゴリズム実装のプレースホルダー】
        // 実際のトレース処理では、未訪問のボクセルを管理する3D boolean配列を用意し、
        // Nodeから出発して近傍数が2のボクセルを辿り続け、次のNodeまたは端点に到達するまで
        // Point3Dをリストに追加して VesselBranch を生成します。
        // ====================================================================
        traceAndConnectBranches(skeletonImage, tree, bifurcationPoints, endPoints);

        return tree;
    }

    /**
     * 指定された座標の26近傍にある「血管ボクセル（>0）」の数をカウントします。
     */
    private int countNeighbors(Image3D img, int x, int y, int z) {
        int count = 0;
        for (int[] offset : NEIGHBORS_26) {
            int nx = x + offset[0];
            int ny = y + offset[1];
            int nz = z + offset[2];
            if (img.getPixelValue(nx, ny, nz) > 0) {
                count++;
            }
        }
        return count;
    }

    private void traceAndConnectBranches(Image3D img, VesselTree tree, List<Point3D> nodes, List<Point3D> endpoints) {
        // FIXME: トレースアルゴリズムの実装
        // 1. 各Nodeから出発可能な方向（近傍のボクセル）を探す
        // 2. 辿った座標を List<Point3D> に格納
        // 3. 行き止まり（端点）または別のNodeに到達したらループ終了
        // 4. VesselBranch branch = new VesselBranch(ID, centerline);
        // 5. branch.setNodes(startNode, endNode);
        // 6. tree.addBranch(branch);
    }

    private static int[][] generate26Neighbors() {
        int[][] neighbors = new int[26][3];
        int idx = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    neighbors[idx++] = new int[]{dx, dy, dz};
                }
            }
        }
        return neighbors;
    }
}