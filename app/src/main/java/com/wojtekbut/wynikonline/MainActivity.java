package com.wojtekbut.wynikonline;

import androidx.appcompat.app.AppCompatActivity;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;


public class MainActivity extends AppCompatActivity {

    TextView isrunning, gospodarz, gosc, status;
    TextView homeScoreText, awayScoreText, timetype;
    EditText gosodarzedit, goscedit;
    Button startservice, halftime;;
    Button homeplus, homeminus, awayplus, awayminus;
    int homescore = 0, awayscore = 0;
    Button save;
    Intent intent;
    Context context = this;
    Message msg;
    Map<String, String> dane = new HashMap<String, String>();
    Boolean wasRunning = false;
    String timetypetext = "gry";
    int periodNr = 1, periodTime = 25*60;

    Boolean fromStartService = false;


    /** Messenger for communicating with service. */
    Messenger mService = null;
    /** Flag indicating whether we have called bind on the service. */
    boolean mIsBound;

    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case ClockService.TIME_UPDATE:
                    dane = (Map<String, String>) msg.obj;
                    isrunning.setText(dane.get("time"));
                    timetypetext = dane.get("timetype");
                    timetype.setText("czas " + timetypetext);
                    break;
                case ClockService.SENDING_DATA:
                    dane = (Map<String, String>) msg.obj;
                    gospodarz.setText(dane.get("home"));
                    gosodarzedit.setText(dane.get("home"));
                    gosc.setText(dane.get("away"));
                    goscedit.setText(dane.get("away"));
                    homeScoreText.setText(dane.get("homescore"));
                    awayScoreText.setText(dane.get("awayscore"));
                    homescore = Integer.valueOf(homeScoreText.getText().toString());
                    awayscore = Integer.valueOf(awayScoreText.getText().toString());

                default:
                    super.handleMessage(msg);
            }
        }
    }

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    final Messenger mMessenger = new Messenger(new IncomingHandler());

    /**
     * Class for interacting with the main interface of the service.
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {

            mService = new Messenger(service);
            status.setText("Attached.");

            try {
                Message msg = Message.obtain(null,
                        ClockService.LOG_ME_IN);
                msg.replyTo = mMessenger;
                mService.send(msg);
                if (wasRunning) {
                    sendData(ClockService.GIVE_ME_DATA);
                } else {
                    daneUpdate();
                    sendData(ClockService.SENDING_DATA);
                }
                if (fromStartService) {
                    resetTime("extra");
                    resetTime("przerwy");
                    sendData(ClockService.SET_TIME);
                }
                // Give it some value as an example.

            } catch (RemoteException e) {
                // In this case the service has crashed before we could even
                // do anything with it; we can count on soon being
                // disconnected (and then reconnected if it can be restarted)
                // so there is no need to do anything here.
            }

            // As part of the sample, tell the user what happened.
            Toast.makeText(context, "Connected",
                    Toast.LENGTH_SHORT).show();
        }
        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            mService = null;
            status.setText("Disconnected.");
            // As part of the sample, tell the user what happened.
            Toast.makeText(context, "Disconnected",
                    Toast.LENGTH_SHORT).show();
        }
    };

    void doBindService() {
        // Establish a connection with the service.  We use an explicit
        // class name because there is no reason to be able to let other
        // applications replace our component.
        bindService(new Intent(context,
                ClockService.class), mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
        status.setText("Binding.");
    }

    void doUnbindService() {
        if (mIsBound) {
            // If we have received the service, and hence registered with
            // it, then now is the time to unregister.
            if (mService != null) {
                try {
                    msg = Message.obtain(null,
                            ClockService.LOG_ME_OUT);
                    msg.replyTo = mMessenger;
                    mService.send(msg);
                } catch (RemoteException e) {
                    // There is nothing special we need to do if the service
                    // has crashed.
                }
            }

            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
            status.setText("Unbinding.");
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        startservice = findViewById(R.id.startservice);
        halftime = findViewById(R.id.halftime);
        isrunning = findViewById(R.id.isrunning);
        gospodarz = findViewById(R.id.gospodarz);
        gosc = findViewById(R.id.gosc);
        gosodarzedit = findViewById(R.id.gospodarzedit);
        goscedit = findViewById(R.id.goscedit);
        save = findViewById(R.id.save);
        homeplus = findViewById(R.id.homeplus);
        homeminus = findViewById(R.id.homeminus);
        awayplus = findViewById(R.id.awayplus);
        awayminus = findViewById(R.id.awayminus);
        homeScoreText = findViewById(R.id.homescore);
        awayScoreText = findViewById(R.id.awayscore);
        status = findViewById(R.id.status);
        timetype = findViewById(R.id.timetype);
        gospodarz.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                gosodarzedit.setVisibility(View.VISIBLE);
                gosodarzedit.requestFocus();
                gospodarz.setVisibility(View.GONE);
                return false;
            }
        });
        gosc.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                goscedit.setVisibility(View.VISIBLE);
                goscedit.requestFocus();
                gosc.setVisibility(View.GONE);
                return false;
            }
        });
        dane.put("homescore", "0");
        dane.put("awayscore", "0");
        intent = new Intent(this, ClockService.class);
        intent.putExtra("timetype", timetypetext);
        Toast.makeText(context, "On Create",
                Toast.LENGTH_SHORT).show();
        if (isserviceruning(ClockService.class)) {
            wasRunning = true;
            doBindService();
            status.setText("Service is running.");
            startservice.setText("Stop Timer");

        } else {
            status.setText("Service is stopped.");
        }
    }

    public void startservice(View v) {
        if (startservice.getText().toString().equals("Start Timer")) {
            startService(intent);
            status.setText("Service is running.");
            doBindService();
            startservice.setText("Stop Timer");
            fromStartService = true;
        }else if (startservice.getText().toString().equals("Stop Timer")) {
            stopService(intent);
            status.setText("Service is stopped.");
            doUnbindService();
            startservice.setText("Start Timer");
        }


    }

    public void onHalftime(View v) {

    }

    public void onsave(View v) throws RemoteException {
        if (gosodarzedit.getText().toString().equals("")) {
            gospodarz.setText("Gospodarze");
        } else {
            gospodarz.setText(gosodarzedit.getText().toString());
        }
        if (goscedit.getText().toString().equals("")) {
            gosc.setText("Goście");
        } else {
            gosc.setText(goscedit.getText().toString());
        }
        gosodarzedit.setVisibility(View.GONE);
        goscedit.setVisibility(View.GONE);
        gospodarz.setVisibility(View.VISIBLE);
        gosc.setVisibility(View.VISIBLE);
        dane.put("home", gospodarz.getText().toString());
        dane.put("away", gosc.getText().toString());
        sendData(ClockService.UPDATE_PLAYER);

    }

    public void onScoreUpdate(View v) throws RemoteException {
        switch (v.getId()) {
            case R.id.homeplus:
                homescore++;
                homeScoreText.setText(String.valueOf(homescore));
                dane.put("homescore",homeScoreText.getText().toString());
                break;
            case R.id.homeminus:
                if (homescore > 0) {
                    homescore--;
                }
                homeScoreText.setText(String.valueOf(homescore));
                dane.put("homescore",homeScoreText.getText().toString());
                break;
            case R.id.awayplus:
                awayscore++;
                awayScoreText.setText(String.valueOf(awayscore));
                dane.put("awayscore",awayScoreText.getText().toString());
                break;
            case R.id.awayminus:
                if (awayscore > 0) {
                    awayscore--;
                }
                awayScoreText.setText(String.valueOf(awayscore));
                dane.put("awayscore",awayScoreText.getText().toString());
                break;
        }
        sendData(ClockService.UPDATE_SCORE);
    }

    void sendData(int code) throws RemoteException {
        if (mIsBound) {
            msg = Message.obtain(null,
                    code, dane);
            msg.replyTo = mMessenger;
            mService.send(msg);
        }
    }

    void daneUpdate() {
        if (dane.get("homescore").equals("")) {
            dane.put("homescore", "0");
        }
        if (dane.get("awayscore").equals("")) {
            dane.put("awayscore", "0");
        }
    }

    boolean isserviceruning(Class<?> serviceClass){
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    void resetTime(String timetypetext) {
        switch (timetypetext) {
            case "gry":
                dane.put("timeminute", "0");
                dane.put("timesecond", "0");
                dane.put("halfminute","");
                dane.put("halfsecond", "");
                dane.put("overminute", "");
                dane.put("oversecond", "");
                break;
            case "przerwy":
                dane.put("halfminute","0");
                dane.put("halfsecond", "0");
                dane.put("timeminute", "");
                dane.put("timesecond", "");
                dane.put("overminute", "");
                dane.put("oversecond", "");
                break;
            case "extra":
                dane.put("overminute", "0");
                dane.put("oversecond", "0");
                dane.put("timeminute", "");
                dane.put("timesecond", "");
                dane.put("halfminute","");
                dane.put("halfsecond", "");
                break;
            case "all":
                dane.put("timeminute", "0");
                dane.put("timesecond", "0");
                dane.put("halfminute","0");
                dane.put("halfsecond", "0");
                dane.put("overminute", "0");
                dane.put("oversecond", "0");
                break;
        }
    }

    @Override
    protected void onDestroy() {
        doUnbindService();
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        Toast.makeText(context, "On Start",
                Toast.LENGTH_SHORT).show();
        super.onStart();
    }
}