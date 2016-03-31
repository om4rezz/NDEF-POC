package droidex.om4rezz.ndefpoc;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Picture;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.util.Arrays;

public class MainActivity extends Activity {
    public static final String MIME_TEXT_PLAIN = "text/plain";
    public static final String TAG = "NfcDemo";

    // NEW
    ImageView iv1;
    ImageView iv2;
    TextView tvMatched;
    TextView tvScore;

    int score = 0;

    // END NEW


    //    private TextView mTextView;
    private NfcAdapter mNfcAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        initViews();

        mNfcAdapter = NfcAdapter.getDefaultAdapter(MainActivity.this);

        if (mNfcAdapter == null) {
            // Stop here, we definitely need NFC
            Toast.makeText(this, "This device doesn't support NFC.", Toast.LENGTH_LONG).show();
            finish();
            return;

        }

        if (!mNfcAdapter.isEnabled()) {
//            mTextView.setText("NFC is disabled.");
            Toast.makeText(this, "NFC is disabled.", Toast.LENGTH_LONG).show();

        } else {
//            mTextView.setText(R.string.explanation);
            Toast.makeText(this, "NFC goes perfect.!", Toast.LENGTH_LONG).show();
        }

        initUI();

        handleIntent(getIntent());
    }

    private void initUI() {
        tvMatched.setVisibility(View.INVISIBLE);
        tvScore.setText("Score: " + score);
    }

    private void initViews() {
        tvMatched = (TextView) findViewById(R.id.tv_matched);
        tvScore = (TextView)findViewById(R.id.tv_score);
        iv1 = (ImageView) findViewById(R.id.imageView1);
        iv2 = (ImageView) findViewById(R.id.imageView2);
    }

    @Override
    protected void onResume() {
        super.onResume();

        /**
         * It's important, that the activity is in the foreground (resumed). Otherwise
         * an IllegalStateException is thrown.
         */
        setupForegroundDispatch(MainActivity.this, mNfcAdapter);
    }

    @Override
    protected void onPause() {
        /**
         * Call this before onPause, otherwise an IllegalArgumentException is thrown as well.
         */
        stopForegroundDispatch(MainActivity.this, mNfcAdapter);

        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        /**
         * This method gets called, when a new Intent gets associated with the current activity instance.
         * Instead of creating a new activity, onNewIntent will be called. For more information have a look
         * at the documentation.
         *
         * In our case this method gets called, when the user attaches a Tag to the device.
         */
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        String action = intent.getAction();
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {

            String type = intent.getType();
            if (MIME_TEXT_PLAIN.equals(type)) {

                Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
                new NdefReaderTask().execute(tag);

            } else {
                Log.d(TAG, "Wrong mime type: " + type);
            }
        } else if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {

            // In case we would still use the Tech Discovered Intent
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            String[] techList = tag.getTechList();
            String searchedTech = Ndef.class.getName();

            for (String tech : techList) {
                if (searchedTech.equals(tech)) {
                    new NdefReaderTask().execute(tag);
                    break;
                }
            }
        }
    }

    /**
     * @param activity The corresponding {@link Activity} requesting the foreground dispatch.
     * @param adapter  The {@link NfcAdapter} used for the foreground dispatch.
     */
    public static void setupForegroundDispatch(final Activity activity, NfcAdapter adapter) {
        final Intent intent = new Intent(activity.getApplicationContext(), activity.getClass());
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        final PendingIntent pendingIntent = PendingIntent.getActivity(activity.getApplicationContext(), 0, intent, 0);

        IntentFilter[] filters = new IntentFilter[1];
        String[][] techList = new String[][]{};

        // Notice that this is the same filter as in our manifest.
        filters[0] = new IntentFilter();
        filters[0].addAction(NfcAdapter.ACTION_NDEF_DISCOVERED);
        filters[0].addCategory(Intent.CATEGORY_DEFAULT);
        try {
            filters[0].addDataType(MIME_TEXT_PLAIN);
        } catch (IntentFilter.MalformedMimeTypeException e) {
            throw new RuntimeException("Check your mime type.");
        }

        adapter.enableForegroundDispatch(activity, pendingIntent, filters, techList);
    }

    /**
     * @param activity The corresponding {@link BaseActivity} requesting to stop the foreground dispatch.
     * @param adapter  The {@link NfcAdapter} used for the foreground dispatch.
     */
    public static void stopForegroundDispatch(final Activity activity, NfcAdapter adapter) {
        adapter.disableForegroundDispatch(activity);
    }

    private class NdefReaderTask extends AsyncTask<Tag, Void, String> {

        @Override
        protected String doInBackground(Tag... params) {
            Tag tag = params[0];

            Ndef ndef = Ndef.get(tag);
            if (ndef == null) {
                // NDEF is not supported by this Tag.
                return null;
            }

            NdefMessage ndefMessage = ndef.getCachedNdefMessage();

            NdefRecord[] records = ndefMessage.getRecords();
            for (NdefRecord ndefRecord : records) {
                if (ndefRecord.getTnf() == NdefRecord.TNF_WELL_KNOWN && Arrays.equals(ndefRecord.getType(), NdefRecord.RTD_TEXT)) {
                    try {
                        return readText(ndefRecord);
                    } catch (UnsupportedEncodingException e) {
                        Log.e(TAG, "Unsupported Encoding", e);
                    }
                }
            }

            return null;
        }

        private String readText(NdefRecord record) throws UnsupportedEncodingException {
        /*
         * See NFC forum specification for "Text Record Type Definition" at 3.2.1
         *
         * http://www.nfc-forum.org/specs/
         *
         * bit_7 defines encoding
         * bit_6 reserved for future use, must be 0
         * bit_5..0 length of IANA language code
         */

            byte[] payload = record.getPayload();

            // Get the Text Encoding
            String textEncoding = ((payload[0] & 128) == 0) ? "UTF-8" : "UTF-16";

            // Get the Language Code
            int languageCodeLength = payload[0] & 0063;

            // String languageCode = new String(payload, 1, languageCodeLength, "US-ASCII");
            // e.g. "en"

            // Get the Text
            return new String(payload, languageCodeLength + 1, payload.length - languageCodeLength - 1, textEncoding);
        }

        public void checkMatching() {
            if (iv1.getTag().equals(iv2.getTag())) {
                tvMatched.setText("Well done :)");
                Toast.makeText(getApplicationContext(), "You got one point", Toast.LENGTH_LONG).show();

                tvScore.post(new Runnable() {
                    @Override
                    public void run() {
                        score++;
                        tvScore.setText("Score: " + score);
                    }
                });

                sendScore();
            } else {
                tvMatched.setText("Hard luck :(");
            }
        }

        public void clearData() {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        tvMatched.post(new Runnable() {
                            @Override
                            public void run() {
                                tvMatched.setVisibility(View.VISIBLE);
                            }
                        });
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    iv1.post(new Runnable() {
                        @Override
                        public void run() {
                            iv1.setImageDrawable(null);
                        }
                    });
                    iv2.post(new Runnable() {
                        @Override
                        public void run() {
                            iv2.setImageDrawable(null);
                        }
                    });
                    tvMatched.post(new Runnable() {
                        @Override
                        public void run() {
                            tvMatched.setVisibility(View.INVISIBLE);
                        }
                    });
                }
            }).start();

        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                Toast.makeText(getApplicationContext(), result, Toast.LENGTH_LONG).show();
//                mTextView.setText("Read content: " + result);
                if (result.equals("11")) {
                    if (iv1.getDrawable() == null) {
                        iv1.setImageResource(R.drawable.card11);
                        iv1.setTag(R.drawable.card11);
                    } else {
                        iv2.setImageResource(R.drawable.card11);
                        iv2.setTag(R.drawable.card11);
                        checkMatching();
                        clearData();
                    }
                } else if (result.equals("22")) {
                    if (iv1.getDrawable() == null) {
                        iv1.setImageResource(R.drawable.card22);
                        iv1.setTag(R.drawable.card22);
                    } else {
                        iv2.setImageResource(R.drawable.card22);
                        iv2.setTag(R.drawable.card22);
                        checkMatching();
                        clearData();
                    }
                } else if (result.equals("33")) {
                    if (iv1.getDrawable() == null) {
                        iv1.setImageResource(R.drawable.card33);
                        iv1.setTag(R.drawable.card33);
                    } else {
                        iv2.setImageResource(R.drawable.card33);
                        iv2.setTag(R.drawable.card33);
                        checkMatching();
                        clearData();
                    }
                } else if (result.equals("55")) {
                    if (iv1.getDrawable() == null) {
                        iv1.setImageResource(R.drawable.card11);
                        iv1.setTag(R.drawable.card11);
                    } else {
                        iv2.setImageResource(R.drawable.card11);
                        iv2.setTag(R.drawable.card11);
                        checkMatching();
                        clearData();
                    }
                }

            }
        }
    }

    private void sendScore() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Socket socket=null;
                try {
                     socket = new Socket("10.9.52.102", 7001);
                    DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                    dos.writeUTF("add point");
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
}