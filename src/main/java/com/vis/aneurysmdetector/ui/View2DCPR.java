package com.vis.aneurysmdetector.ui;

import com.vis.aneurysmdetector.core.AneurysmCandidate;
import com.vis.aneurysmdetector.cpr.UnwrappedImage2D;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * ③の側壁瘤探索に用いる、血管の展開図（CPR距離マップ）を描画するコンポーネント。
 */
public class View2DCPR extends JPanel {

    private UnwrappedImage2D unwrappedData;
    private List<AneurysmCandidate> anomalies;
    
    private CPRClickListener clickListener;

    public interface CPRClickListener {
        void onAnomalyClicked(AneurysmCandidate candidate);
    }

    public View2DCPR() {
        setBackground(new Color(30, 30, 30)); // ダークグレー背景
        
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                handleMouseClick(e.getX(), e.getY());
            }
        });
    }

    public void setCPRClickListener(CPRClickListener listener) {
        this.clickListener = listener;
    }

    public void setUnwrappedData(UnwrappedImage2D unwrappedData, List<AneurysmCandidate> anomalies) {
        this.unwrappedData = unwrappedData;
        this.anomalies = anomalies;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (unwrappedData == null) return;

        Graphics2D g2d = (Graphics2D) g;
        double[][] map = unwrappedData.getDistanceMap();
        int len = unwrappedData.getLengthSteps();
        int angles = unwrappedData.getAngleSteps();

        double scaleX = (double) getWidth() / angles;
        double scaleY = (double) getHeight() / len;

        // 1. 展開図（距離マップ）の描画
        // 半径が大きい（壁が遠い）ほど明るく表示する
        double maxRadius = 15.0; // 正規化のための最大半径（mm）
        
        for (int z = 0; z < len; z++) {
            for (int a = 0; a < angles; a++) {
                double radius = map[z][a];
                int intensity = (int) Math.max(0, Math.min(255, (radius / maxRadius) * 255));
                
                // MRAライクなグレースケール描画
                g2d.setColor(new Color(intensity, intensity, intensity));
                
                int px = (int) (a * scaleX);
                int py = (int) (z * scaleY);
                int pw = (int) Math.ceil(scaleX);
                int ph = (int) Math.ceil(scaleY);
                g2d.fillRect(px, py, pw, ph);
            }
        }

        // 2. 異常起伏（候補）のハイライト描画
        if (anomalies != null && !anomalies.isEmpty()) {
            g2d.setColor(new Color(255, 69, 0, 180)); // 目立つオレンジレッド
            g2d.setStroke(new BasicStroke(2.0f));
            
            for (AneurysmCandidate anomaly : anomalies) {
                // TODO: anomalyのCenterPointからZインデックス（Y座標）を逆算する
                // 仮実装として中央付近に枠を描画
                int highlightY = getHeight() / 2; 
                g2d.drawRect(5, highlightY - 15, getWidth() - 10, 30);
            }
        }
    }

    private void handleMouseClick(int mouseX, int mouseY) {
        if (unwrappedData == null || anomalies == null || clickListener == null) return;

        // クリックされたY座標からZインデックスを求め、対応する候補を探す
        int len = unwrappedData.getLengthSteps();
        double scaleY = (double) getHeight() / len;
        int clickedZ = (int) (mouseY / scaleY);

        // 近似する候補を発火
        for (AneurysmCandidate anomaly : anomalies) {
            // TODO: Zインデックスとの距離判定
            clickListener.onAnomalyClicked(anomaly);
            break; // 最初に見つかったものを発火
        }
    }
}