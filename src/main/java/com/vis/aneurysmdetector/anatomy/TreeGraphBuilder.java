package com.vis.aneurysmdetector.anatomy;

import com.vis.aneurysmdetector.core.Image3D;
import com.vis.aneurysmdetector.core.Point3D;
import com.vis.aneurysmdetector.core.Node;
import com.vis.aneurysmdetector.core.Branch;
import com.vis.aneurysmdetector.core.VesselTree;
import ij.ImageStack;

import java.util.*;

/**
 * 3Dスケルトン画像から血管ネットワーク（NodeとBranch）を構築するクラス。
 */
public class TreeGraphBuilder {

    private static final int[][] NEIGHBORS_26 = generate26Neighbors();

    public VesselTree build(Image3D skeletonImage) {
        ImageStack stack = skeletonImage.getImagePlus().getStack();
        int w = stack.getWidth();
        int h = stack.getHeight();
        int d = stack.getSize();

        // メタデータからピクセルサイズを取得（長さを計算するため）
        double dx = skeletonImage.getImagePlus().getCalibration().pixelWidth;
        double dy = skeletonImage.getImagePlus().getCalibration().pixelHeight;
        double dz = skeletonImage.getImagePlus().getCalibration().pixelDepth;
        if (dx <= 0) dx = 1.0; if (dy <= 0) dy = 1.0; if (dz <= 0) dz = 1.0;

        System.out.println("Building Vessel Tree Graph...");

        // 1. 各ボクセルの近傍数を計算し、Node（端点・分岐点）を特定する
        Map<Point3D, Node> nodeMap = new HashMap<>();
        List<Point3D> pathPoints = new ArrayList<>();
        boolean[][][] isSkeleton = new boolean[d][h][w];

        for (int z = 0; z < d; z++) {
            byte[] pixels = (byte[]) stack.getPixels(z + 1);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if ((pixels[y * w + x] & 0xff) == 255) {
                        isSkeleton[z][y][x] = true;
                        Point3D p = new Point3D(x, y, z);
                        int neighbors = countNeighbors(stack, x, y, z, w, h, d);

                        if (neighbors == 1 || neighbors >= 3) {
                            // 端点または分岐点はNodeとして登録
                            Node.NodeType type = (neighbors == 1) ? Node.NodeType.ENDPOINT : Node.NodeType.BIFURCATION;
                            nodeMap.put(p, new Node(p, type));
                        } else if (neighbors == 2) {
                            // 経路ポイント
                            pathPoints.add(p);
                        }
                    }
                }
            }
        }

        System.out.println("  Detected Nodes: " + nodeMap.size());

        // 2. Node間を追跡してBranchを構築する
        List<Branch> branches = new ArrayList<>();
        boolean[][][] visited = new boolean[d][h][w];

        // 各Nodeから出発して経路を追跡
        for (Node startNode : nodeMap.values()) {
            Point3D startP = startNode.getCoordinate();
            visited[startP.z][startP.y][startP.x] = true;

            // Nodeの周囲26近傍にある未訪問のスケルトンピクセルを探す
            for (int[] off : NEIGHBORS_26) {
                int nx = startP.x + off[0];
                int ny = startP.y + off[1];
                int nz = startP.z + off[2];

                if (isValid(nx, ny, nz, w, h, d) && isSkeleton[nz][ny][nx] && !visited[nz][ny][nx]) {
                    // 新しいBranchの追跡を開始
                    Branch branch = traceBranch(nx, ny, nz, startNode, nodeMap, isSkeleton, visited, w, h, d, dx, dy, dz);
                    if (branch != null) {
                        branches.add(branch);
                    }
                }
            }
        }

        System.out.println("  Constructed Branches: " + branches.size());

        return new VesselTree(new ArrayList<>(nodeMap.values()), branches);
    }

    /**
     * 経路を追跡してBranchオブジェクトを生成します。
     */
    private Branch traceBranch(int startX, int startY, int startZ, Node startNode, Map<Point3D, Node> nodeMap, 
                               boolean[][][] isSkeleton, boolean[][][] visited, 
                               int w, int h, int d, double dx, double dy, double dz) {
        
        List<Point3D> path = new ArrayList<>();
        Point3D current = new Point3D(startX, startY, startZ);
        path.add(startNode.getCoordinate()); // 開始Nodeを追加
        
        Node endNode = null;
        double length = 0.0;

        while (true) {
            visited[current.z][current.y][current.x] = true;
            path.add(current);

            // 長さの加算（前のポイントとのユークリッド距離）
            Point3D prev = path.get(path.size() - 2);
            length += Math.sqrt(Math.pow((current.x - prev.x) * dx, 2) + 
                                Math.pow((current.y - prev.y) * dy, 2) + 
                                Math.pow((current.z - prev.z) * dz, 2));

            // 現在地がNode（分岐点または端点）であれば追跡終了
            if (nodeMap.containsKey(current)) {
                endNode = nodeMap.get(current);
                break;
            }

            // 次の経路ポイントを探す
            Point3D next = null;
            for (int[] off : NEIGHBORS_26) {
                int nx = current.x + off[0];
                int ny = current.y + off[1];
                int nz = current.z + off[2];

                if (isValid(nx, ny, nz, w, h, d) && isSkeleton[nz][ny][nx] && !visited[nz][ny][nx]) {
                    next = new Point3D(nx, ny, nz);
                    break; // 線をなぞるだけなので、1つ見つかればOK
                }
            }

            if (next == null) {
                // ループ構造などで終端が見つからなかった場合（安全のためのフェイルセーフ）
                break;
            }
            current = next;
        }

        if (endNode != null) {
            Branch branch = new Branch(startNode, endNode, path, length);
            startNode.addConnectedBranch(branch);
            endNode.addConnectedBranch(branch);
            return branch;
        }

        return null;
    }

    private int countNeighbors(ImageStack stack, int x, int y, int z, int w, int h, int d) {
        int count = 0;
        for (int[] off : NEIGHBORS_26) {
            int nx = x + off[0];
            int ny = y + off[1];
            int nz = z + off[2];
            if (isValid(nx, ny, nz, w, h, d)) {
                byte[] pixels = (byte[]) stack.getPixels(nz + 1);
                if ((pixels[ny * w + nx] & 0xff) == 255) {
                    count++;
                }
            }
        }
        return count;
    }

    private boolean isValid(int x, int y, int z, int w, int h, int d) {
        return x >= 0 && x < w && y >= 0 && y < h && z >= 0 && z < d;
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