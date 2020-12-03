// Copyright 2020 Ciaran O'Reilly
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.

// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.

// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.
package cat.oreilly.localstt;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionService;
import android.util.Log;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import org.kaldi.Assets;
import org.kaldi.RecognitionListener;
import org.mozilla.deepspeech.libdeepspeech.DeepSpeechModel;

import java.io.File;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.io.IOException;

public class DeepSpeechRecognitionService extends RecognitionService implements RecognitionListener {
    private final static String TAG = DeepSpeechRecognitionService.class.getSimpleName();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Executor executor = Executors.newSingleThreadExecutor();
    private DeepSpeechModel model;
    private DeepSpeechService speechService;

    private RecognitionService.Callback mCallback;

    @Override
    protected void onStartListening(Intent intent, Callback callback) {
        mCallback = callback;
        Log.i(TAG, "onStartListening");
        runRecognizerSetup();
    }

    @Override
    protected void onCancel(Callback callback) {
        Log.i(TAG, "onCancel");
        results(new Bundle(), true);
    }

    @Override
    protected void onStopListening(Callback callback) {
        Log.i(TAG, "onStopListening");
        results(new Bundle(), true);
    }

    private void runRecognizerSetup() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Assets assets = new Assets(DeepSpeechRecognitionService.this);
                    File assetDir = assets.syncAssets();

                    model = new DeepSpeechModel(assetDir.toString() + "/deepspeech-catala/model.tflite");
                    model.enableExternalScorer(assetDir.toString() + "/deepspeech-catala/kenlm.scorer");

                    setupRecognizer();
                } catch (Exception e) {
                    Log.e(TAG, "Failed to init recognizer ");
                    error(android.speech.SpeechRecognizer.ERROR_CLIENT);
                }

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        readyForSpeech(new Bundle());
                        beginningOfSpeech();
                    }
                });
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (speechService != null) {
            speechService.cancel();
            speechService.shutdown();
        }
    }

    private void setupRecognizer() throws IOException {
        try {
            Log.i(TAG, "Setting up recognizer");
            DeepSpeechService speechService = new DeepSpeechService(this.model, 16000.0f);
            speechService.addListener(this);
            speechService.startListening();
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    private void readyForSpeech(Bundle bundle) {
        try {
            mCallback.readyForSpeech(bundle);
        } catch (RemoteException e) {
            // empty
        }
    }

    private void results(Bundle bundle, boolean isFinal) {
        if (speechService != null) {
            speechService.cancel();
        }
        try {
            if (isFinal) {
                mCallback.results(bundle);
            } else {
                mCallback.partialResults(bundle);
            }
        } catch (RemoteException e) {
            // empty
        }
    }

    private Bundle createResultsBundle(String hypothesis) {
        ArrayList<String> hypotheses = new ArrayList<>();
        hypotheses.add(hypothesis);
        Bundle bundle = new Bundle();
        bundle.putStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION, hypotheses);
        return bundle;
    }

    private void beginningOfSpeech() {
        try {
            mCallback.beginningOfSpeech();
        } catch (RemoteException e) {
            // empty
        }
    }

    private void error(int errorCode) {
        speechService.cancel();
        try {
            mCallback.error(errorCode);
        } catch (RemoteException e) {
            // empty
        }
    }

    @Override
    public void onResult(String hypothesis) {
        if (hypothesis != null) {
            Log.i(TAG, hypothesis);
            results(createResultsBundle(hypothesis), true);
        }
    }

    @Override
    public void onPartialResult(String hypothesis) {
        if (hypothesis != null) {
            Log.i(TAG, hypothesis);
            results(createResultsBundle(hypothesis), false);
        }
    }

    @Override
    public void onError(Exception e) {
        Log.e(TAG, e.getMessage());
        error(android.speech.SpeechRecognizer.ERROR_CLIENT);
    }

    @Override
    public void onTimeout() {
        speechService.cancel();
        speechService.startListening();
    }
}
