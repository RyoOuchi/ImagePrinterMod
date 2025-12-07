package com.example.examplemod.ColorHandler;

/**
 * 色変換・色差計算用のユーティリティクラス。
 *
 * 役割：
 * - sRGB(0–255) → CIE XYZ 変換
 * - CIE XYZ → CIE Lab 変換
 * - 2つの Lab 色の色差 ΔE2000 の計算
 */
public class ColorUtils {

    /**
     * sRGB(0–255) を CIE Lab(L*, a*, b*) に変換する。
     *
     * 処理の流れ：
     * 1. sRGB → XYZ (rgbToXyz)
     * 2. XYZ → Lab (xyzToLab)
     *
     * @param r 赤成分(0–255)
     * @param g 緑成分(0–255)
     * @param b 青成分(0–255)
     * @return CIE Lab 値 {L, a, b}
     */
    public static double[] rgbToLab(int r, int g, int b) {
        double[] xyz = rgbToXyz(r, g, b);          // sRGB を XYZ に変換
        return xyzToLab(xyz[0], xyz[1], xyz[2]);   // XYZ を Lab に変換
    }

    /**
     * sRGB(0–255) を CIE XYZ に変換する。
     *
     * 手順：
     * 1. 0–255 の sRGB を 0–1 に正規化
     * 2. ガンマ補正（sRGB の逆ガンマ）を pivotRgb で行う
     * 3. 線形 RGB から XYZ 行列変換（標準的な sRGB → D65 XYZ 変換）
     *
     * @param r 赤成分(0–255)
     * @param g 緑成分(0–255)
     * @param b 青成分(0–255)
     * @return XYZ 値 {X, Y, Z}
     */
    private static double[] rgbToXyz(int r, int g, int b) {
        // 0–255 を 0–1 にしつつ、sRGB のガンマ補正を線形空間に戻す
        double rr = pivotRgb(r / 255.0);
        double gg = pivotRgb(g / 255.0);
        double bb = pivotRgb(b / 255.0);

        // sRGB → XYZ の変換行列（D65基準、一般的な値）
        double x = rr * 0.4124 + gg * 0.3576 + bb * 0.1805;
        double y = rr * 0.2126 + gg * 0.7152 + bb * 0.0722;
        double z = rr * 0.0193 + gg * 0.1192 + bb * 0.9505;

        return new double[]{x, y, z};
    }

    /**
     * sRGB の 0–1 値に対してガンマ補正を外し、線形RGBへ変換する。
     *
     * sRGB の定義に基づき、
     * - 低輝度部：n / 12.92
     * - 高輝度部：((n + 0.055) / 1.055)^2.4
     *
     * @param n sRGB (0–1)
     * @return 線形RGB (0–1)
     */
    private static double pivotRgb(double n) {
        return n <= 0.04045 ? n / 12.92 : Math.pow((n + 0.055) / 1.055, 2.4);
    }

    /**
     * CIE XYZ を CIE Lab に変換する。
     *
     * 手順：
     * 1. 参照白色点(D65)で正規化 (X/Xn, Y/Yn, Z/Zn)
     * 2. Lab用の非線形変換 pivotXyz を適用
     * 3. L, a, b を計算
     *
     * @param x X成分
     * @param y Y成分
     * @param z Z成分
     * @return Lab 値 {L, a, b}
     */
    private static double[] xyzToLab(double x, double y, double z) {
        // 参照白色 (D65) の XYZ 値（標準的な値）
        double Xn = 0.95047, Yn = 1.00000, Zn = 1.08883;

        // 参照白による正規化
        x /= Xn;
        y /= Yn;
        z /= Zn;

        // Lab 変換用の関数を適用
        x = pivotXyz(x);
        y = pivotXyz(y);
        z = pivotXyz(z);

        // L*, a*, b* の計算
        // L は [0,100] の範囲になるように 116*y - 16 で計算（下限0でクリップ）
        double L = Math.max(0, 116 * y - 16);
        double a = 500 * (x - y);
        double b = 200 * (y - z);

        return new double[]{L, a, b};
    }

    /**
     * XYZ を Lab に変換する際の非線形関数 f(t)。
     *
     * t > 0.008856 のとき：立方根
     * t <= 0.008856 のとき：線形近似 (7.787 * t + 16/116)
     *
     * @param n 正規化済み XYZ 成分
     * @return Lab 計算用の f(n)
     */
    private static double pivotXyz(double n) {
        return n > 0.008856 ? Math.cbrt(n) : (7.787 * n) + 16 / 116.0;
    }

    /**
     * 2つの Lab 色の間の色差 ΔE2000 を計算する。
     *
     * ΔE2000 は CIEDE2000 色差式に基づくもので、
     * ヒトの視覚特性により近い色差を表現できるのが特徴。
     *
     * 手順の概要（標準的な CIEDE2000 実装）：
     * 1. L*, a*, b* から彩度 C*, 色相角 h を計算
     * 2. 彩度補正係数 G を計算して a* を補正 (a')
     * 3. 補正後の彩度 C' と色相角 h' を計算
     * 4. 平均値やトーン依存補正 (Sl, Sc, Sh, Rt, Rc, T など) を計算
     * 5. 最終的に ΔL', ΔC', ΔH' をそれぞれの重みで割って合成し、色差 ΔE00 を算出
     *
     * @param lab1 Lab 色1 {L1, a1, b1}
     * @param lab2 Lab 色2 {L2, a2, b2}
     * @return ΔE2000 の値（0 に近いほど色が似ている）
     */
    public static double deltaE2000(double[] lab1, double[] lab2) {
        // Lab 値を取り出し
        double L1 = lab1[0], a1 = lab1[1], b1 = lab1[2];
        double L2 = lab2[0], a2 = lab2[1], b2 = lab2[2];

        // 明度の平均
        double avgLp = (L1 + L2) / 2.0;

        // 元の彩度 C* の計算
        double C1 = Math.sqrt(a1 * a1 + b1 * b1);
        double C2 = Math.sqrt(a2 * a2 + b2 * b2);
        double avgC = (C1 + C2) / 2.0;

        // 彩度依存の補正係数 G
        double G = 0.5 * (1 - Math.sqrt(Math.pow(avgC, 7) / (Math.pow(avgC, 7) + Math.pow(25.0, 7))));

        // a* を補正して a' を得る
        double a1p = (1.0 + G) * a1;
        double a2p = (1.0 + G) * a2;

        // 補正後の彩度 C'
        double C1p = Math.sqrt(a1p * a1p + b1 * b1);
        double C2p = Math.sqrt(a2p * a2p + b2 * b2);

        // 補正後の彩度平均
        double avgCp = (C1p + C2p) / 2.0;

        // 補正後の色相角 h'（-180～180 を 0～360 に正規化）
        double h1p = Math.toDegrees(Math.atan2(b1, a1p));
        if (h1p < 0) h1p += 360;
        double h2p = Math.toDegrees(Math.atan2(b2, a2p));
        if (h2p < 0) h2p += 360;

        double avghp;
        double dhp = h2p - h1p;

        // 180度をまたぐ場合に、差の向きを調整
        if (Math.abs(dhp) > 180) {
            if (h2p <= h1p) dhp += 360;
            else dhp -= 360;
        }

        // 平均色相角（360度まわりでの最短側を選ぶ）
        avghp = (Math.abs(h1p - h2p) > 180)
                ? (h1p + h2p + 360) / 2.0
                : (h1p + h2p) / 2.0;

        // T: 色相依存の補正項
        double T = 1
                - 0.17 * Math.cos(Math.toRadians(avghp - 30))
                + 0.24 * Math.cos(Math.toRadians(2 * avghp))
                + 0.32 * Math.cos(Math.toRadians(3 * avghp + 6))
                - 0.20 * Math.cos(Math.toRadians(4 * avghp - 63));

        // 明度差・彩度差
        double dLp = L2 - L1;
        double dCp = C2p - C1p;

        // 色相差 ΔH'（補正後彩度を使った項）
        double dhPrime = 2 * Math.sqrt(C1p * C2p) * Math.sin(Math.toRadians(dhp / 2.0));

        // 明度の平均 (L* - 50)^2 を使う補正
        double avgLpMinus50Sq = (avgLp - 50);
        avgLpMinus50Sq *= avgLpMinus50Sq;

        // 各成分のスケーリング係数 Sl, Sc, Sh
        double Sl = 1 + (0.015 * avgLpMinus50Sq) / Math.sqrt(20 + avgLpMinus50Sq);
        double Sc = 1 + 0.045 * avgCp;
        double Sh = 1 + 0.015 * avgCp * T;

        // 回転項 Rt の計算に使う中間値
        double deltaTheta = 30 * Math.exp(-Math.pow((avghp - 275) / 25, 2));
        double Rc = 2 * Math.sqrt(Math.pow(avgCp, 7) / (Math.pow(avgCp, 7) + Math.pow(25.0, 7)));
        double Rt = -Rc * Math.sin(Math.toRadians(2 * deltaTheta));

        // 最終的な ΔE2000 の計算
        // 各成分 ΔL', ΔC', ΔH' をそれぞれ Sl, Sc, Sh で割り、クロス項 Rt を含めて合成
        return Math.sqrt(
                Math.pow(dLp / Sl, 2)
                        + Math.pow(dCp / Sc, 2)
                        + Math.pow(dhPrime / Sh, 2)
                        + Rt * (dCp / Sc) * (dhPrime / Sh)
        );
    }
}