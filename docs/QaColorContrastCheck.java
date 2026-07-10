// 极简 JVM 验证：校验 Color.kt 提取出的语义色对是否满足 WCAG AA 对比度。
// 说明：Spacing / Color 对象依赖 Compose UI 运行时（.dp），沙箱无 Android SDK 无法编译运行，
// 故此处对“从源码提取的 8 位十六进制字面量”做独立校验，作为静态核验的量化补充证据。
public class QaColorContrastCheck {
    static int[] rgb(String hex8) {
        if (hex8.length() != 8) throw new IllegalArgumentException("not 8-digit: " + hex8);
        return new int[] {
            Integer.parseInt(hex8.substring(2, 4), 16),
            Integer.parseInt(hex8.substring(4, 6), 16),
            Integer.parseInt(hex8.substring(6, 8), 16)
        };
    }
    static double lin(int c) {
        double s = c / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }
    static double lum(int[] c) {
        return 0.2126 * lin(c[0]) + 0.7152 * lin(c[1]) + 0.0722 * lin(c[2]);
    }
    static double contrast(int[] a, int[] b) {
        double l1 = lum(a), l2 = lum(b);
        double hi = Math.max(l1, l2), lo = Math.min(l1, l2);
        return (hi + 0.05) / (lo + 0.05);
    }
    static int fails = 0;
    static void check(String name, String fg8, String bg8, double minAA) {
        int[] f = rgb(fg8), b = rgb(bg8);
        double cr = contrast(f, b);
        boolean ok = cr >= minAA;
        if (!ok) fails++;
        System.out.printf("%-26s contrast=%.2f  AA(%.1f)=%s%n", name, cr, minAA, ok ? "PASS" : "FAIL");
    }
    public static void main(String[] a) {
        // 浅色方案
        check("L primary / onPrimary ", "FFFFFFFF", "FF3B5BDB", 4.5);
        check("L onSurface / surface ", "FF1A1C20", "FFFDFCFF", 4.5);
        check("L onSurfVar / surfVar ", "FF44474E", "FFE0E2F3", 4.5);
        check("L onPrimCont / primCont", "FF00105C", "FFDEE2FF", 4.5);
        check("L error / onError      ", "FFFFFFFF", "FFBA1A1A", 4.5);
        // 深色方案
        check("D onPrimary / primary  ", "FF05257E", "FFAFC1FF", 4.5);
        check("D onSurface / surface  ", "FFE3E2E6", "FF1A1C20", 4.5);
        check("D onSurfVar / surfVar  ", "FFC4C6D4", "FF44474E", 4.5);
        check("D onError / error      ", "FF690005", "FFFFB4AB", 4.5);
        System.out.println(fails == 0 ? "RESULT: ALL COLOR PAIRS PASS AA" : "RESULT: " + fails + " PAIR(S) FAIL AA");
        System.exit(fails == 0 ? 0 : 1);
    }
}
