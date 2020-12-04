package com.wojtekbut.wynikonline;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.PrimitiveIterator;
import java.util.Timer;
import java.util.TimerTask;

import javax.net.ssl.HttpsURLConnection;

public class ClockService extends Service {

    Handler mHandler;
    boolean isConnected, isRunning, isHalfTime, isOverTime;
    Integer timeMinute, timeSecond;
    Integer overMinute, overSecond;
    Integer halfMinute, halfSecond;
    final String CHANNEL_ID = "100";
    static final int TIME_UPDATE = 1;
    static final int LOG_ME_IN = 2;
    static final int LOG_ME_OUT = 3;
    static final int UPDATE_PLAYER = 4;
    static final int UPDATE_SCORE = 5;
    static final int GIVE_ME_DATA = 6;
    static final int SENDING_DATA = 7;
    static final int SET_TIME = 8;
    String timeMinuteStr, timeSecondStr;
    String home = "Gospodarze", away = "Goście";
    String homescore = "0", awayscore = "0";
    String timetype = "gry";
    Timer mTimer;
    TimerTask mTimerTask;
    NotificationManagerCompat notificationManager;
    NotificationCompat.Builder builder;
    Context context;
    Intent notificationIntent;
    PendingIntent pendingIntent;
    Messenger client;
    Message msg;
    Map<String, String> dane = new HashMap<String, String>();
    Map<String, Integer> timeAll = new HashMap<String, Integer>();
    int periodNr = 1, periodTime = 25*60;
    HttpURLConnection con;
    URL url;
    String baseurl  = "https://rycerzejelonek.waw.pl/tabela/writemecz.php?";


    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case LOG_ME_IN:
                    client =msg.replyTo;
                    isConnected = true;
                    break;
                case LOG_ME_OUT:
                    client = null;
                    isConnected = false;
                    break;
                case UPDATE_PLAYER:
                    dane = (Map<String, String>) msg.obj;
                    home = dane.get("home");
                    away = dane.get("away");
                    break;
                case UPDATE_SCORE:
                    dane = (Map<String, String>) msg.obj;
                    homescore = dane.get("homescore");
                    awayscore = dane.get("awayscore");
                    break;
                case GIVE_ME_DATA:
                    try {
                        if (isConnected)
                            sendToClient(SENDING_DATA);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                    break;
                case SENDING_DATA:
                    dane = (Map<String, String>) msg.obj;
                    home = dane.get("home");
                    away = dane.get("away");
                    homescore = dane.get("homescore");
                    awayscore = dane.get("awayscore");
                    break;
                case SET_TIME:
                    dane = (Map<String, String>) msg.obj;
                    if (dane.get("timeminute") != "")
                        timeMinute = Integer.valueOf(dane.get("timeminute"));
                    if (dane.get("timesecond") != "")
                        timeSecond = Integer.valueOf(dane.get("timesecond"));
                    if (dane.get("overminute") != "")
                        overMinute = Integer.valueOf(dane.get("overminute"));
                    if (dane.get("oversecond") != "")
                        overSecond = Integer.valueOf(dane.get("oversecond"));
                    if (dane.get("halfminute") != "")
                        halfMinute = Integer.valueOf(dane.get("halfminute"));
                    if (dane.get("halfsecond") != "")
                        halfSecond = Integer.valueOf(dane.get("halfsecond"));
                    updateAllTime();
                default:
                    super.handleMessage(msg);
            }
        }
    }

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    final Messenger mMessenger = new Messenger(new IncomingHandler());

    void sendToClient(int code) throws RemoteException {
        msg = Message.obtain(null,
                code, dane);
        client.send(msg);
    }

    @Override
    public void onCreate() {
        context  = this;
        isConnected = false;
        isRunning = false;
        timeMinute = 0;
        timeSecond = 0;
        overMinute = 0;
        overSecond = 0;
        halfMinute = 0;
        halfSecond = 0;
        dane.put("home", home);
        dane.put("away", away);
        dane.put("homescore", homescore);
        dane.put("awayscore", awayscore);


        createNotificationChannel();




        super.onCreate();
    }

    class TimeTimerTask extends TimerTask {

        @Override
        public void run() {
            if (!isOverTime) {
                isRunning = true;
                timeSecond++;
                if (timeSecond == 60) {
                    timeMinute++;
                    timeSecond = 0;
                }
                timeAll.put("gryminute", timeMinute);
                timeAll.put("grysecond", timeSecond);
                Log.d("timer run:", timeAll.toString());
                Log.d("timer run: ", timeMinute.toString() +" " + timeSecond.toString());
                if (timeMinute*60 == periodTime*periodNr){
                    isOverTime = true;
                    //timetype = "extra";
                }
            } else {
                timetype = "extra";
                overSecond++;
                if (overSecond == 60) {
                    overMinute++;
                    overSecond = 0;
                }
                timeAll.put("extraminute", overMinute);
                timeAll.put("extrasecond", overSecond);
            }
            Notification notification =
                    new NotificationCompat.Builder(context, CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_launcher_background)
                            .setContentTitle(home + " " + homescore + " - " + awayscore + " " + away)
                            .setContentText("czas " + timetype + ": " + getTime(timetype))
                            .setContentIntent(pendingIntent)
                            .build();
            NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.notify(1, notification);
            try {
                dane.put("time", getTime(timetype));
                dane.put("timetype", timetype);
                if (isConnected)
                    sendToClient(TIME_UPDATE);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            String paramsurl = null;
            try {
                paramsurl = "home=" + URLEncoder.encode(home, StandardCharsets.UTF_8.toString()) + "&away=" + URLEncoder.encode(away, StandardCharsets.UTF_8.toString()) + "&homescore=" + URLEncoder.encode(homescore, StandardCharsets.UTF_8.toString()) + "&awayscore=" + URLEncoder.encode(awayscore, StandardCharsets.UTF_8.toString());
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            String timeurl = "&";
            switch (timetype) {
                case "gry":
                    timeurl += "time=";
                    break;
                case "przerwy":
                    timeurl += "halftime=";
                    break;
                case "extra":
                    timeurl += "extratime=";
                    break;
            }
            timeurl += getTime(timetype);
            try {
                url = new URL(baseurl+paramsurl+timeurl);
                Log.d("url", url.toString());
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    // All your networking logic
                    // should be here
                    try {
                        HttpsURLConnection myConnection = (HttpsURLConnection) url.openConnection();
                        int res=myConnection.getResponseCode();
                        Log.d("url response", String.valueOf(res));
                        myConnection.disconnect();
                    } catch (Exception e) {
                        Toast.makeText(context, "No internet",
                                Toast.LENGTH_SHORT).show();
                        e.printStackTrace();
                    }
                }
            });
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (isRunning) {
            return super.onStartCommand(intent, flags, startId);
        }
        timetype = intent.getStringExtra("timetype");
        timeAll.put("gryminute", timeMinute);
        timeAll.put("grysecond", timeSecond);
        timeAll.put("przerwyminute", halfMinute);
        timeAll.put("przerwysecond", halfSecond);
        timeAll.put("extraminute", overMinute);
        timeAll.put("extrasecond", overSecond);
        mTimer = new Timer();

        mTimer.scheduleAtFixedRate(new TimeTimerTask(), 1000, 1000);





        notificationIntent = new Intent(this, MainActivity.class)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        pendingIntent =
                PendingIntent.getActivity(this, 0, notificationIntent, 0);

        Notification notification =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentTitle(home + " " + homescore + " - " + awayscore + " " + away)
                        .setContentText("czas " + timetype + ": " + getTime(timetype))
                        .setContentIntent(pendingIntent)
                        .build();
        startForeground(1, notification);



        return super.onStartCommand(intent, flags, startId);
    }

    public ClockService() throws MalformedURLException {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }

    @Override
    public void onDestroy() {
        if (mTimer != null) {
            mTimer.cancel();
            isRunning = false;
            super.onDestroy();
        }
    }

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Notification channel";
            String description = "Notification";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
    private String getTime(String timetype) {
        Log.d("getTime", timetype);
        int minute = timeAll.get(timetype+"minute");
        int second = timeAll.get(timetype+"second");
        if (second < 10) {
            timeSecondStr = "0" + String.valueOf(second);
        } else {
            timeSecondStr = String.valueOf(second);
        }
        if (minute < 10) {
            timeMinuteStr = "0" + String.valueOf(minute);
        } else {
            timeMinuteStr = String.valueOf(minute);
        }
        Log.d("getTime", timeMinuteStr + ":" + timeSecondStr);
        return timeMinuteStr + ":" + timeSecondStr;
    }

    private void updateAllTime() {
        String paramsurl = null;

        paramsurl = "time=" + getTime("gry") + "&halftime=" + getTime("przerwy") + "&extratime=" + getTime("extra");

        try {
            url = new URL(baseurl+paramsurl);
            Log.d("url", url.toString());
        } catch (MalformedURLException e) {
            e.printStackTrace();

        }
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    HttpsURLConnection myConnection = (HttpsURLConnection) url.openConnection();
                    int res=myConnection.getResponseCode();
                    Log.d("url response", String.valueOf(res));
                    myConnection.disconnect();
                } catch (Exception e){
                    Toast.makeText(context, "No internet",
                            Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                }
            }
        });
    }

}