package com.example.myapplication;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final int BLUETOOTH_CONNECT_REQUEST_CODE = 1001;
    private final int MESSAGE_READ = 0;

    ListView listViewDevices;
    EditText editTextSend;
    Button buttonSend, btnPrender, btnApagar, btnTemp, btnHum;
    TextView textViewReceived;


    BluetoothAdapter bluetoothAdapter;
    BluetoothSocket bluetoothSocket;
    OutputStream outputStream;
    InputStream inputStream;
    UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        listViewDevices = findViewById(R.id.listViewDevices);
        editTextSend = findViewById(R.id.editTextSend);
        buttonSend = findViewById(R.id.buttonSend);
        textViewReceived = findViewById(R.id.textViewReceived);
        btnPrender = findViewById(R.id.btnPrender);
        btnApagar = findViewById(R.id.btnApagar);
        btnTemp = findViewById(R.id.btnTemp);
        btnHum = findViewById(R.id.btnHum);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        mHandler = new Handler(msg -> {
            if (msg.what == MESSAGE_READ) {
                String readMessage = (String) msg.obj;

                if (readMessage != null && !readMessage.isEmpty()) {
                    readMessage = readMessage.trim();

                    if (readMessage.startsWith("T")) {
                        String temp = readMessage.substring(1);
                        textViewReceived.setText("Temperatura: " + temp + " °C");
                    } else if (readMessage.startsWith("H")) {
                        String hum = readMessage.substring(1);
                        textViewReceived.setText("Humedad: " + hum + " %");
                    } else {
                        textViewReceived.setText("Mensaje Recibido: " + readMessage);
                    }
                }
            }
            return true;
        });


        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                    BLUETOOTH_CONNECT_REQUEST_CODE);
        } else {
            loadPairedDevicesList();
        }


        buttonSend.setOnClickListener(v -> sendData(editTextSend.getText().toString()));

        btnPrender.setOnClickListener(v -> sendData("LED_ON"));
        btnApagar.setOnClickListener(v -> sendData("LED_OFF"));


        btnTemp.setOnClickListener(v -> sendData("GET_TEMP"));
        btnHum.setOnClickListener(v -> sendData("GET_HUM"));
    }

    private void loadPairedDevicesList() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permiso de Bluetooth Connect denegado.", Toast.LENGTH_SHORT).show();
            return;
        }

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        final BluetoothDevice[] devicesArray = new BluetoothDevice[pairedDevices.size()];
        int index = 0;

        for (BluetoothDevice device : pairedDevices) {
            String name = (device.getName() != null) ? device.getName() : "Dispositivo Desconocido";
            adapter.add(name + "\n" + device.getAddress());
            devicesArray[index++] = device;
        }

        listViewDevices.setAdapter(adapter);


        listViewDevices.setOnItemClickListener((parent, view, position, id) -> {
            BluetoothDevice device = devicesArray[position];
            connectToDevice(device);
        });

        if (pairedDevices.size() > 0) {
            Toast.makeText(this, "Lista de " + pairedDevices.size() + " dispositivos emparejados cargada.", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "No se encontraron dispositivos emparejados. Asegúrate de emparejarlos en la configuración del teléfono.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == BLUETOOTH_CONNECT_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permiso concedido. Cargamos la lista de dispositivos.
                loadPairedDevicesList();
            } else {
                // Permiso denegado.
                Toast.makeText(this, "Permiso de Bluetooth necesario para mostrar dispositivos.", Toast.LENGTH_LONG).show();
            }
        }
    }


    private void connectToDevice(BluetoothDevice device) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permiso de conexión Bluetooth no concedido.", Toast.LENGTH_SHORT).show();
            return;
        }


        new Thread(() -> {
            try {
                if (bluetoothSocket != null && bluetoothSocket.isConnected()) {
                    bluetoothSocket.close();
                }

                bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid);
                bluetoothSocket.connect();
                outputStream = bluetoothSocket.getOutputStream();
                inputStream = bluetoothSocket.getInputStream();

                runOnUiThread(() -> {
                    Toast.makeText(this, "Conectado a " + device.getName(), Toast.LENGTH_SHORT).show();
                    startListeningForData();
                });
            } catch (IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error al conectar. Asegúrese de que el módulo esté encendido y emparejado.", Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                });
            }
        }).start();
    }

    private void sendData(String message) {
        String messageWithTerminator = message + "\n";

        try {
            if (outputStream != null) {
                outputStream.write(messageWithTerminator.getBytes());
            } else {
                Toast.makeText(this, "No hay conexión Bluetooth activa.", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error al enviar datos.", Toast.LENGTH_SHORT).show();
        }
    }


    private void startListeningForData() {
        Thread thread = new Thread(() -> {
            byte delimiter = '\n';
            while (true) {
                try {
                    if (inputStream.available() > 0) {
                        String data = "";
                        int ch;

                        while ((ch = inputStream.read()) != -1 && ch != delimiter) {
                            data += (char) ch;
                        }

                        if (ch == delimiter) {
                            mHandler.obtainMessage(MESSAGE_READ, data).sendToTarget();
                        }
                    }
                    Thread.sleep(50);

                } catch (IOException e) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Conexión Bluetooth perdida.", Toast.LENGTH_SHORT).show());
                    break;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        thread.start();
    }
}