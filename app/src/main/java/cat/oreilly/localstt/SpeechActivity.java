// Copyright 2020 Ciaran O'Reilly
// Copyright 2011-2020, Institute of Cybernetics at Tallinn University of Technology
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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.app.PendingIntent;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.os.Message;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;

public class SpeechActivity extends AppCompatActivity {
    protected static final String TAG = SpeechActivity.class.getSimpleName();

    private static final String MSG = "MSG";
    private static final int MSG_TOAST = 1;
    private static final int MSG_RESULT_ERROR = 2;
    public static final Integer RecordAudioRequestCode = 1;
    private SpeechRecognizer speechRecognizer;
    private EditText editText;

    protected static class SimpleMessageHandler extends Handler {
        private final WeakReference<SpeechActivity> mRef;

        private SimpleMessageHandler(SpeechActivity c) {
            mRef = new WeakReference<>(c);
        }

        public void handleMessage(Message msg) {
            SpeechActivity outerClass = mRef.get();
            if (outerClass != null) {
                Bundle b = msg.getData();
                String msgAsString = b.getString(MSG);
                switch (msg.what) {
                    case MSG_TOAST:
                        outerClass.toast(msgAsString);
                        break;
                    case MSG_RESULT_ERROR:
                        outerClass.showError(msgAsString);
                        break;
                    default:
                        break;
                }
            }
        }
    }

    protected static Message createMessage(int type, String str) {
        Bundle b = new Bundle();
        b.putString(MSG, str);
        Message msg = Message.obtain();
        msg.what = type;
        msg.setData(b);
        return msg;
    }

    protected void toast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }

    void showError(String msg) {
        editText.setText(msg);
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.speech_activity);
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            checkPermission();
        }

        editText = findViewById(R.id.text);
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle bundle) {

            }

            @Override
            public void onBeginningOfSpeech() {
                editText.setText("");
                editText.setHint(R.string.speaknow);
            }

            @Override
            public void onRmsChanged(float v) {

            }

            @Override
            public void onBufferReceived(byte[] bytes) {

            }

            @Override
            public void onEndOfSpeech() {
                speechRecognizer.stopListening();
            }

            @Override
            public void onError(int i) {
                showError();
            }

            @Override
            public void onResults(Bundle bundle) {
                Log.i(TAG, "onResults");
                ArrayList<String> results = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                Log.i(TAG, results.get(0));
                editText.setText(results.get(0));
                returnResults(results);
            }

            @Override
            public void onPartialResults(Bundle bundle) {
                Log.i(TAG, "onPartialResults");
                ArrayList<String> data = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                Log.i(TAG, data.get(0));
                editText.setText(data.get(0));
            }

            @Override
            public void onEvent(int i, Bundle bundle) {
                Log.d(TAG, bundle.toString());
            }
        });

    }

    @Override
    public void onStart() {
        super.onStart();
        Log.i(TAG, "onStart");
        final Intent speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        speechRecognizer.startListening(speechRecognizerIntent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy");
        speechRecognizer.destroy();
    }

    private void checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.RECORD_AUDIO },
                    RecordAudioRequestCode);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RecordAudioRequestCode && grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show();
        }
    }

    private void returnResults(ArrayList<String> results) {
        Handler handler = new SimpleMessageHandler(this);

        Intent incomingIntent = getIntent();
        Log.d(TAG, incomingIntent.toString());
        Bundle extras = incomingIntent.getExtras();
        if (extras == null) {
            return;
        }
        Log.d(TAG, extras.toString());
        PendingIntent pendingIntent = getPendingIntent(extras);
        if (pendingIntent == null) {
            Log.d(TAG, "No pending intent, setting result intent.");
            setResultIntent(handler, results);
        } else {
            Log.d(TAG, pendingIntent.toString());

            Bundle bundle = extras.getBundle(RecognizerIntent.EXTRA_RESULTS_PENDINGINTENT_BUNDLE);
            if (bundle == null) {
                bundle = new Bundle();
            }

            Intent intent = new Intent();
            intent.putExtras(bundle);
            handler.sendMessage(
                    createMessage(MSG_TOAST, String.format(getString(R.string.recognized), results.get(0))));
            try {
                Log.d(TAG, "Sending result via pendingIntent");
                pendingIntent.send(this, AppCompatActivity.RESULT_OK, intent);
            } catch (PendingIntent.CanceledException e) {
                Log.e(TAG, e.getMessage());
                handler.sendMessage(createMessage(MSG_TOAST, e.getMessage()));
            }
        }

        finish();
    }

    private void showError() {
        toast("Error loading recognizer");
    }
    
    private void setResultIntent(final Handler handler, List<String> matches) {
        Intent intent = new Intent();
        intent.putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, new ArrayList<>(matches));
        setResult(Activity.RESULT_OK, intent);
    }

    private PendingIntent getPendingIntent(Bundle extras) {
        Parcelable extraResultsPendingIntentAsParceable = extras
                .getParcelable(RecognizerIntent.EXTRA_RESULTS_PENDINGINTENT);
        if (extraResultsPendingIntentAsParceable != null) {
            // PendingIntent.readPendingIntentOrNullFromParcel(mExtraResultsPendingIntent);
            if (extraResultsPendingIntentAsParceable instanceof PendingIntent) {
                return (PendingIntent) extraResultsPendingIntentAsParceable;
            }
        }
        return null;
    }
}
