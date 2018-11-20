package com.example.akshata.hanchu;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.IntentFilter;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class ReceiveActivity extends AppCompatActivity {

    Toolbar toolbar;
    TextView read_msg_box;
    ListView listView;
    Button btnSend;
    EditText writeMsg;

    WifiManager wifiManager;
    WifiP2pManager mManager;
    WifiP2pManager.Channel mChannel;

    BroadcastReceiver mReceiver;
    IntentFilter mIntentFilter;

    List<WifiP2pDevice> peers = new ArrayList<WifiP2pDevice>();
    String[] deviceNameArray;
    WifiP2pDevice[] deviceArray;

    Client client;
    Server server;
    SendReceive sendReceive;

    static final int MESSAGE_READ = 1;
    public static final String tag = "abcdreceive";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send);
        doInitialWork();
        setListeners();
        startWifip2pDiscovery();
    }

    private void doInitialWork() {
        toolbar = findViewById(R.id.send_toolbar);
        listView = (ListView) findViewById(R.id.peerListView);
        btnSend = (Button) findViewById(R.id.btnSend);
        read_msg_box = (TextView) findViewById(R.id.readMsg);
        writeMsg=(EditText) findViewById(R.id.writeMsg);

        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_black_24dp);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mManager.initialize(this, getMainLooper(), null);

        mReceiver = new WifiDirectBroadcastReceiver(mManager, mChannel, this, "receiver");

        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
    }

    private void startWifip2pDiscovery() {
        mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.i(tag,"xyz discovery started on receiver");
            }
            @Override
            public void onFailure(int i) {
                Log.i(tag,"xyz discovery did not start on receiver");
            }
        });
    }

    Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_READ:
                    byte[] readBuff = (byte[]) msg.obj;
                    String tempMsg = new String(readBuff, 0, msg.arg1);
                    read_msg_box.setText(tempMsg);
                    break;
            }
            return true;
        }
    });

    WifiP2pManager.PeerListListener peerListListener = new WifiP2pManager.PeerListListener() {
        @Override
        public void onPeersAvailable(WifiP2pDeviceList peerList) {
            if (!peerList.getDeviceList().equals(peers)) {
                peers.clear();
                peers.addAll(peerList.getDeviceList());

                deviceNameArray = new String[peerList.getDeviceList().size()];
                deviceArray = new WifiP2pDevice[peerList.getDeviceList().size()];
                int index = 0;

                for (WifiP2pDevice device : peerList.getDeviceList()) {
                    deviceNameArray[index] = device.deviceName;
                    deviceArray[index] = device;
                    index++;
                }

                ArrayAdapter<String> adapter = new ArrayAdapter<String>(getApplicationContext(), android.R.layout.simple_list_item_1, deviceNameArray);
                listView.setAdapter(adapter);
            }

            if (peers.size() == 0) {
                Toast.makeText(getApplicationContext(), "No Device Found", Toast.LENGTH_SHORT).show();
                return;
            }
        }
    };

    WifiP2pManager.ConnectionInfoListener connectionInfoListener = new WifiP2pManager.ConnectionInfoListener() {
        @Override
        public void onConnectionInfoAvailable(WifiP2pInfo wifiP2pInfo) {
            Log.i(tag,"in onconnectioninfoavailable in receiver");

            final InetAddress groupOwnerAddress = wifiP2pInfo.groupOwnerAddress;

            if (wifiP2pInfo.groupFormed && wifiP2pInfo.isGroupOwner) {
                Log.i(tag,"xyz server thread started on receiver");
                server = new Server();
                server.start();
            } else if (wifiP2pInfo.groupFormed) {
                Log.i(tag,"xyz client thread started on receiver");
                client = new Client(groupOwnerAddress);
                client.start();
            }
        }
    };

    public void setListeners() {
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                final WifiP2pDevice device = deviceArray[i];
                WifiP2pConfig config = new WifiP2pConfig();
                config.deviceAddress = device.deviceAddress;

                mManager.connect(mChannel, config, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(getApplicationContext(), "Connected to " + device.deviceName, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(int i) {
                        Toast.makeText(getApplicationContext(), "Not Connected", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String msg=writeMsg.getText().toString();
                if(sendReceive.socket!=null)
                    //sendReceive.write(msg.getBytes());
                    sendReceive.write();
                else
                    Log.i(tag,"send pressed before connection establishment");
            }
        });
    }

    public class Server extends Thread {
        Socket socket;
        ServerSocket serverSocket;

        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket(8880);
                socket = serverSocket.accept();
                sendReceive = new SendReceive(socket);
                sendReceive.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class SendReceive extends Thread {
        private Socket socket;
        private InputStream inputStream;
        private OutputStream outputStream;

        public SendReceive(Socket skt) {
            socket = skt;
            Log.i(tag,"xyz SR thread receiver");
            try {
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public boolean copyFile(InputStream inputStream, OutputStream out) {
            byte buf[] = new byte[1024];
            int len;
            try {
                while ((len = inputStream.read(buf)) != -1) {
                    out.write(buf, 0, len);
                }
                out.close();
                inputStream.close();
            } catch (IOException e) {
                Log.d(tag, e.toString());
                return false;
            }
            return true;
        }

        @Override
        //this is the run method to read a received photo and save as a jpeg file
        public void run() {
            boolean retval = false;
            Log.i(tag,"xyz SR thread run receiver");
            /*final File f = new File(Environment.getExternalStorageDirectory() + "/"
                    +  getPackageName() + "/wifip2pshared-"
                    + System
                    .currentTimeMillis()
                    + ".jpg");
            Log.i(tag,"path : " + Environment.getExternalStorageDirectory() + "/"
                    +  getPackageName() + "/wifip2pshared-"
                    + System
                    .currentTimeMillis()
                    + ".jpg");
            File dirs = new File(f.getParent());
            Log.i(tag,"dirs : " + dirs + " dirs.exists() :" + dirs.exists());
            if (!dirs.exists()) {
                try {
                    retval = dirs.mkdirs();
                } catch (Exception e){
                    Log.i(tag,"exception trying to create hanchu directory ");
                }
                Log.i(tag,"dirs.exists() afetr creation : " + dirs.exists());
                Log.i(tag,"retval mkdir : " + retval);
            }*/

            final File f = new File(android.os.Environment.getExternalStoragePublicDirectory
                    ("pictures")
                    + "/"
                    + "/wifip2pshared-"
                    + System
                    .currentTimeMillis()
                    + ".jpg");

            Log.i(tag,"file path : " + f.getPath());

            try {
                f.createNewFile();
                Log.i(tag, "file created : " + f);
            } catch (IOException e) {
                Log.i(tag,"io exception trying to create file");
                e.printStackTrace();
            }
            try {
                copyFile(inputStream, new FileOutputStream(f));
                if(f==null)
                    Log.i(tag,"photo not received");
                else
                    Log.i(tag,"file details after copying : " + f.getAbsolutePath() + "\n" + f
                            .exists() + "\n" + f.toString() + "\n" + f.toURI());
            } catch (FileNotFoundException e) {
                Log.i(tag,"fnf exception trying to copy file");
                e.printStackTrace();
            }
            Log.i(tag," this place should have the file " + f.getAbsolutePath());
        }

        public void write(){
            ContentResolver cr = getContentResolver();
            InputStream myPhoto = null;
            int len;
            byte buf[]  = new byte[1024];
            try {
                myPhoto = cr.openInputStream(Uri.parse
                        ("/storage/emulated/0/DCIM/100MEDIA/IMAG0001.jpg"));
                if(myPhoto==null)
                    Log.i(tag,"xyz couldnt find photo on sender");
                while ((len = myPhoto.read(buf)) != -1) {
                    outputStream.write(buf, 0, len);
                }
                Log.i(tag,"xyz finished writing photo on sender");
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public class Client extends Thread {
        Socket socket;
        String hostAdd; //its own IP address

        public Client(InetAddress hostAddress) {
            hostAdd = hostAddress.getHostAddress();
            socket = new Socket();
        }

        @Override
        public void run() {
            try {
                Log.i(tag,"xyz client thread on receiver");
                socket.connect(new InetSocketAddress(hostAdd, 8880), 500);
                sendReceive = new SendReceive(socket);
                sendReceive.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mReceiver, mIntentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (client != null && client.socket != null) {
            try {
                Log.i(tag, "destroying clinet socket");
                client.socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (sendReceive != null && sendReceive.socket != null) {
            try {
                Log.i(tag, "destroying sendreceive socket");
                sendReceive.socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (server != null) {
            if (server.socket != null) {
                try {
                    Log.i(tag, "destroying server socket");
                    server.socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (server.serverSocket != null) {
                try {
                    Log.i(tag, "destroying serversocket socket");
                    server.serverSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
