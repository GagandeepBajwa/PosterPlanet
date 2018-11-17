package com.example.bradleycrump.arcoretutorial3;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;

public class PointerDrawable extends Drawable {

    private final Paint paint = new Paint();
    private boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void draw(@androidx.annotation.NonNull Canvas canvas) {
        float cx = canvas.getWidth()/2;
        float cy = canvas.getHeight()/2;
        if(enabled){
            paint.setColor(Color.GREEN);
            canvas.drawCircle(cx, cy, 20, paint);
        }
        else {
            paint.setColor(Color.GRAY);
            canvas.drawText("X", cx, cy, paint);
        }

    }

    @Override
    public void setAlpha(int alpha) {

    }

    @Override
    public void setColorFilter(@androidx.annotation.Nullable ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity() {
        return 0;
    }
}
