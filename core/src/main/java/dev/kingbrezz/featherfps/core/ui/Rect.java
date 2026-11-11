package dev.kingbrezz.featherfps.core.ui;

public record Rect(int x, int y, int w, int h) {
    public int right() { return x + w; }
    public int bottom() { return y + h; }
    public boolean isEmpty() { return w <= 0 || h <= 0; }
    public boolean contains(double px, double py) { return px >= x && px < x + w && py >= y && py < y + h; }
    public Rect inset(int d) { return new Rect(x + d, y + d, w - 2 * d, h - 2 * d); }

    public Rect intersect(Rect o) {
        int nx = Math.max(x, o.x), ny = Math.max(y, o.y);
        int nr = Math.min(right(), o.right()), nb = Math.min(bottom(), o.bottom());
        return new Rect(nx, ny, Math.max(0, nr - nx), Math.max(0, nb - ny));
    }
}
