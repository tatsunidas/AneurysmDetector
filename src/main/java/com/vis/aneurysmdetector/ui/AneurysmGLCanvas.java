package com.vis.aneurysmdetector.ui;

import com.vis.aneurysmdetector.core.AneurysmCandidate;
import com.vis.aneurysmdetector.core.Point3D;
import com.vis.core.view.D3.ui.GLCanvas;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.awt.GLData;

import java.util.List;

public class AneurysmGLCanvas extends GLCanvas {

    private List<AneurysmCandidate> candidates;
    private AneurysmCandidate highlightedCandidate;
    private boolean showBoundingBoxes = false;

    // バウンディングボックス描画用のGLリソース
    private int shaderProgram;
    private int vao, vbo;
    private boolean isOverlayInitialized = false;

    // シンプルなライン描画用シェーダー
    private final String VERT_SRC = 
        "#version 330 core\n" +
        "layout (location = 0) in vec3 aPos;\n" +
        "uniform mat4 mvp;\n" +
        "void main() { gl_Position = mvp * vec4(aPos, 1.0); }\n";

    private final String FRAG_SRC = 
        "#version 330 core\n" +
        "out vec4 FragColor;\n" +
        "uniform vec4 uColor;\n" +
        "void main() { FragColor = uColor; }\n";

    public AneurysmGLCanvas(GLData data) {
        super(data);
    }

    public void setCandidates(List<AneurysmCandidate> candidates) {
        this.candidates = candidates;
    }

    public void setHighlightedCandidate(AneurysmCandidate candidate) {
        this.highlightedCandidate = candidate;
    }

    public void setShowBoundingBoxes(boolean show) {
        this.showBoundingBoxes = show;
    }

    private void initOverlayGL() {
        if (isOverlayInitialized) return;

        // シェーダーのコンパイル
        int vertexShader = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
        GL20.glShaderSource(vertexShader, VERT_SRC);
        GL20.glCompileShader(vertexShader);

        int fragmentShader = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GL20.glShaderSource(fragmentShader, FRAG_SRC);
        GL20.glCompileShader(fragmentShader);

        shaderProgram = GL20.glCreateProgram();
        GL20.glAttachShader(shaderProgram, vertexShader);
        GL20.glAttachShader(shaderProgram, fragmentShader);
        GL20.glLinkProgram(shaderProgram);

        GL20.glDeleteShader(vertexShader);
        GL20.glDeleteShader(fragmentShader);

        // バウンディングボックスの頂点 (中心0, サイズ1の立方体の12本の辺: 24頂点)
        float[] cubeLines = {
            -0.5f,-0.5f,-0.5f,  0.5f,-0.5f,-0.5f,   0.5f,-0.5f,-0.5f,  0.5f, 0.5f,-0.5f,
             0.5f, 0.5f,-0.5f, -0.5f, 0.5f,-0.5f,  -0.5f, 0.5f,-0.5f, -0.5f,-0.5f,-0.5f,
            -0.5f,-0.5f, 0.5f,  0.5f,-0.5f, 0.5f,   0.5f,-0.5f, 0.5f,  0.5f, 0.5f, 0.5f,
             0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f,  -0.5f, 0.5f, 0.5f, -0.5f,-0.5f, 0.5f,
            -0.5f,-0.5f,-0.5f, -0.5f,-0.5f, 0.5f,   0.5f,-0.5f,-0.5f,  0.5f,-0.5f, 0.5f,
             0.5f, 0.5f,-0.5f,  0.5f, 0.5f, 0.5f,  -0.5f, 0.5f,-0.5f, -0.5f, 0.5f, 0.5f
        };

        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();

        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, cubeLines, GL15.GL_STATIC_DRAW);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 3 * Float.BYTES, 0);
        GL20.glEnableVertexAttribArray(0);
        GL30.glBindVertexArray(0);

        isOverlayInitialized = true;
    }

    @Override
    public void render() {
        // 1. メインのボリュームレンダリング（ここで roiTex も描画される）
        super.render();

        // 2. バウンディングボックスのオーバーレイ描画
        if (showBoundingBoxes && candidates != null && getVolumeData() != null) {
            initOverlayGL();

            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_DEPTH_TEST); // ボリュームの手前に常に表示

            GL20.glUseProgram(shaderProgram);
            
            // superクラスが持っている現在のMVP行列を取得 (JOML等のFloatBufferを想定)
            java.nio.FloatBuffer mvpBuffer = getMvpMatrixBuffer(); 
            int mvpLoc = GL20.glGetUniformLocation(shaderProgram, "mvp");
            GL20.glUniformMatrix4fv(mvpLoc, false, mvpBuffer);

            int colorLoc = GL20.glGetUniformLocation(shaderProgram, "uColor");
            GL30.glBindVertexArray(vao);

            // VolumeData のサイズ（正規化座標系への変換用）
            int volW = getVolumeData().width;
            int volH = getVolumeData().height;
            int volD = getVolumeData().depth;

            for (AneurysmCandidate c : candidates) {
                if (c.isCleared()) continue;

                Point3D p = c.getPeakPoint();
                boolean isHighlighted = (c == highlightedCandidate);

                // ボックスのサイズと色（ハイライト時は緑、通常はオレンジ）
                float boxRadius = (float)(c.getMaxBulgeRatio() * 2.5f);
                float r = isHighlighted ? 0.0f : 1.0f;
                float g = isHighlighted ? 1.0f : 0.5f;
                float b = 0.0f;
                GL20.glUniform4f(colorLoc, r, g, b, 1.0f);

                // 描画ごとにモデル行列（スケールと平行移動）を適用したいが、
                // 簡単のため、今回は中心座標とサイズをオフセットとして渡す方法をとる
                // ※ ここは既存の行列スタックの実装に合わせて調整してください
                
                // (実装例：頂点位置 = (aPos * boxSize + offset) / volumeSize - 0.5)
            }

            GL30.glBindVertexArray(0);
            GL20.glUseProgram(0);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        }
    }

    /** 親クラスのMVP行列を取得する仮想メソッド（既存の設計に合わせて実装してください） */
    private java.nio.FloatBuffer getMvpMatrixBuffer() {
        // ... (JOML matrix.get(fb) などの処理)
        return null; 
    }
}