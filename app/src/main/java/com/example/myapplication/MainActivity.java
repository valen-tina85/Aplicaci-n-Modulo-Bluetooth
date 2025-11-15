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

    // Vistas de la interfaz
    ListView listViewDevices;
    EditText editTextSend;
    Button buttonSend, btnPrender, btnApagar, btnTemp, btnHum;
    TextView textViewReceived;

    // Objetos Bluetooth
    BluetoothAdapter bluetoothAdapter;
    BluetoothSocket bluetoothSocket;
    OutputStream outputStream;
    InputStream inputStream;

    // UUID estándar para Serial Port Profile (SPP)
    UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // Handler para actualizar la UI desde el hilo de lectura
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

        // 1. Inicializar Vistas
        listViewDevices = findViewById(R.id.listViewDevices);
        editTextSend = findViewById(R.id.editTextSend);
        buttonSend = findViewById(R.id.buttonSend);
        textViewReceived = findViewById(R.id.textViewReceived);
        btnPrender = findViewById(R.id.btnPrender);
        btnApagar = findViewById(R.id.btnApagar);
        btnTemp = findViewById(R.id.btnTemp);
        btnHum = findViewById(R.id.btnHum);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // 2. Inicializar Handler para el hilo de lectura
        mHandler = new Handler(msg -> {
            if (msg.what == MESSAGE_READ) {
                String readMessage = (String) msg.obj;

                if (readMessage != null && !readMessage.isEmpty()) {
                    readMessage = readMessage.trim(); // Limpia espacios extra

                    // Lógica de PARSEO (T25.5, H60.0)
                    if (readMessage.startsWith("T")) {
                        // Mensaje de Temperatura: T25.5
                        String temp = readMessage.substring(1);
                        textViewReceived.setText("Temperatura: " + temp + " °C");
                    } else if (readMessage.startsWith("H")) {
                        // Mensaje de Humedad: H60.0
                        String hum = readMessage.substring(1);
                        textViewReceived.setText("Humedad: " + hum + " %");
                    } else {
                        // Otros mensajes (e.g., confirmación LED)
                        textViewReceived.setText("Mensaje Recibido: " + readMessage);
                    }
                }
            }
            return true;
        });

        // 3. Solicitud de Permisos y Carga de Dispositivos

        // Permiso ACCESS_FINE_LOCATION (necesario en Android 6-11)
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }

        // Permiso BLUETOOTH_CONNECT (necesario en Android 12+)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                    BLUETOOTH_CONNECT_REQUEST_CODE);
        } else {
            // Si el permiso ya está concedido, cargamos la lista inmediatamente
            loadPairedDevicesList();
        }

        // 4. Configurar Click Listeners

        // Envío Genérico
        buttonSend.setOnClickListener(v -> sendData(editTextSend.getText().toString()));

        // Control LED
        btnPrender.setOnClickListener(v -> sendData("LED_ON"));
        btnApagar.setOnClickListener(v -> sendData("LED_OFF"));

        // Solicitud de Sensor
        btnTemp.setOnClickListener(v -> sendData("GET_TEMP"));
        btnHum.setOnClickListener(v -> sendData("GET_HUM"));
    }

    // =========================================================================
    // Métodos de Control Bluetooth y Permisos
    // =========================================================================

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

        // Configurar el click listener para iniciar la conexión
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

        // Ejecutar la conexión en un hilo secundario para evitar bloquear la UI
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

    // =========================================================================
    // Métodos de Comunicación
    // =========================================================================

    /**
     * Envía una cadena de datos terminada con un salto de línea (\n) al Arduino.
     */
    private void sendData(String message) {
        // El Arduino espera un salto de línea (\n) para usar readStringUntil
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

    /**
     * Hilo de lectura de datos que maneja el delimitador de nueva línea (\n).
     */
    private void startListeningForData() {
        Thread thread = new Thread(() -> {
            // Caracter de nueva línea que el Arduino usa para terminar el envío (println)
            byte delimiter = '\n';

            while (true) {
                try {
                    // Esperar hasta que haya datos
                    if (inputStream.available() > 0) {

                        // Leer carácter por carácter hasta encontrar el delimitador
                        String data = "";
                        int ch;

                        while ((ch = inputStream.read()) != -1 && ch != delimiter) {
                            data += (char) ch;
                        }

                        if (ch == delimiter) {
                            // Si encontramos el delimitador, enviamos el mensaje al Handler
                            mHandler.obtainMessage(MESSAGE_READ, data).sendToTarget();
                        }
                    }

                    // Pequeña pausa para no saturar el CPU
                    Thread.sleep(50);

                } catch (IOException e) {
                    // Se perdió la conexión
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