package com.vis.aneurysmdetector.preprocessing;

import com.vis.aneurysmdetector.core.Image3D;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.AutoThresholder;
import ij.process.ImageProcessor;

/**
 * 血管領域を抽出（2値化）するクラス。
 * MRA画像において高信号（白）となる血流領域をセグメンテーションします。
 */
public class VesselSegmenter {

    private AutoThresholder.Method method;

    /**
     * @param method 自動閾値判定のアルゴリズム（例: AutoThresholder.Method.Otsu）
     */
    public VesselSegmenter(AutoThresholder.Method method) {
        this.method = method;
    }

    /**
     * 画像を2値化し、血管のバイナリマスク（0 or 255）を持つImage3Dを返します。
     * * @param inputImage ノイズ除去済みの3D画像
     * @return 血管マスク画像（2値画像）
     */
    public Image3D segment(Image3D inputImage) {
        ImagePlus imp = inputImage.getImagePlus().duplicate();
        ImageStack stack = imp.getStack();
        
        AutoThresholder thresholder = new AutoThresholder();

        // 3Dボリューム全体での統計情報から閾値を決定するのが理想ですが、
        // ここではスライスごとに最適な閾値を計算して2値化するアプローチをとります。
        for (int z = 1; z <= stack.getSize(); z++) {
            ImageProcessor ip = stack.getProcessor(z);
            
            // ヒストグラムから閾値を計算
            int[] histogram = ip.getHistogram();
            int threshold = thresholder.getThreshold(method, histogram);
            
            // 閾値以上のピクセルを255(血管)、未満を0(背景)に変換
            ip.threshold(threshold);
        }
        
        // 8bit画像に変換してバイナリマスクとして扱う
        ij.process.ImageConverter converter = new ij.process.ImageConverter(imp);
        converter.convertToGray8();
        
        return new Image3D(imp);
    }
}