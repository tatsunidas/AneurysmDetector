package com.vis.aneurysmdetector.core;

import java.util.Objects;

/**
 * 3D画像空間上のボクセル座標（インデックス）を表現するクラス。
 */
public class Point3D {
    public int x;
    public int y;
    public int z;

    public Point3D(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * 2点間のユークリッド距離（ピクセル単位）を計算
     */
    public double distanceTo(Point3D other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        double dz = this.z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Point3D point3D = (Point3D) o;
        return x == point3D.x && y == point3D.y && z == point3D.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }

    @Override
    public String toString() {
        return String.format("(%d, %d, %d)", x, y, z);
    }
}