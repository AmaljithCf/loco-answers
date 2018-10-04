/*
 *   Copyright (C) 2018 SHUBHAM TYAGI
 *
 *    This file is part of LoKo HacK.
 *     Licensed under the GNU GENERAL PUBLIC LICENSE, Version 3.0 (the "License"); you may not
 *     use this file except in compliance with the License. You may obtain a copy of
 *     the License at
 *
 *     https://www.gnu.org/licenses/gpl-3.0
 *
 *    LoKo hacK is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation, either version 3 of the License, or
 *    (at your option) any later version.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with LoKo Hack.  If not, see <http://www.gnu.org/licenses/>.
 *
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 *
 */

package ai.loko.hk.ui.services;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.AsyncTask;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.balsikandar.crashreporter.CrashReporter;
import com.dd.processbutton.iml.ActionProcessButton;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import ai.loko.hk.ui.MainActivity;
import ai.loko.hk.ui.answers.Engine;
import ai.loko.hk.ui.constants.Constant;
import ai.loko.hk.ui.data.Data;
import ai.loko.hk.ui.model.Question;
import ai.loko.hk.ui.ocr.ImageTextReader;
import ai.loko.hk.ui.ocr.Points;
import ai.loko.hk.ui.ocr.Screenshotter;
import ui.R;


@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class OCRFloating extends Service {

    private static final String TAG = OCRFloating.class.getSimpleName();
    public static boolean isGoogle = true;
    ActionProcessButton getAnswer;

    int[] coordinate = new int[4];

    private NotificationManager notificationManager;
    private WindowManager mWindowManager;

    private View mFloatingView;

    private TextView option1, option2, option3;
    private WindowManager.LayoutParams params;
    private ImageTextReader imageTextReader;

    private int width, height;

    private Bitmap mBitmap;


    public OCRFloating() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        mFloatingView = LayoutInflater.from(this).inflate(R.layout.floating, new LinearLayout(this));

        notification();
        int LAYOUT_FLAG;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_PHONE;
        }

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                LAYOUT_FLAG,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = 0;
        params.y = 100;

        if (mWindowManager != null) {
            mWindowManager.addView(mFloatingView, params);
            //
        }

        mFloatingView.findViewById(R.id.head).setOnTouchListener(new View.OnTouchListener() {
            int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        //Calculate the X and Y coordinates of the view.
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);

                        //Update the layout with new X & Y coordinate
                        mWindowManager.updateViewLayout(mFloatingView, params);
                        return true;
                }
                return false;
            }
        });

        getAnswer = mFloatingView.findViewById(R.id.getanswer1);
        getAnswer.setMode(ActionProcessButton.Mode.ENDLESS);

        option1 = mFloatingView.findViewById(R.id.optionA);
        option2 = mFloatingView.findViewById(R.id.optionB);
        option3 = mFloatingView.findViewById(R.id.optionC);

        imageTextReader = new ImageTextReader(getApplicationContext());

        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        width = size.x;
        height = size.y;

        getAnswer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OCRFloating.isGoogle = true;
                getAnswer.setProgress(1);
                Log.d(TAG, "onClick: getanswers clicked");
                captureScreenshot();
            }
        });
        coordinate[0] = (int) Math.ceil((double) Points.X1);
        coordinate[1] = (int) Math.ceil((double) Points.Y1);
        coordinate[2] = (int) Math.ceil((double) Points.X2);
        coordinate[3] = (int) Math.ceil((double) Points.Y2);
    }

    private void captureScreenshot() {

        Screenshotter.getInstance(getApplicationContext()).setSize(width, height).takeScreenshot(new Screenshotter.ScreenshotCallback() {
            @Override
            public void onScreenshot(Bitmap bitmap) {
                mBitmap = bitmap;
                processImage();
            }
        });

    }

    // here we process screen images to extract question and answer
    public void processImage() {
        Log.d(TAG, "processImage: ");
        if (coordinate[2] == 0 || coordinate[3] == 0) {
            coordinate[2] = width;
            coordinate[3] = height;
        }
        final Bitmap croppedGrayscaleImage;
        //if (Data.GRAYSCALE_IAMGE_FOR_OCR)
        //    croppedGrayscaleImage = Utils.getGrayscaleImage(Bitmap.createBitmap(mBitmap, coordinate[0], coordinate[1], coordinate[2] - coordinate[0], coordinate[3] - coordinate[1]));
        //else
        croppedGrayscaleImage = Bitmap.createBitmap(mBitmap, coordinate[0], coordinate[1], coordinate[2] - coordinate[0], coordinate[3] - coordinate[1]);

        final String questionAndOption[] = imageTextReader.getTextFromBitmap2(croppedGrayscaleImage);
        Log.d(TAG, "processImage: " + 15651);

        if (questionAndOption.length == 4) {
            new Update().execute(questionAndOption[0], questionAndOption[1], questionAndOption[2], questionAndOption[3]);
        } else if (questionAndOption.length > 0) {
            Toast.makeText(getApplicationContext(), questionAndOption[0], Toast.LENGTH_SHORT).show();
        }

        if (Data.IMAGE_LOGS_STORAGE) {
            new Thread() {
                @Override
                public void run() {
                    writeToStorage(croppedGrayscaleImage);
                    writeToStorage(questionAndOption);
                }
            }.start();
        }

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = "";
        if (intent != null)
            action = intent.getAction();
        if (action != null && action.equalsIgnoreCase("stop")) {
            stopSelf();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private void notification() {
        Intent i = new Intent(this, OCRFloating.class);
        i.setAction("stop");
        PendingIntent pi = PendingIntent.getService(this, 0, i, 0);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0);
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getApplicationContext());
        mBuilder.setContentText("LoKo HacK: Committed to speed and performance :)")
                .setContentTitle("Tap to remove overlay screen")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pi)
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setOngoing(true).setAutoCancel(true)
                .addAction(android.R.drawable.ic_menu_more, "Open Loko hack", pendingIntent);

        notificationManager.notify(1545, mBuilder.build());

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mFloatingView != null) mWindowManager.removeView(mFloatingView);
        notificationManager.cancelAll();

    }

    WindowManager getWindowManager() {
        return (mWindowManager);
    }

    private void writeToStorage(String[] questionAndOption) {
        File picFile = new File(Constant.path, "QaOption_" + Long.toString(System.currentTimeMillis()) + ".txt");
        String value = "";
        for (String s : questionAndOption) {
            value += s + "\n";
        }
        try {
            picFile.createNewFile();
            FileOutputStream outputStream = new FileOutputStream(picFile);
            outputStream.write(value.getBytes());
            outputStream.close();
        } catch (IOException e) {

            CrashReporter.logException(e);
        }

    }

    private void writeToStorage(Bitmap bmp) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        File picFile = new File(Constant.path, "SCR_" + Long.toString(System.currentTimeMillis()) + ".jpg");
        bmp.compress(Bitmap.CompressFormat.JPEG, 60, bytes);
        try {
            Log.d(TAG, "Writing images");
            picFile.createNewFile();
            FileOutputStream outputStream = new FileOutputStream(picFile);
            outputStream.write(bytes.toByteArray());
            outputStream.close();
        } catch (IOException e) {
            CrashReporter.logException(e);
        }
    }

    private class Update extends AsyncTask<String, Void, String>  {
        private Engine engine;

        @Override
        protected void onPostExecute(String s) {
            // Log.d(TAG, "Option 1==>" + engine.getA1());
            // Log.d(TAG, "Option 2==>" + engine.getB2());
            // Log.d(TAG, "Option 3==>" + engine.getC3());

            option1.setText(engine.getA1());
            option2.setText(engine.getB2());
            option3.setText(engine.getC3());

            getAnswer.setProgress(0);
            switch (s) {
                case "a":
                    option1.setTextColor(Color.RED);
                    option2.setTextColor(Color.BLACK);
                    option3.setTextColor(Color.BLACK);
                    break;
                case "b":
                    option2.setTextColor(Color.RED);
                    option3.setTextColor(Color.BLACK);
                    option1.setTextColor(Color.BLACK);
                    break;
                case "c":
                    option3.setTextColor(Color.RED);
                    option1.setTextColor(Color.BLACK);
                    option2.setTextColor(Color.BLACK);
                    break;
            }
        }

        @Override
        protected String doInBackground(String... strings) {
            //engine = new FindAnswers(strings[0], strings[1], strings[2], strings[3]);
            //obj.search();
            engine = new Engine(new Question(strings[0], strings[1], strings[2], strings[3]));
            engine.search();

            if (!engine.isError()) {
                return engine.getAnswer();
            } else {
                engine = new Engine(new Question(strings[0], strings[1], strings[2], strings[3]));
                // obj = new FindAnswers(strings[0], strings[1], strings[2], strings[3]);
                return engine.search();
            }

        }
    }


}