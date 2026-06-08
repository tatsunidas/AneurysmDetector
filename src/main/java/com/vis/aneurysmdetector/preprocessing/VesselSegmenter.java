package com.vis.aneurysmdetector.preprocessing;

import com.vis.aneurysmdetector.core.Image3D;
import com.vis.aneurysmdetector.core.Point3D;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.StackStatistics;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * MRAボリューム全体のヒストグラムから、上位指定パーセントの輝度を持つピクセルのみを抽出（2値化）し、
 * その後、3D Connected Component Analysis を用いて微小なノイズ（ゴミ）を削除するクラス。
 */
public class VesselSegmenter {

    private double topPercentile;
    private int minComponentVoxels;
    private static final int[][] NEIGHBORS_26 = generate26Neighbors();

    /**
     * @param topPercentile 血管として抽出する上位ピクセルの割合（例: 0.01 = 上位1%）
     * @param minComponentVoxels 削除する微小ゴミの最大ボクセル数（推奨: 100）
     */
    public VesselSegmenter(double topPercentile, int minComponentVoxels) {
        this.topPercentile = topPercentile;
        this.minComponentVoxels = minComponentVoxels;
    }
    
    /**
     * デフォルトコンストラクタ。MRAの標準的な血管体積（約1%）を基準にします。
     */
    public VesselSegmenter() {
        this(0.01, 100); // デフォルト: 上位1.0%, ゴミ削除100ボクセル
    }

    public Image3D segment(Image3D inputImage) {
        ImagePlus imp = inputImage.getImagePlus().duplicate();
        imp.setTitle(inputImage.getImagePlus().getTitle() + "_Segmented");

        // 1. 8-bitに変換（表示レンジを0-255にマッピング）
        ij.process.ImageConverter converter = new ij.process.ImageConverter(imp);
        converter.convertToGray8();

        // 2. ヒストグラムの取得
        StackStatistics stats = new StackStatistics(imp);
        int[] histogram = stats.histogram;

        // 3. 上位パーセンタイルに基づく閾値の計算
        System.out.println("Executing Top-Percentile Thresholding (Target: Top " + (topPercentile * 100) + "%)...");
        int globalThreshold = computePercentileThreshold(histogram, topPercentile);
        System.out.println("Computed Percentile Threshold: " + globalThreshold);

        // 4. 一括で完全に2値化 (255 or 0)
        ImageStack stack = imp.getStack();
        for (int z = 1; z <= stack.getSize(); z++) {
            byte[] pixels = (byte[]) stack.getProcessor(z).getPixels();
            for (int i = 0; i < pixels.length; i++) {
                int val = pixels[i] & 0xff;
                pixels[i] = (val >= globalThreshold) ? (byte) 255 : (byte) 0;
            }
        }

        // 5. 3D CCA による微小ゴミの削除
        System.out.println("Executing 3D Connected Component Analysis...");
        int removedCount = removeSmallComponents(stack);
        System.out.println("Removed " + removedCount + " small noise components (< " + minComponentVoxels + " voxels).");

        return new Image3D(imp);
    }

    /**
     * ヒストグラムの明るい方（255）からピクセル数をカウントし、
     * 全体の topPercentile に達した時点の輝度を閾値として返します。
     */
    private int computePercentileThreshold(int[] histogram, double targetPercentile) {
        long totalPixels = 0;
        for (int count : histogram) {
            totalPixels += count;
        }

        long targetPixelCount = (long) (totalPixels * targetPercentile);
        long accumulated = 0;
        int threshold = 255;

        // 明るいピクセル(255)から暗いピクセル(0)へ向かってカウントダウン
        for (int i = 255; i >= 0; i--) {
            accumulated += histogram[i];
            if (accumulated >= targetPixelCount) {
                threshold = i;
                break;
            }
        }

        // 最低限のフェイルセーフ（閾値が低くなりすぎるのを防ぐ）
        return Math.max(30, threshold);
    }

    private int removeSmallComponents(ImageStack stack) {
        int width = stack.getWidth();
        int height = stack.getHeight();
        int depth = stack.getSize();
        
        boolean[][][] visited = new boolean[depth][height][width];
        int removedComponents = 0;

        for (int z = 0; z < depth; z++) {
            byte[] pixels = (byte[]) stack.getProcessor(z + 1).getPixels();
            for (int y = 0; y < height; y++) {
                int offset = y * width;
                for (int x = 0; x < width; x++) {
                    
                    if ((pixels[offset + x] & 0xff) == 255 && !visited[z][y][x]) {
                        List<Point3D> componentVoxels = new ArrayList<>();
                        Queue<Point3D> queue = new LinkedList<>();
                        
                        Point3D seed = new Point3D(x, y, z);
                        queue.add(seed);
                        visited[z][y][x] = true;
                        componentVoxels.add(seed);

                        while (!queue.isEmpty()) {
                            Point3D p = queue.poll();
                            
                            for (int[] off : NEIGHBORS_26) {
                                int nx = p.x + off[0];
                                int ny = p.y + off[1];
                                int nz = p.z + off[2];

                                if (nx >= 0 && nx < width && ny >= 0 && ny < height && nz >= 0 && nz < depth) {
                                    if (!visited[nz][ny][nx]) {
                                        byte[] nPixels = (byte[]) stack.getProcessor(nz + 1).getPixels();
                                        if ((nPixels[ny * width + nx] & 0xff) == 255) {
                                            visited[nz][ny][nx] = true;
                                            Point3D nextP = new Point3D(nx, ny, nz);
                                            queue.add(nextP);
                                            componentVoxels.add(nextP);
                                        }
                                    }
                                }
                            }
                        }

                        if (componentVoxels.size() < minComponentVoxels) {
                            for (Point3D p : componentVoxels) {
                                byte[] pPixels = (byte[]) stack.getProcessor(p.z + 1).getPixels();
                                pPixels[p.y * width + p.x] = (byte) 0;
                            }
                            removedComponents++;
                        }
                    }
                }
            }
        }
        return removedComponents;
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

/**
 * MRAボリューム全体から自動閾値を算出し、血流領域を2値化した後、
 * 3D Connected Component Analysis を用いて微小なノイズ（ゴミ）を削除するクラス。
 */
//public class VesselSegmenter {
//
//    private AutoThresholder.Method method;
//    private int minComponentVoxels;
//
//    private static final int[][] NEIGHBORS_26 = generate26Neighbors();
//
//    /**
//     * MRA画像向けに最適化されたデフォルトコンストラクタ。
//     * - アルゴリズム: IsoData (Otsuよりもノイズに引っ張られにくく安定しやすい)
//     * - 最小ボクセル数: 100 (明確なゴミだけを削除し、途切れた末梢血管は保護するサイズ)
//     */
//    public VesselSegmenter() {
//        this(AutoThresholder.Method.IsoData, 100);
//    }
//
//    /**
//     * カスタムパラメータで初期化するコンストラクタ。
//     */
//    public VesselSegmenter(AutoThresholder.Method method, int minComponentVoxels) {
//        this.method = method;
//        this.minComponentVoxels = minComponentVoxels;
//    }
//
//    public Image3D segment(Image3D inputImage) {
//        ImagePlus imp = inputImage.getImagePlus().duplicate();
//        imp.setTitle(inputImage.getImagePlus().getTitle() + "_Segmented");
//        
//        System.out.println("Executing Global Thresholding...");
//
//        // 1. 8-bitに変換（表示レンジを0-255にマッピングし、黒つぶれを防ぐ）
//        ij.process.ImageConverter converter = new ij.process.ImageConverter(imp);
//        converter.convertToGray8();
//
//        // 2. 8-bit化されたボリューム全体の統計情報（256階調ヒストグラム）を取得
//        StackStatistics stats = new StackStatistics(imp);
//        int[] histogram = stats.histogram;
//        
//        // 3. グローバル閾値を計算
//        AutoThresholder thresholder = new AutoThresholder();
//        int globalThreshold = thresholder.getThreshold(method, histogram);
//        
//        System.out.println("Computed Global Threshold (" + method.name() + "): " + globalThreshold);
//
//        // 4. 一括で完全に2値化 (ピクセル配列を直接操作して 255 or 0 に固定)
//        ImageStack stack = imp.getStack();
//        for (int z = 1; z <= stack.getSize(); z++) {
//            byte[] pixels = (byte[]) stack.getProcessor(z).getPixels();
//            for (int i = 0; i < pixels.length; i++) {
//                int val = pixels[i] & 0xff; // 符号なしの0〜255として評価
//                pixels[i] = (val >= globalThreshold) ? (byte) 255 : (byte) 0;
//            }
//        }
//
//        // 5. Connected Component Analysis (CCA) で微小ゴミを削除
//        System.out.println("Executing 3D Connected Component Analysis...");
//        int removedCount = removeSmallComponents(stack);
//        System.out.println("Removed " + removedCount + " small noise components (< " + minComponentVoxels + " voxels).");
//
//        return new Image3D(imp);
//    }
//
//    /**
//     * BFSを用いて3D空間の連結成分を探索し、指定サイズ未満の塊を黒(0)に塗りつぶします。
//     * @return 削除された成分（ゴミ）の数
//     */
//    private int removeSmallComponents(ImageStack stack) {
//        int width = stack.getWidth();
//        int height = stack.getHeight();
//        int depth = stack.getSize();
//        
//        boolean[][][] visited = new boolean[depth][height][width];
//        int removedComponents = 0;
//
//        for (int z = 0; z < depth; z++) {
//            byte[] pixels = (byte[]) stack.getProcessor(z + 1).getPixels();
//            for (int y = 0; y < height; y++) {
//                int offset = y * width;
//                for (int x = 0; x < width; x++) {
//                    
//                    // 未訪問の血管ボクセル（255）を発見
//                    if ((pixels[offset + x] & 0xff) == 255 && !visited[z][y][x]) {
//                        
//                        List<Point3D> componentVoxels = new ArrayList<>();
//                        Queue<Point3D> queue = new LinkedList<>();
//                        
//                        Point3D seed = new Point3D(x, y, z);
//                        queue.add(seed);
//                        visited[z][y][x] = true;
//                        componentVoxels.add(seed);
//
//                        // BFSによる領域拡張
//                        while (!queue.isEmpty()) {
//                            Point3D p = queue.poll();
//                            
//                            for (int[] off : NEIGHBORS_26) {
//                                int nx = p.x + off[0];
//                                int ny = p.y + off[1];
//                                int nz = p.z + off[2];
//
//                                if (nx >= 0 && nx < width && ny >= 0 && ny < height && nz >= 0 && nz < depth) {
//                                    if (!visited[nz][ny][nx]) {
//                                        byte[] nPixels = (byte[]) stack.getProcessor(nz + 1).getPixels();
//                                        if ((nPixels[ny * width + nx] & 0xff) == 255) {
//                                            visited[nz][ny][nx] = true;
//                                            Point3D nextP = new Point3D(nx, ny, nz);
//                                            queue.add(nextP);
//                                            componentVoxels.add(nextP);
//                                        }
//                                    }
//                                }
//                            }
//                        }
//
//                        // サイズが閾値未満なら黒(0)で塗りつぶして削除
//                        if (componentVoxels.size() < minComponentVoxels) {
//                            for (Point3D p : componentVoxels) {
//                                byte[] pPixels = (byte[]) stack.getProcessor(p.z + 1).getPixels();
//                                pPixels[p.y * width + p.x] = (byte) 0;
//                            }
//                            removedComponents++;
//                        }
//                    }
//                }
//            }
//        }
//        return removedComponents;
//    }
//
//    /**
//     * 26近傍探索用のオフセット配列を生成します。
//     */
//    private static int[][] generate26Neighbors() {
//        int[][] neighbors = new int[26][3];
//        int idx = 0;
//        for (int dz = -1; dz <= 1; dz++) {
//            for (int dy = -1; dy <= 1; dy++) {
//                for (int dx = -1; dx <= 1; dx++) {
//                    if (dx == 0 && dy == 0 && dz == 0) continue;
//                    neighbors[idx++] = new int[]{dx, dy, dz};
//                }
//            }
//        }
//        return neighbors;
//    }
//}