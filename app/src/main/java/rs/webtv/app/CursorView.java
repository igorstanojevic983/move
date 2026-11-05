package rs.webtv.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Strelica kursora koja se crta preko web stranice. Ne prima dodire. */
public class CursorView extends View {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pressedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();
    private float x, y;
    private boolean pressed;
    private boolean shown = true;
    private final float unit;

    public CursorView(Context ctx) {
        super(ctx);
        float density = ctx.getResources().getDisplayMetrics().density;
        unit = Math.max(1.6f, density * 1.4f);

        fill.setColor(0xFFFFFFFF);
        fill.setStyle(Paint.Style.FILL);
        stroke.setColor(0xFF000000);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(unit * 1.2f);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        pressedPaint.setColor(0x6642A5F5);
        pressedPaint.setStyle(Paint.Style.FILL);

        // Klasična strelica miša (koordinate u "jedinicama")
        float[][] pts = {{0, 0}, {0, 17}, {4, 13}, {7, 20}, {9.5f, 19}, {6.5f, 12}, {12, 12}};
        arrow.moveTo(pts[0][0] * unit, pts[0][1] * unit);
        for (int i = 1; i < pts.length; i++) arrow.lineTo(pts[i][0] * unit, pts[i][1] * unit);
        arrow.close();

        setClickable(false);
        setFocusable(false);
    }

    public void setPosition(float nx, float ny) {
        x = nx;
        y = ny;
        invalidate();
    }

    public void setPressed2(boolean p) {
        pressed = p;
        invalidate();
    }

    public void setShown(boolean s) {
        if (shown != s) {
            shown = s;
            invalidate();
        }
    }

    public boolean isShown2() {
        return shown;
    }

    @Override
    protected void onDraw(Canvas c) {
        if (!shown) return;
        if (pressed) c.drawCircle(x, y, unit * 9, pressedPaint);
        c.save();
        c.translate(x, y);
        c.drawPath(arrow, fill);
        c.drawPath(arrow, stroke);
        c.restore();
    }
}
