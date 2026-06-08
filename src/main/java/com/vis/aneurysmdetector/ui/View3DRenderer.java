package com.vis.aneurysmdetector.ui;

import com.vis.aneurysmdetector.core.AneurysmCandidate;
import com.vis.aneurysmdetector.core.Image3D;
import com.vis.aneurysmdetector.core.Point3D;
import com.vis.core.view.D3.ui.GLCanvas;

import org.lwjgl.opengl.awt.GLData;

import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * GLCanvasをラップし、MIPやVRの表示、動脈瘤候補のハイライト・クロップを制御するコンポーネント。
 */
public class View3DRenderer extends JPanel {

    private GLCanvas glCanvas;
    private Image3D currentImage3D;

    public View3DRenderer() {
        setLayout(new BorderLayout());
        
        // LWJGL 3のGLData設定
        GLData data = new GLData();
        data.majorVersion = 3;
        data.minorVersion = 3;
        data.profile = GLData.Profile.CORE;
        data.samples = 4; // アンチエイリアス

        glCanvas = new GLCanvas(data);
        add(glCanvas, BorderLayout.CENTER);
    }

    /**
     * Image3Dを受け取り、GLCanvasが要求するVolumeDataに変換してGPUへ転送します。
     */
    public void setVolumeData(Image3D volume) {
        this.currentImage3D = volume;
        
        // ※ 実際のシステムにおける VolumeData への変換ロジック
        // com.vis.core.view.D3.ui.VolumeData volData = convertToVolumeData(volume);
        // glCanvas.setVolumeData(volData);
        // glCanvas.setShowVolume(true);
        
        glCanvas.repaint();
    }

    public void setMIPMode(boolean isMIP) {
        glCanvas.setMIPMode(isMIP);
    }

    public void resetCamera() {
        glCanvas.resetCamera();
        glCanvas.repaint();
    }

    /**
     * 候補（短い枝や側壁瘤）をカラーハイライトします。
     * GLCanvasの setRoiData 等の機能を利用してマスクを描画する想定です。
     */
    public void highlightCandidates(List<AneurysmCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            glCanvas.setShowRoi(false);
            return;
        }

        // ====================================================================
        // 【ハイライトの実装例】
        // 1. candidates の座標をもとに、3D FreeFormRoi3D 等を生成する
        // 2. 生成した ROI を glCanvas.setRoiData() で渡し、GPU上で色付けさせる
        // 3. glCanvas.setRoiColors() でハイライト色（赤や黄色など）を指定する
        // ====================================================================

        List<Color> roiColors = new ArrayList<>();
        roiColors.add(Color.RED); // 怪しい候補の色
        
        glCanvas.updateRoiColors(roiColors);
        glCanvas.setRoiAlpha(0.8f);
        glCanvas.setShowRoi(true);
        glCanvas.repaint();
    }

    /**
     * 特定の部位（中心座標）にカメラを向け、周囲をクロップして表示します。
     */
    public void focusAndCrop(Point3D center, int radius) {
        // TODO: glCanvas側のCameraやClippingPlaneAPI（VolumeEditor等）を利用し、
        // 視点を移動させて指定範囲外をカット（非表示）にする処理を呼び出す。
        
        // glCanvas.camera.lookAt(...);
        // glCanvas.setCropBox(...);
        
        glCanvas.repaint();
    }
}