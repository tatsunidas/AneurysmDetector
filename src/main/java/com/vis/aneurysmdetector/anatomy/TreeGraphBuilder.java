package com.vis.aneurysmdetector.anatomy;

import com.vis.aneurysmdetector.core.BifurcationNode;
import com.vis.aneurysmdetector.core.Image3D;
import com.vis.aneurysmdetector.core.Point3D;
import com.vis.aneurysmdetector.core.VesselBranch;
import com.vis.aneurysmdetector.core.VesselTree;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 細線化された3D画像からグラフ構造（VesselTree）を構築するクラス。
 */
public class TreeGraphBuilder {

    private static final int[][] NEIGHBORS_26 = generate26Neighbors();

    public TreeGraphBuilder() {}

    public VesselTree build(Image3D skeletonImage) {
        VesselTree tree = new VesselTree();
        
        int width = skeletonImage.getWidth();
        int height = skeletonImage.getHeight();
        int depth = skeletonImage.getDepth();

        List<Point3D> bifurcationPoints = new ArrayList<>();
        List<Point3D> endPoints = new ArrayList<>();
        
        // 1. 全ボクセルをスキャンし、近傍数から分岐点と端点を検出
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

        // 2. ボクセルを辿って枝を構築
        traceAndConnectBranches(skeletonImage, tree, bifurcationPoints, endPoints);

        return tree;
    }

    private int countNeighbors(Image3D img, int x, int y, int z) {
        int count = 0;
        int w = img.getWidth(), h = img.getHeight(), d = img.getDepth();
        for (int[] offset : NEIGHBORS_26) {
            int nx = x + offset[0];
            int ny = y + offset[1];
            int nz = z + offset[2];
            if (nx >= 0 && nx < w && ny >= 0 && ny < h && nz >= 0 && nz < d) {
                if (img.getPixelValue(nx, ny, nz) > 0) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * 分岐点・端点を起点に未訪問のボクセルを追跡し、枝を生成します。
     */
    private void traceAndConnectBranches(Image3D img, VesselTree tree, List<Point3D> nodes, List<Point3D> endpoints) {
        int w = img.getWidth();
        int h = img.getHeight();
        int d = img.getDepth();
        
        // 訪問済みの「枝」のボクセルを記録するフラグ配列
        boolean[][][] visited = new boolean[d][h][w];

        // 探索の起点リスト（分岐点 + 端点）
        List<Point3D> startPoints = new ArrayList<>();
        startPoints.addAll(nodes);
        startPoints.addAll(endpoints);

        Set<Point3D> nodeSet = new HashSet<>(nodes);
        Set<Point3D> endpointSet = new HashSet<>(endpoints);

        int branchCounter = 1;

        for (Point3D startP : startPoints) {
            // 起点に隣接する未訪問のボクセルを探し、そこからトレースを開始する
            for (int[] offset : NEIGHBORS_26) {
                int nx = startP.x + offset[0];
                int ny = startP.y + offset[1];
                int nz = startP.z + offset[2];

                if (nx < 0 || nx >= w || ny < 0 || ny >= h || nz < 0 || nz >= d) continue;

                // 隣接点が血管であり、まだ訪問しておらず、かつ別の分岐点ではない場合
                if (img.getPixelValue(nx, ny, nz) > 0 && !visited[nz][ny][nx] && !nodeSet.contains(new Point3D(nx, ny, nz))) {
                    
                    List<Point3D> centerline = new ArrayList<>();
                    centerline.add(startP); // 始点（分岐部または端点）を登録

                    Point3D current = new Point3D(nx, ny, nz);
                    visited[current.z][current.y][current.x] = true;
                    centerline.add(current);

                    Point3D endPOfBranch = null;

                    // 一つの枝を末端まで辿るループ
                    while (true) {
                        Point3D nextPoint = null;

                        // 現在地の周囲26近傍を探す
                        for (int[] off : NEIGHBORS_26) {
                            int cx = current.x + off[0];
                            int cy = current.y + off[1];
                            int cz = current.z + off[2];

                            if (cx < 0 || cx >= w || cy < 0 || cy >= h || cz < 0 || cz >= d) continue;

                            if (img.getPixelValue(cx, cy, cz) > 0) {
                                Point3D p = new Point3D(cx, cy, cz);
                                
                                // 次の点が分岐点または端点に到達した場合（枝の終点）
                                if (nodeSet.contains(p) || endpointSet.contains(p)) {
                                    if (!p.equals(startP) && !centerline.contains(p)) {
                                        endPOfBranch = p;
                                        break;
                                    }
                                } 
                                // まだ訪問していない枝の途中経過点の場合
                                else if (!visited[cz][cy][cx]) {
                                    nextPoint = p;
                                }
                            }
                        }

                        if (endPOfBranch != null) {
                            centerline.add(endPOfBranch); // 終点を追加して終了
                            break;
                        } else if (nextPoint != null) {
                            visited[nextPoint.z][nextPoint.y][nextPoint.x] = true;
                            centerline.add(nextPoint);
                            current = nextPoint; // 次の点へ進む
                        } else {
                            break; // 行き止まり（通常は発生しないがノイズ対策）
                        }
                    }

                    // 枝のオブジェクトを生成
                    VesselBranch branch = new VesselBranch("Branch-" + branchCounter++, centerline);

                    // 始点と終点のNodeオブジェクトを紐付ける
                    BifurcationNode sNode = findNodeByPoint(tree, startP);
                    BifurcationNode eNode = findNodeByPoint(tree, endPOfBranch);

                    branch.setNodes(sNode, eNode);
                    if (sNode != null) sNode.addBranch(branch);
                    if (eNode != null) eNode.addBranch(branch);

                    tree.addBranch(branch);
                }
            }
        }
    }

    private BifurcationNode findNodeByPoint(VesselTree tree, Point3D p) {
        if (p == null) return null;
        for (BifurcationNode node : tree.getNodes()) {
            if (node.getCenterPoint().equals(p)) {
                return node;
            }
        }
        return null;
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