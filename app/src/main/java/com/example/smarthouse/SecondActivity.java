package com.example.smarthouse;

import android.bluetooth.BluetoothSocket;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class SecondActivity extends AppCompatActivity {

    public static final String TAG = "SmartHouseBT";
    private Handler apiHandler;
    private Runnable runnableCode;
    private RequestQueue queue;
    private int houseId = 24; // ID par défaut
    private String baseUrl = "http://happyresto.enseeiht.fr/smartHouse/api/v1/devices/";

    // Bluetooth
    private static ConnectedThread bluetoothThread;
    private Handler bluetoothHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second);

        apiHandler = new Handler(Looper.getMainLooper());
        
        // Initialisation du Handler pour recevoir les messages Bluetooth
        bluetoothHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == 1) { // Message reçu
                    String readMessage = (String) msg.obj;
                    handleBluetoothMessage(readMessage);
                }
            }
        };

        // Lancement du thread de communication Bluetooth
        if (MainActivity.bluetoothSocket != null) {
            bluetoothThread = new ConnectedThread(MainActivity.bluetoothSocket);
            bluetoothThread.start();
        }

        Button backButton = findViewById(R.id.back_bt);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        if (MainActivity.isServer) {
            queue = Volley.newRequestQueue(this);
            runnableCode = new Runnable() {
                @Override
                public void run() {
                    refreshData();
                    apiHandler.postDelayed(this, 10000);
                }
            };
        } else {
            // Le client n'a pas besoin de rafraîchir l'API, il attend le Bluetooth
            TextView titleText = findViewById(R.id.header).findViewById(new View(this).generateViewId()); // Placeholder
            // On peut changer le titre pour indiquer le mode
            Log.d(TAG, "Mode Client activé");
        }
    }

    private void handleBluetoothMessage(String message) {
        try {
            if (MainActivity.isServer) {
                // Le serveur reçoit une commande du client (ex: "TOGGLE:id")
                if (message.startsWith("TOGGLE:")) {
                    int deviceIdToToggle = Integer.parseInt(message.substring(7));
                    toggleDevice(deviceIdToToggle, false); // On passera true/false si besoin, ou on laisse l'API gérer
                }
            } else {
                // Le client reçoit les données JSON du serveur
                JSONArray response = new JSONArray(message);
                updateUI(response);
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur traitement message BT: " + message, e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (MainActivity.isServer && apiHandler != null && runnableCode != null) {
            apiHandler.post(runnableCode);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (MainActivity.isServer && apiHandler != null && runnableCode != null) {
            apiHandler.removeCallbacks(runnableCode);
        }
    }

    private void refreshData() {
        if (!MainActivity.isServer) return;

        String requestUrl = baseUrl + houseId;
        JsonArrayRequest jsonArrayRequest = new JsonArrayRequest(Request.Method.GET, requestUrl, null,
                response -> {
                    updateUI(response);
                    // Envoyer les données au client via Bluetooth
                    if (bluetoothThread != null) {
                        bluetoothThread.write(response.toString().getBytes(StandardCharsets.UTF_8));
                    }
                }, this::handleError);
        queue.add(jsonArrayRequest);
    }

    private void updateUI(JSONArray response) {
        LinearLayout linearlayout = findViewById(R.id.linearlayout);
        if (linearlayout == null) return;

        linearlayout.removeAllViews();
        try {
            for (int i = 0; i < response.length(); i++) {
                JSONObject device = response.getJSONObject(i);
                int id = device.getInt("ID");
                String name = device.optString("NAME", "Unknown Device");
                String brand = device.optString("BRAND", "");
                String model = device.optString("MODEL", "");
                String data = device.optString("DATA", "");
                int autonomy = device.optInt("AUTONOMY", -1);
                int state = device.optInt("STATE", 0);
                
                linearlayout.addView(createDeviceView(id, brand, model, name, autonomy, data, state == 1));
            }
        } catch (JSONException e) {
            Log.e(TAG, "JSON Parsing error", e);
        }
    }

    private void handleError(com.android.volley.VolleyError error) {
        Log.e(TAG, "Volley Error");
    }

    public View createDeviceView(int id, String brand, String model, String name, int autonomy, String data, boolean status) {
        LinearLayout outerLayout = new LinearLayout(this);
        // ... (Même style que précédemment)
        LinearLayout.LayoutParams outerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        outerParams.setMargins(0, 0, 0, 12);
        outerLayout.setLayoutParams(outerParams);
        outerLayout.setOrientation(LinearLayout.HORIZONTAL);
        outerLayout.setBackgroundColor(Color.parseColor("#E0E0E0"));
        outerLayout.setPadding(24, 24, 24, 24);
        outerLayout.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout textLayout = new LinearLayout(this);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        textLayout.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView titleTextView = new TextView(this);
        String title = (brand.isEmpty() && model.isEmpty()) ? name : "[" + brand + "-" + model + "] " + name;
        titleTextView.setText(title);
        titleTextView.setTextSize(16);
        titleTextView.setTextColor(Color.DKGRAY);
        titleTextView.setTypeface(null, Typeface.BOLD);

        TextView infoTextView = new TextView(this);
        infoTextView.setText("Autonomy : " + (autonomy == -1 ? "N/A" : autonomy + "%") + " Data : " + data);
        infoTextView.setTextSize(14);
        infoTextView.setTextColor(Color.GRAY);

        textLayout.addView(titleTextView);
        textLayout.addView(infoTextView);

        Button button = new Button(this);
        button.setText(status ? "ON" : "OFF");
        button.setBackgroundColor(status ? Color.parseColor("#4CAF50") : Color.LTGRAY);
        button.setTextColor(status ? Color.WHITE : Color.BLACK);
        
        // Si on est le serveur, les boutons ne sont pas cliquables selon le sujet
        if (MainActivity.isServer) {
            button.setEnabled(false);
        } else {
            button.setOnClickListener(v -> {
                // Le client envoie une commande au serveur via Bluetooth
                String cmd = "TOGGLE:" + id;
                if (bluetoothThread != null) {
                    bluetoothThread.write(cmd.getBytes(StandardCharsets.UTF_8));
                }
            });
        }

        outerLayout.addView(textLayout);
        outerLayout.addView(button);
        return outerLayout;
    }

    private void toggleDevice(int id, boolean currentStatus) {
        if (!MainActivity.isServer) return;

        StringRequest sr = new StringRequest(Request.Method.POST, baseUrl,
                response -> {
                    refreshData(); // Actualise et envoie au client
                }, this::handleError) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("deviceId", String.valueOf(id));
                params.put("houseId", String.valueOf(houseId));
                params.put("action", "turnOnOff");
                return params;
            }
        };
        queue.add(sr);
    }

    // Thread de communication Bluetooth (InputStream/OutputStream)
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(android.bluetooth.BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Erreur flux", e);
            }
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[4096];
            int bytes;
            while (true) {
                try {
                    bytes = mmInStream.read(buffer);
                    String incomingMessage = new String(buffer, 0, bytes);
                    bluetoothHandler.obtainMessage(1, incomingMessage).sendToTarget();
                } catch (IOException e) {
                    Log.d(TAG, "Socket déconnecté");
                    break;
                }
            }
        }

        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) {
                Log.e(TAG, "Erreur écriture", e);
            }
        }
    }
}