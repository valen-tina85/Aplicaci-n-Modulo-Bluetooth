package com.example.myapplication;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
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

    ListView listViewDevices;
    EditText editTextSend;
    Button buttonSend;
    TextView textViewReceived;
    BluetoothAdapter bluetoothAdapter;
    BluetoothSocket bluetoothSocket;
    OutputStream outputStream;
    InputStream inputStream;
    UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    //número completo (00001101-0000-1000-8000-00805F9B34FB) es un UUID estándar
    // que identifica el tipo de servicio Bluetooth que se está utilizando. En este
    // caso, es el Serial Port Profile (SPP), que es el que usan los módulos como
    // el HC-05 para comunicación tipo puerto serie.

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

        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }

        listViewDevices = findViewById(R.id.listViewDevices);
        editTextSend = findViewById(R.id.editTextSend);
        buttonSend = findViewById(R.id.buttonSend);
        textViewReceived = findViewById(R.id.textViewReceived);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        final BluetoothDevice[] devicesArray = new BluetoothDevice[pairedDevices.size()];
        int index = 0;

        for (BluetoothDevice device : pairedDevices) {
            adapter.add(device.getName() + "\n" + device.getAddress());
            devicesArray[index++] = device;
        }

        listViewDevices.setAdapter(adapter);

        listViewDevices.setOnItemClickListener((parent, view, position, id) -> {
            BluetoothDevice device = devicesArray[position];
            connectToDevice(device);
        });

        buttonSend.setOnClickListener(v -> {
            String message = editTextSend.getText().toString();
            try {
                outputStream.write(message.getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void connectToDevice(BluetoothDevice device) {
        try {
            bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid);
            bluetoothSocket.connect();
            outputStream = bluetoothSocket.getOutputStream();
            inputStream = bluetoothSocket.getInputStream();
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            Toast.makeText(this, "Conectado a " + device.getName(), Toast.LENGTH_SHORT).show();
            startListeningForData();
        } catch (IOException e) {
            Toast.makeText(this, "Error al conectar", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private void startListeningForData() {
        Thread thread = new Thread(() -> {
            byte[] buffer = new byte[1024];
            int bytes;
            while (true) {
                try {
                    bytes = inputStream.read(buffer);
                    final String received = new String(buffer, 0, bytes);
                    runOnUiThread(() -> textViewReceived.setText(received));
                } catch (IOException e) {
                    break;
                }
            }
        });
        thread.start();
    }
}
