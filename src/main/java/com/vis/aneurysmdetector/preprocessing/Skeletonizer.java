package com.vis.aneurysmdetector.preprocessing;

import com.vis.aneurysmdetector.core.Image3D;
import ij.ImagePlus;
import sc.fiji.skeletonize3D.Skeletonize3D_;

/**
 * 2値化された血管マスクから中心線を抽出（細線化）するクラス。
 * Fijiの Skeletonize3D_ プラグインを利用して、トポロジーを維持した3D細線化を実行します。
 */
public class Skeletonizer {

    public Skeletonizer() {
        // 必要に応じて初期化処理を記述します
    }

    /**
     * バイナリマスク画像を細線化し、中心線のみが255、背景が0のImage3Dを返します。
     * @param binaryMask 2値化された血管マスク画像
     * @return 細線化（スケルトン化）された3D画像
     */
    public Image3D skeletonize(Image3D binaryMask) {
        // 処理によって元の2値化マスクが上書き（破壊）されないよう、最初に複製します
        ImagePlus imp = binaryMask.getImagePlus().duplicate();
        imp.setTitle(binaryMask.getImagePlus().getTitle() + "_Skeleton");

        System.out.println("Executing Fiji Skeletonize3D_ on volume...");

        // Skeletonize3D_ のインスタンス化
        Skeletonize3D_ skel = new Skeletonize3D_();
        
        // setupメソッドで対象のImagePlusを渡し、内部の初期化を行います
        // 第1引数のargは空文字で問題ありません
        skel.setup("", imp);
        
        // runメソッドを実行して細線化処理を適用します
        // 3Dプラグインのため、引数に渡した単一スライスのProcessorだけでなく、
        // setupで渡したImagePlusのスタック全体が内部で処理・上書きされます
        skel.run(imp.getProcessor());

        System.out.println("Skeletonization completed.");

        // 細線化が完了したImagePlusをImage3Dでラップして返す
        return new Image3D(imp);
    }
}