package com.mina.breathitout;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

public class BubbleView extends View {
  private static final int RED = 0xffFF8080;
  private static final int BLUE = 0xff8080FF;
  private static final int CYAN = 0xff80ffff;
  private static final int GREEN = 0xff80ff80;
  int incrementY;
  int currentY;
  float diameter = 200f;
  public ShapeHolder newBall;
  AnimatorSet animation = null;
  public Drawable cloud;
  public BubbleView(Context context) {
    super(context);
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    super.onLayout(changed, l, t, r, b);
    currentY = getHeight() - 150;
    incrementY = getHeight()/8;
    Log.d("incrementY", getHeight()+ " "+ incrementY);
    newBall = addBall(getWidth()/2, currentY); //height is ready
  }

  public void reset() {
    currentY = getHeight() - 150;
    newBall = addBall(getWidth()/2, currentY);
  }

  public void moveUP() {
    ValueAnimator bounceAnim = ObjectAnimator.ofFloat(newBall, "y", newBall.getY(), newBall.getY() - incrementY);
    Log.d("incrementY", getHeight()+ " "+ incrementY);
    bounceAnim.setDuration(2500);
    AnimatorSet bouncer = new AnimatorSet();
    bouncer.play(bounceAnim);
    bouncer.start();
  }
  public void moveDown() {
//    currentY += 100;
//    ValueAnimator bounceAnim = ObjectAnimator.ofFloat(newBall, "y", 100, getHeight()-200);
//    bounceAnim.setDuration(2500);
//    AnimatorSet bouncer = new AnimatorSet();
//    bouncer.play(bounceAnim);
//    bouncer.start();
//    Log.d("Bubble", "currentY: "+currentY);
//    newBall = addBall(getWidth()/2, currentY);
  }

  private ShapeHolder addBall(float x, float y) {
    OvalShape circle = new OvalShape();
    circle.resize(diameter, diameter);
    cloud = getResources().getDrawable(R.drawable.cloud_person);
    ShapeDrawable drawable = new ShapeDrawable(circle);
    ShapeHolder shapeHolder = new ShapeHolder(drawable);
    shapeHolder.setX(x - diameter/2f);
    shapeHolder.setY(y - diameter);
    int red = (int)(0.7 * 255);
    int green = (int)(0.7 * 255);
    int blue = (int)(0.7 * 255);
    int color = 0xff000000 | red << 16 | green << 8 | blue;
    Paint paint = drawable.getPaint(); //new Paint(Paint.ANTI_ALIAS_FLAG);
    int darkColor = 0xff000000 | blue;
    RadialGradient gradient = new RadialGradient(10f, 20f,
        100f, color, darkColor, Shader.TileMode.CLAMP);
    paint.setShader(gradient);
    paint.setAlpha(90);
    shapeHolder.setPaint(paint);
    return shapeHolder;
  }
  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
//    canvas.save();

    canvas.translate(newBall.getX() - 150, newBall.getY());
    cloud.setBounds(0, 0 , 500, 350);
    cloud.draw(canvas);
//    Log.d("Bubble", "get Y: "+ newBall.getY());
//    newBall.getShape().draw(canvas);
//    canvas.restore();
  }
}