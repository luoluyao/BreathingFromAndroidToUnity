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
import com.unity3d.player.UnityPlayer;

import java.util.Arrays;

/**
 * Audio "FFT" analyzer.
 *
 * @author suhler@google.com (Stephen Uhler)
 */

public class AnalyzeActivity {
  static final String TAG = "AnalyzeActivity";
  boolean isBreathing;
  int breathCount = 0;
  boolean isInhaling = true;
  int thresholdAmpDB = -50;
  int timerCounter = 0;
  int lastMaxTime = 0;
  boolean isInhalingSure = false;

  private Context mContext;
  private static AnalyzeActivity instance;

  private Looper samplingThread;

  private final static double SAMPLE_VALUE_MAX = 32767.0;   // Maximum signal value
  private final static int RECORDER_AGC_OFF = MediaRecorder.AudioSource.VOICE_RECOGNITION;
  private final static int BYTE_OF_SAMPLE = 2;

  private static int fftLen = 1024;
  private static int sampleRate = 48000;
  private static int nFFTAverage = 1;
  private static String wndFuncName;

  double dtRMS = 0;
  double dtRMSFromFT = 0;
  double maxAmpDB;
  double maxAmpFreq;
  double dtRMSFromFT_Log;

  private static int audioSourceId = RECORDER_AGC_OFF;
  private boolean isAWeighting = false;

  private void startBreathing() {
    isBreathing = true;
  }

  private void stopBreathing() {
    isBreathing = false;
    if (timerCounter > 20) {
//      sendMessageIsBreathing();
//      if (!isInhaling) {
//        breathCount++;
//      }
//      else {
//      }
      isInhaling = !isInhaling;
      isInhalingSure = !isInhaling;
      Log.d("TIMER_ENDED", "timer: " + timerCounter);
      lastMaxTime = timerCounter;
    }
    timerCounter = 0;
  }


  public AnalyzeActivity(Context context) {
    this.instance = this;
    this.mContext = context;

    final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
    Log.i(TAG, " max mem = " + maxMemory + "k");

    getAudioSourceNameFromIdPrepare();

    audioSourceId = 0;
    wndFuncName = "Hanning";

    UnityPlayer.UnitySendMessage("Listener", "receiveAlpha", "starting sampling");
    samplingThread = new Looper();
    samplingThread.start();
  }

  public static AnalyzeActivity getInstance(Context context) {
    if(instance == null) {
      instance = new AnalyzeActivity(context);
    }
    return instance;
  }

  protected void destroy() {
    samplingThread.finish();
  }

  static String[] audioSourceNames = {"VOICE_RECOGNITION", "DEFAULT", "MIC", "VOICE_UPLINK",
      "VOICE_DOWNLINK", "VOICE_CALL", "CAMCORDER", "test signal 1\n\t 440Hz @ -6dB",
      "test signal 2\n\t 625Hz @ -6dB\n +1875Hz @ -12dB", "white noise"};
  static int[] audioSourceIDs;

  private void getAudioSourceNameFromIdPrepare() {
    String[] sasid = {"6", "0", "1", "2", "3", "4", "5", "1000", "1001", "1002"};
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

  }

  /**
   * Read a snapshot of audio data at a regular interval, and compute the FFT
   *
   * @author suhler@google.com
   * bewantbe@gmail.com
   */
  double[] spectrumDBcopy;   // XXX, transfers data from Looper to AnalyzeView

  public void sendMessageIsBreathing(String value){
//    UnityPlayer.UnitySendMessage("Listener", "receiveAlpha", ""+breathCount);
    UnityPlayer.UnitySendMessage("Listener", "receiveAlpha", value);
  }

  public class Looper extends Thread {
    AudioRecord record;
    volatile boolean isRunning = true;
    public STFT stft;   // use with care

    DoubleSineGen sineGen1;
    DoubleSineGen sineGen2;
    double[] mdata;

    public Looper() {
      // Signal sources for testing
      double fq0 = 440.0;
      double amp0 = Math.pow(10, 1 / 20.0 * -6.0);
      double fq1 = 625.0;
      double fq2 = 1875.0;
      double amp1 = Math.pow(10, 1 / 20.0 * -6.0);
      double amp2 = Math.pow(10, 1 / 20.0 * -12.0);
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
          sendMessageIsBreathing(""+dtRMSFromFT_Log);
          if (!isBreathing && dtRMSFromFT_Log > thresholdAmpDB) {
            startBreathing();
          }
          if (isBreathing) {
            timerCounter++;
//            if (isInhaling) {
//              progressBar.setProgress(timerCounter);
//            }
            if (timerCounter > 20) {
//              sendMessageIsBreathing();
//              if (isInhaling) {
//                if (!isInhalingSure) {
//                  moveUp();
////                  progressBar.setProgress(timerCounter);
//                }
//                isInhalingSure = true;
//                Log.d("Analyze", "moveUP");
//              } else {
//                Log.d("Analyze", "moveRight");
//              }
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
