package com.vis.aneurysmdetector.core;

import com.vis.core.view.D2.ui.glasses.Praparat;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;

/**
 * 3Dボクセルデータ（MRA画像）を保持・操作する基本クラス。
 * ImageJのImagePlusを内部でラップしてアルゴリズム層に提供します。
 */
public class Image3D {
    private ImagePlus imagePlus;
    private ImageStack stack;
    
    private int width;
    private int height;
    private int depth;
    
    private double spacingX = 1.0;
    private double spacingY = 1.0;
    private double spacingZ = 1.0;

    /**
     * Praparat から Image3D を構築します。
     * @param praparat 対象のPraparat
     */
    public Image3D(Praparat praparat) {
        // Praparatから結合されたImagePlusを取得
        ImagePlus imp = praparat.getImagePlus();
        if (imp == null) {
            throw new IllegalArgumentException("Praparat has no valid ImagePlus data.");
        }
        init(imp);
    }

    /**
     * ImagePlus から直接構築します（前処理パイプラインなどで生成された中間画像用）。
     * @param imp 対象のImagePlus
     */
    public Image3D(ImagePlus imp) {
        if (imp == null) {
            throw new IllegalArgumentException("ImagePlus cannot be null.");
        }
        init(imp);
    }

    private void init(ImagePlus imp) {
        this.imagePlus = imp;
        this.stack = imp.getStack();
        
        this.width = imp.getWidth();
        this.height = imp.getHeight();
        this.depth = imp.getNSlices();
        
        // Voxelの物理サイズ（mmなど）を取得
        Calibration cal = imp.getCalibration();
        if (cal != null) {
            this.spacingX = cal.pixelWidth;
            this.spacingY = cal.pixelHeight;
            this.spacingZ = cal.pixelDepth;
        }
    }

    /**
     * 指定したボクセルの輝度値を取得します。
     */
    public float getPixelValue(int x, int y, int z) {
        if (x < 0 || x >= width || y < 0 || y >= height || z < 0 || z >= depth) {
            return 0.0f; // 境界外
        }
        // ImageJのスタックは1-based index
        return stack.getProcessor(z + 1).getPixelValue(x, y);
    }

    public ImagePlus getImagePlus() { return imagePlus; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getDepth() { return depth; }
    public double getSpacingX() { return spacingX; }
    public double getSpacingY() { return spacingY; }
    public double getSpacingZ() { return spacingZ; }
}