package cat.oreilly.localstt;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.speech.RecognitionService;
import android.util.Log;

import com.google.gson.Gson;
import org.kaldi.Assets;
import org.kaldi.KaldiRecognizer;
import org.kaldi.Model;
import org.kaldi.RecognitionListener;
import org.kaldi.SpeechService;
import org.kaldi.Vosk;

import java.io.File;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.io.IOException;

public class VoskRecognitionService extends RecognitionService implements RecognitionListener {
    private final static String TAG = VoskRecognitionService.class.getSimpleName();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Executor executor = Executors.newSingleThreadExecutor();
    private KaldiRecognizer recognizer;
    private SpeechService speechService;
    private Model model;

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
                    if (model == null) {
                        Assets assets = new Assets(VoskRecognitionService.this);
                        File assetDir = assets.syncAssets();
                        Vosk.SetLogLevel(0);

                        Log.i(TAG, "Loading model");
                        model = new Model(assetDir.toString() + "/vosk-catala");
                    }

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
            if (recognizer == null) {
                Log.i(TAG, "Creating recognizer");

                recognizer = new KaldiRecognizer(model, 16000.0f);
            }

            if (speechService == null) {
                Log.i(TAG, "Creating speechService");

                speechService = new SpeechService(recognizer, 16000.0f);
                speechService.addListener(this);
            } else {
                speechService.cancel();
            }
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
        try {
            if (isFinal) {
                speechService.cancel();
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
            Gson gson = new Gson();
            Map<String, String> map = gson.fromJson(hypothesis, Map.class);
            String text = map.get("text");
            results(createResultsBundle(text), true);
        }
    }

    @Override
    public void onPartialResult(String hypothesis) {
        if (hypothesis != null) {
            Log.i(TAG, hypothesis);
            Gson gson = new Gson();
            Map<String, String> map = gson.fromJson(hypothesis, Map.class);
            String text = map.get("partial");
            results(createResultsBundle(text), false);
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
