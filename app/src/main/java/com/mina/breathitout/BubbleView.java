package com.mina.breathitout;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

public class BubbleView extends View {
  int incrementY;
  int currentY;
  float diameter = 200f;
  public ShapeHolder newBall;
  AnimatorSet animation = null;
  public AnimationDrawable cloud;
  AnimatorSet bouncer1;
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
    bob();
  }

  public void reset() {
    currentY = getHeight() - 150;
    newBall = addBall(getWidth()/2, currentY);
  }

  public void moveUP() {
    bouncer1.cancel();
    ValueAnimator bounceAnim = ObjectAnimator.ofFloat(newBall, "y", newBall.getY(), newBall.getY() - incrementY);
    Log.d("incrementY", getHeight()+ " "+ incrementY);
    bounceAnim.setDuration(2500);
    AnimatorSet bouncer = new AnimatorSet();
    bouncer.play(bounceAnim);
    bouncer.start();
  }

  public void bob() {
    ValueAnimator bounceAnim1 = ObjectAnimator.ofFloat(newBall, "y", newBall.getY(), newBall.getY() - 5, newBall.getY() + 5);
    Log.d("incrementY", getHeight()+ " "+ incrementY);
    bounceAnim1.setDuration(2000);
    bounceAnim1.setRepeatCount(ValueAnimator.INFINITE);
    bouncer1 = new AnimatorSet();
    bouncer1.play(bounceAnim1);
    bouncer1.start();
  }
  public void moveDown() {
    bob();
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
    cloud = (AnimationDrawable) getResources().getDrawable(R.drawable.floating_animation);
    cloud.start();
    ShapeDrawable drawable = new ShapeDrawable(circle);
    ShapeHolder shapeHolder = new ShapeHolder(drawable);
    shapeHolder.setX(x - diameter/2f);
    shapeHolder.setY(y - diameter);
    return shapeHolder;
  }
  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
//    canvas.save();
//    ValueAnimator a = ObjectAnimator.ofFloat(newBall, "y", newBall.getY(), newBall.getY()-100, newBall.getY()+100);
//    a.setDuration(2000);
//    a.setRepeatCount(ValueAnimator.INFINITE);
//    AnimatorSet bouncer1 = new AnimatorSet();
//    bouncer1.play(a);
//    bouncer1.start();
    canvas.translate(newBall.getX() - 150, newBall.getY());
    cloud.setBounds(0, 0 , 500, 350);
    cloud.draw(canvas);
//    Log.d("Bubble", "get Y: "+ newBall.getY());
//    newBall.getShape().draw(canvas);
//    canvas.restore();
  }
}