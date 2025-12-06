package com.example.examplemod.ColorHandler;


public class ColorUtils {

    public static double[] rgbToLab(int r, int g, int b) {
        double[] xyz = rgbToXyz(r, g, b);
        return xyzToLab(xyz[0], xyz[1], xyz[2]);
    }

    private static double[] rgbToXyz(int r, int g, int b) {
        double rr = pivotRgb(r / 255.0);
        double gg = pivotRgb(g / 255.0);
        double bb = pivotRgb(b / 255.0);

        double x = rr * 0.4124 + gg * 0.3576 + bb * 0.1805;
        double y = rr * 0.2126 + gg * 0.7152 + bb * 0.0722;
        double z = rr * 0.0193 + gg * 0.1192 + bb * 0.9505;

        return new double[]{x, y, z};
    }

    private static double pivotRgb(double n) {
        return n <= 0.04045 ? n / 12.92 : Math.pow((n + 0.055) / 1.055, 2.4);
    }

    private static double[] xyzToLab(double x, double y, double z) {
        double Xn = 0.95047, Yn = 1.00000, Zn = 1.08883;

        x /= Xn;
        y /= Yn;
        z /= Zn;

        x = pivotXyz(x);
        y = pivotXyz(y);
        z = pivotXyz(z);

        double L = Math.max(0, 116 * y - 16);
        double a = 500 * (x - y);
        double b = 200 * (y - z);

        return new double[]{L, a, b};
    }

    private static double pivotXyz(double n) {
        return n > 0.008856 ? Math.cbrt(n) : (7.787 * n) + 16 / 116.0;
    }

    public static double deltaE2000(double[] lab1, double[] lab2) {
        double L1 = lab1[0], a1 = lab1[1], b1 = lab1[2];
        double L2 = lab2[0], a2 = lab2[1], b2 = lab2[2];

        double avgLp = (L1 + L2) / 2.0;

        double C1 = Math.sqrt(a1 * a1 + b1 * b1);
        double C2 = Math.sqrt(a2 * a2 + b2 * b2);
        double avgC = (C1 + C2) / 2.0;

        double G = 0.5 * (1 - Math.sqrt(Math.pow(avgC, 7) / (Math.pow(avgC, 7) + Math.pow(25.0, 7))));
        double a1p = (1.0 + G) * a1;
        double a2p = (1.0 + G) * a2;

        double C1p = Math.sqrt(a1p * a1p + b1 * b1);
        double C2p = Math.sqrt(a2p * a2p + b2 * b2);

        double avgCp = (C1p + C2p) / 2.0;

        double h1p = Math.toDegrees(Math.atan2(b1, a1p));
        if (h1p < 0) h1p += 360;
        double h2p = Math.toDegrees(Math.atan2(b2, a2p));
        if (h2p < 0) h2p += 360;

        double avghp;
        double dhp = h2p - h1p;
        if (Math.abs(dhp) > 180) {
            if (h2p <= h1p) dhp += 360;
            else dhp -= 360;
        }

        avghp = (Math.abs(h1p - h2p) > 180)
                ? (h1p + h2p + 360) / 2.0
                : (h1p + h2p) / 2.0;

        double T = 1
                - 0.17 * Math.cos(Math.toRadians(avghp - 30))
                + 0.24 * Math.cos(Math.toRadians(2 * avghp))
                + 0.32 * Math.cos(Math.toRadians(3 * avghp + 6))
                - 0.20 * Math.cos(Math.toRadians(4 * avghp - 63));

        double dLp = L2 - L1;
        double dCp = C2p - C1p;

        double dhPrime = 2 * Math.sqrt(C1p * C2p) * Math.sin(Math.toRadians(dhp / 2.0));

        double avgLpMinus50Sq = (avgLp - 50);
        avgLpMinus50Sq *= avgLpMinus50Sq;

        double Sl = 1 + (0.015 * avgLpMinus50Sq) / Math.sqrt(20 + avgLpMinus50Sq);
        double Sc = 1 + 0.045 * avgCp;
        double Sh = 1 + 0.015 * avgCp * T;

        double deltaTheta = 30 * Math.exp(-Math.pow((avghp - 275) / 25, 2));
        double Rc = 2 * Math.sqrt(Math.pow(avgCp, 7) / (Math.pow(avgCp, 7) + Math.pow(25.0, 7)));
        double Rt = -Rc * Math.sin(Math.toRadians(2 * deltaTheta));

        return Math.sqrt(
                Math.pow(dLp / Sl, 2)
                        + Math.pow(dCp / Sc, 2)
                        + Math.pow(dhPrime / Sh, 2)
                        + Rt * (dCp / Sc) * (dhPrime / Sh)
        );
    }
}