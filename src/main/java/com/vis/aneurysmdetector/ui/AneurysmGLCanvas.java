/**
 * copyright visionary imaging services, inc.
 */
package com.vis.aneurysmdetector.ui;

import com.vis.aneurysmdetector.core.AneurysmCandidate;
import com.vis.aneurysmdetector.core.Point3D;
import com.vis.core.view.D3.ui.GLCanvas;
import com.vis.core.view.D3.ui.VolumeData;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.awt.GLData;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.List;

/**
 * @author tatsunidas
 */
@SuppressWarnings("serial")
public class AneurysmGLCanvas extends GLCanvas {

    private List<AneurysmCandidate> candidates;
    private AneurysmCandidate highlightedCandidate;
    private boolean showBoundingBoxes = false;
    private boolean showAllBoundingBoxes = true; // デフォルトは全表示(ON)
    
	// ========================================================================
	// ★追加: スケルトン（カラーマップ）描画用のフィールド
	// ==========================================
	private boolean showSkeleton = false;
	private int skelShaderProgram = -1;
	private int skelVao = -1, skelVbo = -1;
	private int skelVertexCount = 0;
	// ★追加: OpenGLスレッドへの転送待ちデータ
    private float[] pendingSkeletonData = null;
    private boolean isSkeletonDataDirty = false;

    private int shaderProgram = -1;
    private int vao, vbo;
    private int mvpLoc, colorLoc;    
    
	// ==========================================
	// ★追加: 検証ログ計測用の変数
	// ==========================================
//    private int frameCounter = 0;
//    private long lastLogTime = System.currentTimeMillis();

	public AneurysmGLCanvas(GLData data) {
		super(data);
		// ★ 親クラスの自動スワップをOFFにする！
		setAutoSwapBuffer(false);
	}

    @Override
    public void initGL() {
        super.initGL();
        setMIPMode(true);
        setShowRoi(true);
        optimizeContrast();
    }

    public void setCandidates(List<AneurysmCandidate> candidates) {
        this.candidates = candidates;
    }

    public void setHighlightedCandidate(com.vis.aneurysmdetector.core.AneurysmCandidate c) {
        this.highlightedCandidate = c;
    }

    public void setShowAllBoundingBoxes(boolean showAll) {
        this.showAllBoundingBoxes = showAll;
    }

    public void setShowBoundingBoxes(boolean show) {
        this.showBoundingBoxes = show;
    }

    private void initBoxGL() {
        if (shaderProgram != -1) return;

        String vertSrc = 
            "#version 330 core\n" +
            "layout (location = 0) in vec3 aPos;\n" +
            "uniform mat4 mvp;\n" +
            "void main() { gl_Position = mvp * vec4(aPos, 1.0); }\n";

        String fragSrc = 
            "#version 330 core\n" +
            "out vec4 FragColor;\n" +
            "uniform vec4 uColor;\n" +
            "void main() { FragColor = uColor; }\n";

        int vShader = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
        GL20.glShaderSource(vShader, vertSrc);
        GL20.glCompileShader(vShader);

        int fShader = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GL20.glShaderSource(fShader, fragSrc);
        GL20.glCompileShader(fShader);

        shaderProgram = GL20.glCreateProgram();
        GL20.glAttachShader(shaderProgram, vShader);
        GL20.glAttachShader(shaderProgram, fShader);
        GL20.glLinkProgram(shaderProgram);

        GL20.glDeleteShader(vShader);
        GL20.glDeleteShader(fShader);

        mvpLoc = GL20.glGetUniformLocation(shaderProgram, "mvp");
        colorLoc = GL20.glGetUniformLocation(shaderProgram, "uColor");

        float[] boxLines = {
            -0.5f,-0.5f,-0.5f,  0.5f,-0.5f,-0.5f,    0.5f,-0.5f,-0.5f,  0.5f,-0.5f, 0.5f,
             0.5f,-0.5f, 0.5f, -0.5f,-0.5f, 0.5f,   -0.5f,-0.5f, 0.5f, -0.5f,-0.5f,-0.5f,
            -0.5f, 0.5f,-0.5f,  0.5f, 0.5f,-0.5f,    0.5f, 0.5f,-0.5f,  0.5f, 0.5f, 0.5f,
             0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f,   -0.5f, 0.5f, 0.5f, -0.5f, 0.5f,-0.5f,
            -0.5f,-0.5f,-0.5f, -0.5f, 0.5f,-0.5f,    0.5f,-0.5f,-0.5f,  0.5f, 0.5f,-0.5f,
             0.5f,-0.5f, 0.5f,  0.5f, 0.5f, 0.5f,   -0.5f,-0.5f, 0.5f, -0.5f, 0.5f, 0.5f
        };

        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, boxLines, GL15.GL_STATIC_DRAW);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 0, 0);
        GL20.glEnableVertexAttribArray(0);
        GL30.glBindVertexArray(0);
    }
    
    /**
     * UI側からデータを受け取る（ここはSwingのUIスレッドで呼ばれるため、GL関数は使えない）
     */
    public void setSkeletonData(float[] skeletonVertices) {
        if (skeletonVertices == null || skeletonVertices.length == 0) return;
        
        // データを一時保存し、GPU転送フラグを立てる
        this.pendingSkeletonData = skeletonVertices;
        this.skelVertexCount = skeletonVertices.length / 7;
        this.isSkeletonDataDirty = true;
        
        // 描画スレッドに更新を通知
        repaint();
    }

    public void setShowSkeleton(boolean show) {
        this.showSkeleton = show;
    }

    private void initSkeletonGL() {
        if (skelShaderProgram != -1) return;

        // 頂点ごとの色を受け取り、そのままフラグメントシェーダーに渡す
        String vertSrc = 
            "#version 330 core\n" +
            "layout (location = 0) in vec3 aPos;\n" +
            "layout (location = 1) in vec4 aColor;\n" +
            "uniform mat4 mvp;\n" +
            "out vec4 vColor;\n" +
            "void main() { \n" +
            "   gl_Position = mvp * vec4(aPos, 1.0); \n" +
            "   vColor = aColor; \n" +
            "}\n";

        String fragSrc = 
            "#version 330 core\n" +
            "in vec4 vColor;\n" +
            "out vec4 FragColor;\n" +
            "void main() { FragColor = vColor; }\n";

        int vShader = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
        GL20.glShaderSource(vShader, vertSrc);
        GL20.glCompileShader(vShader);

        int fShader = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GL20.glShaderSource(fShader, fragSrc);
        GL20.glCompileShader(fShader);

        skelShaderProgram = GL20.glCreateProgram();
        GL20.glAttachShader(skelShaderProgram, vShader);
        GL20.glAttachShader(skelShaderProgram, fShader);
        GL20.glLinkProgram(skelShaderProgram);

        GL20.glDeleteShader(vShader);
        GL20.glDeleteShader(fShader);
    }

    private void renderSkeleton() {
        if (skelVao == -1 || skelVertexCount == 0) return;

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        
        // Floating表示にするためデプステストを無効化（常に手前に表示）
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glLineWidth(2.0f); // スケルトンの線の太さ

        GL20.glUseProgram(skelShaderProgram);
        GL30.glBindVertexArray(skelVao);

        double scaleX = getGraphicsConfiguration() != null ? getGraphicsConfiguration().getDefaultTransform().getScaleX() : 1.0;
        double scaleY = getGraphicsConfiguration() != null ? getGraphicsConfiguration().getDefaultTransform().getScaleY() : 1.0;
        float aspect = (float) (w * scaleX) / (float) (h * scaleY);

        Matrix4f proj = new Matrix4f().setPerspective((float) Math.toRadians(45.0f), aspect, 0.01f, 100.0f);
        Matrix4f view = getCamera().getViewMatrix();
        Matrix4f baseModel = getModelMatrix();
        
        // 正規化座標へのスケーリング（バウンディングボックスの時と同じ処理）
        VolumeData vol = getVolumeData();
        Matrix4f skelModel = new Matrix4f(baseModel)
                .translate(-0.5f, -0.5f, -0.5f) // 原点を左下奥から中心へ
                .scale(1.0f / vol.width, 1.0f / vol.height, 1.0f / vol.depth);

        Matrix4f mvp = new Matrix4f(proj).mul(view).mul(skelModel);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer mvpBuffer = stack.mallocFloat(16);
            mvp.get(mvpBuffer);
            int mvpLoc = GL20.glGetUniformLocation(skelShaderProgram, "mvp");
            GL20.glUniformMatrix4fv(mvpLoc, false, mvpBuffer);
        }

        // 線の描画（GL_LINES は2頂点で1本の線を引く）
        GL11.glDrawArrays(GL11.GL_LINES, 0, skelVertexCount);

        GL30.glBindVertexArray(0);
        GL20.glUseProgram(0);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

//    @Override
//    public void paintGL() {
//        super.paintGL();
//
//        // ==========================================================
//        // ★追加: 安全なOpenGLスレッド上で、待機中のデータをGPUへ転送する
//        // ==========================================================
//        if (isSkeletonDataDirty && pendingSkeletonData != null) {
//            if (skelVao == -1) {
//                skelVao = GL30.glGenVertexArrays();
//                skelVbo = GL15.glGenBuffers();
//            }
//
//            GL30.glBindVertexArray(skelVao);
//            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, skelVbo);
//            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, pendingSkeletonData, GL15.GL_STATIC_DRAW);
//
//            GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 7 * Float.BYTES, 0);
//            GL20.glEnableVertexAttribArray(0);
//
//            GL20.glVertexAttribPointer(1, 4, GL11.GL_FLOAT, false, 7 * Float.BYTES, 3 * Float.BYTES);
//            GL20.glEnableVertexAttribArray(1);
//
//            GL30.glBindVertexArray(0);
//
//            // 転送完了したのでフラグを降ろし、メモリを解放
//            isSkeletonDataDirty = false;
//            pendingSkeletonData = null; 
//        }
//
//        // 既存の枠線描画
//        if (showBoundingBoxes && candidates != null && getVolumeData() != null) {
//            initBoxGL();
//            renderBoundingBoxes();
//        }
//
//        // スケルトンの描画
//        if (showSkeleton && getVolumeData() != null) {
//            initSkeletonGL();
//            renderSkeleton();
//        }
//		// ==========================================================
//		// ★ 追加: 全てのオーバーレイを描き終わったら、GPUに処理の完了を待機させ、
//		// 確実に画面に反映させてから次のフレームに進むようにする
//		// ==========================================================
//		org.lwjgl.opengl.GL11.glFinish();
//	}
    
    @Override
    public void paintGL() {
        // --- 計測開始 ---
//        long frameStart = System.nanoTime();

        // 1. 親クラスの描画（ボリュームと既存ROI）
        super.paintGL();
//        long superEnd = System.nanoTime();

        // 2. VBOへのデータ転送処理（Skeletonデータの遅延転送）
        if (isSkeletonDataDirty && pendingSkeletonData != null) {
            if (skelVao == -1) {
                skelVao = org.lwjgl.opengl.GL30.glGenVertexArrays();
                skelVbo = org.lwjgl.opengl.GL15.glGenBuffers();
            }
            org.lwjgl.opengl.GL30.glBindVertexArray(skelVao);
            org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER, skelVbo);
            org.lwjgl.opengl.GL15.glBufferData(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER, pendingSkeletonData, org.lwjgl.opengl.GL15.GL_STATIC_DRAW);
            org.lwjgl.opengl.GL20.glVertexAttribPointer(0, 3, org.lwjgl.opengl.GL11.GL_FLOAT, false, 7 * Float.BYTES, 0);
            org.lwjgl.opengl.GL20.glEnableVertexAttribArray(0);
            org.lwjgl.opengl.GL20.glVertexAttribPointer(1, 4, org.lwjgl.opengl.GL11.GL_FLOAT, false, 7 * Float.BYTES, 3 * Float.BYTES);
            org.lwjgl.opengl.GL20.glEnableVertexAttribArray(1);
            org.lwjgl.opengl.GL30.glBindVertexArray(0);

            isSkeletonDataDirty = false;
            pendingSkeletonData = null; 
        }

        // 3. 枠線の描画
//        long boxStart = System.nanoTime();
        if (showBoundingBoxes && candidates != null && getVolumeData() != null) {
            initBoxGL();
            renderBoundingBoxes();
        }
//        long boxEnd = System.nanoTime();

        // 4. スケルトンの描画
//        long skelStart = System.nanoTime();
        if (showSkeleton && getVolumeData() != null) {
            initSkeletonGL();
            renderSkeleton();
        }
//        long skelEnd = System.nanoTime();

        /*
         * 超重要
         * gldata.doubleBuffer(true)にすること。
         */
        try {
            swapBuffers(); 
        } catch (Exception e) {
            e.printStackTrace();
        }
//        long finishEnd = System.nanoTime();

        // --- 計測とログ出力 (1秒ごとに集計して出力) ---
//        frameCounter++;
//        long now = System.currentTimeMillis();
//        if (now - lastLogTime >= 1000) {
//            double superMs = (superEnd - frameStart) / 1000000.0;
//            double boxMs   = (boxEnd - boxStart) / 1000000.0;
//            double skelMs  = (skelEnd - skelStart) / 1000000.0;
//            double flushMs = (finishEnd - skelEnd) / 1000000.0;
//            double totalMs = (finishEnd - frameStart) / 1000000.0;
//
//            System.out.println("=== [GLCanvas Flicker Investigation] ===");
//            System.out.println("Thread: " + Thread.currentThread().getName());
//            System.out.println("Actual FPS: " + frameCounter);
//            System.out.println("1. super.paintGL (Volume) : " + String.format("%.2f ms", superMs));
//            System.out.println("2. Box Render             : " + String.format("%.2f ms", boxMs));
//            System.out.println("3. Skeleton Render        : " + String.format("%.2f ms", skelMs));
//            System.out.println("4. glFinish Wait          : " + String.format("%.2f ms", flushMs));
//            System.out.println("Total Frame Time          : " + String.format("%.2f ms", totalMs));
//            System.out.println("========================================");
//
//            frameCounter = 0;
//            lastLogTime = now;
//        }
    }

    private void renderBoundingBoxes() {
        int w = getWidth();
        int h = getHeight();
        // ★修正: 画面サイズが確定していない初期ロード時のゼロ除算（NaN）クラッシュを防止！
        if (w <= 0 || h <= 0) return; 

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glLineWidth(2.0f); 

        GL20.glUseProgram(shaderProgram);
        GL30.glBindVertexArray(vao);

        double scaleX = getGraphicsConfiguration() != null ? getGraphicsConfiguration().getDefaultTransform().getScaleX() : 1.0;
        double scaleY = getGraphicsConfiguration() != null ? getGraphicsConfiguration().getDefaultTransform().getScaleY() : 1.0;
        int physW = (int) Math.round(w * scaleX);
        int physH = (int) Math.round(h * scaleY);
        float aspect = (float) physW / physH;

        Matrix4f proj = new Matrix4f().setPerspective((float) Math.toRadians(45.0f), aspect, 0.01f, 100.0f);
        Matrix4f view = getCamera().getViewMatrix();
        Matrix4f baseModel = getModelMatrix();
        VolumeData vol = getVolumeData();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer mvpBuffer = stack.mallocFloat(16);

            for (AneurysmCandidate c : candidates) {
            	if (!showAllBoundingBoxes && c != highlightedCandidate) {
                    continue;
                }
				// ==========================================================
				// ★ 修正: レガシーな glColor3f ではなく、シェーダーの Uniform 変数に色を送る
				// ==========================================================
				if (c == highlightedCandidate) {
					// 選択中の色（例：目立つ黄色 / R, G, B, Alpha）
					GL20.glUniform4f(colorLoc, 1.0f, 1.0f, 0.0f, 1.0f);
				} else {
					// 通常の色（例：オレンジ / R, G, B, Alpha）
					GL20.glUniform4f(colorLoc, 1.0f, 0.5f, 0.0f, 1.0f);
				}
				Point3D p = c.getPeakPoint();
				//左手系の動脈瘤座標 p.x を、右手系ボリュームのX軸に合わせて反転マッピングします。
				// ★ 左手系インデックス p.x を 右手系（vol.width - 1 - p.x）に変換
				float cx = ((vol.width - 1 - p.x) + 0.5f) / vol.width - 0.5f;
                float cy = (p.y + 0.5f) / vol.height - 0.5f;
                float cz = (p.z + 0.5f) / vol.depth - 0.5f;

                double radiusMm = Math.min(5.0, c.getMaxBulgeRatio() * 1.5) * 1.2;
                float bw = (float) ((radiusMm * 2) / (vol.pixelSpacingX * vol.width));
                float bh = (float) ((radiusMm * 2) / (vol.pixelSpacingY * vol.height));
                float bd = (float) ((radiusMm * 2) / (vol.sliceThickness * vol.depth));

                Matrix4f boxModel = new Matrix4f(baseModel)
                        .translate(cx, cy, cz)
                        .scale(bw, bh, bd);
                
                Matrix4f mvp = new Matrix4f(proj).mul(view).mul(boxModel);

                mvp.get(mvpBuffer);
                GL20.glUniformMatrix4fv(mvpLoc, false, mvpBuffer);
                GL11.glDrawArrays(GL11.GL_LINES, 0, 24);
            }
        }

        GL30.glBindVertexArray(0);
        GL20.glUseProgram(0);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }
    
    // ========================================================================
 	// ★修正: 指定した動脈瘤（ボクセル座標）にカメラをフォーカスする
 	// ========================================================================
 	public void focusOn(com.vis.aneurysmdetector.core.Point3D p) {
 		VolumeData vol = getVolumeData();
 		if (vol == null || p == null)
 			return;

 		// 1. ボクセル空間(0 ~ width) を 正規化座標系(-0.5 ~ 0.5) に変換 (ローカル座標)
 		// ★ カメラが正しい右手系ボリュームの動脈瘤候補を向くように反転
 		float cx = ((vol.width - 1 - p.x) + 0.5f) / vol.width - 0.5f;
 		float cy = (p.y + 0.5f) / vol.height - 0.5f;
 		float cz = (p.z + 0.5f) / vol.depth - 0.5f;

 		// ==========================================================
 		// ★ 追加: ローカル座標をワールド座標（実際の描画位置）に変換する
 		// ==========================================================
 		org.joml.Matrix4f baseModel = getModelMatrix();
 		org.joml.Vector3f worldPos = new org.joml.Vector3f(cx, cy, cz);
 		worldPos.mulPosition(baseModel); // ボリュームのスケーリングや反転を適用

 		// 2. 変換後のワールド座標をカメラの注視点(target)に設定
 		getCamera().lookAt(worldPos.x, worldPos.y, worldPos.z, 0.35f);

 		// 再描画を要求
 		repaint();
     }
	
	// ========================================================================
    // ★ 追加: カメラの回転・ズーム・注視点をすべて初期状態に戻す
    // ========================================================================
    public void resetCamera() {
        getCamera().reset(); // Camera.java 内の reset() を呼び出す
        repaint();           // キャンバスを再描画
    }
}