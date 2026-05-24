package com.example.smarthouse;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.os.Build;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "SmartHouseBT";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // UUID standard SPP
    private static final String NAME = "SmartHouseServer";

    private BluetoothAdapter bluetoothAdapter;
    private Button btnServer, btnClient;
    private TextView statusText, waitingText;
    
    // Le socket de communication statique pour être accessible par SecondActivity
    public static BluetoothSocket bluetoothSocket;
    public static boolean isServer = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth non supporté", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        btnServer = findViewById(R.id.btn_server);
        btnClient = findViewById(R.id.btn_client);
        statusText = findViewById(R.id.status_text);
        waitingText = findViewById(R.id.waiting_text);

        btnServer.setOnClickListener(v -> startServerMode());
        btnClient.setOnClickListener(v -> startClientMode());
        
        checkPermissions();
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.ACCESS_FINE_LOCATION
                }, 1);
            }
        } else {
            // Sur Android 11 et moins, ACCESS_FINE_LOCATION est souvent nécessaire pour le BT
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            }
        }
    }

    private boolean hasBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        // Sur Android 10, les permissions normales du manifest suffisent
        return true;
    }

    private void startServerMode() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth non disponible", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Veuillez activer le Bluetooth", Toast.LENGTH_SHORT).show();
            return;
        }
        
        isServer = true;
        btnServer.setText("SERVEUR EN ATTENTE");
        btnServer.setEnabled(false);
        btnClient.setVisibility(View.GONE);
        waitingText.setText("*Attente de connexion d'un client*");
        waitingText.setVisibility(View.VISIBLE);
        
        try {
            new AcceptThread().start();
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors du lancement du thread serveur", e);
            Toast.makeText(this, "Erreur lancement serveur", Toast.LENGTH_SHORT).show();
            resetUI();
        }
    }

    private void startClientMode() {
        if (bluetoothAdapter == null) return;
        if (!bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Veuillez activer le Bluetooth", Toast.LENGTH_SHORT).show();
            return;
        }

        isServer = false;
        btnClient.setText("CLIENT EN ATTENTE");
        btnClient.setEnabled(false);
        btnServer.setVisibility(View.GONE);
        waitingText.setText("*Attente de connexion au serveur*");
        waitingText.setVisibility(View.VISIBLE);

        if (!hasBluetoothPermission()) return;
        
        try {
            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
            if (pairedDevices != null && !pairedDevices.isEmpty()) {
                // Pour simplifier, on prend le premier appareil appairé. 
                // Assurez-vous que seul le serveur est appairé ou qu'il est le premier.
                BluetoothDevice targetDevice = pairedDevices.iterator().next();
                Toast.makeText(this, "Connexion à : " + targetDevice.getName(), Toast.LENGTH_SHORT).show();
                new ConnectThread(targetDevice).start();
            } else {
                Toast.makeText(this, "Aucun appareil appairé trouvé", Toast.LENGTH_SHORT).show();
                resetUI();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException lors de la recherche d'appareils", e);
            Toast.makeText(this, "Permission Bluetooth manquante", Toast.LENGTH_SHORT).show();
            resetUI();
        }
    }

    private void resetUI() {
        btnServer.setEnabled(true);
        btnServer.setText("LANCER LE SERVEUR");
        btnClient.setEnabled(true);
        btnClient.setText("LANCER LE CLIENT");
        btnClient.setVisibility(View.VISIBLE);
        btnServer.setVisibility(View.VISIBLE);
        waitingText.setVisibility(View.GONE);
    }

    private void manageConnectedSocket(BluetoothSocket socket) {
        bluetoothSocket = socket;
        Log.d(TAG, "Socket connecté, lancement de SecondActivity");
        Intent intent = new Intent(this, SecondActivity.class);
        startActivity(intent);
    }

    // Thread Serveur pour accepter une connexion
    private class AcceptThread extends Thread {
        private final BluetoothServerSocket serverSocket;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;
            try {
                if (hasBluetoothPermission() && bluetoothAdapter != null) {
                    tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord("SmartHouse", MY_UUID);
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket listen() failed", e);
            } catch (SecurityException e) {
                Log.e(TAG, "Permission Bluetooth manquante", e);
            }
            serverSocket = tmp;
        }

        public void run() {
            if (serverSocket == null) {
                Log.e(TAG, "Serveur non initialisé (socket null)");
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Erreur d'initialisation du serveur", Toast.LENGTH_SHORT).show();
                    resetUI();
                });
                return;
            }
            
            BluetoothSocket socket = null;
            while (true) {
                try {
                    socket = serverSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "Socket accept() failed", e);
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "Erreur inattendue dans AcceptThread", e);
                    break;
                }

                if (socket != null) {
                    final BluetoothSocket finalSocket = socket;
                    runOnUiThread(() -> manageConnectedSocket(finalSocket));
                    try {
                        serverSocket.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Could not close server socket", e);
                    }
                    break;
                }
            }
        }
    }

    // Thread Client pour initier une connexion
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        public ConnectThread(BluetoothDevice device) {
            BluetoothSocket tmp = null;
            mmDevice = device;
            try {
                if (hasBluetoothPermission()) {
                    tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket create() failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            if (mmSocket == null) return;
            try {
                if (hasBluetoothPermission()) {
                    mmSocket.connect();
                }
            } catch (IOException connectException) {
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    Log.e(TAG, "Could not close the client socket", closeException);
                }
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Connexion échouée", Toast.LENGTH_SHORT).show();
                    resetUI();
                });
                return;
            }
            manageConnectedSocket(mmSocket);
        }
    }
}