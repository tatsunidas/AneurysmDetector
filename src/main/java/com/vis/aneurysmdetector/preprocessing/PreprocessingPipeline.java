package com.vis.aneurysmdetector.preprocessing;

import com.vis.aneurysmdetector.core.Image3D;
import ij.process.AutoThresholder;

/**
 * 前処理ステップ（ノイズ除去 -> セグメンテーション -> 細線化）を連続実行するパイプライン。
 */
public class PreprocessingPipeline {

    private DenoiseFilter denoiser;
    private VesselSegmenter segmenter;
    private Skeletonizer skeletonizer;

    public PreprocessingPipeline() {
        // パラメータの初期化（MRA向けにチューニング）
        this.denoiser = new DenoiseFilter(15, 1); // ノイズの標準偏差と平滑化（ぼかし）の強度
        this.segmenter = new VesselSegmenter();
        this.skeletonizer = new Skeletonizer();
    }

    /**
     * 前処理パイプラインを実行し、デバッグ用に中間画像を表示します。
     * @param inputRawImage 入力MRA画像
     * @param showIntermediateResults trueの場合、ImageJのウィンドウとして途中結果をポップアップします
     * @return 細線化された最終的なスケルトン画像
     */
    public Image3D execute(Image3D inputRawImage, boolean showIntermediateResults) {
        
        System.out.println("--- Pipeline Step 1: Denoising ---");
        Image3D denoisedImage = denoiser.apply(inputRawImage);
        if (showIntermediateResults) {
            denoisedImage.getImagePlus().show();
        }

        System.out.println("--- Pipeline Step 2: Segmentation ---");
        Image3D segmentedMask = segmenter.segment(denoisedImage);
        if (showIntermediateResults) {
            segmentedMask.getImagePlus().show();
        }

        System.out.println("--- Pipeline Step 3: Skeletonization ---");
        Image3D skeletonImage = skeletonizer.skeletonize(segmentedMask);
        if (showIntermediateResults) {
            skeletonImage.getImagePlus().show();
        }

        System.out.println("--- Preprocessing Pipeline Completed ---");
        return skeletonImage;
    }
}