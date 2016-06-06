/* Copyright 2011 Google Inc.
 *
 *Licensed under the Apache License, Version 2.0 (the "License");
 *you may not use this file except in compliance with the License.
 *You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *Unless required by applicable law or agreed to in writing, software
 *distributed under the License is distributed on an "AS IS" BASIS,
 *WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *See the License for the specific language governing permissions and
 *limitations under the License.
 *
 * @author Stephen Uhler
 *
 * 2014 Eddy Xiao <bewantbe@gmail.com>
 * GUI extensively modified.
 * Add some naive auto refresh rate control logic.
 */

package com.mina.breathitout;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ViewFlipper;

import com.mina.breathitout.math.DoubleSineGen;
import com.mina.breathitout.math.STFT;

import java.util.Arrays;

/**
 * Audio "FFT" analyzer.
 *
 * @author suhler@google.com (Stephen Uhler)
 */

public class AnalyzeActivity extends Activity {
  static final String TAG = "AnalyzeActivity";
  boolean isBreathing;
  int breathCount = 0;
  boolean isInhaling = true;
  int thresholdAmpDB = -50;
  int timerCounter = 0;
  int lastMaxTime = 0;
  boolean isInhalingSure = false;

  private ViewFlipper mViewFlipper;
  private Animation.AnimationListener mAnimationListener;
  private Context mContext;
  ProgressBar progressBar;

  private Looper samplingThread;

  private final static double SAMPLE_VALUE_MAX = 32767.0;   // Maximum signal value
  private final static int RECORDER_AGC_OFF = MediaRecorder.AudioSource.VOICE_RECOGNITION;
  private final static int BYTE_OF_SAMPLE = 2;

  private static int fftLen = 1024;
  private static int sampleRate = 48000;
  private static int nFFTAverage = 1;
  private static String wndFuncName;

  TextView txtView;
  TextView breathOutView;

  private static int audioSourceId = RECORDER_AGC_OFF;
  private boolean isAWeighting = false;
  private BubbleView view;

  double dtRMS = 0;
  double dtRMSFromFT = 0;
  double maxAmpDB;
  double maxAmpFreq;
  double dtRMSFromFT_Log;

  private void startBreathing() {
    isBreathing = true;
  }

  private void stopBreathing() {
    isBreathing = false;
    if (timerCounter > 40) {
      if (!isInhaling) {
        breathCount++;
        moveRight();
        if (breathCount == 7) {
          view.reset();
        }
      }
      else {
        animateProgressBar();
      }
      isInhaling = !isInhaling;
      isInhalingSure = !isInhaling;
      Log.d("TIMER_ENDED", "timer: " + timerCounter);
      lastMaxTime = timerCounter;
    }
    timerCounter = 0;
  }



  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);
    requestMicPermission();
    view = new BubbleView(this);
    LinearLayout container = (LinearLayout) findViewById(R.id.container);
    container.addView(view);

    final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
    Log.i(TAG, " max mem = " + maxMemory + "k");

    Resources res = getResources();
    getAudioSourceNameFromIdPrepare(res);

    progressBar = (ProgressBar) findViewById(R.id.progress_bar);

    mContext = this;
    mViewFlipper = (ViewFlipper) this.findViewById(R.id.view_flipper);

    txtView = (TextView) this.findViewById(R.id.start_breathing_text);
    fadeInText();
    breathOutView = (TextView) this.findViewById(R.id.start_breathing__out_text);

    //animation listener
    mAnimationListener = new Animation.AnimationListener() {
      public void onAnimationStart(Animation animation) {
        //animation started event
      }

      public void onAnimationRepeat(Animation animation) {
      }

      public void onAnimationEnd(Animation animation) {
        //TODO animation stopped event
        moveDown();
        fadeInText();
      }
    };
  }

  public void fadeInText() {
    AlphaAnimation fadeIn = new AlphaAnimation(0.0f , 1.0f ) ;
    AlphaAnimation fadeOut = new AlphaAnimation( 1.0f , 0.0f ) ;
    txtView.startAnimation(fadeIn);
    txtView.startAnimation(fadeOut);
    fadeIn.setDuration(4000);
    fadeIn.setFillAfter(true);
    fadeOut.setDuration(4000);
    fadeOut.setFillAfter(true);
    fadeIn.setStartOffset(4200);
  }

  public void animateProgressBar() {
    AnalyzeActivity.this.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if(android.os.Build.VERSION.SDK_INT >= 11){
          // will update the "progress" propriety of seekbar until it reaches progress
          ObjectAnimator progressAnimation = ObjectAnimator.ofInt(progressBar, "progress", progressBar.getProgress(), lastMaxTime);
          progressAnimation.setDuration(1000); // 0.5 second
          progressAnimation.setInterpolator(new DecelerateInterpolator());
          progressAnimation.start();
        }
        else
          progressBar.setProgress(timerCounter); // no animation on Gingerbread or lower
      }
    });
  }

  /**
   * Run processClick() for views, transferring the state in the textView to our
   * internal state, then begin sampling and processing audio data
   */

  @Override
  protected void onResume() {
    super.onResume();

    // load preferences
    SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
    boolean keepScreenOn = sharedPref.getBoolean("keepScreenOn", true);
    if (keepScreenOn) {
      getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    } else {
      getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    audioSourceId = Integer.parseInt(sharedPref.getString("audioSource", Integer.toString(RECORDER_AGC_OFF)));
    wndFuncName = sharedPref.getString("windowFunction", "Hanning");

    samplingThread = new Looper();
    samplingThread.start();
  }

  @Override
  protected void onPause() {
    super.onPause();
    samplingThread.finish();
    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
  }

  @Override
  public void onSaveInstanceState(Bundle savedInstanceState) {
    savedInstanceState.putDouble("dtRMS", dtRMS);
    savedInstanceState.putDouble("dtRMSFromFT", dtRMSFromFT);
    savedInstanceState.putDouble("maxAmpDB", maxAmpDB);
    savedInstanceState.putDouble("maxAmpFreq", maxAmpFreq);
    savedInstanceState.putDouble("dtRMSFromFT_Log", dtRMSFromFT_Log);
    savedInstanceState.putInt("breathCount", breathCount);
    savedInstanceState.putBoolean("isBreathing", isBreathing);
    savedInstanceState.putBoolean("isInhaling", isInhaling);
    savedInstanceState.putInt("lastMaxTime", lastMaxTime);

    super.onSaveInstanceState(savedInstanceState);
  }

  @Override
  public void onRestoreInstanceState(Bundle savedInstanceState) {
    // will be calls after the onStart()
    super.onRestoreInstanceState(savedInstanceState);

    dtRMS = savedInstanceState.getDouble("dtRMS");
    dtRMSFromFT = savedInstanceState.getDouble("dtRMSFromFT");
    dtRMSFromFT_Log = savedInstanceState.getDouble("dtRMSFromFT_Log");
    maxAmpDB = savedInstanceState.getDouble("maxAmpDB");
    maxAmpFreq = savedInstanceState.getDouble("maxAmpFreq");
    breathCount = savedInstanceState.getInt("breathCount");
    isBreathing = savedInstanceState.getBoolean("isBreathing");
    isInhaling = savedInstanceState.getBoolean("isInhaling");
    lastMaxTime = savedInstanceState.getInt("lastMaxTime");
  }

  /**
   * Read a snapshot of audio data at a regular interval, and compute the FFT
   *
   * @author suhler@google.com
   */

  private void requestMicPermission() {
    // Here, thisActivity is the current activity
    if (ContextCompat.checkSelfPermission(this,
        Manifest.permission.RECORD_AUDIO)
        != PackageManager.PERMISSION_GRANTED) {

      // Should we show an explanation?
      if (ActivityCompat.shouldShowRequestPermissionRationale(this,
          Manifest.permission.RECORD_AUDIO)) {

        // Show an expanation to the user *asynchronously* -- don't block
        // this thread waiting for the user's response! After the user
        // sees the explanation, try again to request the permission.

      } else {

        // No explanation needed, we can request the permission.

        ActivityCompat.requestPermissions(this,
            new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE},
            0);

        // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
        // app-defined int constant. The callback method gets the
        // result of the request.
      }
    }
  }

  static String[] audioSourceNames;
  static int[] audioSourceIDs;

  private void getAudioSourceNameFromIdPrepare(Resources res) {
    audioSourceNames = res.getStringArray(R.array.audio_source);
    String[] sasid = res.getStringArray(R.array.audio_source_id);
    audioSourceIDs = new int[audioSourceNames.length];
    for (int i = 0; i < audioSourceNames.length; i++) {
      audioSourceIDs[i] = Integer.parseInt(sasid[i]);
    }
  }

  // Get audio source name from its ID
  // Tell me if there is better way to do it.
  private static String getAudioSourceNameFromId(int id) {
    for (int i = 0; i < audioSourceNames.length; i++) {
      if (audioSourceIDs[i] == id) {
        return audioSourceNames[i];
      }
    }
    Log.e(TAG, "getAudioSourceName(): no this entry.");
    return "";
  }

  private void update() {
    AnalyzeActivity.this.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        refreshBreaths();
        refreshRMS();
        view.invalidate();
      }
    });
  }

  private void moveUp() {
    AnalyzeActivity.this.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        view.moveUP();
        breathOutView.setVisibility(View.VISIBLE);
        AlphaAnimation fadeIn_breathout = new AlphaAnimation(0.0f , 1.0f ) ;
        fadeIn_breathout.setDuration(2000);
        fadeIn_breathout.setAnimationListener(new Animation.AnimationListener() {
          @Override
          public void onAnimationStart(Animation animation) {

          }

          @Override
          public void onAnimationEnd(Animation animation) {
            AlphaAnimation fadeOut_breathout = new AlphaAnimation( 1.0f , 0.0f ) ;
            breathOutView.startAnimation(fadeOut_breathout);
            fadeOut_breathout.setDuration(2000);
            breathOutView.setVisibility(View.INVISIBLE);
          }

          @Override
          public void onAnimationRepeat(Animation animation) {

          }
        });

        breathOutView.startAnimation(fadeIn_breathout);
        fadeIn_breathout.setFillAfter(true);
        animateProgressBar();
      }
    });
  }

  private void moveRight() {
    AnalyzeActivity.this.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        mViewFlipper.setInAnimation(AnimationUtils.loadAnimation(mContext, R.anim.left_in));
        mViewFlipper.setOutAnimation(AnimationUtils.loadAnimation(mContext,R.anim.left_out));
        // controlling animation
        mViewFlipper.getInAnimation().setAnimationListener(mAnimationListener);
        if(android.os.Build.VERSION.SDK_INT >= 11){
          // will update the "progress" propriety of seekbar until it reaches progress
          ObjectAnimator progressAnimation = ObjectAnimator.ofInt(progressBar, "progress", lastMaxTime, 0);
          progressAnimation.setDuration(1000); // 0.5 second
          progressAnimation.setInterpolator(new DecelerateInterpolator());
          progressAnimation.start();
        }
        else
          progressBar.setProgress(0); // no animation on Gingerbread or lower
        mViewFlipper.showNext();
      }
    });
  }

  private void moveDown() {
    AnalyzeActivity.this.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        view.moveDown();
      }
    });
  }

  private void refresh() {
    samplingThread.finish();
    try {
      samplingThread.join();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    samplingThread = new Looper();
    samplingThread.start();
  }

  private void refreshBreaths() {
//    TextView tv = (TextView) findViewById(R.id.textview_breath);
//    tv.setText("B: " + breathCount + " " + isInhaling + " " + lastMaxTime);
  }

  private void refreshRMS() {
//    TextView tv = (TextView) findViewById(R.id.textview_RMS);
//    tv.setText("RMS: " + dtRMSFromFT_Log);
  }
  
  /**
   * Read a snapshot of audio data at a regular interval, and compute the FFT
   *
   * @author suhler@google.com
   * bewantbe@gmail.com
   */
  double[] spectrumDBcopy;   // XXX, transfers data from Looper to AnalyzeView

  public class Looper extends Thread {
    AudioRecord record;
    volatile boolean isRunning = true;
    public STFT stft;   // use with care

    DoubleSineGen sineGen1;
    DoubleSineGen sineGen2;
    double[] mdata;

    public Looper() {
      // Signal sources for testing
      double fq0 = Double.parseDouble(getString(R.string.test_signal_1_freq1));
      double amp0 = Math.pow(10, 1 / 20.0 * Double.parseDouble(getString(R.string.test_signal_1_db1)));
      double fq1 = Double.parseDouble(getString(R.string.test_signal_2_freq1));
      double fq2 = Double.parseDouble(getString(R.string.test_signal_2_freq2));
      double amp1 = Math.pow(10, 1 / 20.0 * Double.parseDouble(getString(R.string.test_signal_2_db1)));
      double amp2 = Math.pow(10, 1 / 20.0 * Double.parseDouble(getString(R.string.test_signal_2_db2)));
      if (audioSourceId == 1000) {
        sineGen1 = new DoubleSineGen(fq0, sampleRate, SAMPLE_VALUE_MAX * amp0);
      } else {
        sineGen1 = new DoubleSineGen(fq1, sampleRate, SAMPLE_VALUE_MAX * amp1);
      }
      sineGen2 = new DoubleSineGen(fq2, sampleRate, SAMPLE_VALUE_MAX * amp2);
    }

    private void SleepWithoutInterrupt(long millis) {
      try {
        Thread.sleep(millis);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }

    // generate test data
    private int readTestData(short[] a, int offsetInShorts, int sizeInShorts, int id) {
      if (mdata == null || mdata.length != sizeInShorts) {
        mdata = new double[sizeInShorts];
      }
      Arrays.fill(mdata, 0.0);
      switch (id - 1000) {
        case 1:
          sineGen2.getSamples(mdata);
        case 0:
          sineGen1.addSamples(mdata);
          for (int i = 0; i < sizeInShorts; i++) {
            a[offsetInShorts + i] = (short) Math.round(mdata[i]);
          }
          break;
        case 2:
          for (int i = 0; i < sizeInShorts; i++) {
            a[i] = (short) (SAMPLE_VALUE_MAX * (2.0 * Math.random() - 1));
          }
          break;
        default:
          Log.w(TAG, "readTestData(): No this source id = " + audioSourceId);
      }
      return sizeInShorts;
    }

    @Override
    public void run() {
      // Wait until previous instance of AudioRecord fully released.
      SleepWithoutInterrupt(500);

      int minBytes = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO,
          AudioFormat.ENCODING_PCM_16BIT);
      if (minBytes == AudioRecord.ERROR_BAD_VALUE) {
        Log.e(TAG, "Looper::run(): Invalid AudioRecord parameter.\n");
        return;
      }

      /**
       * Develop -> Reference -> AudioRecord
       *    Data should be read from the audio hardware in chunks of sizes
       *    inferior to the total recording buffer size.
       */
      // Determine size of buffers for AudioRecord and AudioRecord::read()
      int readChunkSize = fftLen / 2;  // /2 due to overlapped analyze window
      readChunkSize = Math.min(readChunkSize, 2048);  // read in a smaller chunk, hopefully smaller delay
      int bufferSampleSize = Math.max(minBytes / BYTE_OF_SAMPLE, fftLen / 2) * 2;
      // tolerate up to about 1 sec.
      bufferSampleSize = (int) Math.ceil(1.0 * sampleRate / bufferSampleSize) * bufferSampleSize;

      // Use the mic with AGC turned off. e.g. VOICE_RECOGNITION
      // The buffer size here seems not relate to the delay.
      // So choose a larger size (~1sec) so that overrun is unlikely.
      if (audioSourceId < 1000) {
        record = new AudioRecord(audioSourceId, sampleRate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, BYTE_OF_SAMPLE * bufferSampleSize);
      } else {
        record = new AudioRecord(RECORDER_AGC_OFF, sampleRate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, BYTE_OF_SAMPLE * bufferSampleSize);
      }
      Log.i(TAG, "Looper::Run(): Starting recorder... \n" +
          "  source          : " + (audioSourceId < 1000 ? getAudioSourceNameFromId(audioSourceId) : audioSourceId) + "\n" +
          String.format("  sample rate     : %d Hz (request %d Hz)\n", record.getSampleRate(), sampleRate) +
          String.format("  min buffer size : %d samples, %d Bytes\n", minBytes / BYTE_OF_SAMPLE, minBytes) +
          String.format("  buffer size     : %d samples, %d Bytes\n", bufferSampleSize, BYTE_OF_SAMPLE * bufferSampleSize) +
          String.format("  read chunk size : %d samples, %d Bytes\n", readChunkSize, BYTE_OF_SAMPLE * readChunkSize) +
          String.format("  FFT length      : %d\n", fftLen) +
          String.format("  nFFTAverage     : %d\n", nFFTAverage));
      sampleRate = record.getSampleRate();

      if (record.getState() == AudioRecord.STATE_UNINITIALIZED) {
        Log.e(TAG, "Looper::run(): Fail to initialize AudioRecord()");
        // If failed somehow, leave user a chance to change preference.
        return;
      }

      short[] audioSamples = new short[readChunkSize];
      int numOfReadShort;

      stft = new STFT(fftLen, sampleRate, wndFuncName);
      stft.setAWeighting(isAWeighting);
      if (spectrumDBcopy == null || spectrumDBcopy.length != fftLen / 2 + 1) {
        spectrumDBcopy = new double[fftLen / 2 + 1];
      }

      // Start recording
      record.startRecording();

      // Main loop
      // When running in this loop (including when paused), you can not change properties
      // related to recorder: e.g. audioSourceId, sampleRate, bufferSampleSize
      // TODO: allow change of FFT length on the fly.
      while (isRunning) {
        // Read data
        if (audioSourceId >= 1000) {
          numOfReadShort = readTestData(audioSamples, 0, readChunkSize, audioSourceId);
        } else {
          numOfReadShort = record.read(audioSamples, 0, readChunkSize);   // pulling
        }

        stft.feedData(audioSamples, numOfReadShort);

        // If there is new spectrum data, do plot
        if (stft.nElemSpectrumAmp() >= nFFTAverage) {
          // Update spectrum or spectrogram
          final double[] spectrumDB = stft.getSpectrumAmpDB();
          System.arraycopy(spectrumDB, 0, spectrumDBcopy, 0, spectrumDB.length);

          stft.calculatePeak();
          maxAmpFreq = stft.maxAmpFreq;
          maxAmpDB = stft.maxAmpDB;

          // get RMS
          dtRMS = stft.getRMS();
          dtRMSFromFT = stft.getRMSFromFT();
          dtRMSFromFT_Log = 20 * Math.log10(dtRMSFromFT);
          if (!isBreathing && dtRMSFromFT_Log > thresholdAmpDB) {
            startBreathing();
          }
          if (isBreathing) {
            timerCounter++;
//            if (isInhaling) {
//              progressBar.setProgress(timerCounter);
//            }
            if (timerCounter > 40) {
              if (isInhaling) {
                if (!isInhalingSure) {
                  moveUp();
//                  progressBar.setProgress(timerCounter);
                }
                isInhalingSure = true;
                Log.d("Analyze", "moveUP");
              } else {
                Log.d("Analyze", "moveRight");
              }
            }
          }
          if (isBreathing && dtRMSFromFT_Log < thresholdAmpDB) {
            stopBreathing();
          }
          update();
        }
      }
      Log.i(TAG, "Looper::Run(): Stopping and releasing recorder.");
      record.stop();
      record.release();
      record = null;
    }

    public void finish() {
      isRunning = false;
      interrupt();
    }
  }
}
