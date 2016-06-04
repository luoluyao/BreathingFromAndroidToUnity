package com.mina.breathitout;

import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;

public class BubbleView extends View {
  private static final int RED = 0xffFF8080;
  private static final int BLUE = 0xff8080FF;
  private static final int CYAN = 0xff80ffff;
  private static final int GREEN = 0xff80ff80;
  int currentY;
  float diameter = 200f;
  public ShapeHolder newBall;
  AnimatorSet animation = null;
  public BubbleView(Context context) {
    super(context);
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    super.onLayout(changed, l, t, r, b);
    currentY = getHeight();
    newBall = addBall(getWidth()/2, currentY); //height is ready
  }

  public void resetBall() {
    currentY = getHeight();
    newBall = addBall(getWidth()/2, currentY);
  }

  public void moveUP() {
    currentY = 100;
    ValueAnimator bounceAnim = ObjectAnimator.ofFloat(newBall, "y", getHeight()-diameter, 0);
    bounceAnim.setDuration(2500);
    AnimatorSet bouncer = new AnimatorSet();
    bouncer.play(bounceAnim);
    bouncer.start();
//    Log.d("Bubble", "currentY: "+currentY);
//    newBall = addBall(getWidth()/2, currentY);
  }
  public void moveDown() {
    currentY += 100;
    ValueAnimator bounceAnim = ObjectAnimator.ofFloat(newBall, "y", 0, getHeight()-diameter);
    bounceAnim.setDuration(2500);
    AnimatorSet bouncer = new AnimatorSet();
    bouncer.play(bounceAnim);
    bouncer.start();
//    Log.d("Bubble", "currentY: "+currentY);
//    newBall = addBall(getWidth()/2, currentY);
  }
  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (event.getAction() != MotionEvent.ACTION_DOWN &&
        event.getAction() != MotionEvent.ACTION_MOVE) {
      return false;
    }
    moveUP();
    // Bouncing animation with squash and stretch
//    newBall = addBall(getWidth()/2, getHeight());
//    float startY = newBall.getY();
//    float endY = getHeight() - 50f;
//    float h = (float)getHeight();
//    float eventY = event.getY();
//    int duration = (int)(500 * ((h - eventY)/h));
//    bounceAnim.setInterpolator(new AccelerateInterpolator());
//    ValueAnimator squashAnim1 = ObjectAnimator.ofFloat(newBall, "x", newBall.getX(),
//        newBall.getX() - 25f);
//    squashAnim1.setDuration(duration/4);
//    squashAnim1.setRepeatCount(1);
//    squashAnim1.setRepeatMode(ValueAnimator.REVERSE);
//    squashAnim1.setInterpolator(new DecelerateInterpolator());
//    ValueAnimator squashAnim2 = ObjectAnimator.ofFloat(newBall, "width", newBall.getWidth(),
//        newBall.getWidth() + 50);
//    squashAnim2.setDuration(duration/4);
//    squashAnim2.setRepeatCount(1);
//    squashAnim2.setRepeatMode(ValueAnimator.REVERSE);
//    squashAnim2.setInterpolator(new DecelerateInterpolator());
//    ValueAnimator stretchAnim1 = ObjectAnimator.ofFloat(newBall, "y", endY,
//        endY + 25f);
//    stretchAnim1.setDuration(duration/4);
//    stretchAnim1.setRepeatCount(1);
//    stretchAnim1.setInterpolator(new DecelerateInterpolator());
//    stretchAnim1.setRepeatMode(ValueAnimator.REVERSE);
//    ValueAnimator stretchAnim2 = ObjectAnimator.ofFloat(newBall, "height",
//        newBall.getHeight(), newBall.getHeight() - 25);
//    stretchAnim2.setDuration(duration/4);
//    stretchAnim2.setRepeatCount(1);
//    stretchAnim2.setInterpolator(new DecelerateInterpolator());
//    stretchAnim2.setRepeatMode(ValueAnimator.REVERSE);
//    ValueAnimator bounceBackAnim = ObjectAnimator.ofFloat(newBall, "y", endY,
//        startY);
//    bounceBackAnim.setDuration(duration);
//    bounceBackAnim.setInterpolator(new DecelerateInterpolator());
//    // Sequence the down/squash&stretch/up animations
//    AnimatorSet bouncer = new AnimatorSet();
//    bouncer.play(bounceAnim).before(squashAnim1);
//    bouncer.play(squashAnim1).with(squashAnim2);
//    bouncer.play(squashAnim1).with(stretchAnim1);
//    bouncer.play(squashAnim1).with(stretchAnim2);
//    bouncer.play(bounceBackAnim).after(stretchAnim2);
//    // Fading animation - remove the ball when the animation is done
//    ValueAnimator fadeAnim = ObjectAnimator.ofFloat(newBall, "alpha", 1f, 0f);
//    fadeAnim.setDuration(250);
//    // Sequence the two animations to play one after the other
//    AnimatorSet animatorSet = new AnimatorSet();
//    animatorSet.play(bouncer).before(fadeAnim);
//    // Start the animation
//    animatorSet.start();
    return true;
  }

  private ShapeHolder addBall(float x, float y) {
    OvalShape circle = new OvalShape();
    circle.resize(diameter, diameter);
    ShapeDrawable drawable = new ShapeDrawable(circle);
    ShapeHolder shapeHolder = new ShapeHolder(drawable);
    shapeHolder.setX(x - diameter/2f);
    shapeHolder.setY(y - diameter);
    int red = (int)(0.9 * 255);
    int green = (int)(0.9 * 255);
    int blue = (int)(0.9 * 255);
    int color = 0xff000000 | red << 16 | green << 8 | blue;
    Paint paint = drawable.getPaint(); //new Paint(Paint.ANTI_ALIAS_FLAG);
    int darkColor = 0xff000000 | red/4 << 16 | green/4 << 8 | blue/4;
    RadialGradient gradient = new RadialGradient(100f, 100f,
        100f, color, darkColor, Shader.TileMode.CLAMP);
    paint.setShader(gradient);
    shapeHolder.setPaint(paint);
    return shapeHolder;
  }
  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
//    canvas.save();
    canvas.translate(newBall.getX(), newBall.getY());
    Log.d("Bubble", "get Y: "+ newBall.getY());
    newBall.getShape().draw(canvas);
//    canvas.restore();
  }
}