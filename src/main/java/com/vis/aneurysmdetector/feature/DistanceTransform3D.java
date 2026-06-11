/**
 * copyright visionary imaging services, inc.
 */
package com.vis.aneurysmdetector.feature;

import com.vis.aneurysmdetector.core.Image3D;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.FloatProcessor;

/**
 * 3D空間におけるユークリッド距離変換（近似: Chamfer Distance Transform）を行うクラス。
 * 血管マスク（バイナリ画像）の内部ピクセルから、最も近い境界（背景）までの物理距離(mm)を計算します。
 * 
 * @author tatsunidas
 */
public class DistanceTransform3D {

    /**
     * バイナリ画像を受け取り、32-bit Floatの距離マップを生成します。
     * @param binaryMask 前処理で生成された血管の2値化マスク（255:血管, 0:背景）
     * @return 距離マップ（背景は0.0、血管内部は壁までの最短物理距離(mm)を持つImage3D）
     */
    public Image3D computeDistanceMap(Image3D binaryMask) {
        System.out.println("Executing 3D Distance Transform...");
        
        ImagePlus imp = binaryMask.getImagePlus();
        int w = imp.getWidth();
        int h = imp.getHeight();
        int d = imp.getNSlices();

        // メタデータ（ピクセルサイズ mm）を取得
        double dx = imp.getCalibration().pixelWidth;
        double dy = imp.getCalibration().pixelHeight;
        double dz = imp.getCalibration().pixelDepth;
        if (dx <= 0) dx = 1.0; if (dy <= 0) dy = 1.0; if (dz <= 0) dz = 1.0;

        // 32-bit Floatのスタックを初期化
        ImageStack distStack = new ImageStack(w, h);
        float[][][] distMap = new float[d][h][w];

        // 初期化: 背景(0)は距離0、血管(255)は無限大（十分に大きな値）
        for (int z = 0; z < d; z++) {
            byte[] pixels = (byte[]) imp.getStack().getPixels(z + 1);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if ((pixels[y * w + x] & 0xff) == 255) {
                        distMap[z][y][x] = Float.MAX_VALUE;
                    } else {
                        distMap[z][y][x] = 0f;
                    }
                }
            }
        }

        // 3D Chamfer カーネルの物理距離を計算
        // 26近傍への移動コスト（mm）
        float dX = (float) dx; float dY = (float) dy; float dZ = (float) dz;
        float dXY = (float) Math.sqrt(dx*dx + dy*dy);
        float dXZ = (float) Math.sqrt(dx*dx + dz*dz);
        float dYZ = (float) Math.sqrt(dy*dy + dz*dz);
        float dXYZ = (float) Math.sqrt(dx*dx + dy*dy + dz*dz);

        // 前方パス（Top-Left-Front から Bottom-Right-Back へ）
        System.out.println("  Forward pass...");
        for (int z = 0; z < d; z++) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (distMap[z][y][x] > 0) {
                        float min = distMap[z][y][x];
                        
                        // Z - 1 のスライス (9近傍)
                        if (z > 0) {
                            if (x > 0 && y > 0) min = Math.min(min, distMap[z-1][y-1][x-1] + dXYZ);
                            if (y > 0)          min = Math.min(min, distMap[z-1][y-1][x]   + dYZ);
                            if (x < w-1 && y > 0) min = Math.min(min, distMap[z-1][y-1][x+1] + dXYZ);
                            if (x > 0)          min = Math.min(min, distMap[z-1][y][x-1]   + dXZ);
                                                min = Math.min(min, distMap[z-1][y][x]     + dZ);
                            if (x < w-1)        min = Math.min(min, distMap[z-1][y][x+1]   + dXZ);
                            if (x > 0 && y < h-1) min = Math.min(min, distMap[z-1][y+1][x-1] + dXYZ);
                            if (y < h-1)        min = Math.min(min, distMap[z-1][y+1][x]   + dYZ);
                            if (x < w-1 && y < h-1) min = Math.min(min, distMap[z-1][y+1][x+1] + dXYZ);
                        }
                        // 同じ Z スライス (4近傍)
                        if (y > 0) {
                            if (x > 0)          min = Math.min(min, distMap[z][y-1][x-1] + dXY);
                                                min = Math.min(min, distMap[z][y-1][x]   + dY);
                            if (x < w-1)        min = Math.min(min, distMap[z][y-1][x+1] + dXY);
                        }
                        if (x > 0)              min = Math.min(min, distMap[z][y][x-1]   + dX);

                        distMap[z][y][x] = min;
                    }
                }
            }
        }

        // 後方パス（Bottom-Right-Back から Top-Left-Front へ）
        System.out.println("  Backward pass...");
        for (int z = d - 1; z >= 0; z--) {
            for (int y = h - 1; y >= 0; y--) {
                for (int x = w - 1; x >= 0; x--) {
                    if (distMap[z][y][x] > 0) {
                        float min = distMap[z][y][x];
                        
                        // Z + 1 のスライス (9近傍)
                        if (z < d - 1) {
                            if (x > 0 && y > 0) min = Math.min(min, distMap[z+1][y-1][x-1] + dXYZ);
                            if (y > 0)          min = Math.min(min, distMap[z+1][y-1][x]   + dYZ);
                            if (x < w-1 && y > 0) min = Math.min(min, distMap[z+1][y-1][x+1] + dXYZ);
                            if (x > 0)          min = Math.min(min, distMap[z+1][y][x-1]   + dXZ);
                                                min = Math.min(min, distMap[z+1][y][x]     + dZ);
                            if (x < w-1)        min = Math.min(min, distMap[z+1][y][x+1]   + dXZ);
                            if (x > 0 && y < h-1) min = Math.min(min, distMap[z+1][y+1][x-1] + dXYZ);
                            if (y < h-1)        min = Math.min(min, distMap[z+1][y+1][x]   + dYZ);
                            if (x < w-1 && y < h-1) min = Math.min(min, distMap[z+1][y+1][x+1] + dXYZ);
                        }
                        // 同じ Z スライス (4近傍)
                        if (x < w - 1)          min = Math.min(min, distMap[z][y][x+1]   + dX);
                        if (y < h - 1) {
                            if (x > 0)          min = Math.min(min, distMap[z][y+1][x-1] + dXY);
                                                min = Math.min(min, distMap[z][y+1][x]   + dY);
                            if (x < w-1)        min = Math.min(min, distMap[z][y+1][x+1] + dXY);
                        }

                        distMap[z][y][x] = min;
                    }
                }
            }
        }

        // FloatProcessorとしてスタックに格納
        for (int z = 0; z < d; z++) {
            FloatProcessor fp = new FloatProcessor(w, h);
            float[] pixels = (float[]) fp.getPixels();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    pixels[y * w + x] = distMap[z][y][x];
                }
            }
            distStack.addSlice(fp);
        }

        ImagePlus resultImp = new ImagePlus(imp.getTitle() + "_DistMap", distStack);
        resultImp.setCalibration(imp.getCalibration().copy());
        System.out.println("  Distance Transform completed.");
        return new Image3D(resultImp);
    }
}